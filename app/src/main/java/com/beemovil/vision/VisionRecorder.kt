package com.beemovil.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import com.beemovil.files.PublicFileWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * VisionRecorder — Phase V5: Timelapse recording
 *
 * Saves JPEG frames to disk during a vision session.
 * After stopping, can encode frames to MP4 timelapse via MediaCodec.
 * Memory-safe: frames go to disk, never accumulate in RAM.
 *
 * Storage: ~50 KB/frame × 720 frames/hr = ~36 MB/hr
 */
class VisionRecorder(private val context: Context) {

    companion object {
        private const val TAG = "VisionRecorder"
        private const val JPEG_QUALITY = 70
        private const val VIDEO_BITRATE = 4_000_000 // 4 Mbps
        private const val VIDEO_FPS = 24
    }

    var isRecording = false
        private set

    private var sessionDir: File? = null
    private var sessionName: String = ""
    private var frameIndex = 0
    private val frameMetadata = mutableListOf<FrameMetadata>()

    data class FrameMetadata(
        val index: Int,
        val aiText: String?,
        val gpsAddress: String?,
        val timestamp: Long
    )

    data class RecordingResult(
        val frameCount: Int,
        val durationSeconds: Long,
        val sizeBytes: Long,
        val sessionDir: File
    )

    // ── Recording lifecycle ──

    fun startRecording(name: String = "") {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        sessionName = if (name.isBlank()) "emma_vision_$ts" else "${name}_$ts"

        sessionDir = File(context.cacheDir, "vision_recordings/$sessionName").also {
            it.mkdirs()
        }
        frameIndex = 0
        frameMetadata.clear()
        isRecording = true
        Log.i(TAG, "Recording started: $sessionName")
    }

    /**
     * Save a frame to disk as JPEG. Call from the capture loop.
     * Copies the bitmap immediately — safe to recycle the original after.
     */
    fun saveFrame(bitmap: Bitmap, aiText: String? = null, gpsAddress: String? = null) {
        if (!isRecording) return
        val dir = sessionDir ?: return

        try {
            val file = File(dir, "frame_${String.format("%05d", frameIndex)}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }

            frameMetadata.add(FrameMetadata(
                index = frameIndex,
                aiText = aiText,
                gpsAddress = gpsAddress,
                timestamp = System.currentTimeMillis()
            ))
            frameIndex++
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save frame $frameIndex: ${e.message}")
        }
    }

    fun stopRecording(): RecordingResult {
        isRecording = false
        val dir = sessionDir ?: return RecordingResult(0, 0, 0, File(""))

        val duration = if (frameMetadata.size >= 2) {
            (frameMetadata.last().timestamp - frameMetadata.first().timestamp) / 1000
        } else 0L

        val size = dir.listFiles()?.sumOf { it.length() } ?: 0L

        Log.i(TAG, "Recording stopped: $frameIndex frames, ${duration}s, ${size / 1024}KB")
        return RecordingResult(frameIndex, duration, size, dir)
    }

    fun getFrameCount(): Int = frameIndex

    // ── MP4 Encoding ──

    /**
     * Encode saved JPEG frames to MP4 timelapse.
     * If withOverlay, burns AI text + GPS address onto each frame.
     * Returns the MP4 file in Downloads/EMMA/.
     */
    suspend fun encodeToMp4(withOverlay: Boolean = false): File? = withContext(Dispatchers.IO) {
        val dir = sessionDir ?: return@withContext null
        val frames = dir.listFiles()
            ?.filter { it.extension == "jpg" }
            ?.sortedBy { it.name }
            ?: return@withContext null

        if (frames.isEmpty()) return@withContext null

        // Read first frame for dimensions
        val firstBitmap = android.graphics.BitmapFactory.decodeFile(frames[0].absolutePath)
            ?: return@withContext null
        val width = (firstBitmap.width / 2) * 2  // Must be even for H.264
        val height = (firstBitmap.height / 2) * 2
        firstBitmap.recycle()

        val outputFile = File(context.cacheDir, "$sessionName.mp4")

        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var trackIndex = -1
            var muxerStarted = false
            val bufferInfo = MediaCodec.BufferInfo()

            val overlayPaint = if (withOverlay) Paint().apply {
                color = Color.WHITE
                textSize = 36f
                isAntiAlias = true
                setShadowLayer(4f, 2f, 2f, Color.BLACK)
            } else null

            for ((i, frameFile) in frames.withIndex()) {
                var bitmap = android.graphics.BitmapFactory.decodeFile(frameFile.absolutePath) ?: continue
                bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)

                // Apply overlay if requested
                if (withOverlay && i < frameMetadata.size) {
                    val meta = frameMetadata[i]
                    val canvas = Canvas(bitmap)
                    meta.gpsAddress?.let { addr ->
                        canvas.drawText("📍 $addr", 20f, 50f, overlayPaint!!)
                    }
                    meta.aiText?.let { text ->
                        val lines = text.take(120).chunked(40)
                        lines.forEachIndexed { idx, line ->
                            canvas.drawText(line, 20f, height - 80f + (idx * 40f), overlayPaint!!)
                        }
                    }
                }

                // Convert bitmap to YUV and feed to encoder
                val yuvData = bitmapToNV21(bitmap, width, height)
                bitmap.recycle()

                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                    inputBuffer.clear()
                    inputBuffer.put(yuvData)
                    val pts = (i * 1_000_000L / VIDEO_FPS)
                    codec.queueInputBuffer(inputIndex, 0, yuvData.size, pts, 0)
                }

                // Drain output
                while (true) {
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    } else if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex) ?: break
                        if (muxerStarted && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    } else break
                }
            }

            // Signal end of stream
            val eosIndex = codec.dequeueInputBuffer(10_000)
            if (eosIndex >= 0) {
                codec.queueInputBuffer(eosIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            // Drain remaining
            while (true) {
                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (muxerStarted && outputBuffer != null && bufferInfo.size > 0) {
                        muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else break
            }

            codec.stop()
            codec.release()
            if (muxerStarted) muxer.stop()
            muxer.release()

            // Copy to public Downloads/EMMA/
            val publicPath = PublicFileWriter.copyToPublicDownloads(context, outputFile, "video/mp4")
            Log.i(TAG, "MP4 encoded: $publicPath (${outputFile.length() / 1024}KB)")

            outputFile
        } catch (e: Exception) {
            Log.e(TAG, "Encoding failed: ${e.message}")
            null
        }
    }

    /** Simple RGB→NV21 conversion for MediaCodec */
    private fun bitmapToNV21(bitmap: Bitmap, width: Int, height: Int): ByteArray {
        val argb = IntArray(width * height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, height)
        val yuv = ByteArray(width * height * 3 / 2)

        var yIndex = 0
        var uvIndex = width * height

        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()

                if (j % 2 == 0 && i % 2 == 0 && uvIndex < yuv.size - 1) {
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }

    /** Clean up temp frames after encoding */
    fun cleanup() {
        sessionDir?.deleteRecursively()
        sessionDir = null
        frameMetadata.clear()
        frameIndex = 0
    }
}
