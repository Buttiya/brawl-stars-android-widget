# Brawl Stars Android Widget Demo

Демо Android-приложения: поиск игрока по тегу + виджет с текущей и будущей картой выбранного режима.

## Что реализовано
- Поиск игрока по тегу (`#TAG` или `TAG`).
- Нормализация/валидация тега.
- Локальный кэш в Room (`players`, `player_stats_snapshots`, `favorites`, `widget_cache`).
- Выбор режима отслеживания для виджета (Showdown/Gem Grab/Brawl Ball/Knockout/Heist/Bounty/Hot Zone).
- Showdown-группировка: solo/duo/trio считаются одним режимом.
- Виджет показывает:
  - текущую карту выбранного режима,
  - следующую карту (upcoming/predicted),
  - иконку режима перед будущей картой,
  - сохранённый профиль (tag/trophies/EXP/icon).
- Обновление виджета через WorkManager.

## API
Источник: `https://api.brawlapi.com`.

Используемые endpoints:
- `GET /v1/events`
- `GET /v1/gamemodes`
- `GET /v1/icons`
- `GET /v1/graphs/player/{tag}` (legacy route, часто возвращает 404)

`v2` в официальной документации сейчас предназначен для static/raw game files (`/v2/raw/...`) и не даёт live event rotation.
Поэтому будущие карты берутся из `/v1/events`, а если `upcoming` пустой, используется predicted fallback из `https://brawlify.com/events`.

## Ограничения
- `v1` помечен как deprecated и может быть удалён.
- Для многих тегов `GET /v1/graphs/player/{tag}` возвращает `404`; в этом случае приложение сохраняет локальный fallback-профиль по тегу, чтобы не ломались UI/избранное/виджет.

## Локальная сборка
Требуется:
- JDK 17
- Android SDK (platform-tools, platform 35, build-tools)

Сборка:

```powershell
.\gradlew.bat assembleDebug
```

APK:
- `app/build/outputs/apk/debug/app-debug.apk`

## Установка на устройство
```powershell
.\android-sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```