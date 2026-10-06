# Build, runtime и deployment

## Текущий запуск — IMPLEMENTED

Требуются JDK 21, Maven Wrapper 3.9.16 и PostgreSQL для default profile.
Spring Boot получает DB connection из внешней среды, Flyway применяет V1–V9,
Hibernate проверяет схему через `ddl-auto=validate`. Обычный запуск bind'ится на
`127.0.0.1`; Compose задаёт `SERVER_ADDRESS=0.0.0.0` внутри контейнера и публикует
порт только на loopback host.
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
Dockerfile и `compose.yaml` реализованы в TASK-13. Candidate опубликован в `origin/main`
обычным push: `5c9b6bb`, затем `3d235a2`; VPS checkout использует `3d235a2`.
2026-10-06 выполнены Dokploy build/deploy, PostgreSQL/HTTP/security acceptance,
реальный Telegram/AIAI search, app restart и Dokploy recreate обоих services.
Telegram ordinal follow-up после redeploy подтверждён пользователем: «Меню второго»
вернуло сохранённое PARTIAL-меню Pizza Tempo, ID 4, из прежней selection.
CI configuration отсутствует. Remote acceptance подтверждена результатами ниже.

### Проверка TASK-13 — 2026-10-06

| Gate | Фактический результат |
|---|---|
| Maven clean verify | PASS: 399 tests, failures/errors/skipped=0, JDK 21 |
| Clean checkout image build | PASS: повторный local gate из чистого archive candidate 5c9b6bb; Dockerfile/Compose/ignore совпадают с рабочими файлами; build через Wrapper |
| Local Docker/Compose | PASS: Engine 29.8.0, Compose 5.5.1; отдельный smoke project/fresh volume |
| Fresh schema/startup | PASS: PostgreSQL 17.10, Flyway V1–V9, Hibernate validate |
| Dataset/HTTP | PASS: 10 active restaurants, 60 menu items; catalog/OpenAPI/Swagger HTTP 200 |
| Image | PASS: Java 21.0.11, UID/GID 10001; jar present, no source/Wrapper/.env в app runtime |
| Local network/mapping | PASS: Docker port mapping и Windows host listener 127.0.0.1:18080 → app:8080; postgres без host ports; explicit credential mapping |
| Local restart/recreate | PASS: оба services healthy; тот же volume, synthetic mutable criteria/selection/memory preserved |
| Candidate security audit | PASS: известные secret values не найдены в tracked files/image metadata/history/build/app/postgres logs; build args и baked runtime credentials отсутствуют |
| VPS access/resources | PASS для измеренного build/runtime smoke: noninteractive SSH, Docker/Compose/Dokploy; sampled RAM/disk ниже |
| Dokploy deployment | PASS: dedicated Docker Compose project, Git main/compose.yaml, checkout 3d235a2; initial deploy и redeploy UI Done |
| Remote schema/runtime | PASS: PostgreSQL 17.10, 9 successful migrations/current V9, Hibernate validate/JPA startup, 10 active restaurants, 60 menu items, оба healthchecks |
| Remote Telegram/AIAI | PASS: пользователь получил подборку; sanitized log status=OK, modelCalls=2, toolExecutions=1, candidateIds=[2,4,5], sendMessage=PASS |
| Remote private exposure | PASS: catalog/OpenAPI/Swagger HTTP 200 через host loopback; app bind 127.0.0.1:18080, postgres bindings empty; external TCP 18080/8080/5432 не установил соединение |
| Remote one poller | PASS: один app container, local bot process count=0, webhook отсутствует, polling failures=0 в smoke |
| Remote restart/persistence | PASS: criteria/selection/memory hashes и volume совпали; пользователь получил «Меню второго» для ID 4 после app restart |
| Remote redeploy/persistence | PASS: оба container IDs изменились через Dokploy --force-recreate; тот же volume и все mutable hashes; пользователь подтвердил меню ID 4 после redeploy |
| Remote secrets/backup | PASS: known values absent Git tracked files/image metadata/history/build/app/postgres logs; dump nonempty, pg_restore --list PASS, directory/file modes 700/600 |

