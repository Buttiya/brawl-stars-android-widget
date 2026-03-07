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
import com.example.brawlwidgetdemo.data.network.OfficialBrawlStarsService
import com.example.brawlwidgetdemo.domain.isTagValid
import com.example.brawlwidgetdemo.domain.normalizeTag
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

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

        if (normalized.any { token -> modeTokens.contains(token) }) {
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
    private val api: BrawlApiService,
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

        val response = requestPlayer(tag)
        if (response.isSuccessful && response.body() != null) {
            val player = mapPlayer(tag, response.body()!!)
            playerDao.upsert(player)
            insertSnapshotIfChanged(player)
            return Result.success(player)
        }

        val official = requestOfficialPlayer(tag)
        if (official != null) {
            playerDao.upsert(official)
            insertSnapshotIfChanged(official)
            return Result.success(official)
        }

        if (response.code() == 404) {
            if (officialApi == null) {
                return Result.failure(
                    IOException(
                        "Поиск профиля недоступен: api.brawlapi.com больше не отдает player endpoint. " +
                            "Добавь BRAWL_STARS_API_TOKEN в gradle.properties."
                    )
                )
            }
            return Result.failure(IOException("Игрок не найден или токен BRAWL_STARS_API_TOKEN недействителен"))
        }

        return Result.failure(IOException(errorMessageForCode(response.code())))
    }

    private suspend fun requestPlayer(tag: String): Response<JsonObject> {
        return runCatching { api.getPlayerGraphs(tag) }
            .getOrElse { throw IOException("Сервис недоступен") }
    }

    private suspend fun requestOfficialPlayer(tag: String): PlayerEntity? {
        val service = officialApi ?: return null
        val response = runCatching { service.getPlayer(tag) }.getOrNull() ?: return null
        if (!response.isSuccessful) return null

        val body = response.body() ?: return null
        return mapOfficialPlayer(tag, body)
    }

    private suspend fun refreshTrackedModePart() {
        val payload = requestEventsBody() ?: return
        val selectedMode = getTrackedMode()
        val (current, fromApi) = parseEventsByMode(payload, selectedMode)
        val next = fromApi ?: requestPredictedFromBrawlify(selectedMode)
        val modeIcons = requestGameModesBody()

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

    private suspend fun requestEventsBody(): JsonObject? {
        val response = runCatching { api.getEvents() }.getOrNull()
        if (response?.isSuccessful == true && response.body() != null) return response.body()
        return response?.body()
    }

    private suspend fun requestGameModesBody(): JsonObject? {
        val response = runCatching { api.getGameModes() }.getOrNull()
        if (response?.isSuccessful == true && response.body() != null) return response.body()
        return response?.body()
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
            }.getOrNull() ?: return@withContext null
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
            candidateTokens.any { token -> desiredTokens.contains(token) }
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

    private suspend fun resolveIconUrl(iconId: Int?): String? {
        if (iconId == null) return null

        val body = requestIconsBody()
        if (body != null) {
            val allIcons = body.getObj("player") ?: body.getObj("profiles") ?: body
            val entries = allIcons.getArr("regular")
                ?: allIcons.getArr("list")
                ?: allIcons.getArr("items")
                ?: allIcons.getArr("icons")
                ?: JsonArray()

            entries.forEach { raw ->
                val obj = raw.asObj() ?: return@forEach
                if (obj.getInt("id") == iconId) {
                    val url = obj.getStr("imageUrl2")
                        ?: obj.getStr("imageUrl")
                        ?: obj.getStr("url")
                    if (!url.isNullOrBlank()) {
                        return url
                    }
                }
            }
        }

        return "https://cdn.brawlify.com/profile-icons/regular/$iconId.png"
    }

    private suspend fun requestIconsBody(): JsonObject? {
        val response = runCatching { api.getIcons() }.getOrNull()
        if (response?.isSuccessful == true && response.body() != null) return response.body()
        return response?.body()
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

    private fun parseEventsByMode(payload: JsonObject, mode: TrackingMode): Pair<ModeEvent?, ModeEvent?> {
        val active = extractEventCandidates(payload, listOf("active", "current"))
        val predicted = extractEventCandidates(
            payload,
            listOf("upcoming", "next", "predicted", "Predicted", "predictions", "Predictions")
        )

        val current = active.firstForMode(mode)
        val next = predicted.firstForMode(mode)

        return current to next
    }

    private fun extractEventCandidates(payload: JsonObject, keys: List<String>): List<JsonObject> {
        return keys
            .mapNotNull { key -> payload.get(key) }
            .flatMap { raw -> raw.toEventArrays() }
            .flatMap { arr -> arr.mapNotNull { item -> item.asObj() } }
    }

    private fun JsonElement.toEventArrays(): List<JsonArray> {
        if (isJsonArray) return listOf(asJsonArray)
        if (!isJsonObject) return emptyList()

        val obj = asJsonObject
        return listOfNotNull(
            obj.getArr("events"),
            obj.getArr("list"),
            obj.getArr("items"),
            obj.getArr("active"),
            obj.getArr("upcoming"),
            obj.getArr("next")
        )
    }

    private fun List<JsonObject>.firstForMode(mode: TrackingMode): ModeEvent? {
        var fallback: ModeEvent? = null

        forEach { root ->
            val event = root.getObj("event") ?: root.getObj("battle") ?: root
            val slot = root.getInt("slot") ?: event.getInt("slot")

            val modeObj = event.getObj("mode") ?: root.getObj("mode")
            val modeName = modeObj?.getStr("name") ?: event.resolveModeName()
            val modeHash = modeObj?.getStr("hash") ?: event.getStr("modeHash")

            if (!mode.matches(modeName, modeHash, slot)) {
                return@forEach
            }

            val map = event.getObj("map") ?: root.getObj("map")
            val mapName = map?.getStr("name") ?: event.getStr("map")
            val candidate = ModeEvent(
                mapName = mapName ?: "Unknown",
                mapImage = map?.getStr("imageUrl2") ?: map?.getStr("imageUrl") ?: event.getStr("imageUrl"),
                modeId = modeObj?.getInt("id") ?: event.getInt("modeId"),
                modeName = modeName ?: mode.label,
                modeHash = modeHash
            )

            // Prefer entries with a real map name (some slots return mode-only rows with null map).
            if (!mapName.isNullOrBlank()) {
                return candidate
            }

            if (fallback == null) {
                fallback = candidate
            }
        }

        return fallback
    }

    private fun JsonObject.resolveModeName(): String? {
        return getObj("mode")?.getStr("name")
            ?: getObj("gameMode")?.getStr("name")
            ?: getObj("slot")?.getStr("name")
            ?: getObj("map")?.getObj("gameMode")?.getStr("name")
            ?: getStr("mode")
            ?: getStr("gameMode")
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


    private suspend fun buildTagOnlyPlayer(tag: String): PlayerEntity {
        val existing = playerDao.getByTag(tag)
        return PlayerEntity(
            tag = tag,
            name = existing?.name ?: "Player #$tag",
            trophies = existing?.trophies,
            highestTrophies = existing?.highestTrophies,
            expLevel = existing?.expLevel,
            clubTag = existing?.clubTag,
            clubName = existing?.clubName,
            profileIconId = existing?.profileIconId,
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

    private fun errorMessageForCode(code: Int): String = when (code) {
        404 -> "Профиль игрока недоступен в BrawlAPI v1 (endpoint /v1/graphs/player deprecated)"
        429 -> "Превышен лимит API"
        else -> "Сервис временно недоступен ($code)"
    }
}

data class ModeEvent(
    val mapName: String,
    val mapImage: String?,
    val modeId: Int?,
    val modeName: String,
    val modeHash: String?
)

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



