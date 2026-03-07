# Brawl Stars Android Widget App: Implementation Plan (v4, Clubs & Families)

## 1. Product Goal

Расширить приложение новой вкладкой `Clubs`, где пользователь сможет:
1. Искать клубы по тегу и сохранять их статистику.
2. Просматривать сравнение клубов по силе, составу, активности и общим кубкам.
3. Проходить верификацию участника/президента клуба.
4. При подтвержденной роли президента предлагать другим клубам объединение в семейство.
5. Получать клубные сообщения и новости.

Демо-версия: без централизованного backend-сервера, обновление данных только вручную через кнопку `Refresh`.

## 2. Scope and Boundaries

### 2.1 Included in Demo (MVP)
1. Вкладка `Clubs` с поиском клуба по тегу.
2. Сохранение клубов и их snapshots в локальной БД.
3. Ручное обновление статистики (`Refresh`) без фоновой синхронизации.
4. Локальная верификация пользователя (участник/президент) в пределах устройства.
5. Локальный сценарий "предложить объединение в семейство" (черновики предложений).
6. Экран семейств клубов с сравнением:
   - суммарные кубки;
   - число участников с высоким рангом;
   - активность (по доступным признакам);
   - условная "сила" клуба (расчетный рейтинг).
7. Сообщения клуба (локальные объявления президента для подтвержденных участников).
8. Существующий функционал карт, будущих карт и рекомендаций по лучшим персонажам.
9. Раздел советов + блок `Interesting Ideas` с наградами за квесты/клубные баллы.

### 2.2 Deferred to Production
1. Централизованный сервер семейств/сообщений/новостей.
2. Межустройственная синхронизация аккаунта и статусов верификации.
3. Push-уведомления в реальном времени.
4. Модерация и аудит действий президентов.

## 3. Core Functional Requirements

1. `Club Search`: ввод `#TAG` или `TAG`, валидация, загрузка данных клуба.
2. `Club Save`: добавление клуба в локальную коллекцию с историей snapshots.
3. `Verification`:
   - пользователь подтверждает связь с клубом;
   - для роли президента требуется отдельное подтверждение (для демо - локальная схема).
4. `Family Proposal`:
   - только подтвержденный президент может создать предложение;
   - предложения сохраняются локально со статусом (`draft`, `sent`, `accepted`, `declined`).
5. `Family Analytics`:
   - сравнение клубов внутри семейства по ключевым метрикам;
   - рейтинг "сильнее/слабее" на базе прозрачной формулы.
6. `Club Messages`:
   - президент публикует оповещение;
   - подтвержденные участники клуба видят сообщения.
7. `Maps and Recommendations`:
   - текущие/будущие карты;
   - лучшие персонажи для карт;
   - советы для участников/президентов.
8. `Interesting Ideas and Rewards`:
   - карточки с идеями/квестами;
   - начисление наградных баллов по событию (в демо - локально).

## 4. Demo vs Production Architecture

### 4.1 Demo Architecture
1. `Single Device First`: источник правды - локальная Room БД.
2. Данные клубов и событий подтягиваются по API только по `Refresh`.
3. Верификация и сообщения работают локально, без внешнего сервера.

### 4.2 Production Target Architecture
1. `Backend API` для пользователей, клубов, семейств, сообщений и новостей.
2. `Auth + Role Service` для подтверждения президента.
3. `Sync Layer` для двусторонней синхронизации и конфликт-резолюции.
4. `Notification Service` для клубных объявлений и ответов на предложения.

## 5. Data Model (Room, Demo)

1. `clubs`
   - `tag` TEXT PRIMARY KEY
   - `name` TEXT
   - `description` TEXT NULL
   - `total_trophies` INTEGER NULL
   - `member_count` INTEGER NULL
   - `president_name` TEXT NULL
   - `last_synced_at` INTEGER

2. `club_members_snapshots`
   - `id` INTEGER PRIMARY KEY AUTOINCREMENT
   - `club_tag` TEXT
   - `captured_at` INTEGER
   - `payload_json` TEXT

3. `user_verifications`
   - `id` INTEGER PRIMARY KEY CHECK(id = 1)
   - `user_tag` TEXT NULL
   - `club_tag` TEXT NULL
   - `role` TEXT NULL (`member` | `president`)
   - `status` TEXT NOT NULL (`pending` | `verified` | `rejected`)
   - `verified_at` INTEGER NULL

4. `club_families`
   - `id` INTEGER PRIMARY KEY AUTOINCREMENT
   - `family_name` TEXT
   - `created_by_club_tag` TEXT
   - `created_at` INTEGER

