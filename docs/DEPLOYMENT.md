# Build, runtime и deployment

## Текущий запуск — IMPLEMENTED

Требуются JDK 21, Maven Wrapper 3.9.16 и PostgreSQL для default profile.
Spring Boot получает DB connection из внешней среды, Flyway применяет V1–V9,
Hibernate проверяет схему через `ddl-auto=validate`. HTTP bind — `127.0.0.1`.
Test profile использует H2 без внешних API/ключей и доступен для локального REST demo.
Команды запуска и проверки: [README](../README.md#running-locally).

AI provider подключён для opt-in search: `AI_ENABLED=true` и `AIAI_API_KEY`.
Без enabled AI REST работает без ключа и внешних вызовов; test profile отключает AI.
Production ChatClient/search/explanation и limits: [AI](AI.md).
Telegram private-text long polling подключён к тому же search adapter при непустом
TELEGRAM_BOT_TOKEN и enabled AI. ConversationState и bounded JDBC ChatMemory
используют ту же persistent PostgreSQL; restart приложения восстанавливает критерии и memory.
Схема: [DATABASE](DATABASE.md#разговор-и-последняя-подборка). Новых env variables нет.
Google Places enrichment исключён из текущего MVP; Google key для deployment не требуется.
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
| TELEGRAM_BOT_TOKEN | Runtime bot token; вместе с enabled AI активирует Telegram transport |

DB_URL/DB_USER используются прямо в application.yml. Test connection overrides и
команда проверки: [DATABASE](DATABASE.md#postgresql-acceptance).

Planned integration/container variables; они ещё не связаны с приложением:

| Переменная | Назначение |
|---|---|
| TELEGRAM_ALLOWED_CHAT_IDS | Optional allowlist private chats для demo |
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
Telegram принимает только private text messages. Optional demo allowlist пока PLANNED.
Dokploy admin access защищается средствами сервера/Dokploy.

Spring Security вне MVP при закрытом HTTP. Telegram chatId не авторизует HTTP caller.
Полные prompts/provider payload, токены и passwords не записываются в обычные logs.
`.env`, local configuration и dumps исключаются из Git. Backup с conversation data
имеет ограниченный доступ.

## Telegram long polling — IMPLEMENTED

Выбрана plain library `com.github.pengrad:java-telegram-bot-api:10.1.0`, независимая
от Boot starter; dependency и transport подключены.
[Library](https://github.com/pengrad/java-telegram-bot-api), [Telegram getUpdates](https://core.telegram.org/bots/api#getupdates).

- Long polling и webhook не работают одновременно; active webhook должен отсутствовать.
- Activation: `AI_ENABLED=true`, runtime `AIAI_API_KEY` и непустой `TELEGRAM_BOT_TOKEN`.
  Без token Telegram beans/poller не создаются; REST/Swagger продолжают работать.
  Test profile задаёт empty token и disabled AI, не вызывает Telegram API.
- В локальной IDE environment variables должны быть доступны именно запускаемому
  процессу. Секреты не сохраняются в shared project Run Configuration.
- Один Spring lifecycle component запускает один Pengrad updates listener и снимает
  его при остановке; client закрывается вместе с context. Один token — один instance.
- Принимаются только private text messages; остальные updates безопасно игнорируются.
  ChatId остаётся 64-bit, но не передаётся в AI и не записывается в обычные logs.
- ConversationService использует существующий AI adapter и Java-owned text.
  Plain text без parse mode сохраняет названия/адреса с markup-символами буквально.
  Ответ разделяется без усечения на части ≤4096 UTF-16 units, предпочтительно по строкам,
  с сохранением Unicode surrogate pairs. Все части отправляются последовательно;
  только их полный успех позволяет записать transcript/новую selection. Partial send
  уже может показать часть карточки; прежняя selection/version сохраняется, retry отсутствует.
- Стандартное acknowledgment `CONFIRMED_UPDATES_ALL` и offset принадлежат library.
  Ошибка одного update изолируется; собственных checkpoint, queues или send retries нет.
- Logs содержат только status, call/tool counters, собственные candidate IDs,
  explanationFallback и результат sendMessage. SDK exceptions/payload/URLs не логируются.
  Pengrad fallback в JUL `global` при shutdown также отключён: library stop обнуляет
  exception handler, и transport callback иногда приходит после этого.

### Telegram finishing — IMPLEMENTED

`/start`, `/help`, `/new`, unsupported requests и input/error guards реализованы.
Input/AI budgets: [AI](AI.md#bounded-execution). Same-chat serialization и isolation:
[ARCHITECTURE](ARCHITECTURE.md#telegram-turn-serialization-и-delivery-boundary).
Memory/criteria/selection сохраняются в PostgreSQL; send-aware lifecycle:
[AI](AI.md#conversationstate-и-selection-context).

Pengrad использует explicit OkHttp transport: connect 2s, write/read 10s, без automatic
connection retries/redirects. Library увеличивает read timeout для долгого getUpdates;
sendMessage остаётся с 10s read timeout. API error/null/transport exception дают failed
send, без повторной отправки. `429` возвращает `errorCode` и `parameters.retryAfter()`;
числа безопасно логируются. Автоматического resend после retry_after нет. Library
polling errors вызывают sanitized exception handler, затем следующий getUpdates после
собственной паузы (default 100ms в 10.1.0); SleepUpdatesHandler не применяет retry_after как
отдельную задержку. Это ограничение выбранной library, без новой delivery queue.

### Update / offset behavior

Проверена именно Pengrad 10.1.0: `SleepUpdatesHandler` вызывает listener синхронно,
после возврата `CONFIRMED_UPDATES_ALL` задаёт offset = last updateId + 1 и делает
следующий getUpdates. [Versioned source](https://github.com/pengrad/java-telegram-bot-api/blob/10.1.0/library/src/main/java/com/pengrad/telegrambot/impl/SleepUpdatesHandler.java).
Telegram считает update подтверждённым при запросе с большим offset:
[getUpdates contract](https://core.telegram.org/bots/api#getupdates).

Наш handler подтверждает batch после обработки, включая ignored non-private updates
и explicit send failures. Delivery failure не вызывает автоматическое повторение
update. Следующий poll не начинается, пока batch/turn не завершён. RuntimeException
одного update изолируется; library listener exception без такого catch может прервать
переход к следующему poll. Ошибки VM/crash не перехватываются как normal success.

Loopback smoke на реальных SDK/transport подтвердил batch IDs 41–42 → next offset 43,
продолжение после failed send/update и новый poller без local offset. Library держит
offset в request памяти; новый listener/application начинает с default GetUpdates
без сохранённого checkpoint и запрашивает доступные unconfirmed updates Telegram.
Custom offset storage/state machine отсутствует: стандартного поведения достаточно.

Ordinary application restart сохраняет PostgreSQL conversation state/memory/selection
(отдельный acceptance suite). Crash между обработкой/send/DB commit/следующим getUpdates
может оставить повтор или несогласованную доставку; exactly-once/guaranteed delivery
не обещаются. Live Telegram crash/restart НЕ наблюдался, эта граница UNKNOWN,
описана по contract/source; loopback smoke не выдаётся за live Telegram smoke.

## Google Places — EXCLUDED FROM CURRENT MVP

Текущий MVP deployment не требует Google key, billing project или Google API calls.
Google adapter, known place mapping и renderer integration — NOT IMPLEMENTED;
live Google gate — NOT PASSED. [Решение о scope](PRODUCT.md#google-places--excluded-from-current-mvp).

[Places API (New) требует Google Cloud project с enabled billing](https://developers.google.com/maps/documentation/places/web-service/get-api-key).
При попытке self-service billing setup пользователь обнаружил, что Belarus отсутствует
среди доступных billing countries. Пользователь отказался использовать фиктивные
billing country/address; billing prerequisite недоступен в текущем setup.

`GOOGLE_PLACES_ENABLED` и `GOOGLE_MAPS_API_KEY` относятся только к исключённой
возможности, не связаны с текущим приложением и не требуются для запуска.
Будущий пересмотр потребует отдельного scope decision и проверки billing/API access,
quota/budget, mapping конкретного филиала и attribution/Terms/Privacy реального UI.

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
memory/currentCriteria и planned selection, один poller и совместимость версии с применённой схемой.
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
