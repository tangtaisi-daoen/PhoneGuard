package com.familyguard.kid

import android.app.Application
import com.familyguard.core.backend.CloudBaseClient
import com.familyguard.core.session.RuleCacheStore
import com.familyguard.core.session.SessionStore

class KidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SessionStore.init(this)
        RuleCacheStore.init(this)
        client = CloudBaseClient(BuildConfig.CLOUDBASE_ENV_ID).apply {
            // 被控端使用匿名身份，token 可随时重新获取
        }
    }

    companion object {
        /** 全局 CloudBase 客户端（被控端匿名认证）。 */
        lateinit var client: CloudBaseClient
    }
}
