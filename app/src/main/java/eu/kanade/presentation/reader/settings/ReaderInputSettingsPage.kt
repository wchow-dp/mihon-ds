package eu.kanade.presentation.reader.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.reader.input.ReaderAction
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputCaptureState
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputDefaultOptions
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputLayer
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputMapping
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputProfile
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputProfileResolver
import eu.kanade.tachiyomi.ui.reader.setting.ReaderInputSettingsScreenModel
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import java.text.NumberFormat
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SettingsItemsPaddings
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState

@Composable
internal fun ColumnScope.ReaderInputSettingsPage(screenModel: ReaderInputSettingsScreenModel) {
    val profile by screenModel.readerInputProfile.collectAsState()
    val captureState by screenModel.inputCaptureState.collectAsState()
    val volumeKeysEnabled by screenModel.preferences.readWithVolumeKeys.collectAsState()
    val volumeKeysInverted by screenModel.preferences.readWithVolumeKeysInverted.collectAsState()
    val defaultOptions = ReaderInputDefaultOptions(
        volumeKeysEnabled = volumeKeysEnabled,
        volumeKeysInverted = volumeKeysInverted,
    )
    val capturedConflict = capturedInputConflict(
        profile = profile,
        defaultOptions = defaultOptions,
        captureState = captureState,
    )

    HeadingItem(MR.strings.reader_input_global_mappings)
    ReaderInputMappingList(
        mappings = ReaderInputProfileResolver.effectiveGlobalMappings(profile, defaultOptions),
        layer = null,
        screenModel = screenModel,
    )
    ReaderInputActionPicker(layer = null, onSelect = screenModel::startInputCapture)

    HeadingItem(MR.strings.reader_input_mode_overrides)
    ReaderInputLayer.entries.forEach { layer ->
        ReaderInputLayerSection(
            layer = layer,
            mappings = ReaderInputProfileResolver.effectiveMappingsForLayer(profile, defaultOptions, layer),
            screenModel = screenModel,
        )
    }

    TextButton(
        modifier = Modifier.padding(horizontal = SettingsItemsPaddings.Horizontal),
        onClick = screenModel::resetInputProfile,
    ) {
        Text(stringResource(MR.strings.reader_input_reset_all))
    }

    if (captureState.request != null || captureState.capturedBinding != null) {
        ReaderInputCaptureDialog(
            captureState = captureState,
            conflict = capturedConflict,
            screenModel = screenModel,
        )
    }
}

