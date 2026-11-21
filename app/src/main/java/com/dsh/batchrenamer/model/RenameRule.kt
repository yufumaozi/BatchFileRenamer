package com.dsh.batchrenamer.model

/** How the new file name is produced. */
enum class NamingMode(val label: String) {
    TEMPLATE("自定义模板"),
    SEQUENCE("纯序列号"),
}

/** Order used to assign sequence numbers. */
enum class SortOrder(val label: String) {
    MODIFIED_ASC("修改时间 旧→新"),
    MODIFIED_DESC("修改时间 新→旧"),
    TAKEN_ASC("拍摄时间 早→晚"),
    TAKEN_DESC("拍摄时间 晚→早"),
    NAME_ASC("文件名 A→Z"),
    NAME_DESC("文件名 Z→A"),
    SIZE_ASC("大小 小→大"),
    SIZE_DESC("大小 大→小"),
}

/** Which files take part in the batch. */
enum class FileScope(val label: String) {
    ALL("全部文件"),
    IMAGE("仅图片"),
    VIDEO("仅视频"),
    MEDIA("图片 + 视频"),
    ;

    fun matches(file: MediaFile): Boolean = when (this) {
        ALL -> true
        IMAGE -> file.isImage
        VIDEO -> file.isVideo
        MEDIA -> file.isImage || file.isVideo
    }
}

/**
 * The full renaming instruction. Everything the engine needs to build names.
 */
data class RenameRule(
    val mode: NamingMode = NamingMode.TEMPLATE,
    val template: String = "{name}_{n}",
    val startIndex: Int = 1,
    val digits: Int = 3,
    val sortOrder: SortOrder = SortOrder.MODIFIED_ASC,
    val scope: FileScope = FileScope.ALL,
) {
    /** Template actually fed to the engine; sequence mode is template "{n}". */
    val effectiveTemplate: String
        get() = if (mode == NamingMode.SEQUENCE) "{n}" else template
}
