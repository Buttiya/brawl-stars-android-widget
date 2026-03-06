package com.example.brawlwidgetdemo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(player: PlayerEntity)

    @Query("SELECT * FROM players WHERE tag = :tag LIMIT 1")
    fun observeByTag(tag: String): Flow<PlayerEntity?>

    @Query("SELECT * FROM players WHERE tag = :tag LIMIT 1")
    suspend fun getByTag(tag: String): PlayerEntity?

    @Query("SELECT p.* FROM players p INNER JOIN favorites f ON p.tag = f.player_tag ORDER BY f.created_at DESC")
    fun observeFavorites(): Flow<List<PlayerEntity>>
}
