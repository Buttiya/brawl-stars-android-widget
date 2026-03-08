package com.example.brawlwidgetdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.brawlwidgetdemo.ui.AppRootScreen
import com.example.brawlwidgetdemo.ui.AppTheme
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
            var showApiDialog by rememberSaveable { mutableStateOf(app.getSavedApiBaseUrl().isBlank()) }
            var showThemeDialog by rememberSaveable { mutableStateOf(false) }
            var apiUrlInput by rememberSaveable { mutableStateOf(app.getSavedApiBaseUrl()) }
            var isDarkTheme by rememberSaveable { mutableStateOf(app.isDarkThemeEnabled()) }
            var repositoriesVersion by remember { mutableStateOf(0) }

            LaunchedEffect(Unit) {
                apiUrlInput = app.getSavedApiBaseUrl()
            }

            AppTheme(darkTheme = isDarkTheme) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    if (showApiDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                if (app.getSavedApiBaseUrl().isNotBlank()) {
                                    showApiDialog = false
                                }
                            },
                            title = { Text("Адрес API") },
                            text = {
                                OutlinedTextField(
                                    value = apiUrlInput,
                                    onValueChange = { apiUrlInput = it },
                                    label = { Text("Например http://127.0.0.1:8787/") },
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                Button(
                                    enabled = apiUrlInput.isNotBlank(),
                                    onClick = {
                                        app.saveAndApplyApiBaseUrl(apiUrlInput)
                                        repositoriesVersion += 1
                                        showApiDialog = false
                                    }
                                ) {
                                    Text("Сохранить")
                                }
                            }
                        )
                    }

                    if (showThemeDialog) {
                        AlertDialog(
                            onDismissRequest = { showThemeDialog = false },
                            title = { Text("Тема") },
                            text = {
                                Text(if (isDarkTheme) "Сейчас выбрана тёмная тема." else "Сейчас выбрана светлая тема.")
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        isDarkTheme = false
                                        app.setDarkThemeEnabled(false)
                                        showThemeDialog = false
                                    }
                                ) {
                                    Text("Светлая")
                                }
                            },
                            dismissButton = {
                                Button(
                                    onClick = {
                                        isDarkTheme = true
                                        app.setDarkThemeEnabled(true)
                                        showThemeDialog = false
                                    }
                                ) {
                                    Text("Тёмная")
                                }
                            }
                        )
                    }

                    if (showApiDialog) {
                        return@CompositionLocalProvider
                    }

                    val homeVm: PlayerViewModel = viewModel(
                        key = "home-$repositoriesVersion",
                        factory = PlayerViewModelFactory(app.playerRepository)
                    )
                    val profileVm: ProfileViewModel = viewModel(
                        key = "profile-$repositoriesVersion",
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
                        profileIconUrl = app.playerRepository::profileIconUrl,
                        onOpenThemeSettings = {
                            showThemeDialog = true
                        },
                        onOpenApiSettings = {
                            apiUrlInput = app.getSavedApiBaseUrl()
                            showApiDialog = true
                        }
                    )
                }
            }
        }
    }

    private fun enqueueWidgetRefresh() {
        val request = OneTimeWorkRequestBuilder<RefreshWidgetWorker>().build()
        WorkManager.getInstance(this).enqueue(request)
    }
}
