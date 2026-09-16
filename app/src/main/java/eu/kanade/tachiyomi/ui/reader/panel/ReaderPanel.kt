package eu.kanade.tachiyomi.ui.reader.panel

import android.graphics.RectF

data class ReaderPanel(
    val id: String,
    val bounds: RectF,
    val confidence: Float,
) {
    val centerX: Float get() = (bounds.left + bounds.right) / 2f
    val centerY: Float get() = (bounds.top + bounds.bottom) / 2f
    val width: Float get() = bounds.right - bounds.left
    val height: Float get() = bounds.bottom - bounds.top
}

enum class PanelReadingDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
}

enum class PanelPageRenderVariant {
    FULL,
    SPLIT_LEFT,
    SPLIT_RIGHT,
    ROTATE_90,
    ROTATE_180,
    ROTATE_NEGATIVE_90,
}

data class PanelPageKey(
    val chapterId: Long?,
    val chapterUrl: String,
    val chapterName: String,
    val pageIndex: Int,
    val imageUrl: String?,
    val pageUrl: String,
    val isInsertPage: Boolean,
    val renderVariant: PanelPageRenderVariant = PanelPageRenderVariant.FULL,
)

fun PanelPageKey.hasSameLogicalPage(other: PanelPageKey?): Boolean {
    other ?: return false

    return chapterId == other.chapterId &&
        chapterUrl == other.chapterUrl &&
        chapterName == other.chapterName &&
        pageIndex == other.pageIndex &&
        imageUrl == other.imageUrl &&
        pageUrl == other.pageUrl &&
        isInsertPage == other.isInsertPage
}

data class PanelDetectionInput(
    val key: PanelPageKey,
    val imageWidth: Int,
    val imageHeight: Int,
    val image: PanelDetectionImage,
)

data class PanelDetectionResult(
    val panels: List<ReaderPanel>,
)

sealed interface PanelDetectionStatus {
    data object Idle : PanelDetectionStatus
    data object Pending : PanelDetectionStatus
    data object Unavailable : PanelDetectionStatus
    data class Ready(val panels: List<ReaderPanel>) : PanelDetectionStatus
    data class Failed(val reason: String) : PanelDetectionStatus
}

data class PanelReadingState(
    val enabled: Boolean = false,
    val key: PanelPageKey? = null,
    val panelIndex: Int = -1,
    val panels: List<ReaderPanel> = emptyList(),
    val status: PanelDetectionStatus = PanelDetectionStatus.Idle,
) {
    val activePanel: ReaderPanel?
        get() = panels.getOrNull(panelIndex)

    val panelCount: Int
        get() = panels.size
}
