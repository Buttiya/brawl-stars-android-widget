package com.example.brawlwidgetdemo.data.repo

import com.example.brawlwidgetdemo.data.db.DailyTrophyHistoryDao
import com.example.brawlwidgetdemo.data.db.DailyTrophyHistoryEntity
import com.example.brawlwidgetdemo.data.db.FavoriteDao
import com.example.brawlwidgetdemo.data.db.FavoriteEntity
import com.example.brawlwidgetdemo.data.db.PlayerDao
import com.example.brawlwidgetdemo.data.db.PlayerEntity
import com.example.brawlwidgetdemo.data.db.PlayerSnapshotEntity
import com.example.brawlwidgetdemo.data.db.SnapshotDao
import com.example.brawlwidgetdemo.data.db.WidgetCacheDao
import com.example.brawlwidgetdemo.data.db.WidgetCacheEntity
import com.example.brawlwidgetdemo.data.network.AppApiService
import com.example.brawlwidgetdemo.domain.isTagValid
import com.example.brawlwidgetdemo.domain.normalizeTag
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class TrackingMode(
    val key: String,
    val label: String,
    private val modeTokens: Set<String>,
    private val slotFallbacks: Set<Int> = emptySet()
) {
    Showdown(
        key = "showdown",
        label = "Showdown",
        modeTokens = setOf("showdown", "soloshowdown", "duoshowdown", "trioshowdown"),
        slotFallbacks = setOf(2, 5, 39)
    ),
    GemGrab(
        key = "gem_grab",
        label = "Gem Grab",
        modeTokens = setOf("gemgrab")
    ),
    BrawlBall(
        key = "brawl_ball",
        label = "Brawl Ball",
        modeTokens = setOf("brawlball")
    ),
    Knockout(
        key = "knockout",
        label = "Knockout",
        modeTokens = setOf("knockout")
    ),
    Bounty(
        key = "bounty",
        label = "Bounty",
        modeTokens = setOf("bounty")
    ),
    HotZone(
        key = "hot_zone",
        label = "Hot Zone",
        modeTokens = setOf("hotzone")
    );

    fun matches(modeName: String?, modeHash: String?, slot: Int?): Boolean {
        val normalized = listOfNotNull(modeName, modeHash)
            .map(::normalizeModeToken)
            .toSet()

        if (normalized.any { token ->
                modeTokens.contains(token) ||
                    modeTokens.any { known -> token.contains(known) || known.contains(token) }
            }) {
            return true
        }

        return slot != null && slotFallbacks.contains(slot)
    }

    companion object {
        val all: List<TrackingMode> = entries

        fun fromKey(key: String?): TrackingMode {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: Showdown
        }
    }
}

