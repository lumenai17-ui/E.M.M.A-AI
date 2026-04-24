package com.beemovil.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import com.beemovil.llm.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * VisionCaptureLoop — Phase V2: La Mirada
 *
 * Separated capture/analysis logic from UI (lesson from Bee Vision's 2,488-line monolith).
 * Thread-safe via AtomicBoolean. Provider cached per session.
 * Bitmap recycled immediately after encode. Session ID kill-switch for stale threads.
 */
class VisionCaptureLoop(private val context: Context) {

    companion object {
        private const val TAG = "VisionCaptureLoop"
    }

    // ── Thread safety ──
    val isProcessing = AtomicBoolean(false)
    val sessionId = AtomicLong(0L)

    // ── State (read by UI) ──
    var frameCount: Int = 0; private set
    var lastResult: String = ""; private set
    var lastError: String? = null; private set
    private var lastFrameHash: String = ""

    // V10: Error recovery
    private var consecutiveErrors = 0
    var isThrottled: Boolean = false; private set

    // ── Callbacks ──
    var onResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onFrameProcessed: ((Int) -> Unit)? = null
    var onFrameCaptured: ((Bitmap) -> Unit)? = null  // V5: for VisionRecorder
    var onBarcodeDetected: ((BarcodeScanner.ScanResult) -> Unit)? = null  // FR-1: Shopping mode barcode scan

    // FR-1: Track whether barcode scanning is active
    var barcodeScanEnabled: Boolean = false

    // ── Cached provider ──
    private var cachedProvider: LlmProvider? = null
    private var cachedProviderKey: String = ""

    /**
     * Start a new capture session. Returns the session ID.
     * Old sessions auto-terminate when they see a different sessionId.
     */
    fun newSession(): Long {
        val id = System.currentTimeMillis()
        sessionId.set(id)
        frameCount = 0
        lastResult = ""
        lastError = null
        lastFrameHash = ""
        consecutiveErrors = 0
        isThrottled = false
        cachedProvider = null
        cachedProviderKey = ""
        return id
    }

    /**
     * V10: Calculate adaptive interval based on battery level.
     * Returns multiplied interval to save battery when low.
     */
    fun getAdaptiveInterval(baseInterval: Int, batteryPct: Int): Long {
        val multiplier = when {
            batteryPct < 15 -> 3.0   // Critical: 3x slower
            batteryPct < 30 -> 2.0   // Low: 2x slower
            batteryPct < 50 -> 1.5   // Medium: 1.5x slower
            else -> 1.0              // Normal
        }
        // Also slow down if in error recovery
        val errorMultiplier = if (isThrottled) 2.0 else 1.0
        return (baseInterval * multiplier * errorMultiplier * 1000).toLong()
    }

    /**
     * Get or create the LLM provider for this session.
     * Cached to avoid creating a new provider per frame.
     */
    fun getOrCreateProvider(providerType: String, apiKey: String, model: String): LlmProvider? {
        val key = "$providerType:$apiKey:$model"
        if (key == cachedProviderKey && cachedProvider != null) return cachedProvider

        return try {
            val provider = LlmFactory.createProvider(providerType, apiKey, model)
            cachedProvider = provider
            cachedProviderKey = key
            provider
        } catch (e: Exception) {
            lastError = "Provider error: ${e.message?.take(80)}"
            onError?.invoke(lastError!!)
            null
        }
    }

    /**
     * Capture a frame from ImageCapture and process it through the LLM.
     * This is the core loop tick — called every N seconds.
     *
     * @param imageCapture CameraX ImageCapture instance
     * @param executor Camera executor
     * @param provider Pre-cached LLM provider
     * @param systemPrompt System prompt for the LLM
     * @param userPrompt User prompt (includes context, custom question, etc.)
     * @param mySessionId Session ID to check for staleness
     */
    fun captureAndAnalyze(
        imageCapture: ImageCapture,
        executor: Executor,
        provider: LlmProvider,
        systemPrompt: String,
        userPrompt: String,
        mySessionId: Long
    ) {
        if (sessionId.get() != mySessionId) return
        if (isProcessing.get()) return

        isProcessing.set(true)

        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                try {
                    if (sessionId.get() != mySessionId) {
                        isProcessing.set(false)
                        imageProxy.close()
                        return
                    }

                    val bitmap = imageProxy.toBitmap()
                    imageProxy.close() // Release camera immediately

                    // Resize + encode on background thread
                    Thread {
                        try {
                            if (sessionId.get() != mySessionId) {
                                isProcessing.set(false)
                                return@Thread
                            }

                            // V5: Notify recorder BEFORE resize (full-res copy)
                            val bitmapCopy = bitmap.copy(bitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                            onFrameCaptured?.invoke(bitmapCopy)

                            // FR-1: Barcode scan in parallel for Shopping mode
                            var barcodeContext = ""
                            if (barcodeScanEnabled) {
                                try {
                                    val scanCopy = bitmap.copy(bitmap.config ?: android.graphics.Bitmap.Config.ARGB_8888, false)
                                    val results = kotlinx.coroutines.runBlocking {
                                        BarcodeScanner.scan(scanCopy)
                                    }
                                    scanCopy.recycle()
                                    results.firstOrNull()?.let { scan ->
                                        barcodeContext = BarcodeScanner.buildProductContext(scan)
                                        onBarcodeDetected?.invoke(scan)
                                        Log.d(TAG, "Barcode detected: ${scan.rawValue}")
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Barcode scan error: ${e.message}")
                                }
                            }

                            val b64 = resizeAndEncode(bitmap)

                            // FR-1: Inject barcode product info into prompt
                            val enrichedPrompt = if (barcodeContext.isNotBlank()) {
                                "$userPrompt\n\n$barcodeContext"
                            } else userPrompt

                            val messages = listOf(
                                ChatMessage(role = "system", content = systemPrompt),
                                ChatMessage(role = "user", content = enrichedPrompt, images = listOf(b64))
                            )
                            val response = provider.complete(messages, emptyList())
                            val result = response.text ?: ""

                            frameCount++

                            // Anti-spam: Jaccard similarity check
                            val isDuplicate = if (lastFrameHash.isBlank()) false
                                else calculateJaccardSimilarity(lastFrameHash, result) > 0.75

                            if (!isDuplicate && result.isNotBlank()) {
                                lastFrameHash = result
                                lastResult = result
                                onResult?.invoke(result)
                                // V10: Reset error count on success
                                if (consecutiveErrors > 0) {
                                    Log.i(TAG, "Recovered after $consecutiveErrors errors")
                                    consecutiveErrors = 0
                                    isThrottled = false
                                }
                            }

                            onFrameProcessed?.invoke(frameCount)
                        } catch (e: Exception) {
                            // V10: Graceful error recovery
                            consecutiveErrors++
                            when {
                                consecutiveErrors >= 5 -> {
                                    isThrottled = true
                                    lastError = "Conexión inestable. Reduciendo frecuencia..."
                                    onError?.invoke("ALERTA: ${lastError}")
                                    Log.w(TAG, "5+ consecutive errors, throttling")
                                }
                                consecutiveErrors >= 3 -> {
                                    isThrottled = true
                                    lastError = "Reintentando con menor frecuencia..."
                                    onError?.invoke("RETRY: ${lastError}")
                                }
                                else -> {
                                    lastError = e.message?.take(100)
                                    onError?.invoke("Error: ${lastError}")
                                }
                            }
                            Log.e(TAG, "Analysis error (#$consecutiveErrors)", e)
                        } finally {
                            isProcessing.set(false)
                        }
                    }.start()
                } catch (e: Exception) {
                    lastError = "Frame error: ${e.message}"
                    isProcessing.set(false)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                lastError = "Capture: ${exception.message}"
                onError?.invoke(lastError!!)
                isProcessing.set(false)
            }
        })
    }

    /**
     * Resize bitmap to 512px max and encode to base64 JPEG.
     * Recycles the original bitmap immediately.
     */
    private fun resizeAndEncode(bitmap: Bitmap): String {
        // V10: Increased to 768px for better LLM accuracy
        val maxDim = 768
        val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
        val resized = Bitmap.createScaledBitmap(
            bitmap,
            maxOf(1, (bitmap.width * scale).toInt()),
            maxOf(1, (bitmap.height * scale).toInt()),
            true
        )
        bitmap.recycle()

        val baos = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 75, baos) // V10: 75% quality (was 70)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        resized.recycle()
        return b64
    }

    /** Jaccard similarity between two text strings (word-level) */
    private fun calculateJaccardSimilarity(a: String, b: String): Double {
        val setA = a.lowercase().split("\\s+".toRegex()).toSet()
        val setB = b.lowercase().split("\\s+".toRegex()).toSet()
        if (setA.isEmpty() && setB.isEmpty()) return 1.0
        val intersection = setA.intersect(setB).size
        val union = setA.union(setB).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    fun stop() {
        sessionId.set(0)
        cachedProvider = null
    }
}
