package com.yin.cpufreq2.feature.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.yin.cpufreq2.core.cpu.CoreUsage
import com.yin.cpufreq2.core.cpu.getMaxCpuFreqKhz
import com.yin.cpufreq2.core.locale.ConfigManager
import kotlinx.coroutines.Runnable
import kotlin.math.abs

@Suppress("DEPRECATION")
class CpuBarView(
    context: Context,
    private val params: WindowManager.LayoutParams,
    private val wm: WindowManager
) : View(context) {

    private var usages: List<CoreUsage> = emptyList()
    private var scaleFactor: Float = 1.0f
    private var maxFreqHeightRaw: Float = 160f
    private var rotationAngle: Int = 0
    private var reverseOrder: Boolean = false

    private val barWidth = 32f
    private val spacing = 12f
    private val bottomPadding = 0f
    private val maxFreqKhz = getMaxCpuFreqKhz().toFloat().coerceAtLeast(1000f)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var isDragging = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var downParamsX = 0
    private var downParamsY = 0
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: kotlinx.coroutines.Runnable? = null
    private var isLongPressed = false

    enum class DrawMode {
        BAR_CHART,
        TEXT_LIST
    }
    private var currentMode = DrawMode.BAR_CHART


    init {
        loadConfig()
    }

    private fun loadConfig() {
        scaleFactor = ConfigManager.getScale()
        maxFreqHeightRaw = ConfigManager.getMaxHeight()
        rotationAngle = ConfigManager.getRotation()
        reverseOrder = ConfigManager.isReverseOrder()
    }


    fun updateData(newData: List<CoreUsage>) {
        this.usages = newData
        requestLayout()
        updateWindowSize()
        invalidate()
    }

    fun updateConfig() {
        this.scaleFactor = ConfigManager.getScale()
        this.maxFreqHeightRaw = ConfigManager.getMaxHeight()
        this.rotationAngle = ConfigManager.getRotation()
        this.reverseOrder = ConfigManager.isReverseOrder()
        requestLayout()
        updateWindowSize()
        invalidate()
    }

    private fun getCoreCount(): Int = if (usages.isNotEmpty()) usages.size else Runtime.getRuntime().availableProcessors()

    private fun measureMaxTextWidth(): Float {
        val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 32f
            typeface = Typeface.MONOSPACE
        }
        var maxWidth = 0f
        for (pos in 0 until getCoreCount()) {
            val coreIndex = if (reverseOrder) getCoreCount() - 1 - pos else pos
            val usage = if (coreIndex < usages.size) usages[coreIndex] else null
            val text = if (usage != null && usage.isOnline) {
                val percent = usage.usagePercent.toInt().coerceIn(0, 100)
                val percentStr = "%02d".format(percent)
                val freqMHz = (usage.currentFreqKhz / 1000).toInt()
                "[$percentStr]@${freqMHz}"
            } else {
                "[x]"
            }
            val w = measurePaint.measureText(text)
            if (w > maxWidth) maxWidth = w
        }
        return maxWidth + 45f
    }

    private fun getContentWidth(): Float {
        return if (currentMode == DrawMode.BAR_CHART) {
            val cores = getCoreCount().coerceAtLeast(1)
            cores * (barWidth + spacing) + spacing
        } else {
            measureMaxTextWidth()
        }
    }

    private fun getContentHeight(): Float {
        return if (currentMode == DrawMode.BAR_CHART) {
            maxFreqHeightRaw + bottomPadding
        } else {
            val coreCount = getCoreCount()
            val lineHeight = 40f
            val startY = 0f
            startY + coreCount * lineHeight + 40f
        }
    }

    private fun getViewSize(): Pair<Int, Int> {
        val w = (getContentWidth() * scaleFactor).toInt()
        val h = (getContentHeight() * scaleFactor).toInt()
        return if (currentMode == DrawMode.BAR_CHART && (rotationAngle == 90 || rotationAngle == 270)) {
            h to w
        } else {
            w to h
        }
    }

    private fun updateWindowSize() {
        val (newWidth, newHeight) = getViewSize()
        if (params.width != newWidth || params.height != newHeight) {
            params.width = newWidth
            params.height = newHeight
            wm.updateViewLayout(this, params)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val (w, h) = getViewSize()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        when (currentMode) {
            DrawMode.BAR_CHART -> drawBarChart(canvas)
            DrawMode.TEXT_LIST -> drawTextList(canvas)
        }
    }

    private fun drawBarChart(canvas: Canvas) {
        canvas.save()

        if (rotationAngle == 90) {
            canvas.translate(width.toFloat(), 0f)
            canvas.rotate(90f)
        } else if (rotationAngle == 270) {
            canvas.translate(0f, height.toFloat())
            canvas.rotate(270f)
        } else {
            canvas.rotate(rotationAngle.toFloat(), width / 2f, height / 2f)
        }

        val bgWidth = if (rotationAngle == 90 || rotationAngle == 270) height.toFloat() else width.toFloat()
        val bgHeight = if (rotationAngle == 90 || rotationAngle == 270) width.toFloat() else height.toFloat()
        paint.color = Color.parseColor("#33000000")
        canvas.drawRect(0f, 0f, bgWidth, bgHeight, paint)

        val coreCount = getCoreCount()
        val scale = scaleFactor

        for (pos in 0 until coreCount) {
            val coreIndex = if (reverseOrder) coreCount - 1 - pos else pos
            val usage = if (coreIndex < usages.size) usages[coreIndex] else CoreUsage(coreIndex, 0f, 0L, false)

            val leftRaw = spacing + pos * (barWidth + spacing)
            val widthRaw = barWidth
            val left = leftRaw * scale
            val right = (leftRaw + widthRaw) * scale
            val barBottom = maxFreqHeightRaw * scale
            val barTop = (maxFreqHeightRaw * (1 - (usage.currentFreqKhz / maxFreqKhz).coerceIn(0.1f, 1f))) * scale

            if (usage.isOnline) {
                val statusColor = when {
                    usage.usagePercent > 80 -> Color.parseColor("#FF5252")
                    usage.usagePercent > 40 -> Color.parseColor("#FFD740")
                    else -> Color.parseColor("#69F0AE")
                }
                paint.color = Color.parseColor("#1AFFFFFF")
                canvas.drawRect(left, barTop, right, barBottom, paint)
                val usageHeight = (barBottom - barTop) * (usage.usagePercent / 100f)
                paint.color = statusColor
                canvas.drawRect(left, barBottom - usageHeight, right, barBottom, paint)
                strokePaint.color = statusColor
                canvas.drawRect(left, barTop, right, barBottom, strokePaint)
            } else {
                paint.color = Color.parseColor("#33888888")
                val offlineBarTop = maxFreqHeightRaw * scale - 20f * scale
                canvas.drawRect(left, offlineBarTop, right, barBottom, paint)
            }
        }

        canvas.restore()
    }

    private fun drawTextList(canvas: Canvas) {
        canvas.save()
        val coreCount = getCoreCount()
        val scale = scaleFactor

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 32f * scale
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
            typeface = Typeface.MONOSPACE
        }

        val measurePaint = Paint().apply {
            textSize = 32f * scale
            typeface = Typeface.MONOSPACE
        }

        val paddingUnscaled = 20f
        val lineHeightUnscaled = 40f

        val padding = paddingUnscaled * scale
        val lineHeight = lineHeightUnscaled * scale

        var maxTextWidth = 0f
        for (pos in 0 until coreCount) {
            val coreIndex = if (reverseOrder) coreCount - 1 - pos else pos
            val usage = if (coreIndex < usages.size) usages[coreIndex] else null
            val text = if (usage != null && usage.isOnline) {
                val percent = usage.usagePercent.toInt().coerceIn(0, 100)
                val percentStr = "%02d".format(percent)
                val freqMHz = (usage.currentFreqKhz / 1000).toInt()
                "[$percentStr]@${freqMHz}"
            } else {
                "[x]"
            }
            val w = measurePaint.measureText(text)
            if (w > maxTextWidth) maxTextWidth = w
        }

        val bgWidth = maxTextWidth + padding * 2
        val bgHeight = coreCount * lineHeight + padding * 2

        val bgPaint = Paint().apply {
            color = Color.parseColor("#AA000000")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val cornerRadius = 16f * scale
        canvas.drawRoundRect(0f, 0f, bgWidth, bgHeight, cornerRadius, cornerRadius, bgPaint)

        for (pos in 0 until coreCount) {
            val coreIndex = if (reverseOrder) coreCount - 1 - pos else pos
            val usage = if (coreIndex < usages.size) usages[coreIndex] else null

            val text = if (usage != null && usage.isOnline) {
                val percent = usage.usagePercent.toInt().coerceIn(0, 100)
                val percentStr = "%02d".format(percent)
                val freqMHz = (usage.currentFreqKhz / 1000).toInt()
                "[$percentStr]@${freqMHz}"
            } else {
                "[x]"
            }

            val x = padding
            val y = padding + pos * lineHeight + textPaint.textSize * 0.8f
            canvas.drawText(text, x, y, textPaint)
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downParamsX = params.x
                downParamsY = params.y
                isDragging = false
                isLongPressed = false

                longPressRunnable = Runnable {
                    isLongPressed = true
                    onLongPress()
                }
                handler.postDelayed(longPressRunnable!!, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                    cancelLongPress()
                }

                if (isDragging) {
                    params.x = downParamsX + dx.toInt()
                    params.y = downParamsY + dy.toInt()
                    wm.updateViewLayout(this, params)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                cancelLongPress()

                if (!isDragging) {
                    if (isLongPressed) {
                       //TODO
                    } else {

                        currentMode = if (currentMode == DrawMode.BAR_CHART) DrawMode.TEXT_LIST else DrawMode.BAR_CHART
                        requestLayout()
                        updateWindowSize()
                        invalidate()
                    }
                }
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun cancelLongPress() {
        longPressRunnable?.let {
            handler.removeCallbacks(it)
            longPressRunnable = null
        }
    }

    private fun onLongPress() {
        //TODO
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelLongPress()
    }

}