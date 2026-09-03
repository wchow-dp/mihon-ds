package eu.kanade.tachiyomi.ui.reader.setting

import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.kanade.tachiyomi.ui.reader.input.CapturedReaderInput
import eu.kanade.tachiyomi.ui.reader.input.ReaderAction
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputCaptureController
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputCaptureRegistry
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputDefaultOptions
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputLayer
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputLayerMappings
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputMapping
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputProfile
import eu.kanade.tachiyomi.ui.reader.input.ReaderInputProfileResolver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ReaderInputSettingsScreenModel(
    val preferences: ReaderPreferences = Injekt.get(),
    private val inputCaptureController: ReaderInputCaptureController = ReaderInputCaptureController(),
) : ViewModel() {

    private val readerInputProfilePreference = preferences.readerInputProfile()

    val inputCaptureState = inputCaptureController.state

    val readerInputProfile = readerInputProfilePreference
        .changes()
        .stateIn(viewModelScope, SharingStarted.Lazily, readerInputProfilePreference.get())

    fun startInputCapture(layer: ReaderInputLayer?, action: ReaderAction) {
        inputCaptureController.startCapture(layer, action)
    }

    fun cancelInputCapture() {
        inputCaptureController.cancel()
    }

    fun captureInputKeyEvent(event: KeyEvent): Boolean {
        return inputCaptureController.captureKeyEvent(event)
    }

    fun captureInputMotionEvent(event: MotionEvent): Boolean {
        return inputCaptureController.captureMotionEvent(event)
    }

    fun registerInputCapture(): ReaderInputCaptureRegistry.Registration {
        return ReaderInputCaptureRegistry.register(inputCaptureController)
    }

    fun consumeCapturedInput() {
        persistCapturedInput()
    }

    fun replaceCapturedInput() {
        persistCapturedInput()
    }

    fun deleteCustomMapping(layer: ReaderInputLayer?, mappingId: String) {
        readerInputProfilePreference.set(
            deleteCustomMapping(
                profile = readerInputProfilePreference.get(),
                layer = layer,
                mappingId = mappingId,
            ),
        )
    }

    fun disableDefaultMapping(mappingId: String) {
        val current = readerInputProfilePreference.get()
        readerInputProfilePreference.set(
            current.copy(disabledDefaultIds = current.disabledDefaultIds + mappingId),
        )
    }

    fun resetInputProfile() {
        inputCaptureController.cancel()
        readerInputProfilePreference.set(ReaderInputProfile())
    }

    private fun persistCapturedInput() {
        val captured = inputCaptureController.consumeCaptured() ?: return
        val mapping = ReaderInputMapping(
            id = customMappingId(captured),
            binding = captured.binding,
            trigger = captured.trigger,
            action = captured.action,
        )
        readerInputProfilePreference.set(
            replaceCapturedMappingInProfile(
                profile = readerInputProfilePreference.get(),
                defaultOptions = currentDefaultOptions(),
                layer = captured.layer,
                mapping = mapping,
            ),
        )
    }

    private fun currentDefaultOptions(): ReaderInputDefaultOptions {
        return ReaderInputDefaultOptions(
            volumeKeysEnabled = preferences.readWithVolumeKeys.get(),
            volumeKeysInverted = preferences.readWithVolumeKeysInverted.get(),
        )
    }

    companion object {
        fun addMappingToProfile(
            profile: ReaderInputProfile,
            layer: ReaderInputLayer?,
            mapping: ReaderInputMapping,
        ): ReaderInputProfile {
            if (layer == null) {
                return profile.copy(customGlobal = profile.customGlobal + mapping)
            }

            val existing = profile.customOverrides.firstOrNull { it.layer == layer }
            val updatedLayer = if (existing == null) {
                ReaderInputLayerMappings(
                    layer = layer,
                    mappings = listOf(mapping),
                )
            } else {
                existing.copy(mappings = existing.mappings + mapping)
            }

            return profile.copy(
                customOverrides = profile.customOverrides
                    .filterNot { it.layer == layer } + updatedLayer,
            )
        }

        fun replaceMappingInProfile(
            profile: ReaderInputProfile,
            layer: ReaderInputLayer?,
            mapping: ReaderInputMapping,
        ): ReaderInputProfile {
            if (layer == null) {
                return profile.copy(
                    customGlobal = profile.customGlobal
                        .filterNot {
                            ReaderInputProfileResolver.bindingsMatch(it.binding, mapping.binding) &&
                                it.trigger == mapping.trigger
                        } + mapping,
                )
            }

            val existing = profile.customOverrides.firstOrNull { it.layer == layer }
            val updatedLayer = if (existing == null) {
                ReaderInputLayerMappings(
                    layer = layer,
                    mappings = listOf(mapping),
                )
            } else {
                existing.copy(
                    mappings = existing.mappings
                        .filterNot {
                            ReaderInputProfileResolver.bindingsMatch(it.binding, mapping.binding) &&
                                it.trigger == mapping.trigger
                        } + mapping,
                )
            }

            val updatedOverrides = if (existing == null) {
                profile.customOverrides + updatedLayer
            } else {
                profile.customOverrides.map {
                    if (it.layer == layer) {
                        updatedLayer
                    } else {
                        it
                    }
                }
            }

            return profile.copy(customOverrides = updatedOverrides)
        }

        fun replaceCapturedMappingInProfile(
            profile: ReaderInputProfile,
            defaultOptions: ReaderInputDefaultOptions,
            layer: ReaderInputLayer?,
            mapping: ReaderInputMapping,
        ): ReaderInputProfile {
            val sameLayerMappings = when (layer) {
                null -> ReaderInputProfileResolver.effectiveGlobalMappings(profile, defaultOptions)
                else -> ReaderInputProfileResolver.effectiveMappingsForLayer(profile, defaultOptions, layer)
            }
            val effectiveMapping = sameLayerMappings.lastOrNull {
                ReaderInputProfileResolver.bindingsMatch(it.binding, mapping.binding) &&
                    it.trigger == mapping.trigger
            }
            if (effectiveMapping?.action == mapping.action && effectiveMapping.trigger == mapping.trigger) {
                return profile
            }
            val profileWithoutDefaultConflict = if (effectiveMapping?.id?.startsWith("default_") == true) {
                profile.copy(disabledDefaultIds = profile.disabledDefaultIds + effectiveMapping.id)
            } else {
                profile
            }

            return replaceMappingInProfile(
                profile = profileWithoutDefaultConflict,
                layer = layer,
                mapping = mapping,
            )
        }

        fun deleteCustomMapping(
            profile: ReaderInputProfile,
            layer: ReaderInputLayer?,
            mappingId: String,
        ): ReaderInputProfile {
            if (layer == null) {
                return profile.copy(
                    customGlobal = profile.customGlobal.filterNot { it.id == mappingId },
                )
            }

            return profile.copy(
                customOverrides = profile.customOverrides.map {
                    if (it.layer == layer) {
                        it.copy(mappings = it.mappings.filterNot { mapping -> mapping.id == mappingId })
                    } else {
                        it
                    }
                },
            )
        }

        private fun customMappingId(captured: CapturedReaderInput): String {
            val layer = captured.layer?.name?.lowercase() ?: "global"
            return buildString {
                append("custom_")
                append(layer)
                append('_')
                append(captured.action.name.lowercase())
                append('_')
                append(captured.trigger.name.lowercase())
                append('_')
                append(captured.binding.type.name.lowercase())
                append('_')
                append(captured.binding.keyCode)
                append('_')
                append(captured.binding.metaState)
                append('_')
                append(captured.binding.axis)
                append('_')
                append(captured.binding.direction.name.lowercase())
                append('_')
                append(captured.binding.threshold.toBits())
            }
        }
    }
}
