package com.dsh.batchrenamer.engine

import com.dsh.batchrenamer.model.MediaFile
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/**
 * Turns a name template plus a file into a safe, filesystem-friendly file name.
 *
 * Supported placeholders:
 *  {name}  original name without extension
 *  {n}     sequence number (zero padded with digits)
 *  {ext}   original extension, no dot
 *  {date}  yyyyMMdd        {time}  HHmmss
 *  {yyyy}  {MM}  {dd}  {HH}  {mm}  {ss}
 *
 * If the template does not contain {ext}, the original extension is appended
 * automatically so photos never lose their .jpg suffix.
 */
object TemplateEngine {

    private val TOKEN = Regex("\\{(name|ext|n|date|time|yyyy|MM|dd|HH|mm|ss)\\}")
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    const val MAX_NAME_LENGTH = 200

    fun formatIndex(index: Int, digits: Int): String {
        val safeIndex = if (index < 0) 0 else index
        return if (digits <= 0) safeIndex.toString()
        else safeIndex.toString().padStart(digits, '0')
    }

    fun buildName(
        template: String,
        file: MediaFile,
        index: Int,
        digits: Int,
        timestamp: Long,
    ): String {
        val tpl = if (template.isBlank()) "{name}_{n}" else template
        val zoned = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
        val hasExtToken = tpl.contains("{ext}")

        val body = TOKEN.replace(tpl) { match ->
            when (match.groupValues[1]) {
                "name" -> file.baseName
                "ext" -> file.extension
                "n" -> formatIndex(index, digits)
                "date" -> pad(zoned.year, 4) + pad(zoned.monthValue, 2) + pad(zoned.dayOfMonth, 2)
                "time" -> pad(zoned.hour, 2) + pad(zoned.minute, 2) + pad(zoned.second, 2)
                "yyyy" -> pad(zoned.year, 4)
                "MM" -> pad(zoned.monthValue, 2)
                "dd" -> pad(zoned.dayOfMonth, 2)
                "HH" -> pad(zoned.hour, 2)
                "mm" -> pad(zoned.minute, 2)
                "ss" -> pad(zoned.second, 2)
                else -> match.value
            }
        }

        val withExt = if (!hasExtToken && file.extension.isNotEmpty()) {
            "$body.${file.extension}"
        } else {
            body
        }
        return sanitize(withExt)
    }

    /** Makes a raw string safe as a file name on Android storage. */
    fun sanitize(raw: String): String {
        var value = ILLEGAL.replace(raw, "_")
        value = value.trim().trim('.', ' ')
        if (value.length > MAX_NAME_LENGTH) {
            value = value.take(MAX_NAME_LENGTH).trim('.', ' ')
        }
        if (value.isEmpty() || value == "." || value == "..") {
            value = "file"
        }
        return value
    }

    /**
     * Unique throwaway name used during the two-phase rename. The extension is
     * kept so the media database does not re-classify the file mid-flight.
     */
    fun temporaryName(extension: String): String {
        val token = UUID.randomUUID().toString().replace("-", "").take(10)
        return if (extension.isEmpty()) "__rnm_tmp_$token" else "__rnm_tmp_$token.$extension"
    }

    /** Returns the extension of a bare file name. */
    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.length - 1) name.substring(dot + 1) else ""
    }

    /** Detects placeholders a user may have typed but that we do not understand. */
    fun unknownPlaceholders(template: String): List<String> {
        val known = setOf("name", "ext", "n", "date", "time", "yyyy", "MM", "dd", "HH", "mm", "ss")
        return Regex("\\{([^{}]+)\\}").findAll(template)
            .map { it.groupValues[1] }
            .filter { it !in known }
            .distinct()
            .toList()
    }

    private fun pad(value: Int, width: Int): String =
        value.toString().padStart(width, '0')

    fun lowercaseKey(name: String): String = name.lowercase(Locale.ROOT)
}
