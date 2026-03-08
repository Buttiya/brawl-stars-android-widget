package com.example.brawlwidgetdemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.brawlwidgetdemo.data.db.PlayerEntity
import com.example.brawlwidgetdemo.data.repo.TrackingMode

private enum class RootTab(
    val label: String
) {
    Home("Главная"),
    Clubs("Клубы"),
    Profile("Профиль")
}

private enum class AuthMenuMode {
    Selection,
    Login,
    Register
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRootScreen(
    homeState: PlayerUiState,
    profileState: ProfileUiState,
    onHomeTagChange: (String) -> Unit,
    onHomeSearchClick: () -> Unit,
    onHomeToggleFavorite: () -> Unit,
    onHomeSaveForWidget: () -> Unit,
    onHomeRefreshWidget: () -> Unit,
    onHomeSelectTrackingMode: (TrackingMode) -> Unit,
    onHomeTabSelect: (Tab) -> Unit,
    onHomeRefreshFavorites: () -> Unit,
    onHomeSelectFavorite: (String) -> Unit,
    onProfileUsernameChange: (String) -> Unit,
    onProfilePasswordChange: (String) -> Unit,
    onProfileTagChange: (String) -> Unit,
    onProfileRegister: () -> Unit,
    onProfileLogin: () -> Unit,
    onProfileLogout: () -> Unit,
    onProfileLinkTag: () -> Unit,
    onProfileRefreshPlayer: () -> Unit,
    onStartVerificationChallenge: () -> Unit,
    onDoneVerificationChallenge: () -> Unit,
    profileIconUrl: (Int?) -> String?,
    onOpenSettings: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(RootTab.Home) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Brawl Demo") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == RootTab.Home,
                    onClick = { selectedTab = RootTab.Home },
                    icon = { androidx.compose.material3.Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(RootTab.Home.label) }
                )
                NavigationBarItem(
                    selected = selectedTab == RootTab.Clubs,
                    onClick = { selectedTab = RootTab.Clubs },
                    icon = { androidx.compose.material3.Icon(Icons.Default.Groups, contentDescription = null) },
                    label = { Text(RootTab.Clubs.label) }
                )
                NavigationBarItem(
                    selected = selectedTab == RootTab.Profile,
                    onClick = { selectedTab = RootTab.Profile },
                    icon = { androidx.compose.material3.Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text(RootTab.Profile.label) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                RootTab.Home -> {
                    BrawlDemoScreen(
                        state = homeState,
                        onTagChange = onHomeTagChange,
                        onSearchClick = onHomeSearchClick,
                        onToggleFavorite = onHomeToggleFavorite,
                        onSaveForWidget = onHomeSaveForWidget,
                        onRefreshWidget = onHomeRefreshWidget,
                        onSelectTrackingMode = onHomeSelectTrackingMode,
                        onTabSelect = onHomeTabSelect,
                        onRefreshFavorites = onHomeRefreshFavorites,
                        onSelectFavorite = onHomeSelectFavorite
                    )
                }

                RootTab.Clubs -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Coming soon...")
                    }
                }

