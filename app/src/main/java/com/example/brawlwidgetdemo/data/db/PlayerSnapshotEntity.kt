package com.example.brawlwidgetdemo.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "player_stats_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = PlayerEntity::class,
            parentColumns = ["tag"],
            childColumns = ["player_tag"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["player_tag", "captured_at"])])
data class PlayerSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "player_tag") val playerTag: String,
    val trophies: Int?,
    @ColumnInfo(name = "highest_trophies") val highestTrophies: Int?,
    @ColumnInfo(name = "exp_level") val expLevel: Int?,
    @ColumnInfo(name = "captured_at") val capturedAt: Long
)
