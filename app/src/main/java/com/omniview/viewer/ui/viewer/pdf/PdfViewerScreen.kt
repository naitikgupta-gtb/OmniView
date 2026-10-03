package com.omniview.viewer.ui.viewer.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniview.viewer.ui.theme.AccentPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders every page of the PDF to a Bitmap using Android's built-in PdfRenderer
 * (no third-party PDF library needed, keeps APK small), shown as scrollable,
 * shadowed page cards with pinch-to-zoom + pan across the whole document.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(path: String, fileName: String, onBack: () -> Unit) {
    var pageBitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var pageCount by remember { mutableStateOf(0) }

    var barsVisible by remember { mutableStateOf(true) }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val listState = rememberLazyListState()

    LaunchedEffect(path) {
        isLoading = true
        loadError = null
        try {
            val bitmaps = withContext(Dispatchers.IO) {
                val descriptor = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(descriptor)
                val list = mutableListOf<Bitmap>()
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    // Render at 2x page size for crisp text when the user pinch-zooms in.
                    val bmp = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    list.add(bmp)
                    page.close()
                }
                pageCount = renderer.pageCount
                renderer.close()
                descriptor.close()
                list
            }
            pageBitmaps = bitmaps
            if (bitmaps.isEmpty()) loadError = "This PDF has no pages, or could not be decoded."
        } catch (e: Exception) {
            loadError = e.message ?: "Could not open this PDF."
        }
        isLoading = false
    }

    Scaffold(
        containerColor = Color(0xFFEDEDF0), // soft grey so white pages stand out, like a real reader
        topBar = {
            AnimatedVisibility(visible = barsVisible) {
            TopAppBar(
                title = {
                    Column {
                        Text(fileName, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        if (pageCount > 0) {
                            Text("$pageCount pages", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { scale = (scale - 0.25f).coerceAtLeast(1f) }) { Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out") }
                    IconButton(onClick = { scale = (scale + 0.25f).coerceAtMost(4f) }) { Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in") }
                    IconButton(onClick = { }) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
            }
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentPdf)
            }
            loadError != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't open this PDF", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(loadError!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // Tap-to-hide and pinch-zoom must share ONE pointerInput block — two
                    // separate pointerInput modifiers each try to own the touch stream and
                    // end up fighting each other, which is why pinch had stopped responding
                    // after tap-to-hide was added.
                    .pointerInput(Unit) {
                        coroutineScope {
                            launch { detectTapGestures(onTap = { barsVisible = !barsVisible }) }
                            launch {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 4f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                        }
                    }
                    // Zoom is applied to the WHOLE scrolling column as one layer, not per page —
                    // scaling each page individually left their layout bounds unchanged while the
                    // pixels grew, so neighbouring pages visually overlapped. Scaling the entire
                    // column keeps every page's position relative to the others correct.
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offsetX, translationY = offsetY
                    ),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(pageBitmaps) { bmp ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        shadowElevation = 6.dp,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
