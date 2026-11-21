package com.dsh.batchrenamer.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.dsh.batchrenamer.engine.TemplateEngine
import com.dsh.batchrenamer.model.RenamePlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class RenameRecord(val from: String, val to: String)

data class RenameFailure(val fileName: String, val reason: String)

data class ExecutionResult(
    val renamed: List<RenameRecord> = emptyList(),
    val failures: List<RenameFailure> = emptyList(),
) {
    val successCount: Int get() = renamed.size
    val failureCount: Int get() = failures.size
}

/**
 * Applies renames on disk.
 *
 * A batch is applied in two phases:
 *   1. every file is moved to a unique temporary name
 *   2. every temporary name is moved to its final name
 *
 * This makes "swap"/"rotate" batches safe (a.jpg -> b.jpg, b.jpg -> c.jpg),
 * which naively executed would collide halfway through.
 */
class RenameExecutor(private val context: Context) {

    private val mutex = Mutex()

    suspend fun execute(
        treeUri: Uri,
        plans: List<RenamePlan>,
        onProgress: (stage: Int, done: Int, total: Int) -> Unit,
    ): ExecutionResult = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            val dir = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withLock ExecutionResult(failures = listOf(RenameFailure("", "无法打开文件夹")))
            val ops = plans
                .filter { !it.unchanged && it.conflict == null }
                .map { Op(it.file.name, it.targetName) }
            runBatch(dir, ops, onProgress)
        }
    }

    /** Puts every recorded file back to the name it had before the batch. */
    suspend fun undo(
        treeUri: Uri,
        records: List<RenameRecord>,
        onProgress: (stage: Int, done: Int, total: Int) -> Unit,
    ): ExecutionResult = withContext(NonCancellable + Dispatchers.IO) {
        mutex.withLock {
            val dir = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withLock ExecutionResult(failures = listOf(RenameFailure("", "无法打开文件夹")))
            val ops = records.map { Op(it.to, it.from) }
            runBatch(dir, ops, onProgress)
        }
    }

    private data class Op(val from: String, val to: String)

    private fun runBatch(
        dir: DocumentFile,
        ops: List<Op>,
        onProgress: (Int, Int, Int) -> Unit,
    ): ExecutionResult {
        val failures = mutableListOf<RenameFailure>()
        if (ops.isEmpty()) return ExecutionResult(failures = failures)

        val index = HashMap<String, DocumentFile>()
        for (doc in dir.listFiles()) {
            val name = doc.name ?: continue
            index[name] = doc
        }

        // ---- Phase 1: original name -> temporary name ----
        val tempNameOf = HashMap<Op, String>()
        val moved = ArrayList<Op>()
        var done = 0
        for (op in ops) {
            val doc = index.remove(op.from)
            if (doc == null) {
                failures += RenameFailure(op.from, "文件已不存在或已被移动")
                onProgress(1, ++done, ops.size)
                continue
            }
            val tempName = TemplateEngine.temporaryName(TemplateEngine.extensionOf(op.to))
            val renamed = renameTo(doc, tempName)
            if (renamed == null) {
                index[op.from] = doc
                failures += RenameFailure(op.from, "无法准备临时名称（文件夹可能只读）")
            } else {
                index[tempName] = renamed
                tempNameOf[op] = tempName
                moved += op
            }
            onProgress(1, ++done, ops.size)
        }

        // ---- Phase 2: temporary name -> final name ----
        val records = mutableListOf<RenameRecord>()
        done = 0
        for (op in moved) {
            val tempName = tempNameOf[op] ?: continue
            val doc = index.remove(tempName)
            if (doc == null) {
                failures += RenameFailure(op.from, "临时文件意外丢失")
                onProgress(2, ++done, moved.size)
                continue
            }

            if (index.containsKey(op.to)) {
                val restored = renameTo(doc, op.from)
                if (restored != null) index[op.from] = restored
                failures += RenameFailure(op.from, "目标名称已被占用：${op.to}")
            } else {
                val renamed = renameTo(doc, op.to)
                if (renamed == null) {
                    val restored = renameTo(doc, op.from)
                    if (restored != null) index[op.from] = restored
                    failures += RenameFailure(op.from, "重命名为 ${op.to} 失败")
                } else {
                    index[op.to] = renamed
                    records += RenameRecord(op.from, op.to)
                }
            }
            onProgress(2, ++done, moved.size)
        }

        return ExecutionResult(renamed = records, failures = failures)
    }

    /**
     * SAF rename that also returns the (possibly new) document handle.
     */
    private fun renameTo(doc: DocumentFile, newName: String): DocumentFile? {
        return try {
            val newUri = DocumentsContract.renameDocument(context.contentResolver, doc.uri, newName)
            if (newUri == null) null else DocumentFile.fromSingleUri(context, newUri)
        } catch (t: Throwable) {
            null
        }
    }
}
