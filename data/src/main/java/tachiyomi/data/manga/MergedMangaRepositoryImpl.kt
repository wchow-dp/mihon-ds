package tachiyomi.data.manga

import app.cash.sqldelight.async.coroutines.awaitAsList
import kotlinx.coroutines.flow.Flow
import tachiyomi.data.Database
import tachiyomi.data.subscribeToList
import tachiyomi.domain.manga.model.MergedManga
import tachiyomi.domain.manga.repository.MergedMangaRepository

class MergedMangaRepositoryImpl(
    private val database: Database,
) : MergedMangaRepository {

    override suspend fun getMergedMangaForManga(mangaId: Long): List<MergedManga> {
        return database.manga_mergerQueries
            .getMergedMangaForManga(mangaId, MergedMangaMapper::mapMergedManga)
            .awaitAsList()
    }

    override fun getMergedMangaForMangaAsFlow(mangaId: Long): Flow<List<MergedManga>> {
        return database.manga_mergerQueries
            .getMergedMangaForManga(mangaId, MergedMangaMapper::mapMergedManga)
            .subscribeToList()
    }

    override suspend fun insert(mergedManga: MergedManga) {
        database.manga_mergerQueries.insert(
            mangaId = mergedManga.mangaId,
            mergeMangaId = mergedManga.mergeMangaId,
        )
    }

    override suspend fun deleteByMangaId(mangaId: Long) {
        database.manga_mergerQueries.deleteByMangaId(mangaId)
    }
}
