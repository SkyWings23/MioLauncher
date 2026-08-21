package com.miolauncher.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.sin
import kotlin.random.Random

/**
 * 游戏启动加载覆盖层：炫丽粒子动画 + 动态渐变背景 + 提示文案。
 * - translationZ 较低，不会盖住设置面板 / 悬浮窗 / 弹窗
 * - 游戏渲染出第一帧后淡出并移除
 */
class LoadingOverlayView(context: Context) : FrameLayout(context) {

    private val textView: TextView
    private val subtitleView: TextView
    private val particleView: ParticleView

    init {
        setBackgroundColor(Color.TRANSPARENT)

        // 粒子动画层（底层）
        particleView = ParticleView(context)
        addView(particleView, LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // 中间内容层
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        // logo 徽标：静态居中 logo + 外圈旋转加载环
        val logo = android.widget.ImageView(context).apply {
            setImageDrawable(androidx.core.content.ContextCompat.getDrawable(
                context, com.miolauncher.app.R.mipmap.ic_launcher))
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }
        val logoWrap = FrameLayout(context).apply {
            // logo 居中，占 72%
            addView(logo, FrameLayout.LayoutParams(
                (0.72f * 120.dp()).toInt(), (0.72f * 120.dp()).toInt(),
                android.view.Gravity.CENTER,
            ))
            // 外圈旋转加载环（最外层叠加）
            addView(SpinnerRingView(context), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
            ))
        }
        content.addView(logoWrap, LayoutParams(120.dp(), 120.dp()).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })

        textView = TextView(context).apply {
            text = "正在启动 Minecraft…"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 26.dp(), 0, 0)
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }
        subtitleView = TextView(context).apply {
            textSize = 12f
            setTextColor(Color.argb(200, 180, 230, 180))
            gravity = Gravity.CENTER
            setPadding(0, 8.dp(), 0, 0)
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        content.addView(textView)
        content.addView(subtitleView)

        val lp = LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
        ).apply { gravity = Gravity.CENTER }
        addView(content, lp)
    }

    fun setSubtitle(text: String) {
        subtitleView.text = text
    }

    /** 平滑暂停/恢复粒子动画（Activity 生命周期） */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        particleView.start()
        playEnter()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        particleView.stop()
    }

    /** 进入过渡：平滑淡入 */
    private fun playEnter() {
        alpha = 0f
        animate()
            .alpha(1f)
            .setDuration(350)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .start()
    }

    /**
     * 淡出并移除覆盖层（平滑淡出）。
     */
    fun hide() {
        animate()
            .alpha(0f)
            .setDuration(400)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction {
                visibility = View.GONE
                particleView.stop()
                (parent as? android.view.ViewGroup)?.removeView(this)
            }
            .start()
    }

    private fun Int.dp(): Int = Math.round(this * resources.displayMetrics.density)
}

/** 漂浮绿色光点粒子动画层 */
private class ParticleView(context: Context) : View(context) {
    private class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var radius: Float, var alpha: Float,
        var phase: Float,
    )

    private val particles = ArrayList<Particle>()
    private val rng = Random(System.currentTimeMillis())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var bgShader: LinearGradient? = null
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastNanos = 0L

    // 渐变背景色（缓慢脉动）
    private val colorTop = intArrayOf(
        Color.rgb(8, 40, 18), Color.rgb(4, 24, 12), Color.rgb(10, 46, 22),
    )

    init {
        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, 1000f,
            Color.rgb(8, 40, 18), Color.rgb(2, 12, 8),
            Shader.TileMode.CLAMP,
        )
        for (i in 0 until 46) {
            particles.add(Particle(
                x = rng.nextFloat() * 1080f,
                y = rng.nextFloat() * 2400f,
                vx = (rng.nextFloat() - 0.5f) * 26f,
                vy = -(20f + rng.nextFloat() * 55f),
                radius = 2.5f + rng.nextFloat() * 4.5f,
                alpha = 0.25f + rng.nextFloat() * 0.55f,
                phase = rng.nextFloat() * 6.28f,
            ))
        }
    }

    fun start() {
        if (running) return
        running = true
        lastNanos = 0L
        handler.post(frame)
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.nanoTime()
            val dtMs = if (lastNanos == 0L) 16f
            else (now - lastNanos) / 1_000_000f
            lastNanos = now
            step(dtMs.coerceIn(1f, 50f))
            invalidate()
            handler.postDelayed(this, 16)
        }
    }

    private fun step(dtMs: Float) {
        val scale = dtMs / 16f
        val w = width.toFloat().coerceAtLeast(1080f)
        val h = height.toFloat().coerceAtLeast(2400f)
        for (p in particles) {
            p.x += p.vx * scale
            p.y += p.vy * scale
            p.phase += 0.08f * scale
            // 左右摆动
            p.x += sin(p.phase) * 0.6f * scale
            if (p.y < -20f) { p.y = h + 20f; p.x = rng.nextFloat() * w }
            if (p.x < -20f) p.x = w + 20f
            if (p.x > w + 20f) p.x = -20f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 渐变背景
        if (bgShader == null || width != bgW || height != bgH) {
            bgW = width; bgH = height
            bgShader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                Color.rgb(8, 40, 18), Color.rgb(2, 10, 6),
                Shader.TileMode.CLAMP,
            )
            bgPaint.shader = bgShader
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 粒子
        for (p in particles) {
            val twinkle = p.alpha * (0.7f + 0.3f * sin(p.phase * 1.3f))
            paint.color = Color.argb(
                (255 * twinkle).toInt().coerceIn(0, 255),
                90, 230, 130,
            )
            // 光晕
            paint.shader = RadialGradient(
                p.x, p.y, p.radius * 3f,
                Color.argb((255 * twinkle).toInt().coerceIn(0, 120), 120, 255, 160),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(p.x, p.y, p.radius * 3f, paint)
            paint.shader = null
            // 核心点
            paint.color = Color.argb(
                (255 * twinkle).toInt().coerceIn(0, 255), 170, 255, 190,
            )
            canvas.drawCircle(p.x, p.y, p.radius, paint)
        }
    }

    private var bgW = 0
    private var bgH = 0
}

/** 脉动徽标容器：内部放 logo 图标，周围三层交错放大的半透明圆环 + 图标呼吸缩放 */
/** 旋转加载环：一条带渐隐尾迹的弧线绕中心匀速旋转（Android ObjectAnimator 平滑动画） */
private class SpinnerRingView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val animator = android.animation.ObjectAnimator.ofFloat(this, "rotation", 0f, 360f).apply {
        duration = 1200
        repeatCount = android.animation.ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(width, height) / 2f - 3f.dp(context)
        // 底环（半透明）
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f.dp(context)
        paint.color = Color.argb(60, 110, 255, 150)
        canvas.drawCircle(cx, cy, radius, paint)
        // 进度弧线（前 40% 圆周长，亮绿色）
        paint.color = Color.argb(230, 120, 255, 160)
        canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius,
            -90f, 140f, false, paint)
        // 弧线端点头部（更亮）
        paint.strokeWidth = 4f.dp(context)
        paint.color = Color.argb(255, 190, 255, 200)
        canvas.drawArc(cx - radius, cy - radius, cx + radius, cy + radius,
            -90f + 140f - 10f, 10f, false, paint)
    }

    private fun Float.dp(c: Context): Float = this * c.resources.displayMetrics.density
}
