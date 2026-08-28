package com.aishield.detector.detection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Production-path detector: runs a trained TFLite model bundled in assets.
 *
 * MODEL CONTRACT (must match what you export):
 *   File:      app/src/main/assets/ai_image_detector.tflite
 *   Input:     float32 [1, 224, 224, 3], RGB, normalized 0..1
 *   Output:    float32 [1, 1] sigmoid -> probability of AI-generated
 *
 * Any open "universal AI image detector" (e.g. a UniversalFakeDetect /
 * UNFD-style backbone) can be exported to match this contract. If the file
 * is absent the app silently falls back to HeuristicImageDetector.
 */
class TfliteImageDetector private constructor(
    private val interpreter: Interpreter
) : ImageDetector {

    override val name: String = "tflite"

    override fun score(bitmap: Bitmap): Double {
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT, INPUT, true)
        val px = IntArray(INPUT * INPUT)
        scaled.getPixels(px, 0, INPUT, 0, 0, INPUT, INPUT)
        if (scaled !== bitmap) scaled.recycle()

        val input = Array(1) {
            Array(INPUT) { Array(INPUT) { FloatArray(3) } }
        }
        for (i in 0 until INPUT * INPUT) {
            val p = px[i]
            val y = i / INPUT
            val x = i % INPUT
            input[0][y][x][0] = (p shr 16 and 0xFF) / 255f
            input[0][y][x][1] = (p shr 8 and 0xFF) / 255f
            input[0][y][x][2] = (p and 0xFF) / 255f
        }
        val output = Array(1) { FloatArray(1) }
        synchronized(interpreter) {
            interpreter.run(input, output)
        }
        return output[0][0].toDouble().coerceIn(0.0, 1.0)
    }

    fun close() {
        interpreter.close()
    }

    companion object {
        const val MODEL_FILE = "ai_image_detector.tflite"
        const val INPUT = 224

        fun createIfAvailable(context: Context): TfliteImageDetector? = try {
            val names = context.assets.list("") ?: emptyArray()
            if (!names.contains(MODEL_FILE)) {
                null
            } else {
                val bytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
                val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
                    order(ByteOrder.nativeOrder())
                    put(bytes)
                    rewind()
                }
                TfliteImageDetector(Interpreter(buffer))
            }
        } catch (_: Throwable) {
            null
        }
    }
}
