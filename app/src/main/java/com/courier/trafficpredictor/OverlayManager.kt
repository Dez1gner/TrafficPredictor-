package com.courier.trafficpredictor

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var textView: TextView? = null

    fun show(text: String) {
        if (textView == null) {
            val tv = TextView(context)
            tv.setBackgroundColor(Color.parseColor("#CC000000"))
            tv.setTextColor(Color.WHITE)
            tv.textSize = 16f
            tv.setPadding(28, 20, 28, 20)

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = 80

            windowManager.addView(tv, params)
            textView = tv
        }
        textView?.text = text
    }

    fun hide() {
        textView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) { /* уже удалено */ }
        }
        textView = null
    }
}
