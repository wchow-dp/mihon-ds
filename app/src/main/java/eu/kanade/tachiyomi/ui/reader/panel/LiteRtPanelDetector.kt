package eu.kanade.tachiyomi.ui.reader.panel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import tachiyomi.core.common.util.system.logcat
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LiteRtPanelDetector(
    context: Context,
    private val modelAssetPath: String = MODEL_ASSET_PATH,
    private val inputSize: Int = MODEL_INPUT_SIZE,
) : PanelDetector,
    Closeable {

    private val appContext = context.applicationContext
    private val lock = Any()
    private val detectionMutex = Mutex()
    private var interpreter: Interpreter? = null
    private var modelContractValidated = false
    private var closed = false

    override suspend fun detect(input: PanelDetectionInput): PanelDetectionResult {
        return try {
            detectionMutex.withLock {
                detectLocked(input)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.WARN, e) { "LiteRT panel detection failed" }
            PanelDetectionResult(emptyList())
        }
    }

    private fun detectLocked(input: PanelDetectionInput): PanelDetectionResult {
        if (isClosed()) {
            return PanelDetectionResult(emptyList())
        }
        val decoded = decodeBitmap(input) ?: return PanelDetectionResult(emptyList())
        val transform = PanelModelTransform.letterbox(
            sourceWidth = decoded.sourceWidth,
            sourceHeight = decoded.sourceHeight,
            inputSize = inputSize,
        )
        val inputBuffer = try {
            preprocess(decoded.bitmap, transform)
        } finally {
            decoded.bitmap.recycle()
        }
        val detections = synchronized(lock) {
            runInference(inputBuffer)
        }
        val panels = YoloPanelPostProcessor.process(detections, transform)

        return PanelDetectionResult(
            panels = panels,
        )
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            interpreter?.close()
            interpreter = null
        }
    }

    private fun loadModelBuffer(): ByteBuffer {
        val bytes = appContext.assets.open(modelAssetPath).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(bytes)
                rewind()
            }
    }

    private fun decodeBitmap(input: PanelDetectionInput): DecodedPanelBitmap? {
        val sourceWidth = input.imageWidth.takeIf { it > 0 }
        val sourceHeight = input.imageHeight.takeIf { it > 0 }
        val sampleSize = PanelBitmapDecodeConfig.calculateInSampleSize(
            sourceWidth = sourceWidth ?: 0,
            sourceHeight = sourceHeight ?: 0,
            targetMaxSide = inputSize * DECODE_TARGET_MAX_SIDE_MULTIPLIER,
            minimumShortSide = inputSize / DECODE_MIN_SHORT_SIDE_DIVISOR,
        )
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
            inSampleSize = sampleSize
        }
        val bitmap = input.image.newSource().use { source ->
            BitmapFactory.decodeStream(source.inputStream(), null, options)
        } ?: return null

        return DecodedPanelBitmap(
            bitmap = bitmap,
            sourceWidth = sourceWidth ?: bitmap.width,
            sourceHeight = sourceHeight ?: bitmap.height,
        )
    }

    private fun preprocess(
        bitmap: Bitmap,
        transform: PanelModelTransform,
    ): ByteBuffer {
        val letterboxed = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
        try {
            Canvas(letterboxed).apply {
                drawColor(Color.BLACK)
                drawBitmap(
                    bitmap,
                    null,
                    transform.contentRect(),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
            }

            val buffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * java.lang.Float.BYTES)
                .order(ByteOrder.nativeOrder())
            val pixels = IntArray(inputSize * inputSize)
            letterboxed.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
            pixels.forEach { pixel ->
                buffer.putFloat(Color.red(pixel) / 255f)
                buffer.putFloat(Color.green(pixel) / 255f)
                buffer.putFloat(Color.blue(pixel) / 255f)
            }
            buffer.rewind()
            return buffer
        } finally {
            letterboxed.recycle()
        }
    }

    private fun runInference(inputBuffer: ByteBuffer): List<FloatArray> {
        if (closed) {
            return emptyList()
        }
        val interpreter = interpreter ?: try {
            val buffer = loadModelBuffer()
            logcat(LogPriority.INFO) { "Loading TFLite model from $modelAssetPath (Size: ${buffer.capacity()} bytes)" }
            Interpreter(
                buffer,
                Interpreter.Options().apply {
                    setNumThreads(INTERPRETER_THREAD_COUNT)
                },
            )
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "CRITICAL: Failed to load TFLite model!" }
            null
        } ?: return emptyList()

        if (!modelContractValidated) {
            validateModelContract(interpreter)
            modelContractValidated = true
            logcat(LogPriority.INFO) {
                "Model validated. Input shape: ${interpreter.getInputTensor(0).shape().contentToString()}, " +
                    "Output shape: ${interpreter.getOutputTensor(0).shape().contentToString()}"
            }
        }
        val outputTensor = interpreter.getOutputTensor(0)

        val shape = outputTensor.shape()
        val output = when (shape.size) {
            3 -> Array(shape[0]) { Array(shape[1]) { FloatArray(shape[2]) } }
            2 -> Array(shape[0]) { FloatArray(shape[1]) }
            else -> error("Unsupported panel detector output shape: ${shape.contentToString()}")
        }

        inputBuffer.rewind()
        interpreter.run(inputBuffer, output)
        return output.toDetections(shape)
    }

    private fun validateModelContract(interpreter: Interpreter) {
        val inputTensor = interpreter.getInputTensor(0)
        check(inputTensor.dataType() == DataType.FLOAT32) {
            "Unsupported panel detector input type: ${inputTensor.dataType()}"
        }
        check(inputTensor.shape().contentEquals(intArrayOf(1, inputSize, inputSize, 3))) {
            "Unsupported panel detector input shape: ${inputTensor.shape().contentToString()}"
        }

        val outputTensor = interpreter.getOutputTensor(0)
        check(outputTensor.dataType() == DataType.FLOAT32) {
            "Unsupported panel detector output type: ${outputTensor.dataType()}"
        }
        val outputShape = outputTensor.shape()
        check(
            (outputShape.size == 3 && outputShape[0] == 1 && outputShape[2] >= DETECTION_FIELD_COUNT) ||
                (outputShape.size == 2 && outputShape[1] >= DETECTION_FIELD_COUNT),
        ) {
            "Unsupported panel detector output shape: ${outputShape.contentToString()}"
        }
    }

    private fun isClosed(): Boolean {
        return synchronized(lock) {
            closed
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Any.toDetections(shape: IntArray): List<FloatArray> {
        return when (shape.size) {
            3 -> {
                val values = this as Array<Array<FloatArray>>
                when {
                    shape[2] >= DETECTION_FIELD_COUNT -> values[0].map { it.copyOf(DETECTION_FIELD_COUNT) }
                    shape[1] >= DETECTION_FIELD_COUNT -> (0 until shape[2]).map { index ->
                        FloatArray(DETECTION_FIELD_COUNT) { field -> values[0][field][index] }
                    }
                    else -> emptyList()
                }
            }
            2 -> {
                val values = this as Array<FloatArray>
                values.mapNotNull { row ->
                    row.takeIf { it.size >= DETECTION_FIELD_COUNT }?.copyOf(DETECTION_FIELD_COUNT)
                }
            }
            else -> emptyList()
        }
    }

    companion object {
        const val MODEL_ASSET_PATH = "panel/manga_panel_detector_int8.tflite"
        const val MODEL_INPUT_SIZE = 640
        private const val DETECTION_FIELD_COUNT = 6
        private const val DECODE_TARGET_MAX_SIDE_MULTIPLIER = 2
        private const val DECODE_MIN_SHORT_SIDE_DIVISOR = 2
        private const val INTERPRETER_THREAD_COUNT = 2
    }
}

private data class DecodedPanelBitmap(
    val bitmap: Bitmap,
    val sourceWidth: Int,
    val sourceHeight: Int,
)
