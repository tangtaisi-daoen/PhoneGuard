package com.familyguard.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.familyguard.admin.databinding.ActivityMainBinding
import com.familyguard.core.backend.CloudBaseBindings
import com.familyguard.core.session.SessionStore
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        binding.btnRefreshCode.setOnClickListener { generateInviteCode() }
        binding.btnCopyCode.setOnClickListener { copyInviteCode() }
        binding.tvInviteCode.setOnClickListener { copyInviteCode() }

        loadInviteCode()
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
