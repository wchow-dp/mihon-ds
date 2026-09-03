package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.MangasPage
import tachiyomi.domain.source.model.Source as DomainSource

class MergedSource : Source {
    override val id: Long = ID
    override val name: String = "Merged"
    override val lang: String = ""

    override val supportsLatest: Boolean = false

    // Placeholder source for merged entries; it is never browsed or read directly.
    override suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
        throw UnsupportedOperationException()

    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = throw UnsupportedOperationException()

    override suspend fun getPageList(chapter: SChapter): List<Page> =
        throw UnsupportedOperationException()

    companion object {
        const val ID = -6L

        fun isMerged(source: DomainSource): Boolean = source.id == ID
        fun isMerged(sourceId: Long): Boolean = sourceId == ID
    }
}
