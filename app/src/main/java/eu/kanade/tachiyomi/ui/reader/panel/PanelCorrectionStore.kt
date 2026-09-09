package eu.kanade.tachiyomi.ui.reader.panel

import android.content.Context
import android.content.SharedPreferences
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.Locale

/**
 * Manages manual panel order corrections provided by the user.
 *
 * Uses a "Fuzzy Layout DNA" to identify pages across different manga
 * that share similar panel structures.
 *
 * Corrections are persisted against a *canonical* ordering of the panels
 * (top, then left) rather than against raw detector output. Raw detection order
 * is not stable from page to page, so raw indices stop meaning anything the
 * moment a pattern matches a page other than the one it was trained on.
 * Callers still speak in raw indices; the translation happens here.
 */
class PanelCorrectionStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("panel_order_corrections", Context.MODE_PRIVATE)

    /**
     * Panels in a stable, position-derived order. This is the frame of reference for
     * every persisted index, and it matches the ordering used to build the signature.
     */
    private fun canonicalOrder(panels: List<ReaderPanel>): List<ReaderPanel> {
        return panels.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
    }

    /**
     * @param panels the page's detected panels, in raw detection order.
     * @param correctedIndices raw indices of [panels], in the order the user arranged them.
     */
    fun saveCorrection(panels: List<ReaderPanel>, correctedIndices: List<Int>) {
        val canonical = canonicalOrder(panels)
        val canonicalIndices = correctedIndices.map { rawIndex ->
            panels.getOrNull(rawIndex)?.let { canonical.indexOf(it) } ?: -1
        }

        if (!isUsableOrder(canonicalIndices, canonical.size)) {
            logcat(LogPriority.ERROR) { "Layout Memory: refusing to save mismatched correction" }
            return
        }

        val hash = generateLayoutHash(panels)
        prefs.edit().putString(hash, canonicalIndices.joinToString(",")).apply()
        logcat(LogPriority.INFO) { "Layout Memory: Pattern Saved! DNA=$hash" }
    }

    /**
     * @return raw indices of [panels] in the trained order, or null when this layout is unknown.
     */
    fun getCorrection(panels: List<ReaderPanel>): List<Int>? {
        val hash = generateLayoutHash(panels)
        val stored = prefs.getString(hash, null) ?: return null

        val canonical = canonicalOrder(panels)
        val canonicalIndices = stored.split(",").mapNotNull { it.toIntOrNull() }
        if (!isUsableOrder(canonicalIndices, canonical.size)) {
            logcat(LogPriority.WARN) { "Layout Memory: discarding malformed entry DNA=$hash" }
            prefs.edit().remove(hash).apply()
            return null
        }

        logcat(LogPriority.INFO) { "Layout Memory: Pattern MATCHED! DNA=$hash" }
        return canonicalIndices.map { panels.indexOf(canonical[it]) }
    }

    /**
     * Number of layouts currently trained.
     */
    fun size(): Int = prefs.all.size

    /**
     * Forgets every trained layout.
     */
    fun clearAll() {
        val count = size()
        prefs.edit().clear().apply()
        logcat(LogPriority.INFO) { "Layout Memory: cleared trained layouts, was $count" }
    }

    /**
     * A stored order is only usable if it is a complete permutation of the page's panels.
     */
    private fun isUsableOrder(indices: List<Int>, panelCount: Int): Boolean {
        return indices.size == panelCount &&
            indices.toSet().size == panelCount &&
            indices.all { it in 0 until panelCount }
    }

    /**
     * Generates a stable Fuzzy Geometric Signature for a page.
     *
     * Uses 10% buckets to reduce jitter sensitivity.
     */
    private fun generateLayoutHash(panels: List<ReaderPanel>): String {
        if (panels.isEmpty()) return "empty"

        // 1. Find bounding box of all panels to normalize
        var minL = Float.MAX_VALUE; var minT = Float.MAX_VALUE
        var maxR = Float.MIN_VALUE; var maxB = Float.MIN_VALUE
        for (p in panels) {
            minL = minOf(minL, p.bounds.left); minT = minOf(minT, p.bounds.top)
            maxR = maxOf(maxR, p.bounds.right); maxB = maxOf(maxB, p.bounds.bottom)
        }
        val w = maxOf(1f, maxR - minL)
        val h = maxOf(1f, maxB - minT)

        // 2. Canonical ordering keeps the signature stable regardless of input list order
        val sortedPanels = canonicalOrder(panels)

        // 3. Round to nearest 10% (High fuzzy tolerance)
        val signature = sortedPanels.asSequence()
            .map { p ->
                val nl = (((p.bounds.left - minL) / w * 10).toInt() * 10)
                val nt = (((p.bounds.top - minT) / h * 10).toInt() * 10)
                val nr = (((p.bounds.right - minL) / w * 10).toInt() * 10)
                val nb = (((p.bounds.bottom - minT) / h * 10).toInt() * 10)
                "($nl,$nt,$nr,$nb)"
            }
            .joinToString("|")

        // V3: orders are stored against the canonical ordering. V2 entries recorded raw
        // detector indices and are intentionally unreadable under the new key.
        return String.format(Locale.ROOT, "FUZZY_V3:%d:%s", panels.size, signature)
    }
}
