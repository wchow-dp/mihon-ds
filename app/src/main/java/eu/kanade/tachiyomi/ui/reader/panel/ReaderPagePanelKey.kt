package eu.kanade.tachiyomi.ui.reader.panel

import eu.kanade.tachiyomi.ui.reader.model.InsertPage
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import tachiyomi.domain.chapter.model.Chapter

fun ReaderPage.panelPageKey(
    renderVariant: PanelPageRenderVariant = PanelPageRenderVariant.FULL,
): PanelPageKey {
    val chapter = runCatching { chapter.chapter }.getOrNull()
    return PanelPageKey(
        chapterId = chapter?.id,
        chapterUrl = chapter.safeUrl(),
        chapterName = chapter.safeName(),
        pageIndex = index,
        imageUrl = imageUrl,
        pageUrl = url,
        isInsertPage = this is InsertPage,
        renderVariant = renderVariant,
    )
}

fun ReaderPage.matchesPanelKey(key: PanelPageKey?): Boolean {
    key ?: return false
    val currentKey = panelPageKey(key.renderVariant)
    return currentKey.hasSameLogicalPage(key)
}

private fun Chapter?.safeUrl(): String {
    return this?.let { runCatching { it.url }.getOrNull() }.orEmpty()
}

private fun Chapter?.safeName(): String {
    return this?.let { runCatching { it.name }.getOrNull() }.orEmpty()
}
