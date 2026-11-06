package com.omniview.viewer.ui.viewer.image

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import com.omniview.viewer.util.ShareUtils
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val DOUBLE_TAP_SCALE = 2.5f
private const val MAX_SCALE = 6f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerScreen(path: String, fileName: String, onBack: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val context = LocalContext.current

    /** Keeps the zoomed image from being dragged off screen. */
    fun clampOffsets() {
        val maxX = box.width * (scale - 1f) / 2f
        val maxY = box.height * (scale - 1f) / 2f
        offsetX = offsetX.coerceIn(-maxX, maxX)
        offsetY = offsetY.coerceIn(-maxY, maxY)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { ShareUtils.shareFile(context, path) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onSizeChanged { box = it }
                // Tap handling and pinch/pan share one pointerInput block so they don't fight.
                .pointerInput(Unit) {
                    coroutineScope {
                        launch {
                            detectTapGestures(onDoubleTap = { tap ->
                                if (scale > 1.05f) {
                                    scale = 1f; offsetX = 0f; offsetY = 0f
                                } else {
                                    // Zoom in on the tapped spot.
                                    val center = Offset(box.width / 2f, box.height / 2f)
                                    scale = DOUBLE_TAP_SCALE
                                    offsetX = (center.x - tap.x) * (DOUBLE_TAP_SCALE - 1f)
                                    offsetY = (center.y - tap.y) * (DOUBLE_TAP_SCALE - 1f)
                                    clampOffsets()
                                }
                            })
                        }
                        launch {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                    clampOffsets()
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = path,
                contentDescription = fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offsetX, translationY = offsetY
                    )
            )
        }
    }
}
