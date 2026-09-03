package eu.kanade.tachiyomi.ui.reader.setting

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReaderSettingsViewModel(
    readerState: StateFlow<ReaderViewModel.State>,
    val onChangeReadingMode: (ReadingMode) -> Unit,
    val onChangeOrientation: (ReaderOrientation) -> Unit,
    val preferences: ReaderPreferences = Injekt.get(),
    val basePreferences: eu.kanade.domain.base.BasePreferences = Injekt.get(),
) : ViewModel() {

    private val _displayIds = MutableStateFlow<List<Int>>(emptyList())
    val displayIds = _displayIds.asStateFlow()

    init {
        val displayManager = Injekt.get<android.app.Application>().getSystemService(
            Context.DISPLAY_SERVICE,
        ) as DisplayManager
        _displayIds.value = displayManager.displays
            .filter { it.displayId != Display.DEFAULT_DISPLAY }
            .map { it.displayId }
    }

    val viewerFlow = readerState
        .map { it.viewer }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val mangaFlow = readerState
        .map { it.manga }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
}
