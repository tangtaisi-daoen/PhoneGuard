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
import com.familyguard.kid.update.KidUpdateManager
import com.familyguard.kid.update.KidUpdateResult
import com.familyguard.kid.update.UpdateDeliveryPhase
import com.familyguard.kid.update.UpdateDeliveryPolicy
import com.familyguard.kid.update.UpdateDeliveryStatus
import com.familyguard.kid.update.UpdateDeliveryStore
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        const val ACTION_SHOW_BOUND_HOME = "com.familyguard.kid.action.SHOW_BOUND_HOME"
        private const val BIND_TIMEOUT_MILLIS = 60_000L
    }

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
        binding.btnCheckUpdate.setOnClickListener { checkForUpdate() }
        binding.tvUpdateStatus.text = getString(R.string.update_status_idle, BuildConfig.VERSION_NAME)
        if (intent?.action == KidUpdateManager.ACTION_CHECK_UPDATE) {
            checkForUpdate()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_SHOW_BOUND_HOME) {
            if (SessionStore.isBound) {
                showBound()
                startHeartbeat()
            } else {
                showUnbound()
            }
            return
        }
        if (intent.action == KidUpdateManager.ACTION_CHECK_UPDATE) {
            checkForUpdate()
        }
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
            runBinding(code)
        }
    }

    private suspend fun runBinding(code: String) {
        try {
            val result = runBindingAttempt(
                timeoutMillis = BIND_TIMEOUT_MILLIS,
                authenticate = {
                    CloudBaseAuth.signInAnonymously(KidApp.client, SessionStore.deviceId)
                },
                bind = { auth ->
                    CloudBaseBindings.bindWithCode(KidApp.client, code, auth.userId)
                },
            )
            when (result) {
                is BindingAttemptResult.Success -> {
                    val bindingResult = result.value
                    SessionStore.saveBinding(code, bindingResult.adminUid)
                    Toast.makeText(this, R.string.bind_success, Toast.LENGTH_SHORT).show()
                    startHeartbeat()
                    showBound()
                }
                BindingAttemptResult.AuthenticationFailed -> showBindingError(R.string.error_anon_failed)
                BindingAttemptResult.BindingFailed -> showBindingError(R.string.error_bind_failed)
                BindingAttemptResult.TimedOut -> showBindingError(R.string.error_bind_timeout)
                is BindingAttemptResult.UnexpectedFailure -> showBindingError(R.string.error_bind_unexpected)
            }
        } finally {
            if (!isFinishing && !isDestroyed) {
                binding.btnBind.isEnabled = true
                binding.btnBind.text = getString(R.string.bind)
            }
        }
    }

    private fun showBindingError(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
    }

    private fun checkForUpdate() {
        val prepared = KidUpdateManager.preparedApk(this)
        val delivery = UpdateDeliveryStore.load(this)
        if (prepared != null && delivery.phase == UpdateDeliveryPhase.AWAITING_USER_CONFIRMATION) {
            binding.tvUpdateStatus.text = getString(R.string.update_ready, delivery.targetVersionName)
            if (!KidUpdateManager.requestInstall(this, prepared)) {
                binding.tvUpdateStatus.text = getString(R.string.update_allow_source)
            }
            return
        }
        binding.btnCheckUpdate.isEnabled = false
        binding.tvUpdateStatus.text = getString(R.string.checking_update)
        lifecycleScope.launch {
            when (val result = KidUpdateManager.checkAndDownload(this@MainActivity)) {
                KidUpdateResult.UpToDate -> {
                    UpdateDeliveryStore.save(
                        this@MainActivity,
                        UpdateDeliveryStatus(
                            installedVersionCode = KidUpdateManager.installedVersionCode(this@MainActivity),
                            installedVersionName = KidUpdateManager.installedVersionName(this@MainActivity),
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                    binding.tvUpdateStatus.text = getString(R.string.update_up_to_date)
                }
                is KidUpdateResult.Failed -> {
                    UpdateDeliveryStore.update(this@MainActivity) { previous ->
                        UpdateDeliveryPolicy.failed(previous, result.reason, System.currentTimeMillis())
                    }
                    binding.tvUpdateStatus.text = getString(R.string.update_failed, result.reason)
                }
                is KidUpdateResult.Ready -> {
                    UpdateDeliveryStore.save(
                        this@MainActivity,
                        UpdateDeliveryPolicy.readyForInstall(
                            result.manifest.versionCode,
                            result.manifest.versionName,
                            requiresUserConfirmation = true,
                            nowMs = System.currentTimeMillis(),
                        ).copy(
                            installedVersionCode = KidUpdateManager.installedVersionCode(this@MainActivity),
                            installedVersionName = KidUpdateManager.installedVersionName(this@MainActivity),
                        ),
                    )
                    binding.tvUpdateStatus.text = getString(R.string.update_ready, result.manifest.versionName)
                    if (!KidUpdateManager.requestInstall(this@MainActivity, result.apk)) {
                        binding.tvUpdateStatus.text = getString(R.string.update_allow_source)
                    }
                }
            }
            binding.btnCheckUpdate.isEnabled = true
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
