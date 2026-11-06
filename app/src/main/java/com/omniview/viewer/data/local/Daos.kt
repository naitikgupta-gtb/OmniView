package com.omniview.viewer.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: FavoriteEntity)

    @Delete
    suspend fun remove(item: FavoriteEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE path = :path)")
    suspend fun isFavorite(path: String): Boolean

    @Query("DELETE FROM favorites WHERE path = :path")
    suspend fun removeByPath(path: String)
}

@Dao
interface RecentDao {
    @Query("SELECT * FROM recents ORDER BY lastOpenedAt DESC LIMIT 50")
    fun observeAll(): Flow<List<RecentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: RecentEntity)

    @Query("UPDATE recents SET lastPosition = :position WHERE path = :path")
    suspend fun updatePosition(path: String, position: Int)

    @Query("DELETE FROM recents")
    suspend fun clearAll()
}
