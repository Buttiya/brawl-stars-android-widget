package com.example.brawlwidgetdemo.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "player_tag") val playerTag: String,
    @ColumnInfo(name = "created_at") val createdAt: Long
)
