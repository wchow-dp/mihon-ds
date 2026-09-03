package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.kanade.domain.manga.model.readerOrientation
import eu.kanade.domain.manga.model.readingMode
import eu.kanade.tachiyomi.ui.reader.panel.PanelFocusEffect
import eu.kanade.tachiyomi.ui.reader.panel.PanelReadingSettings
import eu.kanade.tachiyomi.ui.reader.panel.PanelSortingAlgorithm
import eu.kanade.tachiyomi.ui.reader.setting.ReaderOrientation
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsViewModel
import eu.kanade.tachiyomi.ui.reader.setting.ReadingMode
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import java.text.NumberFormat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
internal fun ColumnScope.ReadingModePage(viewModel: ReaderSettingsViewModel) {
    HeadingItem(MR.strings.pref_category_for_this_series)
    val manga by viewModel.mangaFlow.collectAsState()

    val readingMode = remember(manga) { ReadingMode.fromPreference(manga?.readingMode?.toInt()) }
    SettingsChipRow(MR.strings.pref_category_reading_mode) {
        ReadingMode.entries.map {
            FilterChip(
                selected = it == readingMode,
                onClick = { viewModel.onChangeReadingMode(it) },
                label = { Text(stringResource(it.stringRes)) },
            )
        }
    }

    val orientation = remember(manga) { ReaderOrientation.fromPreference(manga?.readerOrientation?.toInt()) }
    SettingsChipRow(MR.strings.rotation_type) {
        ReaderOrientation.entries.map {
            FilterChip(
                selected = it == orientation,
                onClick = { viewModel.onChangeOrientation(it) },
                label = { Text(stringResource(it.stringRes)) },
            )
        }
    }

    HeadingItem(MR.strings.pref_category_secondary_display)
    SecondaryDisplayScrollSensitivityItem(viewModel)

    val viewer by viewModel.viewerFlow.collectAsState()
    if (viewer is WebtoonViewer) {
        WebtoonViewerSettings(viewModel)
    } else {
        PagerViewerSettings(viewModel)
    }
}

