package online.youcd.heartrate.data.ble

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import online.youcd.heartrate.R
import online.youcd.heartrate.data.model.HeartRateZone
import online.youcd.heartrate.data.repository.HeartRateRepository
import online.youcd.heartrate.data.session.SessionManager
import java.util.ArrayDeque
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Singleton
class FloatingWindowManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bleManager: BleManager,
    private val repository: HeartRateRepository,
    private val sessionManager: SessionManager
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val density = context.resources.displayMetrics.density

    private var floatingView: View? = null
    private var collectJob: Job? = null
    private var visible = false
    private var expanded = false
    private var connected = false

    // 子视图
    private var container: LinearLayout? = null
    private var heartImg: ImageView? = null
    private var rippleView: View? = null
    private var bpmText: TextView? = null
    private var zoneLabel: TextView? = null
    private var trendArrow: TextView? = null
    private var panel: LinearLayout? = null
    private var avgText: TextView? = null
    private var maxText: TextView? = null
    private var durationText: TextView? = null
    private var zoneBarFill: View? = null

    // 数据
    private var maxHr = 190
    private var lastBpm = 0
    private val recentBpms = ArrayDeque<Int>()

    private var heartbeatAnimator: ValueAnimator? = null
    private var bpmPopAnimator: ValueAnimator? = null

    val isVisible: Boolean
        get() = visible

    fun canDrawOverlays(): Boolean =
        Settings.canDrawOverlays(context)

    fun start() {
        if (visible) return
        if (!canDrawOverlays()) return
        addFloatingView()
        visible = true

        scope.launch {
            repository.profile.collectLatest { profile ->
                maxHr = profile.maxHeartRate()
            }
        }
        collectJob = scope.launch {
            bleManager.connectionState.collectLatest { state ->
                updateConnectionState(state is BleManager.ConnectionState.Connected)
            }
        }
        scope.launch {
            bleManager.heartRate.collectLatest { bpm ->
                updateHeartRate(bpm)
            }
        }
        scope.launch {
            sessionManager.elapsedMillis.collectLatest { millis ->
                durationText?.text = formatDuration(millis / 1000)
            }
        }
    }

    fun stop() {
        if (!visible) return
        collectJob?.cancel()
        collectJob = null
        stopHeartbeat()
        floatingView?.let { runCatching { windowManager.removeView(it) } }
        floatingView = null
        container = null
        visible = false
    }

    fun toggle(): Boolean {
        if (visible) stop() else start()
        return visible
    }

    private fun updateConnectionState(isConnected: Boolean) {
        connected = isConnected
        if (!isConnected) {
            stopHeartbeat()
            heartImg?.setColorFilter(0xFFFF2D55.toInt(), PorterDuff.Mode.SRC_IN)
            bpmText?.text = "--"
            zoneLabel?.text = "未连接"
            zoneLabel?.setTextColor(0xFF8E8E93.toInt())
            trendArrow?.text = ""
        }
    }

    private fun updateHeartRate(bpm: Int) {
        recentBpms.addLast(bpm)
        if (recentBpms.size > 60) recentBpms.removeFirst()

        val avg = recentBpms.average().toInt()
        val max = recentBpms.max()
        val zone = HeartRateZone.from(bpm, maxHr)
        val zoneColor = zone.color.toInt()

        heartImg?.setColorFilter(zoneColor, PorterDuff.Mode.SRC_IN)
        bpmText?.text = bpm.toString()
        zoneLabel?.text = zoneLabelText(zone.id)
        zoneLabel?.setTextColor(zoneColor)

        avgText?.text = "$avg"
        maxText?.text = "$max"

        val fillWidth = ((bpm.toFloat() / maxHr).coerceIn(0f, 1f) * dp(104)).toInt()
        zoneBarFill?.layoutParams = LinearLayout.LayoutParams(fillWidth, dp(6))

        if (lastBpm > 0) {
            trendArrow?.text = when {
                bpm - lastBpm > 3 -> "↑"
                bpm - lastBpm < -3 -> "↓"
                else -> "→"
            }
        }
        if (bpm != lastBpm) {
            popBpmText()
            beatOnce(bpm)
        }
        lastBpm = bpm
    }

    private fun beatOnce(bpm: Int) {
        if (!connected) return
        heartbeatAnimator?.cancel()
        val beatMs = (60_000.0 / bpm.coerceIn(30, 240)).toLong().coerceIn(300L, 2000L)
        heartbeatAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = beatMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                val scale = 1f + 0.18f * sin(t * PI.toFloat())
                heartImg?.scaleX = scale
                heartImg?.scaleY = scale
                val ringScale = 0.4f + t * 1.0f
                rippleView?.scaleX = ringScale
                rippleView?.scaleY = ringScale
                rippleView?.alpha = 1f - t
            }
            start()
        }
    }

    private fun stopHeartbeat() {
        heartbeatAnimator?.cancel()
        heartbeatAnimator = null
        heartImg?.scaleX = 1f
        heartImg?.scaleY = 1f
        rippleView?.alpha = 0f
    }

    private fun popBpmText() {
        bpmPopAnimator?.cancel()
        bpmText?.scaleX = 1f
        bpmText?.scaleY = 1f
        bpmPopAnimator = ValueAnimator.ofFloat(1.15f, 1f).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val s = anim.animatedValue as Float
                bpmText?.scaleX = s
                bpmText?.scaleY = s
            }
            start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addFloatingView() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24f * density
                setColor(0xD9121212.toInt())
                setStroke(dp(1), 0x14FFFFFF)
            }
            elevation = dp(20).toFloat()
        }
        this.container = container

        // 主行
        val mainRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        // 图标 + 发光圆环
        val iconWrap = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        }
        val ripple = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RING
                setStroke(dp(2), 0xFFFFFFFF.toInt())
                setColor(Color.TRANSPARENT)
            }
            alpha = 0f
            layoutParams = FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER)
        }
        rippleView = ripple
        val heart = ImageView(context).apply {
            setImageResource(R.drawable.ic_stat_heart)
            setColorFilter(0xFFFF2D55.toInt(), PorterDuff.Mode.SRC_IN)
            layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)
        }
        heartImg = heart
        iconWrap.addView(ripple)
        iconWrap.addView(heart)

        // 数值列
        val infoCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        val valueRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val bpm = TextView(context).apply {
            text = "--"
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        }
        bpmText = bpm
        val unit = TextView(context).apply {
            text = "BPM"
            setTextColor(0xFF8E8E93.toInt())
            textSize = 11f
            setPadding(dp(5), 0, 0, dp(3))
        }
        val trend = TextView(context).apply {
            setTextColor(0xFF8E8E93.toInt())
            textSize = 14f
            setPadding(dp(6), 0, 0, 0)
        }
        trendArrow = trend
        valueRow.addView(bpm)
        valueRow.addView(unit)
        valueRow.addView(trend)

        val zone = TextView(context).apply {
            setTextColor(0xFF8E8E93.toInt())
            textSize = 11f
            setPadding(dp(2), dp(1), 0, 0)
        }
        zoneLabel = zone
        infoCol.addView(valueRow)
        infoCol.addView(zone)

        mainRow.addView(iconWrap)
        mainRow.addView(infoCol)

        // 展开面板
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(2), dp(16), dp(14))
            visibility = View.GONE
            alpha = 0f
        }
        this.panel = panel

        val panelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avgStat = panelStat("平均", 1f)
        val maxStat = panelStat("最高", 1f)
        val durStat = panelStat("时长", 1.4f)
        avgText = avgStat.second
        maxText = maxStat.second
        durationText = durStat.second
        panelRow.addView(avgStat.first)
        panelRow.addView(maxStat.first)
        panelRow.addView(durStat.first)

        val track = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(0x22FFFFFF)
            }
            layoutParams = LinearLayout.LayoutParams(dp(104), dp(6)).apply {
                topMargin = dp(8)
            }
        }
        val fill = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(3).toFloat()
                setColor(0xFFFF2D55.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(6))
        }
        zoneBarFill = fill
        track.addView(fill)

        panel.addView(panelRow)
        panel.addView(track)

        container.addView(mainRow)
        container.addView(panel)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(140)
        }

        // 拖拽
        val startX = FloatArray(1)
        val startY = FloatArray(1)
        val initialX = IntArray(1)
        val initialY = IntArray(1)
        val moved = BooleanArray(1)
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val handler = Handler(Looper.getMainLooper())
        var lastClickTime = 0L
        var pendingToggle: Runnable? = null

        container.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX[0] = event.rawX
                    startY[0] = event.rawY
                    initialX[0] = params.x
                    initialY[0] = params.y
                    moved[0] = false
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX[0]
                    val dy = event.rawY - startY[0]
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved[0] = true
                    if (moved[0]) {
                        params.x = initialX[0] + dx.toInt()
                        params.y = initialY[0] + dy.toInt()
                        runCatching { windowManager.updateViewLayout(v, params) }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    if (!moved[0]) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastClickTime < 300) {
                            pendingToggle?.let { handler.removeCallbacks(it) }
                            pendingToggle = null
                            openApp()
                        } else {
                            lastClickTime = now
                            pendingToggle?.let { handler.removeCallbacks(it) }
                            val toggle = Runnable { togglePanel() }
                            pendingToggle = toggle
                            handler.postDelayed(toggle, 300)
                        }
                    }
                    true
                }

                else -> false
            }
        }

        windowManager.addView(container, params)
        floatingView = container
    }

    private fun togglePanel() {
        val panel = panel ?: return
        expanded = !expanded
        if (expanded) {
            panel.visibility = View.VISIBLE
            panel.animate().alpha(1f).setDuration(180).start()
        } else {
            panel.animate().alpha(0f).setDuration(140)
                .withEndAction { panel.visibility = View.GONE }.start()
        }
    }

    private fun openApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        runCatching { context.startActivity(intent) }
    }

    private fun panelStat(label: String, weight: Float): Pair<FrameLayout, TextView> {
        val column = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(52), weight)
        }
        val value = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            text = "--"
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val labelText = TextView(context).apply {
            setTextColor(0xFF8E8E93.toInt())
            textSize = 10f
            text = label
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(1), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        inner.addView(value)
        inner.addView(labelText)
        column.addView(inner)
        return column to value
    }

    private fun zoneLabelText(id: Int): String = when (id) {
        1 -> "恢复区 Z1"
        2 -> "热身区 Z2"
        3 -> "燃脂区 Z3"
        4 -> "有氧区 Z4"
        else -> "极限区 Z5"
    }

    private fun formatDuration(totalSeconds: Long): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun dp(value: Int): Int =
        (value * density).toInt()
}
