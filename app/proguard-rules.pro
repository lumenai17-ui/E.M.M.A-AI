# ═══════════════════════════════════════
# E.M.M.A. AI — ProGuard/R8 Rules
# Sprint 7: Production Build Optimization
# ═══════════════════════════════════════

# ─── KEEP RULES ───────────────────────

# Keep E.M.M.A. Plugin interface and all implementations
-keep class com.beemovil.plugins.EmmaPlugin { *; }
-keep class com.beemovil.plugins.builtins.** { *; }

# Keep LLM layer (OpenAI JSON parsing relies on field names)
-keep class com.beemovil.llm.** { *; }

# Keep data classes used in JSON serialization
-keep class com.beemovil.llm.ChatMessage { *; }
-keep class com.beemovil.llm.LlmResponse { *; }
-keep class com.beemovil.llm.ToolCall { *; }
-keep class com.beemovil.llm.ToolDefinition { *; }
-keep class com.beemovil.llm.ModelRegistry$ModelEntry { *; }

# Keep Room Database entities and DAOs (annotated by Room)
-keep class com.beemovil.database.** { *; }

# Keep Google API model classes (used via reflection)
-keep class com.google.api.services.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.auth.** { *; }

# Keep Koog Agent framework
-keep class ai.koog.** { *; }

# Keep LiteRT-LM (on-device inference)
-keep class com.google.ai.edge.litertlm.** { *; }

# Keep Telegram Bot Service (started via Intent action strings)
-keep class com.beemovil.telegram.TelegramBotService { *; }

# Keep Tunnel Service
-keep class com.beemovil.tunnel.** { *; }

# ─── GOOGLE CREDENTIAL MANAGER ───────

-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

# ─── OKHTTP ───────────────────────────

-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ─── JSOUP ────────────────────────────

-keep class org.jsoup.** { *; }

# ─── APACHE POI (Document Reader) ─────

-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }

# ─── PDFBOX ───────────────────────────

-dontwarn com.tom_roush.pdfbox.**
-keep class com.tom_roush.pdfbox.** { *; }

# ─── JAVAX.MAIL (Email) ──────────────

-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }

# ─── MOZILLA RHINO (JS Sandbox) ──────

-dontwarn org.mozilla.javascript.**
-keep class org.mozilla.javascript.** { *; }

# ─── MARKWON ──────────────────────────

-keep class io.noties.markwon.** { *; }

# ─── COIL ─────────────────────────────

-keep class coil.** { *; }

# ─── LOTTIE ───────────────────────────

-keep class com.airbnb.lottie.** { *; }

# ─── GENERAL ──────────────────────────

# Keep Enum values (used in serialization)
-keepclassmembers enum * { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable implementations
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
}

# Don't warn about missing annotations
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn sun.misc.Unsafe

# Keep stack traces readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ─── COROUTINES ───────────────────────

-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ─── KOTLIN SERIALIZATION ────────────

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.beemovil.**$$serializer { *; }
