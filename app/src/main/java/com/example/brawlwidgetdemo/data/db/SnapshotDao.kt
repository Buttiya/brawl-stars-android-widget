package com.example.brawlwidgetdemo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SnapshotDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(snapshot: PlayerSnapshotEntity)

    @Query("SELECT * FROM player_stats_snapshots WHERE player_tag = :tag ORDER BY captured_at DESC LIMIT 1")
    suspend fun getLatest(tag: String): PlayerSnapshotEntity?
}
