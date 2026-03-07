# Brawl Stars Android Widget Demo

Android-приложение на Kotlin/Compose с тремя вкладками: `Главная`, `Клубы`, `Профиль`.

## Актуальный функционал
- Нижняя навигация:
  - `Главная` — поиск игрока, избранное, управление данными для виджета.
  - `Клубы` — заглушка `Coming soon...`.
  - `Профиль` — локальная авторизация, привязка игрового профиля и подтверждение.
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
- Ручное обновление виджета кнопкой `Refresh`.

## Профиль и подтверждение
- Локальная авторизация (один аккаунт на устройстве): `логин + пароль`.
- Привязка игрового профиля по `tag`.
- В карточке профиля отображаются:
  - ник,
  - кубки,
  - EXP,
  - победы `3v3`,
  - победы `Showdown`.
- Статус под `Победы Showdown`:
  - зелёный текст `Профиль подтверждён`,
  - красный текст `Профиль не был подтверждён`.
- Challenge-подтверждение:
  1. Нажать `Запросить иконку`.
  2. Приложение выбирает случайную иконку **редкого/сверхредкого** бойца,
     доступного у игрока, и отличную от текущей.
  3. Пользователь меняет иконку в игре в течение 5 минут.
  4. Нажимает `Done.` — приложение сверяет текущую иконку с ожидаемой.
  5. При успехе профиль помечается подтверждённым.
  6. Если время вышло, задание сбрасывается и нужно запросить новую иконку.

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
- Авторизация в демо локальная (не серверная, не production-ready).
