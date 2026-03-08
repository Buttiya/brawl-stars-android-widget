package com.example.brawlwidgetdemo.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_trophy_history",
    foreignKeys = [
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["tag"],
            childColumns = ["player_tag"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["player_tag", "record_date"], unique = true),
        Index(value = ["player_tag", "captured_at"])
    ]
)
data class DailyTrophyHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "player_tag") val playerTag: String,
    @ColumnInfo(name = "record_date") val recordDate: Long,
    val trophies: Int,
    @ColumnInfo(name = "daily_delta") val dailyDelta: Int,
    @ColumnInfo(name = "captured_at") val capturedAt: Long
)
