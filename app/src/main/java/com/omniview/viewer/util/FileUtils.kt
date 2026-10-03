package com.omniview.viewer.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.omniview.viewer.data.model.FileCategory
import com.omniview.viewer.ui.theme.AccentArchive
import com.omniview.viewer.ui.theme.AccentAudio
import com.omniview.viewer.ui.theme.AccentCode
import com.omniview.viewer.ui.theme.AccentDoc
import com.omniview.viewer.ui.theme.AccentImage
import com.omniview.viewer.ui.theme.AccentPdf
import com.omniview.viewer.ui.theme.AccentSheet
import com.omniview.viewer.ui.theme.AccentSlide
import com.omniview.viewer.ui.theme.AccentText
import com.omniview.viewer.ui.theme.AccentVideo
import kotlin.math.log10
import kotlin.math.pow

fun iconFor(category: FileCategory): ImageVector = when (category) {
    FileCategory.FOLDER -> Icons.Filled.Folder
    FileCategory.PDF -> Icons.Filled.PictureAsPdf
    FileCategory.IMAGE -> Icons.Filled.Image
    FileCategory.VIDEO -> Icons.Filled.Videocam
    FileCategory.AUDIO -> Icons.Filled.Audiotrack
    FileCategory.DOCUMENT -> Icons.Filled.Description
    FileCategory.SPREADSHEET -> Icons.Filled.TableChart
    FileCategory.PRESENTATION -> Icons.Filled.Slideshow
    FileCategory.CODE -> Icons.Filled.Code
    FileCategory.TEXT -> Icons.Filled.TextSnippet
    FileCategory.ARCHIVE -> Icons.Filled.FolderZip
    FileCategory.UNKNOWN -> Icons.Filled.InsertDriveFile
}

fun colorFor(category: FileCategory): Color = when (category) {
    FileCategory.FOLDER -> AccentDoc
    FileCategory.PDF -> AccentPdf
    FileCategory.IMAGE -> AccentImage
    FileCategory.VIDEO -> AccentVideo
    FileCategory.AUDIO -> AccentAudio
    FileCategory.DOCUMENT -> AccentDoc
    FileCategory.SPREADSHEET -> AccentSheet
    FileCategory.PRESENTATION -> AccentSlide
    FileCategory.CODE -> AccentCode
    FileCategory.TEXT -> AccentText
    FileCategory.ARCHIVE -> AccentArchive
    FileCategory.UNKNOWN -> AccentText
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return "%.1f %s".format(value, units[digitGroups])
}

fun formatDate(timestampMillis: Long): String {
    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestampMillis))
}
