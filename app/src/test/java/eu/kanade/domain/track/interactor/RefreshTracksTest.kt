package eu.kanade.domain.track.interactor

import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import eu.kanade.tachiyomi.network.HttpException
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.domain.track.model.Track
import tachiyomi.domain.track.repository.TrackRepository
import eu.kanade.tachiyomi.data.database.models.Track as DbTrack

class RefreshTracksTest {

    @Test
    fun `all trackers from remote uses read only fetch without refresh`() = runTest {
        val localTrack = track(lastChapterRead = 0.0, status = RecordingTracker.READING)
        val remoteTrack = track(lastChapterRead = 2.0, status = RecordingTracker.READING).toDbTrack()
        val tracker = RecordingTracker(remoteTrack = remoteTrack)
        val chapterRepository = FakeChapterRepository(
            listOf(
                chapter(id = 1, chapterNumber = 1.0, sourceOrder = 2, read = false),
                chapter(id = 2, chapterNumber = 2.0, sourceOrder = 1, read = false),
            ),
        )
        val trackRepository = FakeTrackRepository(listOf(localTrack))

        val failures = refreshTracks(tracker, trackRepository, chapterRepository).await(
            mangaId = MANGA_ID,
            progressSyncMode = TrackProgressSyncMode.AllTrackersFromRemote,
        )

        failures.shouldBeEmpty()
        tracker.fetchRemoteCalls shouldBe 1
        tracker.refreshCalls shouldBe 0
        tracker.remoteUpdates.shouldBeEmpty()
        trackRepository.inserted.map { it.lastChapterRead } shouldContainExactly listOf(2.0)
        chapterRepository.updates.map { it.id to it.read } shouldContainExactly listOf(
            1L to true,
            2L to true,
        )
    }

    @Test
    fun `all trackers from remote skips missing remote track without refresh`() = runTest {
        val tracker = RecordingTracker(remoteTrack = null)
        val chapterRepository = FakeChapterRepository(
            listOf(
                chapter(id = 1, chapterNumber = 1.0, sourceOrder = 1, read = false),
            ),
        )
        val trackRepository = FakeTrackRepository(
            listOf(track(lastChapterRead = 0.0, status = RecordingTracker.READING)),
        )

        val failures = refreshTracks(tracker, trackRepository, chapterRepository).await(
            mangaId = MANGA_ID,
            progressSyncMode = TrackProgressSyncMode.AllTrackersFromRemote,
        )

        failures.shouldBeEmpty()
        tracker.fetchRemoteCalls shouldBe 1
        tracker.refreshCalls shouldBe 0
        tracker.remoteUpdates.shouldBeEmpty()
        trackRepository.inserted.shouldBeEmpty()
        chapterRepository.updates.shouldBeEmpty()
    }

    @Test
    fun `all trackers from remote skips http 404 missing remote track without refresh`() = runTest {
        val tracker = RecordingTracker(remoteTrack = null, fetchFailureCode = 404)
        val chapterRepository = FakeChapterRepository(
            listOf(
                chapter(id = 1, chapterNumber = 1.0, sourceOrder = 1, read = false),
            ),
        )
        val trackRepository = FakeTrackRepository(
            listOf(track(lastChapterRead = 0.0, status = RecordingTracker.READING)),
        )

        val failures = refreshTracks(tracker, trackRepository, chapterRepository).await(
            mangaId = MANGA_ID,
            progressSyncMode = TrackProgressSyncMode.AllTrackersFromRemote,
        )

        failures.shouldBeEmpty()
        tracker.fetchRemoteCalls shouldBe 1
        tracker.refreshCalls shouldBe 0
        tracker.remoteUpdates.shouldBeEmpty()
        trackRepository.inserted.shouldBeEmpty()
        chapterRepository.updates.shouldBeEmpty()
    }

