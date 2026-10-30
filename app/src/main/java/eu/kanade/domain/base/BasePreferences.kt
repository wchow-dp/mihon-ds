package eu.kanade.domain.base

import android.content.Context
import dev.icerock.moko.resources.StringResource
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import eu.kanade.tachiyomi.util.system.GLUtil
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.MR

@Inject
@SingleIn(AppScope::class)
class BasePreferences(
    val context: Context,
    private val preferenceStore: PreferenceStore,
) {

    val downloadedOnly: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("pref_downloaded_only"),
        false,
    )

    val incognitoMode: Preference<Boolean> = preferenceStore.getBoolean(Preference.appStateKey("incognito_mode"), false)

    // On by default: this is a dual-screen build, and MainActivity turns it off while no secondary
    // display is present, so a single-screen device is not left in a broken state.
    fun enableDualScreenMode() = preferenceStore.getBoolean(Preference.appStateKey("enable_dual_screen_mode"), true)

    // Set when MainActivity turned dual-screen mode off because the secondary display was missing,
    // rather than the user turning it off, so it can be turned back on once the display returns.
    fun dualScreenAutoDisabled() = preferenceStore.getBoolean(
        Preference.appStateKey("dual_screen_auto_disabled"),
        false,
    )

    fun secondaryDisplayId() = preferenceStore.getInt(Preference.appStateKey("secondary_display_id"), -1)

    fun swapPresentationRotation() = preferenceStore.getBoolean(Preference.appStateKey("swap_presentation_rotation"), false)

    fun alwaysShowDashboard() = preferenceStore.getBoolean(Preference.appStateKey("always_show_dashboard"), true)

    // On by default: the companion runs as its own task, so without this it keeps showing Mihon
    // on the second screen over whatever the user switched to.
    fun leaveAppWithCompanion() = preferenceStore.getBoolean(
        Preference.appStateKey("leave_app_with_companion"),
        true,
    )

    fun closeCompanionOnLeave() = preferenceStore.getBoolean(Preference.appStateKey("close_companion_on_leave"), true)

    val extensionInstaller: ExtensionInstallerPreference = ExtensionInstallerPreference(context, preferenceStore)

    val shownOnboardingFlow: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("onboarding_complete"),
        false,
    )

    enum class ExtensionInstaller(val titleRes: StringResource, val requiresSystemPermission: Boolean) {
        LEGACY(MR.strings.ext_installer_legacy, true),
        PACKAGEINSTALLER(MR.strings.ext_installer_packageinstaller, true),
        SHIZUKU(MR.strings.ext_installer_shizuku, false),
        PRIVATE(MR.strings.ext_installer_private, false),
    }

    val displayProfile: Preference<String> = preferenceStore.getString("pref_display_profile_key", "")

    val hardwareBitmapThreshold: Preference<Int> = preferenceStore.getInt(
        "pref_hardware_bitmap_threshold",
        GLUtil.SAFE_TEXTURE_LIMIT,
    )

    val installationId: Preference<String> = preferenceStore.getString(Preference.appStateKey("installation_id"), "")

    val donationCampaignShown: Preference<Boolean> = preferenceStore.getBoolean(
        Preference.appStateKey("donation_campaign_shown"),
        false,
    )

    val skippedAppUpdate: Preference<String> = preferenceStore.getString(
        Preference.appStateKey("skipped_app_update"),
        "",
    )
}
