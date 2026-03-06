package com.example.brawlwidgetdemo.data.repo

import com.example.brawlwidgetdemo.data.db.FavoriteDao
import com.example.brawlwidgetdemo.data.db.FavoriteEntity
import com.example.brawlwidgetdemo.data.db.PlayerDao
import com.example.brawlwidgetdemo.data.db.PlayerEntity
import com.example.brawlwidgetdemo.data.db.PlayerSnapshotEntity
import com.example.brawlwidgetdemo.data.db.SnapshotDao
import com.example.brawlwidgetdemo.data.db.WidgetCacheDao
import com.example.brawlwidgetdemo.data.db.WidgetCacheEntity
import com.example.brawlwidgetdemo.data.network.BrawlApiService
import com.example.brawlwidgetdemo.domain.isTagValid
import com.example.brawlwidgetdemo.domain.normalizeTag
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import java.io.IOException

class PlayerRepository(
    private val api: BrawlApiService,
    private val playerDao: PlayerDao,
    private val snapshotDao: SnapshotDao,
    private val favoriteDao: FavoriteDao,
    private val widgetCacheDao: WidgetCacheDao
) {
    fun observePlayer(tag: String): Flow<PlayerEntity?> = playerDao.observeByTag(tag)

    fun observeFavoritePlayers(): Flow<List<PlayerEntity>> = playerDao.observeFavorites()

    fun observeWidgetCache(): Flow<WidgetCacheEntity?> = widgetCacheDao.observe()

    suspend fun getWidgetCache(): WidgetCacheEntity? = widgetCacheDao.get()

    suspend fun searchPlayer(rawTag: String): Result<String> {
        val tag = normalizeTag(rawTag)
        if (!isTagValid(tag)) {
            return Result.failure(IllegalArgumentException("Невалидный тег"))
        }

        return fetchAndCachePlayer(tag).map { it.tag }
    }

    suspend fun toggleFavorite(tag: String): Boolean {
        val normalized = normalizeTag(tag)
        return if (favoriteDao.isFavorite(normalized)) {
            favoriteDao.removeFavorite(normalized)
            false
        } else {
            favoriteDao.addFavorite(FavoriteEntity(playerTag = normalized, createdAt = System.currentTimeMillis()))
            true
        }
    }

    suspend fun refreshFavorites() {
        favoriteDao.getAll().forEach { favorite ->
            fetchAndCachePlayer(favorite.playerTag)
        }
    }

    suspend fun saveProfileForWidget(rawTag: String) {
        val tag = normalizeTag(rawTag)
        val previous = widgetCacheDao.get() ?: emptyWidgetCache()
        widgetCacheDao.upsert(previous.copy(savedPlayerTag = tag, updatedAt = System.currentTimeMillis()))
        refreshSavedProfilePart(tag)
    }

    suspend fun refreshWidgetData() {
        refreshSoloMapsPart()
        val savedTag = widgetCacheDao.get()?.savedPlayerTag
        if (!savedTag.isNullOrBlank()) {
            refreshSavedProfilePart(savedTag)
        }
    }

    private suspend fun fetchAndCachePlayer(rawTag: String): Result<PlayerEntity> {
        val tag = normalizeTag(rawTag)
        val response = api.getPlayerGraphs(tag)
        if (!response.isSuccessful) {
            return Result.failure(IOException(errorMessageForCode(response.code())))
        }

        val body = response.body() ?: return Result.failure(IOException("Пустой ответ API"))
        val player = mapPlayer(tag, body)

        playerDao.upsert(player)
        insertSnapshotIfChanged(player)
        return Result.success(player)
    }

    private suspend fun refreshSoloMapsPart() {
        val response = api.getEvents()
        if (!response.isSuccessful) return

        val body = response.body() ?: return
        val (current, next) = parseSoloEvents(body)

        val prev = widgetCacheDao.get() ?: emptyWidgetCache()
        widgetCacheDao.upsert(
            prev.copy(
                soloCurrentMapName = current?.mapName,
                soloCurrentMapImageUrl = current?.mapImage,
                soloNextMapName = next?.mapName ?: "TBD",
                soloNextMapStartAt = next?.startAt,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun refreshSavedProfilePart(rawTag: String) {
        val player = fetchAndCachePlayer(rawTag).getOrNull() ?: return
        val iconUrl = resolveIconUrl(player.profileIconId)

        val prev = widgetCacheDao.get() ?: emptyWidgetCache()
        widgetCacheDao.upsert(
            prev.copy(
                savedPlayerTag = player.tag,
                savedPlayerTrophies = player.trophies,
                savedPlayerIconUrl = iconUrl,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun resolveIconUrl(iconId: Int?): String? {
        if (iconId == null) return null

        val response = api.getIcons()
        if (!response.isSuccessful) return null

        val body = response.body() ?: return null
        val allIcons = body.getObj("player") ?: body.getObj("profiles") ?: body
        val entries = allIcons.getArr("regular")
            ?: allIcons.getArr("list")
            ?: allIcons.getArr("items")
            ?: allIcons.getArr("icons")
            ?: JsonArray()

        entries.forEach { raw ->
            val obj = raw.asObj() ?: return@forEach
            if (obj.getInt("id") == iconId) {
                return obj.getStr("imageUrl2")
                    ?: obj.getStr("imageUrl")
                    ?: obj.getStr("url")
            }
        }

        return null
    }

    private suspend fun insertSnapshotIfChanged(player: PlayerEntity) {
        val latest = snapshotDao.getLatest(player.tag)
        val hasChanged = latest == null || latest.trophies != player.trophies

        if (hasChanged) {
            snapshotDao.insert(
                PlayerSnapshotEntity(
                    playerTag = player.tag,
                    trophies = player.trophies,
                    highestTrophies = player.highestTrophies,
                    expLevel = player.expLevel,
                    capturedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun parseSoloEvents(payload: JsonObject): Pair<SoloMap?, SoloMap?> {
        val active = payload.getArr("active") ?: payload.getArr("current") ?: JsonArray()
        val upcoming = payload.getArr("upcoming") ?: payload.getArr("next") ?: JsonArray()

        val current = active.firstSolo()
        val next = upcoming.firstSolo()

        return current to next
    }

    private fun JsonArray.firstSolo(): SoloMap? {
        forEach { raw ->
            val event = raw.asObj() ?: return@forEach
            val mode = event.getObj("mode")
            val modeName = mode?.getStr("name") ?: event.getStr("mode") ?: event.getStr("gameMode")
            if (!modeName.orEmpty().contains("solo showdown", ignoreCase = true)) {
                return@forEach
            }

            val map = event.getObj("map")
            return SoloMap(
                mapName = map?.getStr("name") ?: event.getStr("map") ?: "Unknown",
                mapImage = map?.getStr("imageUrl") ?: map?.getStr("imageUrl2"),
                startAt = event.getStr("startTime")?.toLongOrNull()
                    ?: event.getStr("start")?.toLongOrNull()
            )
        }
        return null
    }

    private fun mapPlayer(tag: String, payload: JsonObject): PlayerEntity {
        val root = payload.getObj("player") ?: payload
        val club = root.getObj("club")
        val icon = root.getObj("icon")

        return PlayerEntity(
            tag = (root.getStr("tag")?.replace("#", "") ?: tag).uppercase(),
            name = root.getStr("name") ?: payload.getStr("name"),
            trophies = root.getInt("trophies") ?: payload.getInt("trophies"),
            highestTrophies = root.getInt("highestTrophies") ?: payload.getInt("highestTrophies"),
            expLevel = root.getInt("expLevel") ?: payload.getInt("expLevel"),
            clubTag = club?.getStr("tag")?.replace("#", ""),
            clubName = club?.getStr("name"),
            profileIconId = icon?.getInt("id") ?: root.getInt("iconId"),
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    private fun emptyWidgetCache() = WidgetCacheEntity(
        id = 1,
        soloCurrentMapName = null,
        soloCurrentMapImageUrl = null,
        soloNextMapName = "TBD",
        soloNextMapStartAt = null,
        savedPlayerTag = null,
        savedPlayerTrophies = null,
        savedPlayerIconUrl = null,
        updatedAt = System.currentTimeMillis()
    )

    private fun errorMessageForCode(code: Int): String = when (code) {
        404 -> "Игрок не найден"
        429 -> "Превышен лимит API"
        else -> "Сервис временно недоступен ($code)"
    }
}

data class SoloMap(
    val mapName: String,
    val mapImage: String?,
    val startAt: Long?
)

private fun JsonObject.getObj(key: String): JsonObject? {
    val value = get(key) ?: return null
    return if (value.isJsonObject) value.asJsonObject else null
}

private fun JsonObject.getArr(key: String): JsonArray? {
    val value = get(key) ?: return null
    return if (value.isJsonArray) value.asJsonArray else null
}

private fun JsonElement.asObj(): JsonObject? = if (isJsonObject) asJsonObject else null

private fun JsonObject.getStr(key: String): String? {
    val value: JsonElement = get(key) ?: return null
    return if (value.isJsonNull) null else value.asString
}

private fun JsonObject.getInt(key: String): Int? {
    val value: JsonElement = get(key) ?: return null
    return if (value.isJsonNull) null else runCatching { value.asInt }.getOrNull()
}
