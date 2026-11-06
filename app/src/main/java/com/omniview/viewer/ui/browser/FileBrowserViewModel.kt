package com.omniview.viewer.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omniview.viewer.data.local.FavoriteEntity
import com.omniview.viewer.data.local.RecentEntity
import com.omniview.viewer.data.model.FileCategory
import com.omniview.viewer.data.model.FileItem
import com.omniview.viewer.data.repository.FileRepository
import com.omniview.viewer.data.repository.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowserUiState(
    val currentPath: String = "",
    val items: List<FileItem> = emptyList(),
    val isGridView: Boolean = false,
    val sortOrder: SortOrder = SortOrder.NAME,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val isLoading: Boolean = false,
    val favorites: List<FavoriteEntity> = emptyList(),
    val recents: List<RecentEntity> = emptyList(),
    val categoryFilter: FileCategory? = null
)

@HiltViewModel
class FileBrowserViewModel @Inject constructor(
    private val repository: FileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val rootPath: String = repository.rootDirectory().absolutePath

    init {
        _uiState.value = _uiState.value.copy(currentPath = rootPath)
        loadDirectory(rootPath)
        observeFavoritesAndRecents()
    }

    private fun observeFavoritesAndRecents() {
        viewModelScope.launch {
            repository.observeFavorites().collect { favs ->
                _uiState.value = _uiState.value.copy(favorites = favs)
            }
        }
        viewModelScope.launch {
            repository.observeRecents().collect { recents ->
                _uiState.value = _uiState.value.copy(recents = recents)
            }
        }
    }

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, currentPath = path)
            val items = repository.listDirectory(path, _uiState.value.sortOrder)
            _uiState.value = _uiState.value.copy(items = items, isLoading = false)
        }
    }

    fun navigateInto(item: FileItem) {
        if (item.isDirectory) loadDirectory(item.path)
    }

    fun navigateUp(): Boolean {
        val current = java.io.File(_uiState.value.currentPath)
        val parent = current.parentFile
        return if (parent != null && current.absolutePath != rootPath) {
            loadDirectory(parent.absolutePath)
            true
        } else false
    }

    /** True when the browser is showing the root folder with no search/category filter active
     *  — i.e. there's nowhere left for the system back button to take the user "up" to. */
    fun isAtTopLevel(): Boolean =
        _uiState.value.currentPath == rootPath && !_uiState.value.isSearching

    /** Clears an active search or category filter, returning to normal folder browsing. */
    fun clearSearch() {
        onSearchQueryChange("")
    }

    fun toggleGridView() {
        _uiState.value = _uiState.value.copy(isGridView = !_uiState.value.isGridView)
    }

    /** Called when the user taps a category chip (Images, Videos, PDF, etc.) on the home screen. */
    fun browseCategory(category: FileCategory) {
        _uiState.value = _uiState.value.copy(
            categoryFilter = category,
            isSearching = true,
            searchQuery = category.name.lowercase().replaceFirstChar { it.uppercase() },
            isLoading = true
        )
        viewModelScope.launch {
            val results = repository.searchByCategory(category)
            _uiState.value = _uiState.value.copy(items = results, isLoading = false)
        }
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.value = _uiState.value.copy(sortOrder = order)
        loadDirectory(_uiState.value.currentPath)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query, isSearching = query.isNotBlank(), categoryFilter = null)
        if (query.isBlank()) {
            loadDirectory(_uiState.value.currentPath)
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val results = repository.searchDevice(query)
            _uiState.value = _uiState.value.copy(items = results, isLoading = false)
        }
    }

    fun toggleFavorite(item: FileItem) {
        viewModelScope.launch { repository.toggleFavorite(item) }
    }

    fun recordOpened(item: FileItem) {
        viewModelScope.launch { repository.recordOpened(item) }
    }

    fun deleteItem(item: FileItem) {
        viewModelScope.launch {
            repository.delete(item)
            loadDirectory(_uiState.value.currentPath)
        }
    }

    fun renameItem(item: FileItem, newName: String) {
        viewModelScope.launch {
            repository.rename(item, newName)
            loadDirectory(_uiState.value.currentPath)
        }
    }
}
