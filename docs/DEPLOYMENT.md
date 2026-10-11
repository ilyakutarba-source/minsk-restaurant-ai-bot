# Build, runtime и deployment

## Production release — 2026-10-11

Статус: **POST_V2_V13_PRODUCTION_RELEASE_PASS**. Развёрнутый commit — `6b2de35`,
Flyway V13, Hibernate validate PASS. App и PostgreSQL healthy; каталог содержит
36 активных заведений / 23 бренда / 101 menu item / 247 интервалов.

Production acceptance подтвердила:

- Применение V12/V13 и сохранность прежнего каталога, IDs, conversation state,
  selection и JDBC memory при миграции; persistent volume сохранён.
- Один активный app/poller, отсутствие webhook и getUpdates conflict в проверке.
- HTTPS с валидным сертификатом; Mini App/static доступны, неподписанный,
  tampered и stale initData отклоняются с 401.
- Private REST/Swagger/OpenAPI/admin/actuator не опубликованы через proxy;
  прямые app/DB ports недоступны извне в проверенной конфигурации.
- Реальные `/start`, `/help`, `/new`; AI search и «Меню второго» через Telegram.
  Search вернул три разных бренда, follow-up — меню второй показанной позиции.
- Реальный Mini App launch через menu button «Рестораны» в Telegram Web,
  structured search, menu и details.
- Backup, фактический restore в изолированной PostgreSQL и rehearsal V11→V13,
  повторный запуск без повторного применения миграций.
- Проверку известных runtime secret values в frontend, image metadata/history
  и доступных build/app/DB logs; совпадений не найдено.

