package com.example.brawlwidgetdemo.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val tag: String,
    val name: String?,
    val trophies: Int?,
    @ColumnInfo(name = "highest_trophies") val highestTrophies: Int?,
    @ColumnInfo(name = "exp_level") val expLevel: Int?,
    @ColumnInfo(name = "club_tag") val clubTag: String?,
    @ColumnInfo(name = "club_name") val clubName: String?,
    @ColumnInfo(name = "profile_icon_id") val profileIconId: Int?,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long
)
