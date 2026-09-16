package eu.kanade.tachiyomi.ui.reader.input

import kotlinx.serialization.Serializable

@Serializable
enum class ReaderInputLayer {
    PAGED,
    WEBTOON,
    GUIDED_READING,
}

@Serializable
enum class ReaderAction {
    NEXT,
    PREVIOUS,
    NEXT_CHAPTER,
    PREVIOUS_CHAPTER,
    TOGGLE_MENU,
    TOGGLE_COMPANION_PAGE,
    TOGGLE_GUIDED_READING,
    ROTATE_PAGE,
    ROTATE_PAGE_90,
    ROTATE_PAGE_180,
    ROTATE_PAGE_270,
    OPEN_READER_SETTINGS,
    NEXT_PANEL,
    PREVIOUS_PANEL,
    NEXT_PAGE,
    PREVIOUS_PAGE,
    SCROLL_DOWN,
    SCROLL_UP,
    FAST_SCROLL_DOWN,
    FAST_SCROLL_UP,
    HOLD_SCROLL_DOWN,
    HOLD_SCROLL_UP,
}

@Serializable
enum class InputBindingType {
    KEY,
    AXIS,
}

@Serializable
enum class AxisDirection {
    POSITIVE,
    NEGATIVE,
}

@Serializable
enum class ReaderInputTrigger {
    PRESS,
    HOLD,
}

@Serializable
data class InputBinding(
    val type: InputBindingType,
    val keyCode: Int = 0,
    val metaState: Int = 0,
    val axis: Int = 0,
    val direction: AxisDirection = AxisDirection.POSITIVE,
    val threshold: Float = DEFAULT_AXIS_THRESHOLD,
) {
    companion object {
        const val DEFAULT_AXIS_THRESHOLD = 0.5f

        fun key(keyCode: Int, metaState: Int = 0): InputBinding {
            return InputBinding(
                type = InputBindingType.KEY,
                keyCode = keyCode,
                metaState = metaState,
            )
        }

        fun axis(
            axis: Int,
            direction: AxisDirection,
            threshold: Float = DEFAULT_AXIS_THRESHOLD,
        ): InputBinding {
            return InputBinding(
                type = InputBindingType.AXIS,
                axis = axis,
                direction = direction,
                threshold = threshold,
            )
        }
    }
}

@Serializable
data class ReaderInputMapping(
    val id: String,
    val binding: InputBinding,
    val trigger: ReaderInputTrigger = ReaderInputTrigger.PRESS,
    val action: ReaderAction,
)

@Serializable
data class ReaderInputLayerMappings(
    val layer: ReaderInputLayer,
    val mappings: List<ReaderInputMapping> = emptyList(),
)

@Serializable
data class ReaderInputProfile(
    val version: Int = 1,
    val disabledDefaultIds: Set<String> = emptySet(),
    val customGlobal: List<ReaderInputMapping> = emptyList(),
    val customOverrides: List<ReaderInputLayerMappings> = emptyList(),
)
