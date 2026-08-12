package com.familyguard.admin

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.familyguard.admin.databinding.ActivityRegisterBinding
import com.familyguard.core.backend.CloudBaseAuth
import com.familyguard.core.session.SessionStore
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnSendCode.setOnClickListener { sendCode() }
        binding.btnRegister.setOnClickListener { doRegister() }
    }

    private fun sendCode() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.error_email_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnSendCode.isEnabled = false
        lifecycleScope.launch {
            val verificationId = CloudBaseAuth.sendEmailVerification(AdminApp.client, email)
            if (verificationId == null) {
                Toast.makeText(this@RegisterActivity, R.string.error_send_code_failed, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@RegisterActivity, R.string.code_sent, Toast.LENGTH_SHORT).show()
                pendingVerificationId = verificationId
            }
            binding.btnSendCode.isEnabled = true
        }
    }

    private fun doRegister() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val code = binding.etCode.text?.toString()?.trim().orEmpty()
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        // 与 CloudBase 规则一致：小写字母开头，6-25 位小写字母/数字/_- 
        val usernameOk = Regex("^[a-z][0-9a-z_-]{5,24}$").matches(username)
        if (email.isBlank() || code.isBlank() || !usernameOk || password.length < 8) {
            Toast.makeText(this, R.string.error_register_input, Toast.LENGTH_SHORT).show()
            return
        }
        val verificationId = pendingVerificationId
        if (verificationId == null) {
            Toast.makeText(this, R.string.error_send_code_first, Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnRegister.isEnabled = false
        lifecycleScope.launch {
            val token = CloudBaseAuth.verifyEmailCode(AdminApp.client, verificationId, code)
            if (token == null) {
                Toast.makeText(this@RegisterActivity, R.string.error_code_invalid, Toast.LENGTH_SHORT).show()
                binding.btnRegister.isEnabled = true
                return@launch
            }
            val auth = CloudBaseAuth.signUpWithEmail(AdminApp.client, email, token, username, password)
            if (auth == null) {
                Toast.makeText(this@RegisterActivity, R.string.error_register_failed, Toast.LENGTH_SHORT).show()
                binding.btnRegister.isEnabled = true
                return@launch
            }
            SessionStore.saveAuth(auth.accessToken, auth.refreshToken, auth.userId, username)
            Toast.makeText(this@RegisterActivity, R.string.register_success, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private var pendingVerificationId: String? = null
}
