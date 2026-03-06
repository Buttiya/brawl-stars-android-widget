# Brawl Stars Android Widget - Demo

В репозитории подготовлена демо-версия Android-приложения по `FINAL_IMPLEMENTATION_PLAN.md` (с виджетом), в максимально простом интерфейсе.

## Что реализовано (демо)
- Поиск игрока по тегу через `GET /v1/graphs/player/{tag}`.
- Нормализация/валидация тега (`#` убирается, upper-case, regex из плана).
- Локальный кэш игрока в Room (`players`).
- История snapshots в Room (`player_stats_snapshots`) при изменении трофеев.
- Избранные игроки (`favorites`) + ручной refresh избранных.
- Виджет + `widget_cache`:
  - текущая карта Solo Showdown,
  - следующая карта Solo Showdown,
  - сохранённый профиль (`playerTag`, `totalTrophies`, `profileIcon`).
- Кнопка refresh в виджете запускает one-time `WorkManager` worker.

## Что специально не включено
- Новости и посты (модуль `news/posts` полностью исключён).
- Сложный дизайн, анимации, расширенная навигация.

## Структура
- `app/src/main/java/com/example/brawlwidgetdemo/data/network` - Retrofit API.
- `app/src/main/java/com/example/brawlwidgetdemo/data/db` - Room entities/dao/db.
- `app/src/main/java/com/example/brawlwidgetdemo/data/repo` - репозиторий и маппинг API -> DB.
- `app/src/main/java/com/example/brawlwidgetdemo/ui` - ViewModel и примитивный Compose UI.
- `app/src/main/java/com/example/brawlwidgetdemo/widget` - provider, worker, icon cache.
- `app/src/main/res/layout/widget_brawl.xml` - макет виджета.

## Запуск
1. Открыть проект в Android Studio.
2. Дождаться синхронизации Gradle.
3. Запустить модуль `app` на эмуляторе/устройстве.
4. Добавить виджет `Brawl Demo` на рабочий стол.
5. В приложении найти игрока и нажать `Save profile for widget`.
6. Нажать `Refresh widget data` в приложении или `Refresh` в виджете.

Примечание: в текущем CLI-окружении нет `gradle/gradlew`, поэтому сборка здесь не запускалась.

## Legacy source
`brawlify.js` оставлен в репозитории как reference-источник API-контрактов.
