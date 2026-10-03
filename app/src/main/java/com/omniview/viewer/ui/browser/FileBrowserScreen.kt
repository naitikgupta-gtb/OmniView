package com.omniview.viewer.ui.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omniview.viewer.data.model.FileCategory
import com.omniview.viewer.data.model.FileItem
import com.omniview.viewer.data.repository.SortOrder
import com.omniview.viewer.util.colorFor
import com.omniview.viewer.util.formatDate
import com.omniview.viewer.util.formatFileSize
import com.omniview.viewer.util.iconFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    onOpenFile: (FileItem) -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var lastBackPressTime by remember { mutableStateOf(0L) }

    // System back button: clear an active search/category first, then step up one folder
    // at a time, and only let the system close the app once we're already at the top —
    // and even then, require a second press within 2s so a stray back-tap doesn't exit.
    BackHandler(enabled = true) {
        when {
            state.isSearching -> viewModel.clearSearch()
            viewModel.navigateUp() -> Unit
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000) {
                    (context as? android.app.Activity)?.finish()
                } else {
                    lastBackPressTime = now
                    android.widget.Toast.makeText(context, "Press back again to exit", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OmniView", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.toggleGridView() }) {
                        Icon(
                            if (state.isGridView) Icons.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = "Toggle view"
                        )
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            DropdownMenuItem(text = { Text("Name") }, onClick = { viewModel.setSortOrder(SortOrder.NAME); showSortMenu = false })
                            DropdownMenuItem(text = { Text("Date") }, onClick = { viewModel.setSortOrder(SortOrder.DATE); showSortMenu = false })
                            DropdownMenuItem(text = { Text("Size") }, onClick = { viewModel.setSortOrder(SortOrder.SIZE); showSortMenu = false })
                            DropdownMenuItem(text = { Text("Type") }, onClick = { viewModel.setSortOrder(SortOrder.TYPE); showSortMenu = false })
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search files on device") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )

            if (!state.isSearching) {
                // Category shortcuts
                Text(
                    "Categories",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                CategoryGrid(onCategoryClick = { cat -> viewModel.browseCategory(cat) })

                if (state.recents.isNotEmpty()) {
                    Text(
                        "Recent Files",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyRow(modifier = Modifier.padding(horizontal = 12.dp)) {
                        items(state.recents) { recent ->
                            RecentChip(name = recent.name) {
                                onOpenFile(FileItem.fromFile(java.io.File(recent.path)))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Files", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { viewModel.navigateUp() }) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Up")
                    }
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.items.isEmpty()) {
                EmptyState(text = if (state.isSearching) "No files found" else "This folder is empty")
            } else if (state.isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.items) { item ->
                        FileGridCell(item = item, onClick = {
                            if (item.isDirectory) viewModel.navigateInto(item)
                            else { viewModel.recordOpened(item); onOpenFile(item) }
                        })
                    }
                }
            } else {
                LazyColumn2(items = state.items, onClick = { item ->
                    if (item.isDirectory) viewModel.navigateInto(item)
                    else { viewModel.recordOpened(item); onOpenFile(item) }
                }, onFavoriteToggle = { viewModel.toggleFavorite(it) })
            }
        }
    }
}

@Composable
private fun LazyColumn2(
    items: List<FileItem>,
    onClick: (FileItem) -> Unit,
    onFavoriteToggle: (FileItem) -> Unit
) {
    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items) { item ->
            FileListRow(item = item, onClick = { onClick(item) }, onFavoriteToggle = { onFavoriteToggle(item) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun FileListRow(item: FileItem, onClick: () -> Unit, onFavoriteToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colorFor(item.category).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconFor(item.category), contentDescription = null, tint = colorFor(item.category))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (item.isDirectory) formatDate(item.lastModified)
                else "${formatFileSize(item.sizeBytes)} • ${formatDate(item.lastModified)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onFavoriteToggle) {
            Icon(Icons.Filled.StarBorder, contentDescription = "Favorite")
        }
    }
}

@Composable
private fun FileGridCell(item: FileItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colorFor(item.category).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(iconFor(item.category), contentDescription = null, tint = colorFor(item.category), modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RecentChip(name: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(6.dp)
    ) {
        Text(name, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private data class CategoryDef(val label: String, val icon: ImageVector, val category: FileCategory)

@Composable
private fun CategoryGrid(onCategoryClick: (FileCategory) -> Unit) {
    val categories = listOf(
        CategoryDef("Images", Icons.Filled.Image, FileCategory.IMAGE),
        CategoryDef("Videos", Icons.Filled.Videocam, FileCategory.VIDEO),
        CategoryDef("Audio", Icons.Filled.Audiotrack, FileCategory.AUDIO),
        CategoryDef("PDF", Icons.Filled.PictureAsPdf, FileCategory.PDF),
        CategoryDef("Documents", Icons.Filled.Description, FileCategory.DOCUMENT),
        CategoryDef("Code", Icons.Filled.Code, FileCategory.CODE),
    )
    LazyRow(modifier = Modifier.padding(horizontal = 10.dp)) {
        items(categories) { cat ->
            Surface(
                onClick = { onCategoryClick(cat.category) },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(6.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(cat.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(cat.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
