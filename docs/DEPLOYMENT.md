# Docker, Dokploy и VPS

Редакция: 2026-10-01. План будущей TASK-13, не выполненный deployment. Dockerfile/Compose/config/CI файлов пока нет. Scope и DoD: [PRODUCT.md](PRODUCT.md); задачи: [BACKLOG.md](BACKLOG.md).

## Target

```text
GitHub repository, selected commit
→ Dokploy Compose project
→ VPS
   ├── app: один Spring Boot instance, Telegram long polling
   └── postgres: persistent named volume
```

Один bot token — один активный poller. Для диплома не нужны replicas, Stack services, отдельный webhook server, Redis/Kafka и публичный HTTP.

## Docker и Compose

Будущий Dockerfile: Maven build → совместимый Java 21 runtime; pinned image tags, непривилегированный runtime user, secrets не включать в build/image.

Compose содержит только app, postgres и persistent DB volume:

- app использует DB hostname postgres, не localhost.
- PostgreSQL healthcheck и ожидание готовности перед app startup.
- Flyway управляет схемой, Hibernate validate проверяет её.
- DB credentials и API tokens передаются runtime environment.
- Dataset входит в image либо загружается документированным controlled import.
- Не ссылаться на абсолютные пути компьютера студента.
- Не публиковать PostgreSQL port в интернет.

