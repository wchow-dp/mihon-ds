package eu.kanade.domain.track.interactor

import dev.zacsweers.metro.Inject
import eu.kanade.domain.track.model.toDbTrack
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import tachiyomi.domain.track.interactor.GetTracks

@Inject
class GetRecommendations(
    private val getTracks: GetTracks,
    private val trackerManager: TrackerManager,
) {

    suspend fun await(mangaId: Long): List<TrackSearch> {
        return supervisorScope {
            val tracks = getTracks.await(mangaId)
            if (tracks.isEmpty()) return@supervisorScope emptyList()

            tracks
                .mapNotNull { track ->
                    val service = trackerManager.get(track.trackerId)
                    if (service != null && service.isLoggedIn) {
                        service to track
                    } else {
                        null
                    }
                }
                .map { (service, track) ->
                    async {
                        try {
                            service.getRecommendations(track.toDbTrack())
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }
                .awaitAll()
                .flatten()
                .distinctBy { it.remote_id } // Simple deduplication by remote_id
        }
    }
}
