package com.omniview.viewer.data.model

import java.io.File

enum class FileCategory {
    IMAGE, VIDEO, AUDIO, PDF, DOCUMENT, SPREADSHEET, PRESENTATION,
    CODE, TEXT, ARCHIVE, FOLDER, UNKNOWN
}

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val category: FileCategory
) {
    val file: File get() = File(path)

    val extension: String
        get() = if (isDirectory) "" else name.substringAfterLast('.', "").lowercase()

    companion object {
        fun fromFile(file: File): FileItem {
            val category = if (file.isDirectory) FileCategory.FOLDER
                else FileTypeClassifier.classify(file.extension)
            return FileItem(
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified(),
                category = category
            )
        }
    }
}

object FileTypeClassifier {
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic")
    private val videoExt = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi")
    private val audioExt = setOf("mp3", "wav", "ogg", "m4a", "flac", "aac")
    private val docExt = setOf("doc", "docx", "rtf", "odt")
    private val sheetExt = setOf("xls", "xlsx", "ods")
    private val slideExt = setOf("ppt", "pptx", "odp")
    private val archiveExt = setOf("zip", "rar", "7z", "tar", "gz")
    private val codeExt = setOf(
        "py", "js", "ts", "jsx", "tsx", "html", "htm", "css", "scss",
        "java", "kt", "kts", "c", "cpp", "h", "hpp", "cs", "go", "rs",
        "php", "rb", "swift", "sh", "sql", "json", "xml", "yaml", "yml",
        "gradle", "properties", "dart", "lua", "r"
    )
    private val textExt = setOf("txt", "md", "log", "csv", "ini", "cfg")

    fun classify(extensionRaw: String): FileCategory {
        val ext = extensionRaw.lowercase()
        return when {
            ext == "pdf" -> FileCategory.PDF
            ext in imageExt -> FileCategory.IMAGE
            ext in videoExt -> FileCategory.VIDEO
            ext in audioExt -> FileCategory.AUDIO
            ext in docExt -> FileCategory.DOCUMENT
            ext in sheetExt -> FileCategory.SPREADSHEET
            ext in slideExt -> FileCategory.PRESENTATION
            ext in archiveExt -> FileCategory.ARCHIVE
            ext in codeExt -> FileCategory.CODE
            ext in textExt -> FileCategory.TEXT
            else -> FileCategory.UNKNOWN
        }
    }
}
