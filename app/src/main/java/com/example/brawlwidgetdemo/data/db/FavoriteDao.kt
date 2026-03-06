package com.example.brawlwidgetdemo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE player_tag = :tag")
    suspend fun removeFavorite(tag: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE player_tag = :tag)")
    suspend fun isFavorite(tag: String): Boolean

    @Query("SELECT * FROM favorites ORDER BY created_at DESC")
    fun observeAll(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites ORDER BY created_at DESC")
    suspend fun getAll(): List<FavoriteEntity>
}
