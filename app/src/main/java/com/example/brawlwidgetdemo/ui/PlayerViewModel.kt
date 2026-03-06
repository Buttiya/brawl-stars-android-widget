package com.example.brawlwidgetdemo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.brawlwidgetdemo.data.db.PlayerEntity
import com.example.brawlwidgetdemo.data.db.WidgetCacheEntity
import com.example.brawlwidgetdemo.data.repo.PlayerRepository
import com.example.brawlwidgetdemo.domain.normalizeTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerUiState(
    val inputTag: String = "",
    val selectedTag: String? = null,
    val player: PlayerEntity? = null,
    val favorites: List<PlayerEntity> = emptyList(),
    val isFavorite: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentTab: Tab = Tab.Search,
    val widgetCache: WidgetCacheEntity? = null
)

enum class Tab {
    Search,
    Favorites
}

class PlayerViewModel(
    private val repository: PlayerRepository
) : ViewModel() {
    private val inputTag = MutableStateFlow("")
    private val selectedTag = MutableStateFlow<String?>(null)
    private val isLoading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val currentTab = MutableStateFlow(Tab.Search)

    val uiState: StateFlow<PlayerUiState> = combine(
        inputTag,
        selectedTag,
        selectedTag.flatMapLatest { tag -> if (tag == null) flowOf(null) else repository.observePlayer(tag) },
        repository.observeFavoritePlayers(),
        repository.observeWidgetCache(),
        isLoading,
        error,
        currentTab
    ) { input, selected, player, favorites, widgetCache, loading, err, tab ->
        val normalized = selected?.let(::normalizeTag)
        val favorite = normalized != null && favorites.any { it.tag == normalized }

        PlayerUiState(
            inputTag = input,
            selectedTag = selected,
            player = player,
            favorites = favorites,
            isFavorite = favorite,
            isLoading = loading,
            error = err,
            currentTab = tab,
            widgetCache = widgetCache
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerUiState())

    fun onTagChange(value: String) {
        inputTag.value = value
    }

    fun setTab(tab: Tab) {
        currentTab.value = tab
    }

    fun search() {
        val query = inputTag.value
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            val result = repository.searchPlayer(query)
            result.onSuccess { tag ->
                selectedTag.value = tag
            }.onFailure {
                error.value = it.message ?: "Ошибка"
            }
            isLoading.value = false
        }
    }

    fun toggleFavorite() {
        val tag = selectedTag.value ?: return
        viewModelScope.launch {
            repository.toggleFavorite(tag)
        }
    }

    fun saveSelectedProfileForWidget() {
        val tag = selectedTag.value ?: return
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            runCatching { repository.saveProfileForWidget(tag) }
                .onFailure { error.value = it.message ?: "Не удалось сохранить профиль для виджета" }
            isLoading.value = false
        }
    }

    fun loadFavorite(tag: String) {
        selectedTag.value = normalizeTag(tag)
        inputTag.value = tag
        currentTab.value = Tab.Search
    }

    fun refreshFavorites() {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            runCatching { repository.refreshFavorites() }
                .onFailure { error.value = it.message ?: "Ошибка обновления" }
            isLoading.value = false
        }
    }

    fun refreshWidget() {
        viewModelScope.launch {
            isLoading.value = true
            error.value = null
            runCatching { repository.refreshWidgetData() }
                .onFailure { error.value = it.message ?: "Ошибка обновления виджета" }
            isLoading.value = false
        }
    }
}

class PlayerViewModelFactory(
    private val repository: PlayerRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlayerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
