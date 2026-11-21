package com.dsh.batchrenamer.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.dsh.batchrenamer.model.MediaFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Thin wrapper over SAF (Storage Access Framework). No storage permission is
 * needed: the user grants access to one folder through the system picker and
 * the grant is persisted.
 */
class DocumentRepository(private val context: Context) {

    fun folderName(treeUri: Uri): String? =
        DocumentFile.fromTreeUri(context, treeUri)?.name

    fun isWritable(treeUri: Uri): Boolean =
        DocumentFile.fromTreeUri(context, treeUri)?.canWrite() == true

    suspend fun listFiles(treeUri: Uri): List<MediaFile> = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
        if (!dir.isDirectory) return@withContext emptyList()

        val out = ArrayList<MediaFile>()
        for (doc in dir.listFiles()) {
            if (!doc.isFile) continue
            val name = doc.name ?: continue
            out += MediaFile(
                uri = doc.uri,
                name = name,
                size = doc.length(),
                lastModified = doc.lastModified(),
                mimeType = doc.type,
            )
        }
        out
    }
}
