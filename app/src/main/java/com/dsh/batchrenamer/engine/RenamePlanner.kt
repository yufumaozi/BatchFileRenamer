package com.dsh.batchrenamer.engine

import com.dsh.batchrenamer.model.ConflictType
import com.dsh.batchrenamer.model.MediaFile
import com.dsh.batchrenamer.model.RenamePlan
import com.dsh.batchrenamer.model.RenameRule
import com.dsh.batchrenamer.model.SortOrder

/**
 * Builds the complete rename plan ("dry run") without touching any file.
 *
 * Sequence numbers follow the chosen sort order, so {n} always lines up with
 * the order the user sees in the preview.
 */
class RenamePlanner(
    private val takenAt: (MediaFile) -> Long? = { null },
) {

    fun plan(files: List<MediaFile>, rule: RenameRule): List<RenamePlan> {
        val selected = files.filter { rule.scope.matches(it) }
        if (selected.isEmpty()) return emptyList()

        val selectedUris = selected.mapTo(HashSet()) { it.uri }
        val untouchedNames = HashSet<String>()
        for (file in files) {
            if (file.uri !in selectedUris) {
                untouchedNames += TemplateEngine.lowercaseKey(file.name)
            }
        }

        val timeCache = HashMap<String, Long>()
        fun effectiveTime(file: MediaFile): Long {
            val key = file.uri.toString()
            return timeCache.getOrPut(key) {
                if (file.isImage) (takenAt(file) ?: file.lastModified) else file.lastModified
            }
        }

        val ordered: List<MediaFile> = when (rule.sortOrder) {
            SortOrder.MODIFIED_ASC -> selected.sortedBy { it.lastModified }
            SortOrder.MODIFIED_DESC -> selected.sortedByDescending { it.lastModified }
            SortOrder.TAKEN_ASC -> selected.sortedBy { effectiveTime(it) }
            SortOrder.TAKEN_DESC -> selected.sortedByDescending { effectiveTime(it) }
            SortOrder.NAME_ASC -> selected.sortedBy { TemplateEngine.lowercaseKey(it.name) }
            SortOrder.NAME_DESC -> selected.sortedByDescending { TemplateEngine.lowercaseKey(it.name) }
            SortOrder.SIZE_ASC -> selected.sortedBy { it.size }
            SortOrder.SIZE_DESC -> selected.sortedByDescending { it.size }
        }

        val usedTargets = HashSet<String>()
        val result = ArrayList<RenamePlan>(ordered.size)

        ordered.forEachIndexed { offset, file ->
            val index = rule.startIndex + offset
            val target = TemplateEngine.buildName(
                template = rule.effectiveTemplate,
                file = file,
                index = index,
                digits = rule.digits,
                timestamp = effectiveTime(file),
            )
            val key = TemplateEngine.lowercaseKey(target)
            val conflict: ConflictType? = when {
                target.isBlank() -> ConflictType.INVALID
                key in untouchedNames -> ConflictType.EXISTING
                key in usedTargets -> ConflictType.DUPLICATE
                else -> null
            }
            usedTargets += key

            result += RenamePlan(
                file = file,
                targetName = target,
                conflict = conflict,
                unchanged = target == file.name,
            )
        }

        return result
    }
}
