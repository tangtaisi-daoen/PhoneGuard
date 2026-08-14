package com.familyguard.core.update

import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

data class DownloadedApk(val file: File, val sizeBytes: Long, val sha256: String)

object UpdateHttpClient {
    private const val MAX_MANIFEST_BYTES = 64 * 1024L
    private val http = OkHttpClient.Builder()
        .callTimeout(90, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    suspend fun fetchManifest(url: String): UpdateManifest? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body ?: return@use null
                if (body.contentLength() > MAX_MANIFEST_BYTES) return@use null
                val bytes = readLimited(body.byteStream(), MAX_MANIFEST_BYTES) ?: return@use null
                val text = bytes.toString(Charsets.UTF_8)
                gson.fromJson(text, UpdateManifest::class.java)
            }
        }.getOrNull()
    }

    suspend fun downloadApk(url: String, target: File, expectedMaxBytes: Long): DownloadedApk? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    val declared = body.contentLength()
                    if (declared <= 0 || declared > expectedMaxBytes) return@use null
                    val digest = MessageDigest.getInstance("SHA-256")
                    var copied = 0L
                    FileOutputStream(target).use { output ->
                        body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                copied += count
                                if (copied > expectedMaxBytes) error("APK exceeds declared size")
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    DownloadedApk(target, copied, digest.digest().joinToString("") { "%02x".format(it) })
                }
            }.onFailure { target.delete() }.getOrNull()
        }

    private fun readLimited(input: java.io.InputStream, maxBytes: Long): ByteArray? = input.use {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = it.read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) return null
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
}
