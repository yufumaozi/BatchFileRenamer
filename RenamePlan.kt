package com.dsh.batchrenamer.model

/** Why a planned rename cannot be applied as-is. */
enum class ConflictType(val label: String) {
    DUPLICATE("与其他目标重名"),
    EXISTING("文件夹内已有同名文件"),
    INVALID("名称不合法"),
}

/**
 * One "this file becomes that name" decision, produced before anything is touched.
 */
data class RenamePlan(
    val file: MediaFile,
    val targetName: String,
    val conflict: ConflictType? = null,
    val unchanged: Boolean = false,
) {
    val blocked: Boolean get() = conflict != null
}
