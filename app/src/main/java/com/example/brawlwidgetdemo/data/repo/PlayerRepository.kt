package com.example.brawlwidgetdemo.data.repo

import com.example.brawlwidgetdemo.data.db.FavoriteDao
import com.example.brawlwidgetdemo.data.db.FavoriteEntity
import com.example.brawlwidgetdemo.data.db.PlayerDao
import com.example.brawlwidgetdemo.data.db.PlayerEntity
import com.example.brawlwidgetdemo.data.db.PlayerSnapshotEntity
import com.example.brawlwidgetdemo.data.db.SnapshotDao
import com.example.brawlwidgetdemo.data.db.WidgetCacheDao
import com.example.brawlwidgetdemo.data.db.WidgetCacheEntity
import com.example.brawlwidgetdemo.data.network.OfficialBrawlStarsService
import com.example.brawlwidgetdemo.domain.isTagValid
import com.example.brawlwidgetdemo.domain.normalizeTag
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
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
    private val officialApi: OfficialBrawlStarsService?,
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
            return Result.failure(IllegalArgumentException("Невалидный тег. Допустимы только символы: 0,2,8,9,P,Y,L,Q,G,R,J,C,U,V"))
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

    suspend fun setTrackedMode(mode: TrackingMode) {
        val prev = widgetCacheDao.get() ?: emptyWidgetCache()
        widgetCacheDao.upsert(
            prev.copy(
                trackedModeKey = mode.key,
                trackedModeLabel = mode.label,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveProfileForWidget(rawTag: String) {
        val tag = normalizeTag(rawTag)
        val previous = widgetCacheDao.get() ?: emptyWidgetCache()
        widgetCacheDao.upsert(previous.copy(savedPlayerTag = tag, updatedAt = System.currentTimeMillis()))
        refreshSavedProfilePart(tag)
    }

    suspend fun refreshWidgetData() {
        refreshTrackedModePart()
        val savedTag = widgetCacheDao.get()?.savedPlayerTag
        if (!savedTag.isNullOrBlank()) {
            refreshSavedProfilePart(savedTag)
        }
    }

    private suspend fun fetchAndCachePlayer(rawTag: String): Result<PlayerEntity> {
        val tag = normalizeTag(rawTag)

        if (officialApi == null) {
            return Result.failure(IOException("Добавь BRAWL_STARS_API_TOKEN в gradle.properties"))
        }

        val official = requestOfficialPlayer(tag)
        if (official != null) {
            playerDao.upsert(official)
            insertSnapshotIfChanged(official)
            return Result.success(official)
        }

        return Result.failure(IOException("Игрок не найден или токен BRAWL_STARS_API_TOKEN недействителен"))
    }

    private suspend fun requestOfficialPlayer(tag: String): PlayerEntity? {
        val service = officialApi ?: return null
        val response = runCatching { service.getPlayer(tag) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null

        val body = response.body() ?: return null
        return mapOfficialPlayer(tag, body)
    }

    private suspend fun refreshTrackedModePart() {
        val selectedMode = getTrackedMode()
        val rotation = requestOfficialRotationEvents()

        val now = Instant.now()
        val current = rotation
            .filter { event -> event.start <= now && event.end > now }
            .firstOrNull { event -> selectedMode.matches(event.modeEvent.modeName, event.modeEvent.modeHash, event.slotId) }
            ?.modeEvent

        val nextFromOfficial = rotation
            .filter { event -> event.start > now }
            .sortedBy { event -> event.start }
            .firstOrNull { event -> selectedMode.matches(event.modeEvent.modeName, event.modeEvent.modeHash, event.slotId) }
            ?.modeEvent

        val next = nextFromOfficial ?: requestPredictedFromBrawlify(selectedMode)
        val modeIcons = requestBrawlifyGameModesBody()

        val prev = widgetCacheDao.get() ?: emptyWidgetCache()
        widgetCacheDao.upsert(
            prev.copy(
                trackedModeKey = selectedMode.key,
                trackedModeLabel = selectedMode.label,
                trackedCurrentModeName = current?.modeName ?: selectedMode.label,
                trackedCurrentModeIconUrl = resolveModeIconUrl(modeIcons, current),
                trackedNextModeName = next?.modeName ?: selectedMode.label,
                trackedNextModeIconUrl = resolveModeIconUrl(modeIcons, next),
                soloCurrentMapName = current?.mapName ?: "-",
                soloCurrentMapImageUrl = current?.mapImage,
                soloNextMapName = next?.mapName ?: "TBD",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun requestOfficialRotationEvents(): List<RotationEvent> {
        val service = officialApi ?: return emptyList()
        val response = runCatching { service.getEventsRotation() }.getOrNull() ?: return emptyList()
        if (!response.isSuccessful) return emptyList()

        val items = response.body() ?: return emptyList()
        return items.mapNotNull { raw ->
            val root = raw.asObj() ?: return@mapNotNull null
            val event = root.getObj("event") ?: return@mapNotNull null

            val start = parseOfficialTime(root.getStr("startTime")) ?: return@mapNotNull null
            val end = parseOfficialTime(root.getStr("endTime")) ?: return@mapNotNull null
            val mapName = event.getStr("map") ?: return@mapNotNull null
            val mode = event.getStr("mode")

            RotationEvent(
                slotId = root.getInt("slotId"),
                modeEvent = ModeEvent(
                    mapName = mapName,
                    mapImage = null,
                    modeId = event.getInt("modeId"),
                    modeName = mode ?: selectedLabelForUnknownMode(root.getInt("slotId")),
                    modeHash = mode
                ),
                start = start,
                end = end
            )
        }
    }

    private suspend fun requestBrawlifyGameModesBody(): JsonObject? {
        val json = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL("https://api.brawlapi.com/v1/gamemodes").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "Brawlify.com/app")
                    setRequestProperty("Accept", "application/json")
                }

                connection.inputStream.bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: return null

        val parsed = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return null
        return when {
            parsed.isJsonObject -> parsed.asJsonObject
            parsed.isJsonArray -> JsonObject().apply { add("list", parsed.asJsonArray) }
            else -> null
        }
    }

    private suspend fun requestPredictedFromBrawlify(mode: TrackingMode): ModeEvent? {
        val html = withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL("https://brawlify.com/events").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 15000
                    setRequestProperty("User-Agent", "Mozilla/5.0 Brawlify.com/app")
                    setRequestProperty("Accept", "text/html")
                }

                connection.inputStream.bufferedReader().use { it.readText() }
            }.getOrNull()
        } ?: return null

        val text = html
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val predictedChunks = Regex("\\d{1,3}%\\s+PREDICTED\\s+(.+?)\\s+Est\\.", RegexOption.IGNORE_CASE)
            .findAll(text)
            .map { it.groupValues[1].trim() }
            .toList()

        for (chunk in predictedChunks) {
            val candidate = parsePredictedChunk(chunk) ?: continue
            if (mode.matches(candidate.modeName, candidate.modeHash, null)) {
                return candidate
            }
        }

        return null
    }

    private fun parsePredictedChunk(chunk: String): ModeEvent? {
        val normalized = chunk.replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return null

        val modeHints = listOf(
            "TRIO SHOWDOWN" to "trio-showdown",
            "DUO SHOWDOWN" to "duo-showdown",
            "SOLO SHOWDOWN" to "solo-showdown",
            "SHOWDOWN" to "showdown",
            "GEM GRAB" to "gem-grab",
            "BRAWL BALL" to "brawl-ball",
            "KNOCKOUT" to "knockout",
            "HEIST" to "heist",
            "BOUNTY" to "bounty",
            "HOT ZONE" to "hot-zone"
        )

        val upper = normalized.uppercase()
        val match = modeHints.firstOrNull { (label, _) -> upper.endsWith(label) } ?: return null

        val modeLabel = match.first
        val modeHash = match.second
        val mapName = normalized.substring(0, normalized.length - modeLabel.length).trim()
        if (mapName.isBlank()) return null

        return ModeEvent(
            mapName = mapName,
            mapImage = null,
            modeId = null,
            modeName = modeLabel,
            modeHash = modeHash
        )
    }

    private fun resolveModeIconUrl(gameModesBody: JsonObject?, event: ModeEvent?): String? {
        if (event == null || gameModesBody == null) return null

        val modes = gameModesBody.getArr("list")
            ?: gameModesBody.getArr("items")
            ?: gameModesBody.getArr("gamemodes")
            ?: return null

        val byId = event.modeId?.let { id ->
            modes.firstOrNull { raw -> raw.asObj()?.getInt("id") == id }?.asObj()
        }

        if (byId != null) {
            return byId.getStr("imageUrl") ?: byId.getStr("imageUrl2")
        }

        val desiredTokens = listOfNotNull(event.modeHash, event.modeName)
            .map(::normalizeModeToken)
            .toSet()

        val byToken = modes.firstOrNull { raw ->
            val obj = raw.asObj() ?: return@firstOrNull false
            val candidateTokens = listOfNotNull(obj.getStr("hash"), obj.getStr("name"))
                .map(::normalizeModeToken)
                .toSet()
            candidateTokens.any { token -> desiredTokens.any { desired -> token.contains(desired) || desired.contains(token) } }
        }?.asObj()

        return byToken?.getStr("imageUrl") ?: byToken?.getStr("imageUrl2")
    }

    private suspend fun refreshSavedProfilePart(rawTag: String) {
        val player = fetchAndCachePlayer(rawTag).getOrNull() ?: return
        val iconUrl = resolveIconUrl(player.profileIconId)

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
    }

    private fun resolveIconUrl(iconId: Int?): String? {
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
    val modeHash: String?
)

data class RotationEvent(
    val slotId: Int?,
    val modeEvent: ModeEvent,
    val start: Instant,
    val end: Instant
)

private val OFFICIAL_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSX").withZone(ZoneOffset.UTC)

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