Local container smoke использовал disabled AI, empty external keys/token и synthetic
mutable rows, без paid/provider/Telegram calls. После проверки удалены только
его disposable containers/network/volume/image и temporary credential file. Remote Telegram ordinal follow-up и actual
Dokploy redeploy обязательны для завершённой deployment acceptance. Все local и remote
Acceptance Criteria TASK-13 проверены.
Public docs содержат только sanitized summary.
Повторный LOCAL DEPLOYMENT этап завершён по запросу пользователя: `clean verify`
399 PASS, новый fresh PostgreSQL volume, actual image build/startup/health/HTTP/security
и targeted cleanup PASS. AI/Telegram были отключены, реальные provider calls не выполнялись.
Runtime contract перепроверен по application.yml и Java configuration: default HTTP
address 127.0.0.1, default port 8080; Compose override address 0.0.0.0 внутри app.
Allowlist не поддерживается, healthcheck использует existing catalog GET с чтением БД.
Source/pom/старые Flyway migrations и AI contract не изменены относительно TASK-12;
tools ровно три, Google config не добавлен. Remote deployment не запускался в local этапе;
результаты remote этапа приведены в таблице отдельно.

## Deployment topology — IMPLEMENTED

```text
GitHub repository → Dokploy Compose project → VPS
                                            ├── app: один Spring Boot instance
                                            └── postgres: persistent named volume
```

Runtime — Java 21. Выбран remote AIAI API, локальная модель/GPU не требуется.
CPU/RAM/disk учитывают application, PostgreSQL, image build, Dokploy overhead и backup
storage. Фактический read-only audit и последующий измеренный build/runtime smoke
приведены ниже. Resource verdict PASS относится к проверенной нагрузке этого MVP.
Один bot token — один активный poller. Microservices, replicas и публичный webhook
не входят в MVP. Scope: [PRODUCT](PRODUCT.md).

### VPS resource/access audit — 2026-10-06

| Проверка | Наблюдение |
|---|---|
| SSH | Noninteractive key-based access PASS через настроенный локальный alias |
| ОС | Ubuntu 24.04.5 LTS, Linux 6.8.0-139-generic |
| CPU | 2 logical CPUs; load average 0.01/0.06/0.02, CPU idle 99–100% в короткой выборке |
| RAM | 3.82 GiB total, 1.90 GiB available на момент audit; swap отсутствует |
| Disk / backup filesystem | 77.40 GiB total, 65.77 GiB available, 12% used; inode usage 6% |
| Docker | Client/Engine 28.5.0, overlay2; daemon доступен через текущий SSH account |
| Compose | 5.5.1 |
| Dokploy | Container image v0.30.6, healthy; localhost HTTP 200. Authenticated UI/API session не проверялась |
| Existing inventory | 5 running containers, 3 named volumes, 6 networks; Dokploy и другой учебный проект |
| Existing memory use | Dokploy около 929 MiB; остальные четыре containers суммарно около 414 MiB в одной выборке |
| Existing memory limits | У всех пяти containers HostConfig.Memory=0 / MemorySwap=0: явные container limits отсутствуют |
| Docker storage | Images около 4.45 GB, volumes около 118 MB; build cache отсутствует |
| Target inventory | Minsk Restaurant Bot containers/volumes/networks отсутствуют |
| App port candidate | 18080 свободен на момент `ss -lnt`; повторно проверить перед deploy, host bind только loopback |
| Backup permissions | User-owned home filesystem доступен для записи и имеет тот же запас места; `/var/backups` напрямую недоступен; noninteractive sudo недоступен |

**Resource verdict после read-only audit: PARTIAL.** Доступ/CPU/disk/Docker/Compose/Dokploy подтверждены.
Запас RAM выглядит достаточным для одного runtime app + PostgreSQL с учётом local
smoke measurements, но build peak, совместная нагрузка с существующим проектом и
remote runtime тогда ещё не были измерены. Отсутствие swap и container memory limits учитывается
перед build/deploy; факт healthy Dokploy не закрывает эти acceptance gates.

Listeners: TCP 22/80/443/3000/2377/7946 на all interfaces, DNS 53 на loopback.
80/443/3000 соответствуют существующим Traefik/Dokploy, 2377/7946 — активному Docker
Swarm. Публичная достижимость через host/provider firewall этим audit не проверялась.
PostgreSQL 5432 и application 8080/18080 host listeners отсутствуют. Новый проект
использует Compose mode и отдельную bridge network; существующий Swarm не меняется.

