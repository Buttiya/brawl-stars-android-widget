package com.example.brawlwidgetdemo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTrophyHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DailyTrophyHistoryEntity)

    @Query(
        "SELECT * FROM daily_trophy_history " +
            "WHERE player_tag = :tag " +
            "ORDER BY record_date ASC"
    )
    fun observeByTag(tag: String): Flow<List<DailyTrophyHistoryEntity>>

    @Query(
        "SELECT * FROM daily_trophy_history " +
            "WHERE player_tag = :tag AND record_date = :recordDate " +
            "LIMIT 1"
    )
    suspend fun getByTagAndDate(tag: String, recordDate: Long): DailyTrophyHistoryEntity?

    @Query(
        "SELECT * FROM daily_trophy_history " +
            "WHERE player_tag = :tag AND record_date < :recordDate " +
            "ORDER BY record_date DESC LIMIT 1"
    )
    suspend fun getPreviousBefore(tag: String, recordDate: Long): DailyTrophyHistoryEntity?
}
