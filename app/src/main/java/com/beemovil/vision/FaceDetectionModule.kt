package com.beemovil.vision

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.tasks.await

/**
 * FaceDetectionModule — Phase V9: La Experiencia
 *
 * Lightweight ML Kit face detection. On-device only.
 * Counts faces and detects emotions (smile probability).
 * Does NOT store biometric data. Does NOT recognize identities.
 *
 * Output: prompt hint like "Hay 3 personas, 2 sonriendo"
 */
class FaceDetectionModule {

    companion object {
        private const val TAG = "FaceDetection"
    }

    data class FaceHint(
        val faceCount: Int,
        val smilingCount: Int,
        val dominantEmotion: String,
        val promptHint: String
    )

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.15f)
            .build()
    )

    /**
     * Analyze a bitmap for faces. Returns null if no faces found.
     * Fast mode: ~15ms on modern devices.
     */
    suspend fun analyze(bitmap: Bitmap): FaceHint? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = detector.process(image).await()

            if (faces.isEmpty()) return null

            val smiling = faces.count { (it.smilingProbability ?: 0f) > 0.55f }
            val total = faces.size

            val emotion = when {
                smiling > total / 2 -> "sonrientes"
                smiling == 0 -> "serios"
                else -> "neutrales"
            }

            val hint = when {
                total == 1 && smiling > 0 -> "Hay 1 persona sonriendo"
                total == 1 -> "Hay 1 persona"
                smiling > 0 -> "Hay $total personas, $smiling sonriendo"
                else -> "Hay $total personas, la mayoría $emotion"
            }

            Log.d(TAG, "Detected: $total faces, $smiling smiling")
            FaceHint(total, smiling, emotion, hint)
        } catch (e: Exception) {
            Log.w(TAG, "Face detection failed: ${e.message}")
            null
        }
    }

    fun close() {
        try { detector.close() } catch (_: Exception) {}
    }
}