**Текущий resource verdict: PASS для TASK-13 smoke.** Последующие actual Dokploy build,
startup, Telegram/AIAI turns, app restart и recreate двух services прошли без OOM/failure.
В 120 samples с интервалом 5s minimum MemAvailable около 1.34 GiB, maximum load1 1.59,
minimum disk available около 64.17 GiB. Runtime sample: app около 350 MiB,
PostgreSQL около 93 MiB. Это sampled headroom, а не измерение мгновенного peak.
Пять существующих unrelated container IDs и uptime сохранены.

Audit не создавал/останавливал/удалял containers, volumes или networks, не менял
firewall/SSH/Dokploy configuration и не выводил secrets/environment values. Private operational
details и inventory evidence остаются local-only. SSH доступ подтверждён;
candidate опубликован после прямого разрешения пользователя на точный repository.

## Environment configuration

Текущая application configuration:

| Переменная | Назначение |
|---|---|
| DB_URL | PostgreSQL JDBC URL для default profile |
| DB_USER | Имя пользователя DB; именно DB_USER, не DB_USERNAME |
| DB_PASSWORD | Пароль DB из внешней среды |
| SERVER_ADDRESS / SERVER_PORT | Compose: 0.0.0.0 / 8080 внутри app; default запуск остаётся loopback |
| SPRING_PROFILES_ACTIVE | Только обычный запуск; test включает H2, production Compose не передаёт test profile |
| SPRING_DATASOURCE_PASSWORD | Пароль отдельной test DB при PostgreSQL tests |
| AI_ENABLED | Opt-in production AI search; default false |
| AIAI_API_KEY | Runtime credential AIAI.BY при AI_ENABLED=true |
| TELEGRAM_BOT_TOKEN | Runtime bot token; вместе с enabled AI активирует Telegram transport |

