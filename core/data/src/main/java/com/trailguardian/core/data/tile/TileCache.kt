package com.trailguardian.core.data.tile

import android.content.Context
import java.io.File
import java.security.MessageDigest

class TileCache(context: Context) {
    private val root = File(context.applicationContext.filesDir, "tiles").apply { mkdirs() }
    private val lock = Any()

    fun get(url: String): ByteArray? = synchronized(lock) {
        val file = fileFor(url)
        if (!file.exists()) return null
        runCatching { file.readBytes() }.getOrNull()
    }

    fun put(url: String, bytes: ByteArray) {
        synchronized(lock) {
            val file = fileFor(url)
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                file.writeBytes(bytes)
                temp.delete()
            }
        }
    }

    fun delete(urls: Collection<String>) {
        synchronized(lock) {
            urls.forEach { url -> fileFor(url).delete() }
        }
    }

    fun clear() {
        synchronized(lock) {
            root.listFiles()?.forEach { it.delete() }
        }
    }

    fun count(): Int = synchronized(lock) {
        root.listFiles()?.count { it.isFile && it.extension == "tile" } ?: 0
    }

    fun totalBytes(): Long = synchronized(lock) {
        root.listFiles()?.filter { it.isFile && it.extension == "tile" }?.sumOf { it.length() } ?: 0L
    }

    fun sizeOf(urls: Collection<String>): Long = synchronized(lock) {
        urls.sumOf { url ->
            val file = fileFor(url)
            if (file.exists()) file.length() else 0L
        }
    }

    fun sizeOfOne(url: String): Long = synchronized(lock) {
        val file = fileFor(url)
        if (file.exists()) file.length() else 0L
    }

    private fun fileFor(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) } + ".tile"
        return File(root, name)
    }
}
