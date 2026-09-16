package eu.kanade.presentation.reader.settings

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import eu.kanade.tachiyomi.ui.reader.input.AxisDirection
import eu.kanade.tachiyomi.ui.reader.input.InputBinding
import eu.kanade.tachiyomi.ui.reader.input.InputBindingType
import eu.kanade.tachiyomi.ui.reader.input.ReaderAction
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputLayer
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun ReaderInputLayer.readerInputLabel(): String {
    return when (this) {
        ReaderInputLayer.PAGED -> stringResource(MR.strings.reader_input_layer_paged)
        ReaderInputLayer.WEBTOON -> stringResource(MR.strings.reader_input_layer_webtoon)
        ReaderInputLayer.GUIDED_READING -> stringResource(MR.strings.reader_input_layer_guided_reading)
    }
}

@Composable
internal fun ReaderAction.readerInputLabel(): String {
    return when (this) {
        ReaderAction.NEXT -> stringResource(MR.strings.reader_action_next)
        ReaderAction.PREVIOUS -> stringResource(MR.strings.reader_action_previous)
        ReaderAction.NEXT_CHAPTER -> stringResource(MR.strings.reader_action_next_chapter)
        ReaderAction.PREVIOUS_CHAPTER -> stringResource(MR.strings.reader_action_previous_chapter)
        ReaderAction.TOGGLE_MENU -> stringResource(MR.strings.reader_action_toggle_menu)
        ReaderAction.TOGGLE_COMPANION_PAGE -> stringResource(MR.strings.reader_action_toggle_companion_page)
        ReaderAction.TOGGLE_GUIDED_READING -> stringResource(MR.strings.reader_action_toggle_guided_reading)
        ReaderAction.OPEN_READER_SETTINGS -> stringResource(MR.strings.reader_action_open_reader_settings)
        ReaderAction.ROTATE_PAGE -> stringResource(MR.strings.reader_action_rotate_page)
        ReaderAction.NEXT_PANEL -> stringResource(MR.strings.reader_action_next_panel)
        ReaderAction.PREVIOUS_PANEL -> stringResource(MR.strings.reader_action_previous_panel)
        ReaderAction.NEXT_PAGE -> stringResource(MR.strings.reader_action_next_page)
        ReaderAction.PREVIOUS_PAGE -> stringResource(MR.strings.reader_action_previous_page)
        ReaderAction.SCROLL_DOWN -> stringResource(MR.strings.reader_action_scroll_down)
        ReaderAction.SCROLL_UP -> stringResource(MR.strings.reader_action_scroll_up)
        ReaderAction.FAST_SCROLL_DOWN -> stringResource(MR.strings.reader_action_fast_scroll_down)
        ReaderAction.FAST_SCROLL_UP -> stringResource(MR.strings.reader_action_fast_scroll_up)
        ReaderAction.HOLD_SCROLL_DOWN -> stringResource(MR.strings.reader_action_hold_scroll_down)
        ReaderAction.HOLD_SCROLL_UP -> stringResource(MR.strings.reader_action_hold_scroll_up)
    }
}

internal fun InputBinding.readerInputLabel(): String {
    return when (type) {
        InputBindingType.KEY -> listOfNotNull(
            metaState.takeIf { it != 0 }?.let(::formatMetaState),
            formatKeyCode(keyCode),
        ).joinToString(" + ")
        InputBindingType.AXIS -> {
            val directionLabel = when (direction) {
                AxisDirection.POSITIVE -> "+"
                AxisDirection.NEGATIVE -> "-"
            }
            "${formatAxisName(axis)} $directionLabel"
        }
    }
}

private fun formatKeyCode(keyCode: Int): String {
    return KeyEvent.keyCodeToString(keyCode)
        .removePrefix("KEYCODE_")
        .replace('_', ' ')
}

private fun formatAxisName(axis: Int): String {
    return when (axis) {
        MotionEvent.AXIS_HAT_X -> "Hat X"
        MotionEvent.AXIS_HAT_Y -> "Hat Y"
        MotionEvent.AXIS_LTRIGGER -> "L2"
        MotionEvent.AXIS_RTRIGGER -> "R2"
        MotionEvent.AXIS_BRAKE -> "Brake"
        MotionEvent.AXIS_GAS -> "Gas"
        else -> MotionEvent.axisToString(axis)
            .removePrefix("AXIS_")
            .replace('_', ' ')
    }
}

private fun formatMetaState(metaState: Int): String {
    return buildList {
        if (metaState and KeyEvent.META_CTRL_ON != 0) add("CTRL")
        if (metaState and KeyEvent.META_SHIFT_ON != 0) add("SHIFT")
        if (metaState and KeyEvent.META_ALT_ON != 0) add("ALT")
    }.joinToString(" + ")
}
