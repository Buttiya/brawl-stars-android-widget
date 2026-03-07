# Brawl Stars Android Widget App: Final Implementation Plan (v3, widget data refined)

## 1. Product Goal

Собрать Android-приложение с виджетом, которое позволяет:
1. Искать игроков по тегу через API, зафиксированный в репозитории (`brawlify.js` / BrawlAPI).
2. Хранить и агрегировать данные в локальной БД (кэш + история).
3. Создавать свои посты-новости внутри приложения.

Результат v1: рабочий поиск игрока по тегу, виджет с картами Solo Showdown и сохраненным профилем игрока, локальный CRUD новостей.

## 2. Scope and v1 Boundaries

Включено в v1:
1. Поиск игрока по тегу и просмотр статистики.
2. Избранные игроки и фоновое обновление.
3. История snapshots в Room для офлайн-отображения.
4. Виджет с обязательными полями:
   - текущая карта в Solo Showdown
   - следующая карта в Solo Showdown
   - сохраненный профиль пользователя: `totalTrophies`, `profileIcon`, `playerTag`
5. Локальная лента новостей и редактор постов (CRUD).

Не включено в v1:
1. Облачная синхронизация новостей между устройствами.
2. Авторизация пользователей.

## 3. Tech Stack and Architecture

1. Android:
   - Kotlin
   - Jetpack Compose
   - MVVM + Repository + UseCase
   - Coroutines + Flow
2. Data:
   - Retrofit + OkHttp (или Ktor Client)
   - Room
   - DataStore (настройки виджета, выбранный профиль)
3. Background:
   - WorkManager
4. Widget:
   - Glance AppWidget (fallback RemoteViews при необходимости)
5. Логирование:
   - Timber

## 4. API Contracts (based on `brawlify.js`)

Источники:
1. [brawlify.js](C:\Codex\brawl-stars-android-widget\brawlify.js)
2. [README.md](C:\Codex\brawl-stars-android-widget\README.md)

Base URL:
1. `https://api.brawlapi.com/v1`

Header:
1. `User-Agent: Brawlify.com/app`

Эндпоинты, используемые в v1:
1. `GET /graphs/player/{playerTag}` -> данные профиля/статистики игрока.
2. `GET /events` -> текущие и upcoming события (для карты Solo Showdown).
3. `GET /icons` -> справочник иконок (разрешение iconId -> URL).

Дополнительно доступны (необязательно в MVP UI):
1. `GET /brawlers`
2. `GET /maps`
3. `GET /gamemodes`
4. `GET /maps/{mapId}`

Нормализация тега:
1. Принимаем `#TAG` в UI.
2. В API передаем `TAG` (без `#`, uppercase).

## 5. Widget Data Contract (mandatory)

Далее взят конкретный пример - одиночное столкновение, но пользователь сможет выбирать какой режим он хочет отслеживать.
Виджет обязан отображать 2 блока:

1. Solo Showdown Maps
   - `currentMapName`: текущая карта в одиночном столкновении.
   - `currentMapImageUrl` (если есть в `events`).
   - `nextMapName`: следующая карта в одиночном столкновении.
   - `nextMapStartAt` (локальное время старта, если поле есть в API).

2. Saved Player Profile
   - `playerTag`
   - `totalTrophies`
   - `profileIconUrl` (через `/icons` по iconId из данных игрока)

Правила выбора данных:
1. Профиль для виджета берется из сохраненного пользователем `playerTag`.
2. Если профиль не выбран, показывать `Select player` + только блок карт.
3. Если `next` карта недоступна в API, показывать `TBD`.

## 6. Data Model and DB Schema

### 6.1 Entities

1. `players`
   - `tag` TEXT PRIMARY KEY
   - `name` TEXT NULL
   - `trophies` INTEGER NULL
   - `profile_icon_id` INTEGER NULL
   - `last_synced_at` INTEGER

2. `player_stats_snapshots`
   - `id` INTEGER PRIMARY KEY AUTOINCREMENT
   - `player_tag` TEXT
   - `trophies` INTEGER NULL
   - `captured_at` INTEGER

