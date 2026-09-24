package com.courier.trafficpredictor

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

/**
 * Компактная плавающая панелька поверх других приложений (в т.ч. поверх навигатора):
 * большая кнопка "Светофор" (легко попасть в тряске), маленькая кнопка "Стоп"
 * и тонкая строка статуса. Панельку можно перетащить пальцем в удобное место.
 */
class OverlayManager(
    private val context: Context,
    private val onMarkLight: () -> Unit,
    private val onStop: () -> Unit
) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: LinearLayout? = null
    private var statusText: TextView? = null
    private lateinit var params: WindowManager.LayoutParams

    fun showPanel() {
        if (rootView != null) return

        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#DD1B1B1B"))
                cornerRadius = dp(14).toFloat()
            }
        }

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val markButton = Button(context).apply {
            text = "СВЕТОФОР"
            textSize = 15f
            setPadding(dp(20), dp(28), dp(20), dp(28))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f)
            setOnClickListener { onMarkLight() }
        }

        val stopButton = Button(context).apply {
            text = "СТОП"
            textSize = 12f
            setPadding(dp(10), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
            setOnClickListener { onStop() }
        }

        buttonsRow.addView(markButton)
        buttonsRow.addView(stopButton)

        val status = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            text = ""
            setPadding(dp(2), dp(6), dp(2), dp(0))
        }
        statusText = status

        container.addView(buttonsRow)
        container.addView(status)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = dp(12)
        params.y = dp(60)

        // Перетаскивание панели пальцем (по фону контейнера, не по кнопкам)
        var touchStartX = 0
        var touchStartY = 0
        var startRawX = 0f
        var startRawY = 0f
        var moved = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = params.x
                    touchStartY = params.y
                    startRawX = event.rawX
                    startRawY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startRawX).toInt()
                    val dy = (event.rawY - startRawY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    if (moved) {
                        params.x = touchStartX + dx
                        params.y = touchStartY + dy
                        try {
                            windowManager.updateViewLayout(container, params)
                        } catch (e: Exception) { /* окно уже могло закрыться */ }
                    }
                    true
                }
                else -> false
            }
        }

        windowManager.addView(container, params)
        rootView = container
    }

    fun setStatus(text: String) {
        statusText?.text = text
    }

    fun hide() {
        rootView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) { /* уже удалено */ }
        }
        rootView = null
        statusText = null
    }
}
