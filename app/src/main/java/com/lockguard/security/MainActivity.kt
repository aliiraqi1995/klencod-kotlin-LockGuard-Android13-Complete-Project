package com.lockguard.security

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.lockguard.security.receiver.IntruderAdminReceiver
import com.lockguard.security.ui.LockGuardApp

class MainActivity : ComponentActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    // معالج طلب صلاحية مدير الجهاز (Device Administrator)
    private val deviceAdminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            Toast.makeText(this, "تم تفعيل حارس أندرويد بنجاح!", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "يرجى منح صلاحية مسؤول الجهاز ليعمل كاشف الرمز الخاطئ", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, IntruderAdminReceiver::class.java)

        setContent {
            LockGuardApp(
                isAdminActive = devicePolicyManager.isAdminActive(adminComponent),
                isBatteryIgnored = checkBatteryOptimizationIgnored(),
                onRequestDeviceAdmin = { requestDeviceAdmin() },
                onRequestBatteryIgnore = { requestIgnoreBatteryOptimization() }
            )
        }
    }

    private fun requestDeviceAdmin() {
        if (!devicePolicyManager.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "يتطلب تطبيق حارس قفل الشاشة هذه الصلاحية لمعرفة متى يتم إدخال رمز خاطئ والتقاط صورة للمتطفل فوراً."
                )
            }
            deviceAdminLauncher.launch(intent)
        }
    }

    private fun checkBatteryOptimizationIgnored(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // بديل لبعض الشركات مثل شاومي وسامسونج
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
}