@Composable
private fun ColumnScope.SecondaryDisplayScrollSensitivityItem(viewModel: ReaderSettingsViewModel) {
    val numberFormat = remember { NumberFormat.getPercentInstance() }
    val secondaryDisplayScrollSensitivity by viewModel.preferences.secondaryDisplayScrollSensitivity().collectAsState()

    SliderItem(
        value = secondaryDisplayScrollSensitivity,
        valueRange = ReaderPreferences.let {
            it.SECONDARY_DISPLAY_SCROLL_SENSITIVITY_MIN..it.SECONDARY_DISPLAY_SCROLL_SENSITIVITY_MAX
        },
        label = stringResource(MR.strings.pref_secondary_display_scroll_sensitivity),
        valueString = numberFormat.format(secondaryDisplayScrollSensitivity / 100f),
        onChange = {
            viewModel.preferences.secondaryDisplayScrollSensitivity().set(it)
        },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

@Composable
private fun ColumnScope.PagerViewerSettings(viewModel: ReaderSettingsViewModel) {
    HeadingItem(MR.strings.pager_viewer)

    val navigationModePager by viewModel.preferences.navigationModePager.collectAsState()
    val pagerNavInverted by viewModel.preferences.pagerNavInverted.collectAsState()
    TapZonesItems(
        selected = navigationModePager,
        onSelect = viewModel.preferences.navigationModePager::set,
        invertMode = pagerNavInverted,
        onSelectInvertMode = viewModel.preferences.pagerNavInverted::set,
    )

    val imageScaleType by viewModel.preferences.imageScaleType.collectAsState()
    SettingsChipRow(MR.strings.pref_image_scale_type) {
        ReaderPreferences.ImageScaleType.mapIndexed { index, it ->
            FilterChip(
                selected = imageScaleType == index + 1,
                onClick = { viewModel.preferences.imageScaleType.set(index + 1) },
                label = { Text(stringResource(it)) },
            )
        }
    }

    val zoomStart by viewModel.preferences.zoomStart.collectAsState()
    SettingsChipRow(MR.strings.pref_zoom_start) {
        ReaderPreferences.ZoomStart.mapIndexed { index, it ->
            FilterChip(
                selected = zoomStart == index + 1,
                onClick = { viewModel.preferences.zoomStart.set(index + 1) },
                label = { Text(stringResource(it)) },
            )
        }
    }

    val isSideBySideViewEnabled by screenModel.preferences.sideBySideMode().collectAsState()

    CheckboxItem(
        label = stringResource(MR.strings.pref_crop_borders),
        pref = viewModel.preferences.cropBorders,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_landscape_zoom),
        pref = viewModel.preferences.landscapeZoom,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_navigate_pan),
        pref = viewModel.preferences.navigateToPan,
    )

    HeadingItem(MR.strings.pref_guided_reading)

    val panelReadingEnabled by viewModel.preferences.panelReadingPaged().collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_panel_reading),
        pref = viewModel.preferences.panelReadingPaged(),
    )

    if (panelReadingEnabled) {
        val panelSortingAlgorithm by viewModel.preferences.panelSortingAlgorithm().collectAsState()
        SettingsChipRow(MR.strings.pref_panel_sorting_algorithm) {
            PanelSortingAlgorithm.entries.map { algorithm ->
                FilterChip(
                    selected = panelSortingAlgorithm == algorithm,
                    onClick = { viewModel.preferences.panelSortingAlgorithm().set(algorithm) },
                    label = { Text(stringResource(algorithm.titleRes)) },
                )
            }
        }

        val panelTransitionMillis by viewModel.preferences.panelReadingTransitionMillis().collectAsState()
        SliderItem(
            label = stringResource(MR.strings.pref_panel_transition_duration),
            value = panelTransitionMillis,
            valueRange = PanelReadingSettings.PANEL_TRANSITION_MIN_MILLIS..PanelReadingSettings.PANEL_TRANSITION_MAX_MILLIS,
            steps = (PanelReadingSettings.PANEL_TRANSITION_MAX_MILLIS / PanelReadingSettings.PANEL_TRANSITION_STEP_MILLIS) - 1,
            valueString = if (panelTransitionMillis == 0) {
                stringResource(MR.strings.pref_panel_transition_instant)
            } else {
                "${panelTransitionMillis}ms"
            },
            onChange = {
                viewModel.preferences.panelReadingTransitionMillis()
                    .set(PanelReadingSettings.normalizeTransitionMillis(it))
            },
        )

        CheckboxItem(
            label = stringResource(MR.strings.pref_panel_primary_overlay),
            pref = viewModel.preferences.panelReadingPrimaryOverlay(),
        )

        val panelFocusEffect by viewModel.preferences.panelReadingFocusEffect().collectAsState()
        SettingsChipRow(MR.strings.pref_panel_focus_effect) {
            listOf(
                PanelFocusEffect.OFF to MR.strings.off,
                PanelFocusEffect.DARKEN to MR.strings.pref_panel_focus_effect_darken,
            ).map { (effect, title) ->
                FilterChip(
                    selected = panelFocusEffect == effect,
                    onClick = { viewModel.preferences.panelReadingFocusEffect().set(effect) },
                    label = { Text(stringResource(title)) },
                )
            }
        }

        CheckboxItem(
            label = stringResource(MR.strings.pref_panel_secondary_overlay),
            pref = viewModel.preferences.panelReadingSecondaryOverlay(),
        )

        val panelFocusEffectSecondary by viewModel.preferences.panelReadingFocusEffectSecondary().collectAsState()
        SettingsChipRow(MR.strings.pref_panel_focus_effect_secondary) {
            listOf(
                PanelFocusEffect.OFF to MR.strings.off,
                PanelFocusEffect.DARKEN to MR.strings.pref_panel_focus_effect_darken,
            ).map { (effect, title) ->
                FilterChip(
                    selected = panelFocusEffectSecondary == effect,
                    onClick = { viewModel.preferences.panelReadingFocusEffectSecondary().set(effect) },
                    label = { Text(stringResource(title)) },
                )
            }
        }

        if (panelFocusEffect != PanelFocusEffect.OFF) {
            val panelFocusStrength by viewModel.preferences.panelReadingFocusStrength().collectAsState()
            SliderItem(
                label = stringResource(MR.strings.pref_panel_focus_strength),
                value = panelFocusStrength,
                valueRange = PanelReadingSettings.PANEL_FOCUS_STRENGTH_MIN..PanelReadingSettings.PANEL_FOCUS_STRENGTH_MAX,
                steps = 19,
                valueString = "$panelFocusStrength%",
                onChange = {
                    viewModel.preferences.panelReadingFocusStrength()
                        .set(PanelReadingSettings.normalizeFocusStrength(it))
                },
            )
        }

        val panelReadingDirection by viewModel.preferences.panelReadingDirection().collectAsState()
        SettingsChipRow(MR.strings.pref_panel_reading_direction) {
            val directions = remember {
                listOf(
                    eu.kanade.tachiyomi.ui.reader.panel.PanelReadingDirection.LEFT_TO_RIGHT to MR.strings.panel_reading_direction_ltr,
                    eu.kanade.tachiyomi.ui.reader.panel.PanelReadingDirection.RIGHT_TO_LEFT to MR.strings.panel_reading_direction_rtl,
                )
            }
            directions.map { (direction, title) ->
                FilterChip(
                    selected = panelReadingDirection == direction,
                    onClick = { viewModel.preferences.panelReadingDirection().set(direction) },
                    label = { Text(stringResource(title)) },
                )
            }
        }
    }

    val dualPageSplitPaged by viewModel.preferences.dualPageSplitPaged.collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_dual_page_split),
        pref = viewModel.preferences.dualPageSplitPaged,
        enabled = !isSideBySideViewEnabled,
    )

    if (dualPageSplitPaged) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_dual_page_invert),
            pref = viewModel.preferences.dualPageInvertPaged,
        )
    }

    val dualPageRotateToFit by viewModel.preferences.dualPageRotateToFit.collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_page_rotate),
        pref = viewModel.preferences.dualPageRotateToFit,
    )

    if (dualPageRotateToFit) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_page_rotate_invert),
            pref = viewModel.preferences.dualPageRotateToFitInvert,
        )
    }

    HeadingItem(MR.strings.label_spanning)

    CheckboxItem(
        label = stringResource(MR.strings.side_by_side_view),
        pref = screenModel.preferences.sideBySideMode(),
    )

    if (isSideBySideViewEnabled) {
        val manualHingeGap by screenModel.preferences.manualHingeGap().collectAsState()
        SliderItem(
            label = stringResource(MR.strings.pref_hinge_gap),
            value = manualHingeGap,
            valueRange = 0..200,
            valueString = "${manualHingeGap}px",
            onChange = { screenModel.preferences.manualHingeGap().set(it) },
        )

        SettingsChipRow(MR.strings.pref_hinge_presets) {
            FilterChip(
                selected = manualHingeGap == 84,
                onClick = { screenModel.preferences.manualHingeGap().set(84) },
                label = { Text(stringResource(MR.strings.hinge_duo1)) },
            )
            FilterChip(
                selected = manualHingeGap == 66,
                onClick = { screenModel.preferences.manualHingeGap().set(66) },
                label = { Text(stringResource(MR.strings.hinge_duo2)) },
            )
            FilterChip(
                selected = manualHingeGap == 0,
                onClick = { screenModel.preferences.manualHingeGap().set(0) },
                label = { Text(stringResource(MR.strings.hinge_fold)) },
            )
        }
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_auto_enable_book_mode),
        pref = screenModel.preferences.autoEnableSideBySide(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.pref_auto_disable_book_mode),
        pref = screenModel.preferences.autoDisableSideBySide(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.pref_auto_adjust_hinge_gap),
        pref = screenModel.preferences.autoAdjustHingeGap(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.pref_auto_disable_on_start),
        pref = screenModel.preferences.autoDisableSideBySideOnStart(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.pref_center_single_page),
        pref = screenModel.preferences.centerSinglePage(),
    )
    if (isSideBySideViewEnabled) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_side_by_side_page_offset),
            pref = screenModel.preferences.sideBySidePageOffset(),
        )
    }
}

