package eu.kanade.tachiyomi.ui.main

import android.animation.ValueAnimator
import android.app.ActivityOptions
import android.app.SearchManager
import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.animation.doOnEnd
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.util.Consumer
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.NavigatorDisposeBehavior
import cafe.adriel.voyager.navigator.currentOrThrow
import dev.zacsweers.metro.Inject
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.interactor.GetIncognitoState
import eu.kanade.presentation.components.AdaptiveSheet
import eu.kanade.presentation.components.AppStateBanners
import eu.kanade.presentation.components.DownloadedOnlyBannerBackgroundColor
import eu.kanade.presentation.components.IncognitoModeBannerBackgroundColor
import eu.kanade.presentation.components.IndexingBannerBackgroundColor
import eu.kanade.presentation.more.AppMigratingScreen
import eu.kanade.presentation.more.settings.screen.browse.ExtensionStoresScreen
import eu.kanade.presentation.more.settings.screen.data.RestoreBackupScreen
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.DefaultNavigatorScreenTransition
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.GlobalSearchScreen
import eu.kanade.tachiyomi.ui.deeplink.DeepLinkScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.manga.MangaScreen
import eu.kanade.tachiyomi.ui.more.NewUpdateScreen
import eu.kanade.tachiyomi.ui.more.OnboardingScreen
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputCaptureRegistry
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.isBenchmarkBuildType
import eu.kanade.tachiyomi.util.system.isNavigationBarNeedsScrim
import eu.kanade.tachiyomi.util.system.updaterEnabled
import eu.kanade.tachiyomi.util.view.setComposeContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.app.di.AppGraph
import mihon.app.di.appGraph
import mihon.core.dualscreen.DualScreenState
import mihon.core.metro.metroGraph
import mihon.core.migration.Migrator
import mihon.domain.database.repository.DatabaseRepository
import mihon.feature.support.SupportUsScreen
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.automirroredrounded.OpenInNew
import mihon.icons.materialsymbols.roundedfilled.VolunteerActivism
import tachiyomi.core.common.Constants
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeSource
import kotlin.time.times

private const val DUAL_SCREEN_START_DEBOUNCE_MS = 1000L

class MainActivity : BaseActivity() {

    private val graph: AppGraph by lazy { metroGraph() }

    @Inject private lateinit var libraryPreferences: LibraryPreferences

    @Inject private lateinit var preferences: BasePreferences

    @Inject private lateinit var downloadCache: DownloadCache

    @Inject private lateinit var chapterCache: ChapterCache

    @Inject private lateinit var databaseRepository: DatabaseRepository

    private val isAppMigrating = MutableStateFlow(Migrator.isRunning)

    @Inject private lateinit var getIncognitoState: GetIncognitoState

    @Inject private lateinit var extensionManager: ExtensionManager

    // To be checked by splash screen. If true then splash screen will be removed.
    var ready = false