5. `family_clubs`
   - `family_id` INTEGER
   - `club_tag` TEXT
   - PRIMARY KEY (`family_id`, `club_tag`)

6. `family_merge_proposals`
   - `id` INTEGER PRIMARY KEY AUTOINCREMENT
   - `from_club_tag` TEXT
   - `to_club_tag` TEXT
   - `family_id` INTEGER NULL
   - `message` TEXT
   - `status` TEXT (`draft` | `sent` | `accepted` | `declined`)
   - `created_at` INTEGER
   - `updated_at` INTEGER

7. `club_messages`
   - `id` INTEGER PRIMARY KEY AUTOINCREMENT
   - `club_tag` TEXT
   - `author_role` TEXT
   - `title` TEXT
   - `content` TEXT
   - `created_at` INTEGER
   - `visibility` TEXT (`verified_members`)

8. `reward_events`
   - `id` INTEGER PRIMARY KEY AUTOINCREMENT
   - `club_tag` TEXT
   - `user_tag` TEXT
   - `reason` TEXT (`quest` | `president_points`)
   - `points` INTEGER
   - `created_at` INTEGER

## 6. Key Use Cases

1. `SearchClubByTagUseCase`
2. `SaveClubUseCase`
3. `RefreshClubStatsUseCase`
4. `VerifyUserRoleUseCase`
5. `CreateFamilyProposalUseCase`
6. `RespondFamilyProposalUseCase`
7. `BuildFamilyComparisonUseCase`
8. `PublishClubMessageUseCase`
9. `GetVerifiedClubFeedUseCase`
10. `AssignRewardPointsUseCase`

## 7. UI Plan

1. `Clubs Tab`
   - поиск и карточка клуба;
   - кнопки `Save` и `Refresh`;
   - история изменений.
2. `Verification Screen`
   - текущий статус;
   - подтверждение роли участника/президента.
3. `Family Screen`
   - список семейства;
   - сравнение клубов;
   - входящие/исходящие предложения.
4. `Club Messages Screen`
   - лента объявлений клуба;
   - создание объявления (только президент).
5. `Ideas & Rewards Screen`
   - советы;
   - интересные идеи;
   - награды за квесты/баллы от президента.

## 8. Security and Validation

1. Нормализация тегов: trim, uppercase, удаление `#`.
2. Разрешенные символы тегов: `^[0289PYLQGRJCUV]{3,}$`.
3. Гейт прав:
   - создание предложений семейства и публикация клубных сообщений только для `verified president`.
4. Логирование:
   - без персональных данных сверх `tag`, `clubTag`, `role`.

## 9. Testing Plan

Unit:
1. Валидация тегов клуба.
2. Проверка ролевых ограничений (president-only actions).
3. Расчет сравнения "сильнее/слабее".
4. Фильтрация сообщений для подтвержденных участников.

Integration:
1. Поиск клуба -> сохранение -> refresh -> обновление snapshots.
2. Верификация -> доступ к president-функциям.
3. Жизненный цикл предложения объединения (`draft -> sent -> accepted/declined`).

UI:
1. Переходы внутри вкладки `Clubs`.
2. Корректность отображения аналитики семейства.
3. Создание и чтение клубных сообщений.

## 10. Milestones

1. Phase 1: Data schema migration + Clubs tab skeleton.
2. Phase 2: Club search/save/refresh + snapshots.
3. Phase 3: Verification flow + role gates.
4. Phase 4: Family proposals + family analytics.
5. Phase 5: Club messages + ideas/rewards block.
6. Phase 6: Stabilization, тесты и демо-подготовка.

## 11. Definition of Done (Demo)

1. Пользователь может найти клуб по тегу, сохранить и обновить статистику вручную.
2. В приложении работает локальная верификация участника/президента.
3. Только подтвержденный президент может создавать предложения объединения и клубные объявления.
4. Экран семейства показывает сравнение клубов по ключевым метрикам.
5. Сообщения клуба доступны подтвержденным участникам соответствующего клуба.
6. Блок карт/будущих карт/лучших персонажей сохранен и доступен из UI.
7. Реализован блок советов и `Interesting Ideas` с локальными наградами.

## 12. Assumptions

1. Для демо принимается локальная модель доверия без серверной криптографической верификации.
2. Источники внешних данных по клубам и картам остаются доступными по текущему API-слою.
3. Продакшн-сервер будет добавлен отдельной фазой без критического рефакторинга UI-контрактов.
