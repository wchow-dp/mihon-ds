package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.reader.settings.ReaderInputSettingsPage
import eu.kanade.tachiyomi.ui.reader.setting.ReaderInputSettingsScreenModel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

object SettingsReaderControlsScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.reader_controls

    @Composable
    override fun getPreferences(): List<Preference> {
        return persistentListOf(
            Preference.PreferenceItem.CustomPreference(
                title = stringResource(MR.strings.reader_controls),
                subtitle = stringResource(MR.strings.reader_controls_summary),
                content = {
                    val viewModel = viewModel<ReaderInputSettingsScreenModel>()
                    Column {
                        ReaderInputSettingsPage(viewModel)
                    }
                },
            ),
        )
    }
}
