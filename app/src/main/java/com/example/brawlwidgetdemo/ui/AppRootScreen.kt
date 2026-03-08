package com.example.brawlwidgetdemo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
    onOpenThemeSettings: () -> Unit,
    onOpenServerSettings: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(RootTab.Home) }
    var settingsMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Brawl Demo") },
                actions = {
                    Box {
                        IconButton(onClick = { settingsMenuExpanded = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Настройки")
                        }
                        DropdownMenu(
                            expanded = settingsMenuExpanded,
                            onDismissRequest = { settingsMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Сменить тему") },
                                onClick = {
                                    settingsMenuExpanded = false
                                    onOpenThemeSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Сменить сервер") },
                                onClick = {
                                    settingsMenuExpanded = false
                                    onOpenServerSettings()
                                }
                            )
                        }
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

                TrophyHistoryCard(state = state)
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

@Composable
private fun TrophyHistoryCard(state: ProfileUiState) {
    val points = state.trophyHistory
    if (state.player == null || points.isEmpty()) return

    val startLabel = points.first().label
    val endLabel = points.last().label
    val lastDelta = points.last().delta
    val deltaColor = if (lastDelta >= 0) Color(0xFF1B8A3A) else Color(0xFFB3261E)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("История трофеев", style = MaterialTheme.typography.titleMedium)
            Text("Интервал: $startLabel - $endLabel")
            Text("Последний дневной прирост: ${formatSignedValue(lastDelta)}", color = deltaColor)

            if (points.size < 2) {
                Text("График появится, когда накопится хотя бы 2 дня данных.")
                return@Column
            }

            TrophyHistoryChart(
                points = points,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = startLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = endLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun TrophyHistoryChart(
    points: List<DailyTrophyPointUi>,
    modifier: Modifier = Modifier
) {
    val minValue = points.minOf { it.trophies }
    val maxValue = points.maxOf { it.trophies }
    val valueRange = (maxValue - minValue).coerceAtLeast(1)
    val lineColor = MaterialTheme.colorScheme.primary
    val fillColor = lineColor.copy(alpha = 0.12f)
    val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Canvas(modifier = modifier) {
        val horizontalPadding = 24.dp.toPx()
        val verticalPadding = 20.dp.toPx()
        val chartWidth = size.width - horizontalPadding * 2
        val chartHeight = size.height - verticalPadding * 2
        if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

        val stepX = if (points.size > 1) chartWidth / points.lastIndex else 0f
        val offsets = points.mapIndexed { index, point ->
            val normalizedY = (point.trophies - minValue).toFloat() / valueRange.toFloat()
            Offset(
                x = horizontalPadding + stepX * index,
                y = size.height - verticalPadding - normalizedY * chartHeight
            )
        }

        val strokePath = buildSmoothPath(offsets)
        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(offsets.last().x, size.height - verticalPadding)
            lineTo(offsets.first().x, size.height - verticalPadding)
            close()
        }

        drawLine(
            color = axisColor,
            start = Offset(horizontalPadding, size.height - verticalPadding),
            end = Offset(size.width - horizontalPadding, size.height - verticalPadding),
            strokeWidth = 1.dp.toPx()
        )
        drawPath(path = fillPath, color = fillColor)
        drawPath(
            path = strokePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        offsets.forEach { point ->
            drawCircle(color = lineColor, radius = 4.dp.toPx(), center = point)
        }
    }
}

private fun buildSmoothPath(points: List<Offset>): Path {
    return Path().apply {
        if (points.isEmpty()) return@apply
        moveTo(points.first().x, points.first().y)
        if (points.size == 1) return@apply

        for (index in 0 until points.lastIndex) {
            val current = points[index]
            val next = points[index + 1]
            val previous = points.getOrElse(index - 1) { current }
            val afterNext = points.getOrElse(index + 2) { next }

            val control1 = Offset(
                x = current.x + (next.x - previous.x) / 6f,
                y = current.y + (next.y - previous.y) / 6f
            )
            val control2 = Offset(
                x = next.x - (afterNext.x - current.x) / 6f,
                y = next.y - (afterNext.y - current.y) / 6f
            )

            cubicTo(
                control1.x,
                control1.y,
                control2.x,
                control2.y,
                next.x,
                next.y
            )
        }
    }
}

private fun formatSignedValue(value: Int): String {
    return if (value > 0) "+$value" else value.toString()
}
