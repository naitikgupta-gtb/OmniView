package com.omniview.viewer.ui.viewer.video

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Handles both video and audio playback via Media3/ExoPlayer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPlayerScreen(path: String, fileName: String, isAudio: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(java.io.File(path))))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (isAudio) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "\uD83C\uDFB5",
                        fontSize = 64.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(fileName, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true } },
                        modifier = Modifier.fillMaxWidth().height(60.dp)
                    )
                }
            } else {
                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true } },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
