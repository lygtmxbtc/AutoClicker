package com.example.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * 无障碍服务：负责在屏幕上显示悬浮控件，并在用户开启连点后，
 * 通过 dispatchGesture 在目标准星位置连续模拟点击。
 */
class AutoClickService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var targetView: CrosshairView? = null
    private var panelView: View? = null

    private var targetParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var intervalMs = 50L
    private var isClicking = false

    private lateinit var intervalLabel: TextView
    private var clickToggleButton: Button? = null

    /** 每次手势结束后再安排下一次点击，避免多个注入手势互相取消。 */
    private val clickLoop = Runnable {
        if (isClicking) {
            performClickAtTarget()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopClicking()
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        stopClicking()
        removeOverlay()
        return super.onUnbind(intent)
    }

    // ------------------------------------------------------------------
    // 悬浮控件
    // ------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        val metrics = resources.displayMetrics

        // 1. 目标准星（可拖动，决定点击落点）
        val crosshair = CrosshairView(this)
        val size = dp(60)
        val tParams = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels / 2 - size / 2
            y = metrics.heightPixels / 2 - size / 2
        }
        crosshair.setOnTouchListener(DragTouchListener(tParams) { windowManager.updateViewLayout(crosshair, tParams) })
        windowManager.addView(crosshair, tParams)
        targetView = crosshair
        targetParams = tParams

        // 2. 控制面板：拖动手柄 + 间隔调节 + 开始/停止按钮
        val panel = buildControlPanel()
        val pParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = metrics.heightPixels - dp(220)
        }
        windowManager.addView(panel, pParams)
        panelView = panel
        panelParams = pParams
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun buildControlPanel(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(220, 33, 33, 33))
            setPadding(dp(12), dp(10), dp(12), dp(12))
        }

        // 拖动手柄
        val dragHandle = TextView(this).apply {
            text = "⣿ 连点器（拖动此处移动）"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 0, 0, dp(8))
        }
        dragHandle.setOnTouchListener(DragTouchListener(panelParamsProvider = { panelParams!! }) {
            windowManager.updateViewLayout(panelView, panelParams)
        })
        root.addView(dragHandle)

        // 间隔调节
        intervalLabel = TextView(this).apply {
            text = "点击间隔：$intervalMs ms"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        val intervalRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val minus = Button(this).apply {
            text = "－"
            setOnClickListener {
                intervalMs = (intervalMs - 10).coerceAtLeast(10)
                intervalLabel.text = "点击间隔：$intervalMs ms"
            }
        }
        val plus = Button(this).apply {
            text = "＋"
            setOnClickListener {
                intervalMs += 10
                intervalLabel.text = "点击间隔：$intervalMs ms"
            }
        }
        intervalRow.addView(minus)
        intervalRow.addView(plus)
        root.addView(intervalLabel)
        root.addView(intervalRow)

        // 点击一次开始，再点击一次停止。使用 ACTION_DOWN 可在下一次注入手势前立即停止。
        val toggleButton = Button(this).apply {
            text = getString(R.string.start_clicking)
            setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.isPressed = true
                        if (isClicking) stopClicking() else startClicking()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        true
                    }
                    else -> true
                }
            }
        }
        clickToggleButton = toggleButton
        root.addView(toggleButton)

        // 关闭（停止服务并移除悬浮窗）
        val closeButton = Button(this).apply {
            text = "关闭连点器"
            setOnClickListener { disableSelf() }
        }
        root.addView(closeButton)

        return root
    }

    private fun removeOverlay() {
        targetView?.let { runCatching { windowManager.removeView(it) } }
        panelView?.let { runCatching { windowManager.removeView(it) } }
        targetView = null
        panelView = null
    }

    // ------------------------------------------------------------------
    // 连点逻辑
    // ------------------------------------------------------------------

    private fun startClicking() {
        if (isClicking) return
        isClicking = true
        clickToggleButton?.text = getString(R.string.stop_clicking)
        setTargetTouchable(false)
        handler.post(clickLoop)
    }

    private fun stopClicking() {
        isClicking = false
        handler.removeCallbacks(clickLoop)
        clickToggleButton?.text = getString(R.string.start_clicking)
        setTargetTouchable(true)
    }

    private fun scheduleNextClick() {
        if (isClicking) {
            handler.postDelayed(clickLoop, intervalMs)
        }
    }

    private fun performClickAtTarget() {
        val view = targetView ?: return
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val x = loc[0] + view.width / 2f
        val y = loc[1] + view.height / 2f

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 1)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val accepted = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) {
                    scheduleNextClick()
                }

                override fun onCancelled(gestureDescription: GestureDescription) {
                    scheduleNextClick()
                }
            },
            handler
        )
        if (!accepted) {
            scheduleNextClick()
        }
    }

    /** 连点时让准星不可触摸，使模拟点击能够落到下层应用。 */
    private fun setTargetTouchable(touchable: Boolean) {
        val view = targetView ?: return
        val params = targetParams ?: return
        params.flags = if (touchable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    /** 通用拖动监听：跟随手指移动更新 WindowManager 布局的 x/y。 */
    private inner class DragTouchListener(
        private val params: WindowManager.LayoutParams? = null,
        private val panelParamsProvider: (() -> WindowManager.LayoutParams)? = null,
        private val onMoved: () -> Unit
    ) : View.OnTouchListener {

        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f

        private fun lp() = params ?: panelParamsProvider!!.invoke()

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = lp()
            return when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = initialX + (event.rawX - touchX).toInt()
                    lp.y = initialY + (event.rawY - touchY).toInt()
                    onMoved()
                    true
                }
                else -> false
            }
        }
    }

    /** 简单的红色准星视图，用于标示点击落点。 */
    private class CrosshairView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 255, 0, 0)
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(width, height) / 2f - 6f
            canvas.drawCircle(cx, cy, r, fill)
            canvas.drawCircle(cx, cy, r, paint)
            canvas.drawLine(cx - r, cy, cx + r, cy, paint)
            canvas.drawLine(cx, cy - r, cx, cy + r, paint)
        }
    }
}
