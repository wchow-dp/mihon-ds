package eu.kanade.domain.track.interactor

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.source.Source
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository
import eu.kanade.tachiyomi.data.database.models.Track as DbTrack

class SyncChapterProgressWithTrackTest {

    @Test
    fun `auto sync progress from trackers defaults to disabled`() {
        TrackPreferences(InMemoryPreferenceStore())
            .autoSyncReadChapters()
            .get() shouldBe false
    }

    @Test
    fun `sync updates local unread chapters from regular tracker progress`() = runTest {
        val chapters = listOf(
            chapter(id = 1, chapterNumber = 1.0, sourceOrder = 3, read = false),
            chapter(id = 2, chapterNumber = 2.0, sourceOrder = 2, read = false),
            chapter(id = 3, chapterNumber = 3.0, sourceOrder = 1, read = false),
        )
        val chapterRepository = FakeChapterRepository(chapters)
        val trackRepository = FakeTrackRepository()
        val interactor = interactor(chapterRepository, trackRepository)

        interactor.syncFromTrack(
            mangaId = MANGA_ID,
            remoteTrack = track(lastChapterRead = 2.0, status = TestTracker.READING),
            tracker = TestTracker(notStartedStatuses = setOf(TestTracker.PLAN_TO_READ)),
        )

        chapterRepository.updates.map { it.id to it.read } shouldContainExactly listOf(
            1L to true,
            2L to true,
        )
    }

    @Test
    fun `sync from tracker only writes read status to local chapters`() = runTest {
        val chapters = listOf(
            chapter(id = 1, chapterNumber = 1.0, sourceOrder = 2, read = false),
            chapter(id = 2, chapterNumber = 2.0, sourceOrder = 1, read = false),
        )
        val chapterRepository = FakeChapterRepository(chapters)
        val trackRepository = FakeTrackRepository()
        val interactor = interactor(chapterRepository, trackRepository)

        interactor.syncFromTrack(
            mangaId = MANGA_ID,
            remoteTrack = track(lastChapterRead = 2.0, status = TestTracker.READING),
            tracker = TestTracker(notStartedStatuses = emptySet()),
        )

        chapterRepository.updates.map { it.id to it.read } shouldContainExactly listOf(
            1L to true,
            2L to true,
        )
    }

    @Test
    fun `sync from tracker stops at non continuous source order chapter numbers`() = runTest {
        val chapters = listOf(
            chapter(id = 1, chapterNumber = 1.0, sourceOrder = 4, read = false),
            chapter(id = 2, chapterNumber = 2.0, sourceOrder = 3, read = false),
            chapter(id = 3, chapterNumber = 1.0, sourceOrder = 2, read = false),
            chapter(id = 4, chapterNumber = 3.0, sourceOrder = 1, read = false),
        )
        val chapterRepository = FakeChapterRepository(chapters)
        val trackRepository = FakeTrackRepository()
        val interactor = interactor(chapterRepository, trackRepository)

        interactor.syncFromTrack(
            mangaId = MANGA_ID,
            remoteTrack = track(lastChapterRead = 3.0, status = TestTracker.READING),
            tracker = TestTracker(notStartedStatuses = emptySet()),
        )

        chapterRepository.updates.map { it.id to it.read } shouldContainExactly listOf(
            1L to true,
            2L to true,
        )
    }

    @Test
    fun `sync does not mark local chapters read when remote tracker has not started status`() = runTest {
        val chapters = listOf(
            chapter(id = 1, chapterNumber = 1.0, sourceOrder = 2, read = false),
            chapter(id = 2, chapterNumber = 2.0, sourceOrder = 1, read = false),
        )
        val chapterRepository = FakeChapterRepository(chapters)
        val trackRepository = FakeTrackRepository()
        val interactor = interactor(chapterRepository, trackRepository)

        interactor.syncFromTrack(
            mangaId = MANGA_ID,
            remoteTrack = track(lastChapterRead = 2.0, status = TestTracker.PLAN_TO_READ),
            tracker = TestTracker(notStartedStatuses = setOf(TestTracker.PLAN_TO_READ)),
        )

        chapterRepository.updates.shouldBeEmpty()
    }

    @Test
    fun `await keeps default refresh limited to enhanced trackers`() = runTest {
        val chapters = listOf(
            chapter(id = 1, chapterNumber = 1.0, sourceOrder = 2, read = false),
            chapter(id = 2, chapterNumber = 2.0, sourceOrder = 1, read = false),
        )
        val chapterRepository = FakeChapterRepository(chapters)
        val trackRepository = FakeTrackRepository()
        val interactor = interactor(chapterRepository, trackRepository)

        interactor.await(
            mangaId = MANGA_ID,
            remoteTrack = track(lastChapterRead = 2.0, status = TestTracker.READING),
            tracker = TestTracker(notStartedStatuses = emptySet()),
        )

        chapterRepository.updates.shouldBeEmpty()
    }

