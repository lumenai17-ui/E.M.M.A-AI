package com.beemovil.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared OkHttpClient for all skills and services.
 *
 * Problem: Previously, each skill (WeatherSkill, WebSearchSkill, WebFetchSkill, etc.)
 * created its own OkHttpClient, each with its own connection pool and thread pool.
 * On a mobile device with limited resources, this wastes memory and file descriptors.
 *
 * Solution: A single shared client with standardized timeouts.
 * Skills can use BeeHttpClient.default for standard requests, or
 * BeeHttpClient.longPoll for long-polling operations (Telegram).
 */
object BeeHttpClient {

    /** Default client — 15s connect, 30s read, 15s write. Good for most API calls. */
    val default: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Long-poll client — 35s timeouts. Used for Telegram polling, streaming. */
    val longPoll: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(35, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** LLM client — 30s connect, 120s read (for long model responses). */
    val llm: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Download client — 60s connect, 10 min read. For large file downloads (LLM models ~3GB). */
    val download: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
