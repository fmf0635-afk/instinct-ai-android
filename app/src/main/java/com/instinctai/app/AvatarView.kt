package com.instinctai.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

class AvatarView(context: Context, private val female: Boolean) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pulse = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1500
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
    }

    init {
        contentDescription = if (female) "Animated female AI avatar" else "Animated male AI avatar"
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h * .43f + pulse * 3f
        val scale = (minOf(w, h) / 210f).coerceAtLeast(.65f)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)

        paint.color = 0x3370D8EF
        canvas.drawCircle(0f, 0f, 100f + pulse * 4f, paint)
        paint.color = 0xFF263D56.toInt()
        canvas.drawOval(RectF(-83f, 56f, 83f, 178f), paint)
        paint.color = 0xFF70D8EF.toInt()
        canvas.drawRoundRect(RectF(-8f, 60f, 8f, 120f), 8f, 8f, paint)

        paint.color = 0xFFE9B995.toInt()
        canvas.drawOval(RectF(-58f, -76f, 58f, 68f), paint)
        paint.color = 0xFF352A2C.toInt()
        if (female) {
            canvas.drawOval(RectF(-66f, -88f, 66f, 15f), paint)
            canvas.drawRoundRect(RectF(-67f, -20f, -48f, 100f), 12f, 12f, paint)
            canvas.drawRoundRect(RectF(48f, -20f, 67f, 100f), 12f, 12f, paint)
        } else {
            canvas.drawOval(RectF(-59f, -85f, 59f, -28f), paint)
        }

        paint.color = 0xFF29242A.toInt()
        canvas.drawCircle(-22f, -8f, 5f, paint)
        canvas.drawCircle(22f, -8f, 5f, paint)
        paint.color = 0xFFB76E68.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3.5f
        canvas.drawArc(RectF(-14f, 12f, 14f, 34f), 15f, 150f, false, paint)
        paint.style = Paint.Style.FILL
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }
}