Отдельный платный natural-language запрос через production Mini App и launch в
mobile client не проверялись. Natural Mini App API принят отдельно на offline
fixtures и в opt-in live acceptance до production release; [AI eval](AI.md#ai-eval).
Это границы проверки, а не отсутствующие функции.

После release права generated environment file приведены к **0600** и проверены
повторно. На следующих deploy права нужно проверять снова: механизм генерации
файла не исправлялся. Содержимое environment file, backup и private evidence
не являются публичной документацией.

## Текущий запуск — IMPLEMENTED

Default profile требует PostgreSQL и внешние `DB_URL`, `DB_USER`, `DB_PASSWORD`.
Flyway управляет V1–V13; Hibernate использует `ddl-auto=validate`,
`open-in-view=false`. Test profile использует H2 без live keys/calls.
Локальные команды: [README](../README.md#running-locally).

AI включается через `AI_ENABLED=true` и `AIAI_API_KEY`.
Для Telegram transport также нужен непустой `TELEGRAM_BOT_TOKEN`.
Без AI REST и structured Mini App search/details/menu продолжают работать;
подписанный Mini App API всё равно требует server-side bot token.
State/memory используют ту же persistent PostgreSQL: [DATABASE](DATABASE.md#разговор-и-последняя-подборка).
Google key не нужен; Google enrichment не реализован.

## Deployment topology — IMPLEMENTED

```text
GitHub main → Dokploy Docker Compose project → VPS
Telegram private chat ← long polling → app (один Spring Boot instance)
HTTPS → Traefik → app: /miniapp/, assets, /api/miniapp/v1/**
                    app → private backend network → PostgreSQL → named volume
Private REST/Swagger ← host loopback → app
```

[compose.yaml](../compose.yaml) задаёт два services, private backend bridge,
один app, loopback publishing app port и PostgreSQL без host port.
**Public HTTPS находится в deployment configuration Dokploy**, отдельном update
Patch: app подключён к proxy network и имеет restricted Traefik routing labels;
PostgreSQL остаётся только в backend network. Базовый Compose не содержит public
route, TLS labels или shared proxy network. Native Domains не используются.

Действующий proxy разрешает Mini App/static и `/api/miniapp/v1/**`, передаёт
`X-Telegram-Init-Data`, перенаправляет HTTP на HTTPS и `/miniapp` на `/miniapp/`.
Остальные поверхности приложения остаются private; весь app port не публикуется.
Схема авторизации: [ARCHITECTURE](ARCHITECTURE.md#v2-04--mini-app-boundary).

## Environment configuration

| Application variable | Назначение |
|---|---|
| DB_URL / DB_USER / DB_PASSWORD | Внешнее PostgreSQL connection для обычного запуска |
| SERVER_ADDRESS / SERVER_PORT | Default loopback; Compose задаёт 0.0.0.0 / 8080 внутри app |
| AI_ENABLED | Default false; true для runtime AI и Telegram transport |
| AIAI_API_KEY | Server-side AI credential при enabled AI |
| TELEGRAM_BOT_TOKEN | Server-side polling credential и проверка Mini App initData |
| SPRING_PROFILES_ACTIVE | Test включает H2; production не использует test profile |
| SPRING_DATASOURCE_PASSWORD | Пароль выделенной PostgreSQL test DB |

| Compose/Dokploy input | Mapping |
|---|---|
| POSTGRES_DB | Имя БД postgres и часть app DB_URL |
| POSTGRES_USER | postgres user и app DB_USER |
| POSTGRES_PASSWORD | postgres password и app DB_PASSWORD |
| POSTGRES_VOLUME_NAME | Уникальное стабильное имя persistent volume |
| APP_HOST_PORT | Свободный host port, publishing только 127.0.0.1 |
| AI_ENABLED / AIAI_API_KEY / TELEGRAM_BOT_TOKEN | Передаются только app |

Compose явно передаёт нужные variables каждому service, общий service `env_file`
не используется. App DB_URL формируется как `jdbc:postgresql://postgres:5432/${POSTGRES_DB}`.
Отдельные DB_URL/DB_USER/DB_PASSWORD в Dokploy для этого Compose не нужны.
Имена БД/пользователя должны быть простыми identifiers без URL metacharacters.
Изменение initialization env существующего volume не меняет пользователей и пароли БД.

Mini App `restaurant-bot.miniapp.dev-mode=false` по умолчанию и в production.
Search Clock использует Europe/Minsk; APP_TIMEZONE и chat allowlist не реализованы.
AI provider/model и call/timeouts contract: [AI](AI.md#provider-и-совместимость).

## Docker и Compose

Multi-stage [Dockerfile](../Dockerfile) использует Temurin Java 21.0.11 JDK для
Maven Wrapper build и JRE для runtime. Runtime работает с UID/GID 10001:10001;
содержит JAR и curl, без source tree, Maven и environment file.
Docker build пропускает tests; `clean verify` выполняется отдельным gate.

PostgreSQL image — `postgres:17.10-bookworm`. App ждёт `service_healthy` postgres.
DB healthcheck — `pg_isready`; app healthcheck читает
`/api/v1/restaurants?size=1` через HTTP и проверяет реальное DB read, без AI.
Actuator не добавлен. Health failure сам по себе не перезапускает container:
`restart: unless-stopped` действует при завершении процесса.

App использует `init: true`, shutdown grace 45s. Named volume монтируется в
`/var/lib/postgresql/data`; identity задаёт POSTGRES_VOLUME_NAME. Его не менять
при redeploy, не переиспользовать для другого проекта и не открывать одновременно
двумя PostgreSQL containers.

## V2-04 — Mini App local demo и live gate

`/miniapp/`, `index.html`, `style.css`, `app.js` обслуживает тот же Spring Boot server,
без Node service или frontend build. Для отдельного loopback demo без live keys:

```powershell
.\mvnw.cmd spring-boot:test-run '-Dspring-boot.run.profiles=test' '-Dspring-boot.run.arguments=--server.port=18084 --server.address=127.0.0.1 --restaurant-bot.miniapp.dev-mode=true --restaurant-bot.telegram.bot-token= --restaurant-bot.ai.enabled=false'
```

Открыть <http://127.0.0.1:18084/miniapp/>. При конфликте выбрать свободный local port.
Dev flag задаётся явно; test profile сам его не включает. Guard требует dev/test
profile, отсутствие prod/production, loopback binding и loopback request.
Не использовать dev-mode за public proxy: proxy может выглядеть loopback для backend.
В обычном browser без Telegram launch proof API отвечает AUTH_FAILED.

Production launch gate **PASS** через Telegram Web 2026-10-11; отдельный mobile launch
не проверялся. Telegram menu button настроен на HTTPS `/miniapp/`.
Keyboard sendData flow не интегрирован. В staging нужен отдельный bot для polling;
второй poller с production token недопустим.

## V2-05 — release prerequisites

Natural Mini App API использует тот же server-side initData verifier и существующий
AI adapter. При disabled/unavailable AI отвечает controlled 503; форма/details/menu
сохраняют работоспособность. Новых secrets/providers/tools/migrations эта возможность
не вводит. [Stateless contract](ARCHITECTURE.md#v2-04--mini-app-boundary).

История: до V2 release локальная acceptance 2026-10-08 ожидала public HTTPS и live
Telegram launch. Эти условия закрыты subsequent production release; они не являются
текущими блокерами. Для будущего release снова проверять topology, auth, TLS,
secret-file permissions и реальные пользовательские сценарии.

## Доступ и секреты

Public static assets не содержат token/key. `/api/miniapp/**` проверяет HMAC и freshness
raw `X-Telegram-Init-Data`; untrusted userId/query не авторизует caller.
REST `/api/v1/**`, Swagger/OpenAPI остаются private; доступ для demo — host loopback
или собственный SSH tunnel. PostgreSQL не подключается к proxy и не имеет host ports.

Secrets передаются только во внешней runtime среде, вне Git/images/build args/frontend.
Полные prompts, initData, provider payload, private chat IDs и credentials не входят
в обычные logs. Environment file хранится с ограниченным доступом **0600**;
проверять права после каждого deploy. Полные interpolated Compose config/container
inspect могут раскрыть secrets; для syntax check использовать `config --quiet`.
Backup с conversation data хранится приватно вне checkout/Git.

## Telegram long polling — IMPLEMENTED

Pengrad Telegram Bot API 10.1.0 запускает один updates listener через Spring lifecycle
и закрывает client при shutdown. Один bot token — один активный app/poller;
webhook должен отсутствовать. Только private text messages поступают в
ConversationService. Test profile отключает transport.

Ответы — plain text, без parse mode, lossless split до 4096 UTF-16 units с сохранением
surrogate pairs. Части отправляются последовательно; только полный успех разрешает
transcript/selection update. Explicit failed/partial send не повторяется автоматически.
Commands, bounded input/calls и memory contracts: [AI](AI.md).

Transport: connect 2s, read/write 10s, без automatic connection retries/redirects;
library увеличивает read timeout для long polling. 429 не вызывает automatic resend.
Polling errors дают sanitized log и следующую попытку getUpdates по поведению library.
Logs содержат status, counters, catalog candidate IDs и send result, без chatId/payload.

### Update / offset behavior

Listener обрабатывает batch синхронно и возвращает `CONFIRMED_UPDATES_ALL`, включая
ignored updates и failed sends. Затем library выставляет offset последнего update +1;
собственного durable checkpoint нет. Новый poller начинает с default GetUpdates.
Обычный restart сохраняет PostgreSQL criteria/memory/selection. Crash между send,
DB commit и acknowledgment может дать повтор или несогласованную доставку;
exactly-once/outbox/guaranteed delivery не реализованы.
Same-chat serialization: [ARCHITECTURE](ARCHITECTURE.md#telegram-turn-serialization-и-delivery-boundary).

## Dokploy runbook — IMPLEMENTED

1. Проверить candidate build/tests, достаточность ресурсов, свободный loopback app port,
   стабильную identity volume, backup и совместимость forward migrations.
2. Использовать repository branch `main`, `compose.yaml`, Docker Compose mode,
   один app instance. Auto Deploy выключен; production release выполняется вручную.
3. Задать runtime variables через защищённую environment configuration.
   Сохранить restricted HTTPS Patch: app в proxy и backend, postgres только в backend,
   разрешённая поверхность только Mini App/static/API, dev-mode=false.
4. Выполнить один обычный build/deploy с остановкой старого same-token poller перед
   запуском replacement. Проверить app/DB health, Flyway V13 и Hibernate validate.
5. Проверить каталог 36/23/101/247, preservation, volume identity и один poller.
6. Проверить TLS/HTTP redirect, public assets, signed API, unsigned/tampered/stale 401,
   недоступность private endpoints и direct app/DB ports.
7. Проверить environment-file permissions 0600 и отсутствие secret values в assets,
   image metadata/history и доступных logs, не публикуя совпадения/значения.
8. Проверить Telegram commands/search/follow-up и Mini App launch/search/menu/details.

Public hostname, VPS IP, SSH account, Dokploy admin URL и operational resource names
хранятся вне публичной документации.

## Restart, redeploy и persistence acceptance — PASS

Обычный Compose replacement допускает downtime, сохраняет volume и один app instance.
Не использовать rolling/blue-green с двумя same-token pollers. Проверять сохранность
mutable criteria/selection/memory отдельно от seed counts. Restart и recreate прошли
в первоначальной acceptance; V13 release подтвердил preservation при upgrade и
restart изолированного clone без повторного применения миграций.

Не выполнять `down -v`, удаление volume или смену POSTGRES_VOLUME_NAME при redeploy.
Image rollback сам по себе не откатывает Flyway/schema. Предыдущий immutable image
проверен на изолированных V11 и V13 clone без AI/poller; production rollback не выполнялся.

## PostgreSQL backup и restore

Custom-format logical backup включает private conversation state/memory/selection.
Хранить вне checkout/app filesystem с directory 700 и file 600, без public download.
Проверять nonempty archive и `pg_restore --list`; это не заменяет actual restore.
Для согласованного migration baseline остановить app writes, затем восстановить
backup в отдельную PostgreSQL с отдельным volume/network, без production PGDATA,
host ports, live AI и Telegram poller. Сравнить данные и Flyway history/checksums,
применить candidate migrations, проверить validate/reapply и preservation.

В release 2026-10-11 **actual isolated restore/rehearsal PASS**: V11 backup →
отдельный PostgreSQL clone → V12/V13 → restart. Production restore не выполнялся.
Contents, filenames, hashes и private evidence backup не публикуются.
Внешняя копия/retention требуют отдельного operational решения; backup на том же VPS
не защищает от потери диска. HA, zero downtime и RPO/RTO guarantees не реализованы.

## Проверка TASK-13 — 2026-10-06

История первоначального deployment: V9, 10 филиалов / 60 menu items,
private HTTP/DB, Docker Compose/Dokploy, persistent volume, Telegram/AIAI search,
app restart и recreate с follow-up — PASS. Тогда public Mini App route отсутствовал.
Текущее состояние описывает [production release V13](#production-release--2026-10-11).

## Google Places — EXCLUDED FROM CURRENT MVP

Google adapter/mapping/renderer integration не реализованы; Google key, billing
project и calls не требуются. [Scope](PRODUCT.md#google-places--excluded-from-current-mvp).

## CI — PLANNED

CI и auto-deploy не настроены. Возможный отдельный pipeline — Maven verify → Docker
build; обычные tests без live keys, live eval отдельно. Это план, не текущая функция.
