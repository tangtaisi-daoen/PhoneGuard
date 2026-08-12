package com.familyguard.admin

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.familyguard.admin.databinding.ActivityMainBinding
import com.familyguard.core.session.SessionStore

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
    }
}
