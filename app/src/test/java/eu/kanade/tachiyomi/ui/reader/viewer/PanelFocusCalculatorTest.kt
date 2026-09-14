package eu.kanade.tachiyomi.ui.reader.viewer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanelFocusCalculatorTest {

    @Test
    fun `balanced panels use full-panel fit scale`() {
        val scale = PanelFocusCalculator.calculateScale(
            viewWidth = 1000,
            viewHeight = 1000,
            panelWidth = 400f,
            panelHeight = 500f,
            minScale = 0.5f,
            maxScale = 8f,
        )

        assertEquals(1000f / (500f * 1.12f), scale, 0.001f)
    }

    @Test
    fun `tall narrow panels use full-panel fit scale`() {
        val fitScale = 1200f / (1800f * 1.12f)
        val scale = PanelFocusCalculator.calculateScale(
            viewWidth = 1600,
            viewHeight = 1200,
            panelWidth = 360f,
            panelHeight = 1800f,
            minScale = 0.5f,
            maxScale = 8f,
        )

        assertEquals(fitScale, scale, 0.001f)
    }

    @Test
    fun `wide short panels use full-panel fit scale`() {
        val fitScale = 1000f / (1800f * 1.12f)
        val scale = PanelFocusCalculator.calculateScale(
            viewWidth = 1000,
            viewHeight = 1600,
            panelWidth = 1800f,
            panelHeight = 360f,
            minScale = 0.1f,
            maxScale = 8f,
        )

        assertEquals(fitScale, scale, 0.001f)
    }

    @Test
    fun `wide panels on portrait viewport stay full-panel fit`() {
        val fullWidthScale = 1000f / (1800f * 1.12f)
        val scale = PanelFocusCalculator.calculateScale(
            viewWidth = 1000,
            viewHeight = 1600,
            panelWidth = 1800f,
            panelHeight = 900f,
            minScale = 0.1f,
            maxScale = 8f,
        )

        assertEquals(fullWidthScale, scale, 0.001f)
    }

    @Test
    fun `tall panels on landscape viewport stay full-panel fit`() {
        val fullHeightScale = 1000f / (1800f * 1.12f)
        val scale = PanelFocusCalculator.calculateScale(
            viewWidth = 1600,
            viewHeight = 1000,
            panelWidth = 900f,
            panelHeight = 1800f,
            minScale = 0.1f,
            maxScale = 8f,
        )

        assertEquals(fullHeightScale, scale, 0.001f)
    }

    @Test
    fun `wide panels matching landscape viewport stay full panel fit`() {
        val fullPanelScale = 1600f / (1800f * 1.12f)
        val scale = PanelFocusCalculator.calculateScale(
            viewWidth = 1600,
            viewHeight = 1000,
            panelWidth = 1800f,
            panelHeight = 900f,
            minScale = 0.5f,
            maxScale = 8f,
        )

        assertEquals(fullPanelScale, scale, 0.001f)
    }

    @Test
    fun `wide panel focus remains centered even with reading direction bias`() {
        val focus = PanelFocusCalculator.calculateFocus(
            viewWidth = 1000,
            viewHeight = 1600,
            imageWidth = 2000,
            imageHeight = 1200,
            panelLeft = 100f,
            panelTop = 100f,
            panelRight = 1900f,
            panelBottom = 1000f,
            minScale = 0.5f,
            maxScale = 8f,
            horizontalBias = PanelFocusHorizontalBias.START_RIGHT,
        )

        assertEquals(1000f, focus.centerX, 0.001f)
    }

    @Test
    fun `tall panel focus remains centered on landscape viewport`() {
        val focus = PanelFocusCalculator.calculateFocus(
            viewWidth = 1600,
            viewHeight = 1000,
            imageWidth = 1200,
            imageHeight = 2000,
            panelLeft = 100f,
            panelTop = 100f,
            panelRight = 1000f,
            panelBottom = 1900f,
            minScale = 0.5f,
            maxScale = 8f,
            horizontalBias = PanelFocusHorizontalBias.CENTER,
        )

        assertEquals(1000f, focus.centerY, 0.001f)
    }

    @Test
    fun `edge panel focus can target panel center when overpan is allowed`() {
        val focus = PanelFocusCalculator.calculateFocus(
            viewWidth = 1000,
            viewHeight = 1600,
            imageWidth = 2000,
            imageHeight = 3000,
            panelLeft = 0f,
            panelTop = 900f,
            panelRight = 240f,
            panelBottom = 1400f,
            minScale = 0.1f,
            maxScale = 8f,
            allowOverpan = true,
        )

        assertEquals(120f, focus.centerX, 0.001f)
    }

    @Test
    fun `top edge panel focus can target panel center when overpan is allowed`() {
        val focus = PanelFocusCalculator.calculateFocus(
            viewWidth = 1000,
            viewHeight = 1600,
            imageWidth = 2000,
            imageHeight = 3000,
            panelLeft = 600f,
            panelTop = 0f,
            panelRight = 1400f,
            panelBottom = 240f,
            minScale = 0.1f,
            maxScale = 8f,
            allowOverpan = true,
        )

        assertEquals(120f, focus.centerY, 0.001f)
    }

    @Test
    fun `wide short panels on short-wide viewport stay full-panel fit`() {
        val fullWidthScale = 1600f / (1800f * 1.12f)
        val scale = PanelFocusCalculator.calculateScale(
            viewWidth = 1600,
            viewHeight = 700,
            panelWidth = 1800f,
            panelHeight = 360f,
            minScale = 0.1f,
            maxScale = 8f,
        )

        assertEquals(fullWidthScale, scale, 0.001f)
    }

    @Test
    fun `tall panels on short-wide viewport stay full-panel fit`() {
        val fullHeightScale = 700f / (1800f * 1.12f)
        val scale = PanelFocusCalculator.calculateScale(
            viewWidth = 1600,
            viewHeight = 700,
            panelWidth = 900f,
            panelHeight = 1800f,
            minScale = 0.1f,
            maxScale = 8f,
        )

        assertEquals(fullHeightScale, scale, 0.001f)
    }

    @Test
    fun `viewport width change requires panel refresh`() {
        val shouldRefresh = PanelFocusCalculator.shouldRefreshForViewportChange(
            oldWidth = 1000,
            oldHeight = 1600,
            newWidth = 1600,
            newHeight = 1600,
        )

        assertEquals(true, shouldRefresh)
    }

    @Test
    fun `viewport height change requires panel refresh`() {
        val shouldRefresh = PanelFocusCalculator.shouldRefreshForViewportChange(
            oldWidth = 1000,
            oldHeight = 1600,
            newWidth = 1000,
            newHeight = 900,
        )

        assertEquals(true, shouldRefresh)
    }

    @Test
    fun `unchanged viewport does not require panel refresh`() {
        val shouldRefresh = PanelFocusCalculator.shouldRefreshForViewportChange(
            oldWidth = 1000,
            oldHeight = 1600,
            newWidth = 1000,
            newHeight = 1600,
        )

        assertEquals(false, shouldRefresh)
    }
}
