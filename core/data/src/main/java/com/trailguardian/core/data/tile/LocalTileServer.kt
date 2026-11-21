package com.trailguardian.core.data.tile

import android.util.Log
import com.trailguardian.core.geo.OfflinePlanner
import com.trailguardian.core.model.ContourStatus
import com.trailguardian.core.model.MapSources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LocalTileServer(
    private val cache: TileCache,
    private val client: OkHttpClient,
    private val renderer: ContourRenderer,
) {
    companion object {
        const val PORT = 18432
        fun baseUrl(): String = "http://127.0.0.1:$PORT"
    }

    private val _contourStatus = MutableStateFlow(ContourStatus.IDLE)
    val contourStatus: StateFlow<ContourStatus> = _contourStatus.asStateFlow()

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newFixedThreadPool(8)
    private var contourSuccess = 0
    private var contourFailures = 0

    private val esriRegex = Regex("^/esri/(\\d+)/(\\d+)/(\\d+)$")
    private val labelsRegex = Regex("^/labels/(\\d+)/(\\d+)/(\\d+)$")
    private val terrainRegex = Regex("^/terrain/(\\d+)/(\\d+)/(\\d+)$")
    private val contourRegex = Regex("^/contour/(\\d+)/(\\d+)/(\\d+)$")

    fun start() {
        if (running.getAndSet(true)) return
        try {
            val socket = ServerSocket(PORT)
            serverSocket = socket
            Thread {
                while (running.get()) {
                    try {
                        val connection = socket.accept()
                        executor.execute { handleConnection(connection) }
                    } catch (error: Exception) {
                        if (running.get()) Log.w(TAG, "tile accept failed", error)
                    }
                }
            }.apply {
                name = "trail-tile-server"
                isDaemon = true
                start()
            }
        } catch (error: Exception) {
            running.set(false)
            Log.w(TAG, "local tile server failed to bind", error)
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private fun handleConnection(connection: Socket) {
        connection.use { socket ->
            try {
                val input = BufferedInputStream(socket.getInputStream())
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2 || parts[0] != "GET") {
                    writeResponse(socket, 400, ByteArray(0))
                    return
                }
                val path = parts[1].substringBefore('?')
                while (true) {
                    val line = readLine(input) ?: break
                    if (line.isEmpty()) break
                }
                val bytes = resolve(path)
                if (bytes == null) writeResponse(socket, 404, ByteArray(0))
                else writeResponse(socket, 200, bytes)
            } catch (error: Exception) {
                Log.w(TAG, "tile request failed", error)
            }
        }
    }

    private fun resolve(path: String): ByteArray? {
        esriRegex.matchEntire(path)?.let { match ->
            val z = match.groupValues[1].toInt()
            val y = match.groupValues[2].toInt()
            val x = match.groupValues[3].toInt()
            val url = OfflinePlanner.expandTileUrl(
                MapSources.ESRI_SATELLITE.baseUrl, z, x, y,
            )
            return fetchOrCache(url)
        }
        labelsRegex.matchEntire(path)?.let { match ->
            val z = match.groupValues[1].toInt()
            val y = match.groupValues[2].toInt()
            val x = match.groupValues[3].toInt()
            val url = OfflinePlanner.expandTileUrl(
                MapSources.ESRI_SATELLITE.overlayUrls.first(), z, x, y,
            )
            return fetchOrCache(url)
        }
        terrainRegex.matchEntire(path)?.let { match ->
            val z = match.groupValues[1].toInt()
            val x = match.groupValues[2].toInt()
            val y = match.groupValues[3].toInt()
            val url = OfflinePlanner.expandTileUrl(
                MapSources.CONTOUR_TERRAIN.baseUrl, z, x, y,
            )
            return fetchOrCache(url)
        }
        contourRegex.matchEntire(path)?.let { match ->
            val z = match.groupValues[1].toInt()
            val x = match.groupValues[2].toInt()
            val y = match.groupValues[3].toInt()
            return renderContour(z, x, y)
        }
        return null
    }

    private fun fetchOrCache(url: String): ByteArray? {
        cache.get(url)?.let { return it }
        return try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                cache.put(url, bytes)
                bytes
            }
        } catch (error: Exception) {
            Log.w(TAG, "tile fetch failed: $url", error)
            null
        }
    }

    private fun renderContour(z: Int, x: Int, y: Int): ByteArray? {
        if (z > 15 || z < 1) return markContourFailed()
        val key = contourKey(z, x, y)
        cache.get(key)?.let {
            markContourSuccess()
            return it
        }
        val terrainUrl = OfflinePlanner.expandTileUrl(
            MapSources.CONTOUR_TERRAIN.baseUrl, z, x, y,
        )
        val terrain = fetchOrCache(terrainUrl) ?: return markContourFailed()
        val contour = renderer.generate(terrain, z) ?: return markContourFailed()
        cache.put(key, contour)
        markContourSuccess()
        return contour
    }

    private fun contourKey(z: Int, x: Int, y: Int): String = "contour://$z/$x/$y"

    private fun markContourSuccess(): ByteArray? {
        synchronized(this) {
            contourSuccess += 1
            if (_contourStatus.value != ContourStatus.READY) {
                _contourStatus.value = ContourStatus.READY
            }
        }
        return null
    }

    private fun markContourFailed(): ByteArray? {
        synchronized(this) {
            contourFailures += 1
            if (contourSuccess == 0 && contourFailures >= 3) {
                _contourStatus.value = ContourStatus.ERROR
            } else if (contourSuccess == 0) {
                _contourStatus.value = ContourStatus.LOADING
            }
        }
        return null
    }

    fun resetContourStatus(enabled: Boolean) {
        synchronized(this) {
            contourSuccess = 0
            contourFailures = 0
            _contourStatus.value = if (enabled) ContourStatus.LOADING else ContourStatus.IDLE
        }
    }

    private fun writeResponse(socket: Socket, status: Int, body: ByteArray) {
        val reason = if (status == 200) "OK" else if (status == 404) "Not Found" else "Bad Request"
        val header = buildString {
            append("HTTP/1.1 $status $reason\r\n")
            append("Content-Type: image/png\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray()
        val output = BufferedOutputStream(socket.getOutputStream())
        output.write(header)
        if (body.isNotEmpty()) output.write(body)
        output.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val builder = StringBuilder()
        var previous = -1
        while (true) {
            val current = input.read()
            if (current == -1) return if (builder.isEmpty()) null else builder.toString()
            if (current == '\n'.code) {
                if (previous == '\r'.code) builder.setLength(builder.length - 1)
                return builder.toString()
            }
            builder.append(current.toChar())
            previous = current
        }
    }

    private val TAG = "LocalTileServer"
}
