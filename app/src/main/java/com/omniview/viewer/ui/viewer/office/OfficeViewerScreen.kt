package com.omniview.viewer.ui.viewer.office

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniview.viewer.ui.theme.AccentDoc
import com.omniview.viewer.ui.theme.AccentSheet
import com.omniview.viewer.ui.theme.AccentSlide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

// ------------------------------- data shapes -------------------------------

data class SheetData(val name: String, val rows: List<List<String>>)
data class SlideData(val index: Int, val lines: List<String>, val hadNonTextContent: Boolean)

sealed class OfficeContent {
    data class Paragraphs(val items: List<String>) : OfficeContent()
    data class Sheets(val items: List<SheetData>) : OfficeContent()
    data class Slides(val items: List<SlideData>) : OfficeContent()
    data class Error(val message: String) : OfficeContent()
    object Empty : OfficeContent()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OfficeViewerScreen(path: String, fileName: String, extension: String, onBack: () -> Unit) {
    var content by remember { mutableStateOf<OfficeContent>(OfficeContent.Empty) }
    var isLoading by remember { mutableStateOf(true) }

    val accentColor = when (extension.lowercase()) {
        "doc", "docx" -> AccentDoc
        "xls", "xlsx" -> AccentSheet
        "ppt", "pptx" -> AccentSlide
        else -> AccentDoc
    }

    LaunchedEffect(path) {
        isLoading = true
        content = withContext(Dispatchers.IO) {
            try {
                when (extension.lowercase()) {
                    "docx" -> OfficeContent.Paragraphs(extractDocxXml(path))
                    "xlsx" -> OfficeContent.Sheets(extractXlsxStructured(path))
                    "pptx" -> OfficeContent.Slides(extractPptxStructured(path))
                    "doc", "xls", "ppt" -> OfficeContent.Error(
                        "This is an old Office 97-2003 format (.${extension.lowercase()}). " +
                        "Supporting it would require a library that only works on Android 8.0+, " +
                        "which would drop support for Android 7. Please re-save this file as " +
                        ".docx / .xlsx / .pptx (in Word/Excel/PowerPoint: File → Save As → choose " +
                        "the newer format) and it will open normally."
                    )
                    else -> OfficeContent.Error("Unsupported office format: $extension")
                }
            } catch (e: Exception) {
                OfficeContent.Error(e.message ?: e.javaClass.simpleName)
            }
        }
        isLoading = false
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                HorizontalDivider(color = accentColor.copy(alpha = 0.25f), thickness = 2.dp)
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val c = content) {
                OfficeContent.Empty -> if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = accentColor) }
                }
                is OfficeContent.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Couldn't open this file", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(c.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                is OfficeContent.Paragraphs -> ParagraphView(c.items)
                is OfficeContent.Sheets -> SpreadsheetView(c.items, accentColor)
                is OfficeContent.Slides -> SlideView(c.items, accentColor)
            }
        }
    }
}

// ------------------------------- DOCX / DOC view -------------------------------

