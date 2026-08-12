package com.familyguard.kid.guard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import com.familyguard.kid.R

/**
 * 全屏拦截 Activity（借鉴 cst 的 bringAppToForeground）：
 * 游戏/全屏应用可能压制悬浮窗，本 Activity 以 CLEAR_TASK 抢占前台，游戏无法覆盖。
 */
class BlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        active = true
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SECURE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        setContentView(R.layout.activity_block)

        val reason = intent.getStringExtra(EXTRA_REASON)
        findViewById<android.widget.TextView>(R.id.tvBlockReason)?.text =
            reason ?: getString(R.string.overlay_default_reason)

        findViewById<android.widget.Button>(R.id.btnBlockHome)?.setOnClickListener {
            goHome()
        }
    }

    private fun goHome() {
        BlockOverlay.hide()
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home)
        finish()
    }

    /** 拦截返回键，防止退回受限应用。 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK ||
            keyCode == KeyEvent.KEYCODE_MENU ||
            keyCode == KeyEvent.KEYCODE_SEARCH ||
            keyCode == KeyEvent.KEYCODE_APP_SWITCH
        ) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        active = false
        BlockOverlay.hide()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_REASON = "reason"

        /** 当前是否有拦截页在前台（防轮询重复启动）。 */
        @Volatile
        var active: Boolean = false
            private set

        /** 启动拦截页（CLEAR_TASK 清除受限应用的任务栈）。 */
        fun launch(context: android.content.Context, reason: String?) {
            if (active) return
            val intent = Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_REASON, reason)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT,
                )
            context.startActivity(intent)
        }
    }
}
