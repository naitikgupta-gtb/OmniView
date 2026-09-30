package com.omniview.viewer.ui.viewer.office

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.omniview.viewer.ui.theme.AccentDoc
import com.omniview.viewer.ui.theme.AccentSheet
import com.omniview.viewer.ui.theme.AccentSlide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

private enum class OfficeKind(val htmlAsset: String, val jsFunction: String, val accent: Color) {
    DOCX("office/docx_viewer.html", "renderDoc", AccentDoc),
    XLSX("office/xlsx_viewer.html", "renderSheet", AccentSheet),
    PPTX("office/pptx_viewer.html", "renderPptx", AccentSlide)
}

/** Page size + margins (in twips, 1/1440 inch) of a .docx, read from its last <w:sectPr>. */
private fun readDocxPageMeta(path: String): String {
    var w = 11906; var h = 16838
    var top = 1440; var right = 1440; var bottom = 1440; var left = 1440
    try {
        ZipFile(File(path)).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return@use
            val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
            fun attr(tag: String, name: String): Int? =
                Regex("""$name="(-?\d+)"""").find(tag)?.groupValues?.get(1)?.toIntOrNull()
            Regex("""<w:pgSz[^>]*>""").findAll(xml).lastOrNull()?.value?.let { tag ->
                attr(tag, "w:w")?.let { w = it }; attr(tag, "w:h")?.let { h = it }
            }
            Regex("""<w:pgMar[^>]*>""").findAll(xml).lastOrNull()?.value?.let { tag ->
                attr(tag, "w:top")?.let { top = kotlin.math.abs(it) }
                attr(tag, "w:right")?.let { right = it }
                attr(tag, "w:bottom")?.let { bottom = kotlin.math.abs(it) }
                attr(tag, "w:left")?.let { left = it }
            }
        }
    } catch (_: Exception) { /* fall back to A4 with 1" margins */ }
    return """{"w":$w,"h":$h,"top":$top,"right":$right,"bottom":$bottom,"left":$left}"""
}

/**
 * Renders .docx/.xlsx/.pptx close to how they look in Office, using browser-side engines
 * (mammoth.js, SheetJS, pptx-preview) inside a local, offline WebView. One tap on the
 * document hides / shows the top bar and the on-page controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeWebViewer(path: String, fileName: String, extension: String, onBack: () -> Unit) {
    val kind = when (extension.lowercase()) {
        "docx" -> OfficeKind.DOCX
        "xlsx" -> OfficeKind.XLSX
        "pptx" -> OfficeKind.PPTX
        else -> OfficeKind.DOCX
    }
    var base64Content by remember { mutableStateOf<String?>(null) }
    var docxMeta by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var barsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            docxMeta = if (kind == OfficeKind.DOCX) readDocxPageMeta(path) else null
            base64Content = Base64.encodeToString(File(path).readBytes(), Base64.NO_WRAP)
        }
    }

    // Page loaded + file bytes ready -> hand the file to the page's render function.
    LaunchedEffect(pageReady, base64Content) {
        val data = base64Content
        val wv = webView
        if (pageReady && data != null && wv != null) {
            val extra = docxMeta?.let { ", $it" } ?: ""
            wv.evaluateJavascript("${kind.jsFunction}(\"$data\"$extra)", null)
        }
    }

    // Keep the in-page controls (zoom, indicator, scrollbar) in sync with the top bar.
    LaunchedEffect(barsVisible, pageReady) {
        webView?.evaluateJavascript(
            "window.OmniViewerControls && OmniViewerControls.setVisible($barsVisible)", null
        )
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(visible = barsVisible) {
                TopAppBar(
                    title = { Text(fileName, maxLines = 1) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = kind.accent.copy(alpha = 0.12f))
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { ctx ->
                    createOfficeWebView(
                        ctx,
                        onPageFinished = { pageReady = true },
                        onTapCallback = { barsVisible = !barsVisible }
                    ).also { webView = it }
                        .apply { loadUrl("file:///android_asset/${kind.htmlAsset}") }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (base64Content == null || !pageReady) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = kind.accent)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
private fun createOfficeWebView(
    context: android.content.Context,
    onPageFinished: () -> Unit,
    onTapCallback: () -> Unit
): WebView =
    WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.allowFileAccess = true
        settings.domStorageEnabled = true
        // Native pinch-to-zoom in addition to the on-page +/- buttons.
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        isVerticalScrollBarEnabled = true
        addJavascriptInterface(object {
            @JavascriptInterface
            fun onTap() {
                Handler(Looper.getMainLooper()).post { onTapCallback() }
            }
        }, "OmniAndroid")
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                onPageFinished()
            }
        }
    }