    @Test
    fun `all trackers from remote reports non missing remote fetch failures`() = runTest {
        val tracker = RecordingTracker(remoteTrack = null, fetchFailureCode = 500)
        val chapterRepository = FakeChapterRepository(
            listOf(
                chapter(id = 1, chapterNumber = 1.0, sourceOrder = 1, read = false),
            ),
        )
        val trackRepository = FakeTrackRepository(
            listOf(track(lastChapterRead = 0.0, status = RecordingTracker.READING)),
        )

        val failures = refreshTracks(tracker, trackRepository, chapterRepository).await(
            mangaId = MANGA_ID,
            progressSyncMode = TrackProgressSyncMode.AllTrackersFromRemote,
        )

        failures.size shouldBe 1
        failures.first().first shouldBe tracker
        tracker.fetchRemoteCalls shouldBe 1
        tracker.refreshCalls shouldBe 0
        tracker.remoteUpdates.shouldBeEmpty()
        trackRepository.inserted.shouldBeEmpty()
        chapterRepository.updates.shouldBeEmpty()
    }

    private fun refreshTracks(
        tracker: BaseTracker,
        trackRepository: FakeTrackRepository,
        chapterRepository: FakeChapterRepository,
    ): RefreshTracks {
        val trackerManager = mockk<TrackerManager> {
            every { get(TRACKER_ID) } returns tracker
        }

        return RefreshTracks(
            getTracks = GetTracks(trackRepository),
            trackerManager = trackerManager,
            insertTrack = InsertTrack(trackRepository),
            syncChapterProgressWithTrack = SyncChapterProgressWithTrack(
                updateChapter = UpdateChapter(chapterRepository),
                insertTrack = InsertTrack(trackRepository),
                getChaptersByMangaId = GetChaptersByMangaId(chapterRepository),
            ),
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
            trackerId = TRACKER_ID,
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

    private class FakeTrackRepository(
        private val tracks: List<Track>,
    ) : TrackRepository {
        val inserted = mutableListOf<Track>()

        override suspend fun getTrackById(id: Long): Track? = error("unused")

        override suspend fun getTracksByMangaId(mangaId: Long): List<Track> = tracks

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

    private class RecordingTracker(
        private val remoteTrack: DbTrack?,
        private val fetchFailureCode: Int? = null,
    ) : BaseTracker(TRACKER_ID, "RecordingTracker") {
        var refreshCalls = 0
        var fetchRemoteCalls = 0
        val remoteUpdates = mutableListOf<DbTrack>()

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
        override fun getCompletionStatus(): Long = COMPLETED
        override fun hasNotStartedReading(status: Long): Boolean = status == PLAN_TO_READ
        override fun getScoreList(): ImmutableList<String> = persistentListOf()
        override fun get10PointScore(track: Track): Double = 0.0
        override fun indexToScore(index: Int): Double = 0.0
        override fun displayScore(track: Track): String = ""
        override suspend fun update(track: DbTrack, didReadChapter: Boolean): DbTrack {
            remoteUpdates += track
            return track
        }
        override suspend fun bind(track: DbTrack, hasReadChapters: Boolean): DbTrack = track
        override suspend fun search(query: String): List<TrackSearch> = emptyList()
        override suspend fun refresh(track: DbTrack): DbTrack {
            refreshCalls += 1
            remoteUpdates += track
            return track
        }
        override suspend fun fetchRemoteTrack(track: DbTrack): DbTrack? {
            fetchRemoteCalls += 1
            fetchFailureCode?.let { code ->
                return fetchRemoteTrackOrNull { throw HttpException(code) }
            }
            return remoteTrack
        }
        override suspend fun login(username: String, password: String) = Unit
        override fun logout() = Unit
        override fun getUsername(): String = "username"
        override fun getPassword(): String = "password"
        override fun saveCredentials(username: String, password: String) = Unit
        override suspend fun register(item: DbTrack, mangaId: Long) = Unit
        override suspend fun setRemoteStatus(track: DbTrack, status: Long) = Unit
        override suspend fun setRemoteLastChapterRead(track: DbTrack, chapterNumber: Int) = Unit
        override suspend fun setRemoteScore(track: DbTrack, scoreString: String) = Unit
        override suspend fun setRemoteStartDate(track: DbTrack, epochMillis: Long) = Unit
        override suspend fun setRemoteFinishDate(track: DbTrack, epochMillis: Long) = Unit
        override suspend fun setRemotePrivate(track: DbTrack, private: Boolean) = Unit

        companion object {
            const val READING = 1L
            const val PLAN_TO_READ = 2L
            const val COMPLETED = 3L
        }
    }

    private companion object {
        const val MANGA_ID = 100L
        const val TRACKER_ID = 1L
    }
}
