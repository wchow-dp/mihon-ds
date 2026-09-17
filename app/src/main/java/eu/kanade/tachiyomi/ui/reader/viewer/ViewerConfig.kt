package eu.kanade.tachiyomi.ui.reader.viewer

import eu.kanade.tachiyomi.ui.reader.panel.PanelFocusEffect
import eu.kanade.tachiyomi.ui.reader.panel.PanelReadingSettings
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tachiyomi.core.common.preference.Preference

/**
 * Common configuration for all viewers.
 */
abstract class ViewerConfig(readerPreferences: ReaderPreferences, private val scope: CoroutineScope) {

    var imagePropertyChangedListener: (() -> Unit)? = null

    var navigationModeChangedListener: (() -> Unit)? = null

    var panelReadingDisplayChangedListener: (() -> Unit)? = null

    var tappingInverted = ReaderPreferences.TappingInvertMode.NONE
    var longTapEnabled = true
    var usePageTransitions = false
    var doubleTapAnimDuration = 500
    var panelReadingTransitionDuration = PanelReadingSettings.PANEL_TRANSITION_DEFAULT_MILLIS
    var panelReadingFocusEffect = PanelFocusEffect.DARKEN
    var panelReadingFocusStrength = PanelReadingSettings.PANEL_FOCUS_STRENGTH_DEFAULT
    var panelReadingPrimaryOverlay = true
    var volumeKeysEnabled = false
    var volumeKeysInverted = false
    var alwaysShowChapterTransition = true
    var navigationMode = 0
        protected set

    var forceNavigationOverlay = false

    var navigationOverlayOnStart = false

    var dualPageSplit = false
        protected set

    var dualPageInvert = false
        protected set

    var dualPageRotateToFit = false
        protected set

    var dualPageRotateToFitInvert = false
        protected set

    var reshapeWidePagesOnTallScreensOnly = true
        protected set

    var reshapeWidePagesChangedListener: (() -> Unit)? = null

    abstract var navigator: ViewerNavigation
        protected set

    init {
        readerPreferences.readWithLongTap
            .register({ longTapEnabled = it })

        readerPreferences.pageTransitions
            .register({ usePageTransitions = it })

        readerPreferences.doubleTapAnimSpeed
            .register({ doubleTapAnimDuration = it })

        readerPreferences.panelReadingTransitionMillis()
            .register(
                { panelReadingTransitionDuration = PanelReadingSettings.normalizeTransitionMillis(it) },
                { panelReadingDisplayChangedListener?.invoke() },
            )

        readerPreferences.panelReadingFocusEffect()
            .register(
                { panelReadingFocusEffect = it },
                { panelReadingDisplayChangedListener?.invoke() },
            )

        readerPreferences.panelReadingFocusStrength()
            .register(
                { panelReadingFocusStrength = PanelReadingSettings.normalizeFocusStrength(it) },
                { panelReadingDisplayChangedListener?.invoke() },
            )

        readerPreferences.panelReadingPrimaryOverlay()
            .register(
                { panelReadingPrimaryOverlay = it },
                { panelReadingDisplayChangedListener?.invoke() },
            )

        readerPreferences.readWithVolumeKeys
            .register({ volumeKeysEnabled = it })

        readerPreferences.readWithVolumeKeysInverted
            .register({ volumeKeysInverted = it })

        readerPreferences.alwaysShowChapterTransition
            .register({ alwaysShowChapterTransition = it })

        readerPreferences.reshapeWidePagesOnTallScreensOnly
            .register(
                { reshapeWidePagesOnTallScreensOnly = it },
                // Not the shared image-property listener: that leaves already-built pages alone, so
                // the page in front of you would not change and the setting would look broken. This
                // one rebuilds the views, which re-runs the decode that decides the page's shape.
                { reshapeWidePagesChangedListener?.invoke() },
            )

        forceNavigationOverlay = readerPreferences.showNavigationOverlayNewUser.get()
        if (forceNavigationOverlay) {
            readerPreferences.showNavigationOverlayNewUser.set(false)
        }

        readerPreferences.showNavigationOverlayOnStart
            .register({ navigationOverlayOnStart = it })
    }

    protected abstract fun defaultNavigation(): ViewerNavigation

    abstract fun updateNavigation(navigationMode: Int)

    fun <T> Preference<T>.register(
        valueAssignment: (T) -> Unit,
        onChanged: (T) -> Unit = {},
    ) {
        changes()
            .onEach { valueAssignment(it) }
            .distinctUntilChanged()
            .onEach { onChanged(it) }
            .launchIn(scope)
    }
}
