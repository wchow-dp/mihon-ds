package eu.kanade.tachiyomi.ui.reader.viewer

import kotlin.math.min

object PanelFocusCalculator {

    @Suppress("UNUSED_PARAMETER")
    fun calculateFocus(
        viewWidth: Int,
        viewHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
        panelLeft: Float,
        panelTop: Float,
        panelRight: Float,
        panelBottom: Float,
        minScale: Float,
        maxScale: Float,
        horizontalBias: PanelFocusHorizontalBias = PanelFocusHorizontalBias.CENTER,
        padding: Float = PANEL_FOCUS_PADDING,
        allowOverpan: Boolean = false,
    ): PanelFocusTarget {
        val panelWidth = panelRight - panelLeft
        val panelHeight = panelBottom - panelTop
        val scale = calculateScale(
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            panelWidth = panelWidth,
            panelHeight = panelHeight,
            minScale = minScale,
            maxScale = maxScale,
            padding = padding,
        )

        if (viewWidth <= 0 || viewHeight <= 0 || panelWidth <= 0f || panelHeight <= 0f) {
            return PanelFocusTarget(
                scale = scale,
                centerX = (panelLeft + panelRight) / 2f,
                centerY = (panelTop + panelBottom) / 2f,
            )
        }

        val visibleWidth = viewWidth / scale
        val visibleHeight = viewHeight / scale
        val centerX = (panelLeft + panelRight) / 2f
        val centerY = (panelTop + panelBottom) / 2f

        return PanelFocusTarget(
            scale = scale,
            centerX = centerX.clampCenter(visibleWidth, imageWidth, allowOverpan),
            centerY = centerY.clampCenter(visibleHeight, imageHeight, allowOverpan),
        )
    }

    fun calculateScale(
        viewWidth: Int,
        viewHeight: Int,
        panelWidth: Float,
        panelHeight: Float,
        minScale: Float,
        maxScale: Float,
        padding: Float = PANEL_FOCUS_PADDING,
    ): Float {
        if (viewWidth <= 0 || viewHeight <= 0 || panelWidth <= 0f || panelHeight <= 0f) {
            return minScale
        }

        val paddedWidth = panelWidth * padding
        val paddedHeight = panelHeight * padding
        val widthFitScale = viewWidth.toFloat() / paddedWidth
        val heightFitScale = viewHeight.toFloat() / paddedHeight
        val fitScale = min(widthFitScale, heightFitScale)

        return fitScale.coerceIn(minScale, maxScale)
    }

    fun shouldRefreshForViewportChange(
        oldWidth: Int,
        oldHeight: Int,
        newWidth: Int,
        newHeight: Int,
    ): Boolean {
        return oldWidth != newWidth || oldHeight != newHeight
    }

    private fun Float.clampCenter(visibleSize: Float, imageSize: Int, allowOverpan: Boolean): Float {
        if (imageSize <= 0) return this

        val imageSizeFloat = imageSize.toFloat()
        if (allowOverpan) return coerceIn(0f, imageSizeFloat)

        if (visibleSize >= imageSizeFloat) return imageSizeFloat / 2f

        val halfVisibleSize = visibleSize / 2f
        return coerceIn(halfVisibleSize, imageSizeFloat - halfVisibleSize)
    }

}

data class PanelFocusTarget(
    val scale: Float,
    val centerX: Float,
    val centerY: Float,
)

enum class PanelFocusHorizontalBias {
    CENTER,
    START_LEFT,
    START_RIGHT,
}

const val PANEL_FOCUS_PADDING = 1.12f
