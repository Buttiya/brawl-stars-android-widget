# Brawl Stars Android Widget Demo

Android-приложение на Kotlin/Compose с поиском игрока по тегу, избранным и домашним виджетом с текущей/следующей картой выбранного режима.

## Актуальный функционал
- Поиск игрока по тегу (`#TAG` или `TAG`) с нормализацией и валидацией.
- Сохранение игроков в локальную БД (Room) + история изменений (`player_stats_snapshots`).
- Избранные игроки: добавление/удаление, массовое обновление.
- Выбор режима для виджета:
  - `Showdown` (solo/duo/trio объединены)
  - `Gem Grab`
  - `Brawl Ball`
  - `Knockout`
  - `Bounty`
  - `Hot Zone`
- Виджет показывает:
  - текущую карту выбранного режима,
  - следующую карту,
  - иконку режима,
  - сохранённый профиль (`name/tag`, `trophies`, `EXP`, `icon`).
- Ручное обновление виджета кнопкой `Refresh` (через `WorkManager`).

## Как работает загрузка данных
1. Профиль игрока запрашивается из `https://api.brawlapi.com/v1/graphs/player/{tag}`.
2. Если endpoint недоступен (частый `404`), используется fallback на официальный API Brawl Stars (`https://api.brawlstars.com/v1/players/%23{tag}`), если задан токен.
3. Карты/режимы для виджета берутся из:
   - `GET /v1/events`
   - `GET /v1/gamemodes`
4. Если в `events` нет `upcoming` для выбранного режима, используется predicted fallback с `https://brawlify.com/events`.

## Требования
- JDK 17
- Android SDK (compile/target SDK 35)
- `minSdk = 26`

## Настройка официального API (опционально, но рекомендуется)
Без токена поиск игрока может не работать для части тегов из-за ограничений `brawlapi` v1.

Добавьте токен одним из способов:
1. В `gradle.properties`:

```properties
BRAWL_STARS_API_TOKEN=your_token_here
```

2. Или через переменную окружения:

```powershell
$env:BRAWL_STARS_API_TOKEN="your_token_here"
```

## Локальная сборка
```powershell
.\gradlew.bat assembleDebug
```

APK:
- `app/build/outputs/apk/debug/app-debug.apk`

## Установка на устройство
```powershell
.\android-sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

## Коротко по архитектуре
- UI: Jetpack Compose + `ViewModel`
- Data: Retrofit/OkHttp + Room
- Фоновые задачи: WorkManager
- Источник правды для виджета: таблица `widget_cache`

## Ограничения
- `api.brawlapi.com/v1` помечен как deprecated и может менять поведение.
- Без `BRAWL_STARS_API_TOKEN` часть профилей будет недоступна.
- Predicted fallback с `brawlify.com/events` основан на парсинге HTML, поэтому чувствителен к изменениям верстки сайта.
