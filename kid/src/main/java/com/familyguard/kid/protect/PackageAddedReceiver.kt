package com.familyguard.kid.protect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.familyguard.core.backend.CloudBaseAuth
import com.familyguard.core.backend.CloudBaseEvents
import com.familyguard.core.session.SessionStore
import com.familyguard.kid.KidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 新安装应用监听 → 上报异常（让管理端知道装了新 app）。 */
class PackageAddedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        // 排除自身
        if (pkg == context.packageName) return
        CoroutineScope(Dispatchers.IO).launch {
            val auth = CloudBaseAuth.signInAnonymously(KidApp.client, SessionStore.deviceId) ?: return@launch
            CloudBaseEvents.report(KidApp.client, auth.userId, "NEW_APP", "安装了新应用：$pkg", adminUid = SessionStore.boundAdminUid)
        }
    }
}
