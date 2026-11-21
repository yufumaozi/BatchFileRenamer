package com.dsh.batchrenamer.engine

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads the "date taken" from photos. Results are cached because sorting may
 * revisit the same file many times.
 */
object ExifDates {

    private val FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
    private val cache = ConcurrentHashMap<String, Long>()

    fun clear() = cache.clear()

    /**
     * Returns the capture time in epoch millis, or null when the file has no
     * usable EXIF date (videos almost never do).
     */
    fun takenAt(context: Context, uri: Uri): Long? {
        val key = uri.toString()
        cache[key]?.let { return it }

        val value = readFromDisk(context, uri) ?: return null
        cache[key] = value
        return value
    }

    private fun readFromDisk(context: Context, uri: Uri): Long? {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            stream.use {
                val exif = ExifInterface(it)
                val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?.takeIf { s -> s.isNotBlank() }
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)?.takeIf { s -> s.isNotBlank() }
                    ?: return null
                val parsed = runCatching { LocalDateTime.parse(raw.trim(), FORMAT) }.getOrNull()
                    ?: return null
                parsed.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        } catch (t: Throwable) {
            null
        }
    }
}
