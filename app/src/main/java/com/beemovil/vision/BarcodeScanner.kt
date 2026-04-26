package com.beemovil.vision

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * BarcodeScanner — FR-1: QR/Barcode Detection for Shopping Mode
 *
 * Uses Google ML Kit Barcode Scanning to detect QR codes, EAN-13, UPC-A,
 * and other barcode formats from camera frames. When a product barcode is
 * detected, it queries open APIs (OpenFoodFacts, UPCitemdb) for product info.
 *
 * Integration: Called from VisionCaptureLoop when mode == SHOPPING.
 * The product info is injected into the LLM prompt for contextual reviews.
 */
object BarcodeScanner {

    private const val TAG = "BarcodeScanner"

    private val scanner = BarcodeScanning.getClient()

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // Cache to avoid re-scanning the same barcode
    private val recentScans = mutableMapOf<String, ProductInfo>()

    data class ScanResult(
        val rawValue: String,
        val format: String,       // "QR_CODE", "EAN_13", "UPC_A", etc.
        val type: Int,            // Barcode.TYPE_URL, TYPE_PRODUCT, etc.
        val product: ProductInfo? // Looked-up product info (null if not found)
    )

    data class ProductInfo(
        val name: String,
        val brand: String = "",
        val category: String = "",
        val nutriscore: String = "",    // A-E grade for food
        val ingredients: String = "",
        val imageUrl: String = "",
        val source: String = "",         // "OpenFoodFacts", "UPCitemdb", "WebSearch"
        val reviews: String = "",        // Review snippets from web
        val price: String = ""           // Price info if found
    )

    /**
     * Scan a bitmap for barcodes/QR codes.
     * Returns list of detected codes with product info (if available).
     */
    suspend fun scan(bitmap: Bitmap): List<ScanResult> {
        val image = InputImage.fromBitmap(bitmap, 0)

        val barcodes = suspendCancellableCoroutine<List<Barcode>> { cont ->
            scanner.process(image)
                .addOnSuccessListener { codes ->
                    cont.resume(codes)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Barcode scan failed: ${e.message}")
                    cont.resume(emptyList())
                }
        }

        if (barcodes.isEmpty()) return emptyList()

        return barcodes.mapNotNull { barcode ->
            val raw = barcode.rawValue ?: return@mapNotNull null
            val format = formatName(barcode.format)

            Log.d(TAG, "Detected: $raw ($format)")

            // Lookup product info for product barcodes
            val product = if (isProductBarcode(barcode.format)) {
                lookupProduct(raw)
            } else null

            ScanResult(
                rawValue = raw,
                format = format,
                type = barcode.valueType,
                product = product
            )
        }
    }

    /**
     * Lookup product information from open APIs.
     * Pipeline: OpenFoodFacts → UPCitemdb → Web Search
     */
    private suspend fun lookupProduct(barcode: String): ProductInfo? {
        // Check cache first
        recentScans[barcode]?.let { return it }

        // Try OpenFoodFacts (best for food products, worldwide)
        var product = lookupOpenFoodFacts(barcode)

        // Try UPCitemdb (general products)
        if (product == null) {
            product = lookupUPCitemdb(barcode)
        }

        // Fallback: Web search for the barcode
        if (product == null) {
            product = lookupViaWebSearch(barcode)
        }

        if (product != null) {
            recentScans[barcode] = product
            // Keep cache small
            if (recentScans.size > 50) {
                recentScans.remove(recentScans.keys.first())
            }
        }

        return product
    }

    private suspend fun lookupOpenFoodFacts(barcode: String): ProductInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://world.openfoodfacts.org/api/v2/product/$barcode.json?fields=product_name,brands,categories,nutriscore_grade,ingredients_text,image_url")
                .header("User-Agent", "E.M.M.A. AI/7.2 (Android)")
                .build()

            val response = http.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(response.body?.string() ?: return@withContext null)
            if (json.optInt("status") != 1) return@withContext null

