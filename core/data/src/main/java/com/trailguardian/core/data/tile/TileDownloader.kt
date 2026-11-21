package com.trailguardian.core.data.tile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class DownloadProgress(
    val completed: Int,
    val downloaded: Int,
    val cached: Int,
    val failed: Int,
    val bytes: Long,
    val progress: Int,
)

data class DownloadResult(
    val total: Int,
    val downloaded: Int,
    val cached: Int,
    val failed: Int,
    val bytes: Long,
)

class TileDownloader(
    private val cache: TileCache,
    private val client: OkHttpClient,
    private val concurrency: Int = 6,
) {
    suspend fun download(
        urls: List<String>,
        onProgress: suspend (DownloadProgress) -> Unit = {},
    ): DownloadResult = withContext(Dispatchers.IO) {
        val completed = AtomicInteger(0)
        val downloaded = AtomicInteger(0)
        val cached = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val bytes = AtomicLong(0L)
        val semaphore = Semaphore(concurrency)
        val total = urls.size

        coroutineScope {
            urls.forEach { url ->
                launch {
                    semaphore.withPermit {
                        val existing = cache.get(url)
                        if (existing != null) {
                            cached.incrementAndGet()
                            bytes.addAndGet(existing.size.toLong())
                        } else {
                            val request = Request.Builder().url(url).build()
                            try {
                                client.newCall(request).execute().use { response ->
                                    if (!response.isSuccessful) throw IllegalIOException("HTTP ${response.code}")
                                    val body = response.body?.bytes() ?: throw IllegalIOException("Empty body")
                                    cache.put(url, body)
                                    downloaded.incrementAndGet()
                                    bytes.addAndGet(body.size.toLong())
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                failed.incrementAndGet()
                            }
                        }
                        val current = completed.incrementAndGet()
                        onProgress(
                            DownloadProgress(
                                completed = current,
                                downloaded = downloaded.get(),
                                cached = cached.get(),
                                failed = failed.get(),
                                bytes = bytes.get(),
                                progress = if (total == 0) 100 else (current * 100 / total),
                            ),
                        )
                    }
                }
            }
        }

        DownloadResult(
            total = total,
            downloaded = downloaded.get(),
            cached = cached.get(),
            failed = failed.get(),
            bytes = bytes.get(),
        )
    }

    private class IllegalIOException(message: String) : Exception(message)
}
