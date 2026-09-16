package eu.kanade.tachiyomi.ui.reader.input

interface ReaderActionTarget {
    fun handleActivityAction(action: ReaderAction): Boolean
    fun handleViewerAction(action: ReaderAction): Boolean
}

object ReaderActionDispatcher {

    private val activityActions = setOf(
        ReaderAction.NEXT_CHAPTER,
        ReaderAction.PREVIOUS_CHAPTER,
        ReaderAction.TOGGLE_MENU,
        ReaderAction.TOGGLE_COMPANION_PAGE,
        ReaderAction.TOGGLE_GUIDED_READING,
        ReaderAction.OPEN_READER_SETTINGS,
        // Rotation is handled by the activity, which owns the viewer. Anything missing from this
        // set is routed to handleViewerAction instead and silently returns false, so a new action
        // has to be registered here as well as in the enum, its label, and actionsForLayer.
        ReaderAction.ROTATE_PAGE,
        ReaderAction.ROTATE_PAGE_90,
        ReaderAction.ROTATE_PAGE_180,
        ReaderAction.ROTATE_PAGE_270,
    )

    fun dispatch(action: ReaderAction, target: ReaderActionTarget): Boolean {
        return if (action in activityActions) {
            target.handleActivityAction(action)
        } else {
            target.handleViewerAction(action)
        }
    }
}
