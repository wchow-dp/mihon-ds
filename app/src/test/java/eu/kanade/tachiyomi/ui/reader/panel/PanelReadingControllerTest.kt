package eu.kanade.tachiyomi.ui.reader.panel

import android.content.Context
import android.graphics.RectF
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import tachiyomi.core.common.preference.Preference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PanelReadingControllerTest {

    /**
     * PanelReadingController reaches for a Context (toasts on a saved correction) and pulls
     * ReaderPreferences and PanelCorrectionStore from Injekt by default. Injekt is not wired up
     * in a plain JVM test, and those defaults are evaluated at construction, so every dependency
     * is passed explicitly here rather than left to resolve.
     */
    private fun testController(
        scope: kotlinx.coroutines.CoroutineScope,
        detector: PanelDetector,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        isEnabled: () -> Boolean = { true },
        readingDirection: () -> PanelReadingDirection = { PanelReadingDirection.LEFT_TO_RIGHT },
    ): PanelReadingController {
        val context = mockk<Context>(relaxed = true)
        // A relaxed mock would hand back a mock enum for the sorting algorithm and a mock
        // correction for every page, so both are stubbed with the real defaults: sort with
        // XY_CUT, and no stored correction.
        val sortingPreference = mockk<Preference<PanelSortingAlgorithm>>(relaxed = true)
        every { sortingPreference.get() } returns PanelSortingAlgorithm.XY_CUT
        every { sortingPreference.changes() } returns emptyFlow()
        val preferences = mockk<ReaderPreferences>(relaxed = true)
        every { preferences.panelSortingAlgorithm() } returns sortingPreference
        val corrections = mockk<PanelCorrectionStore>(relaxed = true)
        every { corrections.getCorrection(any()) } returns null
        return PanelReadingController(
            scope = scope,
            detector = detector,
            isEnabled = isEnabled,
            readingDirection = readingDirection,
            dispatcher = dispatcher,
            context = context,
            readerPreferences = preferences,
            correctionStore = corrections,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `detects one page at a time`() = runTest {
        val firstKey = pageKey(pageIndex = 3)
        val secondKey = pageKey(pageIndex = 4)
        val detector = TrackingDelayedPanelDetector(
            panels = listOf(panel(id = "a", left = 0f)),
        )
        val controller = testController(
            scope = this,
            detector = detector,
            dispatcher = StandardTestDispatcher(testScheduler),
        ).apply {
            setEnabledState(true)
        }

        controller.onVisiblePagesChanged(listOf(firstKey, secondKey))
        controller.onPageImageReady(input(firstKey))
        controller.onPageImageReady(input(secondKey))

        runCurrent()

        assertEquals(listOf(firstKey), detector.startedKeys)
        assertEquals(1, detector.maxConcurrentDetects)

        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf(firstKey, secondKey), detector.startedKeys)

        advanceUntilIdle()

        assertEquals(1, detector.maxConcurrentDetects)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `oversized visible page is marked unavailable instead of queued for detection`() = runTest {
        val key = pageKey(pageIndex = 3)
        val detector = CountingPanelDetector(panels = listOf(panel(id = "a", left = 0f)))
        val controller = testController(
            scope = this,
            detector = detector,
            dispatcher = StandardTestDispatcher(testScheduler),
        ).apply {
            setEnabledState(true)
        }

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(
            input(
                key = key,
                imageBytes = ByteArray((PanelDetectionImage.MAX_SNAPSHOT_BYTE_COUNT + 1).toInt()),
            ),
        )

        runCurrent()

        assertEquals(0, detector.detectCount)
        assertEquals(key, controller.state.value.key)
        assertEquals(PanelDetectionStatus.Unavailable, controller.state.value.status)
    }

    @Test
    fun `visible page without detection input is marked unavailable`() = runTest {
        val key = pageKey(pageIndex = 3)
        val controller = controller(panels = listOf(panel(id = "a", left = 0f)))

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageDetectionUnavailable(key)
        advanceUntilIdle()

        assertEquals(key, controller.state.value.key)
        assertEquals(PanelDetectionStatus.Unavailable, controller.state.value.status)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cached panel pages are capped and evict oldest offscreen pages`() = runTest {
        val keys = (0..32).map { pageKey(pageIndex = it) }
        val detector = CountingPanelDetector(panels = listOf(panel(id = "a", left = 0f)))
        val controller = testController(
            scope = this,
            detector = detector,
            dispatcher = StandardTestDispatcher(testScheduler),
        ).apply {
            setEnabledState(true)
        }

        keys.forEach { key ->
            controller.onVisiblePagesChanged(listOf(key))
            controller.onPageImageReady(input(key))
            advanceUntilIdle()
        }

        assertEquals(33, detector.detectCount)

        controller.onVisiblePagesChanged(listOf(keys.first()))
        controller.onPageImageReady(input(keys.first()))
        advanceUntilIdle()

        assertEquals(34, detector.detectCount)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `offscreen detection waits until a visible page is ready`() = runTest {
        val visibleKey = pageKey(pageIndex = 3)
        val offscreenKey = pageKey(pageIndex = 4)
        val detector = CountingPanelDetector(panels = listOf(panel(id = "a", left = 0f)))
        val controller = testController(
            scope = this,
            detector = detector,
            dispatcher = StandardTestDispatcher(testScheduler),
        ).apply {
            setEnabledState(true)
        }

        controller.onVisiblePagesChanged(listOf(visibleKey))
        controller.onPageImageReady(input(offscreenKey))

        runCurrent()

        assertEquals(0, detector.detectCount)

        controller.onVisiblePagesChanged(listOf(offscreenKey))
        advanceUntilIdle()

        assertEquals(1, detector.detectCount)
        assertEquals(offscreenKey, controller.state.value.key)
    }

    @Test
    fun `detects and activates first panel for visible page`() = runTest {
        val key = pageKey()
        val controller = controller(
            panels = listOf(
                panel(id = "a", left = 0f),
                panel(id = "b", left = 120f),
            ),
        )

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(input(key))
        advanceUntilIdle()

        assertEquals(key, controller.state.value.key)
        assertEquals(2, controller.state.value.panels.size)
        assertEquals(0, controller.state.value.panelIndex)
        assertEquals("a", controller.activePanelFor(key)?.id)
    }

    @Test
    fun `cached panels are resorted when reading direction changes`() = runTest {
        var direction = PanelReadingDirection.LEFT_TO_RIGHT
        val key = pageKey()
        val detector = CountingPanelDetector(
            panels = listOf(
                panel(id = "left", left = 0f),
                panel(id = "right", left = 120f),
            ),
        )
        val controller = testController(
            scope = this,
            detector = detector,
            dispatcher = StandardTestDispatcher(testScheduler),
            readingDirection = { direction },
        ).apply {
            setEnabledState(true)
        }

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(input(key))
        advanceUntilIdle()

        assertEquals(listOf("left", "right"), controller.state.value.panels.map { it.id })
        assertEquals(1, detector.detectCount)

        direction = PanelReadingDirection.RIGHT_TO_LEFT
        controller.onPageImageReady(input(key))
        advanceUntilIdle()

        assertEquals(listOf("right", "left"), controller.state.value.panels.map { it.id })
        assertEquals(1, detector.detectCount)
    }

    @Test
    fun `next panel advances within same page`() = runTest {
        val key = pageKey()
        val controller = controller(
            panels = listOf(
                panel(id = "a", left = 0f),
                panel(id = "b", left = 120f),
            ),
        )

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(input(key))
        advanceUntilIdle()

        assertEquals(PanelMoveResult.Moved, controller.moveToNextPanel(key))
        assertEquals("b", controller.activePanelFor(key)?.id)
    }

    @Test
    fun `next panel returns no panel after last panel`() = runTest {
        val key = pageKey()
        val controller = controller(panels = listOf(panel(id = "a", left = 0f)))

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(input(key))
        advanceUntilIdle()

        assertEquals(PanelMoveResult.NoPanelStep, controller.moveToNextPanel(key))
        assertEquals("a", controller.activePanelFor(key)?.id)
    }

    @Test
    fun `previous panel from first panel returns no panel step`() = runTest {
        val key = pageKey()
        val controller = controller(panels = listOf(panel(id = "a", left = 0f)))

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(input(key))
        advanceUntilIdle()

        assertEquals(PanelMoveResult.NoPanelStep, controller.moveToPreviousPanel(key))
        assertEquals("a", controller.activePanelFor(key)?.id)
    }

    @Test
    fun `select panel activates requested index for current page`() = runTest {
        val key = pageKey()
        val controller = controller(
            panels = listOf(
                panel(id = "a", left = 0f),
                panel(id = "b", left = 120f),
            ),
        )

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(input(key))
        advanceUntilIdle()

        assertEquals(true, controller.selectPanel(key, 1))
        assertEquals(1, controller.state.value.panelIndex)
        assertEquals("b", controller.activePanelFor(key)?.id)
    }

    @Test
    fun `select panel rejects invalid index without changing active panel`() = runTest {
        val key = pageKey()
        val controller = controller(
            panels = listOf(
                panel(id = "a", left = 0f),
                panel(id = "b", left = 120f),
            ),
        )

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(input(key))
        advanceUntilIdle()

        assertEquals(false, controller.selectPanel(key, 4))
        assertEquals(0, controller.state.value.panelIndex)
        assertEquals("a", controller.activePanelFor(key)?.id)
    }

    @Test
    fun `offscreen page detection does not replace visible page state`() = runTest {
        val visibleKey = pageKey(pageIndex = 3)
        val offscreenKey = pageKey(pageIndex = 4)
        val controller = controller(panels = listOf(panel(id = "a", left = 0f)))

        controller.onVisiblePagesChanged(listOf(visibleKey))
        controller.onPageImageReady(input(visibleKey))
        advanceUntilIdle()

        assertEquals(visibleKey, controller.state.value.key)
        assertEquals("a", controller.activePanelFor(visibleKey)?.id)

        controller.onPageImageReady(input(offscreenKey))
        advanceUntilIdle()

        assertEquals(visibleKey, controller.state.value.key)
        assertEquals("a", controller.activePanelFor(visibleKey)?.id)
    }

    @Test
    fun `queued offscreen page activates when it becomes visible`() = runTest {
        val visibleKey = pageKey(pageIndex = 3)
        val offscreenKey = pageKey(pageIndex = 4)
        val controller = controller(panels = listOf(panel(id = "a", left = 0f)))

        controller.onVisiblePagesChanged(listOf(visibleKey))
        controller.onPageImageReady(input(offscreenKey))
        advanceUntilIdle()

        assertNull(controller.state.value.key)

        controller.onVisiblePagesChanged(listOf(offscreenKey))
        advanceUntilIdle()

        assertEquals(offscreenKey, controller.state.value.key)
        assertEquals(1, controller.state.value.panels.size)
        assertEquals(0, controller.state.value.panelIndex)
        assertEquals("a", controller.activePanelFor(offscreenKey)?.id)
    }

    @Test
    fun `disabled controller never moves panels`() = runTest {
        val key = pageKey()
        val controller = controller(
            panels = listOf(panel(id = "a", left = 0f)),
            enabled = false,
        )

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(input(key))
        advanceUntilIdle()

        assertEquals(PanelMoveResult.Disabled, controller.moveToNextPanel(key))
        assertNull(controller.activePanelFor(key))
    }

    @Test
    fun `disabling while detection is pending keeps panels inactive`() = runTest {
        val key = pageKey()
        val controller = testController(
            scope = this,
            detector = DelayedPanelDetector(listOf(panel(id = "a", left = 0f))),
            dispatcher = StandardTestDispatcher(testScheduler),
        ).apply {
            setEnabledState(true)
        }

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(input(key))

        assertEquals(PanelDetectionStatus.Pending, controller.state.value.status)

        controller.setEnabledState(false)
        advanceUntilIdle()

        assertEquals(false, controller.state.value.enabled)
        assertNull(controller.state.value.key)
        assertNull(controller.activePanelFor(key))
    }

    @Test
    fun `panel movement returns pending while detection is pending for page`() = runTest {
        val key = pageKey()
        val controller = testController(
            scope = this,
            detector = DelayedPanelDetector(listOf(panel(id = "a", left = 0f))),
            dispatcher = StandardTestDispatcher(testScheduler),
        ).apply {
            setEnabledState(true)
        }

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(input(key))

        assertEquals(PanelDetectionStatus.Pending, controller.state.value.status)
        assertEquals(PanelMoveResult.Pending, controller.moveToNextPanel(key))
        assertEquals(PanelMoveResult.Pending, controller.moveToPreviousPanel(key))
    }

    @Test
    fun `stale detector failure after disabling does not publish failed state`() = runTest {
        val key = pageKey()
        val controller = testController(
            scope = this,
            detector = NonCancellableFailingPanelDetector(),
            dispatcher = StandardTestDispatcher(testScheduler),
        ).apply {
            setEnabledState(true)
        }

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(input(key))

        assertEquals(PanelDetectionStatus.Pending, controller.state.value.status)

        controller.setEnabledState(false)
        advanceUntilIdle()

        assertEquals(false, controller.state.value.enabled)
        assertNull(controller.state.value.key)
        assertNull(controller.activePanelFor(key))
        assertEquals(PanelDetectionStatus.Idle, controller.state.value.status)
    }

    @Test
    fun `detector failure after enabled source turns off does not publish failed state`() = runTest {
        var enabled = true
        val key = pageKey()
        val controller = testController(
            scope = this,
            detector = DelayedFailingPanelDetector(),
            dispatcher = StandardTestDispatcher(testScheduler),
            isEnabled = { enabled },
        ).apply {
            setEnabledState(true)
        }

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(input(key))

        assertEquals(PanelDetectionStatus.Pending, controller.state.value.status)

        enabled = false
        advanceUntilIdle()

        assertEquals(false, controller.state.value.enabled)
        assertNull(controller.state.value.key)
        assertEquals(PanelDetectionStatus.Idle, controller.state.value.status)
    }

    @Test
    fun `detector success after enabled source turns off clears pending state`() = runTest {
        var enabled = true
        val key = pageKey()
        val controller = testController(
            scope = this,
            detector = DelayedPanelDetector(listOf(panel("one", 0f))),
            dispatcher = StandardTestDispatcher(testScheduler),
            isEnabled = { enabled },
        ).apply {
            setEnabledState(true)
        }

        controller.onVisiblePagesChanged(listOf(key))
        controller.onPageImageReady(input(key))

        assertEquals(PanelDetectionStatus.Pending, controller.state.value.status)

        enabled = false
        advanceUntilIdle()

        assertEquals(false, controller.state.value.enabled)
        assertNull(controller.state.value.key)
        assertNull(controller.activePanelFor(key))
        assertEquals(PanelDetectionStatus.Idle, controller.state.value.status)
    }

    private fun TestScope.controller(
        panels: List<ReaderPanel>,
        enabled: Boolean = true,
    ): PanelReadingController {
        return testController(
            scope = this,
            detector = StaticPanelDetector(panels),
            dispatcher = StandardTestDispatcher(testScheduler),
            isEnabled = { enabled },
        ).apply {
            setEnabledState(enabled)
        }
    }

    private fun pageKey(pageIndex: Int = 3): PanelPageKey {
        return PanelPageKey(
            chapterId = 1L,
            chapterUrl = "chapter://1",
            chapterName = "Chapter 1",
            pageIndex = pageIndex,
            imageUrl = "image://page-$pageIndex",
            pageUrl = "page://$pageIndex",
            isInsertPage = false,
            renderVariant = PanelPageRenderVariant.FULL,
        )
    }

    private fun input(
        key: PanelPageKey,
        imageBytes: ByteArray = byteArrayOf(1, 2, 3),
    ): PanelDetectionInput {
        return PanelDetectionInput(
            key = key,
            imageWidth = 240,
            imageHeight = 120,
            image = PanelDetectionImage.fromBytes(imageBytes),
        )
    }

    private fun panel(
        id: String,
        left: Float,
        top: Float = 0f,
        width: Float = 100f,
        height: Float = 100f,
    ): ReaderPanel {
        return ReaderPanel(
            id = id,
            bounds = RectF().apply {
                this.left = left
                this.top = top
                right = left + width
                bottom = top + height
            },
            confidence = 1f,
        )
    }

    private class DelayedPanelDetector(
        private val panels: List<ReaderPanel>,
    ) : PanelDetector {
        override suspend fun detect(input: PanelDetectionInput): PanelDetectionResult {
            delay(1_000)
            return PanelDetectionResult(panels)
        }
    }

    private class StaticPanelDetector(
        private val panels: List<ReaderPanel>,
    ) : PanelDetector {
        override suspend fun detect(input: PanelDetectionInput): PanelDetectionResult {
            return PanelDetectionResult(panels)
        }
    }

    private class CountingPanelDetector(
        private val panels: List<ReaderPanel>,
    ) : PanelDetector {
        var detectCount = 0
            private set

        override suspend fun detect(input: PanelDetectionInput): PanelDetectionResult {
            detectCount += 1
            return PanelDetectionResult(panels)
        }
    }

    private class TrackingDelayedPanelDetector(
        private val panels: List<ReaderPanel>,
    ) : PanelDetector {
        val startedKeys = mutableListOf<PanelPageKey>()
        var maxConcurrentDetects = 0
            private set

        private var activeDetects = 0

        override suspend fun detect(input: PanelDetectionInput): PanelDetectionResult {
            startedKeys += input.key
            activeDetects += 1
            maxConcurrentDetects = maxOf(maxConcurrentDetects, activeDetects)
            try {
                delay(1_000)
                return PanelDetectionResult(panels)
            } finally {
                activeDetects -= 1
            }
        }
    }

    private class NonCancellableFailingPanelDetector : PanelDetector {
        override suspend fun detect(input: PanelDetectionInput): PanelDetectionResult {
            withContext(NonCancellable) {
                delay(1_000)
                throw IllegalStateException("stale failure")
            }
        }
    }

    private class DelayedFailingPanelDetector : PanelDetector {
        override suspend fun detect(input: PanelDetectionInput): PanelDetectionResult {
            delay(1_000)
            throw IllegalStateException("disabled failure")
        }
    }
}
