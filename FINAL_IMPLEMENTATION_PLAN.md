# Brawl Stars Android Widget App: Final Implementation Plan (v1)

## 1. Product Goal

Собрать Android-приложение с виджетом, которое позволяет:
1. Искать игроков Brawl Stars по тегу через официальный API.
2. Хранить и агрегировать данные игроков в локальной БД для быстрого доступа и офлайн-режима.
3. Создавать свои посты-новости внутри приложения.

Результат v1: стабильный поиск, кэш/история данных, рабочий app widget, полноценный локальный CRUD новостей.

## 2. Scope and v1 Boundaries

Включено в v1:
1. Поиск и просмотр профиля игрока по тегу.
2. Избранные игроки и их фоновое обновление.
3. История снапшотов статистики игрока.
4. App Widget для быстрого просмотра выбранного игрока и перехода в приложение.
5. Локальный модуль новостей (создание/редактирование/удаление/просмотр/фильтр).

Не включено в v1:
1. Серверная авторизация пользователей.
2. Публикация новостей во внешний backend.
3. Реалтайм-сокеты.
4. Мультиязычность (только RU/EN по дефолтным ресурсам, без полноценных локализаций).

## 3. Tech Stack and Architecture

1. Язык и UI:
   - Kotlin
   - Jetpack Compose
2. Архитектура:
   - MVVM
   - UseCase + Repository pattern
   - Unidirectional UI state (StateFlow)
3. Сеть:
   - Retrofit + OkHttp
   - Kotlinx Serialization
4. БД:
   - Room
5. DI:
   - Hilt
6. Async:
   - Coroutines + Flow
7. Фоновые задачи:
   - WorkManager
8. Виджет:
   - Glance AppWidget (если ограничение устройства, fallback на RemoteViews)
9. Логирование/краши:
   - Timber
   - Firebase Crashlytics (optional but recommended)

Пакетная структура:
1. `app/`
2. `core/network/`
3. `core/database/`
4. `core/model/`
5. `feature/player_search/`
6. `feature/player_detail/`
7. `feature/favorites/`
8. `feature/news/`
9. `feature/widget/`
10. `domain/`

## 4. External API Contracts (Brawl Stars)

Базовый URL:
1. `https://api.brawlstars.com/v1/`

Ключевые эндпоинты v1:
1. `GET /players/{playerTag}`
2. Опционально: `GET /clubs/{clubTag}` для отображения данных клуба в деталке игрока.

Требования к тегу:
1. В UI принимаем `#XXXX`.
2. Перед запросом URL-encode `#` -> `%23`.
3. Нормализация:
   - trim
   - uppercase
   - валидация regexp: `^#[0289PYLQGRJCUV]{3,}$` (допустимый набор символов для Supercell tag alphabet).

Заголовки:
1. `Authorization: Bearer <BRAWL_API_TOKEN>`

Timeout/retry:
1. connect/read timeout: 15s
2. retry: 1 повтор только для сетевых ошибок (не для 4xx)

HTTP handling:
1. `200`: успех
2. `401/403`: проблема токена или доступа (показываем user-facing ошибку + dev log)
3. `404`: игрок не найден
4. `429`: превышение лимитов, показываем "повторите позже"
5. `5xx`: временная ошибка сервера, предлагаем retry

## 5. Data Model and DB Schema

### 5.1 Entities

1. `players`
   - `tag` TEXT PRIMARY KEY
   - `name` TEXT
   - `name_color` TEXT NULL
   - `icon_id` INTEGER NULL
   - `trophies` INTEGER
   - `highest_trophies` INTEGER
   - `exp_level` INTEGER
   - `club_tag` TEXT NULL
   - `club_name` TEXT NULL
   - `last_synced_at` INTEGER (epoch millis)

2. `player_stats_snapshots`
   - `id` INTEGER PRIMARY KEY AUTOINCREMENT
   - `player_tag` TEXT (FK -> players.tag)
   - `trophies` INTEGER
   - `highest_trophies` INTEGER
   - `exp_level` INTEGER
   - `captured_at` INTEGER
   - Индекс: (`player_tag`, `captured_at` DESC)

3. `favorites`
   - `player_tag` TEXT PRIMARY KEY (FK -> players.tag)
   - `created_at` INTEGER
   - `notify_updates` INTEGER (0/1, default 0)

4. `news_posts`
   - `id` INTEGER PRIMARY KEY AUTOINCREMENT
   - `title` TEXT
   - `content` TEXT
   - `category` TEXT
   - `created_at` INTEGER
   - `updated_at` INTEGER
   - `is_pinned` INTEGER (0/1, default 0)

### 5.2 Room DAOs

1. `PlayerDao`
   - `getPlayer(tag)`
   - `upsertPlayer(player)`
   - `observePlayer(tag): Flow<PlayerEntity?>`

2. `SnapshotDao`
   - `insertSnapshot(snapshot)`
   - `getLatestSnapshots(tag, limit)`
   - `getLastSnapshot(tag)`

3. `FavoritesDao`
   - `getAllFavorites(): Flow<List<FavoriteEntity>>`
   - `upsertFavorite(favorite)`
   - `deleteFavorite(tag)`
   - `isFavorite(tag): Flow<Boolean>`

4. `NewsDao`
   - `observeNews(filter): Flow<List<NewsPostEntity>>`
   - `getNewsById(id)`
   - `insertNews(post)`
   - `updateNews(post)`
   - `deleteNews(id)`

## 6. Domain Layer (Use Cases)

