package eu.kanade.domain.track.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.track.interactor.UpsertTrack
import tachiyomi.domain.track.model.Track
import kotlin.math.max

@Inject
class SyncChapterProgressWithTrack(
    private val updateChapter: UpdateChapter,
    private val upsertTrack: UpsertTrack,
    private val getChaptersByMangaId: GetChaptersByMangaId,
) {

    suspend fun await(
        mangaId: Long,
        remoteTrack: Track,
        tracker: Tracker,
    ) {
        if (tracker !is EnhancedTracker) return

        sync(mangaId, remoteTrack, tracker, updateRemote = true)
    }

    suspend fun syncFromTrack(
        mangaId: Long,
        remoteTrack: Track,
        tracker: Tracker,
    ) {
        sync(mangaId, remoteTrack, tracker, updateRemote = false)
    }

    private suspend fun sync(
        mangaId: Long,
        remoteTrack: Track,
        tracker: Tracker,
        updateRemote: Boolean,
    ) {
        // Current chapters in database
        val sortedChapters = getChaptersByMangaId.await(mangaId)
            .sortedByDescending { it.sourceOrder }
            .filter { it.isRecognizedNumber }

        // Chapters to update to follow tracker
        var lastNumber = 0.0
        val chapterUpdates = if (!tracker.hasNotStartedReading(remoteTrack.status) &&
            remoteTrack.lastChapterRead > 0.0 &&
            sortedChapters.isNotEmpty()
        ) {
            val maxLocalChapter = sortedChapters.maxOf { it.chapterNumber }.toDouble()
            val effectiveLastRead = minOf(remoteTrack.lastChapterRead, maxLocalChapter)

            sortedChapters
                .takeWhile {
                    val matches = it.chapterNumber >= lastNumber && it.chapterNumber <= effectiveLastRead
                    if (matches) {
                        lastNumber = it.chapterNumber.toDouble()
                    }
                    matches
                }
                .filter { !it.read }
                .map { ChapterUpdate(it.id) { read = true } }
        } else {
            emptyList()
        }

        // only take into account continuous reading
        val localLastRead = sortedChapters.takeWhile { it.read }.lastOrNull()?.chapterNumber ?: 0F
        val lastRead = max(remoteTrack.lastChapterRead, localLastRead.toDouble())
        val updatedTrack = remoteTrack.copy(lastChapterRead = lastRead)

        try {
            // Update Tracker to localLastRead if needed
            if (updateRemote && updatedTrack.lastChapterRead > remoteTrack.lastChapterRead) {
                tracker.update(updatedTrack.toDbTrack())
                // update Track in database
                upsertTrack.await(updatedTrack)
            }
            // Update local chapters following Tracker
            updateChapter.awaitAll(chapterUpdates)
        } catch (e: Throwable) {
            logcat(LogPriority.WARN, e)
        }
    }
}
