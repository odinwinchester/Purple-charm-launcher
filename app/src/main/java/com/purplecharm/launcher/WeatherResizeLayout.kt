package com.purplecharm.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.roundToInt

/** Width snaps to the home column grid; height follows the original artwork ratio. */
internal class WeatherResizeLayout(
    context: Context, image: ImageView, private val columns: Int, initialSpan: Int,
    private val onEditing: (Boolean) -> Unit, private val onCommitted: (Int) -> Unit,
    onOptions: () -> Unit
) : ViewGroup(context) {
    private val handleSize = (24 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private val border = resources.displayMetrics.density.roundToInt().coerceAtLeast(1)
    private val sw = image.drawable.intrinsicWidth
    private val sh = image.drawable.intrinsicHeight
    private val art = WeatherArtworkFrame(context, image, 2)
    private var span = initialSpan.coerceIn(1, columns)
    private var placement = WidgetResizeGeometry.place(0, 0, columns, span, handleSize, sw, sh, border)
    var isEditing = false
        private set
    private var handleDragging = false
    private var startX = 0f
    private var startY = 0f
    private var startSpan = span
    private val leftHandle = handle(-1)
    private val rightHandle = handle(1)
    init {
        clipChildren = true
        addView(art, LayoutParams(-2, -2))
        addView(leftHandle, LayoutParams(handleSize, handleSize))
        addView(rightHandle, LayoutParams(handleSize, handleSize))
        // The artwork child fills this ViewGroup. Handle gestures on the child too:
        // on real Android a long press may target the ImageView instead of the parent.
        val longPress = View.OnLongClickListener { beginEditing(); true }
        val click = View.OnClickListener { if (isEditing) endEditing() else onOptions() }
        setOnClickListener(click)
        setOnLongClickListener(longPress)
        art.setOnClickListener(click)
        art.setOnLongClickListener(longPress)
        image.setOnClickListener(click)
        image.setOnLongClickListener(longPress)
    }
    private fun handle(direction: Int) = TextView(context).apply {
        text = "↔"
        textSize = 14f
        setTextColor(Color.rgb(198, 153, 235))
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { setColor(Color.argb(170, 35, 14, 52)); cornerRadius = handleSize / 2f }
        visibility = View.GONE
        contentDescription = if (direction < 0) "Alça esquerda: arraste para redimensionar" else "Alça direita: arraste para redimensionar"
        setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX; startY = event.rawY; startSpan = placement.span
                    handleDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (handleDragging) {
                        val dx = (event.rawX - startX) * direction
                        val dy = (event.rawY - startY) * sw / sh
                        val delta = if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) dx else dy
                        span = WidgetResizeGeometry.snap(startSpan, delta, placement.cellWidth, columns)
                        requestLayout()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (handleDragging) {
                        handleDragging = false
                        // ACTION_UP may arrive before the next measure/layout pass.
                        // Resolve the latest requested span now, not the previous frame.
                        span = WidgetResizeGeometry.place(this@WeatherResizeLayout.width,
                            this@WeatherResizeLayout.height, columns, span,
                            handleSize, sw, sh, border).span
                        onCommitted(span)
                        requestLayout()
                        parent?.requestDisallowInterceptTouchEvent(false)
                        announceForAccessibility("Widget com $span colunas; proporção original preservada")
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    span = startSpan; handleDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    requestLayout()
                    true
                }
                else -> true
            }
        }
    }
    fun beginEditing() {
        if (isEditing) return
        isEditing = true
        leftHandle.visibility = View.VISIBLE; rightHandle.visibility = View.VISIBLE
        onEditing(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        announceForAccessibility("Redimensionar: arraste as alças laterais. Toque fora ou pressione Voltar para concluir.")
        requestLayout()
    }
    fun endEditing() {
        if (!isEditing) return
        if (handleDragging) { span = startSpan; handleDragging = false }
        isEditing = false
        leftHandle.visibility = View.GONE; rightHandle.visibility = View.GONE
        parent?.requestDisallowInterceptTouchEvent(false)
        onEditing(false)
        requestLayout()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        placement = WidgetResizeGeometry.place(w, h, columns, span, if (isEditing) handleSize else 0, sw, sh, border)
        art.resizedWidth = placement.artWidth
        art.measure(MeasureSpec.makeMeasureSpec(placement.artWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(placement.artHeight, MeasureSpec.EXACTLY))
        for (handle in listOf(leftHandle, rightHandle)) handle.measure(
            MeasureSpec.makeMeasureSpec(handleSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(handleSize, MeasureSpec.EXACTLY))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val x = (placement.column * placement.cellWidth).toInt() + if (isEditing) handleSize else 0
        val y = ((height - art.measuredHeight) / 2).coerceAtLeast(0)
        art.layout(x, y, x + art.measuredWidth, y + art.measuredHeight)
        val handleTop = (y + art.measuredHeight / 2 - handleSize / 2).coerceIn(0, (height - handleSize).coerceAtLeast(0))
        leftHandle.layout(x - handleSize, handleTop, x, handleTop + handleSize)
        rightHandle.layout(x + art.measuredWidth, handleTop, x + art.measuredWidth + handleSize, handleTop + handleSize)
    }
}
