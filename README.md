# Minsk Restaurant AI Bot

Дипломный проект Telegram AI-гида по ограниченному каталогу ресторанов Минска.
Java services и собственная БД определяют факты и результаты поиска; LLM используется
для понимания запроса, выбора инструмента и контролируемого объяснения.

## Features

**IMPLEMENTED:** каталог десяти реальных активных филиалов, собственные часы и ориентировочные
чеки, 60 menu items (по 6 на филиал, PARTIAL), controlled JSON import, детерминированный Restaurant Search,
четыре REST endpoints и Swagger, Flyway и тесты на H2/PostgreSQL; stateless Spring AI
search tool, Structured Output explanation и Java factual renderer/fallback;
Telegram private chats/long polling, bounded PostgreSQL ChatMemory, criteria continuation,
current selection/references, menu/details follow-up, `/start`, `/help`, `/new`,
controlled errors/unsupported requests и input/output limits.

**IMPLEMENTED:** Docker Compose app/postgres и отдельный Dokploy/VPS deployment.
Текущая deployment acceptance: [DEPLOYMENT](docs/DEPLOYMENT.md).

**EXCLUDED FROM CURRENT MVP:** Google Places enrichment. Рейтинг и live часы Google
не входят в текущий scope; [продуктовое решение](docs/PRODUCT.md#google-places--excluded-from-current-mvp).

## Example user scenario

Работающий Telegram-сценарий при enabled AI и runtime bot token:

> Сегодня в 21:00 нас двое, общий бюджет до 150 BYN, итальянская кухня, хочется спокойно.

LLM извлекает критерии, Java выполняет поиск и возвращает до трёх вариантов.
Запрос «Меню второго» читает сохранённую часть меню по последней показанной подборке.
«Есть паста у первого?» применяет PASTA filter; «До скольки третий?» перечитывает
собственное недельное расписание через Java services.
Короткое «А если нас четверо?» сохраняет остальные критерии. `/new` очищает memory
и criteria/selection без AI calls. Полный структурированный запрос доступен через REST/Swagger без AI.
Пользовательские сценарии и scope: [PRODUCT](docs/PRODUCT.md).

Команды: `/start` — знакомство, `/help` — примеры поиска и follow-up,
`/new` — новый разговор без старых критериев и ordinal references. Все команды Java-only,
без AI calls. Бот объясняет ограничения booking, other city и currency; использует
только Минск/BYN. Вход ограничен 2000 символами. Длинные plain-text ответы делятся
без потери фактов на части до 4096; failed/partial send не обновляет transcript/selection.
Один poller, sequential turns одного чата, без guaranteed/exactly-once delivery.
Update/offset и restart boundaries: [DEPLOYMENT](docs/DEPLOYMENT.md#update--offset-behavior).

## Tech stack

- Java 21, один Maven module, Maven Wrapper 3.9.16.
- Spring Boot 3.5.16, Spring Data JPA/Hibernate, PostgreSQL 17.10, Flyway.
- REST и springdoc 2.9.1; H2 для части tests, JUnit 5/Mockito.
- Spring AI 1.1.8 закреплён BOM; OpenAI provider starter и ChatClient подключены.
- Для AI выбран OpenAI-compatible AIAI.BY и модель `gpt-4.1-mini`.
- Telegram transport — Pengrad Telegram Bot API 10.1.0, private chats/long polling.

Точные зависимости: [pom.xml](pom.xml). Конфигурация AI: [AI](docs/AI.md).

## Architecture overview

Один Spring Boot application — modular monolith. REST controllers вызывают
application services; services читают JPA repositories. Telegram adapter вызывает
ConversationService, который через bounded AI adapter и tools обращается к Java services.
У LLM нет SQL/JPA-доступа.
Границы компонентов: [ARCHITECTURE](docs/ARCHITECTURE.md).

## Restaurant Search overview

Поиск принимает гостей, общий BYN-бюджет, дату/время, optional cuisine и preferredTags.
Java сначала применяет hard filters, затем ранжирует допустимые рестораны:

```text
catalog → hard filters → eligible restaurants → soft tag matching → ranking → ≤3 candidates
matchCount DESC → estimatedTotal ASC → restaurant ID ASC
```

`estimatedTotal = estimatedCheckPerGuest × guests` вычисляется через BigDecimal.
Число гостей не является фильтром вместимости. Часы учитывают дату, местное время
Минска и продолжение интервала предыдущего дня. Tags влияют только на ranking.
Правила и contract: [Restaurant Search](docs/ARCHITECTURE.md#правила-поиска-и-рекомендаций).

Пример тела `POST /api/v1/recommendations`:

```json
{
  "guests": 2,
  "totalBudgetByn": 150.00,
  "date": "TODAY",
  "time": "21:00",
  "cuisine": "ITALIAN",
  "preferredTags": ["QUIET"]
}
```

## AI integration overview

**IMPLEMENTED:** Spring AI ChatClient выбирает один из ровно трёх read-only tools:
searchRestaurants, getRestaurantDetails, getRestaurantMenu. Модель выбирает причины объяснения
через native Structured Output; Java проверяет план и формирует фактические карточки.
Invalid/failed search explanation даёт Java fallback. До двух model calls и одного tool
execution на turn; во втором call tools отключены. Menu/details используют один model
call, существующий ReferenceResolver и MenuService/RestaurantService; Java формирует
фактический ответ без search. Telegram conversation entry добавляет safe memory и Java criteria merge.
Contracts: [AI](docs/AI.md).

## Database

Flyway V1–V9 создаёт каталог/меню, conversation state, JDBC memory и selection, применяет локальные datasets; Hibernate использует
`validate`. У данных сохранены source и verifiedAt. Меню всегда PARTIAL; отсутствие
позиции в БД не доказывает её отсутствия в полном меню. Поиск не зависит от MenuItem.
Модель, provenance и import contract: [DATABASE](docs/DATABASE.md).

DERIVED check — ручной ориентир из опубликованных цен выбранного основного блюда
и супа на гостя; runtime не рассчитывает корзину по меню. `verifiedAt` означает
дату проверки источника, а не дату импорта. Конкретные источники и граница применения
общего сетевого меню к филиалам приведены в DATABASE.

## REST API / Swagger

| Method | Path | Назначение |
|---|---|---|
| GET | `/api/v1/restaurants` | Active catalog, page/size |
| GET | `/api/v1/restaurants/{id}` | Own details |
| GET | `/api/v1/restaurants/{id}/menu` | Partial menu, optional dishType/maxItemPriceByn |
| POST | `/api/v1/recommendations` | Полные criteria, до трёх ordered candidates |

Swagger: <http://127.0.0.1:8080/swagger-ui/index.html>.
OpenAPI: <http://127.0.0.1:8080/v3/api-docs>.
HTTP доступен на loopback; REST/Swagger предназначены для локального запуска и demo.

## Configuration

Default profile требует `DB_URL`, `DB_USER`, `DB_PASSWORD` из внешней среды.
Test profile использует H2 и не требует внешних ключей или PostgreSQL.
AI search включается отдельно: `AI_ENABLED=true` и `AIAI_API_KEY` в environment.
По умолчанию AI отключён. Для private Telegram long polling задайте также
`TELEGRAM_BOT_TOKEN` в environment запускаемого процесса. Memory использует ту же DB.
Секреты хранятся вне Git. Runtime/Compose mapping: [DEPLOYMENT](docs/DEPLOYMENT.md#environment-configuration).

## Running locally

Требуется JDK 21 в `JAVA_HOME`. Первый запуск Wrapper/сборки требует доступа к Maven Central.
Из корня проекта в PowerShell для H2 demo:

```powershell
.\mvnw.cmd spring-boot:test-run "-Dspring-boot.run.profiles=test"
```

Для PostgreSQL задайте DB-переменные в среде и запустите:

```powershell
.\mvnw.cmd spring-boot:run
```

На Linux/macOS используется `./mvnw` вместо `.\mvnw.cmd`.

## Tests

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean verify
```

Tests проверяют каталог, меню/import, search boundaries, overnight, ranking, REST,
AI tool/plan guards и production SDK через offline loopback HTTP fixtures,
criteria merge/ambiguity, two-chat isolation, `/new`, window/safe writes и application restart;
menu/details routing, exact/ordinal references, fresh own facts и PARTIAL semantics.
Telegram finishing tests также проверяют commands/unsupported/errors, response timeout
на production AI SDK, split/partial failure, concurrent same-chat turns и two-chat isolation;
Pengrad offset/restart/429 smoke использует только локальный HTTP fixture.
Обычные test/verify не вызывают live provider и не требуют API key.
Отдельный минимальный live smoke (один turn, максимум два платных calls), только
после проверки account budget/quotas и с AIAI_API_KEY в environment:

```powershell
.\mvnw.cmd "-Dtest=AiaiLiveSmokeIT" test
```

Итоговый live eval запускается **отдельно и явно** на изолированной PostgreSQL test
DB с `AIAI_API_KEY` в environment. Telegram token не нужен; transport выключен
test profile. Набор содержит 12 conversational и 5 adversarial cases; те же inputs,
contexts и assertions используются offline в `FinalAiOfflineEvalTest`. Не запускать
на рабочей БД: harness использует синтетические чаты и очищает их после проверки.

```powershell
.\mvnw.cmd test "-Dtest=AiaiFinalLiveEvalIT" `
  "-Dspring.datasource.url=$env:DB_URL" `
  "-Dspring.datasource.username=$env:DB_USER" `
  "-Dspring.datasource.driver-class-name=org.postgresql.Driver"
```

Пароль test DB задаётся через `SPRING_DATASOURCE_PASSWORD`, без CLI argument.
Provider/model/settings и критерии результата: [AI eval](docs/AI.md#ai-eval).
Обычный `test/verify` не выбирает `*IT` и не обращается к внешним API.

Итоговая проверка 2026-10-06: `clean test` и `clean verify` — по 399 tests;
PostgreSQL 17.10 — 398 tests (H2-only bootstrap исключён), failures/errors/skipped=0.
Отдельный live eval на AIAI.BY/gpt-4.1-mini: main **12/12**, adversarial **5/5**,
critical failures **0**. Фиксированный Clock, контекст cases и границы проверки:
[AI eval](docs/AI.md#ai-eval). Результат одного run не гарантирует все будущие ответы модели.

H2 не заменяет PostgreSQL acceptance. Команда проверки на отдельной пустой test БД:
[PostgreSQL acceptance](docs/DATABASE.md#postgresql-acceptance).

## Deployment

Dockerfile и `compose.yaml` реализованы: Maven Wrapper multi-stage build → Java 21
JRE/non-root runtime; app + PostgreSQL 17.10, healthchecks и стабильный named volume.
Local и remote deployment acceptance PASS: build/health/private HTTP/DB, Telegram/AIAI
search, restart, Dokploy recreate, mutable state и ordinal follow-up после обоих запусков.
Telegram long polling требует один
активный app/poller, `AI_ENABLED=true`, runtime AIAI_API_KEY и TELEGRAM_BOT_TOKEN.
Google key не требуется.

### Docker Compose local start

Нужен запущенный Docker Engine/Compose. В отдельном local env file вне repository
(или ignored `.env`) задать POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD,
POSTGRES_VOLUME_NAME (уникальное стабильное имя volume), APP_HOST_PORT (свободный port).
Для local container smoke установить `AI_ENABLED=false`, AIAI_API_KEY и
TELEGRAM_BOT_TOKEN оставить пустыми. Значения реальных secrets не помещать в Git.

```sh
docker compose --env-file <LOCAL_ENV_FILE> config --quiet
docker compose --env-file <LOCAL_ENV_FILE> build
docker compose --env-file <LOCAL_ENV_FILE> up -d --wait --wait-timeout 240
docker compose --env-file <LOCAL_ENV_FILE> ps
```

REST: `http://127.0.0.1:<APP_HOST_PORT>/api/v1/restaurants`, Swagger:
`http://127.0.0.1:<APP_HOST_PORT>/swagger-ui/index.html`. PostgreSQL host port отсутствует.
При обычном stop/start/redeploy сохранять POSTGRES_VOLUME_NAME и не удалять volume.
Shell env имеет приоритет над env file: при smoke не использовать production AI/token.
Перед публикацией deployment candidate выполнить `mvnw clean verify` отдельно.

### Dokploy/VPS

Source: этот repository, branch `main`, Compose file `compose.yaml`, режим Docker
Compose; отдельный проект, без Domains/Traefik route/scaling. Env из Dokploy UI явно
mapped в Compose; POSTGRES_PASSWORD также передаётся как app DB_PASSWORD.
REST/Swagger доступны через host loopback и SSH tunnel, DB — внутри Compose network.
VPS resources, Telegram/AIAI E2E, mutable-state restart/redeploy и backup smoke
зафиксированы отдельно от local tests. Runbook, status, private access и backup:
[DEPLOYMENT](docs/DEPLOYMENT.md).

## Current MVP limitations

Каталог ограничен 10 конкретными филиалами трёх сетей; полным каталогом Минска он не является.
Сетевые филиалы используют проверенное общее меню и одинаковую методику чека.
Поддержаны Минск, BYN, 1–6 гостей,
сегодня и следующие шесть дней. REST не разбирает естественный язык.
DERIVED check — собственная ориентировочная оценка, не официальный средний чек.
Наличие столика, блюда, праздничные часы и тишина не гарантируются.
Google Places enrichment исключён из текущего MVP; Google key для запуска не требуется.
Поиск и details используют собственные данные. Собственные телефон/website
не сохранены; details явно сообщает об отсутствии этих полей.
Memory ограничена 20 user/assistant messages на чат.
Booking, публичный admin/chat API, геопоиск и RAG/vector search вне MVP.

Критерии завершения диплома: [Definition of Done](docs/PRODUCT.md#definition-of-done).
Демонстрация рассчитана на 8–10 минут: архитектура → поиск → меню по позиции →
изменение гостей → собственные часы → evidence проверок и ограничения.
