package mihon.core.dualscreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.core.screen.Screen
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow

object DualScreenState {
    /**
     * Controls the content displayed on the secondary screen.
     * Null means the secondary screen is showing the default dashboard.
     */
    private val _activeScreen = MutableStateFlow<Screen?>(null)
    val activeScreen = _activeScreen.asStateFlow()

    /**
     * Events sent from the secondary screen back to the primary activity.
     * Used for navigation that must happen on the main screen context.
     */
    private val _mainScreenEvents = Channel<MainScreenEvent>(
        capacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val mainScreenEvents = _mainScreenEvents.receiveAsFlow()

    private val _rotationEvents = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val rotationEvents = _rotationEvents.asSharedFlow()

    val LocalNavigateUp = staticCompositionLocalOf<(() -> Unit)?> { null }

    @Composable
    fun navigateUpOr(fallback: () -> Unit): () -> Unit {
        return LocalNavigateUp.current ?: fallback
    }

    /**
     * Payload for the source-filter companion screen.
     *
     * Voyager Screens are Serializable and Android writes the navigator stack into the
     * instance-state Bundle, so a Screen cannot hold a FilterList or lambdas — doing so
     * threw NotSerializableException whenever the app was backgrounded with filters open.
     * The screen carries only a sourceId and looks the rest up here.
     */
    data class SourceFilterContext(
        val sourceId: Long,
        val filters: FilterList,
        val onReset: () -> Unit,
        val onFilter: () -> Unit,
        val onUpdate: (FilterList) -> Unit,
    )

    private val _sourceFilterContext = MutableStateFlow<SourceFilterContext?>(null)
    val sourceFilterContext = _sourceFilterContext.asStateFlow()

    fun setSourceFilterContext(context: SourceFilterContext?) {
        _sourceFilterContext.value = context
    }

    fun openScreen(screen: Screen) {
        _activeScreen.value = screen
    }

    fun openOnMainScreen(screen: Screen) {
        _mainScreenEvents.trySend(MainScreenEvent.OpenScreen(screen))
    }

    fun triggerRotationUpdate() {
        _rotationEvents.tryEmit(Unit)
    }

    fun close() {
        _activeScreen.value = null
        _sourceFilterContext.value = null
    }

    sealed interface MainScreenEvent {
        data class OpenScreen(val screen: Screen) : MainScreenEvent
    }
}