package com.example.brawlwidgetdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.brawlwidgetdemo.ui.AppRootScreen
import com.example.brawlwidgetdemo.ui.PlayerViewModel
import com.example.brawlwidgetdemo.ui.PlayerViewModelFactory
import com.example.brawlwidgetdemo.ui.ProfileViewModel
import com.example.brawlwidgetdemo.ui.ProfileViewModelFactory
import com.example.brawlwidgetdemo.widget.RefreshWidgetWorker

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = LocalContext.current.applicationContext as BrawlDemoApp

            val homeVm: PlayerViewModel = viewModel(
                factory = PlayerViewModelFactory(app.playerRepository)
            )
            val profileVm: ProfileViewModel = viewModel(
                factory = ProfileViewModelFactory(app.authRepository, app.playerRepository)
            )

            val homeState by homeVm.uiState.collectAsStateWithLifecycle()
            val profileState by profileVm.uiState.collectAsStateWithLifecycle()

            AppRootScreen(
                homeState = homeState,
                profileState = profileState,
                onHomeTagChange = homeVm::onTagChange,
                onHomeSearchClick = homeVm::search,
                onHomeToggleFavorite = homeVm::toggleFavorite,
                onHomeSaveForWidget = {
                    homeVm.saveSelectedProfileForWidget()
                    enqueueWidgetRefresh()
                },
                onHomeRefreshWidget = {
                    enqueueWidgetRefresh()
                },
                onHomeSelectTrackingMode = {
                    homeVm.selectTrackingMode(it)
                    enqueueWidgetRefresh()
                },
                onHomeTabSelect = homeVm::setTab,
                onHomeRefreshFavorites = homeVm::refreshFavorites,
                onHomeSelectFavorite = homeVm::loadFavorite,
                onProfileUsernameChange = profileVm::onUsernameChange,
                onProfilePasswordChange = profileVm::onPasswordChange,
                onProfileTagChange = profileVm::onTagChange,
                onProfileRegister = profileVm::register,
                onProfileLogin = profileVm::login,
                onProfileLogout = profileVm::logout,
                onProfileLinkTag = profileVm::linkTag,
                onProfileRefreshPlayer = profileVm::refreshLinkedProfile,
                onStartVerificationChallenge = profileVm::startVerificationChallenge,
                onDoneVerificationChallenge = profileVm::completeVerificationChallenge,
                profileIconUrl = app.playerRepository::profileIconUrl
            )
        }
    }

    private fun enqueueWidgetRefresh() {
        val request = OneTimeWorkRequestBuilder<RefreshWidgetWorker>().build()
        WorkManager.getInstance(this).enqueue(request)
    }
}
