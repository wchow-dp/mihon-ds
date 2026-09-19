package eu.kanade.tachiyomi.ui.reader.viewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import eu.kanade.tachiyomi.ui.reader.panel.PanelFocusEffect
import eu.kanade.tachiyomi.ui.reader.panel.PanelReadingSettings

class PanelHighlightOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class PanelRegion(
        val panelIndex: Int,
        val number: Int?,
        val bounds: RectF,
        val active: Boolean,
    )

    var panelRegions: List<PanelRegion> = emptyList()
        set(value) {
            field = value.map { region ->
                region.copy(bounds = RectF(region.bounds))
            }
            invalidate()
        }

    var focusEffect: PanelFocusEffect = PanelFocusEffect.DARKEN

    /**
     * Whether to draw the panel outlines and numbers (the "highlights").
     *
     * Independent of [focusEffect]: the backdrop (dim) and the highlights are separate settings,
     * so turning the backdrop off must not take the outlines with it, and vice versa. Correction
     * mode always draws them regardless, since you cannot reorder panels you cannot see.
     */
    var showHighlights: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var focusStrength: Int = PanelReadingSettings.PANEL_FOCUS_STRENGTH_DEFAULT
        set(value) {
            val normalized = PanelReadingSettings.normalizeFocusStrength(value)
            if (field == normalized) return
            field = normalized
            invalidate()
        }

    var isCorrectionMode: Boolean = false
        set(value) {
            field = value
            if (!value) {
                draggedPanelIndex = null
                targetPanelIndex = null
            }
            invalidate()
        }

    var onPanelClick: ((Int) -> Unit)? = null
    var onSwapPanels: ((Int, Int) -> Unit)? = null

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val activeAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(115, 58, 137, 255)
        style = Paint.Style.STROKE
        strokeWidth = 8f * resources.displayMetrics.density
    }

    private val activeOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 90, 156, 255)
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val inactiveOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(185, 245, 247, 250)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val targetOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val numberBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 28, 32, 38)
        style = Paint.Style.FILL
    }

    private val activeNumberBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 58, 137, 255)
        style = Paint.Style.FILL
    }

    private val numberTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 12f * resources.displayMetrics.scaledDensity
        typeface = Typeface.DEFAULT_BOLD
    }

    private val dimPath = Path()
    private val cornerRadius = 6f
    private val numberBounds = RectF()

    private var pressedPanelIndex: Int? = null
    private var draggedPanelIndex: Int? = null
    private var targetPanelIndex: Int? = null
    private var dragX: Float = 0f
    private var dragY: Float = 0f

    override fun onDraw(canvas: Canvas) {
        if (panelRegions.isEmpty()) return
        super.onDraw(canvas)

        // The backdrop and the outlines are drawn independently. drawFocusEffect already does
        // nothing when the backdrop is off, and the outlines/numbers are gated on showHighlights,
        // so each setting is honoured on its own instead of one switching off the other.
        val activeRegion = panelRegions.firstOrNull { it.active }
        if (!isCorrectionMode) {
            activeRegion?.let { drawFocusEffect(canvas, it) }
        }

        if (!showHighlights && !isCorrectionMode) return

        panelRegions.forEach { region ->
            if (region.panelIndex != draggedPanelIndex) {
                val paint = when {
                    region.panelIndex == targetPanelIndex -> targetOutlinePaint
                    region.active && !isCorrectionMode -> activeOutlinePaint
                    else -> inactiveOutlinePaint
                }
                canvas.drawRoundRect(region.bounds, cornerRadius, cornerRadius, paint)

                if (region.active && !isCorrectionMode) {
                    canvas.drawRoundRect(region.bounds, cornerRadius, cornerRadius, activeAccentPaint)
                }

                drawNumber(canvas, region)
            }
        }

        // Draw dragged panel last (on top)
        draggedPanelIndex?.let { idx ->
            val region = panelRegions.firstOrNull { it.panelIndex == idx } ?: return@let
            drawNumberAt(canvas, region, dragX, dragY)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val hitRegion = panelRegions
            .asReversed()
            .firstOrNull { it.bounds.contains(event.x, event.y) }

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedPanelIndex = hitRegion?.panelIndex
                if (isCorrectionMode && hitRegion != null) {
                    draggedPanelIndex = hitRegion.panelIndex
                    dragX = event.x; dragY = event.y
                    invalidate()
                }
                hitRegion != null
            }
            MotionEvent.ACTION_MOVE -> {
                if (isCorrectionMode && draggedPanelIndex != null) {
                    dragX = event.x; dragY = event.y
                    targetPanelIndex = hitRegion?.panelIndex?.takeIf { it != draggedPanelIndex }
                    invalidate()
                    true
                } else {
                    false
                }
            }
            MotionEvent.ACTION_UP -> {
                val pressedIndex = pressedPanelIndex
                val draggedIndex = draggedPanelIndex
                val targetIndex = targetPanelIndex

                pressedPanelIndex = null
                draggedPanelIndex = null
                targetPanelIndex = null

                if (isCorrectionMode && draggedIndex != null && targetIndex != null) {
                    onSwapPanels?.invoke(draggedIndex, targetIndex)
                    invalidate()
                    true
                } else if (!isCorrectionMode && pressedIndex != null && hitRegion?.panelIndex == pressedIndex) {
                    onPanelClick?.invoke(pressedIndex)
                    performClick()
                    true
                } else {
                    invalidate()
                    false
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedPanelIndex = null
                draggedPanelIndex = null
                targetPanelIndex = null
                invalidate()
                false
            }
            else -> pressedPanelIndex != null || draggedPanelIndex != null
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun drawNumber(canvas: Canvas, region: PanelRegion) {
        val left = region.bounds.left + 6f * resources.displayMetrics.density
        val top = region.bounds.top + 6f * resources.displayMetrics.density
        drawNumberAt(canvas, region, left, top, isAbsolute = false)
    }

    private fun drawNumberAt(canvas: Canvas, region: PanelRegion, x: Float, y: Float, isAbsolute: Boolean = true) {
        val number = region.number ?: return
        val text = number.toString()
        val horizontalPadding = 7f * resources.displayMetrics.density
        val badgeHeight = 20f * resources.displayMetrics.density
        val badgeWidth = maxOf(
            badgeHeight,
            numberTextPaint.measureText(text) + horizontalPadding * 2f,
        )

        val left = if (isAbsolute) x - badgeWidth / 2f else x
        val top = if (isAbsolute) y - badgeHeight / 2f else y
        numberBounds.set(left, top, left + badgeWidth, top + badgeHeight)

        canvas.drawRoundRect(
            numberBounds,
            badgeHeight / 2f,
            badgeHeight / 2f,
            if (region.active && !isCorrectionMode) activeNumberBackgroundPaint else numberBackgroundPaint,
        )

        val baseline = numberBounds.centerY() - (numberTextPaint.descent() + numberTextPaint.ascent()) / 2f
        canvas.drawText(text, numberBounds.centerX(), baseline, numberTextPaint)
    }

    private fun drawFocusEffect(canvas: Canvas, activeRegion: PanelRegion) {
        if (focusStrength <= 0 || focusEffect == PanelFocusEffect.OFF) return

        buildOutsidePath(activeRegion.bounds)
        when (focusEffect) {
            PanelFocusEffect.OFF -> Unit
            PanelFocusEffect.DARKEN -> drawDim(canvas, alpha = PanelReadingSettings.dimAlphaForStrength(focusStrength))
        }
    }

    private fun buildOutsidePath(activeBounds: RectF) {
        dimPath.reset()
        dimPath.fillType = Path.FillType.EVEN_ODD
        dimPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        dimPath.addRoundRect(activeBounds, cornerRadius, cornerRadius, Path.Direction.CW)
    }

    private fun drawDim(canvas: Canvas, alpha: Int) {
        if (alpha <= 0) return

        dimPaint.color = Color.argb(alpha, 0, 0, 0)
        canvas.drawPath(dimPath, dimPaint)
    }
}
