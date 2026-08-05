package com.naeem.screenrecorder

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class TouchOverlayView(context: Context) : View(context) {

    private val paint = Paint().apply {
        color = Color.parseColor("#66FFFFFF")
        isAntiAlias = true
    }

    private var touchX = 0f
    private var touchY = 0f
    private var radius = 0f
    private var visible = false

    fun showTouch(x: Float, y: Float) {
        touchX = x
        touchY = y
        visible = true
        val anim = ValueAnimator.ofFloat(0f, 40f)
        anim.duration = 300
        anim.addUpdateListener {
            radius = it.animatedValue as Float
            invalidate()
        }
        anim.start()
        postDelayed({ visible = false; invalidate() }, 350)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visible) {
            canvas.drawCircle(touchX, touchY, radius, paint)
        }
    }
}
