package com.example.brawlwidgetdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.brawlwidgetdemo.ui.BrawlDemoScreen
import com.example.brawlwidgetdemo.ui.PlayerViewModel
import com.example.brawlwidgetdemo.ui.PlayerViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = LocalContext.current.applicationContext as BrawlDemoApp
            val vm: PlayerViewModel = viewModel(factory = PlayerViewModelFactory(app.playerRepository))
            val uiState by vm.uiState.collectAsStateWithLifecycle()

            BrawlDemoScreen(
                state = uiState,
                onTagChange = vm::onTagChange,
                onSearchClick = vm::search,
                onToggleFavorite = vm::toggleFavorite,
                onSaveForWidget = vm::saveSelectedProfileForWidget,
                onRefreshWidget = vm::refreshWidget,
                onTabSelect = vm::setTab,
                onRefreshFavorites = vm::refreshFavorites,
                onSelectFavorite = vm::loadFavorite
            )
        }
    }
}
