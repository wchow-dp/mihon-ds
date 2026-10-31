package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode

object ReaderPanelReadingMode {
    fun isActive(
        panelReadingEnabled: Boolean,
        readingModePreference: Int,
    ): Boolean {
        return panelReadingEnabled && ReadingMode.isPagerType(readingModePreference)
    }
}