            val p = json.getJSONObject("product")
            ProductInfo(
                name = p.optString("product_name", "").ifBlank { return@withContext null },
                brand = p.optString("brands", ""),
                category = p.optString("categories", "").split(",").firstOrNull()?.trim() ?: "",
                nutriscore = p.optString("nutriscore_grade", "").uppercase(),
                ingredients = p.optString("ingredients_text", "").take(200),
                imageUrl = p.optString("image_url", ""),
                source = "OpenFoodFacts"
            )
        } catch (e: Exception) {
            Log.w(TAG, "OpenFoodFacts lookup failed: ${e.message}")
            null
        }
    }

    private suspend fun lookupUPCitemdb(barcode: String): ProductInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.upcitemdb.com/prod/trial/lookup?upc=$barcode")
                .header("User-Agent", "E.M.M.A. AI/7.2 (Android)")
                .build()

            val response = http.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(response.body?.string() ?: return@withContext null)
            val items = json.optJSONArray("items")
            if (items == null || items.length() == 0) return@withContext null

            val item = items.getJSONObject(0)
            ProductInfo(
                name = item.optString("title", "").ifBlank { return@withContext null },
                brand = item.optString("brand", ""),
                category = item.optString("category", ""),
                source = "UPCitemdb"
            )
        } catch (e: Exception) {
            Log.w(TAG, "UPCitemdb lookup failed: ${e.message}")
            null
        }
    }

    /**
     * Fallback: Search the web for the barcode to find product info.
     * Uses a simple DuckDuckGo instant answer API (no API key needed).
     */
    private suspend fun lookupViaWebSearch(barcode: String): ProductInfo? = withContext(Dispatchers.IO) {
        try {
            // DuckDuckGo instant answer — free, no key
            val query = java.net.URLEncoder.encode("$barcode product review price", "UTF-8")
            val request = Request.Builder()
                .url("https://api.duckduckgo.com/?q=$query&format=json&no_html=1&skip_disambig=1")
                .header("User-Agent", "E.M.M.A. AI/7.2 (Android)")
                .build()

            val response = http.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val json = JSONObject(response.body?.string() ?: return@withContext null)
            val abstractText = json.optString("AbstractText", "")
            val heading = json.optString("Heading", "")
            val abstractUrl = json.optString("AbstractURL", "")

            // Also check related topics for more info
            val relatedTopics = json.optJSONArray("RelatedTopics")
            val snippets = buildString {
                if (relatedTopics != null) {
                    for (i in 0 until minOf(relatedTopics.length(), 3)) {
                        val topic = relatedTopics.optJSONObject(i) ?: continue
                        val text = topic.optString("Text", "")
                        if (text.isNotBlank()) {
                            if (isNotEmpty()) append(" | ")
                            append(text.take(100))
                        }
                    }
                }
            }

            if (heading.isBlank() && abstractText.isBlank() && snippets.isBlank()) {
                return@withContext null
            }

            ProductInfo(
                name = heading.ifBlank { "Producto (código: $barcode)" },
                brand = "",
                category = "",
                reviews = (abstractText.take(200) + if (snippets.isNotBlank()) " | $snippets" else "").take(300),
                source = "WebSearch"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Web search lookup failed: ${e.message}")
            null
        }
    }

    /**
     * Build a context string for the LLM from a scan result.
     * Enhanced to prompt for richer, more actionable responses.
     */
    fun buildProductContext(result: ScanResult): String = buildString {
        appendLine("[PRODUCT SCAN] Barcode: ${result.rawValue} (${result.format})")
        result.product?.let { p ->
            appendLine("Product: ${p.name}")
            if (p.brand.isNotBlank()) appendLine("Brand: ${p.brand}")
            if (p.category.isNotBlank()) appendLine("Category: ${p.category}")
            if (p.nutriscore.isNotBlank()) appendLine("Nutri-Score: ${p.nutriscore}")
            if (p.ingredients.isNotBlank()) appendLine("Ingredients: ${p.ingredients}")
            if (p.price.isNotBlank()) appendLine("Price: ${p.price}")
            if (p.reviews.isNotBlank()) appendLine("Web Info: ${p.reviews}")
            appendLine("Source: ${p.source}")
            appendLine()
            appendLine("[INSTRUCTION] Provide a helpful summary of this product. Include:")
            appendLine("- What it is and what it's used for")
            appendLine("- Quality assessment or Nutri-Score explanation if food")
            appendLine("- Estimated price range if known")
            appendLine("- Suggest better alternatives if applicable")
            appendLine("- Any health/safety considerations")
        } ?: run {
            appendLine("Product info not found in any database (OpenFoodFacts, UPCitemdb, Web).")
            appendLine("[INSTRUCTION] Tell the user you couldn't find this product in databases.")
            appendLine("Suggest they try scanning again or searching manually.")
        }
    }

    /**
     * Clear the scan cache.
     */
    fun clearCache() {
        recentScans.clear()
    }

    private fun isProductBarcode(format: Int): Boolean = format in listOf(
        Barcode.FORMAT_EAN_13,
        Barcode.FORMAT_EAN_8,
        Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E,
        Barcode.FORMAT_CODE_128,
        Barcode.FORMAT_CODE_39
    )

    private fun formatName(format: Int): String = when (format) {
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_PDF417 -> "PDF_417"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        else -> "UNKNOWN"
    }
}
