package eu.kanade.tachiyomi.ui.reader

import eu.kanade.tachiyomi.ui.reader.panel.PanelPageKey

internal object ReaderPresentationPageLoadGuard {

    fun shouldStartPageLoad(
        expectedKey: PanelPageKey,
        requestedTag: Any?,
    ): Boolean {
        return requestedTag != expectedKey
    }

    /**
     * Whether a finished decode should be applied to the view.
     *
     * Deliberately does not consider whether the view is attached. The companion's display
     * connection can flap (the AYN Thor re-initialises the second screen repeatedly), which
     * detaches the presentation's view for a moment. A decode that finishes during that moment
     * used to be discarded here, and because the request marker was already stamped nothing ever
     * retried it -- the second screen stayed black until the page changed. Applying the image to a
     * briefly-detached view is safe: it is stored and drawn when the view reattaches. The only
     * reason to drop a finished decode is that the view has since been asked for a *different*
     * page, which the key comparison still catches.
     */
    fun canApplyPageLoad(
        expectedKey: PanelPageKey,
        requestedTag: Any?,
    ): Boolean {
        return requestedTag == expectedKey
    }
}