DB_URL/DB_USER используются прямо в application.yml. Test connection overrides и
команда проверки: [DATABASE](DATABASE.md#postgresql-acceptance).

Compose явно передаёт перечисленные runtime variables; общий `env_file` в services
не используется, чтобы не передавать каждому контейнеру все секреты Dokploy.

| Вход Compose/Dokploy | Mapping |
|---|---|
| POSTGRES_DB | postgres POSTGRES_DB; database name в app DB_URL |
| POSTGRES_USER | postgres POSTGRES_USER; app DB_USER |
| POSTGRES_PASSWORD | postgres POSTGRES_PASSWORD; app DB_PASSWORD — один source of truth |
| POSTGRES_VOLUME_NAME | Обязательное уникальное стабильное имя named volume |
| APP_HOST_PORT | Обязательный свободный host port, выбранный после `ss -lnt`; bind всегда 127.0.0.1 |
| AI_ENABLED | app AI_ENABLED; default false для local smoke, remote должен быть true |
| AIAI_API_KEY | Только app; runtime secret для remote AI |
| TELEGRAM_BOT_TOKEN | Только app; runtime secret для remote polling |

DB_URL внутри app всегда `jdbc:postgresql://postgres:5432/${POSTGRES_DB}`.
Отдельные DB_URL/DB_USER/DB_PASSWORD в Dokploy UI не нужны: Compose выполняет mapping.
POSTGRES_DB/USER выбираются как простые PostgreSQL identifiers без URL metacharacters.
TELEGRAM_ALLOWED_CHAT_IDS и APP_TIMEZONE приложением не поддерживаются и не передаются.
Search Clock уже использует Europe/Minsk. Google variables не нужны.

AiConfiguration создаёт production model явно: key из `restaurant-bot.ai.api-key`
через AIAI_API_KEY, base-url `https://api.aiai.by`, completions-path
`/v1/chat/completions`, model `gpt-4.1-mini`. Provider auto-models и memory auto-config
отключены. В текущем application.yml `restaurant-bot.ai.explanation-enabled=true`;
remote search использовал два model calls. Значение false отключает optional second call.
Origin base и versioned path не должны дублировать `/v1`. Retry/transport timeouts и
исполнение в пределах turn budget принадлежат [AI](AI.md#bounded-execution).

DB_PASSWORD и POSTGRES_PASSWORD должны согласовываться при первой инициализации
контейнера. Изменение init env существующего volume не меняет пароль/пользователей
PostgreSQL автоматически. Последующие изменения выполняются отдельно.
Секретные значения не помещаются в docs, Git, build args, image или обычные logs.

## Docker и Compose — IMPLEMENTED files; runtime acceptance separate

- Multi-stage Dockerfile: `eclipse-temurin:21.0.11_10-jdk-noble` →
  `eclipse-temurin:21.0.11_10-jre-noble`. Существование exact tags проверено registry
  manifest inspection. Build использует Maven Wrapper 3.9.16: `sh ./mvnw -B -ntp -DskipTests package`.
  Перед candidate publication обязателен отдельный `mvnw clean verify`.
- Runtime: JRE 21, application jar, curl для healthcheck; UID/GID 10001:10001.
  Maven/source tree и `.env` в runtime не копируются; secrets отсутствуют в Dockerfile ARG/ENV.
  Version tags controlled, не `latest`; OS packages могут обновляться при rebuild.
- `.dockerignore` допускает только Wrapper/pom/src/build definitions и дополнительно
  исключает local config, logs, credentials и dumps даже под src. Git/IDE/probes/docs/target
  не входят в build context.
- Ровно два services: app и `postgres:17.10-bookworm`; один app (`scale: 1`).
  Docker Compose mode, без Swarm Stack/replicas/Traefik route/shared dokploy-network.
- Project-scoped bridge допускает исходящие Telegram/AIAI calls. DB host port отсутствует.
  App слушает 8080 внутри контейнера; host publishing только `127.0.0.1:${APP_HOST_PORT}`.
- PostgreSQL healthcheck — `pg_isready`; app ждёт `service_healthy`.
  App healthcheck — HTTP GET `/api/v1/restaurants?size=1` через curl с timeout 5s.
  Он проверяет готовность HTTP и чтение БД; внешние AI/Telegram API им не проверяются.
  Отдельный Actuator не добавлен.
- `restart: unless-stopped` у обоих services; app `init: true`, shutdown grace 45s.
  Health failure сам по себе не перезапускает контейнер: Docker restart policy работает
  при выходе процесса. Postgres readiness gate относится к первоначальному startup.
- Volume `postgres_data` монтируется в `/var/lib/postgresql/data`; его Docker identity
  берётся из обязательного POSTGRES_VOLUME_NAME и не зависит от checkout/project name.
  Имя уникально для этого deployment; не переиспользовать volume другого проекта и не
  менять имя при redeploy. Два PostgreSQL containers не должны открывать его одновременно.
- Flyway V1–V9 управляет fresh schema/datasets без внешних calls; Hibernate validate.

Local и remote container smoke подтверждены. Remote restart/redeploy сравнивают
реальные mutable criteria/selection/memory hashes, а не только повторное появление seed data.
[Compose startup order](https://docs.docker.com/compose/how-tos/startup-order/),
[port publishing](https://docs.docker.com/engine/network/port-publishing/),
[Temurin image source](https://github.com/adoptium/containers),
[PostgreSQL image](https://github.com/docker-library/postgres).

## Доступ и секреты

REST/Swagger доступны локально. На VPS — private network/loopback publishing и
SSH tunnel для demo. PostgreSQL находится только в private Compose network.
Telegram принимает только private text messages. Demo allowlist не реализован.
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

## Dokploy runbook — IMPLEMENTED

1. Через уже настроенный SSH key-based доступ выполнить read-only audit:
   `uname -a`, `nproc`, `free -h`, `df -h`, `docker version`, `docker compose version`,
   `docker info`. Подтвердить работающий Dokploy и резерв для image build/backup.
   Секреты/полный container inspect не выводить. Проверить `ss -lnt`, выбрать свободный
   APP_HOST_PORT; не фиксировать VPS port до проверки. Проверить Docker host boundary:
   версии Engine до 28 имеют известную localhost publishing границу для соседей L2;
   достаточность конкретной конфигурации требует проверки, а не предположения.
2. После local verify/container/security gates опубликовать deployment candidate.
   Candidate push не означает TASK-13 DONE. Repository:
   `https://github.com/ilyakutarba-source/minsk-restaurant-ai-bot`, branch `main`,
   Compose path `compose.yaml`, Dockerfile в корне.
3. Создать отдельный Dokploy project `minsk-restaurant-ai-bot` и Compose service
   в **Docker Compose** mode. Записать фактическое generated Compose project name
   локально; использовать его в operational commands. Не использовать Stack/Swarm,
   auto-deploy/scaling, Domains, Traefik labels или shared proxy network. Проверить
   rendered topology без interpolated secrets; isolated deployment не должен менять
   loopback bind, fixed volume identity или добавлять публичный route.
   Для Dokploy v0.30.6 в Advanced → Networks включить **Detach dokploy-network**
   у app и postgres; дополнительные networks не выбирать. Deprecated isolated
   deployment toggle оставить выключенным. В TASK-13 runtime inspection подтвердил
   только project-scoped backend network у обоих services.
4. Dokploy Environment: задать POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD,
   POSTGRES_VOLUME_NAME, APP_HOST_PORT, `AI_ENABLED=true`, AIAI_API_KEY и
   TELEGRAM_BOT_TOKEN. Secret values вводятся через UI/runtime, вне Git/build args.
   Dokploy сохраняет `.env` для interpolation; значения UI не становятся container
   environment автоматически. Явный mapping находится в `compose.yaml`.
5. До запуска remote poller остановить локальный запуск с тем же token. На VPS должен
   быть ровно один app/poller. Webhook отсутствует; domain не требуется.
6. Выполнить обычный Build/Deploy. Проверить оба healthchecks, Flyway current V9,
   Hibernate validate, 10 active restaurants и 60 menu items. First deployment использует
   отдельный fresh volume; local development DB/dump не переносится.
7. На host проверить `ss -lntp` и только targeted port mappings: app host bind
   `127.0.0.1:<chosen-port>`, postgres bindings отсутствуют. Проверить локально catalog,
   `/v3/api-docs` и Swagger. Не выводить `docker compose config` или `docker inspect`
   целиком: они содержат runtime credentials. Для syntax audit: `config --quiet`;
   env audit проверяет только names/presence, logs audit не публикует matches.
   После deploy/redeploy проверить permissions generated `.env`: в TASK-13 установлен
   mode 600 с владельцем root. `config --quiet` выполнен от владельца файла в Dokploy
   container; обычный SSH account не получает read access к этому файлу.
8. Пользователь отправляет реальный запрос в Telegram: «Сегодня в 21:00 нас двое,
   бюджет 150 BYN, хочется итальянскую кухню». PASS требует реально полученного
   ответа и sanitized counters/tools, подтверждающих VPS → AIAI → Java → PostgreSQL.
9. Выполнить restart и ordinary Dokploy redeploy по сценарию ниже; подтвердить mutable
   state, один app/poller и неизменный volume. Создать безопасный test backup, если доступ
   позволяет; проверить nonempty file и `pg_restore --list`.

Другие VPS deployments/volumes/firewall/SSH configuration не меняются. Prune/reset/
flush commands не нужны. [Dokploy Compose/environment/volumes](https://docs.dokploy.com/docs/core/docker-compose).

### Private REST demo

Из local shell создать tunnel через собственный настроенный SSH alias:

```sh
ssh -N -L 127.0.0.1:18080:127.0.0.1:<APP_HOST_PORT> <VPS_ALIAS>
```

Локально открыть `http://127.0.0.1:18080/swagger-ui/index.html` либо
`http://127.0.0.1:18080/api/v1/restaurants`. Local port 18080 — пример; сначала
проверить, что он свободен. Реальные IP/login/admin URL не помещаются в public docs.

## Restart, redeploy и persistence acceptance — PASS

В Dokploy используется обычный Compose recreate с одной app instance; downtime
допустим. Не включать rolling/blue-green/дублирование poller. `restart app` выполняется
в контексте фактического проекта, затем обычный Dokploy Redeploy. Не применять `down -v`,
volume remove, пересоздание БД или изменение POSTGRES_VOLUME_NAME.
Чтобы unchanged image всё равно прошёл реальный recreate, в Advanced → Command
сохранён полный command (Dokploy добавляет начальное `docker`):

```sh
compose -p <actual-compose-project> --env-file .env -f compose.yaml up -d --build --remove-orphans --force-recreate
```

Это ordinary Compose replacement двух services; app scale остаётся 1.

После успешного remote search зафиксировать только агрегаты/boolean evidence:
currentCriteria существует, selection содержит показанный порядок ID, memory непуста.
Не публиковать chatId/текст memory. Зафиксировать volume identity, schema V9 и app count.
После restart и отдельно после redeploy проверить те же state/volume, healthchecks,
Flyway validate, один poller; пользователь отправляет «Меню второго» и получает меню
правильного ранее показанного restaurantId. Наличие только 10 seed restaurants не
доказывает persistence. TASK-13 real baseline после search: 1 criteria row,
3 selection rows, 2 memory rows; второй selection restaurantId=4. После app restart
все hashes совпали; пользователь получил меню ID 4. После этого memory rows=4.
Dokploy redeploy пересоздал оба containers; volume, schema V9 и все hashes снова
совпали с обновлённым baseline. После redeploy пользователь в том же чате получил
корректное сохранённое PARTIAL-меню ID 4 на «Меню второго». Sanitized log:
status=OK, modelCalls=1, toolExecutions=1, sendMessage=PASS. После этого turn
memory rows=6, criteria и selection hashes прежние. Это подтверждает mutable state
persistence для проверенного сценария; не является crash/recovery или full restore test.
Откат image не откатывает Flyway migrations автоматически. Runtime secrets в уже
инициализированном PostgreSQL volume не меняют DB credentials автоматически.

## PostgreSQL backup — remote smoke PASS

Логический custom-format dump включает conversation state/memory/selection и является
приватными данными. Хранить вне app container/checkout/Git, например в выделенном
`$HOME/backups/minsk-restaurant-ai-bot`; directory 700, files 600, доступ только у
operational owner. Container определяется по фактическому Compose project name и
service label; читать root-owned `.env` для backup не требуется. У owner должны быть
Docker access и права создать этот каталог; не менять
права чужих backup directories. Шаблон для Linux VPS:

```sh
umask 077
compose_project='replace-with-actual-dokploy-project-name'
pg_container=$(docker ps -q \
  --filter "label=com.docker.compose.project=$compose_project" \
  --filter 'label=com.docker.compose.service=postgres')
test -n "$pg_container" && test "$(printf '%s\n' "$pg_container" | wc -l)" -eq 1 || exit 1
backup_dir="$HOME/backups/minsk-restaurant-ai-bot"
install -d -m 700 "$backup_dir"
dump="$backup_dir/restaurantbot-$(date -u +%Y%m%dT%H%M%SZ).dump"
docker exec "$pg_container" \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$dump.partial" &&
test -s "$dump.partial" &&
docker exec -i "$pg_container" \
  pg_restore --list < "$dump.partial" > /dev/null &&
mv "$dump.partial" "$dump"
```

Password не передаётся в command line; exec использует runtime DB initialization
variables. В shell не включать `set -x`. `.partial` при ошибке не считается готовым
backup. Проверить итоговые size/permissions; dump/list contents не выводить и не
создавать public download URL. Archive validation не доказывает полный restore.
Здесь имя файла датировано; рядом приватно записать schema version V9 и deployment
revision без credentials. Read-only audit подтвердил writable user-owned home filesystem
и запас 65.77 GiB до build. В remote smoke создан private user-owned directory mode 700,
custom-format dump размером 24990 bytes mode 600; `pg_restore --list` PASS.
Рядом приватно записаны schema V9 и deployment revision 3d235a2.
Dump содержит реальные conversation data; contents не выводились и не публиковались.
Full restore не выполнялся; `/var/backups` не использовался.

Retention и внешняя копия требуют отдельного operational решения: один dump на том же
VPS не защищает от потери диска, место контролируется перед каждой копией. Автоматический
schedule/full restore drill, HA, zero downtime и RPO/RTO guarantees вне TASK-13.

## CI — PLANNED

Возможный pipeline: Maven verify → Docker build. Unit/integration tests не используют
реальные AI/Telegram/Google keys; live AI eval выполняется отдельно.
CI и auto-deploy не настроены. Первичная доставка предполагает ordinary manual deploy
с проверкой persistence; automation может добавляться после этого.
