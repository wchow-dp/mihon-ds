package eu.kanade.tachiyomi.ui.reader.panel

import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.math.max
import kotlin.math.min

object PanelSorter {

    fun sort(
        panels: List<ReaderPanel>,
        direction: PanelReadingDirection,
        algorithm: PanelSortingAlgorithm = PanelSortingAlgorithm.ROW_BASED,
    ): List<ReaderPanel> {
        val valid = panels
            .filter { it.width >= MIN_PANEL_SIZE && it.height >= MIN_PANEL_SIZE }
            .removeDuplicatePanels()

        if (valid.isEmpty()) return emptyList()

        val rtl = direction == PanelReadingDirection.RIGHT_TO_LEFT
        logcat(LogPriority.INFO) { "SORTER: Sorting ${valid.size} panels using algorithm: $algorithm (RTL: $rtl)" }

        return when (algorithm) {
            PanelSortingAlgorithm.ROW_BASED -> sortRowBased(valid, direction)
            PanelSortingAlgorithm.XY_CUT -> sortXyCut(valid, direction)
            PanelSortingAlgorithm.ADVANCED_RECURSIVE -> {
                val pageWidth = panels.maxOf { it.bounds.right }.toInt()
                val pageHeight = panels.maxOf { it.bounds.bottom }.toInt()

                valid.forEach {
                    logcat(LogPriority.DEBUG) {
                        "panel id=${it.id} bounds=${it.bounds} area=${it.width * it.height} confidence=${it.confidence}"
                    }
                }

                val result = PanelOrderEstimator.estimateOrder(
                    valid,
                    rtl = rtl,
                    sourceWidth = pageWidth,
                    sourceHeight = pageHeight,
                )

                result.forEachIndexed { index, it ->
                    logcat(LogPriority.DEBUG) {
                        "result[$index] id=${it.id} bounds=${it.bounds}"
                    }
                }

                result
            }
        }
    }

    // ── Row-based sorting ─────────────────────────────────────────────────────

    private fun sortRowBased(
        valid: List<ReaderPanel>,
        direction: PanelReadingDirection,
    ): List<ReaderPanel> {
        val sortedByY = valid.sortedBy { it.centerY }
        val rows = mutableListOf<MutableList<ReaderPanel>>()
        sortedByY.forEach { panel ->
            val row = rows.firstOrNull { existingRow ->
                val rowMinTop = existingRow.minOf { it.bounds.top }
                val rowMaxBottom = existingRow.maxOf { it.bounds.bottom }
                val overlap = min(panel.bounds.bottom, rowMaxBottom) - max(panel.bounds.top, rowMinTop)
                val overlapRatio = if (panel.height > 0) overlap / panel.height else 0f
                val avgRowHeight = existingRow.map { it.height }.average().toFloat()
                val isTallSidebar = panel.height > avgRowHeight * 1.5f && overlapRatio > 0.8f
                overlapRatio > 0.5f && !isTallSidebar
            }
            if (row != null) {
                row += panel
            } else {
                rows += mutableListOf(panel)
            }
        }

        return rows.sortedBy { it.rowCenterY() }.flatMap { row ->
            when (direction) {
                PanelReadingDirection.LEFT_TO_RIGHT -> row.sortedBy { it.bounds.left }
                PanelReadingDirection.RIGHT_TO_LEFT -> row.sortedByDescending { it.bounds.left }
            }
        }
    }

    // ── XY-cut sorting ────────────────────────────────────────────────────────

    private fun sortXyCut(valid: List<ReaderPanel>, direction: PanelReadingDirection): List<ReaderPanel> {
        if (valid.size <= 1) return valid
        return try {
            xyCut(valid, direction, depth = 0)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Recursive XY-cut failed, falling back to Row-based" }
            sortRowBased(valid, direction)
        }
    }

    private fun xyCut(panels: List<ReaderPanel>, direction: PanelReadingDirection, depth: Int): List<ReaderPanel> {
        if (panels.size <= 1 || depth > MAX_RECURSION_DEPTH) return panels
        val hCut = findHorizontalCut(panels)
        if (hCut != null) {
            val top = panels.filter { it.centerY < hCut }
            val bottom = panels.filter { it.centerY >= hCut }
            if (top.isNotEmpty() && bottom.isNotEmpty()) {
                return xyCut(top, direction, depth + 1) + xyCut(bottom, direction, depth + 1)
            }
        }
        val vCut = findVerticalCut(panels)
        if (vCut != null) {
            val left = panels.filter { it.centerX < vCut }
            val right = panels.filter { it.centerX >= vCut }
            if (left.isNotEmpty() && right.isNotEmpty()) {
                return when (direction) {
                    PanelReadingDirection.RIGHT_TO_LEFT -> xyCut(right, direction, depth + 1) +
                        xyCut(left, direction, depth + 1)
                    PanelReadingDirection.LEFT_TO_RIGHT -> xyCut(left, direction, depth + 1) +
                        xyCut(right, direction, depth + 1)
                }
            }
        }
        return sortRowBased(panels, direction)
    }

