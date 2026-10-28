package com.lockguard.security.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lockguard.security.MainActivity
import com.lockguard.security.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * خدمة التقاط الكاميرا الأمامية لأندرويد 13+
 * تعمل كـ ForegroundService لضمان عدم إغلاقها من النظام وتلتقط الصورة خلسة بدون واجهة
 */
class CameraCaptureService : Service() {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val attempt = intent?.getIntExtra("ATTEMPT_NUMBER", 1) ?: 1
        startAsForeground()

        takeFrontCameraSelfie(attempt)

        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val channelId = "lockguard_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "حارس قفل الشاشة",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("حارس قفل الشاشة نشط")
            .setContentText("جاري تأمين هاتفك ورصد أي محاولة اختراق...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(101, notification)
    }

    private fun takeFrontCameraSelfie(attemptNumber: Int) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // اختيار الكاميرا الأمامية تحديداً
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                // مجلد حفظ لقطات المتطفلين
                val photosDir = File(filesDir, "intruders").apply { if (!exists()) mkdirs() }
                val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val photoFile = File(photosDir, "INTRUDER_${timeStamp}_att${attemptNumber}.jpg")

                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                // التقاط الصورة
                imageCapture?.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(this),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            Log.i(TAG, "📸 تم التقاط وحفظ صورة المتطفل بنجاح في: ${photoFile.absolutePath}")
                            notifyOwnerIntruderCaptured(photoFile.name)
                            stopSelf()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "فشل التقاط صورة الكاميرا: ${exception.message}", exception)
                            stopSelf()
                        }
                    }
                )
            } catch (exc: Exception) {
                Log.e(TAG, "تعذر تشغيل الكاميرا الأمامية في الخلفية: ${exc.message}", exc)
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun notifyOwnerIntruderCaptured(filename: String) {
        val channelId = "intruder_alerts_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                channelId,
                "تنبيهات المتطفلين",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(alertChannel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("OPEN_GALLERY", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 1, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val alertNotif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("🚨 تنبيه: محاولة فتح قفل فاشلة!")
            .setContentText("تم التقاط صورة للشخص الذي حاول فتح هاتفك بواسطة الكاميرا الأمامية.")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(202, alertNotif)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "LockGuardCameraService"
    }
}