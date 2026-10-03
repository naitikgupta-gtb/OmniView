package com.omniview.viewer

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.omniview.viewer.data.model.FileItem
import com.omniview.viewer.ui.navigation.OmniNavGraph
import com.omniview.viewer.ui.navigation.Routes
import com.omniview.viewer.ui.theme.OmniViewTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.FileOutputStream

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Resolves an incoming ACTION_VIEW intent (from "Open with") to a local file path. */
    private fun resolveIncomingFile(intent: Intent?): FileItem? {
        val uri: Uri = intent?.data ?: return null
        if (intent.action != Intent.ACTION_VIEW) return null

        return when (uri.scheme) {
            "file" -> uri.path?.let { path -> FileItem.fromFile(File(path)) }
            "content" -> {
                // Copy the content:// stream into our own cache dir so the existing
                // File-based viewers (PdfRenderer, POI, etc.) can open it normally.
                var displayName = "opened_file"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        displayName = cursor.getString(nameIndex)
                    }
                }
                val outFile = File(cacheDir, "opened/$displayName")
                outFile.parentFile?.mkdirs()
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(outFile).use { output -> input.copyTo(output) }
                    }
                    FileItem.fromFile(outFile)
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniViewTheme {
                Surface {
                    var hasPermission by remember { mutableStateOf(hasStoragePermission()) }
                    var pendingIntent by remember { mutableStateOf(intent) }
                    val navController = rememberNavController()

                    val legacyPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { hasPermission = hasStoragePermission() }

                    val manageStorageLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) { hasPermission = hasStoragePermission() }

                    if (hasPermission) {
                        OmniNavGraph(navController = navController)

                        // Once permission is granted (or was already granted) and we were
                        // opened via "Open with", jump straight to the right viewer.
                        LaunchedEffect(pendingIntent, hasPermission) {
                            val fileItem = resolveIncomingFile(pendingIntent)
                            if (fileItem != null) {
                                navController.navigate(Routes.viewer(fileItem))
                                pendingIntent = null
                            }
                        }
                    } else {
                        PermissionRequestScreen(onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:$packageName")
                                }
                                manageStorageLauncher.launch(intent)
                            } else {
                                legacyPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            }
                        })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun hasStoragePermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
}

@Composable
private fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("OmniView needs storage access", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "To open and browse your files, OmniView needs permission to read device storage.\nOmniView is 100% offline — your files never leave your device.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRequestPermission) { Text("Grant Access") }
        }
    }
}
