package com.beemovil.vision

import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

data class FaceResult(
    val boundingBox: Rect,
    val trackingId: Int?,
    val smilingProbability: Float?,
    val rightEyeOpenProbability: Float?,
    val leftEyeOpenProbability: Float?
)

class FaceAnalyzer(
    private val onFacesDetected: (List<FaceResult>, Int, Int) -> Unit
) : ImageAnalysis.Analyzer {

    // High accuracy, tracking enabled
    private val options = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .enableTracking()
        .build()

    private val detector = FaceDetection.getClient(options)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val results = faces.map { face ->
                        FaceResult(
                            boundingBox = face.boundingBox,
                            trackingId = face.trackingId,
                            smilingProbability = face.smilingProbability,
                            rightEyeOpenProbability = face.rightEyeOpenProbability,
                            leftEyeOpenProbability = face.leftEyeOpenProbability
                        )
                    }
                    onFacesDetected(results, image.width, image.height)
                }
                .addOnFailureListener {
                    // Ignore errors during live analysis
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
