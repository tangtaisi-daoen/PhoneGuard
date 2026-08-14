package com.familyguard.kid.guide

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.familyguard.kid.MainActivity
import com.familyguard.kid.R
import com.familyguard.kid.databinding.ActivityGuideBinding

/** 防护权限引导页：使用情况访问 / 无障碍 / 电池优化 / 自启动。 */
class GuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuideBinding
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) refresh() else openNotificationSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnUsage.setOnClickListener {
            if (GuardStatus.hasUsageAccess(this)) returnToBoundHome()
            else startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.btnAccessibility.setOnClickListener {
            if (GuardStatus.isAccessibilityEnabled(this)) returnToBoundHome()
            else startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnBattery.setOnClickListener {
            if (GuardStatus.ignoresBatteryOptimization(this)) {
                returnToBoundHome()
                return@setOnClickListener
            }
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            runCatching { startActivity(intent) }.onFailure {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        binding.btnAutostart.setOnClickListener {
            if (GuardStatus.isAutostartConfirmed(this)) returnToBoundHome()
            else openOppoAutostartSettings()
        }
        binding.btnConfirmAutostart.setOnClickListener {
            GuardStatus.confirmAutostart(this)
            Toast.makeText(this, R.string.guide_autostart_confirmed_toast, Toast.LENGTH_SHORT).show()
            refresh()
        }
        binding.btnAdmin.setOnClickListener {
            if (GuardStatus.isAdminActive(this)) returnToBoundHome()
            else openDeviceAdmin()
        }
        binding.btnOverlay.setOnClickListener {
            if (GuardStatus.canDrawOverlays(this)) {
                returnToBoundHome()
                return@setOnClickListener
            }
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            runCatching { startActivity(intent) }
                .onFailure { Toast.makeText(this, R.string.guide_open_settings_failed, Toast.LENGTH_SHORT).show() }
        }
        binding.btnNotifications.setOnClickListener {
            if (GuardStatus.notificationsEnabled(this)) {
                returnToBoundHome()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings()
            }
        }
        binding.btnRefreshGuide.setOnClickListener { refresh() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val usage = GuardStatus.hasUsageAccess(this)
        val a11y = GuardStatus.isAccessibilityEnabled(this)
        val battery = GuardStatus.ignoresBatteryOptimization(this)
        val admin = GuardStatus.isAdminActive(this)
        val overlay = GuardStatus.canDrawOverlays(this)
        val autostart = GuardStatus.isAutostartConfirmed(this)
        val notifications = GuardStatus.notificationsEnabled(this)
        binding.tvUsage.text = getString(if (usage) R.string.guide_ok else R.string.guide_missing)
        binding.tvAccessibility.text = getString(if (a11y) R.string.guide_ok else R.string.guide_missing)
        binding.tvBattery.text = getString(if (battery) R.string.guide_ok else R.string.guide_missing)
        binding.tvAutostart.text = getString(
            if (autostart) R.string.guide_autostart_confirmed else R.string.guide_autostart_manual,
        )
        binding.tvAdmin.text = getString(if (admin) R.string.guide_ok else R.string.guide_missing)
        binding.tvOverlay.text = getString(if (overlay) R.string.guide_ok else R.string.guide_missing)
        binding.tvNotifications.text = getString(if (notifications) R.string.guide_ok else R.string.guide_missing)
        binding.btnUsage.setText(if (usage) R.string.guide_protected else R.string.guide_go)
        binding.btnAccessibility.setText(if (a11y) R.string.guide_protected else R.string.guide_go)
        binding.btnBattery.setText(if (battery) R.string.guide_protected else R.string.guide_go)
        binding.btnAutostart.setText(if (autostart) R.string.guide_protected else R.string.guide_go)
        binding.btnAdmin.setText(if (admin) R.string.guide_protected else R.string.guide_go)
        binding.btnOverlay.setText(if (overlay) R.string.guide_protected else R.string.guide_go)
        binding.btnNotifications.setText(if (notifications) R.string.guide_protected else R.string.guide_go)
        binding.btnConfirmAutostart.isEnabled = !autostart
    }

    private fun returnToBoundHome() {
        Toast.makeText(this, R.string.guide_permission_protected, Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            ),
        )
        finish()
    }

    /** 激活设备管理器（防卸载）。 */
    private fun openDeviceAdmin() {
        val cn = android.content.ComponentName(this, com.familyguard.kid.protect.KidDeviceAdminReceiver::class.java)
        val intent = Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, cn)
            .putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.guide_admin_explain))
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, R.string.guide_open_settings_failed, Toast.LENGTH_SHORT).show() }
    }

    /** OPPO/ColorOS 自启动管理入口（不同版本入口不同，逐个尝试）。 */
    private fun openOppoAutostartSettings() {
        val candidates = listOf(
            "com.coloros.safecenter/.startupapp.StartupAppListActivity",
            "com.oplus.safecenter/.startupapp.StartupAppListActivity",
            "com.coloros.phonemanager/.startupapp.StartupAppListActivity",
        )
        for (c in candidates) {
            val (pkg, cls) = c.split('/', limit = 2)
            try {
                startActivity(Intent().setClassName(pkg, cls))
                return
            } catch (_: Exception) {
            }
        }
        runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            .onFailure { Toast.makeText(this, R.string.guide_open_settings_failed, Toast.LENGTH_SHORT).show() }
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, R.string.guide_open_settings_failed, Toast.LENGTH_SHORT).show() }
    }
}
