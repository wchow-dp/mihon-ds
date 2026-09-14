package eu.kanade.tachiyomi.ui.reader.panel

import android.graphics.RectF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class PanelSorterTest {

    @Test
    fun `sorts left to right rows for LTR`() {
        val panels = listOf(
            panel(id = "bottom-right", left = 200f, top = 220f),
            panel(id = "top-right", left = 200f, top = 20f),
            panel(id = "bottom-left", left = 20f, top = 220f),
            panel(id = "top-left", left = 20f, top = 20f),
        )

        val result = PanelSorter.sort(panels, PanelReadingDirection.LEFT_TO_RIGHT)

        assertEquals(
            listOf("top-left", "top-right", "bottom-left", "bottom-right"),
            result.map { it.id },
        )
    }

    @Test
    fun `sorts right to left rows for RTL`() {
        val panels = listOf(
            panel(id = "bottom-right", left = 200f, top = 220f),
            panel(id = "top-right", left = 200f, top = 20f),
            panel(id = "bottom-left", left = 20f, top = 220f),
            panel(id = "top-left", left = 20f, top = 20f),
        )

        val result = PanelSorter.sort(panels, PanelReadingDirection.RIGHT_TO_LEFT)

        assertEquals(
            listOf("top-right", "top-left", "bottom-right", "bottom-left"),
            result.map { it.id },
        )
    }

    @Test
    fun `keeps lower right panel after spanning upper panels for RTL`() {
        val panels = listOf(
            panel(id = "lower-right", left = 780f, top = 492f, width = 485f, height = 340f),
            panel(id = "middle-strip", left = 625f, top = 155f, width = 150f, height = 660f),
            panel(id = "left-large", left = 90f, top = 155f, width = 525f, height = 660f),
            panel(id = "right-top", left = 780f, top = 155f, width = 485f, height = 285f),
            panel(id = "wide-strip", left = 90f, top = 850f, width = 1175f, height = 280f),
            panel(id = "bottom-left", left = 0f, top = 1130f, width = 680f, height = 660f),
            panel(id = "bottom-right", left = 690f, top = 1130f, width = 575f, height = 860f),
            panel(id = "left-face", left = 0f, top = 1720f, width = 680f, height = 480f),
        )

        val result = PanelSorter.sort(panels, PanelReadingDirection.RIGHT_TO_LEFT)

        assertEquals(
            listOf(
                "right-top",
                "lower-right",
                "middle-strip",
                "left-large",
                "wide-strip",
                "bottom-right",
                "bottom-left",
                "left-face",
            ),
            result.map { it.id },
        )
    }

    @Test
    fun `keeps slightly staggered top edges in the same row`() {
        val panels = listOf(
            panel(id = "right", left = 220f, top = 24f),
            panel(id = "left", left = 20f, top = 12f),
            panel(id = "bottom", left = 20f, top = 220f),
        )

        val result = PanelSorter.sort(panels, PanelReadingDirection.LEFT_TO_RIGHT)

        assertEquals(
            listOf("left", "right", "bottom"),
            result.map { it.id },
        )
    }

    @Test
    fun `filters invalid tiny panels`() {
        val panels = listOf(
            panel(id = "valid", left = 20f, top = 20f, width = 120f, height = 120f),
            panel(id = "tiny", left = 40f, top = 40f, width = 2f, height = 2f),
        )

        val result = PanelSorter.sort(panels, PanelReadingDirection.RIGHT_TO_LEFT)

        assertEquals(listOf("valid"), result.map { it.id })
    }

    @Test
    fun `removes near duplicate panels before sorting`() {
        val panels = listOf(
            panel(id = "left", left = 20f, top = 20f, width = 120f, height = 120f),
            panel(id = "right", left = 220f, top = 20f, width = 120f, height = 120f),
            panel(id = "right-duplicate", left = 224f, top = 24f, width = 118f, height = 118f, confidence = 0.91f),
        )

        val result = PanelSorter.sort(panels, PanelReadingDirection.LEFT_TO_RIGHT)

        assertEquals(listOf("left", "right"), result.map { it.id })
    }

    @Test
    fun `removes mostly contained panel duplicates`() {
        val panels = listOf(
            panel(id = "outer", left = 20f, top = 20f, width = 220f, height = 160f, confidence = 0.96f),
            panel(id = "inner-duplicate", left = 28f, top = 28f, width = 204f, height = 144f, confidence = 0.92f),
            panel(id = "next", left = 260f, top = 20f, width = 140f, height = 160f),
        )

        val result = PanelSorter.sort(panels, PanelReadingDirection.LEFT_TO_RIGHT)

        assertEquals(listOf("outer", "next"), result.map { it.id })
    }

    @Test
    fun `keeps smaller inset panels when they are not duplicate sized`() {
        val panels = listOf(
            panel(id = "outer", left = 20f, top = 20f, width = 300f, height = 220f, confidence = 0.96f),
            panel(id = "inset", left = 200f, top = 120f, width = 90f, height = 80f, confidence = 0.94f),
        )

        val result = PanelSorter.sort(panels, PanelReadingDirection.LEFT_TO_RIGHT)

        assertEquals(listOf("outer", "inset"), result.map { it.id })
    }

    @Test
    fun `removes duplicates with IOU greater than 0-72`() {
        val panels = listOf(
            panel(id = "base", left = 0f, top = 0f, width = 100f, height = 100f, confidence = 0.95f),
            // Offset by 15px: Intersection 85x100=8500, Union 10000+10000-8500=11500, IOU=0.739 (Should remove)
            panel(id = "overlap-high", left = 15f, top = 0f, width = 100f, height = 100f, confidence = 0.90f),
        )

        val result = PanelSorter.sort(panels, PanelReadingDirection.LEFT_TO_RIGHT)

        assertEquals(listOf("base"), result.map { it.id })
    }

    @Test
    fun `keeps panels with IOU less than 0-72`() {
        val panels = listOf(
            panel(id = "base", left = 0f, top = 0f, width = 100f, height = 100f, confidence = 0.95f),
            // Offset by 20px: Intersection 80x100=8000, Union 10000+10000-8000=12000, IOU=0.666 (Should keep)
            panel(id = "overlap-low", left = 20f, top = 0f, width = 100f, height = 100f, confidence = 0.90f),
        )

        val result = PanelSorter.sort(panels, PanelReadingDirection.LEFT_TO_RIGHT)

        assertEquals(listOf("base", "overlap-low"), result.map { it.id })
    }

    @Disabled(
        "Known bug, not a stale expectation. Nested panels come out inner-first under " +
            "ADVANCED_RECURSIVE: the inset is ordered before its container. Not the default " +
            "algorithm. Re-enable when PanelOrderEstimator handles containment.",
    )
    @Test
    fun `orders nested inset panel after container`() {
        val container = panel(id = "container", left = 20f, top = 20f, width = 400f, height = 400f)
        val inset = panel(id = "inset", left = 50f, top = 50f, width = 100f, height = 100f)
        val next = panel(id = "next", left = 450f, top = 20f, width = 100f, height = 100f)

        // Using sort to trigger internal estimateOrder with ADVANCED_RECURSIVE
        val result = PanelSorter.sort(listOf(inset, next, container), PanelReadingDirection.LEFT_TO_RIGHT, PanelSortingAlgorithm.ADVANCED_RECURSIVE)

        // Should be: container -> inset -> next
        assertEquals(listOf("container", "inset", "next"), result.map { it.id })
    }

    @Disabled(
        "Known bug, not a stale expectation. PanelSorter.removeDuplicatePanels applies " +
            "\"Row-Box Suppression\": a panel 1.5x larger than, and 90% containing, two or more " +
            "others is discarded as a false detection. A genuine large panel with two insets is " +
            "indistinguishable to that heuristic, so it is dropped and guided reading skips it. " +
            "Affects every sorting algorithm. Re-enable once the heuristic can tell the two apart.",
    )
    @Test
    fun `orders multi-level nested panels correctly`() {
        val a = panel(id = "A", left = 0f, top = 0f, width = 500f, height = 500f)
        val b = panel(id = "B", left = 50f, top = 50f, width = 300f, height = 300f)
        val c = panel(id = "C", left = 100f, top = 100f, width = 100f, height = 100f)

        val result = PanelSorter.sort(listOf(c, a, b), PanelReadingDirection.LEFT_TO_RIGHT, PanelSortingAlgorithm.ADVANCED_RECURSIVE)

        // Should resolve nested hierarchy: A (container) -> B (nested in A) -> C (nested in B)
        assertEquals(listOf("A", "B", "C"), result.map { it.id })
    }

    @Test
    fun `no containment relationship produces standard order`() {
        val p1 = panel(id = "1", left = 200f, top = 20f)
        val p2 = panel(id = "2", left = 20f, top = 20f)

        val result = PanelSorter.sort(listOf(p1, p2), PanelReadingDirection.RIGHT_TO_LEFT, PanelSortingAlgorithm.ADVANCED_RECURSIVE)

        assertEquals(listOf("1", "2"), result.map { it.id })
    }

    // When panels overlap enough that no clean cut exists, ADVANCED_RECURSIVE drops to its
    // fallback sort. That used to rank by centerY, which sank a tall panel below shorter
    // neighbours starting at the same height: a panel spanning two rows has its midpoint
    // between them. Observed in normal reading, not a synthetic case.
    @Test
    fun `fallback orders a tall panel ahead of the shorter panels it overlaps`() {
        val tall = panel(id = "tall", left = 0f, top = 0f, width = 400f, height = 400f)
        val topRight = panel(id = "topRight", left = 200f, top = 0f, width = 400f, height = 210f)
        val botRight = panel(id = "botRight", left = 200f, top = 190f, width = 400f, height = 210f)

        val result = PanelSorter.sort(
            listOf(topRight, botRight, tall),
            PanelReadingDirection.LEFT_TO_RIGHT,
            PanelSortingAlgorithm.ADVANCED_RECURSIVE,
        )

        assertEquals(listOf("tall", "topRight", "botRight"), result.map { it.id })
    }

    @Test
    fun `fallback row tolerance absorbs jitter in detected top edges`() {
        val tall = panel(id = "tall", left = 0f, top = 4f, width = 400f, height = 400f)
        val topRight = panel(id = "topRight", left = 200f, top = 0f, width = 400f, height = 210f)
        val botRight = panel(id = "botRight", left = 200f, top = 193f, width = 400f, height = 210f)

        val result = PanelSorter.sort(
            listOf(topRight, botRight, tall),
            PanelReadingDirection.LEFT_TO_RIGHT,
            PanelSortingAlgorithm.ADVANCED_RECURSIVE,
        )

        // A 4px difference in top edge must not split these into separate rows.
        assertEquals(listOf("tall", "topRight", "botRight"), result.map { it.id })
    }

    @Test
    fun `fallback reads right to left within a row`() {
        val tall = panel(id = "tall", left = 200f, top = 0f, width = 400f, height = 400f)
        val topLeft = panel(id = "topLeft", left = 0f, top = 0f, width = 400f, height = 210f)
        val botLeft = panel(id = "botLeft", left = 0f, top = 190f, width = 400f, height = 210f)

        val result = PanelSorter.sort(
            listOf(topLeft, botLeft, tall),
            PanelReadingDirection.RIGHT_TO_LEFT,
            PanelSortingAlgorithm.ADVANCED_RECURSIVE,
        )

        assertEquals(listOf("tall", "topLeft", "botLeft"), result.map { it.id })
    }

    private fun panel(
        id: String,
        left: Float,
        top: Float,
        width: Float = 120f,
        height: Float = 120f,
        confidence: Float = 0.99f,
    ): ReaderPanel {
        return ReaderPanel(
            id = id,
            bounds = RectF().apply {
                this.left = left
                this.top = top
                right = left + width
                bottom = top + height
            },
            confidence = confidence,
        )
    }
}
