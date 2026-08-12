package com.familyguard.kid.protect

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.familyguard.core.backend.CloudBaseAuth
import com.familyguard.core.backend.CloudBaseEvents
import com.familyguard.core.session.SessionStore
import com.familyguard.kid.KidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 设备管理器：激活后系统拦截直接卸载；被停用（弟弟尝试解除）时上报异常。
 */
class KidDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        // 设备管理器被停用 → 上报异常（防卸载被解除）
        CoroutineScope(Dispatchers.IO).launch {
            val auth = CloudBaseAuth.signInAnonymously(KidApp.client, SessionStore.deviceId) ?: return@launch
            CloudBaseEvents.report(KidApp.client, auth.userId, "ADMIN_DISABLED", "设备管理器已被停用，卸载保护失效")
        }
    }
}
