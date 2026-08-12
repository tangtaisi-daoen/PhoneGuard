package com.familyguard.kid.guide

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.familyguard.kid.R
import com.familyguard.kid.databinding.ActivityGuideBinding

/** 防护权限引导页：使用情况访问 / 无障碍 / 电池优化 / 自启动。 */
class GuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGuideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        binding.btnBattery.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            runCatching { startActivity(intent) }.onFailure {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        binding.btnAutostart.setOnClickListener {
            openOppoAutostartSettings()
        }
        binding.btnAdmin.setOnClickListener { openDeviceAdmin() }
        binding.btnOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName"),
            )
            runCatching { startActivity(intent) }
                .onFailure { Toast.makeText(this, R.string.guide_open_settings_failed, Toast.LENGTH_SHORT).show() }
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
        binding.tvUsage.text = getString(if (usage) R.string.guide_ok else R.string.guide_missing)
        binding.tvAccessibility.text = getString(if (a11y) R.string.guide_ok else R.string.guide_missing)
        binding.tvBattery.text = getString(if (battery) R.string.guide_ok else R.string.guide_missing)
        binding.tvAutostart.text = getString(R.string.guide_autostart_manual)
        binding.tvAdmin.text = getString(if (admin) R.string.guide_ok else R.string.guide_missing)
        binding.tvOverlay.text = getString(if (overlay) R.string.guide_ok else R.string.guide_missing)
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
}
