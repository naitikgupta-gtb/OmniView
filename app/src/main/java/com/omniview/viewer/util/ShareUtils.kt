package com.omniview.viewer.util

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Shares a file through Android's system share sheet, so it can go to WhatsApp, Telegram,
 * Gmail, Bluetooth, Nearby Share, Google Drive, or any other app the person has installed.
 */
object ShareUtils {

    fun shareFile(context: Context, path: String) {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            Toast.makeText(context, "This file can't be shared", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = uriFor(context, file)
        if (uri == null) {
            Toast.makeText(context, "Couldn't prepare this file for sharing", Toast.LENGTH_SHORT).show()
            return
        }

        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"

        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            // ClipData makes sure the receiving app is granted read access on every Android version.
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share ${file.name}")
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "No app available to share with", Toast.LENGTH_SHORT).show()
        }
    }

    private fun uriFor(context: Context, file: File): Uri? {
        val authority = "${context.packageName}.fileprovider"
        return try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: IllegalArgumentException) {
            // File is in a place FileProvider isn't configured for (for example an SD card).
            // Share a temporary copy from the app's cache instead.
            try {
                val copyDir = File(context.cacheDir, "shared").apply { mkdirs() }
                val copy = File(copyDir, file.name)
                file.copyTo(copy, overwrite = true)
                FileProvider.getUriForFile(context, authority, copy)
            } catch (e2: Exception) {
                null
            }
        }
    }
}