Compose поддерживает startup dependency по service_healthy. Само создание container ещё не доказывает готовность БД. [Docker startup order](https://docs.docker.com/compose/how-tos/startup-order/)

Проверка после реализации: чистый запуск, API/Telegram smoke, restart и redeploy с сохранением собственных restaurant/menu и conversation data. Полный restore drill — SHOULD.

## Environment configuration

Имена ниже — contract приложения для будущей реализации; exact starter properties связать явно после version gate.

| Переменная | Назначение |
|---|---|
| TELEGRAM_BOT_TOKEN | Секрет bot token |
| TELEGRAM_ALLOWED_CHAT_IDS | Optional demo allowlist private chats |
| AI_API_KEY | Секрет выбранного provider, если требуется |
| AI_MODEL | Выбранная модель |
| AI_BASE_URL | Optional endpoint выбранного provider |
| GOOGLE_PLACES_ENABLED | Условная integration flag по gate |
| GOOGLE_MAPS_API_KEY | Только для enabled Google |
| DB_URL / DB_USER / DB_PASSWORD | JDBC connection |
| POSTGRES_DB / POSTGRES_USER / POSTGRES_PASSWORD | PostgreSQL container initialization |
| APP_TIMEZONE | Europe/Minsk |
| SPRING_PROFILES_ACTIVE | Согласованный deployment profile |

Не считать AI_* автоматически распознаваемыми Spring AI starter: mapping принадлежит project configuration. DB_PASSWORD и POSTGRES_PASSWORD должны согласовываться при первой настройке. Изменение init env существующего PostgreSQL volume само по себе не меняет пароль/пользователей БД; последующее изменение выполнять документированно.

При GOOGLE_PLACES_ENABLED=false own details/search доступны, и приложение не требует Google key. Эта ветка не выдаёт stub за live enrichment.

Пример файла с пустыми/демонстрационными env placeholders допустим в будущей TASK-13; реальные значения вне Git. .env и backup dumps исключать из repository.

## Доступ и секреты

- REST/Swagger на VPS не публиковать наружу; при demo использовать loopback binding/SSH tunnel.
- PostgreSQL только в private Compose network.
- Telegram private chats; allowlist полезен для ограниченного demo и расходов API.
- Dokploy admin access защитить средствами сервера/Dokploy.
- Tokens не логировать; API payload/prompts по умолчанию целиком не записывать.
- Локальный Swagger открывать только в предусмотренной demo конфигурации.

Spring Security вне MVP при этих условиях. Если появится публичный admin API/accounts, понадобится отдельное scope решение и Security. Сам Telegram chatId не авторизует произвольный HTTP caller.

## Telegram long polling

TASK-00 рекомендует plain library `com.github.pengrad:java-telegram-bot-api:10.1.0`: DTO/API smoke прошёл на Java 21 с обеими Boot линиями. Использовать её update handling/offset; live polling, acknowledgment/restart и failed send проверить в TASK-06/11. [Сравнение и границы проверки](TASK-00-FEASIBILITY.md#4-version-decision).

- getUpdates и webhook не работают одновременно; для long polling не оставлять активный webhook.
- chatId/update identifiers обрабатываются подходящим 64-bit типом.
- Сообщения одного чата обрабатываются последовательно.
- После успешного sendMessage сохраняется новая selection по правилам [AI.md](AI.md#conversationstate-и-selection-context).
- При явном failed send не считать новую подборку показанной.
- Показать список одной компактной message, экранировать форматирование.

Если library корректно управляет offset, отдельный domain checkpoint не нужен. Если собственный offset необходим, достаточно одного технического lastProcessedUpdateId на bot instance с документированным порядком обновления. Нет update history/state machine и обещания exactly-once. Возможность повторов при crash описать как ограничение.

Long polling API offset и правила получения updates: [Telegram Bot API](https://core.telegram.org/bots/api#getupdates). На Telegram 429 учитывать retry_after; retries не превращать в бесконечную очередь.

## Dokploy runbook будущей реализации

1. Подтвердить VPS access/resources и установленный Dokploy.
2. Подключить GitHub repository и выбрать commit.
3. Создать проект в **Compose mode**.
4. Настроить runtime env/secrets; явно передать их контейнерам через environment/env_file будущего Compose.
5. Назначить устойчивый named volume для PostgreSQL.
6. Не публиковать REST/Swagger и DB; app получает outbound доступ к Telegram/provider/Google.
7. Build/deploy, проверить app logs без секретов, Flyway и Telegram smoke.
8. Записать commit, применённые settings и обычный restart/redeploy порядок.

Документация Dokploy предупреждает: env из UI не добавляются в containers автоматически; необходимо явное Compose mapping. [Dokploy Compose](https://docs.dokploy.com/docs/core/docker-compose)

Не хранить БД в каталоге checkout, который может заменяться при redeploy. Не удалять volume при обычном перезапуске. Domain для Telegram long polling не требуется.

## Redeploy и persistence

- Сделать redeploy того же/нового образа, сохранив named volume.
- Проверить restaurant/menu данные и рабочий conversation.
- Проверить restart app: memory и selection доступны из PostgreSQL.
- Убедиться, что второй poller не запущен параллельно с тем же token.
- Если app version откатывается, проверить совместимость с уже применённой Flyway schema. Откат image не откатывает schema автоматически.

Production-grade zero downtime, HA, RPO/RTO и автоматический rollback не нужны.

## Backup

Обязательный результат TASK-13 — документированный простой способ backup, место хранения вне ephemeral container и связь dump с датой/schema version. Автоматическое расписание и full restore drill не являются DoD.

После появления Compose можно документировать пример для Linux VPS:

```sh
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > restaurantbot-backup.dump
```

Это пример будущего runbook, сейчас не выполнялся. Имя service и actual credentials подтвердить в TASK-13. Dump хранить вне Git и ограничивать доступ: он может содержать conversation data.

Restore test — SHOULD: восстановить dump в отдельную тестовую БД и проверить собственные данные и schema. Не выполнять restore поверх рабочей БД как часть обычного redeploy.

## Optional CI

GitHub Actions после готового MVP:

```text
push / PR
→ mvn verify
→ Docker build
```

Unit/integration checks не используют реальные AI/Google/Telegram secrets. Live AI eval запускается отдельно. По необходимости добавить PostgreSQL job; production ML pipeline не нужен.

Сначала deploy выбранного commit вручную через Dokploy. Auto-deploy webhook добавлять только после проверки обычного запуска/redeploy, как отдельный SHOULD. CI не блокирует vertical slice и TASK-01.

## Открытые входные данные

N5 (VPS/resources/access/backup destination), model endpoint и conditional Google access остаются в [BACKLOG.md](BACKLOG.md#open-gates). На этом этапе сервер не выбирался, пакеты не устанавливались и external deployment не выполнялся.

TASK-00 2026-10-02 подтвердил локальную JDK 21 (probes: Temurin 21.0.11) и Maven 3.9.16 вне PATH; PATH Java остаётся 17. Docker CLI есть, daemon недоступен; PostgreSQL client 17.10 есть, server login требует отсутствующего пароля. Данных существующего VPS/Dokploy не найдено. Remote AI не требует GPU; достаточность конкретного сервера остаётся UNKNOWN. [Deployment evidence и условные resource estimates](TASK-00-FEASIBILITY.md#14-vps--deployment-notes).
