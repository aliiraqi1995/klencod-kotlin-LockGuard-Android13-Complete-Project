package com.lockguard.security.receiver

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.lockguard.security.service.CameraCaptureService

/**
 * مستقبل أحداث مدير الجهاز (DeviceAdminReceiver)
 * عند فشل إدخال رمز القفل، يقوم نظام أندرويد تلقائياً باستدعاء دالة onPasswordFailed
 */
class IntruderAdminReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w(TAG, "🚨 تم رصد محاولة إدخال رمز قفل شاشة خاطئ!")

        val prefs = context.getSharedPreferences("lock_guard_prefs", Context.MODE_PRIVATE)
        val failCount = prefs.getInt("fail_count", 0) + 1
        prefs.edit().putInt("fail_count", failCount).apply()

        val maxAllowed = prefs.getInt("threshold", 1) // الافتراضي التقاط فوري من أول خطأ

        if (failCount >= maxAllowed) {
            // تشغيل خدمة التقاط الكاميرا الأمامية بالخلفية
            val serviceIntent = Intent(context, CameraCaptureService::class.java).apply {
                putExtra("ATTEMPT_NUMBER", failCount)
                putExtra("TIMESTAMP", System.currentTimeMillis())
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.i(TAG, "✅ تم فتح القفل بنجاح من المالك الشرعي")
        // تصفير عداد المحاولات الفاشلة عند النجاح
        val prefs = context.getSharedPreferences("lock_guard_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("fail_count", 0).apply()
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "🛡️ تم تفعيل حارس أمان مدير الجهاز بنجاح")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "⚠️ تم إلغاء تفعيل مدير الجهاز")
    }

    companion object {
        private const val TAG = "LockGuardAdmin"
    }
}