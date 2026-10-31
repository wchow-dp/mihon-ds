package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderPanelReadingModeTest {

    @Test
    fun `guided reading is active for pager modes when enabled`() {
        assertTrue(
            ReaderPanelReadingMode.isActive(
                panelReadingEnabled = true,
                readingModePreference = ReadingMode.RIGHT_TO_LEFT.flagValue,
            ),
        )
        assertTrue(
            ReaderPanelReadingMode.isActive(
                panelReadingEnabled = true,
                readingModePreference = ReadingMode.VERTICAL.flagValue,
            ),
        )
    }

    @Test
    fun `guided reading is inactive for webtoon modes even when enabled`() {
        assertFalse(
            ReaderPanelReadingMode.isActive(
                panelReadingEnabled = true,
                readingModePreference = ReadingMode.WEBTOON.flagValue,
            ),
        )
        assertFalse(
            ReaderPanelReadingMode.isActive(
                panelReadingEnabled = true,
                readingModePreference = ReadingMode.CONTINUOUS_VERTICAL.flagValue,
            ),
        )
    }

    @Test
    fun `guided reading is inactive when disabled`() {
        assertFalse(
            ReaderPanelReadingMode.isActive(
                panelReadingEnabled = false,
                readingModePreference = ReadingMode.LEFT_TO_RIGHT.flagValue,
            ),
        )
    }
}
