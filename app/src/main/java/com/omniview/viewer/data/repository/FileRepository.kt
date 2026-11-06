package com.omniview.viewer.data.repository

import android.os.Environment
import com.omniview.viewer.data.local.FavoriteDao
import com.omniview.viewer.data.local.FavoriteEntity
import com.omniview.viewer.data.local.RecentDao
import com.omniview.viewer.data.local.RecentEntity
import com.omniview.viewer.data.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class SortOrder { NAME, DATE, SIZE, TYPE }

@Singleton
class FileRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val recentDao: RecentDao
) {
    fun rootDirectory(): File = Environment.getExternalStorageDirectory()

    suspend fun listDirectory(
        path: String,
        sortOrder: SortOrder = SortOrder.NAME,
        query: String? = null
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val dir = File(path)
        val children = dir.listFiles()?.toList() ?: emptyList()
        val filtered = if (!query.isNullOrBlank()) {
            children.filter { it.name.contains(query, ignoreCase = true) }
        } else children

        val items = filtered.map { FileItem.fromFile(it) }
        val sorted = when (sortOrder) {
            SortOrder.NAME -> items.sortedBy { it.name.lowercase() }
            SortOrder.DATE -> items.sortedByDescending { it.lastModified }
            SortOrder.SIZE -> items.sortedByDescending { it.sizeBytes }
            SortOrder.TYPE -> items.sortedBy { it.extension }
        }
        // Folders always first
        sorted.sortedByDescending { it.isDirectory }
    }

    suspend fun searchDevice(query: String, root: File = rootDirectory(), maxResults: Int = 200): List<FileItem> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<FileItem>()
            fun walk(dir: File) {
                if (results.size >= maxResults) return
                val children = dir.listFiles() ?: return
                for (child in children) {
                    if (results.size >= maxResults) return
                    if (child.name.contains(query, ignoreCase = true)) {
                        results.add(FileItem.fromFile(child))
                    }
                    if (child.isDirectory && !child.name.startsWith(".")) walk(child)
                }
            }
            walk(root)
            results
        }

    suspend fun searchByCategory(
        category: com.omniview.viewer.data.model.FileCategory,
        root: File = rootDirectory(),
        maxResults: Int = 300
    ): List<FileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FileItem>()
        fun walk(dir: File) {
            if (results.size >= maxResults) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (results.size >= maxResults) return
                if (child.isFile) {
                    val item = FileItem.fromFile(child)
                    if (item.category == category) results.add(item)
                }
                if (child.isDirectory && !child.name.startsWith(".")) walk(child)
            }
        }
        walk(root)
        results.sortedByDescending { it.lastModified }
    }

    // Favorites
    fun observeFavorites(): Flow<List<FavoriteEntity>> = favoriteDao.observeAll()
    suspend fun toggleFavorite(item: FileItem) {
        if (favoriteDao.isFavorite(item.path)) {
            favoriteDao.removeByPath(item.path)
        } else {
            favoriteDao.add(FavoriteEntity(path = item.path, name = item.name))
        }
    }
    suspend fun isFavorite(path: String): Boolean = favoriteDao.isFavorite(path)

    // Recents
    fun observeRecents(): Flow<List<RecentEntity>> = recentDao.observeAll()
    suspend fun recordOpened(item: FileItem) {
        recentDao.upsert(
            RecentEntity(path = item.path, name = item.name, lastOpenedAt = System.currentTimeMillis())
        )
    }
    suspend fun updateLastPosition(path: String, position: Int) = recentDao.updatePosition(path, position)
    suspend fun clearRecents() = recentDao.clearAll()

    // Basic file ops
    fun rename(item: FileItem, newName: String): Boolean {
        val target = File(item.file.parentFile, newName)
        return item.file.renameTo(target)
    }

    fun delete(item: FileItem): Boolean = item.file.deleteRecursively()

    fun copy(item: FileItem, destinationDir: File): Boolean = try {
        item.file.copyTo(File(destinationDir, item.name), overwrite = false)
        true
    } catch (e: Exception) { false }
}
