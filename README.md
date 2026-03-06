# Brawl Stars Android Widget Demo

Демо Android-приложения по плану репозитория: простой поиск игрока по тегу + виджет с картами Solo Showdown и сохранённым профилем.

## Что реализовано в демо
- Поиск игрока по тегу (`#TAG` или `TAG`).
- Нормализация/валидация тега (trim, uppercase, удаление `#`, regex).
- Локальный кэш в Room:
  - `players`
  - `player_stats_snapshots`
  - `favorites`
  - `widget_cache`
- Виджет:
  - текущая карта Solo Showdown
  - следующая карта Solo Showdown (или `TBD`, если API не даёт upcoming)
  - сохранённый профиль: `playerTag`, `trophies`, `EXP`, `profileIcon`
- Обновление виджета через WorkManager (и из виджета, и из приложения).
- Fallback для профилей:
  - сначала `api.brawlapi.com` (`/v1/graphs/player/{tag}`)
  - затем официальный `api.brawlstars.com` (`/v1/players/%23{tag}`) при наличии токена.

## Что не входит в эту демку
- Новости/посты (news/posts) исключены.
- Облачная синхронизация/авторизация.
- Production-grade UI/дизайн.

## API и источники
Primary:
- `https://api.brawlapi.com/v1`

Secondary:
- `https://proxy.brawlapi.com/v1` (используется как fallback, может быть недоступен в отдельных сетях)

Official fallback (для полноты профилей):
- `https://api.brawlstars.com/v1`
- Требует `BRAWL_STARS_API_TOKEN`.

## Настройка токена (рекомендуется)
Чтобы стабильно получать профиль для любых тегов, добавьте токен в `gradle.properties`:

```properties
BRAWL_STARS_API_TOKEN=YOUR_TOKEN_HERE
```

Токен создаётся в [developer.brawlstars.com](https://developer.brawlstars.com/) и привязывается к публичному IP.

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
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Как проверить демо
1. Введите тег игрока (`#PLVV2VY98` или любой другой).
2. Нажмите `Find`.
3. Нажмите `Save profile for widget`.
4. Нажмите `Refresh widget data`.
5. Проверьте виджет на рабочем столе.

## Текущие ограничения API
- `next` карта зависит от содержимого `/events`. Если upcoming нет, показывается `TBD`.
- Если ключ Supercell не совпадает по IP, официальный fallback будет недоступен.

## Основные файлы
- `app/src/main/java/com/example/brawlwidgetdemo/data/network`
- `app/src/main/java/com/example/brawlwidgetdemo/data/db`
- `app/src/main/java/com/example/brawlwidgetdemo/data/repo/PlayerRepository.kt`
- `app/src/main/java/com/example/brawlwidgetdemo/ui`
- `app/src/main/java/com/example/brawlwidgetdemo/widget`
- `app/src/main/res/layout/widget_brawl.xml`