    private fun findHorizontalCut(panels: List<ReaderPanel>): Float? {
        val yCoords = panels.flatMap { listOf(it.bounds.top, it.bounds.bottom) }.distinct().sorted()
        for (i in 0 until yCoords.size - 1) {
            val cutY = (yCoords[i] + yCoords[i + 1]) / 2f
            val isCleanEnough = panels.none { p ->
                val coreTop = p.bounds.top + (p.height * CORE_TOLERANCE_RATIO)
                val coreBottom = p.bounds.bottom - (p.height * CORE_TOLERANCE_RATIO)
                cutY > coreTop && cutY < coreBottom
            }
            if (isCleanEnough) return cutY
        }
        return null
    }

    private fun findVerticalCut(panels: List<ReaderPanel>): Float? {
        val xCoords = panels.flatMap { listOf(it.bounds.left, it.bounds.right) }.distinct().sorted()
        for (i in 0 until xCoords.size - 1) {
            val cutX = (xCoords[i] + xCoords[i + 1]) / 2f
            val isCleanEnough = panels.none { p ->
                val coreLeft = p.bounds.left + (p.width * CORE_TOLERANCE_RATIO)
                val coreRight = p.bounds.right - (p.width * CORE_TOLERANCE_RATIO)
                cutX > coreLeft && cutX < coreRight
            }
            if (isCleanEnough) return cutX
        }
        return null
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private fun List<ReaderPanel>.removeDuplicatePanels(): List<ReaderPanel> {
        val kept = mutableListOf<ReaderPanel>()
        // 1. Row-Box Suppression: Discard large boxes containing 2+ smaller panels
        val potentialContainers = sortedByDescending { it.area }
        val toDiscard = mutableSetOf<ReaderPanel>()
        for (large in potentialContainers) {
            val constituents = filter { small ->
                small !== large && large.area > small.area * 1.5f &&
                    large.bounds.contains(small.bounds, coverageThreshold = 0.90f)
            }
            if (constituents.size >= 2) {
                logcat(LogPriority.INFO) {
                    "Discarding Row-Box ${large.id} in favor of ${constituents.size} constituents"
                }
                toDiscard.add(large)
            }
        }
        // 2. Standard IOU Deduplication (0.72)
        filter { it !in toDiscard }.sortedByDescending { it.confidence }.forEach { candidate ->
            if (kept.none { it.isDuplicateOf(candidate) }) kept += candidate
        }
        return kept
    }

    private fun android.graphics.RectF.contains(other: android.graphics.RectF, coverageThreshold: Float): Boolean {
        val left = max(this.left, other.left)
        val top = max(this.top, other.top)
        val right = min(this.right, other.right)
        val bottom = min(this.bottom, other.bottom)
        if (right <= left || bottom <= top) return false
        val intersectionArea = (right - left) * (bottom - top)
        val otherArea = (other.right - other.left) * (other.bottom - other.top)
        return (intersectionArea / otherArea) >= coverageThreshold
    }

    private fun ReaderPanel.isDuplicateOf(other: ReaderPanel): Boolean {
        val left = max(bounds.left, other.bounds.left)
        val top = max(bounds.top, other.bounds.top)
        val right = min(bounds.right, other.bounds.right)
        val bottom = min(bounds.bottom, other.bounds.bottom)
        if (right <= left || bottom <= top) return false
        val intersection = (right - left) * (bottom - top)
        val union = area + other.area - intersection
        return if (union <= 0f) false else (intersection / union) >= 0.72f
    }

    private val ReaderPanel.area: Float get() = width * height
    private const val MIN_PANEL_SIZE = 8f
    private const val CORE_TOLERANCE_RATIO = 0.15f
    private const val MAX_RECURSION_DEPTH = 32
    private fun List<ReaderPanel>.rowCenterY(): Float = if (isEmpty()) 0f else map { it.centerY }.average().toFloat()
}
