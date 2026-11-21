package com.dsh.batchrenamer.model

import android.net.Uri
import java.util.Locale

/**
 * One file discovered inside the user-picked folder (SAF tree).
 */
data class MediaFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String?,
) {
    /** Extension without the leading dot, as written on disk. Empty when there is none. */
    val extension: String
        get() {
            val dot = name.lastIndexOf('.')
            return if (dot > 0 && dot < name.length - 1) name.substring(dot + 1) else ""
        }

    /** File name with the extension stripped off. */
    val baseName: String
        get() {
            val ext = extension
            return if (ext.isEmpty()) name else name.dropLast(ext.length + 1)
        }

    val isImage: Boolean
        get() = mimeType?.startsWith("image/") == true || extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS

    val isVideo: Boolean
        get() = mimeType?.startsWith("video/") == true || extension.lowercase(Locale.ROOT) in VIDEO_EXTENSIONS

    companion object {
        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif",
            "avif", "dng", "raw", "tif", "tiff", "jfif",
        )
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "mov", "avi", "3gp", "webm", "flv", "wmv", "m4v", "ts", "mts", "m2ts",
        )
    }
}
