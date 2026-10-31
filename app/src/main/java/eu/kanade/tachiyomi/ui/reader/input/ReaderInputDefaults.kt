// Mihon DS: long call chains here predate the line-length check; wrapping them by hand risks the reader
@file:Suppress("ktlint:standard:max-line-length")

package eu.kanade.tachiyomi.ui.reader.input

import android.view.KeyEvent

data class ReaderInputDefaultOptions(
    val volumeKeysEnabled: Boolean,
    val volumeKeysInverted: Boolean,
)

object ReaderInputDefaults {

    fun global(options: ReaderInputDefaultOptions): List<ReaderInputMapping> {
        return buildList {
            add(default("default_key_dpad_right_next", KeyEvent.KEYCODE_DPAD_RIGHT, ReaderAction.NEXT))
            add(default("default_key_dpad_down_next", KeyEvent.KEYCODE_DPAD_DOWN, ReaderAction.NEXT))
            add(default("default_key_page_down_next", KeyEvent.KEYCODE_PAGE_DOWN, ReaderAction.NEXT))
            add(default("default_key_dpad_left_previous", KeyEvent.KEYCODE_DPAD_LEFT, ReaderAction.PREVIOUS))
            add(default("default_key_dpad_up_previous", KeyEvent.KEYCODE_DPAD_UP, ReaderAction.PREVIOUS))
            add(default("default_key_page_up_previous", KeyEvent.KEYCODE_PAGE_UP, ReaderAction.PREVIOUS))
            add(default("default_key_menu_toggle", KeyEvent.KEYCODE_MENU, ReaderAction.TOGGLE_MENU))
            add(default("default_key_n_next_chapter", KeyEvent.KEYCODE_N, ReaderAction.NEXT_CHAPTER))
            add(default("default_key_p_previous_chapter", KeyEvent.KEYCODE_P, ReaderAction.PREVIOUS_CHAPTER))
            add(default("default_key_r1_next", KeyEvent.KEYCODE_BUTTON_R1, ReaderAction.NEXT))
            add(default("default_key_l1_previous", KeyEvent.KEYCODE_BUTTON_L1, ReaderAction.PREVIOUS))
            add(default("default_key_r2_next_panel", KeyEvent.KEYCODE_BUTTON_R2, ReaderAction.NEXT_PANEL))
            add(default("default_key_l2_previous_panel", KeyEvent.KEYCODE_BUTTON_L2, ReaderAction.PREVIOUS_PANEL))

            if (options.volumeKeysEnabled) {
                val downAction = if (options.volumeKeysInverted) ReaderAction.PREVIOUS else ReaderAction.NEXT
                val upAction = if (options.volumeKeysInverted) ReaderAction.NEXT else ReaderAction.PREVIOUS
                add(default("default_key_volume_down", KeyEvent.KEYCODE_VOLUME_DOWN, downAction))
                add(default("default_key_volume_up", KeyEvent.KEYCODE_VOLUME_UP, upAction))
            }
        }
    }

    fun overrides(options: ReaderInputDefaultOptions): List<ReaderInputLayerMappings> {
        return listOf(
            ReaderInputLayerMappings(
                layer = ReaderInputLayer.PAGED,
                mappings = pagedSemanticMappings(options),
            ),
            ReaderInputLayerMappings(
                layer = ReaderInputLayer.WEBTOON,
                mappings = listOf(
                    default(
                        "default_webtoon_key_r2_scroll_down",
                        KeyEvent.KEYCODE_BUTTON_R2,
                        ReaderAction.SCROLL_DOWN,
                    ),
                    default(
                        "default_webtoon_key_r2_hold_scroll_down",
                        KeyEvent.KEYCODE_BUTTON_R2,
                        ReaderAction.HOLD_SCROLL_DOWN,
                        trigger = ReaderInputTrigger.HOLD,
                    ),
                    default(
                        "default_webtoon_key_l2_scroll_up",
                        KeyEvent.KEYCODE_BUTTON_L2,
                        ReaderAction.SCROLL_UP,
                    ),
                    default(
                        "default_webtoon_key_l2_hold_scroll_up",
                        KeyEvent.KEYCODE_BUTTON_L2,
                        ReaderAction.HOLD_SCROLL_UP,
                        trigger = ReaderInputTrigger.HOLD,
                    ),
                ),
            ),
        )
    }

    private fun pagedSemanticMappings(options: ReaderInputDefaultOptions): List<ReaderInputMapping> {
        return buildList {
            add(default("default_paged_key_dpad_down_next_page", KeyEvent.KEYCODE_DPAD_DOWN, ReaderAction.NEXT_PAGE))
            add(default("default_paged_key_page_down_next_page", KeyEvent.KEYCODE_PAGE_DOWN, ReaderAction.NEXT_PAGE))
            add(default("default_paged_key_r1_next_page", KeyEvent.KEYCODE_BUTTON_R1, ReaderAction.NEXT_PAGE))
            add(
                default(
                    "default_paged_key_dpad_up_previous_page",
                    KeyEvent.KEYCODE_DPAD_UP,
                    ReaderAction.PREVIOUS_PAGE,
                ),
            )
            add(
                default(
                    "default_paged_key_page_up_previous_page",
                    KeyEvent.KEYCODE_PAGE_UP,
                    ReaderAction.PREVIOUS_PAGE,
                ),
            )
            add(default("default_paged_key_l1_previous_page", KeyEvent.KEYCODE_BUTTON_L1, ReaderAction.PREVIOUS_PAGE))

            if (options.volumeKeysEnabled) {
                val downAction = if (options.volumeKeysInverted) ReaderAction.PREVIOUS_PAGE else ReaderAction.NEXT_PAGE
                val upAction = if (options.volumeKeysInverted) ReaderAction.NEXT_PAGE else ReaderAction.PREVIOUS_PAGE
                add(default("default_paged_key_volume_down", KeyEvent.KEYCODE_VOLUME_DOWN, downAction))
                add(default("default_paged_key_volume_up", KeyEvent.KEYCODE_VOLUME_UP, upAction))
            }
        }
    }

    private fun default(
        id: String,
        keyCode: Int,
        action: ReaderAction,
        metaState: Int = 0,
        trigger: ReaderInputTrigger = ReaderInputTrigger.PRESS,
    ): ReaderInputMapping {
        return ReaderInputMapping(
            id = id,
            binding = InputBinding.key(keyCode, metaState),
            trigger = trigger,
            action = action,
        )
    }
}
