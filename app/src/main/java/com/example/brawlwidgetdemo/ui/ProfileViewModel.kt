package com.example.brawlwidgetdemo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.brawlwidgetdemo.data.db.PlayerEntity
import com.example.brawlwidgetdemo.data.db.UserAccountEntity
import com.example.brawlwidgetdemo.data.repo.AuthRepository
import com.example.brawlwidgetdemo.data.repo.PlayerRepository
import com.example.brawlwidgetdemo.domain.isTagValid
import com.example.brawlwidgetdemo.domain.normalizeTag
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class VerificationChallenge(
    val expectedIconId: Int,
    val expectedIconUrl: String,
    val brawlerName: String,
    val rarity: String,
    val expiresAtMillis: Long
)

data class ProfileUiState(
    val account: UserAccountEntity? = null,
    val player: PlayerEntity? = null,
    val usernameInput: String = "",
    val passwordInput: String = "",
    val tagInput: String = "",
    val challenge: VerificationChallenge? = null,
    val challengeSecondsLeft: Long = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

private data class ProfileCoreState(
    val account: UserAccountEntity?,
    val player: PlayerEntity?,
    val usernameInput: String,
    val passwordInput: String,
    val tagInput: String
)

private data class ProfileDynamicState(
    val challenge: VerificationChallenge?,
    val challengeSecondsLeft: Long,
    val isLoading: Boolean
)

private data class ChallengeIcon(
    val iconId: Int,
    val brawlerId: Int,
    val brawlerName: String,
    val rarity: String,
    val imageUrl: String
)

private val RARE_AND_SUPER_RARE_ICON_POOL = listOf(
    ChallengeIcon(28000004, 16000001, "Colt", "Rare", "https://cdn.brawlify.com/brawlers/borders/16000001.png"),
    ChallengeIcon(28000005, 16000003, "Brock", "Rare", "https://cdn.brawlify.com/brawlers/borders/16000003.png"),
    ChallengeIcon(28000006, 16000007, "Jessie", "Super Rare", "https://cdn.brawlify.com/brawlers/borders/16000007.png"),
    ChallengeIcon(28000007, 16000008, "Nita", "Rare", "https://cdn.brawlify.com/brawlers/borders/16000008.png"),
    ChallengeIcon(28000008, 16000009, "Dynamike", "Super Rare", "https://cdn.brawlify.com/brawlers/borders/16000009.png"),
    ChallengeIcon(28000009, 16000010, "El Primo", "Rare", "https://cdn.brawlify.com/brawlers/borders/16000010.png"),
    ChallengeIcon(28000010, 16000002, "Bull", "Rare", "https://cdn.brawlify.com/brawlers/borders/16000002.png"),
    ChallengeIcon(28000011, 16000004, "Rico", "Super Rare", "https://cdn.brawlify.com/brawlers/borders/16000004.png"),
    ChallengeIcon(28000012, 16000006, "Barley", "Rare", "https://cdn.brawlify.com/brawlers/borders/16000006.png"),
    ChallengeIcon(28000013, 16000013, "Poco", "Rare", "https://cdn.brawlify.com/brawlers/borders/16000013.png"),
    ChallengeIcon(28000034, 16000018, "Darryl", "Super Rare", "https://cdn.brawlify.com/brawlers/borders/16000018.png"),
    ChallengeIcon(28000035, 16000019, "Penny", "Super Rare", "https://cdn.brawlify.com/brawlers/borders/16000019.png"),
    ChallengeIcon(28000039, 16000025, "Carl", "Super Rare", "https://cdn.brawlify.com/brawlers/borders/16000025.png"),
    ChallengeIcon(28000040, 16000024, "Rosa", "Rare", "https://cdn.brawlify.com/brawlers/borders/16000024.png"),
    ChallengeIcon(28000042, 16000022, "Tick", "Super Rare", "https://cdn.brawlify.com/brawlers/borders/16000022.png"),
    ChallengeIcon(28000043, 16000027, "8-BIT", "Super Rare", "https://cdn.brawlify.com/brawlers/borders/16000027.png"),
    ChallengeIcon(28000049, 16000034, "Jacky", "Super Rare", "https://cdn.brawlify.com/brawlers/borders/16000034.png"),
    ChallengeIcon(28000150, 16000061, "Gus", "Super Rare", "https://cdn.brawlify.com/brawlers/borders/16000061.png")
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val playerRepository: PlayerRepository
) : ViewModel() {
    private val usernameInput = MutableStateFlow("")
    private val passwordInput = MutableStateFlow("")
    private val tagInput = MutableStateFlow("")
    private val challenge = MutableStateFlow<VerificationChallenge?>(null)
    private val nowMillis = MutableStateFlow(System.currentTimeMillis())
    private val isLoading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        isLoading.value = false
        error.value = throwable.message ?: "Ошибка"
    }

    private val accountFlow = authRepository.observeAccount()

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                nowMillis.value = System.currentTimeMillis()
                val current = challenge.value ?: continue
                if (current.expiresAtMillis <= nowMillis.value) {
                    challenge.value = null
                    error.value = "Время вышло. Запроси новую иконку для подтверждения."
                }
            }
        }
    }

    private val coreState = combine(
        accountFlow,
        accountFlow.flatMapLatest { account ->
            val linkedTag = account?.linkedPlayerTag
            if (linkedTag.isNullOrBlank()) {
                flowOf(null)
            } else {
                playerRepository.observePlayer(linkedTag)
            }
        },
        usernameInput,
        passwordInput,
        tagInput
    ) { account, player, username, password, tag ->
        ProfileCoreState(
            account = account,
            player = player,
            usernameInput = username,
            passwordInput = password,
            tagInput = if (tag.isBlank()) account?.linkedPlayerTag ?: "" else tag
        )
    }

    private val dynamicState = combine(
        challenge,
        nowMillis,
        isLoading
    ) { challengeState, now, loading ->
        ProfileDynamicState(
            challenge = challengeState,
            challengeSecondsLeft = challengeState?.let { maxOf(0, (it.expiresAtMillis - now) / 1000) } ?: 0L,
            isLoading = loading
        )
    }

    val uiState: StateFlow<ProfileUiState> = combine(
        coreState,
        dynamicState,
        error,
        message
    ) { core, dynamic, err, msg ->
        ProfileUiState(
            account = core.account,
            player = core.player,
            usernameInput = core.usernameInput,
            passwordInput = core.passwordInput,
            tagInput = core.tagInput,
            challenge = dynamic.challenge,
            challengeSecondsLeft = dynamic.challengeSecondsLeft,
            isLoading = dynamic.isLoading,
            error = err,
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfileUiState())

    fun onUsernameChange(value: String) {
        usernameInput.value = value
    }

    fun onPasswordChange(value: String) {
        passwordInput.value = value
    }

    fun onTagChange(value: String) {
        tagInput.value = value
    }

    fun register() {
        viewModelScope.launch(exceptionHandler) {
            isLoading.value = true
            clearMessages()
            val result = authRepository.register(usernameInput.value, passwordInput.value)
            result.onSuccess {
                message.value = "Аккаунт создан"
                passwordInput.value = ""
            }.onFailure {
                error.value = it.message ?: "Не удалось создать аккаунт"
            }
            isLoading.value = false
        }
    }

    fun login() {
        viewModelScope.launch(exceptionHandler) {
            isLoading.value = true
            clearMessages()
            val result = authRepository.login(usernameInput.value, passwordInput.value)
            result.onSuccess {
                message.value = "Вы вошли"
                passwordInput.value = ""
            }.onFailure {
                error.value = it.message ?: "Не удалось войти"
            }
            isLoading.value = false
        }
    }

    fun logout() {
        viewModelScope.launch(exceptionHandler) {
            authRepository.logout()
            challenge.value = null
            clearMessages()
            message.value = "Вы вышли"
        }
    }

    fun linkTag() {
        val input = tagInput.value
        val normalized = normalizeTag(input)
        if (!isTagValid(normalized)) {
            error.value = "Невалидный тег"
            return
        }

        viewModelScope.launch(exceptionHandler) {
            isLoading.value = true
            clearMessages()
            val result = playerRepository.searchPlayer(normalized)
            result.onSuccess { normalizedTag ->
                authRepository.linkPlayerTag(normalizedTag)
                authRepository.setVerified(false)
                challenge.value = null
                message.value = "Профиль привязан"
            }.onFailure {
                error.value = it.message ?: "Не удалось привязать профиль"
            }
            isLoading.value = false
        }
    }

    fun refreshLinkedProfile() {
        val tag = uiState.value.account?.linkedPlayerTag ?: return
        viewModelScope.launch(exceptionHandler) {
            isLoading.value = true
            clearMessages()
            val result = playerRepository.searchPlayer(tag)
            result.onFailure {
                error.value = it.message ?: "Не удалось обновить профиль"
            }
            isLoading.value = false
        }
    }

    fun startVerificationChallenge() {
        val account = uiState.value.account
        if (account == null || !account.isLoggedIn) {
            error.value = "Сначала войди в аккаунт"
            return
        }

        val tag = account.linkedPlayerTag
        if (tag.isNullOrBlank()) {
            error.value = "Сначала привяжи профиль по тегу"
            return
        }

        viewModelScope.launch(exceptionHandler) {
            isLoading.value = true
            clearMessages()

            val refreshResult = playerRepository.searchPlayer(tag)
            if (refreshResult.isFailure) {
                error.value = refreshResult.exceptionOrNull()?.message ?: "Не удалось загрузить профиль"
                isLoading.value = false
                return@launch
            }

            val latestPlayer = playerRepository.getPlayerByTag(tag)
            val currentIconId = latestPlayer?.profileIconId

            val ownedBrawlersResult = playerRepository.getOwnedBrawlerIds(tag)
            val ownedBrawlerIds = ownedBrawlersResult.getOrElse {
                error.value = it.message ?: "Не удалось получить список бойцов"
                isLoading.value = false
                return@launch
            }

            val available = RARE_AND_SUPER_RARE_ICON_POOL.filter { icon ->
                icon.brawlerId in ownedBrawlerIds && icon.iconId != currentIconId
            }

            if (available.isEmpty()) {
                error.value = "Нет доступной редкой/сверхредкой иконки, отличной от текущей"
                isLoading.value = false
                return@launch
            }

            val target = available[Random.nextInt(available.size)]
            challenge.value = VerificationChallenge(
                expectedIconId = target.iconId,
                expectedIconUrl = target.imageUrl,
                brawlerName = target.brawlerName,
                rarity = target.rarity,
                expiresAtMillis = System.currentTimeMillis() + CHALLENGE_TTL_MS
            )
            authRepository.setVerified(false)
            message.value = "Поменяй иконку на выбранную и нажми Done."
            isLoading.value = false
        }
    }

    fun completeVerificationChallenge() {
        val account = uiState.value.account
        val activeChallenge = challenge.value

        if (account == null || !account.isLoggedIn) {
            error.value = "Сначала войди в аккаунт"
            return
        }
        if (account.linkedPlayerTag.isNullOrBlank()) {
            error.value = "Сначала привяжи профиль по тегу"
            return
        }
        if (activeChallenge == null) {
            error.value = "Нет активного задания. Запроси новую иконку."
            return
        }
        if (System.currentTimeMillis() > activeChallenge.expiresAtMillis) {
            challenge.value = null
            error.value = "Время вышло. Запроси новую иконку."
            return
        }

        val tag = account.linkedPlayerTag

        viewModelScope.launch(exceptionHandler) {
            isLoading.value = true
            clearMessages()

            val refreshResult = playerRepository.searchPlayer(tag)
            if (refreshResult.isFailure) {
                error.value = refreshResult.exceptionOrNull()?.message ?: "Не удалось проверить профиль"
                isLoading.value = false
                return@launch
            }

            val latestPlayer = playerRepository.getPlayerByTag(tag)
            val matched = latestPlayer?.profileIconId == activeChallenge.expectedIconId

            if (matched) {
                authRepository.setVerified(true)
                challenge.value = null
                message.value = "Профиль подтвержден"
            } else {
                authRepository.setVerified(false)
                error.value = "Иконка пока не совпадает с выбранной. Поставь нужную и нажми Done."
            }
            isLoading.value = false
        }
    }

    private fun clearMessages() {
        error.value = null
        message.value = null
    }

    companion object {
        private const val CHALLENGE_TTL_MS = 5 * 60 * 1000L
    }
}

class ProfileViewModelFactory(
    private val authRepository: AuthRepository,
    private val playerRepository: PlayerRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(authRepository, playerRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
