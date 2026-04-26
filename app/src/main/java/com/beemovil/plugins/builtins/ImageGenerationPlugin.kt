package com.beemovil.plugins.builtins

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.beemovil.files.PublicFileWriter
import com.beemovil.llm.ToolDefinition
import com.beemovil.media.PollinationsClient
import com.beemovil.plugins.EmmaPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Image Generation Plugin for E.M.M.A.
 *
 * Uses Pollinations.ai for text-to-image generation.
 * - With API key: uses gen.pollinations.ai with advanced models (Flux, GPT Image, etc.)
 * - Without API key: falls back to legacy image.pollinations.ai (still works, free)
 *
 * Generated images are:
 *   1. Saved to internal cache
 *   2. Copied to Downloads/EMMA/ via PublicFileWriter for visibility
 *   3. Returned as a TOOL_CALL::file_generated signal for chat attachment
 */
class ImageGenerationPlugin(private val context: Context) : EmmaPlugin {

    override val id = "generate_ai_image"
    private val TAG = "ImageGenPlugin"

    companion object {
        private const val DEFAULT_WIDTH = 1024
        private const val DEFAULT_HEIGHT = 1024
    }

    override fun getToolDefinition(): ToolDefinition {
        return ToolDefinition(
            name = id,
            description = "Genera una imagen con inteligencia artificial a partir de una descripción de texto (text-to-image). " +
                    "Úsalo cuando el usuario pida: 'genera una imagen de...', 'dibuja...', 'crea una ilustración de...', " +
                    "'hazme un logo', 'genera un arte de...'. " +
                    "El prompt DEBE estar en inglés para mejores resultados.",
            parameters = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("prompt", JSONObject().apply {
                        put("type", "string")
                        put("description", "Descripción detallada de la imagen a generar. DEBE estar en inglés. " +
                                "Incluye estilo artístico, colores, composición. Ej: 'A futuristic city at sunset, cyberpunk style, neon lights, 4k quality'")
                    })
                    put("style", JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray().apply {
                            put("photorealistic")
                            put("digital_art")
                            put("anime")
                            put("oil_painting")
                            put("watercolor")
                            put("3d_render")
                            put("pixel_art")
                            put("sketch")
                            put("logo")
                            put("flat_design")
                            put("cinematic")
                            put("fashion")
                            put("architectural")
                            put("comic")
                            put("surreal")
                            put("vintage")
                            put("neon")
                            put("sticker")
                        })
                        put("description", "Estilo visual de la imagen. Opciones: photorealistic, digital_art, anime, oil_painting, watercolor, 3d_render, pixel_art, sketch, logo, flat_design, cinematic, fashion, architectural, comic, surreal, vintage, neon, sticker.")
                    })
                    put("width", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Ancho en píxeles (default: 1024). Rango: 256-1536.")
                    })
                    put("height", JSONObject().apply {
                        put("type", "integer")
                        put("description", "Alto en píxeles (default: 1024). Rango: 256-1536.")
                    })
                })
                put("required", JSONArray().put("prompt"))
            }
        )
    }

    override suspend fun execute(args: Map<String, Any>): String {
        val prompt = args["prompt"] as? String ?: return "ERROR_TOOL_FAILED: Falta el prompt para generar la imagen."
        val style = args["style"] as? String ?: "digital_art"
        val width = (args["width"] as? Number)?.toInt()?.coerceIn(256, 1536) ?: DEFAULT_WIDTH
        val height = (args["height"] as? Number)?.toInt()?.coerceIn(256, 1536) ?: DEFAULT_HEIGHT

        return withContext(Dispatchers.IO) {
            try {
                val enhancedPrompt = buildEnhancedPrompt(prompt, style)
                Log.i(TAG, "Generating image: $enhancedPrompt (${width}x${height})")

                // Use new API if key available, otherwise fallback to legacy
                val url = if (PollinationsClient.hasApiKey(context)) {
                    PollinationsClient.imageUrl(enhancedPrompt, width, height, "flux")
                } else {
                    PollinationsClient.legacyImageUrl(enhancedPrompt, width, height)
                }

                // Attempt with automatic retry on failure
                var imageBytes = PollinationsClient.downloadMedia(context, url)
                if (imageBytes == null) {
                    Log.w(TAG, "First attempt failed, retrying...")
                    kotlinx.coroutines.delay(2000)
                    imageBytes = PollinationsClient.downloadMedia(context, url)
                }
                if (imageBytes == null) {
                    return@withContext "ERROR_TOOL_FAILED: La generación de imagen falló después de 2 intentos. El servicio de Pollinations no respondió. Informa al usuario que hubo un timeout y que puede intentar de nuevo."
                }

                // Save to internal cache
                val timestamp = System.currentTimeMillis()
                val fileName = "emma_generated_${timestamp}.png"
                val cacheDir = File(context.cacheDir, "generated_images").apply { mkdirs() }
                val localFile = File(cacheDir, fileName)

                FileOutputStream(localFile).use { fos ->
                    fos.write(imageBytes)
                }

                // Verify it's a valid image
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(localFile.absolutePath, options)
                if (options.outWidth <= 0) {
                    localFile.delete()
                    return@withContext "ERROR_TOOL_FAILED: La imagen descargada no es válida. Pide al usuario que intente con otro prompt."
                }

                // Copy to public Downloads/EMMA/ and use that path for the chat
                val publicPath = PublicFileWriter.copyToPublicDownloads(context, localFile, "image/png")

                Log.i(TAG, "Image generated: ${options.outWidth}x${options.outHeight} → $fileName (public: $publicPath)")

                "TOOL_CALL::file_generated::$publicPath"

            } catch (e: Exception) {
                Log.e(TAG, "Image generation error: ${e.message}", e)
                "ERROR_TOOL_FAILED: Error generando imagen: ${e.message}. Informa al usuario del error."
            }
        }
    }

    private fun buildEnhancedPrompt(basePrompt: String, style: String): String {
        val intent = detectIntent(basePrompt)
        val styleDNA = getStyleDNA(style)
        val intentBoost = getIntentBoost(intent)
        val qualityBase = "masterpiece, best quality, highly detailed"

        return "$basePrompt, $intentBoost, $styleDNA, $qualityBase"
    }

    private fun detectIntent(prompt: String): String {
        val p = prompt.lowercase()
        return when {
            p.containsAny("logo", "brand", "icon", "emblem", "logotipo", "marca") -> "LOGO"
            p.containsAny("portrait", "face", "person", "headshot", "selfie", "retrato", "rostro") -> "PORTRAIT"
            p.containsAny("landscape", "scenery", "mountain", "ocean", "city", "skyline", "paisaje", "montaña") -> "LANDSCAPE"
            p.containsAny("product", "item", "bottle", "packaging", "shoe", "producto") -> "PRODUCT"
            p.containsAny("food", "dish", "meal", "recipe", "plate", "comida", "plato") -> "FOOD"
            p.containsAny("ui", "interface", "app", "dashboard", "website", "mockup") -> "UI_DESIGN"
            p.containsAny("pattern", "texture", "wallpaper", "background", "patrón", "fondo") -> "PATTERN"
            p.containsAny("meme", "funny", "reaction", "viral", "chistoso") -> "MEME"
            p.containsAny("sticker", "emoji", "whatsapp", "telegram") -> "STICKER"
            else -> "ART"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it, ignoreCase = true) }

    private fun getIntentBoost(intent: String): String = when (intent) {
        "LOGO" -> "isolated on pure white background, no text, geometric precision, brand identity, scalable vector-like design, negative space, single focal element, corporate clean"
        "PORTRAIT" -> "bust shot, cinematic composition, eye contact with viewer, sharp focus on eyes, beautiful bokeh background, rembrandt lighting, skin texture detail, catchlight in eyes"
        "LANDSCAPE" -> "panoramic wide angle, rule of thirds composition, leading lines toward horizon, atmospheric perspective, dramatic cloud formations, golden hour warm lighting, depth layering"
        "PRODUCT" -> "studio product photography, three-point lighting setup, clean gradient background, soft reflections on surface, commercial advertising quality, floating hero shot"
        "FOOD" -> "overhead food photography, rustic wooden table, fresh ingredients, steam rising, natural window light, shallow DOF, food styling, appetizing warm tones"
        "UI_DESIGN" -> "modern UI design, clean layout, consistent spacing, glass morphism effects, dark mode interface, subtle gradients, professional mockup"
        "PATTERN" -> "seamless tileable pattern, repeating motif, decorative design, symmetrical, textile quality, wallpaper design"
        "MEME" -> "humorous exaggerated expression, bold outlines, vibrant flat colors, meme-worthy composition, internet culture aesthetic"
        "STICKER" -> "die-cut sticker design, thick white border, cute chibi style, transparent background, bold clean outlines, kawaii aesthetic, emoji quality"
        else -> "award-winning composition, museum gallery quality, masterful use of light and shadow, emotionally evocative"
    }

    private fun getStyleDNA(style: String): String = when (style) {
        "photorealistic" -> "photorealistic photograph, shot on Canon EOS R5 with 85mm f/1.4 lens, shallow depth of field, natural lighting, RAW photo quality, subtle film grain, ACES tonemapped, professional color grading, 8K UHD"
        "digital_art" -> "digital illustration masterpiece, trending on ArtStation top weekly, by Greg Rutkowski and Alphonse Mucha, intricate ornamental details, volumetric atmospheric fog, dramatic rim lighting with color spill, cinematic color grading"
        "anime" -> "anime key visual, studio ufotable production quality, dynamic action pose, particle effects, cel-shaded rendering with soft gradient shadows, vibrant saturated color palette, detailed background environment painting"
        "oil_painting" -> "oil painting on canvas, classical fine art technique, rich impasto brushwork, chiaroscuro dramatic lighting, renaissance composition, warm color palette with deep shadows, museum gallery piece"
        "watercolor" -> "watercolor painting, wet-on-wet technique, soft bleeding edges, transparent color washes, visible paper texture, fluid organic forms, delicate botanical illustration quality, ethereal luminous atmosphere"
        "3d_render" -> "3D render, Octane Render engine, volumetric god rays, subsurface scattering on organic materials, PBR materials, HDRi environment lighting, depth of field, Unreal Engine 5 cinematic quality"
        "pixel_art" -> "pixel art, 32-bit retro aesthetic, limited color palette, dithering technique, isometric perspective, nostalgic gaming style, clean pixel-perfect edges, sprite sheet quality"
        "sketch" -> "detailed pencil sketch, fine cross-hatching technique, anatomically precise proportions, graphite on textured paper, architectural rendering quality, expressive confident line work"
        "logo" -> "professional brand logo, clean vector graphic design, minimalist composition, clever use of negative space, single color plus accent variation, perfectly symmetrical, pure white background, Fortune 500 corporate identity quality"
        "flat_design" -> "flat design illustration, modern geometric shapes, clean bold outlines, limited harmonious color palette, Material Design inspired, infographic quality, crisp edges, contemporary editorial illustration"
        // ── New styles ──
        "cinematic" -> "cinematic film still, anamorphic lens flare, widescreen 2.39:1 aspect, dramatic volumetric lighting, color graded teal and orange, shallow DOF, epic scale, IMAX quality, directed by Denis Villeneuve"
        "fashion" -> "high fashion editorial photography, Vogue magazine cover quality, studio rim lighting, model pose, haute couture styling, clean background, professional retouching, Alexander McQueen aesthetic"
        "architectural" -> "architectural visualization render, V-Ray global illumination, clean modern lines, floor-to-ceiling windows, natural daylight, interior design magazine, ArchDaily featured, minimalist Scandinavian"
        "comic" -> "comic book art style, bold ink outlines, halftone dot shading, dynamic action panel composition, vibrant primary colors, Marvel/DC quality, speech bubble ready, superhero energy"
        "surreal" -> "surrealist masterpiece, dreamlike impossible geometry, melting reality, floating objects, Salvador Dali meets Magritte, impossible architecture, vivid subconscious imagery"
        "vintage" -> "vintage film photography, Kodak Portra 400 color profile, soft warm tones, light leak artifacts, grainy texture, 1970s aesthetic, nostalgic golden hour, retro magazine quality"
        "neon" -> "neon cyberpunk aesthetic, rain-soaked streets, holographic reflections, magenta and cyan neon glow, Blade Runner atmosphere, night city scene, volumetric fog, futuristic Tokyo"
        "sticker" -> "die-cut sticker illustration, thick white border outline, cute kawaii chibi style, bold clean vectors, transparent background, emoji quality, WhatsApp sticker pack"
        else -> "professional quality, highly detailed, sharp focus, beautiful composition, award-winning, trending on ArtStation"
    }
}
