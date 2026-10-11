# Minsk Restaurant AI Bot

Telegram AI-гид по ограниченному, вручную проверенному каталогу ресторанов Минска.
Помогает подобрать заведение под время посещения, число гостей, общий бюджет и кухню,
затем посмотреть адрес, расписание и сохранённую часть меню.

Production release завершён **2026-10-11**: `POST_V2_V13_PRODUCTION_RELEASE_PASS`.
Развёрнутый commit — `6b2de35`, Flyway — **V13**. Каталог содержит **36 активных
заведений, 23 бренда, 101 позицию меню и 247 недельных интервалов работы**.
Telegram Bot и Mini App доступны в production; HTTPS обслуживает Dokploy/Traefik.
Результаты и границы проверки: [DEPLOYMENT](docs/DEPLOYMENT.md#production-release--2026-10-11).

## Features

- Поиск для Минска: 1–6 гостей, общий бюджет в BYN, сегодня и следующие шесть дней,
  точное время, необязательная кухня и предпочтения по обстановке.
- До трёх вариантов разных брендов: Java выбирает лучший подходящий филиал каждого
  явного `brandKey`. Бюджет, кухня и часы ради заполнения выдачи не ослабляются.
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
В production smoke 2026-10-11 этот запрос вернул **Pizza Tempo — 65.40 BYN,
Da Claudio — 120.00 BYN, Сорренто — 130.00 BYN** на двоих.
«Меню второго» показало сохранённое частичное меню Da Claudio.
Это результат на указанную дату и dataset, а не обещание будущей цены или наличия столика.

«А если нас четверо?» в Telegram меняет гостей, сохраняя остальные критерии.
`/new` очищает разговор, критерии и ordinal references без обращения к модели.
В Mini App критерии задаются заново; разговорного продолжения там нет.
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

LLM понимает запрос и выбирает один из трёх tools: `searchRestaurants`,
`getRestaurantDetails`, `getRestaurantMenu`. Java определяет реальные заведения,
цены, часы, порядок и принадлежность к бренду. У модели нет SQL/JPA-доступа,
инструмента записи или произвольного HTTP-доступа. Structured Output ограничивает
объяснение разрешёнными причинами; Java валидирует план и формирует фактический ответ,
при ошибке объяснения использует fallback. Максимум два model calls и одно tool execution
на запрос; menu/details — один model call.

Поиск: hard filters → совпадения tags → `matchCount DESC`, сумма ASC, ID ASC
→ дедупликация по `brandKey` → максимум три варианта.
`estimatedTotal = estimatedCheckPerGuest × guests`, денежные вычисления — BigDecimal.
Число гостей не проверяет вместимость, часы проверяют прибытие с учётом overnight.
Контракты: [ARCHITECTURE](docs/ARCHITECTURE.md), [AI](docs/AI.md).

## Database

Flyway V1–V13 создаёт каталог, частичное меню, conversation state, JDBC memory и
selection; Hibernate использует `validate`. V12 добавляет обязательный `brand_key`
с явным mapping филиалов, V13 добавляет шесть проверенных заведений и их данные.
Применённые миграции и datasets не переписываются.

`DERIVED` check — ручной ориентир по опубликованным ценам выбранных блюд, а не
расчёт корзины во время поиска. `verifiedAt` — дата проверки источника.
Схема, provenance, история V9/V11 и актуальные данные: [DATABASE](docs/DATABASE.md).

## REST API / Swagger

| Method | Path | Назначение |
|---|---|---|
| GET | `/api/v1/restaurants` | Active catalog, page/size |
| GET | `/api/v1/restaurants/{id}` | Собственные details |
| GET | `/api/v1/restaurants/{id}/menu` | PARTIAL menu, optional dishType/maxItemPriceByn |
| POST | `/api/v1/recommendations` | Полные criteria, до трёх вариантов разных брендов |

Swagger: <http://127.0.0.1:8080/swagger-ui/index.html>.
OpenAPI: <http://127.0.0.1:8080/v3/api-docs>.
Эти endpoints остаются private в production. Публичные Mini App endpoints проверяют
подписанный `X-Telegram-Init-Data` на сервере: [Mini App boundary](docs/ARCHITECTURE.md#v2-04--mini-app-boundary).

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

Зафиксированная V13 acceptance 2026-10-11: `clean test` и `clean verify` — по
**575 tests PASS**; targeted PostgreSQL V13 migration/audit/shared transport suite —
**12 tests PASS**, без failures/errors/skipped. PostgreSQL audit проверил 135 запросов
дважды и подтвердил порядок, hard filters и разнообразие брендов.
Это результаты release acceptance, не новая проверка при редактировании документации.
H2 не заменяет PostgreSQL: [PostgreSQL acceptance](docs/DATABASE.md#postgresql-acceptance).

Opt-in live tests запускаются отдельно, с внешним `AIAI_API_KEY` и выделенной test DB
для stateful eval. Не использовать production DB: harness создаёт синтетический state
и очищает его. Условия и исторический eval: [AI eval](docs/AI.md#ai-eval).

## Deployment

Dockerfile: multi-stage Maven build → Java 21 JRE, non-root runtime.
Compose: app + PostgreSQL 17.10, healthchecks и стабильный named volume.
Production использует Dokploy Compose project; отдельный deployment Patch подключает
app к Traefik и публикует через HTTPS только Mini App/static и Mini App API.
Базовый [compose.yaml](compose.yaml) содержит private backend и loopback host port;
сам по себе public HTTPS route не создаёт. PostgreSQL не имеет host port.

Один bot token — один активный app/poller; webhook не используется.
Production search и follow-up проверены через Telegram, Mini App launch/form/menu/details —
через Telegram Web. Отдельный production AI-запрос Mini App и mobile launch не проверялись.
Runbook, persistence, backup и границы безопасности: [DEPLOYMENT](docs/DEPLOYMENT.md).

## Current MVP limitations

Каталог ограничен 36 заведениями. Меню частичное; поиск не проверяет свободные столики,
наличие блюд, последний горячий заказ или праздничные исключения расписания.
Ориентировочный чек не гарантирует итоговую сумму. Tags не обещают обстановку.
Google Places, рейтинг, booking, оплата, геопоиск, favorites, публичный admin/chat API
и RAG/vector search не реализованы. Телефон и website не сохранены в domain model.
Telegram memory ограничена; exactly-once delivery не гарантируется.

Scope и DoD: [PRODUCT](docs/PRODUCT.md#definition-of-done).
История первоначального MVP и V2 явно отделена от текущего состояния в документах выше.
