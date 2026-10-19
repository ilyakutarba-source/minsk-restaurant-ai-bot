# Minsk Restaurant AI Bot

Telegram AI-гид по ограниченному, вручную проверенному каталогу ресторанов Минска.
Помогает подобрать заведение под время посещения, число гостей, общий бюджет и кухню,
затем посмотреть адрес, расписание и сохранённую часть меню.

Telegram Bot и Mini App развёрнуты; [результаты production release 2026-10-11](docs/DEPLOYMENT.md#production-release--2026-10-11).

## Features

- Поиск для Минска: 1–6 гостей, общий бюджет в BYN, сегодня и следующие шесть дней,
  точное время, необязательная кухня и предпочтения по обстановке.
- До трёх вариантов разных брендов с учётом бюджета, кухни и часов работы.
- Telegram private chat: запрос обычным языком, уточнение критериев, продолжение
  поиска, «Меню второго», «Есть паста у первого?», «До скольки последний?».
- `/start`, `/help`, `/new`; bounded PostgreSQL memory и последняя показанная
  подборка сохраняются между обычными перезапусками.
- Telegram Mini App `/miniapp/`: форма поиска и отдельный поиск обычным языком,
  карточки, «Подробнее» и «Меню», возврат назад, loading/empty/error states и тема Telegram.
  Запросы Mini App независимы; для AI-поиска каждый раз нужны полные критерии.
  Форма, детали и меню работают без AI.
- Собственные источники и даты проверки; меню всегда `PARTIAL`, у части заведений
  сохранённого меню нет. Это не мешает поиску.
- REST API и Swagger для локального запуска и приватной проверки backend.

## Example user scenario

> Сегодня в 21:00 нас двое, общий бюджет 150 BYN, итальянская кухня.

AI извлекает критерии, Java проверяет их и ищет в собственной PostgreSQL.
«Меню второго» показывает сохранённое меню второго варианта, а «А если нас четверо?»
продолжает поиск с изменённым числом гостей. `/new` начинает новый разговор.
Сценарии и ограничения: [PRODUCT](docs/PRODUCT.md).

## Tech stack

- Java 21, один Maven module, Maven Wrapper 3.9.16.
- Spring Boot 3.5.16, Spring Data JPA/Hibernate, PostgreSQL 17.10, Flyway.
- REST, springdoc/Swagger 2.9.1; JUnit 5/Mockito, H2 для части тестов.
- Spring AI 1.1.8, OpenAI-compatible API AIAI.BY, модель `gpt-4.1-mini`.
- Pengrad Telegram Bot API 10.1.0, private chats и long polling.
- Mini App: HTML/CSS/vanilla JavaScript и Telegram Web App SDK; тот же Spring Boot server.
- Docker Compose, Dokploy/Traefik, VPS, persistent PostgreSQL volume.

Точные зависимости: [pom.xml](pom.xml). Build/runtime: [DEPLOYMENT](docs/DEPLOYMENT.md).

## Architecture overview

Один Spring Boot application — modular monolith с пакетами `catalog`, `search`,
`api`, `ai`, `conversation`, `telegram`, `miniapp`.

```text
Telegram → ConversationService → Spring AI adapter → validated read-only tool
Mini App form / private REST ──────────────────────→ Java services → PostgreSQL
Mini App natural search → stateless Spring AI ─────→ Java services
trusted DTO → Java factual renderer / Mini App mapper → user
```

AI понимает запрос; Java services определяют факты и порядок результатов из собственной БД.
Границы компонентов, поиск и REST contracts: [ARCHITECTURE](docs/ARCHITECTURE.md).
Инструменты, ограничения модели и память: [AI](docs/AI.md).

## Database

PostgreSQL хранит каталог, частичное меню и состояние разговора; схемой управляет Flyway.
Модель, источники данных и миграции: [DATABASE](docs/DATABASE.md).

## REST API / Swagger

Swagger: <http://127.0.0.1:8080/swagger-ui/index.html>.
OpenAPI: <http://127.0.0.1:8080/v3/api-docs>.
REST/Swagger остаются private в production.
Endpoints и HTTP contracts: [ARCHITECTURE](docs/ARCHITECTURE.md#rest-api).

Пример `POST /api/v1/recommendations`:

```json
{
  "guests": 2,
  "totalBudgetByn": 150.00,
  "date": "TODAY",
  "time": "21:00",
  "cuisine": "ITALIAN",
  "preferredTags": []
}
```

## Configuration

Default profile требует `DB_URL`, `DB_USER`, `DB_PASSWORD` во внешней среде.
По умолчанию AI отключён. Для AI нужны `AI_ENABLED=true`, `AIAI_API_KEY`;
для Telegram long polling — также `TELEGRAM_BOT_TOKEN`.
Mini App проверяет подпись с помощью server-side bot token; dev-mode по умолчанию false.
Secrets не хранятся в Git, образах или frontend.
Переменные и Compose mapping: [DEPLOYMENT](docs/DEPLOYMENT.md#environment-configuration).

## Running locally

Требуется JDK 21 в `JAVA_HOME`; первая сборка требует доступа к Maven Central.
H2 demo без ключей, из корня проекта в PowerShell:

```powershell
.\mvnw.cmd spring-boot:test-run "-Dspring-boot.run.profiles=test"
```

Для PostgreSQL задайте DB-переменные во внешней среде:

```powershell
.\mvnw.cmd spring-boot:run
```

На Linux/macOS используйте `./mvnw`. Локальное Mini App demo с явным dev-mode:
[DEPLOYMENT](docs/DEPLOYMENT.md#v2-04--mini-app-local-demo-и-live-gate).
Для Compose заполните отдельный local env file вне Git по таблице deployment;
для smoke оставьте AI выключенным и bot token пустым:

```powershell
$localEnvFile = 'C:\path\outside-repository\local.env'
docker compose --env-file $localEnvFile config --quiet
docker compose --env-file $localEnvFile up -d --build
docker compose --env-file $localEnvFile ps
```

## Tests

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean verify
```

Обычные tests используют offline AI/Telegram fixtures и не требуют live credentials.
Они проверяют каталог/import/rollback, search и разные бренды, REST, tool/plan guards,
criteria continuation, references, memory/selection, restart и ошибки доставки,
Mini App API/auth и оба способа поиска.

H2 не заменяет PostgreSQL. Команда отдельной проверки и зафиксированные результаты:
[PostgreSQL acceptance](docs/DATABASE.md#postgresql-acceptance).

Opt-in live tests запускаются отдельно, с внешним `AIAI_API_KEY` и выделенной test DB
для stateful eval. Не использовать production DB: harness создаёт синтетический state
и очищает его. Условия и исторический eval: [AI eval](docs/AI.md#ai-eval).

## Deployment

Docker Compose запускает приложение и PostgreSQL; production размещён в Dokploy на VPS.
Настройка HTTPS, ограничения polling, runbook и backup: [DEPLOYMENT](docs/DEPLOYMENT.md).

## Current MVP limitations

Каталог ограничен, меню частичное, чек ориентировочный. Поиск не подтверждает
свободный столик или наличие блюда. Пользовательские ограничения:
[PRODUCT](docs/PRODUCT.md#пользовательское-поведение-и-ограничения).
