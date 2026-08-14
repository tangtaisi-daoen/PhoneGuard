package com.familyguard.kid.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.familyguard.core.session.SessionStore
import com.familyguard.kid.stats.HeartbeatService

/** Restores the guard loop after a reboot or a successful in-place update. */
class UpdateRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            UpdateDeliveryStore.update(context) { previous ->
                UpdateDeliveryPolicy.afterPackageReplaced(
                    previous,
                    installedVersionCode = KidUpdateManager.installedVersionCode(context),
                    installedVersionName = KidUpdateManager.installedVersionName(context),
                    nowMs = System.currentTimeMillis(),
                )
            }
        }
        if (!SessionStore.isBound) return
        val service = Intent(context, HeartbeatService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(service)
        } else {
            context.startService(service)
        }
    }
}
