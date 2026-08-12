package com.familyguard.admin

import android.app.Application
import com.familyguard.core.backend.CloudBaseClient
import com.familyguard.core.session.SessionStore

class AdminApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionStore.init(this)
        client = CloudBaseClient(BuildConfig.CLOUDBASE_ENV_ID).apply {
            accessToken = SessionStore.accessToken
        }
    }

    companion object {
        /** 全局 CloudBase 客户端（登录态从 SessionStore 恢复）。 */
        lateinit var client: CloudBaseClient
    }
}
