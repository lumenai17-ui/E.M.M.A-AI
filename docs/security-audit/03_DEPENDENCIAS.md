# 03 — Dependencias

Análisis estático de `app/build.gradle.kts`. No se ejecutó scanner de CVEs (eso requiere build); revisión manual por versión y reputación.

---

## Resumen

- **Total libs:** ~40 declaradas
- **Pre-release / alpha:** 2 (security, credentials)
- **Pesadas:** Apache POI + xmlbeans (PDF/Office parsing) — gran impacto en APK
- **Sin BOM ni catálogo central:** versiones esparcidas, fácil de quedar desactualizado

---

## L-07 · Dependencias en alpha

| Dep | Versión | Impacto |
|-----|---------|---------|
| `androidx.security:security-crypto` | `1.1.0-alpha06` | EncryptedSharedPreferences (C-06). Alpha = API/comportamiento puede cambiar. |
| `androidx.credentials:credentials` | `1.5.0-alpha05` | Google Sign-In moderno. Alpha. |
| `androidx.credentials:credentials-play-services-auth` | `1.5.0-alpha05` | Idem. |

**Propuesta:** Pinear a versiones estables o seguir la rama beta cuando salga. Documentar con un comentario.

---

## L-08 · Apache POI: APK bloat

```
implementation("org.apache.poi:poi-ooxml:5.2.5") {
    exclude(group = "org.apache.xmlbeans", module = "xmlbeans")
}
implementation("org.apache.xmlbeans:xmlbeans:5.1.1")
```

Apache POI con xmlbeans agrega varios MB al APK. Probablemente solo se usa para leer .docx/.xlsx en `ReadDocumentPlugin`.

**Propuesta:**
- Si solo se necesitan extractos de texto, considerar:
  - `Apache PDFBox-Android` (ya está) para PDFs.
  - Una lib más liviana para .docx (ej. parser propio para `word/document.xml` dentro del zip — los .docx son zips).
- O cargar POI como dynamic feature module (Play Asset Delivery) si el usuario realmente lo usa solo a veces.

---

## I-deps · Versiones estables, observaciones

| Dep | Versión | Comentario |
|-----|---------|------------|
| `androidx.compose.ui` | bom 2024.06.00 | Estable; revisar si hay 2024.11+ con fixes |
| `androidx.activity:activity-compose` | 1.8.2 | Estable |
| `androidx.lifecycle:*` | 2.7.0 | Hay 2.8.x estable |
| `androidx.navigation:navigation-compose` | 2.7.6 | Hay 2.8.x estable |
| `androidx.core:core-ktx` | 1.12.0 | Hay 1.15.x |
| `androidx.work:work-runtime-ktx` | 2.9.0 | Estable |
| `androidx.room:*` | 2.6.1 | Estable |
| `androidx.camera:*` | 1.3.1 | Hay 1.4.x con mejoras |
| `okhttp` | 4.12.0 | OK; OkHttp 5.x está en RC pero 4.x es soportado |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.1 | Última estable |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7.3 | Estable |
| `io.coil-kt:coil-compose` | 2.6.0 | OK; coil 3 ya salió pero 2.x sigue soportado |
| `org.jsoup:jsoup` | 1.17.2 | OK |
| `org.mozilla:rhino` | 1.7.14 | OK; última estable |
| `com.airbnb.android:lottie-compose` | 6.0.0 | Antiguo; hay 6.x más reciente |
| `com.tom-roush:pdfbox-android` | 2.0.27.0 | Estable |
| `com.sun.mail:android-mail` | 1.6.7 | Estable |

**Propuesta:** Centralizar versiones en `gradle/libs.versions.toml` (Gradle version catalog). Más fácil de auditar y bumpear.

---

## I-deps · Servicios Google API

```
google-api-services-drive:v3-rev20250220-2.0.0
google-api-services-calendar:v3-rev20250115-2.0.0
google-api-services-tasks:v1-rev20250302-2.0.0
google-api-services-gmail:v1-rev20260112-2.0.0   ← rev de 2026
google-auth-library-oauth2-http:1.32.0
google-api-client-android:2.7.0
```

Las "google-api-services" pueden tener fechas raras pero son normales (rev = fecha del schema). Apache HttpClient excluido — bien.

---

## I-deps · ML Kit

```
mlkit:face-detection:16.1.6
mlkit:text-recognition:16.0.0
mlkit:barcode-scanning:17.3.0
```

Versiones razonables. ML Kit tiene buen track record de seguridad.

---

## I-deps · LLM on-device

```
com.google.ai.edge.litertlm:litertlm-android:0.9.0
ai.koog:koog-agents:0.5.0
```

- LiteRT-LM 0.9.0 es muy nuevo (sub-1.0). Nota en build.gradle dice "compilado con Kotlin 2.2, allow cross-version usage" — convive con Kotlin 1.9 vía `-Xskip-metadata-version-check`. Riesgo de inestabilidad / bugs ABI.
- `ai.koog:koog-agents:0.5.0` — KMP agent framework de JetBrains; relativamente joven.

**Propuesta:** Vigilar releases. Tener plan B (otro on-device runtime).

---

## Recomendaciones generales de dependencias

1. **Catálogo de versiones:** mover a `gradle/libs.versions.toml`.
2. **Renovate / Dependabot:** activar bot que abra PRs con bumps.
3. **OWASP Dependency-Check:** correr en CI (`./gradlew dependencyCheckAnalyze`) para CVEs conocidos.
4. **APK Analyzer:** medir el tamaño por dependencia. POI suele ser candidato a optimizar.
5. **Inspección manual periódica:** alpha → estable cada 3-6 meses.