class PlayerRepository(
    private val api: AppApiService,
    private val playerDao: PlayerDao,
    private val snapshotDao: SnapshotDao,
    private val dailyTrophyHistoryDao: DailyTrophyHistoryDao,
    private val favoriteDao: FavoriteDao,
    private val widgetCacheDao: WidgetCacheDao
) {
    fun observePlayer(tag: String): Flow<PlayerEntity?> = playerDao.observeByTag(tag)

    fun observeDailyTrophyHistory(tag: String): Flow<List<DailyTrophyHistoryEntity>> =
        dailyTrophyHistoryDao.observeByTag(tag)

    fun observeFavoritePlayers(): Flow<List<PlayerEntity>> = playerDao.observeFavorites()

    fun observeWidgetCache(): Flow<WidgetCacheEntity?> = widgetCacheDao.observe()

    suspend fun getWidgetCache(): WidgetCacheEntity? = widgetCacheDao.get()

    suspend fun getPlayerByTag(rawTag: String): PlayerEntity? = playerDao.getByTag(normalizeTag(rawTag))

    suspend fun getOwnedBrawlerIds(rawTag: String): Result<Set<Int>> {
        val tag = normalizeTag(rawTag)
        if (!isTagValid(tag)) {
            return Result.failure(IllegalArgumentException("Невалидный тег"))
        }

        val response = runCatching { api.getPlayer(tag) }.getOrNull()
            ?: return Result.failure(IOException("Не удалось запросить профиль игрока"))
        if (!response.isSuccessful) {
            return Result.failure(IOException("Не удалось получить список бойцов"))
        }

        val body = response.body()
            ?: return Result.failure(IOException("Пустой ответ API"))
        val brawlers = body.getArr("brawlers") ?: return Result.success(emptySet())

        val ids = brawlers.mapNotNull { raw -> raw.asObj()?.getInt("id") }.toSet()
        return Result.success(ids)
    }

    suspend fun searchPlayer(rawTag: String): Result<String> {
        val tag = normalizeTag(rawTag)
        if (!isTagValid(tag)) {
            return Result.failure(IllegalArgumentException("Невалидный тег. Допустимы только символы: 0,2,8,9,P,Y,L,Q,G,R,J,C,U,V"))
        }

        return fetchAndCachePlayer(tag).map { it.tag }
    }

    suspend fun refreshPlayer(rawTag: String): Result<PlayerEntity> {
        val tag = normalizeTag(rawTag)
        if (!isTagValid(tag)) {
            return Result.failure(IllegalArgumentException("Невалидный тег. Допустимы только символы: 0,2,8,9,P,Y,L,Q,G,R,J,C,U,V"))
        }

        return fetchAndCachePlayer(tag)
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

    suspend fun setTrackedMode(mode: TrackingMode) {
        val prev = widgetCacheDao.get() ?: emptyWidgetCache()
        widgetCacheDao.upsert(
            prev.copy(
                trackedModeKey = mode.key,
                trackedModeLabel = mode.label,
                trackedCurrentModeName = mode.label,
                trackedCurrentModeIconUrl = null,
                trackedNextModeName = mode.label,
                trackedNextModeIconUrl = null,
                soloCurrentMapName = "-",
                soloCurrentMapImageUrl = null,
                soloNextMapName = "TBD",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveProfileForWidget(rawTag: String) {
        val tag = normalizeTag(rawTag)
        val previous = widgetCacheDao.get() ?: emptyWidgetCache()
        widgetCacheDao.upsert(previous.copy(savedPlayerTag = tag, updatedAt = System.currentTimeMillis()))
        refreshSavedProfilePart(tag).getOrThrow()
    }

    suspend fun refreshWidgetData() {
        refreshTrackedModePart()
        val savedTag = widgetCacheDao.get()?.savedPlayerTag
        if (!savedTag.isNullOrBlank()) {
            refreshSavedProfilePart(savedTag).getOrThrow()
        }
    }

    private suspend fun fetchAndCachePlayer(rawTag: String): Result<PlayerEntity> {
        val tag = normalizeTag(rawTag)

        val player = requestApiPlayer(tag)
        if (player != null) {
            playerDao.upsert(player)
            insertSnapshotIfChanged(player)
            upsertDailyTrophyHistory(player)
            return Result.success(player)
        }

        return Result.failure(IOException("Игрок не найден или API недоступен"))
    }

    private suspend fun requestApiPlayer(tag: String): PlayerEntity? {
        val response = runCatching { api.getPlayer(tag) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null

        val body = response.body() ?: return null
        return mapOfficialPlayer(tag, body)
    }

    private suspend fun refreshTrackedModePart() {
        val selectedMode = getTrackedMode()
        val trackedMap = requestTrackedMap(selectedMode)
        val current = trackedMap?.current
        val next = trackedMap?.next

        val prev = widgetCacheDao.get() ?: emptyWidgetCache()
        widgetCacheDao.upsert(
            prev.copy(
                trackedModeKey = selectedMode.key,
                trackedModeLabel = selectedMode.label,
                trackedCurrentModeName = current?.modeName ?: selectedMode.label,
                trackedCurrentModeIconUrl = current?.modeIconUrl,
                trackedNextModeName = next?.modeName ?: current?.modeName ?: selectedMode.label,
                trackedNextModeIconUrl = next?.modeIconUrl,
                soloCurrentMapName = current?.mapName ?: "-",
                soloCurrentMapImageUrl = current?.mapImage,
                soloNextMapName = next?.mapName ?: current?.mapName ?: "TBD",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun requestTrackedMap(mode: TrackingMode): TrackedModePayload? {
        val response = runCatching { api.getTrackedMap(mode.key) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null
        val body = response.body() ?: return null
        return TrackedModePayload(
            current = body.getObj("current")?.toModeEvent(),
            next = body.getObj("next")?.toModeEvent()
        )
    }

    private suspend fun refreshSavedProfilePart(rawTag: String): Result<Unit> {
        val player = fetchAndCachePlayer(rawTag).getOrElse { return Result.failure(it) }
        val iconUrl = profileIconUrl(player.profileIconId)

        val prev = widgetCacheDao.get() ?: emptyWidgetCache()
        widgetCacheDao.upsert(
            prev.copy(
                savedPlayerTag = player.tag,
                savedPlayerName = player.name ?: prev.savedPlayerName ?: "Player #${player.tag}",
                savedPlayerTrophies = player.trophies ?: prev.savedPlayerTrophies,
                savedPlayerExpLevel = player.expLevel ?: prev.savedPlayerExpLevel,
                savedPlayerIconUrl = iconUrl ?: prev.savedPlayerIconUrl,
                updatedAt = System.currentTimeMillis()
            )
        )
        return Result.success(Unit)
    }

    fun profileIconUrl(iconId: Int?): String? {
        if (iconId == null) return null
        return "https://cdn.brawlify.com/profile-icons/regular/$iconId.png"
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

    private suspend fun upsertDailyTrophyHistory(player: PlayerEntity) {
        val trophies = player.trophies ?: return
        val now = System.currentTimeMillis()
        val today = LocalDate.now(HISTORY_ZONE_ID).toEpochDay()
        val existing = dailyTrophyHistoryDao.getByTagAndDate(player.tag, today)
        val previous = dailyTrophyHistoryDao.getPreviousBefore(player.tag, today)
        val dailyDelta = trophies - (previous?.trophies ?: trophies)

        dailyTrophyHistoryDao.upsert(
            DailyTrophyHistoryEntity(
                id = existing?.id ?: 0,
                playerTag = player.tag,
                recordDate = today,
                trophies = trophies,
                dailyDelta = dailyDelta,
                capturedAt = now
            )
        )
    }

    private suspend fun getTrackedMode(): TrackingMode {
        val key = widgetCacheDao.get()?.trackedModeKey
        return TrackingMode.fromKey(key)
    }

    private fun mapOfficialPlayer(tag: String, payload: JsonObject): PlayerEntity {
        val club = payload.getObj("club")
        val icon = payload.getObj("icon")

        return PlayerEntity(
            tag = (payload.getStr("tag")?.replace("#", "") ?: tag).uppercase(),
            name = payload.getStr("name"),
            trophies = payload.getInt("trophies"),
            highestTrophies = payload.getInt("highestTrophies"),
            expLevel = payload.getInt("expLevel"),
            clubTag = club?.getStr("tag")?.replace("#", ""),
            clubName = club?.getStr("name"),
            profileIconId = icon?.getInt("id"),
            victories3v3 = payload.getInt("3vs3Victories"),
            soloVictories = payload.getInt("soloVictories"),
            duoVictories = payload.getInt("duoVictories"),
            lastSyncedAt = System.currentTimeMillis()
        )
    }

    private fun emptyWidgetCache() = WidgetCacheEntity(
        id = 1,
        trackedModeKey = TrackingMode.Showdown.key,
        trackedModeLabel = TrackingMode.Showdown.label,
        trackedCurrentModeName = TrackingMode.Showdown.label,
        trackedCurrentModeIconUrl = null,
        trackedNextModeName = TrackingMode.Showdown.label,
        trackedNextModeIconUrl = null,
        soloCurrentMapName = null,
        soloCurrentMapImageUrl = null,
        soloNextMapName = "TBD",
        savedPlayerTag = null,
        savedPlayerName = null,
        savedPlayerTrophies = null,
        savedPlayerExpLevel = null,
        savedPlayerIconUrl = null,
        updatedAt = System.currentTimeMillis()
    )
}

data class ModeEvent(
    val mapName: String,
    val mapImage: String?,
    val modeId: Int?,
    val modeName: String,
    val modeHash: String?,
    val modeIconUrl: String? = null
)

data class RotationEvent(
    val slotId: Int?,
    val modeEvent: ModeEvent,
    val start: Instant,
    val end: Instant
)

data class TrackedModePayload(
    val current: ModeEvent?,
    val next: ModeEvent?
)

private val OFFICIAL_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSX").withZone(ZoneOffset.UTC)

private val HISTORY_ZONE_ID: ZoneId = ZoneId.systemDefault()

private fun parseOfficialTime(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.from(OFFICIAL_TIME_FORMATTER.parse(value)) }.getOrNull()
}

private fun selectedLabelForUnknownMode(slotId: Int?): String {
    return when (slotId) {
        2, 5, 39 -> "Showdown"
        else -> "Unknown"
    }
}

private fun normalizeModeToken(value: String): String {
    return value.lowercase().replace(Regex("[^a-z0-9]"), "")
}

private fun JsonObject.toModeEvent(): ModeEvent? {
    val mapName = getStr("mapName") ?: return null
    val modeName = getStr("modeName") ?: return null
    return ModeEvent(
        mapName = mapName,
        mapImage = getStr("mapImage"),
        modeId = getInt("modeId"),
        modeName = modeName,
        modeHash = getStr("modeHash"),
        modeIconUrl = getStr("modeIconUrl")
    )
}

private fun JsonObject.getObj(key: String): JsonObject? {
    val value = get(key) ?: return null
    return runCatching { if (value.isJsonObject) value.asJsonObject else null }.getOrNull()
}

private fun JsonObject.getArr(key: String): JsonArray? {
    val value = get(key) ?: return null
    return runCatching { if (value.isJsonArray) value.asJsonArray else null }.getOrNull()
}

private fun JsonElement.asObj(): JsonObject? = if (isJsonObject) asJsonObject else null

private fun JsonObject.getStr(key: String): String? {
    val value: JsonElement = get(key) ?: return null
    if (value.isJsonNull) return null
    return runCatching { value.asString }.getOrNull()
}

private fun JsonObject.getInt(key: String): Int? {
    val value: JsonElement = get(key) ?: return null
    return if (value.isJsonNull) null else runCatching { value.asInt }.getOrNull()
}


