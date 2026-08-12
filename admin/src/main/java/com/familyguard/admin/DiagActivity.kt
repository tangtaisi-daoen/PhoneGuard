package com.familyguard.admin

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.familyguard.admin.databinding.ActivityDiagBinding
import com.familyguard.core.backend.CloudBaseDb
import com.familyguard.core.session.SessionStore
import kotlinx.coroutines.launch

/** 诊断页：显示登录态与 CloudBase 连通性，帮助定位"没反应"类问题。 */
class DiagActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDiagBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiagBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnDiag.setOnClickListener { runDiag() }
        refreshSessionInfo()
    }

    private fun refreshSessionInfo() {
        val sb = StringBuilder()
        sb.append("uid: ").append(SessionStore.userId ?: "null").append('\n')
        sb.append("username: ").append(SessionStore.username ?: "null").append('\n')
        val token = SessionStore.accessToken
        sb.append("accessToken: ").append(if (token.isNullOrBlank()) "null" else token.take(20) + "...").append('\n')
        val rt = SessionStore.refreshToken
        sb.append("refreshToken: ").append(if (rt.isNullOrBlank()) "null" else rt.take(12) + "...").append('\n')
        binding.tvSession.text = sb.toString()
    }

    private fun runDiag() {
        binding.tvResult.text = "诊断中…"
        binding.tvResult.visibility = View.VISIBLE
        binding.btnDiag.isEnabled = false
        lifecycleScope.launch {
            val sb = StringBuilder()
            val before = System.currentTimeMillis()
            val docs = CloudBaseDb.queryDocuments(AdminApp.client, "bindings", limit = 1)
            val cost = System.currentTimeMillis() - before
            sb.append("请求 bindings 集合: ")
            if (docs != null) {
                sb.append("OK（${cost}ms）\n")
            } else {
                sb.append("失败（${cost}ms）\n")
                sb.append("原因: ").append(AdminApp.client.lastError ?: "未知").append('\n')
            }
            binding.tvResult.text = sb.toString()
            binding.btnDiag.isEnabled = true
            if (docs == null) {
                Toast.makeText(this@DiagActivity, "诊断失败，请把上方原因文字告诉我", Toast.LENGTH_LONG).show()
            }
        }
    }
}