    @Test
    fun `await syncs chapter progress for enhanced trackers`() = runTest {
        val chapters = listOf(
            chapter(id = 1, chapterNumber = 1.0, sourceOrder = 2, read = false),
            chapter(id = 2, chapterNumber = 2.0, sourceOrder = 1, read = false),
        )
        val chapterRepository = FakeChapterRepository(chapters)
        val trackRepository = FakeTrackRepository()
        val interactor = interactor(chapterRepository, trackRepository)

        interactor.await(
            mangaId = MANGA_ID,
            remoteTrack = track(lastChapterRead = 2.0, status = TestTracker.READING),
            tracker = EnhancedTestTracker(notStartedStatuses = emptySet()),
        )

        chapterRepository.updates.map { it.id to it.read } shouldContainExactly listOf(
            1L to true,
            2L to true,
        )
    }

    @Test
    fun `sync from tracker does not update remote tracker from continuous local progress`() = runTest {
        val chapters = listOf(
            chapter(id = 1, chapterNumber = 1.0, sourceOrder = 3, read = true),
            chapter(id = 2, chapterNumber = 2.0, sourceOrder = 2, read = true),
            chapter(id = 3, chapterNumber = 3.0, sourceOrder = 1, read = false),
        )
        val chapterRepository = FakeChapterRepository(chapters)
        val trackRepository = FakeTrackRepository()
        val interactor = interactor(chapterRepository, trackRepository)
        val tracker = TestTracker(notStartedStatuses = emptySet())

        interactor.syncFromTrack(
            mangaId = MANGA_ID,
            remoteTrack = track(lastChapterRead = 1.0, status = TestTracker.READING),
            tracker = tracker,
        )

        chapterRepository.updates.shouldBeEmpty()
        tracker.updates.shouldBeEmpty()
        trackRepository.inserted.shouldBeEmpty()
    }

    @Test
    fun `await updates remote tracker from continuous local progress for enhanced trackers`() = runTest {
        val chapters = listOf(
            chapter(id = 1, chapterNumber = 1.0, sourceOrder = 3, read = true),
            chapter(id = 2, chapterNumber = 2.0, sourceOrder = 2, read = true),
            chapter(id = 3, chapterNumber = 3.0, sourceOrder = 1, read = false),
        )
        val chapterRepository = FakeChapterRepository(chapters)
        val trackRepository = FakeTrackRepository()
        val interactor = interactor(chapterRepository, trackRepository)
        val tracker = TestTracker(notStartedStatuses = emptySet())

        interactor.await(
            mangaId = MANGA_ID,
            remoteTrack = track(lastChapterRead = 1.0, status = TestTracker.READING),
            tracker = EnhancedTestTracker(notStartedStatuses = emptySet(), updates = tracker.updates),
        )

        chapterRepository.updates.shouldBeEmpty()
        tracker.updates.map { it.last_chapter_read } shouldContainExactly listOf(2.0)
        trackRepository.inserted.map { it.lastChapterRead } shouldContainExactly listOf(2.0)
    }

    private fun interactor(
        chapterRepository: FakeChapterRepository,
        trackRepository: FakeTrackRepository,
    ): SyncChapterProgressWithTrack {
        return SyncChapterProgressWithTrack(
            updateChapter = UpdateChapter(chapterRepository),
            insertTrack = InsertTrack(trackRepository),
            getChaptersByMangaId = GetChaptersByMangaId(chapterRepository),
        )
    }

    private fun chapter(
        id: Long,
        chapterNumber: Double,
        sourceOrder: Long,
        read: Boolean,
    ): Chapter {
        return Chapter.create().copy(
            id = id,
            mangaId = MANGA_ID,
            read = read,
            sourceOrder = sourceOrder,
            chapterNumber = chapterNumber,
        )
    }

    private fun track(
        lastChapterRead: Double,
        status: Long,
    ): Track {
        return Track(
            id = 1,
            mangaId = MANGA_ID,
            trackerId = 1,
            remoteId = 10,
            libraryId = null,
            title = "Test",
            lastChapterRead = lastChapterRead,
            totalChapters = 0,
            status = status,
            score = 0.0,
            remoteUrl = "",
            startDate = 0,
            finishDate = 0,
            private = false,
        )
    }

