package com.gordon.common_module.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class SpiderView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, def: Int = 0
) : View(context, attrs, def) {
    var count = 6
    var radius: Float = 0f
    var centerX: Float = 0f
    var centerY: Float = 0f
    var angle = Math.PI * 2 / count.toFloat()
    val mSpiderPath = Path()
    val mSpiderPaint = Paint()
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        radius = w.coerceAtMost(h) / 2 * 0.9f
        centerX = w / 2f
        centerY = h / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawSpider(canvas)
    }

    private fun drawSpider(canvas: Canvas) {
        val rDistance = radius / (count - 1)
        mSpiderPath.reset()
        for (i in 1 until count) {
            mSpiderPath.reset()
            val currR = rDistance * i
            repeat(count) { index ->
                if (index == 0) {
                    mSpiderPath.moveTo(centerX + currR, centerY)
                } else {
                    val x = centerX + currR * cos(angle * index)
                    val y = centerY + currR * sin(angle * index)
                    mSpiderPath.lineTo(x.toFloat(), y.toFloat())
                }
            }
            canvas.drawPath(mSpiderPath, mSpiderPaint)
        }
    }
}