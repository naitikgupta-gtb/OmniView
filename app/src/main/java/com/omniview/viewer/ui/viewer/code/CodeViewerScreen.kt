package com.omniview.viewer.ui.viewer.code

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Universal code viewer: line numbers + lightweight syntax highlighting, works for any text-based extension. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeViewerScreen(path: String, fileName: String, extension: String, onBack: () -> Unit) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var fontSize by remember { mutableStateOf(14f) }

    LaunchedEffect(path) {
        lines = withContext(Dispatchers.IO) {
            try { File(path).readLines() } catch (e: Exception) { listOf("Could not read file: ${e.message}") }
        }
        isLoading = false
    }

    val hScroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileName, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    IconButton(onClick = { fontSize = (fontSize - 1f).coerceAtLeast(10f) }) { Icon(Icons.Filled.TextDecrease, contentDescription = "Smaller") }
                    IconButton(onClick = { fontSize = (fontSize + 1f).coerceAtMost(32f) }) { Icon(Icons.Filled.TextIncrease, contentDescription = "Larger") }
                }
            )
        },
        containerColor = Color(0xFF282C34)
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        } else {
            SelectionContainer {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .horizontalScroll(hScroll)
                        // Pinch to change the text size, same idea as zooming a page.
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                fontSize = (fontSize * zoom).coerceIn(10f, 32f)
                            }
                        }
                ) {
                    itemsIndexed(lines) { index, line ->
                        Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text(
                                text = (index + 1).toString().padStart(4, ' '),
                                color = Color(0xFF5C6370),
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Text(
                                text = CodeHighlighter.highlight(line, extension),
                                fontFamily = FontFamily.Monospace,
                                fontSize = fontSize.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