@Composable
private fun ColumnScope.WebtoonViewerSettings(viewModel: ReaderSettingsViewModel) {
    val numberFormat = remember { NumberFormat.getPercentInstance() }

    HeadingItem(MR.strings.webtoon_viewer)

    val navigationModeWebtoon by viewModel.preferences.navigationModeWebtoon.collectAsState()
    val webtoonNavInverted by viewModel.preferences.webtoonNavInverted.collectAsState()
    TapZonesItems(
        selected = navigationModeWebtoon,
        onSelect = viewModel.preferences.navigationModeWebtoon::set,
        invertMode = webtoonNavInverted,
        onSelectInvertMode = viewModel.preferences.webtoonNavInverted::set,
    )

    val webtoonSidePadding by viewModel.preferences.webtoonSidePadding.collectAsState()
    SliderItem(
        value = webtoonSidePadding,
        valueRange = ReaderPreferences.let { it.WEBTOON_PADDING_MIN..it.WEBTOON_PADDING_MAX },
        label = stringResource(MR.strings.pref_webtoon_side_padding),
        valueString = numberFormat.format(webtoonSidePadding / 100f),
        onChange = {
            viewModel.preferences.webtoonSidePadding.set(it)
        },
        pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )

    CheckboxItem(
        label = stringResource(MR.strings.pref_crop_borders),
        pref = viewModel.preferences.cropBordersWebtoon,
    )

    val dualPageSplitWebtoon by viewModel.preferences.dualPageSplitWebtoon.collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_dual_page_split),
        pref = viewModel.preferences.dualPageSplitWebtoon,
    )

    if (dualPageSplitWebtoon) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_dual_page_invert),
            pref = viewModel.preferences.dualPageInvertWebtoon,
        )
    }

    val dualPageRotateToFitWebtoon by viewModel.preferences.dualPageRotateToFitWebtoon.collectAsState()
    CheckboxItem(
        label = stringResource(MR.strings.pref_page_rotate),
        pref = viewModel.preferences.dualPageRotateToFitWebtoon,
    )

    if (dualPageRotateToFitWebtoon) {
        CheckboxItem(
            label = stringResource(MR.strings.pref_page_rotate_invert),
            pref = viewModel.preferences.dualPageRotateToFitInvertWebtoon,
        )
    }

    CheckboxItem(
        label = stringResource(MR.strings.pref_double_tap_zoom),
        pref = viewModel.preferences.webtoonDoubleTapZoomEnabled,
    )
    CheckboxItem(
        label = stringResource(MR.strings.pref_webtoon_disable_zoom_out),
        pref = viewModel.preferences.webtoonDisableZoomOut,
    )
}

@Composable
private fun ColumnScope.TapZonesItems(
    selected: Int,
    onSelect: (Int) -> Unit,
    invertMode: ReaderPreferences.TappingInvertMode,
    onSelectInvertMode: (ReaderPreferences.TappingInvertMode) -> Unit,
) {
    SettingsChipRow(MR.strings.pref_viewer_nav) {
        ReaderPreferences.TapZones.mapIndexed { index, it ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(stringResource(it)) },
            )
        }
    }

    if (selected != 5) {
        SettingsChipRow(MR.strings.pref_read_with_tapping_inverted) {
            ReaderPreferences.TappingInvertMode.entries.map {
                FilterChip(
                    selected = it == invertMode,
                    onClick = { onSelectInvertMode(it) },
                    label = { Text(stringResource(it.titleRes)) },
                )
            }
        }
    }
}
