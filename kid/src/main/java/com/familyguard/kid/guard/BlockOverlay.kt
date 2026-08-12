package com.familyguard.kid.guard

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.familyguard.kid.R

/**
 * 全屏拦截浮层（借鉴 cst：SYSTEM_ALERT_WINDOW 全屏遮罩，无法绕过）。
 * 拦截期间覆盖整屏并拦截返回键；回到非受限应用时由 GuardAccessibilityService 调用 hide()。
 */
object BlockOverlay {

    private var view: View? = null
    private var windowManager: WindowManager? = null

    /** 是否正在显示。 */
    fun isShowing(): Boolean = view != null

    /** 显示全屏拦截页。 */
    fun show(context: Context, reason: String?) {
        if (view != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val overlay = LayoutInflater.from(context).inflate(R.layout.view_block_overlay, null)
        overlay.findViewById<TextView>(R.id.tvOverlayReason)?.text = reason ?: context.getString(R.string.overlay_default_reason)

        // 硬件键拦截（返回/菜单/搜索/最近任务；Home 由系统接管）
        overlay.isFocusable = true
        overlay.isFocusableInTouchMode = true
        overlay.requestFocus()
        overlay.setOnKeyListener { _, keyCode, _ ->
            keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_MENU ||
                keyCode == KeyEvent.KEYCODE_SEARCH ||
                keyCode == KeyEvent.KEYCODE_APP_SWITCH
        }

        // 「返回桌面」按钮：隐藏浮层并回桌面（后续打开受限 app 会再次拦截）
        overlay.findViewById<View>(R.id.btnGoHome)?.setOnClickListener {
            hide()
            runCatching {
                val home = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(home)
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        overlay.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        runCatching {
            wm.addView(overlay, params)
            view = overlay
            windowManager = wm
        }
    }

    /** 隐藏拦截页（前台回到非受限应用时）。 */
    fun hide() {
        val v = view ?: return
        runCatching { windowManager?.removeView(v) }
        view = null
        windowManager = null
    }
}