    private class FakeChapterRepository(
        private val chapters: List<Chapter>,
    ) : ChapterRepository {
        val updates = mutableListOf<ChapterUpdate>()

        override suspend fun addAll(chapters: List<Chapter>): List<Chapter> = error("unused")

        override suspend fun update(chapterUpdate: ChapterUpdate) {
            updates += chapterUpdate
        }

        override suspend fun updateAll(chapterUpdates: List<ChapterUpdate>) {
            updates += chapterUpdates
        }

        override suspend fun removeChaptersWithIds(chapterIds: List<Long>) = error("unused")

        override suspend fun getChapterByMangaId(mangaId: Long, applyScanlatorFilter: Boolean): List<Chapter> {
            return chapters
        }

        override suspend fun getScanlatorsByMangaId(mangaId: Long): List<String> = error("unused")

        override fun getScanlatorsByMangaIdAsFlow(mangaId: Long): Flow<List<String>> = emptyFlow()

        override suspend fun getBookmarkedChaptersByMangaId(mangaId: Long): List<Chapter> = error("unused")

        override suspend fun getChapterById(id: Long): Chapter? = error("unused")

        override suspend fun getChapterByMangaIdAsFlow(
            mangaId: Long,
            applyScanlatorFilter: Boolean,
        ): Flow<List<Chapter>> = emptyFlow()

        override suspend fun getChapterByUrlAndMangaId(url: String, mangaId: Long): Chapter? = error("unused")
    }

    private class FakeTrackRepository : TrackRepository {
        val inserted = mutableListOf<Track>()

        override suspend fun getTrackById(id: Long): Track? = error("unused")

        override suspend fun getTracksByMangaId(mangaId: Long): List<Track> = error("unused")

        override fun getTracksAsFlow(): Flow<List<Track>> = emptyFlow()

        override fun getTracksByMangaIdAsFlow(mangaId: Long): Flow<List<Track>> = emptyFlow()

        override suspend fun delete(mangaId: Long, trackerId: Long) = error("unused")

        override suspend fun insert(track: Track) {
            inserted += track
        }

        override suspend fun insertAll(tracks: List<Track>) {
            inserted += tracks
        }
    }

    private open class TestTracker(
        private val notStartedStatuses: Set<Long>,
        val updates: MutableList<DbTrack> = mutableListOf(),
    ) : Tracker {

        override val id: Long = 1
        override val name: String = "TestTracker"
        override val client: OkHttpClient = OkHttpClient()
        override val supportsReadingDates: Boolean = false
        override val supportsPrivateTracking: Boolean = false
        override val isLoggedIn: Boolean = true
        override val isLoggedInFlow: Flow<Boolean> = flowOf(true)

        override fun getLogo(): Int = 0
        override fun getStatusList(): List<Long> = listOf(READING, PLAN_TO_READ)
        override fun getStatus(status: Long): StringResource? = null
        override fun getReadingStatus(): Long = READING
        override fun getRereadingStatus(): Long = READING
        override fun getCompletionStatus(): Long = 3
        override fun getScoreList(): ImmutableList<String> = persistentListOf()
        override fun get10PointScore(track: Track): Double = 0.0
        override fun indexToScore(index: Int): Double = 0.0
        override fun displayScore(track: Track): String = ""
        override suspend fun update(track: DbTrack, didReadChapter: Boolean): DbTrack {
            updates += track
            return track
        }
        override suspend fun bind(track: DbTrack, hasReadChapters: Boolean): DbTrack = track
        override suspend fun search(query: String): List<TrackSearch> = emptyList()
        override suspend fun refresh(track: DbTrack): DbTrack = track
        override suspend fun fetchRemoteTrack(track: DbTrack): DbTrack? = track
        override suspend fun login(username: String, password: String) = Unit
        override fun logout() = Unit
        override fun getUsername(): String = "username"
        // Added upstream in v0.20.4; EnhancedTestTracker inherits these from here.
        override fun getDisplayUsername(): String = "username"
        override fun saveDisplayUsername(displayName: String) = Unit
        override fun getPassword(): String = "password"
        override fun saveCredentials(username: String, password: String) = Unit
        override suspend fun register(item: DbTrack, mangaId: Long) = Unit
        override suspend fun setRemoteStatus(track: DbTrack, status: Long) = Unit
        override suspend fun setRemoteLastChapterRead(track: DbTrack, chapterNumber: Int) = Unit
        override suspend fun setRemoteScore(track: DbTrack, scoreString: String) = Unit
        override suspend fun setRemoteStartDate(track: DbTrack, epochMillis: Long) = Unit
        override suspend fun setRemoteFinishDate(track: DbTrack, epochMillis: Long) = Unit
        override suspend fun setRemotePrivate(track: DbTrack, private: Boolean) = Unit
        override fun hasNotStartedReading(status: Long): Boolean = status in notStartedStatuses

        companion object {
            const val READING = 1L
            const val PLAN_TO_READ = 2L
        }
    }

    private class EnhancedTestTracker(
        notStartedStatuses: Set<Long>,
        updates: MutableList<DbTrack> = mutableListOf(),
    ) : TestTracker(notStartedStatuses, updates), EnhancedTracker {
        override fun getAcceptedSources(): List<String> = emptyList()
        override fun loginNoop() = Unit
        override suspend fun match(manga: Manga): TrackSearch? = null
        override fun isTrackFrom(track: Track, manga: Manga, source: Source?): Boolean = false
        override fun migrateTrack(track: Track, manga: Manga, newSource: Source): Track? = null
    }

    private companion object {
        const val MANGA_ID = 100L
    }
}
