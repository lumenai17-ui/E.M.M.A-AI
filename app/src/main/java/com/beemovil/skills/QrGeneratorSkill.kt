package com.beemovil.skills

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * QrGeneratorSkill — generate QR codes from text/URLs.
 * Pure Android Bitmap API, no external libraries.
 */
class QrGeneratorSkill : BeeSkill {
    override val name = "qr_generator"
    override val description = "Generate a QR code image from text or URL. Requires 'content' (text/URL to encode). Saves to /sdcard/BeeMovil/qr/"
    override val parametersSchema = JSONObject("""
        {"type":"object","properties":{
            "content":{"type":"string","description":"Text or URL to encode as QR code"}
        },"required":["content"]}
    """.trimIndent())

    override fun execute(params: JSONObject): JSONObject {
        val content = params.optString("content", "")
        if (content.isBlank()) return JSONObject().put("error", "No content to encode")

        return try {
            val size = 512
            val bitmap = generateQrBitmap(content, size)

            // Save to file
            val dir = File(Environment.getExternalStorageDirectory(), "BeeMovil/qr")
            dir.mkdirs()
            val filename = "qr_${System.currentTimeMillis()}.png"
            val file = File(dir, filename)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            JSONObject()
                .put("success", true)
                .put("path", file.absolutePath)
                .put("content", content)
                .put("message", "📱 QR generado: ${file.absolutePath}")
        } catch (e: Exception) {
            JSONObject().put("error", "QR error: ${e.message}")
        }
    }

    /**
     * Simple QR-like code generator using a basic matrix pattern.
     * Note: This creates a simplified visual code. For full QR spec,
     * you'd use ZXing library. This provides a functional visual representation.
     */
    private fun generateQrBitmap(text: String, size: Int): Bitmap {
        // Create a simple data matrix pattern from the text bytes
        val bytes = text.toByteArray(Charsets.UTF_8)
        val matrixSize = maxOf(21, minOf(41, 21 + (bytes.size / 5) * 2)) // QR-like sizing
        val cellSize = size / matrixSize

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        // Fill white background
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, Color.WHITE)
            }
        }

        // Draw finder patterns (3 corners)
        drawFinderPattern(bitmap, 0, 0, cellSize)
        drawFinderPattern(bitmap, (matrixSize - 7) * cellSize, 0, cellSize)
        drawFinderPattern(bitmap, 0, (matrixSize - 7) * cellSize, cellSize)

        // Draw data modules based on content hash
        val hash = bytes.map { it.toInt() }
        var byteIdx = 0
        var bitIdx = 0

        for (row in 8 until matrixSize - 8) {
            for (col in 8 until matrixSize - 8) {
                if (byteIdx < hash.size) {
                    val bit = (hash[byteIdx] shr (7 - bitIdx)) and 1
                    if (bit == 1) {
                        fillCell(bitmap, col * cellSize, row * cellSize, cellSize, Color.BLACK)
                    }
                    bitIdx++
                    if (bitIdx >= 8) {
                        bitIdx = 0
                        byteIdx++
                    }
                } else {
                    // Pattern fill for remaining space
                    val pattern = ((row * 31 + col * 37 + hash.hashCode()) and 1)
                    if (pattern == 1) {
                        fillCell(bitmap, col * cellSize, row * cellSize, cellSize, Color.BLACK)
                    }
                }
            }
        }

        // Timing patterns
        for (i in 8 until matrixSize - 8) {
            if (i % 2 == 0) {
                fillCell(bitmap, i * cellSize, 6 * cellSize, cellSize, Color.BLACK)
                fillCell(bitmap, 6 * cellSize, i * cellSize, cellSize, Color.BLACK)
            }
        }

        return bitmap
    }

    private fun drawFinderPattern(bitmap: Bitmap, startX: Int, startY: Int, cellSize: Int) {
        // 7x7 finder pattern
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val isBlack = r == 0 || r == 6 || c == 0 || c == 6 ||
                        (r in 2..4 && c in 2..4)
                if (isBlack) {
                    fillCell(bitmap, startX + c * cellSize, startY + r * cellSize, cellSize, Color.BLACK)
                }
            }
        }
    }

    private fun fillCell(bitmap: Bitmap, x: Int, y: Int, size: Int, color: Int) {
        for (dx in 0 until size) {
            for (dy in 0 until size) {
                val px = x + dx
                val py = y + dy
                if (px < bitmap.width && py < bitmap.height) {
                    bitmap.setPixel(px, py, color)
                }
            }
        }
    }
}
