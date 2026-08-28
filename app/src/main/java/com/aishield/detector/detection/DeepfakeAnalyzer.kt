package com.aishield.detector.detection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deepfake indicator score for detected face regions.
 *
 * - If assets/deepfake_detector.tflite exists, it is used. Contract:
 *     Input  float32 [1, 224, 224, 3] RGB 0..1 (face crop)
 *     Output float32 [1, 1] sigmoid -> probability the face is synthetic
 * - Otherwise a geometric baseline: faces in synthetic media tend to be
 *   significantly smoother than the surrounding scene (missing sensor noise),
 *   so we compare face-region noise vs scene noise.
 */
class DeepfakeAnalyzer(context: Context) {

    private val interpreter: Interpreter? = loadModel(context)

    fun analyze(scene: Bitmap, face: FaceHelper.Face): Double? {
        val faceBmp = cropFace(scene, face) ?: return null
        return try {
            interpreter?.let { runModel(it, faceBmp) } ?: baseline(scene, faceBmp)
        } catch (_: Throwable) {
            null
        } finally {
            faceBmp.recycle()
        }
    }

    private fun baseline(scene: Bitmap, faceBmp: Bitmap): Double {
        val faceGray = BitmapUtilsCompat.gray(faceBmp, 128, 128)
        val sceneGray = BitmapUtilsCompat.gray(scene, 128, 128)
        val faceNoise = HeuristicImageDetector.medianResidualNoise(faceGray, 128, 128)
        val sceneNoise = HeuristicImageDetector.medianResidualNoise(sceneGray, 128, 128)
        // Smooth face inside a noisy scene -> suspicious.
        val r = (sceneNoise - faceNoise) / max(sceneNoise, 0.5)
        val logit = 0.55 + 2.2 * r.coerceIn(-1.5, 1.5)
        return (1.0 / (1.0 + Math.exp(-logit))).coerceIn(0.0, 1.0)
    }

    private fun cropFace(scene: Bitmap, face: FaceHelper.Face): Bitmap? = runCatching {
        val size = (face.eyesDistance * 3.2f).roundToInt().coerceAtLeast(48)
        val left = (face.cx - size / 2).roundToInt()
        val top = (face.cy - size / 2.4f).roundToInt()
        val l = max(0, min(left, scene.width - 1))
        val t = max(0, min(top, scene.height - 1))
        val w = min(size, scene.width - l)
        val h = min(size, scene.height - t)
        if (w < 24 || h < 24) null else Bitmap.createBitmap(scene, l, t, w, h)
    }.getOrNull()

    private fun runModel(model: Interpreter, faceBmp: Bitmap): Double {
        val scaled = Bitmap.createScaledBitmap(faceBmp, 224, 224, true)
        val px = IntArray(224 * 224)
        scaled.getPixels(px, 0, 224, 0, 0, 224, 224)
        if (scaled !== faceBmp) scaled.recycle()
        val input = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }
        for (i in 0 until 224 * 224) {
            val p = px[i]
            val y = i / 224
            val x = i % 224
            input[0][y][x][0] = (p shr 16 and 0xFF) / 255f
            input[0][y][x][1] = (p shr 8 and 0xFF) / 255f
            input[0][y][x][2] = (p and 0xFF) / 255f
        }
        val output = Array(1) { FloatArray(1) }
        synchronized(model) { model.run(input, output) }
        return output[0][0].toDouble().coerceIn(0.0, 1.0)
    }

    private fun loadModel(context: Context): Interpreter? = try {
        val names = context.assets.list("") ?: emptyArray()
        if (!names.contains(MODEL_FILE)) null
        else {
            val bytes = context.assets.open(MODEL_FILE).use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
            Interpreter(buf)
        }
    } catch (_: Throwable) {
        null
    }

    private object BitmapUtilsCompat {
        fun gray(src: Bitmap, w: Int, h: Int): IntArray {
            val scaled = Bitmap.createScaledBitmap(src, w, h, true)
            val px = IntArray(w * h)
            scaled.getPixels(px, 0, w, 0, 0, w, h)
            if (scaled !== src) scaled.recycle()
            val out = IntArray(px.size)
            for (i in px.indices) {
                val p = px[i]
                out[i] = (30 * (p shr 16 and 0xFF) + 59 * (p shr 8 and 0xFF) + 11 * (p and 0xFF)) / 100
            }
            return out
        }
    }

    companion object {
        const val MODEL_FILE = "deepfake_detector.tflite"
    }
}
