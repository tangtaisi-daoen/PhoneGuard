package com.familyguard.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import com.familyguard.admin.databinding.ActivityMainBinding
import com.familyguard.admin.notify.AdminNotifyService
import com.familyguard.core.backend.CloudBaseBindings
import com.familyguard.core.backend.CloudBaseRules
import com.familyguard.core.session.SessionStore
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startNotifyService(showToast = true) else {
            Toast.makeText(this, R.string.admin_notification_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 未登录不允许进入主界面
        if (!SessionStore.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tvWelcome.text = getString(R.string.welcome_admin, SessionStore.username.orEmpty())

        binding.btnLogout.setOnClickListener {
            SessionStore.clearAuth()
            Toast.makeText(this, R.string.logged_out, Toast.LENGTH_SHORT).show()
            finish()
        }
        binding.btnDiag.setOnClickListener {
            startActivity(Intent(this, DiagActivity::class.java))
        }
        binding.btnRules.setOnClickListener {
            startActivity(Intent(this, RulesActivity::class.java))
        }
        binding.btnMonitor.setOnClickListener {
            startActivity(Intent(this, MonitorActivity::class.java))
        }
        binding.btnAnomaly.setOnClickListener {
            startActivity(Intent(this, AnomalyActivity::class.java))
        }
        binding.btnNotify.setOnClickListener {
            ensureNotifyService()
        }
        binding.btnRefreshCode.setOnClickListener { confirmRefreshCode() }
        binding.btnCopyCode.setOnClickListener { copyInviteCode() }
        binding.tvInviteCode.setOnClickListener { copyInviteCode() }

        loadInviteCode()
        ensureRuleOwnership()
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            startNotifyService(showToast = false)
        }
    }

    private fun ensureNotifyService() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startNotifyService(showToast = true)
        }
    }

    private fun startNotifyService(showToast: Boolean) {
        val intent = Intent(this, AdminNotifyService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        if (showToast) Toast.makeText(this, R.string.start_notify, Toast.LENGTH_SHORT).show()
    }

    /** 加载当前邀请码；没有则自动生成。 */
    private fun loadInviteCode() {
        val uid = SessionStore.userId ?: return
        lifecycleScope.launch {
            binding.btnRefreshCode.isEnabled = false
            val code = CloudBaseBindings.getMyInviteCode(AdminApp.client, uid)
            if (code == null) {
                generateInviteCode()
            } else {
                showCode(code)
            }
            binding.btnRefreshCode.isEnabled = true
        }
    }

    /** 为旧 rules 文档补写 kidDeviceId，使被控端可按自身身份读取规则。 */
    private fun ensureRuleOwnership() {
        val uid = SessionStore.userId ?: return
        lifecycleScope.launch {
            val kidId = CloudBaseBindings.getBoundKidDeviceId(AdminApp.client, uid) ?: return@launch
            CloudBaseRules.ensureKidDeviceId(AdminApp.client, uid, kidId)
        }
    }

    /** 刷新邀请码前确认（防止误触导致旧码失效）。 */
    private fun confirmRefreshCode() {
        android.app.AlertDialog.Builder(this)
            .setTitle(R.string.refresh_code_confirm_title)
            .setMessage(R.string.refresh_code_confirm_msg)
            .setPositiveButton(R.string.refresh_code_confirm_ok) { _, _ -> generateInviteCode() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun generateInviteCode() {
        val uid = SessionStore.userId ?: return
        binding.btnRefreshCode.isEnabled = false
        lifecycleScope.launch {
            val code = CloudBaseBindings.generateInviteCode(AdminApp.client, uid)
            if (code == null) {
                val reason = AdminApp.client.lastError ?: "未知原因"
                Toast.makeText(this@MainActivity, getString(R.string.error_gen_code_failed) + "\n$reason", Toast.LENGTH_LONG).show()
            } else {
                showCode(code)
                Toast.makeText(this@MainActivity, R.string.code_generated, Toast.LENGTH_SHORT).show()
            }
            binding.btnRefreshCode.isEnabled = true
        }
    }

    private fun showCode(code: String) {
        binding.tvInviteCode.text = code
        binding.tvInviteCode.visibility = android.view.View.VISIBLE
        binding.btnCopyCode.visibility = android.view.View.VISIBLE
    }

    private fun copyInviteCode() {
        val code = binding.tvInviteCode.text.toString()
        if (code.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("invite", code))
        Toast.makeText(this, R.string.code_copied, Toast.LENGTH_SHORT).show()
    }
}