3. `favorites`
   - `player_tag` TEXT PRIMARY KEY
   - `created_at` INTEGER

4. `widget_cache`
   - `id` INTEGER PRIMARY KEY CHECK(id = 1)
   - `solo_current_map_name` TEXT NULL
   - `solo_current_map_image_url` TEXT NULL
   - `solo_next_map_name` TEXT NULL
   - `solo_next_map_start_at` INTEGER NULL
   - `saved_player_tag` TEXT NULL
   - `saved_player_trophies` INTEGER NULL
   - `saved_player_icon_url` TEXT NULL
   - `updated_at` INTEGER

5. `news_posts`
   - `id` INTEGER PRIMARY KEY AUTOINCREMENT
   - `title` TEXT
   - `content` TEXT
   - `category` TEXT
   - `created_at` INTEGER
   - `updated_at` INTEGER
   - `is_pinned` INTEGER DEFAULT 0

## 7. Domain and Data Flow

Use cases:
1. `SearchPlayerByTagUseCase`
2. `RefreshFavoritesUseCase`
3. `RefreshWidgetSoloMapsUseCase`
   - получает `/events`
   - фильтрует только Solo Showdown
   - выбирает текущую и следующую карту
   - пишет в `widget_cache`
4. `RefreshWidgetSavedProfileUseCase`
   - берет `saved_player_tag`
   - обновляет игрока через `/graphs/player/{tag}`
   - обогащает иконкой через `/icons`
   - пишет `trophies/icon/tag` в `widget_cache`
5. `Create/Update/DeleteNewsPostUseCase`

## 8. UI and Widget Behavior

Экраны приложения:
1. Search
2. Player Detail
3. Favorites
4. News Feed
5. News Editor

Виджет:
1. Верхний блок: `Solo Showdown` текущая и следующая карта.
2. Нижний блок: сохраненный профиль (`tag`, `total trophies`, `profile icon`).
3. Tap по профилю -> открывает `Player Detail`.
4. Кнопка refresh -> запускает one-time worker на обновление карт и профиля.

## 9. Validation, Reliability, Security

Validation:
1. Тег: trim + uppercase + удаление `#`.
2. Допустимые символы: `^[0289PYLQGRJCUV]{3,}$`.

Reliability:
1. Таймауты: 15s.
2. Retry: 1 повтор для сетевых ошибок.
3. На ошибке сети виджет показывает last-known cached данные.

Security:
1. Для текущего API bearer token не требуется.
2. Не логировать персональные данные сверх `playerTag`.

## 10. Testing Plan

Unit:
1. Tag normalization/validation.
2. Парсинг `/events` -> current/next Solo Showdown.
3. Маппинг iconId -> iconUrl через `/icons`.

Integration:
1. Обновление `widget_cache` из API.
2. Виджет читает данные из `widget_cache` при офлайне.
3. Search + save profile + widget render.

UI:
1. Выбор профиля для виджета.
2. Отображение текущей/следующей Solo карты.
3. Отображение кубков/иконки/тега сохраненного профиля.

## 11. Delivery Milestones

1. Week 1: skeleton + Room + network layer.
2. Week 2: player search + save profile.
3. Week 3: widget maps/profile data pipeline.
4. Week 4: widget UI + refresh actions.
5. Week 5: news module.
6. Week 6: tests + stabilization.

## 12. Definition of Done

1. Виджет показывает текущую и следующую Solo Showdown карту.
2. Виджет показывает сохраненный профиль: `playerTag`, `totalTrophies`, `profileIcon`.
3. При отсутствии сети виджет использует последний валидный кэш.
4. Поиск игрока и сохранение профиля работают стабильно.
5. CRUD новостей работает.

## 13. Assumptions

1. `/events` содержит признак режима для фильтрации Solo Showdown.
2. `/graphs/player/{tag}` содержит поле, достаточное для `totalTrophies` и `iconId`.
3. Если структура API отличается, маппинг фиксируется в адаптере без изменения UI-контракта виджета.