1. `SearchPlayerUseCase(tag)`
   - валидация тега
   - загрузка кэша
   - запрос в API
   - upsert в `players`
   - запись snapshot при изменении статистики

2. `RefreshFavoritePlayersUseCase()`
   - загрузка всех избранных
   - последовательный запрос API с throttling (например delay 300-500ms)
   - обновление players + snapshots

3. `ToggleFavoriteUseCase(tag)`

4. `GetPlayerHistoryUseCase(tag, limit)`

5. `CreateNewsPostUseCase(input)`

6. `UpdateNewsPostUseCase(input)`

7. `DeleteNewsPostUseCase(id)`

8. `GetNewsFeedUseCase(filter)`

## 7. UI/UX Specification

## 7.1 Screens

1. Search Screen
   - поле тега
   - кнопка "Найти"
   - блок recent searches (до 5 последних)
   - error/success states

2. Player Detail Screen
   - имя, тег, трофеи, клуб
   - статус "обновлено X мин назад"
   - кнопка "В избранное"
   - мини-граф прогресса (из snapshots, v1 можно list-based trend)

3. Favorites Screen
   - список избранных игроков
   - pull-to-refresh
   - индикатор изменения трофеев с прошлого snapshot

4. News Feed Screen
   - список постов
   - фильтр по категории
   - pinned posts сверху

5. News Editor Screen
   - поля: title, content, category
   - валидация
   - create/update mode

## 7.2 Widget Behavior

1. Виджет отображает:
   - ник
   - тег
   - трофеи
   - время обновления
2. Источник:
   - последний выбранный игрок (из DataStore)
   - fallback: первый избранный
3. Действия:
   - tap по карточке: открыть `PlayerDetailScreen`
   - кнопка refresh: инициировать background refresh
4. Обновление:
   - при ручном refresh
   - при успешном обновлении favorite worker
   - периодический update через WorkManager (с учетом ограничений Android)

## 8. Repositories and Data Flow

1. `PlayerRepository`
   - `observePlayer(tag): Flow<Player>`
   - `searchAndSync(tag): Result<Player>`
   - `refreshFavorites(): Result<Unit>`

2. `NewsRepository`
   - `observeNews(filter): Flow<List<NewsPost>>`
   - `create(postInput)`
   - `update(postInput)`
   - `delete(id)`

3. `WidgetRepository`
   - `getSelectedTag()`
   - `setSelectedTag(tag)`
   - `getWidgetPlayer(): Flow<Player?>`

Поток поиска:
1. UI -> ViewModel (`submitTag`)
2. ViewModel -> `SearchPlayerUseCase`
3. UseCase -> Repo (cache + remote)
4. Repo -> Room update
5. Room Flow -> UI state update
6. Widget refresh trigger if selected tag equals searched tag

## 9. Error Handling and Validation

1. Ошибки делим на:
   - ValidationError
   - NetworkError
   - ApiError(code)
   - UnknownError
2. User messages:
   - invalid tag -> "Неверный тег игрока"
   - 404 -> "Игрок не найден"
   - 429 -> "Слишком много запросов, попробуйте позже"
   - network timeout -> "Проблема сети"
3. Editor validations:
   - title: 3..120 символов
   - content: 10..5000 символов
   - category: enum (`GENERAL`, `PATCH`, `ESPORTS`, `GUIDE`)

## 10. Security and Config

1. Токен API хранить:
   - `local.properties` -> `BRAWL_API_TOKEN`
   - маппинг в `BuildConfig` на этапе сборки
2. Никогда не коммитить token.
3. Добавить `networkSecurityConfig` только если потребуется custom policy.
4. Логи не должны печатать полный токен.

## 11. Background Jobs

1. `FavoritesRefreshWorker`
   - периодический запуск: каждые 6 часов (минимум под ограничения платформы)
   - условия: сеть подключена
   - при успехе: обновить widget
2. `OneTimePlayerRefreshWorker`
   - ручной refresh из widget

## 12. Testing Plan

Unit tests:
1. Tag validation.
2. SearchPlayerUseCase (success, 404, 429, timeout).
3. Snapshot creation logic (создается только при изменении показателей).
4. News use cases validations.

Integration tests:
1. Repo: cache-first then network update.
2. Favorites refresh updates multiple players.
3. News CRUD in Room.

UI tests:
1. Search flow -> detail open.
2. Add/remove favorite.
3. Create/edit/delete news post.

Manual QA:
1. Widget tap opens expected screen.
2. Offline open shows cached player.
3. API error banners корректно отображаются.

## 13. Delivery Milestones

1. M1 (Week 1): project skeleton, DI, navigation, basic screens stubs.
2. M2 (Week 2): player search + detail + API integration.
3. M3 (Week 3): Room cache + favorites + snapshots + workers.
4. M4 (Week 4): widget implementation.
5. M5 (Week 5): news module CRUD + filters.
6. M6 (Week 6): tests + stabilization + release build.

## 14. Acceptance Criteria (Definition of Done)

1. Пользователь может найти игрока по тегу и увидеть актуальные данные.
2. После перезапуска приложения данные последнего игрока доступны из БД.
3. Избранные игроки автоматически обновляются в фоне.
4. Виджет показывает выбранного/избранного игрока и открывает детали по нажатию.
5. Пользователь может создать, отредактировать и удалить новостной пост.
6. Основные тесты (unit + integration critical path) проходят в CI.

## 15. Assumptions

1. Используется официальный Brawl Stars API token с разрешенным IP.
2. В v1 новости локальные (без общего облачного фида между устройствами).
3. Для графика прогресса допускается упрощенное отображение без внешней chart-библиотеки.
