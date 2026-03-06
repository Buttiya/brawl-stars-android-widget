package com.example.brawlwidgetdemo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.brawlwidgetdemo.data.db.PlayerEntity
import java.text.DateFormat
import java.util.Date

@Composable
fun BrawlDemoScreen(
    state: PlayerUiState,
    onTagChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveForWidget: () -> Unit,
    onRefreshWidget: () -> Unit,
    onTabSelect: (Tab) -> Unit,
    onRefreshFavorites: () -> Unit,
    onSelectFavorite: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        WidgetCacheCard(state, onRefreshWidget)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onTabSelect(Tab.Search) }) {
                Text("Search")
            }
            Button(onClick = {
                onTabSelect(Tab.Favorites)
                if (state.favorites.isEmpty()) {
                    onRefreshFavorites()
                }
            }) {
                Text("Favorites")
            }
        }

        when (state.currentTab) {
            Tab.Search -> SearchTab(state, onTagChange, onSearchClick, onToggleFavorite, onSaveForWidget)
            Tab.Favorites -> FavoritesTab(state.favorites, onRefreshFavorites, onSelectFavorite)
        }
    }
}

@Composable
private fun WidgetCacheCard(
    state: PlayerUiState,
    onRefreshWidget: () -> Unit
) {
    val cache = state.widgetCache
    val timeText = cache?.soloNextMapStartAt?.let {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
    } ?: "TBD"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Widget cache")
            Text("Solo current: ${cache?.soloCurrentMapName ?: "-"}")
            Text("Solo next: ${cache?.soloNextMapName ?: "TBD"}")
            Text("Next start: $timeText")
            Text("Saved tag: ${cache?.savedPlayerTag?.let { "#$it" } ?: "Select player"}")
            Text("Saved trophies: ${cache?.savedPlayerTrophies?.toString() ?: "-"}")
            Text("Icon URL: ${cache?.savedPlayerIconUrl ?: "-"}")
            Button(onClick = onRefreshWidget) {
                Text("Refresh widget data")
            }
        }
    }
}

@Composable
private fun SearchTab(
    state: PlayerUiState,
    onTagChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveForWidget: () -> Unit
) {
    OutlinedTextField(
        value = state.inputTag,
        onValueChange = onTagChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Player tag (e.g. #ABC123)") },
        singleLine = true
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSearchClick) { Text("Find") }
        Button(onClick = onToggleFavorite, enabled = state.player != null) {
            Text(if (state.isFavorite) "Remove favorite" else "Add favorite")
        }
    }

    Button(onClick = onSaveForWidget, enabled = state.player != null) {
        Text("Save profile for widget")
    }

    if (state.isLoading) {
        CircularProgressIndicator()
    }

    if (state.error != null) {
        Text(text = state.error, color = MaterialTheme.colorScheme.error)
    }

    state.player?.let { player ->
        PlayerCard(player)
    }
}

@Composable
private fun FavoritesTab(
    favorites: List<PlayerEntity>,
    onRefreshFavorites: () -> Unit,
    onSelectFavorite: (String) -> Unit
) {
    Button(onClick = onRefreshFavorites) {
        Text("Refresh all")
    }

    if (favorites.isEmpty()) {
        Text("No favorites yet")
        return
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(favorites, key = { it.tag }) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectFavorite(item.tag) }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(text = item.name ?: "Unknown")
                    Text(text = "#${item.tag}")
                    Text(text = "Trophies: ${item.trophies ?: 0}")
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(player: PlayerEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = player.name ?: "Unknown")
            Text(text = "#${player.tag}")
            Text(text = "Trophies: ${player.trophies ?: 0}")
            Text(text = "Highest: ${player.highestTrophies ?: 0}")
            Text(text = "EXP: ${player.expLevel ?: 0}")
            Text(text = "Club: ${player.clubName ?: "-"}")
        }
    }
}
