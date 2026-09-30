package com.omniview.viewer.ui.viewer.text

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerScreen(path: String, fileName: String, isCsv: Boolean, onBack: () -> Unit) {
    var content by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(path) {
        val lines = withContext(Dispatchers.IO) { File(path).readLines() }
        content = lines
        isLoading = false
    }

    val displayedLines = if (searchQuery.isBlank()) content
        else content.filter { it.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(fileName, maxLines = 1) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                    actions = { IconButton(onClick = { showSearch = !showSearch }) { Icon(Icons.Filled.Search, contentDescription = "Search") } }
                )
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Find in file") },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        singleLine = true
                    )
                }
            }
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        } else if (isCsv) {
            CsvTable(lines = displayedLines, modifier = Modifier.padding(padding))
        } else {
            SelectionContainer {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                    items(displayedLines) { line ->
                        Text(line, fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.padding(vertical = 1.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CsvTable(lines: List<String>, modifier: Modifier = Modifier) {
    val rows = remember(lines) { lines.map { it.split(",") } }
    val hScroll = rememberScrollState()
    LazyColumn(modifier = modifier.fillMaxSize().horizontalScroll(hScroll)) {
        items(rows) { cells ->
            Row {
                cells.forEach { cell ->
                    Text(
                        cell.trim(),
                        modifier = Modifier.width(140.dp).padding(6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2
                    )
                }
            }
            HorizontalDivider()
        }
    }
}
