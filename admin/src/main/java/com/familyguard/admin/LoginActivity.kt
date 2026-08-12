package com.familyguard.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.familyguard.admin.databinding.ActivityLoginBinding
import com.familyguard.core.backend.CloudBaseAuth
import com.familyguard.core.session.SessionStore
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 已登录直接进主界面
        if (SessionStore.isLoggedIn) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { doLogin() }
        binding.btnGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun doLogin() {
        val username = binding.etUsername.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString().orEmpty()
        if (username.isBlank() || password.isBlank()) {
            Toast.makeText(this, R.string.error_empty_input, Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnLogin.isEnabled = false
        binding.btnLogin.text = getString(R.string.logging_in)
        lifecycleScope.launch {
            val auth = CloudBaseAuth.signIn(AdminApp.client, username, password)
            if (auth == null) {
                binding.btnLogin.isEnabled = true
                binding.btnLogin.text = getString(R.string.login)
                Toast.makeText(this@LoginActivity, R.string.error_login_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            SessionStore.saveAuth(auth.accessToken, auth.refreshToken, auth.userId, username)
            Toast.makeText(this@LoginActivity, R.string.login_success, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
            finish()
        }
    }
}
