package eu.kanade.tachiyomi.ui.reader.panel

import android.graphics.RectF
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.math.max
import kotlin.math.min

object YoloPanelPostProcessor {
    private const val PANEL_CLASS_ID = 0
    private const val MIN_BOX_SIZE = 2f

    fun process(
        detections: List<FloatArray>,
        transform: PanelModelTransform,
        confidenceThreshold: Float = 0.15f,
        nmsIouThreshold: Float = 0.35f, // Restored to 0.35 to be more aggressive against merging
    ): List<ReaderPanel> {
        logcat(LogPriority.DEBUG) { "Post-processing ${detections.size} raw detections" }
        detections.forEachIndexed { index, det ->
            logcat(LogPriority.DEBUG) {
                "raw[$index] conf=${det[4]} bounds=[${det[0]}, ${det[1]}, ${det[2]}, ${det[3]}]"
            }
        }
        val candidates = detections
            .asSequence()
            .mapNotNull { detection ->
                if (detection.size < 6) return@mapNotNull null

                val confidence = detection[4]
                val classId = detection[5].toInt()
                if (confidence < confidenceThreshold || classId != PANEL_CLASS_ID) {
                    return@mapNotNull null
                }

                // The bundled manga detector exports [x1, y1, x2, y2, confidence, class_id].
                val coordinates = detection.toModelInputCoordinates(transform.inputSize)
                val bounds = RectF().apply {
                    left = ((coordinates[0] - transform.padX) / transform.scale)
                        .coerceIn(0f, transform.sourceWidth.toFloat())
                    top = ((coordinates[1] - transform.padY) / transform.scale)
                        .coerceIn(0f, transform.sourceHeight.toFloat())
                    right = ((coordinates[2] - transform.padX) / transform.scale)
                        .coerceIn(0f, transform.sourceWidth.toFloat())
                    bottom = ((coordinates[3] - transform.padY) / transform.scale)
                        .coerceIn(0f, transform.sourceHeight.toFloat())
                }
                if (boundsWidth(bounds) < MIN_BOX_SIZE || boundsHeight(bounds) < MIN_BOX_SIZE) {
                    return@mapNotNull null
                }

                ReaderPanel(
                    id = "yolo-${bounds.left}-${bounds.top}-${bounds.right}-${bounds.bottom}",
                    bounds = bounds,
                    confidence = confidence,
                )
            }
            .sortedByDescending { it.confidence }
            .toList()

        val boxes = candidates.fold(mutableListOf<ReaderPanel>()) { kept, candidate ->
            if (kept.none { iou(it.bounds, candidate.bounds) > nmsIouThreshold }) {
                kept.add(candidate)
            }
            kept
        }

        logcat(LogPriority.INFO) {
            "Post-processing ${detections.size} raw detections into ${boxes.size} filtered boxes"
        }
        boxes.forEachIndexed { i, box ->
            logcat(LogPriority.DEBUG) {
                "Box $i: [${box.bounds.left}, ${box.bounds.top}, ${box.bounds.right}, ${box.bounds.bottom}] Conf: ${box.confidence}"
            }
        }

        return boxes
    }

    private fun FloatArray.toModelInputCoordinates(inputSize: Int): FloatArray {
        val coordinates = copyOfRange(0, 4)
        val looksNormalized = coordinates.all { it in -0.1f..1.1f }
        if (!looksNormalized) return coordinates

        return FloatArray(4) { index -> coordinates[index] * inputSize }
    }

    private fun iou(a: RectF, b: RectF): Float {
        val intersectionLeft = max(a.left, b.left)
        val intersectionTop = max(a.top, b.top)
        val intersectionRight = min(a.right, b.right)
        val intersectionBottom = min(a.bottom, b.bottom)
        val intersectionWidth = max(0f, intersectionRight - intersectionLeft)
        val intersectionHeight = max(0f, intersectionBottom - intersectionTop)
        val intersectionArea = intersectionWidth * intersectionHeight
        val unionArea = boundsWidth(a) * boundsHeight(a) + boundsWidth(b) * boundsHeight(b) - intersectionArea
        return if (unionArea <= 0f) 0f else intersectionArea / unionArea
    }

    private fun boundsWidth(bounds: RectF): Float {
        return bounds.right - bounds.left
    }

    private fun boundsHeight(bounds: RectF): Float {
        return bounds.bottom - bounds.top
    }
}
