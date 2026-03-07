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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.brawlwidgetdemo.data.db.PlayerEntity
import com.example.brawlwidgetdemo.data.repo.TrackingMode

@Composable
fun BrawlDemoScreen(
    state: PlayerUiState,
    onTagChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSaveForWidget: () -> Unit,
    onRefreshWidget: () -> Unit,
    onSelectTrackingMode: (TrackingMode) -> Unit,
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
        WidgetCacheCard(state, onRefreshWidget, onSelectTrackingMode)

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
    onRefreshWidget: () -> Unit,
    onSelectTrackingMode: (TrackingMode) -> Unit
) {
    val cache = state.widgetCache
    val modeChunks = run {
        val preferredOrder = listOf(
            TrackingMode.Showdown,
            TrackingMode.GemGrab,
            TrackingMode.Knockout,
            TrackingMode.Bounty,
            TrackingMode.HotZone,
            TrackingMode.BrawlBall
        )
        val available = state.availableTrackingModes
        val reordered = preferredOrder.filter { it in available } + available.filter { it !in preferredOrder }
        listOf(
            reordered.take(3),
            reordered.drop(3).take(3),
            reordered.drop(6)
        ).filter { it.isNotEmpty() }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Widget cache")
            Text("Tracked mode: ${cache?.trackedModeLabel ?: state.selectedTrackingMode.label}")

            modeChunks.forEach { rowModes ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowModes.forEach { mode ->
                        Button(onClick = { onSelectTrackingMode(mode) }) {
                            Text(mode.label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Text("Current: ${cache?.trackedCurrentModeName ?: state.selectedTrackingMode.label} | ${cache?.soloCurrentMapName ?: "-"}")
            Text("Next: ${cache?.trackedNextModeName ?: state.selectedTrackingMode.label} | ${cache?.soloNextMapName ?: "TBD"}")
            Text("Next icon URL: ${cache?.trackedNextModeIconUrl ?: "-"}")
            Text("Saved nick: ${cache?.savedPlayerName ?: "-"}")
            Text("Saved trophies: ${cache?.savedPlayerTrophies?.toString() ?: "-"}")
            Text("Saved EXP: ${cache?.savedPlayerExpLevel?.toString() ?: "-"}")
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
        label = { Text("Player tag (e.g. #PLVV2VY98)") },
        singleLine = true
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onSearchClick) { Text("Find") }
        Button(onClick = onToggleFavorite, enabled = state.player != null) {
            Text(if (state.isFavorite) "Remove favorite" else "Add favorite")
        }
    }

    Button(onClick = onSaveForWidget, enabled = state.inputTag.isNotBlank() || state.player != null) {
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
            Text(text = "Nick: ${player.name ?: "Unknown"}")
            Text(text = "Trophies: ${player.trophies ?: 0}")
            Text(text = "EXP: ${player.expLevel ?: 0}")
        }
    }
}