                RootTab.Profile -> {
                    ProfileScreen(
                        state = profileState,
                        onUsernameChange = onProfileUsernameChange,
                        onPasswordChange = onProfilePasswordChange,
                        onTagChange = onProfileTagChange,
                        onRegister = onProfileRegister,
                        onLogin = onProfileLogin,
                        onLogout = onProfileLogout,
                        onLinkTag = onProfileLinkTag,
                        onRefreshPlayer = onProfileRefreshPlayer,
                        onStartVerificationChallenge = onStartVerificationChallenge,
                        onDoneVerificationChallenge = onDoneVerificationChallenge,
                        profileIconUrl = profileIconUrl
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    state: ProfileUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTagChange: (String) -> Unit,
    onRegister: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onLinkTag: () -> Unit,
    onRefreshPlayer: () -> Unit,
    onStartVerificationChallenge: () -> Unit,
    onDoneVerificationChallenge: () -> Unit,
    profileIconUrl: (Int?) -> String?
) {
    val account = state.account
    var authMenuMode by rememberSaveable { mutableStateOf(AuthMenuMode.Selection) }

    LaunchedEffect(account?.isLoggedIn) {
        if (account?.isLoggedIn == true) {
            authMenuMode = AuthMenuMode.Selection
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Профиль", style = MaterialTheme.typography.headlineSmall)

        when {
            account == null -> {
                Text("Войди или создай аккаунт")
                when (authMenuMode) {
                    AuthMenuMode.Selection -> {
                        Button(onClick = { authMenuMode = AuthMenuMode.Login }, enabled = !state.isLoading) {
                            Text("Войти")
                        }
                        Button(onClick = { authMenuMode = AuthMenuMode.Register }, enabled = !state.isLoading) {
                            Text("Зарегистрироваться")
                        }
                    }

                    AuthMenuMode.Login -> {
                        CredentialsFields(
                            username = state.usernameInput,
                            password = state.passwordInput,
                            onUsernameChange = onUsernameChange,
                            onPasswordChange = onPasswordChange
                        )
                        Button(onClick = onLogin, enabled = !state.isLoading) {
                            Text("Войти")
                        }
                        Button(onClick = { authMenuMode = AuthMenuMode.Selection }, enabled = !state.isLoading) {
                            Text("Назад")
                        }
                    }

                    AuthMenuMode.Register -> {
                        CredentialsFields(
                            username = state.usernameInput,
                            password = state.passwordInput,
                            onUsernameChange = onUsernameChange,
                            onPasswordChange = onPasswordChange
                        )
                        Button(onClick = onRegister, enabled = !state.isLoading) {
                            Text("Зарегистрироваться")
                        }
                        Button(onClick = { authMenuMode = AuthMenuMode.Selection }, enabled = !state.isLoading) {
                            Text("Назад")
                        }
                    }
                }
            }

            !account.isLoggedIn -> {
                Text("Аккаунт: ${account.username}")
                when (authMenuMode) {
                    AuthMenuMode.Selection -> {
                        Text("Выбери действие")
                        Button(onClick = { authMenuMode = AuthMenuMode.Login }, enabled = !state.isLoading) {
                            Text("Войти")
                        }
                        Button(onClick = { authMenuMode = AuthMenuMode.Register }, enabled = !state.isLoading) {
                            Text("Зарегистрироваться")
                        }
                    }

                    AuthMenuMode.Login -> {
                        Text("Войди в аккаунт")
                        CredentialsFields(
                            username = state.usernameInput,
                            password = state.passwordInput,
                            onUsernameChange = onUsernameChange,
                            onPasswordChange = onPasswordChange
                        )
                        Button(onClick = onLogin, enabled = !state.isLoading) {
                            Text("Войти")
                        }
                        Button(onClick = { authMenuMode = AuthMenuMode.Selection }, enabled = !state.isLoading) {
                            Text("Назад")
                        }
                    }

                    AuthMenuMode.Register -> {
                        Text("Или создай другой аккаунт")
                        CredentialsFields(
                            username = state.usernameInput,
                            password = state.passwordInput,
                            onUsernameChange = onUsernameChange,
                            onPasswordChange = onPasswordChange
                        )
                        Button(onClick = onRegister, enabled = !state.isLoading) {
                            Text("Зарегистрироваться")
                        }
                        Button(onClick = { authMenuMode = AuthMenuMode.Selection }, enabled = !state.isLoading) {
                            Text("Назад")
                        }
                    }
                }
            }

            else -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Логин: ${account.username}")
                        Text("Подтвержденный профиль: ${if (account.isVerified) "Да" else "Нет"}")
                        Button(onClick = onLogout, enabled = !state.isLoading) {
                            Text("Выйти")
                        }
                    }
                }

                OutlinedTextField(
                    value = state.tagInput,
                    onValueChange = onTagChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Тег профиля (например #PLVV2VY98)") },
                    singleLine = true
                )

                Button(onClick = onLinkTag, enabled = !state.isLoading) {
                    Text("Привязать профиль")
                }

                Button(onClick = onRefreshPlayer, enabled = !state.isLoading && !account.linkedPlayerTag.isNullOrBlank()) {
                    Text("Обновить данные")
                }

                if (!account.isVerified) {
                    VerificationChallengeCard(
                        state = state,
                        onStartVerificationChallenge = onStartVerificationChallenge,
                        onDoneVerificationChallenge = onDoneVerificationChallenge
                    )
                }

                ProfilePlayerCard(
                    player = state.player,
                    isVerified = account.isVerified,
                    profileIconUrl = profileIconUrl
                )
            }
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CredentialsFields(
    username: String,
    password: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Логин") },
        singleLine = true
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Пароль") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation()
    )
}

@Composable
private fun VerificationChallengeCard(
    state: ProfileUiState,
    onStartVerificationChallenge: () -> Unit,
    onDoneVerificationChallenge: () -> Unit
) {
    val challenge = state.challenge
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Подтверждение профиля")
            if (challenge == null) {
                Text("Запроси иконку и смени её в игре за 5 минут.")
                Button(onClick = onStartVerificationChallenge, enabled = !state.isLoading && state.player != null) {
                    Text("Запросить иконку")
                }
                return@Column
            }

            Text("Поставь эту иконку: ${challenge.brawlerName} (${challenge.rarity})")
            AsyncImage(
                model = challenge.expectedIconUrl,
                contentDescription = "Challenge icon",
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Text("Осталось: ${state.challengeSecondsLeft} сек")
            Button(onClick = onDoneVerificationChallenge, enabled = !state.isLoading) {
                Text("Done.")
            }
            Button(onClick = onStartVerificationChallenge, enabled = !state.isLoading) {
                Text("Новая иконка")
            }
        }
    }
}

@Composable
private fun ProfilePlayerCard(
    player: PlayerEntity?,
    isVerified: Boolean,
    profileIconUrl: (Int?) -> String?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (player == null) {
                Text("Пока нет привязанного игрового профиля")
                return@Column
            }

            AsyncImage(
                model = profileIconUrl(player.profileIconId),
                contentDescription = "Profile icon",
                modifier = Modifier
                    .size(160.dp)
                    .align(Alignment.CenterHorizontally)
            )

            val showdownWins = (player.soloVictories ?: 0) + (player.duoVictories ?: 0)

            Text("Ник: ${player.name ?: "Unknown"}")
            Text("Кубки: ${player.trophies ?: 0}")
            Text("EXP: ${player.expLevel ?: 0}")
            Text("Победы 3v3: ${player.victories3v3 ?: 0}")
            Text("Победы Showdown: $showdownWins")
            Text(
                text = if (isVerified) "Профиль подтверждён" else "Профиль не был подтверждён",
                color = if (isVerified) Color(0xFF1B8A3A) else Color(0xFFB3261E)
            )
        }
    }
}