@Composable
private fun ParagraphView(paragraphs: List<String>) {
    if (paragraphs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No readable text found in this file", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    SelectionContainer {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
            items(paragraphs) { paragraph ->
                Text(paragraph, modifier = Modifier.padding(vertical = 5.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

// ------------------------------- XLSX / XLS view -------------------------------

@Composable
private fun SpreadsheetView(sheets: List<SheetData>, accentColor: Color) {
    if (sheets.isEmpty() || sheets.all { it.rows.isEmpty() }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data found in this spreadsheet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var selectedSheet by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        if (sheets.size > 1) {
            ScrollableTabRow(selectedTabIndex = selectedSheet, containerColor = Color.White, contentColor = accentColor) {
                sheets.forEachIndexed { index, sheet ->
                    Tab(selected = selectedSheet == index, onClick = { selectedSheet = index }, text = { Text(sheet.name) })
                }
            }
        }
        SheetGrid(sheets[selectedSheet], accentColor)
    }
}

@Composable
private fun SheetGrid(sheet: SheetData, accentColor: Color) {
    val colCount = (sheet.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val hScroll = rememberScrollState()
    val cellWidth = 110.dp

    Column(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
        // Header row: column letters
        Row(Modifier.background(accentColor.copy(alpha = 0.12f))) {
            GridCell("", width = 40.dp, bold = true, isHeader = true)
            repeat(colCount) { col ->
                GridCell(columnLetter(col), width = cellWidth, bold = true, isHeader = true)
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(sheet.rows) { rowIndex, row ->
                Row {
                    GridCell((rowIndex + 1).toString(), width = 40.dp, bold = true, isHeader = true, background = accentColor.copy(alpha = 0.06f))
                    repeat(colCount) { col ->
                        GridCell(row.getOrNull(col) ?: "", width = cellWidth)
                    }
                }
            }
        }
    }
}

@Composable
private fun GridCell(text: String, width: androidx.compose.ui.unit.Dp, bold: Boolean = false, isHeader: Boolean = false, background: Color = Color.Transparent) {
    Box(
        modifier = Modifier
            .width(width)
            .heightIn(min = 40.dp)
            .background(background)
            .border(width = 0.5.dp, color = Color(0xFFE0E0E0))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = if (bold) MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodySmall,
            color = if (isHeader) Color(0xFF444444) else Color.Black
        )
    }
}

private fun columnLetter(index: Int): String {
    var i = index
    val sb = StringBuilder()
    do {
        sb.insert(0, ('A' + (i % 26)))
        i = i / 26 - 1
    } while (i >= 0)
    return sb.toString()
}

// ------------------------------- PPTX view -------------------------------

@Composable
private fun SlideView(slides: List<SlideData>, accentColor: Color) {
    if (slides.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No slides found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val slide = slides[page]
            SelectionContainer {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text("Slide ${slide.index}", style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    if (slide.lines.isEmpty()) {
                        Text("(No text on this slide)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        slide.lines.forEachIndexed { i, line ->
                            Text(
                                line,
                                style = if (i == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                                fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }
                    if (slide.hadNonTextContent) {
                        Spacer(Modifier.height(12.dp))
                        Surface(shape = RoundedCornerShape(8.dp), color = accentColor.copy(alpha = 0.1f)) {
                            Text(
                                "[Chart / image content — not shown in text view]",
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor
                            )
                        }
                    }
                }
            }
        }

        // Bottom slide-number strip, like a filmstrip
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            items(slides) { slide ->
                val isSelected = pagerState.currentPage == slide.index - 1
                Surface(
                    onClick = { scope.launch { pagerState.animateScrollToPage(slide.index - 1) } },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) accentColor else Color.White,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = 44.dp, height = 32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${slide.index}",
                            color = if (isSelected) Color.White else Color(0xFF666666),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------
// Modern OOXML formats — manual ZIP + XmlPullParser (no POI needed at all)
// ---------------------------------------------------------------------------------

private fun newPullParser(): XmlPullParser =
    android.util.Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
    }

private fun XmlPullParser.localName(): String = (name ?: "").substringAfterLast(':')

private fun extractDocxXml(path: String): List<String> {
    ZipFile(File(path)).use { zip ->
        val entry = zip.getEntry("word/document.xml") ?: return listOf("Could not find document content.")
        val parser = newPullParser()
        parser.setInput(zip.getInputStream(entry), "UTF-8")

        val paragraphs = mutableListOf<String>()
        val currentParagraph = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.localName() == "t") {
                        currentParagraph.append(parser.nextText())
                        event = parser.eventType
                        continue
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.localName() == "p") {
                        val text = currentParagraph.toString().trim()
                        if (text.isNotBlank()) paragraphs.add(text)
                        currentParagraph.clear()
                    }
                }
            }
            event = parser.next()
        }
        return paragraphs
    }
}

private fun extractPptxStructured(path: String): List<SlideData> {
    ZipFile(File(path)).use { zip ->
        val slideEntries = zip.entries().asSequence()
            .filter { it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedBy { entry -> Regex("\\d+").find(entry.name)?.value?.toIntOrNull() ?: 0 }
            .toList()

        return slideEntries.mapIndexed { index, entry ->
            val lines = mutableListOf<String>()
            var hadNonText = false
            val parser = newPullParser()
            parser.setInput(zip.getInputStream(entry), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val local = parser.localName()
                    if (local == "t") {
                        val text = parser.nextText().trim()
                        if (text.isNotBlank()) lines.add(text)
                        event = parser.eventType
                        continue
                    } else if (local == "graphicFrame" || local == "pic") {
                        hadNonText = true
                    }
                }
                event = parser.next()
            }
            SlideData(index + 1, lines, hadNonText)
        }
    }
}

private fun extractXlsxStructured(path: String): List<SheetData> {
    ZipFile(File(path)).use { zip ->
        val sharedStrings = mutableListOf<String>()
        zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
            val parser = newPullParser()
            parser.setInput(zip.getInputStream(entry), "UTF-8")
            var event = parser.eventType
            val current = StringBuilder()
            var insideSi = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.localName()) {
                        "si" -> { insideSi = true; current.clear() }
                        "t" -> if (insideSi) current.append(parser.nextText())
                    }
                    XmlPullParser.END_TAG -> if (parser.localName() == "si") {
                        sharedStrings.add(current.toString())
                        insideSi = false
                    }
                }
                event = parser.next()
            }
        }

        val sheetEntries = zip.entries().asSequence()
            .filter { it.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .sortedBy { entry -> Regex("\\d+").find(entry.name)?.value?.toIntOrNull() ?: 0 }
            .toList()

        return sheetEntries.mapIndexed { sheetIndex, entry ->
            val parser = newPullParser()
            parser.setInput(zip.getInputStream(entry), "UTF-8")

            var event = parser.eventType
            val allRows = mutableListOf<List<String>>()
            var rowCells = mutableListOf<String>()
            var cellType: String? = null
            var cellValue = StringBuilder()
            var inCell = false

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.localName()) {
                        "row" -> rowCells = mutableListOf()
                        "c" -> { inCell = true; cellType = parser.getAttributeValue(null, "t"); cellValue = StringBuilder() }
                        "v" -> if (inCell) cellValue.append(parser.nextText())
                        "t" -> if (inCell && cellType == "inlineStr") cellValue.append(parser.nextText())
                    }
                    XmlPullParser.END_TAG -> when (parser.localName()) {
                        "c" -> {
                            val resolved = when (cellType) {
                                "s" -> cellValue.toString().toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                                else -> cellValue.toString()
                            }
                            rowCells.add(resolved)
                            inCell = false
                        }
                        "row" -> if (rowCells.any { it.isNotBlank() }) allRows.add(rowCells)
                    }
                }
                event = parser.next()
            }
            SheetData("Sheet ${sheetIndex + 1}", allRows)
        }
    }
}
