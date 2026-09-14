package eu.kanade.tachiyomi.ui.reader.panel

import kotlin.math.max
import kotlin.math.min

/**
 * Advanced Panel reading-order estimator.
 *
 * This implementation uses the "Cleanest-Cut" recursive strategy.
 * It is designed to be the reliable geometric baseline, while manual
 * corrections handle title-specific or artistic edge cases.
 */
object PanelOrderEstimator {

    private const val DEFAULT_THRESHOLD = 0.25f
    private const val MAX_DEPTH = 32

    /** Share of the median panel height within which two panels count as the same row. */
    private const val ROW_TOLERANCE_RATIO = 0.35f

    fun estimateOrder(
        panels: List<ReaderPanel>,
        rtl: Boolean = true,
        sourceWidth: Int = 0,
        sourceHeight: Int = 0,
        interceptionRatioThreshold: Float = DEFAULT_THRESHOLD,
    ): List<ReaderPanel> {
        if (panels.size <= 1) return panels

        val out = mutableListOf<ReaderPanel>()
        divide(panels, rtl, interceptionRatioThreshold, depth = 0, out)
        return out
    }

    private fun divide(
        panels: List<ReaderPanel>,
        rtl: Boolean,
        threshold: Float,
        depth: Int,
        out: MutableList<ReaderPanel>,
    ) {
        if (panels.size == 1) {
            out.add(panels[0])
            return
        }
        if (depth > MAX_DEPTH) {
            out.addAll(stableFallbackSort(panels, rtl))
            return
        }

        val split = findBestSplit(panels, threshold, rtl)
        if (split == null || split.first.size == panels.size || split.second.size == panels.size) {
            out.addAll(stableFallbackSort(panels, rtl))
            return
        }

        divide(split.first, rtl, threshold, depth + 1, out)
        divide(split.second, rtl, threshold, depth + 1, out)
    }

    private fun findBestSplit(
        panels: List<ReaderPanel>,
        threshold: Float,
        rtl: Boolean,
    ): Pair<List<ReaderPanel>, List<ReaderPanel>>? {
        val bestH = tryAxis(panels, threshold, horizontal = true, rtl = rtl)
        val bestV = tryAxis(panels, threshold, horizontal = false, rtl = rtl)

        return when {
            bestH != null && bestV != null -> {
                // Pick whichever axis is cleaner (less panel interception)
                if (bestH.interception <= bestV.interception) bestH.groups else bestV.groups
            }
            bestH != null -> bestH.groups
            bestV != null -> bestV.groups
            else -> null
        }
    }

    private data class SplitCandidate(
        val groups: Pair<List<ReaderPanel>, List<ReaderPanel>>,
        val interception: Float
    )

    private fun tryAxis(
        panels: List<ReaderPanel>,
        threshold: Float,
        horizontal: Boolean,
        rtl: Boolean
    ): SplitCandidate? {
        val candidates = if (horizontal) {
            (panels.map { it.bounds.top } + panels.map { it.bounds.bottom }).distinct().sorted()
        } else {
            (panels.map { it.bounds.left } + panels.map { it.bounds.right }).distinct()
                .let { if (rtl) it.sortedDescending() else it.sorted() }
        }

        var best: SplitCandidate? = null
        for (pivot in candidates) {
            val result = tryPivot(panels, pivot, threshold, horizontal, rtl) ?: continue

            if (result.interception == 0f) return result
            if (best == null || result.interception < best.interception) {
                best = result
            }
        }
        return best
    }

    private fun tryPivot(
        panels: List<ReaderPanel>,
        pivot: Float,
        threshold: Float,
        horizontal: Boolean,
        rtl: Boolean,
    ): SplitCandidate? {
        val sideA = mutableListOf<ReaderPanel>()
        val sideB = mutableListOf<ReaderPanel>()
        var maxInterception = 0f

        for (panel in panels) {
            val sideResult = if (horizontal) {
                if (panel.bounds.bottom <= pivot) SideResult(0, 0f)
                else if (panel.bounds.top >= pivot) SideResult(1, 0f)
                else crossingInterception(panel.bounds.top, panel.bounds.bottom, pivot, threshold)
            } else if (rtl) {
                if (panel.bounds.left >= pivot) SideResult(0, 0f)
                else if (panel.bounds.right <= pivot) SideResult(1, 0f)
                else crossingInterception(-panel.bounds.right, -panel.bounds.left, -pivot, threshold)
            } else {
                if (panel.bounds.right <= pivot) SideResult(0, 0f)
                else if (panel.bounds.left >= pivot) SideResult(1, 0f)
                else crossingInterception(panel.bounds.left, panel.bounds.right, pivot, threshold)
            }

            if (sideResult.side == -1) return null
            maxInterception = max(maxInterception, sideResult.interception)
            if (sideResult.side == 0) sideA.add(panel) else sideB.add(panel)
        }

        if (sideA.isEmpty() || sideB.isEmpty()) return null
        return SplitCandidate(sideA to sideB, maxInterception)
    }

    private data class SideResult(val side: Int, val interception: Float)

    private fun crossingInterception(min: Float, max: Float, pivot: Float, threshold: Float): SideResult {
        val extent = max - min
        if (extent <= 0f) return SideResult(0, 0f)
        val ratio = minOf((pivot - min) / extent, 1f - (pivot - min) / extent)

        return when {
            ratio > threshold -> SideResult(-1, ratio)
            (pivot - min) / extent > 0.5f -> SideResult(0, ratio)
            else -> SideResult(1, ratio)
        }
    }

    /**
     * Orders panels the splitter could not separate cleanly.
     *
     * Ranks by the edge a reader reaches first, not by the panel's midpoint. Sorting on
     * centerY sank a tall panel below shorter neighbours that begin at the same height --
     * a panel spanning two rows has its midpoint between them, so it was read second, and
     * the taller it was the further it sank. Panels whose tops are within a tolerance of
     * each other count as one row and are ordered across the page in reading direction.
     *
     * The tolerance is taken from the median panel height so that one very tall panel does
     * not widen it; using the mean let the outlier that caused the problem mask it.
     */
    private fun stableFallbackSort(panels: List<ReaderPanel>, rtl: Boolean): List<ReaderPanel> {
        if (panels.size <= 1) return panels

        val heights = panels.map { it.height }.sorted()
        val medianHeight = heights[heights.size / 2]
        val tolerance = medianHeight * ROW_TOLERANCE_RATIO

        val rows = mutableListOf<MutableList<ReaderPanel>>()
        panels.sortedBy { it.bounds.top }.forEach { panel ->
            val row = rows.lastOrNull()
            if (row != null && panel.bounds.top - row.first().bounds.top <= tolerance) {
                row += panel
            } else {
                rows += mutableListOf(panel)
            }
        }

        return rows.flatMap { row ->
            row.sortedBy { if (rtl) -it.centerX else it.centerX }
        }
    }
}