@Composable
private fun ColumnScope.ReaderInputHoldScrollSpeedSettings(screenModel: ReaderInputSettingsScreenModel) {
    val numberFormat = remember { NumberFormat.getPercentInstance() }
    val speedsLinkedPref = screenModel.preferences.webtoonHoldScrollSpeedsLinked()
    val forwardSpeedPref = screenModel.preferences.webtoonHoldScrollForwardSpeed()
    val backwardSpeedPref = screenModel.preferences.webtoonHoldScrollBackwardSpeed()
    val speedsLinked by speedsLinkedPref.collectAsState()
    val forwardSpeed by forwardSpeedPref.collectAsState()
    val backwardSpeed by backwardSpeedPref.collectAsState()
    val speedRange = ReaderPreferences.let {
        it.WEBTOON_HOLD_SCROLL_SPEED_MIN..it.WEBTOON_HOLD_SCROLL_SPEED_MAX
    }

    HeadingItem(MR.strings.reader_input_hold_scroll_speed)
    CheckboxItem(
        label = stringResource(MR.strings.reader_input_link_hold_scroll_speeds),
        checked = speedsLinked,
        onClick = {
            val newValue = !speedsLinked
            speedsLinkedPref.set(newValue)
            if (newValue) {
                backwardSpeedPref.set(forwardSpeed)
            }
        },
    )

    if (speedsLinked) {
        SliderItem(
            value = forwardSpeed,
            valueRange = speedRange,
            label = stringResource(MR.strings.reader_input_hold_scroll_speed),
            valueString = numberFormat.format(forwardSpeed / 100f),
            onChange = {
                forwardSpeedPref.set(it)
                backwardSpeedPref.set(it)
            },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    } else {
        SliderItem(
            value = forwardSpeed,
            valueRange = speedRange,
            label = stringResource(MR.strings.reader_input_forward_hold_scroll_speed),
            valueString = numberFormat.format(forwardSpeed / 100f),
            onChange = { forwardSpeedPref.set(it) },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        SliderItem(
            value = backwardSpeed,
            valueRange = speedRange,
            label = stringResource(MR.strings.reader_input_backward_hold_scroll_speed),
            valueString = numberFormat.format(backwardSpeed / 100f),
            onChange = { backwardSpeedPref.set(it) },
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@Composable
private fun ColumnScope.ReaderInputLayerSection(
    layer: ReaderInputLayer,
    mappings: List<ReaderInputMapping>,
    screenModel: ReaderInputSettingsScreenModel,
) {
    Text(
        text = layer.readerInputLabel(),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(
            start = SettingsItemsPaddings.Horizontal,
            top = 6.dp,
            end = SettingsItemsPaddings.Horizontal,
            bottom = 4.dp,
        ),
    )

    if (layer == ReaderInputLayer.WEBTOON) {
        ReaderInputHoldScrollSpeedSettings(screenModel)
    }

    if (mappings.isEmpty()) {
        Text(
            text = stringResource(MR.strings.reader_input_using_global),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(
                start = SettingsItemsPaddings.Horizontal,
                end = SettingsItemsPaddings.Horizontal,
                bottom = 8.dp,
            ),
        )
    } else {
        ReaderInputMappingList(
            mappings = mappings,
            layer = layer,
            screenModel = screenModel,
        )
    }

    ReaderInputActionPicker(layer = layer, onSelect = screenModel::startInputCapture)
}

@Composable
private fun ReaderInputMappingList(
    mappings: List<ReaderInputMapping>,
    layer: ReaderInputLayer?,
    screenModel: ReaderInputSettingsScreenModel,
) {
    mappings.forEach { mapping ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = SettingsItemsPaddings.Horizontal,
                    end = SettingsItemsPaddings.Horizontal,
                    bottom = 8.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = mapping.binding.readerInputLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mapping.action.readerInputLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = {
                    if (mapping.id.startsWith("default_")) {
                        screenModel.disableDefaultMapping(mapping.id)
                    } else {
                        screenModel.deleteCustomMapping(layer = layer, mappingId = mapping.id)
                    }
                },
            ) {
                Text(stringResource(MR.strings.reader_input_delete_binding))
            }
        }
    }
}

@Composable
private fun ReaderInputActionPicker(
    layer: ReaderInputLayer?,
    onSelect: (ReaderInputLayer?, ReaderAction) -> Unit,
) {
    SettingsChipRow(MR.strings.reader_input_add_binding) {
        actionsForLayer(layer).forEach { action ->
            FilterChip(
                selected = false,
                onClick = { onSelect(layer, action) },
                label = { Text(action.readerInputLabel()) },
            )
        }
    }
}

private fun actionsForLayer(layer: ReaderInputLayer?): List<ReaderAction> {
    return when (layer) {
        null -> listOf(
            ReaderAction.NEXT,
            ReaderAction.PREVIOUS,
            ReaderAction.NEXT_PANEL,
            ReaderAction.PREVIOUS_PANEL,
            ReaderAction.NEXT_CHAPTER,
            ReaderAction.PREVIOUS_CHAPTER,
            ReaderAction.TOGGLE_MENU,
            ReaderAction.TOGGLE_COMPANION_PAGE,
            ReaderAction.TOGGLE_GUIDED_READING,
            ReaderAction.OPEN_READER_SETTINGS,
        )
        ReaderInputLayer.PAGED -> listOf(
            ReaderAction.NEXT,
            ReaderAction.PREVIOUS,
            ReaderAction.NEXT_PAGE,
            ReaderAction.PREVIOUS_PAGE,
            ReaderAction.TOGGLE_MENU,
        )
        ReaderInputLayer.WEBTOON -> listOf(
            ReaderAction.SCROLL_DOWN,
            ReaderAction.SCROLL_UP,
            ReaderAction.FAST_SCROLL_DOWN,
            ReaderAction.FAST_SCROLL_UP,
            ReaderAction.HOLD_SCROLL_DOWN,
            ReaderAction.HOLD_SCROLL_UP,
            ReaderAction.TOGGLE_MENU,
            ReaderAction.OPEN_READER_SETTINGS,
        )
        ReaderInputLayer.GUIDED_READING -> listOf(
            ReaderAction.NEXT_PANEL,
            ReaderAction.PREVIOUS_PANEL,
            ReaderAction.NEXT_PAGE,
            ReaderAction.PREVIOUS_PAGE,
        )
    }
}

private fun capturedInputConflict(
    profile: ReaderInputProfile,
    defaultOptions: ReaderInputDefaultOptions,
    captureState: ReaderInputCaptureState,
): ReaderInputMapping? {
    val binding = captureState.capturedBinding ?: return null
    val action = captureState.capturedAction ?: return null
    val trigger = captureState.capturedTrigger
    val sameLayerMappings = when (val layer = captureState.capturedLayer) {
        null -> ReaderInputProfileResolver.effectiveGlobalMappings(profile, defaultOptions)
        else -> ReaderInputProfileResolver.effectiveMappingsForLayer(profile, defaultOptions, layer)
    }
    val effectiveMapping = sameLayerMappings.lastOrNull { mapping ->
        ReaderInputProfileResolver.bindingsMatch(mapping.binding, binding) &&
            mapping.trigger == trigger
    }
    return effectiveMapping?.takeIf { it.action != action }
}
