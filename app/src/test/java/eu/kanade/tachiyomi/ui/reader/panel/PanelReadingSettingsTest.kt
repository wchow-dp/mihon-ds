package eu.kanade.tachiyomi.ui.reader.panel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanelReadingSettingsTest {

    @Test
    fun `transition duration is clamped to supported range`() {
        assertEquals(
            PanelReadingSettings.PANEL_TRANSITION_MIN_MILLIS,
            PanelReadingSettings.normalizeTransitionMillis(-20),
        )
        assertEquals(
            PanelReadingSettings.PANEL_TRANSITION_MAX_MILLIS,
            PanelReadingSettings.normalizeTransitionMillis(1200),
        )
    }

    @Test
    fun `transition duration is rounded to slider step`() {
        assertEquals(250, PanelReadingSettings.normalizeTransitionMillis(274))
        assertEquals(300, PanelReadingSettings.normalizeTransitionMillis(275))
    }

    @Test
    fun `focus strength is clamped to supported range`() {
        assertEquals(PanelReadingSettings.PANEL_FOCUS_STRENGTH_MIN, PanelReadingSettings.normalizeFocusStrength(-1))
        assertEquals(PanelReadingSettings.PANEL_FOCUS_STRENGTH_MAX, PanelReadingSettings.normalizeFocusStrength(120))
    }

    @Test
    fun `dim alpha follows focus strength`() {
        assertEquals(0, PanelReadingSettings.dimAlphaForStrength(0))
        assertEquals(255, PanelReadingSettings.dimAlphaForStrength(100))
    }

    @Test
    fun `focus effects expose only stable guided reading options`() {
        assertEquals(
            listOf(PanelFocusEffect.OFF, PanelFocusEffect.DARKEN),
            PanelFocusEffect.entries.toList(),
        )
    }
}
