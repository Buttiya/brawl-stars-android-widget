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
  - иконку режима (при наличии),
  - сохранённый профиль (`name/tag`, `trophies`, `EXP`, `icon`).
- Автообновление данных виджета каждые ~5 минут (через `AlarmManager` + `WorkManager`).
- Ручное обновление виджета кнопкой Refresh.

## Источники данных
### Официальный API (основной)
1. Профиль игрока:
   - `GET https://api.brawlstars.com/v1/players/%23{tag}`
2. Ротация событий/карт:
   - `GET https://api.brawlstars.com/v1/events/rotation`

`events/rotation` возвращает список с `startTime`/`endTime`. Приложение определяет:
- текущую карту: `startTime <= now < endTime`
- следующую карту: ближайшая запись с `startTime > now`

### Brawlify/BrawlAPI fallback
- Если в официальной ротации нет будущей карты для выбранного режима, используется predicted fallback:
  - `https://brawlify.com/events` (парсинг `PREDICTED`).
- Иконки режимов берутся из:
  - `https://api.brawlapi.com/v1/gamemodes`
- Иконка профиля игрока формируется по `iconId` через CDN:
  - `https://cdn.brawlify.com/profile-icons/regular/{iconId}.png`

## Настройка токена
Добавьте токен одним из способов:

1. В `gradle.properties`:

```properties
BRAWL_STARS_API_TOKEN=your_token_here
```

2. Или через переменную окружения:

```powershell
$env:BRAWL_STARS_API_TOKEN="your_token_here"
```

## Требования
- JDK 17
- Android SDK (compile/target SDK 35)
- `minSdk = 26`

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
- Predicted fallback с `brawlify.com/events` основан на парсинге HTML и может сломаться при изменении верстки.
- В официальный API иконки режимов не входят, поэтому они грузятся из `brawlapi.com`.


