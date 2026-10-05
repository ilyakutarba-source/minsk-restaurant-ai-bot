# Build, runtime и deployment

## Текущий запуск — IMPLEMENTED

Требуются JDK 21, Maven Wrapper 3.9.16 и PostgreSQL для default profile.
Spring Boot получает DB connection из внешней среды, Flyway применяет V1–V4,
Hibernate проверяет схему через `ddl-auto=validate`. HTTP bind — `127.0.0.1`.
Test profile использует H2 без внешних API/ключей и доступен для локального REST demo.
Команды запуска и проверки: [README](../README.md#running-locally).

AI provider подключён для opt-in stateless search: `AI_ENABLED=true` и `AIAI_API_KEY`.
Без enabled AI REST работает без ключа и внешних вызовов; test profile отключает AI.
Production ChatClient/search/explanation и limits: [AI](AI.md).
Telegram, Google и persistent conversation state пока не подключены.
Dockerfile, Compose и CI configuration пока отсутствуют. Ниже описан **PLANNED**
deployment contract, а не инструкция уже готового контейнерного запуска.

## Target — PLANNED

```text
GitHub repository → Dokploy Compose project → VPS
                                            ├── app: один Spring Boot instance
                                            └── postgres: persistent named volume
```

Runtime — Java 21. Выбран remote AIAI API, локальная модель/GPU не требуется.
CPU/RAM/disk должны учитывать application, PostgreSQL, image build и backup storage;
достаточность конкретного VPS и доступ к Dokploy ещё необходимо подтвердить.
Один bot token — один активный poller. Microservices, replicas и публичный webhook
не входят в MVP. Scope: [PRODUCT](PRODUCT.md).

## Environment configuration

Текущая application configuration:

| Переменная | Назначение |
|---|---|
| DB_URL | PostgreSQL JDBC URL для default profile |
| DB_USER | Имя пользователя DB; именно DB_USER, не DB_USERNAME |
| DB_PASSWORD | Пароль DB из внешней среды |
| SPRING_PROFILES_ACTIVE | Spring profile; test включает H2 |
| SPRING_DATASOURCE_PASSWORD | Пароль отдельной test DB при PostgreSQL tests |
| AI_ENABLED | Opt-in production AI search; default false |
| AIAI_API_KEY | Runtime credential AIAI.BY при AI_ENABLED=true |

DB_URL/DB_USER используются прямо в application.yml. Test connection overrides и
команда проверки: [DATABASE](DATABASE.md#postgresql-acceptance).

Planned integration/container variables; они ещё не связаны с приложением:

| Переменная | Назначение |
|---|---|
| TELEGRAM_BOT_TOKEN | Секрет Telegram bot token |
| TELEGRAM_ALLOWED_CHAT_IDS | Optional allowlist private chats для demo |
| GOOGLE_PLACES_ENABLED | Conditional enrichment flag после проверки доступности/условий |
| GOOGLE_MAPS_API_KEY | Credential только при enabled Google integration |
| POSTGRES_DB / POSTGRES_USER / POSTGRES_PASSWORD | Инициализация PostgreSQL container |
| APP_TIMEZONE | Planned явная runtime setting; текущий Search Clock уже использует Europe/Minsk |

AiConfiguration создаёт production model явно: key из `restaurant-bot.ai.api-key`
через AIAI_API_KEY, base-url `https://api.aiai.by`, completions-path
`/v1/chat/completions`, model `gpt-4.1-mini`. Provider auto-models и memory auto-config
отключены. `restaurant-bot.ai.explanation-enabled=false` отключает optional second call.
Origin base и versioned path не должны дублировать `/v1`. Retry/transport timeouts и
исполнение в пределах turn budget принадлежат [AI](AI.md#bounded-execution).

DB_PASSWORD и POSTGRES_PASSWORD должны согласовываться при первой инициализации
контейнера. Изменение init env существующего volume не меняет пароль/пользователей
PostgreSQL автоматически. Последующие изменения выполняются отдельно.
Секретные значения не помещаются в docs, Git, build args, image или обычные logs.

## Docker и Compose — PLANNED

- Maven build → Java 21 runtime; pinned image tags и непривилегированный runtime user.
- Два services: app и postgres, persistent named PostgreSQL volume.
- DB hostname внутри сети — postgres, не localhost.
- PostgreSQL healthcheck; startup dependency `service_healthy`.
- Flyway управляет схемой, Hibernate validate проверяет её.
- Runtime credentials через environment/env_file; реальные значения вне Git.
- Initial datasets входят в image, внешних API calls при seed нет.
- PostgreSQL port не публикуется в интернет; временные checkout paths не используются.

В Compose текущий loopback-only app bind потребуется согласовать с container network
и закрытым способом доступа к REST. Политика приложения остаётся непубличной;
открытие интерфейса наружу не является частью этого contract.
После реализации проверяются clean start, API/Telegram smoke, restart/redeploy и
сохранение restaurant/menu/conversation данных. [Compose startup order](https://docs.docker.com/compose/how-tos/startup-order/).

## Доступ и секреты

REST/Swagger доступны локально. На VPS — private network/loopback publishing и
SSH tunnel для demo. PostgreSQL находится только в private Compose network.
Telegram предназначен для private chats; allowlist ограничивает demo и расходы.
Dokploy admin access защищается средствами сервера/Dokploy.

Spring Security вне MVP при закрытом HTTP. Telegram chatId не авторизует HTTP caller.
Полные prompts/provider payload, токены и passwords не записываются в обычные logs.
`.env`, local configuration и dumps исключаются из Git. Backup с conversation data
имеет ограниченный доступ.

## Telegram long polling — PLANNED

Выбрана plain library `com.github.pengrad:java-telegram-bot-api:10.1.0`, независимая
от Boot starter; dependency и transport ещё не добавлены.
[Library](https://github.com/pengrad/java-telegram-bot-api), [Telegram getUpdates](https://core.telegram.org/bots/api#getupdates).

- Long polling и webhook не работают одновременно; active webhook должен отсутствовать.
- ChatId/update identifiers — 64-bit; сообщения одного чата обрабатываются последовательно.
- Update acknowledgment/offset используется из library и проверяется на restart/failure.
- Selection сохраняется после successful sendMessage; failed send её не заменяет.
- Подборка отправляется одной компактной message с escaping; lifecycle: [AI](AI.md#conversationstate-и-selection-context).
- Telegram 429 учитывает retry_after; бесконечная очередь/retry loop не создаётся.

Если library достаточно управляет offset, checkpoint storage не нужен. При доказанной
необходимости допустим один technical lastProcessedUpdateId на bot instance.
Exactly-once delivery и delivery state machine не обещаются; crash может дать повтор.

## Google Places — PLANNED, DEFERRED

Core catalog/search/menu работают без Google key и API. Enrichment допускается только
для details известного конкретного филиала, после проверки billing, доступа, mapping
place ID, доступных полей и требований attribution/Terms/Privacy для реального UI.
При disabled integration ключ не требуется. Live access пока не подтверждён;
DEFERRED не означает доступную или реализованную функцию.

Выбран Place Details API (New), не Google catalog/menu import. Planned field mask:
`id,rating,userRatingCount,currentOpeningHours,googleMapsUri,attributions`; wildcard
не используется. Mask содержит Enterprise fields, поэтому требуется соответствующий
billing/quota budget; тариф и account limits проверяются перед включением.
[Place Details](https://developers.google.com/maps/documentation/places/web-service/place-details),
[pricing](https://developers.google.com/maps/billing-and-pricing/pricing).

Google rating не участвует в ranking; currentOpeningHours не подменяет own weekly
schedule. Поля могут отсутствовать. Google timeout/403/429 дают own details + warning,
без выдуманного рейтинга и скрытой смены search result.

Project contract: persist только проверенный place ID; live content не сохраняется
в own DB, memory, model context или log snapshots. Google Maps attribution и
применимые third-party credits показываются отдельно; ссылка не заменяет attribution.
Требования фактического Telegram UI должны быть проверены до включения enrichment.
[Policies](https://developers.google.com/maps/documentation/places/web-service/policies),
[service terms](https://cloud.google.com/maps-platform/terms/maps-service-terms).

## Dokploy runbook — PLANNED

1. Подтвердить VPS resources/access, установленный Dokploy и место backup.
2. Подключить repository и выбрать version для deployment.
3. Создать Compose project после появления Dockerfile/Compose.
4. Настроить runtime env и explicit Compose environment/env_file mapping.
5. Назначить persistent named PostgreSQL volume; закрыть REST/DB от публичного доступа.
6. Build/deploy, проверить Flyway, API и Telegram smoke без вывода секретов.
7. Проверить ordinary restart/redeploy с сохранением данных и одним poller.

Переменные из Dokploy UI должны быть явно переданы контейнерам через Compose mapping.
[Dokploy Compose](https://docs.dokploy.com/docs/core/docker-compose).
БД не хранится в сменяемом checkout; ordinary redeploy не удаляет volume.
Domain для long polling не требуется.

## Redeploy и backup — PLANNED

Образ может обновляться при сохранении named volume. Проверяются каталог/меню,
planned memory/selection, один poller и совместимость версии с применённой схемой.
Откат image не откатывает Flyway migrations автоматически.

Backup должен сохраняться вне ephemeral container/Git и иметь дату/schema version.
После появления Compose пример для Linux VPS:

```sh
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > restaurantbot-backup.dump
```

Это planned пример, сейчас service/config ещё отсутствуют. Автоматическое расписание
и full restore drill — возможные улучшения; test restore выполняется в отдельной БД.
HA, zero downtime и формальные RPO/RTO вне MVP.

## CI — PLANNED

Возможный pipeline: Maven verify → Docker build. Unit/integration tests не используют
реальные AI/Telegram/Google keys; live AI eval выполняется отдельно.
CI и auto-deploy не настроены. Первичная доставка предполагает ordinary manual deploy
с проверкой persistence; automation может добавляться после этого.
