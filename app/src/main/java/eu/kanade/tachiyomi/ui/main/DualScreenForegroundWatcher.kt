package eu.kanade.tachiyomi.ui.main

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import eu.kanade.domain.base.BasePreferences
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Collections
import java.util.WeakHashMap

/**
 * Closes the companion display when the app is no longer showing on the primary display.
 *
 * DualScreenActivity is singleInstance on its own task, so it survives the user pressing home
 * or switching apps — leaving the second screen showing Mihon over whatever is now in front.
 *
 * Tracks the started activities rather than counting them: a plain counter treats a stop
 * without a matching start as "reached zero", which fires spuriously while an activity is
 * being recreated during startup. The check is also delayed, because moving between two of
 * our own activities can stop the outgoing one before the incoming one starts, which would
 * otherwise look like the app had left the foreground.
 *
 * MainActivity.onResume already reopens the companion, so returning to the app restores it.
 */
class DualScreenForegroundWatcher(
    private val context: Context,
) : Application.ActivityLifecycleCallbacks {

    private val started: MutableSet<Activity> =
        Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())

    private val handler = Handler(Looper.getMainLooper())
    private val closeCompanion = Runnable {
        if (started.isNotEmpty()) return@Runnable
        val preferences = Injekt.get<BasePreferences>()
        if (!preferences.enableDualScreenMode().get()) return@Runnable
        if (!preferences.closeCompanionOnLeave().get()) return@Runnable

        val intent = Intent(context, DualScreenActivity::class.java).apply {
            action = DualScreenActivity.ACTION_FINISH
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // The companion may already be gone; nothing to close.
        }
    }

    override fun onActivityStarted(activity: Activity) {
        if (activity is DualScreenActivity) return
        started.add(activity)
        handler.removeCallbacks(closeCompanion)
    }

    override fun onActivityStopped(activity: Activity) {
        if (activity is DualScreenActivity) return
        started.remove(activity)
        if (started.isEmpty()) {
            handler.removeCallbacks(closeCompanion)
            handler.postDelayed(closeCompanion, SETTLE_DELAY_MS)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private companion object {
        /** Long enough for an activity swap or a recreation to settle. */
        const val SETTLE_DELAY_MS = 700L
    }
}
