package com.familyguard.kid

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.familyguard.core.backend.CloudBaseAuth
import com.familyguard.core.backend.CloudBaseBindings
import com.familyguard.core.session.SessionStore
import com.familyguard.kid.databinding.ActivityMainBinding
import com.familyguard.kid.guide.GuideActivity
import com.familyguard.kid.stats.HeartbeatService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (SessionStore.isBound) {
            showBound()
            startHeartbeat()
        } else {
            showUnbound()
        }
        binding.btnBind.setOnClickListener { doBind() }
        binding.btnGuide.setOnClickListener { openGuide() }
    }

    private fun showUnbound() {
        binding.groupBound.visibility = View.GONE
        binding.groupUnbound.visibility = View.VISIBLE
    }

    private fun showBound() {
        binding.groupBound.visibility = View.VISIBLE
        binding.groupUnbound.visibility = View.GONE
    }

    private fun openGuide() {
        startActivity(Intent(this, GuideActivity::class.java))
    }

    private fun doBind() {
        val code = binding.etInviteCode.text?.toString()?.trim().orEmpty()
        if (code.length != 6) {
            Toast.makeText(this, R.string.error_code_format, Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnBind.isEnabled = false
        binding.btnBind.text = getString(R.string.binding)
        lifecycleScope.launch {
            // 匿名登录（deviceId 本地稳定）→ 绑定
            val auth = CloudBaseAuth.signInAnonymously(KidApp.client, SessionStore.deviceId)
            if (auth == null) {
                Toast.makeText(this@MainActivity, R.string.error_anon_failed, Toast.LENGTH_SHORT).show()
            } else {
                val result = CloudBaseBindings.bindWithCode(KidApp.client, code, auth.userId)
                if (result == null) {
                    Toast.makeText(this@MainActivity, R.string.error_bind_failed, Toast.LENGTH_SHORT).show()
                } else {
                    SessionStore.saveBinding(code, result.adminUid)
                    Toast.makeText(this@MainActivity, R.string.bind_success, Toast.LENGTH_SHORT).show()
                    startHeartbeat()
                    showBound()
                }
            }
            binding.btnBind.isEnabled = true
            binding.btnBind.text = getString(R.string.bind)
        }
    }

    /** 绑定后启动心跳上报服务（前台服务，Android 8+ 用 startForegroundService）。 */
    private fun startHeartbeat() {
        val intent = Intent(this, HeartbeatService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
