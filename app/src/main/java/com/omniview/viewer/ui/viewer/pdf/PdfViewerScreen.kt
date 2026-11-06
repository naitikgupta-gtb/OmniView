package com.omniview.viewer.ui.viewer.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniview.viewer.ui.theme.AccentPdf
import com.omniview.viewer.util.ShareUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val MAX_RENDER_PIXELS = 8_000_000f

/** Keeps one PdfRenderer open. PdfRenderer allows a single open page at a time, hence the mutex. */
private class PdfHolder(private val pfd: ParcelFileDescriptor, private val renderer: PdfRenderer) {
    private val mutex = Mutex()
    private var closed = false
    val pageCount: Int = renderer.pageCount

    suspend fun pageSizes(): List<Pair<Int, Int>> = mutex.withLock {
        List(pageCount) { i ->
            val page = renderer.openPage(i)
            try { Pair(page.width.coerceAtLeast(1), page.height.coerceAtLeast(1)) } finally { page.close() }
        }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = mutex.withLock {
        if (closed) return@withLock null
        val page = renderer.openPage(index)
        try {
            val heightPx = (widthPx * page.height.toFloat() / page.width).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bmp
        } catch (t: Throwable) {
            null
        } finally {
            page.close()
        }
    }

    suspend fun close() = mutex.withLock {
        if (!closed) {
            closed = true
            try { renderer.close() } catch (_: Exception) {}
            try { pfd.close() } catch (_: Exception) {}
        }
    }
}

/** Scroll position the zoom gesture is steering towards (updated on every pinch step). */
private class ZoomAnchor { var h = 0f; var v = 0f }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(path: String, fileName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var holder by remember { mutableStateOf<PdfHolder?>(null) }
    var sizes by remember { mutableStateOf<List<Pair<Int, Int>>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var barsVisible by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(1f) }
    // Top bar zoom buttons live outside the scrolling area, so they ask for a step through this
    // flag: 1 = zoom out, 2 = zoom in, 0 = idle.
    var zoomStep by remember { mutableStateOf(0) }

    val cache = remember {
        object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6L).toInt()) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
        }
    }

    LaunchedEffect(path) {
        try {
            val opened = withContext(Dispatchers.IO) {
                val pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    val h = PdfHolder(pfd, PdfRenderer(pfd))
                    h to h.pageSizes()
                } catch (t: Throwable) {
                    try { pfd.close() } catch (_: Exception) {}
                    throw t
                }
            }
            holder = opened.first
            sizes = opened.second
            if (sizes.isEmpty()) loadError = "This PDF has no pages."
        } catch (e: Exception) {
            loadError = e.message ?: "Could not open this PDF."
        }
    }

    DisposableEffect(path) {
        onDispose {
            val h = holder
            if (h != null) CoroutineScope(Dispatchers.IO).launch { h.close() }
        }
    }

    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }

    Scaffold(
        containerColor = Color(0xFFEDEDF0),
        topBar = {
            AnimatedVisibility(visible = barsVisible) {
                TopAppBar(
                    title = {
                        Column {
                            Text(fileName, maxLines = 1, fontWeight = FontWeight.SemiBold)
                            if (sizes.isNotEmpty()) {
                                Text("Page $currentPage / ${sizes.size}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = {
                        IconButton(onClick = { zoomStep = 1 }) { Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out") }
                        IconButton(onClick = { zoomStep = 2 }) { Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in") }
                        IconButton(onClick = { ShareUtils.shareFile(context, path) }) { Icon(Icons.Filled.Share, contentDescription = "Share") }
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
        val h = holder
        when {
            loadError != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text("Couldn't open this PDF", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(loadError!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            h == null || sizes.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentPdf)
            }
            else -> BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val viewportWpx = constraints.maxWidth.toFloat()
                val viewportHpx = constraints.maxHeight.toFloat()
                val density = androidx.compose.ui.platform.LocalDensity.current
                val padHPx = with(density) { 10.dp.toPx() }
                val gapPx = with(density) { 14.dp.toPx() }
                val hState = rememberScrollState()
                val scope = rememberCoroutineScope()
                val anchor = remember { ZoomAnchor() }
                var adjustJob by remember { mutableStateOf<Job?>(null) }

                fun pageWidthPx(z: Float) = viewportWpx * z - 2 * padHPx

                /** Distance in pixels from the top of the document to the top of the screen at zoom z. */
                fun scrollTopPx(z: Float): Float {
                    val dw = pageWidthPx(z)
                    var sum = 0f
                    val idx = listState.firstVisibleItemIndex.coerceAtMost(sizes.size)
                    for (i in 0 until idx) sum += dw * sizes[i].second / sizes[i].first + gapPx
                    return sum + listState.firstVisibleItemScrollOffset
                }

                fun beginAdjust() {
                    anchor.h = hState.value.toFloat()
                    anchor.v = scrollTopPx(zoom)
                }

                /**
                 * Zoom keeps the spot under the fingers in place: the page widths grow (so the list
                 * lays out real, bigger pages and nothing is left blank), then scroll positions are
                 * moved so the same part of the page stays under [focus].
                 */
                fun applyZoom(target: Float, focus: Offset, pan: Offset) {
                    val newZoom = target.coerceIn(MIN_ZOOM, MAX_ZOOM)
                    val r = newZoom / zoom
                    anchor.h = (anchor.h + focus.x) * r - focus.x - pan.x
                    anchor.v = (anchor.v + focus.y) * r - focus.y - pan.y
                    zoom = newZoom
                    adjustJob?.cancel()
                    adjustJob = scope.launch {
                        withFrameNanos { }
                        withFrameNanos { }
                        hState.scrollTo(anchor.h.roundToInt().coerceAtLeast(0))
                        listState.scrollBy(anchor.v - scrollTopPx(zoom))
                    }
                }

                LaunchedEffect(zoomStep) {
                    when (zoomStep) {
                        1 -> { beginAdjust(); applyZoom(zoom / 1.25f, Offset(viewportWpx / 2, viewportHpx / 2), Offset.Zero) }
                        2 -> { beginAdjust(); applyZoom(zoom * 1.25f, Offset(viewportWpx / 2, viewportHpx / 2), Offset.Zero) }
                    }
                    zoomStep = 0
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        // Single tap hides/shows the bars; double tap zooms in/out on the tapped spot.
                        .pointerInput(viewportWpx) {
                            detectTapGestures(
                                onTap = { barsVisible = !barsVisible },
                                onDoubleTap = { offset ->
                                    beginAdjust()
                                    applyZoom(if (zoom > 1.05f) 1f else DOUBLE_TAP_ZOOM, offset, Offset.Zero)
                                }
                            )
                        }
                        // Two-finger pinch. Only reacts when two or more fingers are down, so normal
                        // one-finger scrolling is left completely alone.
                        .pointerInput(viewportWpx) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                var started = false
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    if (event.changes.count { it.pressed } >= 2) {
                                        if (!started) { started = true; beginAdjust() }
                                        val zoomChange = event.calculateZoom()
                                        val centroid = event.calculateCentroid()
                                        val pan = event.calculatePan()
                                        if (centroid != Offset.Unspecified) applyZoom(zoom * zoomChange, centroid, pan)
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                ) {
                    Box(Modifier.fillMaxSize().horizontalScroll(hState)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .width(with(density) { (viewportWpx * zoom).toDp() })
                                .fillMaxHeight(),
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(sizes.size) { index ->
                                val (pw, ph) = sizes[index]
                                val aspectHW = ph.toFloat() / pw
                                // Re-render sharper only at a few zoom steps so pinching stays smooth.
                                val bucket = listOf(1f, 1.5f, 2f, 3f, 4f, 5f).firstOrNull { it >= zoom - 0.001f } ?: 5f
                                val wanted = (viewportWpx * bucket - 2 * padHPx).roundToInt()
                                val limit = sqrt(MAX_RENDER_PIXELS / aspectHW).toInt()
                                PdfPage(
                                    index = index,
                                    aspectWH = pw.toFloat() / ph,
                                    renderWidthPx = minOf(wanted, limit).coerceAtLeast(200),
                                    holder = h!!,
                                    cache = cache
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    index: Int,
    aspectWH: Float,
    renderWidthPx: Int,
    holder: PdfHolder,
    cache: LruCache<String, Bitmap>
) {
    // Keeps showing the previous (softer) bitmap until the sharper one is ready.
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(index, renderWidthPx) {
        val key = "$index@$renderWidthPx"
        val hit = cache.get(key)
        if (hit != null) {
            bitmap = hit
        } else {
            val rendered = withContext(Dispatchers.IO) { holder.render(index, renderWidthPx) }
            if (rendered != null) {
                cache.put(key, rendered)
                bitmap = rendered
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        shadowElevation = 6.dp,
        color = Color.White,
        modifier = Modifier.fillMaxWidth().aspectRatio(aspectWH)
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
