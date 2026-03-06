package com.example.brawlwidgetdemo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WidgetCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: WidgetCacheEntity)

    @Query("SELECT * FROM widget_cache WHERE id = 1")
    fun observe(): Flow<WidgetCacheEntity?>

    @Query("SELECT * FROM widget_cache WHERE id = 1")
    suspend fun get(): WidgetCacheEntity?
}
