package com.example.brawlwidgetdemo.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "widget_cache")
data class WidgetCacheEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "solo_current_map_name") val soloCurrentMapName: String?,
    @ColumnInfo(name = "solo_current_map_image_url") val soloCurrentMapImageUrl: String?,
    @ColumnInfo(name = "solo_next_map_name") val soloNextMapName: String?,
    @ColumnInfo(name = "saved_player_tag") val savedPlayerTag: String?,
    @ColumnInfo(name = "saved_player_trophies") val savedPlayerTrophies: Int?,
    @ColumnInfo(name = "saved_player_exp_level") val savedPlayerExpLevel: Int?,
    @ColumnInfo(name = "saved_player_icon_url") val savedPlayerIconUrl: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)