    private var navigator: Navigator? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            restoreAutoDisabledDualScreen()
            checkAndStartDualScreenActivity()
        }
        override fun onDisplayRemoved(displayId: Int) {
            if (!hasActiveSecondaryDisplay()) autoDisableDualScreen()
        }

        // A secondary display that was off coming back on is a change, not an add.
        override fun onDisplayChanged(displayId: Int) = restoreAutoDisabledDualScreen()
    }

    private fun hasActiveSecondaryDisplay(): Boolean {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return displayManager.displays.any {
            it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON
        }
    }

    // Turn dual-screen mode off so the user isn't stuck without the secondary display, but remember
    // that the app did it. On the Thor the second screen can briefly report off (screen asleep,
    // still waking when the app restarts), and writing the user's setting off for good made the
    // companion toggle seem to untick itself at random.
    private fun autoDisableDualScreen() {
        if (!preferences.enableDualScreenMode().get()) return
        preferences.dualScreenAutoDisabled().set(true)
        preferences.enableDualScreenMode().set(false)
    }

    private fun restoreAutoDisabledDualScreen() {
        if (!preferences.dualScreenAutoDisabled().get()) return
        if (preferences.enableDualScreenMode().get() || !hasActiveSecondaryDisplay()) return
        preferences.dualScreenAutoDisabled().set(false)
        preferences.enableDualScreenMode().set(true)
    }

    init {
        registerSecureActivity(this)
    }

    override fun onResume() {
        super.onResume()
        checkAndStartDualScreenActivity()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        DualScreenState.triggerRotationUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        graph.inject(this)
        val isLaunch = savedInstanceState == null

        // Prevent splash screen showing up on configuration changes
        val splashScreen = if (isLaunch) installSplashScreen() else null

        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            Migrator.awaitAndRelease()
            isAppMigrating.value = false
        }
        // Uncaught on purpose: a failed database migration otherwise leaves the migrating screen up for good,
        // while the crash screen tells what went wrong and lets the logs be shared
        lifecycleScope.launch { databaseRepository.awaitOpen() }

        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot) {
            finish()
            return
        }

        // The main UI belongs on the primary display. On a dual-screen device the companion has its
        // own launcher, and tapping the app icon there launches MainActivity on the secondary
        // display. The dual-screen logic then assumes MainActivity is on the primary screen and
        // keeps trying to put the companion dashboard on the display MainActivity itself occupies,
        // which spins up DualScreenActivity a couple of times a second. Bounce to the primary
        // display before any of that starts, so the icon on either screen opens the app the same way.
        if (currentDisplayId() != Display.DEFAULT_DISPLAY) {
            val options = ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            }
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                options.toBundle(),
            )
            finish()
            return
        }

        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        // Auto-disable DS mode on startup so users aren't stuck if the secondary display is gone,
        // and turn it back on if only the app had turned it off and the display is back.
        if (hasActiveSecondaryDisplay()) restoreAutoDisabledDualScreen() else autoDisableDualScreen()

        preferences.enableDualScreenMode().changes()
            .onEach { enabled ->
                if (enabled) {
                    // On, by the user or by the restore above: nothing left to restore.
                    preferences.dualScreenAutoDisabled().set(false)
                    checkAndStartDualScreenActivity()
                } else {
                    DualScreenState.close()
                    val intent = Intent(this, DualScreenActivity::class.java)
                    intent.action = DualScreenActivity.ACTION_FINISH
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                    }
                }
            }
            .launchIn(lifecycleScope)

        preferences.alwaysShowDashboard().changes()
            .drop(1)
            .onEach { checkAndStartDualScreenActivity() }
            .launchIn(lifecycleScope)

        // Companion dismissed by the user: step the primary display back too, so both screens
        // leave together. moveTaskToBack rather than finish -- the app stays in recents and
        // returning picks up where it left off.
        lifecycleScope.launch {
            DualScreenState.mainScreenLeaveRequests.collect {
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    moveTaskToBack(true)
                }
            }
        }

        DualScreenState.activeScreen
            .drop(1)
            .onEach { checkAndStartDualScreenActivity() }
            .launchIn(lifecycleScope)

        setComposeContent {
            val appMigrating by isAppMigrating.collectAsState()
            val databaseMigrating by databaseRepository.isMigrating.collectAsState()
            val migrating = appMigrating || databaseMigrating
            var migratingSince by remember { mutableStateOf(if (migrating) TimeSource.Monotonic.markNow() else null) }
            LaunchedEffect(migrating) {
                val since = migratingSince
                if (migrating) {
                    if (since == null) migratingSince = TimeSource.Monotonic.markNow()
                } else if (since != null) {
                    // A migration that finishes right away would otherwise only flash the screen
                    delay(MIGRATING_SCREEN_MIN_DURATION - since.elapsedNow())
                    migratingSince = null
                }
            }
            if (migrating || migratingSince != null) {
                AppMigratingScreen()
                // Release the splash screen so the reason for the wait shows
                LaunchedEffect(Unit) { ready = true }
                return@setComposeContent
            }

            val context = LocalContext.current

            var incognito by remember { mutableStateOf(false) }
            val downloadOnly by preferences.downloadedOnly.collectAsState()
            val indexing by downloadCache.isInitializing.collectAsState()

            val isSystemInDarkTheme = isSystemInDarkTheme()
            val statusBarBackgroundColor = when {
                indexing -> IndexingBannerBackgroundColor
                downloadOnly -> DownloadedOnlyBannerBackgroundColor
                incognito -> IncognitoModeBannerBackgroundColor
                else -> MaterialTheme.colorScheme.surface
            }
            LaunchedEffect(isSystemInDarkTheme, statusBarBackgroundColor) {
                // Draw edge-to-edge and set system bars color to transparent
                val lightStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.BLACK)
                val darkStyle = SystemBarStyle.dark(Color.TRANSPARENT)
                enableEdgeToEdge(
                    statusBarStyle = if (statusBarBackgroundColor.luminance() > 0.5) lightStyle else darkStyle,
                    navigationBarStyle = if (isSystemInDarkTheme) darkStyle else lightStyle,
                )
            }

            Navigator(
                screen = HomeScreen,
                disposeBehavior = NavigatorDisposeBehavior(disposeNestedNavigators = false, disposeSteps = true),
            ) { navigator ->
                LaunchedEffect(navigator) {
                    this@MainActivity.navigator = navigator

                    if (isLaunch) {
                        // Set start screen
                        handleIntentAction(intent, navigator)

                        // Reset Incognito Mode on relaunch
                        preferences.incognitoMode.set(false)
                    }

                    launch {
                        mihon.core.dualscreen.DualScreenState.mainScreenEvents.collectLatest { event ->
                            when (event) {
                                is mihon.core.dualscreen.DualScreenState.MainScreenEvent.OpenScreen -> {
                                    navigator.push(event.screen)
                                }
                            }
                        }
                    }

                    launch {
                        // Searches from the companion run where single-screen would have run them: in the
                        // source being browsed, or in the library
                        DualScreenState.mainScreenSearches.collect { search ->
                            val screen = navigator.lastItem
                            when {
                                screen is BrowseSourceScreen && screen.sourceId == search.sourceId -> {
                                    if (search.genre) screen.searchGenre(search.query) else screen.search(search.query)
                                }
                                screen is HomeScreen -> screen.search(search.query)
                                else -> navigator.push(BrowseSourceScreen(search.sourceId, search.query))
                            }
                        }
                    }
                }
                LaunchedEffect(navigator.lastItem) {
                    (navigator.lastItem as? BrowseSourceScreen)?.sourceId
                        .let(getIncognitoState::subscribe)
                        .collectLatest { incognito = it }
                }

                val scaffoldInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)
                Scaffold(
                    topBar = {
                        AppStateBanners(
                            downloadedOnlyMode = downloadOnly,
                            incognitoMode = incognito,
                            indexing = indexing,
                            modifier = Modifier.windowInsetsPadding(scaffoldInsets),
                        )
                    },
                    contentWindowInsets = scaffoldInsets,
                ) { contentPadding ->
                    // Consume insets already used by app state banners
                    Box {
                        // Shows current screen
                        DefaultNavigatorScreenTransition(
                            navigator = navigator,
                            modifier = Modifier
                                .padding(contentPadding)
                                .consumeWindowInsets(contentPadding),
                        )

                        // Draw navigation bar scrim when needed
                        if (remember { isNavigationBarNeedsScrim() }) {
                            Spacer(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
                                    .alpha(0.8f)
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                            )
                        }
                    }
                }

                // Pop source-related screens when incognito mode is turned off
                LaunchedEffect(Unit) {
                    preferences.incognitoMode.changes()
                        .drop(1)
                        .filter { !it }
                        .onEach {
                            val currentScreen = navigator.lastItem
                            if (currentScreen is BrowseSourceScreen ||
                                (currentScreen is MangaScreen && currentScreen.fromSource)
                            ) {
                                navigator.popUntilRoot()
                            }
                        }
                        .launchIn(this)
                }

                HandleOnNewIntent(context = context, navigator = navigator)

                if (!isBenchmarkBuildType) {
                    if (isLaunch) CheckForUpdates()
                    ShowOnboarding()
                    ShowDonationCampaign()
                }
            }
        }

        val startTime = System.currentTimeMillis()
        splashScreen?.setKeepOnScreenCondition {
            val elapsed = System.currentTimeMillis() - startTime
            elapsed <= SPLASH_MIN_DURATION || (!ready && elapsed <= SPLASH_MAX_DURATION)
        }
        setSplashScreenExitAnimation(splashScreen)

        if (isLaunch && libraryPreferences.autoClearChapterCache.get()) {
            lifecycleScope.launch(Dispatchers.IO) {
                chapterCache.clear()
            }
        }
    }

    // Guards against the companion start/finish feedback loop. DualScreenActivity.onDestroy calls
    // DualScreenState.close(), which flips activeScreen, which this class collects and reacts to by
    // starting the companion again. When something keeps finishing the companion (e.g. the main
    // window contending for the same display), that becomes a start/finish loop several times a
    // second. Refusing to restart within a short window of the last start breaks the cycle while
    // still allowing the single legitimate start.
    private var lastDualScreenStartElapsedMs = 0L

    /**
     * The display this activity is currently shown on, across API levels (Activity.display is 30+).
     */
    private fun currentDisplayId(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.displayId ?: Display.DEFAULT_DISPLAY
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.displayId
        }
    }

    private fun checkAndStartDualScreenActivity() {
        // Only open the companion while this activity is actually on screen.
        //
        // DualScreenForegroundWatcher closes the companion when the app leaves the
        // foreground, which finishes DualScreenActivity, whose onDestroy calls
        // DualScreenState.close(). That flips activeScreen to null, and the collector that
        // watches activeScreen reopened the companion at the dashboard -- so leaving the app
        // with a screen open reset the companion instead of closing it. activeScreen never
        // emits when it is already null, which is why leaving from the dashboard closed
        // correctly and leaving from anything else did not.
        //
        // onResume calls this unconditionally, so returning to the app still reopens it.
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return

        // The reader takes the second screen directly while it is open. Starting the dashboard
        // there now would land on top of the reader's Presentation; the reader finishes it to
        // reclaim the display, which flips activeScreen and brings us straight back here -- a
        // start/finish war a couple of times a second that reconfigures the display and blacks
        // out the companion. Stand down while the reader owns it.
        if (DualScreenState.readerOwnsSecondary.value) return

        // Do not manage the companion while this window is itself on a secondary display. That
        // happens when the app is opened from the companion's launcher; onCreate bounces it to the
        // primary display, but until that lands, starting the dashboard here puts it on the same
        // display this window occupies and they fight over it. Waiting until the window is on the
        // primary display means the dashboard is started once, cleanly, instead of flickering a few
        // times first. onResume runs this again after the bounce settles.
        if (currentDisplayId() != Display.DEFAULT_DISPLAY) return

        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val targetId = preferences.secondaryDisplayId().get()

        // Try to find the user's preferred display, otherwise fallback to the first external one
        var presentationDisplay = if (targetId != -1 && targetId != Display.DEFAULT_DISPLAY) {
            displayManager.getDisplay(targetId)
        } else {
            null
        }

        if (presentationDisplay == null) {
            presentationDisplay = displayManager.displays.find {
                it.displayId != Display.DEFAULT_DISPLAY &&
                    it.state != Display.STATE_OFF
            }
        }

        val dualScreenEnabled = preferences.enableDualScreenMode().get()
        val alwaysShowDashboard = preferences.alwaysShowDashboard().get()
        val activeScreen = DualScreenState.activeScreen.value

        if (presentationDisplay != null && dualScreenEnabled && (alwaysShowDashboard || activeScreen != null)) {
            val nowMs = android.os.SystemClock.elapsedRealtime()
            if (nowMs - lastDualScreenStartElapsedMs < DUAL_SCREEN_START_DEBOUNCE_MS) return
            lastDualScreenStartElapsedMs = nowMs

            val options = android.app.ActivityOptions.makeBasic()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                options.setLaunchDisplayId(presentationDisplay.displayId)
            }
            val intent = Intent(this, DualScreenActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            try {
                startActivity(intent, options.toBundle())
            } catch (e: Exception) {
                logcat(LogPriority.ERROR) { "Failed to start DualScreenActivity: ${e.message}" }
            }
        }
    }

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        when (val screen = navigator?.lastItem) {
            is AssistContentScreen -> {
                screen.onProvideAssistUrl()?.let { outContent.webUri = it.toUri() }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (ReaderInputCaptureRegistry.captureKeyEvent(event)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (ReaderInputCaptureRegistry.captureMotionEvent(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun reportFullyDrawn() {
        try {
            super.reportFullyDrawn()
        } catch (e: SecurityException) {
            // Some Samsung Android 9 to 11 builds deny the platform's fully drawn broadcast
            // and the report only feeds startup metrics, so it shouldn't crash the app
            logcat(LogPriority.WARN, e)
        }
    }

    @Composable
    private fun HandleOnNewIntent(context: Context, navigator: Navigator) {
        LaunchedEffect(Unit) {
            callbackFlow {
                val componentActivity = context as ComponentActivity
                val consumer = Consumer<Intent> { trySend(it) }
                componentActivity.addOnNewIntentListener(consumer)
                awaitClose { componentActivity.removeOnNewIntentListener(consumer) }
            }
                .collectLatest { handleIntentAction(it, navigator) }
        }
    }

    @Composable
    private fun CheckForUpdates() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        // App updates
        LaunchedEffect(Unit) {
            if (!updaterEnabled) return@LaunchedEffect
            var lastCheck: Long? = null
            // The app can stay open for days now, so a cold start alone rarely checks.
            // Elapsed realtime keeps counting while the device sleeps, unlike the monotonic clock.
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    lastCheck?.let {
                        delay(it + APP_UPDATE_CHECK_INTERVAL.inWholeMilliseconds - SystemClock.elapsedRealtime())
                    }
                    lastCheck = SystemClock.elapsedRealtime()
                    try {
                        val result = context.appGraph.updateChecker.checkForUpdate()
                        if (
                            result is GetApplicationRelease.Result.NewUpdate &&
                            result.release.version != preferences.skippedAppUpdate.get() &&
                            navigator.items.none { it is NewUpdateScreen }
                        ) {
                            val updateScreen = NewUpdateScreen(
                                versionName = result.release.version,
                                changelogInfo = result.release.info,
                                releaseLink = result.release.releaseLink,
                                downloadLink = result.release.downloadLink,
                            )
                            navigator.push(updateScreen)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e)
                    }
                }
            }
        }

        // Extensions updates
        LaunchedEffect(Unit) {
            try {
                extensionManager.checkForUpdates()
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    @Composable
    private fun ShowOnboarding() {
        val navigator = LocalNavigator.currentOrThrow

        LaunchedEffect(Unit) {
            if (!preferences.shownOnboardingFlow.get() && navigator.lastItem !is OnboardingScreen) {
                navigator.push(OnboardingScreen())
            }
        }
    }

    @Composable
    private fun ShowDonationCampaign() {
        val navigator = LocalNavigator.currentOrThrow

        var showCampaign by remember { mutableStateOf(false) }
        if (showCampaign) {
            val uriHandler = LocalUriHandler.current
            val dismissSupportMessage = {
                preferences.donationCampaignShown.set(true)
                showCampaign = false
            }
            AdaptiveSheet(
                onDismissRequest = dismissSupportMessage,
                enableImplicitDismiss = false,
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(MR.strings.donationCampaign_title),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = stringResource(MR.strings.donationCampaign_paragraph1),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(MR.strings.donationCampaign_paragraph2),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(MR.strings.donationCampaign_paragraph3),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    HorizontalDivider()

                    Button(
                        modifier = Modifier
                            .padding(top = MaterialTheme.padding.small)
                            .padding(horizontal = MaterialTheme.padding.medium)
                            .fillMaxWidth(),
                        onClick = {
                            navigator.push(SupportUsScreen())
                            dismissSupportMessage()
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.RoundedFilled.VolunteerActivism,
                                contentDescription = null,
                            )
                            Text(
                                text = stringResource(MR.strings.label_support_us),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        modifier = Modifier
                            .padding(bottom = MaterialTheme.padding.small)
                            .padding(horizontal = MaterialTheme.padding.medium),
                    ) {
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            onClick = { uriHandler.openUri(Constants.URL_DISCORD) },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                            ) {
                                Text(
                                    text = stringResource(MR.strings.donationCampaign_contactPlatform),
                                )
                                Icon(
                                    imageVector = MaterialSymbols.AutoMirroredRounded.OpenInNew,
                                    contentDescription = null,
                                )
                            }
                        }
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            onClick = dismissSupportMessage,
                        ) {
                            Text(
                                text = stringResource(MR.strings.donationCampaign_dismiss),
                            )
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            try {
                val firstInstallTime = packageManager.getPackageInfo(packageName, 0).firstInstallTime
                val eligibleTime = Instant.fromEpochMilliseconds(firstInstallTime).plus(6 * 30.days)
                showCampaign = (Clock.System.now() >= eligibleTime && !preferences.donationCampaignShown.get())
            } catch (_: PackageManager.NameNotFoundException) {
            }
        }
    }

    /**
     * Sets custom splash screen exit animation on devices prior to Android 12.
     *
     * When custom animation is used, status and navigation bar color will be set to transparent and will be restored
     * after the animation is finished.
     */
    @Suppress("Deprecation")
    private fun setSplashScreenExitAnimation(splashScreen: SplashScreen?) {
        val root = findViewById<View>(android.R.id.content)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && splashScreen != null) {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT

            splashScreen.setOnExitAnimationListener { splashProvider ->
                // For some reason the SplashScreen applies (incorrect) Y translation to the iconView
                splashProvider.iconView.translationY = 0F

                val activityAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = LinearOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        root.translationY = value * 16.dpToPx
                    }
                }

                val splashAnim = ValueAnimator.ofFloat(1F, 0F).apply {
                    interpolator = FastOutSlowInInterpolator()
                    duration = SPLASH_EXIT_ANIM_DURATION
                    addUpdateListener { va ->
                        val value = va.animatedValue as Float
                        splashProvider.view.alpha = value
                    }
                    doOnEnd {
                        splashProvider.remove()
                    }
                }

                activityAnim.start()
                splashAnim.start()
            }
        }
    }

    private fun handleIntentAction(intent: Intent, navigator: Navigator): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) {
            NotificationReceiver.dismissNotification(
                applicationContext,
                notificationId,
                intent.getIntExtra("groupId", 0),
            )
        }

        val tabToOpen = when (intent.action) {
            Constants.SHORTCUT_LIBRARY -> HomeScreen.Tab.Library()
            Constants.SHORTCUT_MANGA -> {
                val idToOpen = intent.extras?.getLong(Constants.MANGA_EXTRA) ?: return false
                navigator.popUntilRoot()
                HomeScreen.Tab.Library(idToOpen)
            }
            Constants.SHORTCUT_UPDATES -> HomeScreen.Tab.Updates
            Constants.SHORTCUT_HISTORY -> HomeScreen.Tab.History
            Constants.SHORTCUT_SOURCES -> HomeScreen.Tab.Browse(false)
            Constants.SHORTCUT_EXTENSIONS -> HomeScreen.Tab.Browse(true)
            Constants.SHORTCUT_DOWNLOADS -> {
                navigator.popUntilRoot()
                HomeScreen.Tab.More(toDownloads = true)
            }
            Intent.ACTION_APPLICATION_PREFERENCES -> {
                navigator.popUntilRoot()
                navigator.push(SettingsScreen())
                null
            }
            Intent.ACTION_SEARCH, Intent.ACTION_SEND, "com.google.android.gms.actions.SEARCH_ACTION" -> {
                // If the intent match the "standard" Android search intent
                // or the Google-specific search intent (triggered by saying or typing "search *query* on *Tachiyomi*" in Google Search/Google Assistant)

                // Get the search query provided in extras, and if not null, perform a global search with it.
                val query = intent.getStringExtra(SearchManager.QUERY) ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!query.isNullOrEmpty()) {
                    navigator.popUntilRoot()
                    navigator.push(DeepLinkScreen(query))
                }
                null
            }
            INTENT_SEARCH -> {
                val query = intent.getStringExtra(INTENT_SEARCH_QUERY)
                if (!query.isNullOrEmpty()) {
                    val filter = intent.getStringExtra(INTENT_SEARCH_FILTER)
                    navigator.popUntilRoot()
                    navigator.push(GlobalSearchScreen(query, filter))
                }
                null
            }
            Intent.ACTION_VIEW -> {
                // Handling opening of backup files
                if (intent.data.toString().endsWith(".tachibk")) {
                    navigator.popUntilRoot()
                    navigator.push(RestoreBackupScreen(intent.data.toString()))
                }
                // Deep link to add extension store
                else if (intent.isAddExtensionStoreIntent()) {
                    intent.data?.getQueryParameter("url")?.let { repoUrl ->
                        navigator.popUntilRoot()
                        navigator.push(ExtensionStoresScreen(repoUrl))
                    }
                }
                null
            }
            else -> return false
        }

        if (tabToOpen != null) {
            lifecycleScope.launch { HomeScreen.openTab(tabToOpen) }
        }

        ready = true
        return true
    }

    private fun Intent.isAddExtensionStoreIntent(): Boolean {
        return (scheme == "tachiyomi" && data?.host == "add-repo") ||
            (scheme == "mihon" && data?.host == "extension-store")
    }

    companion object {
        const val INTENT_SEARCH = "eu.kanade.tachiyomi.SEARCH"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"
    }
}

// Splash screen
private const val SPLASH_MIN_DURATION = 500 // ms
private const val SPLASH_MAX_DURATION = 5000 // ms
private const val SPLASH_EXIT_ANIM_DURATION = 400L // ms

private val MIGRATING_SCREEN_MIN_DURATION = 1.seconds
private val APP_UPDATE_CHECK_INTERVAL = 6.hours
