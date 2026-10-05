# Minsk Restaurant AI Bot

Дипломный проект Telegram AI-гида по ограниченному каталогу ресторанов Минска.
Java services и собственная БД определяют факты и результаты поиска; LLM используется
для понимания запроса, выбора инструмента и контролируемого объяснения.

## Features

**IMPLEMENTED:** каталог трёх реальных филиалов, собственные часы и ориентировочные
чеки, partial menus, controlled JSON import, детерминированный Restaurant Search,
четыре REST endpoints и Swagger, Flyway и тесты на H2/PostgreSQL; stateless Spring AI
search tool, Structured Output explanation и Java factual renderer/fallback.

**PLANNED:** Telegram private chats/long polling, menu/details AI tools, разговорные
продолжения, PostgreSQL ChatMemory и последняя показанная подборка,
условное Google Places enrichment, Docker Compose и Dokploy/VPS.

## Example user scenario

Целевой Telegram-сценарий после подключения AI и транспорта:

> Сегодня в 21:00 нас двое, общий бюджет до 150 BYN, итальянская кухня, хочется спокойно.

LLM извлекает критерии, Java выполняет поиск и возвращает до трёх вариантов.
Запрос «Меню второго» будет разрешаться по последней показанной подборке.
Сейчас полный структурированный запрос можно проверить через REST/Swagger без AI.
Пользовательские сценарии и scope: [PRODUCT](docs/PRODUCT.md).

## Tech stack

- Java 21, один Maven module, Maven Wrapper 3.9.16.
- Spring Boot 3.5.16, Spring Data JPA/Hibernate, PostgreSQL, Flyway.
- REST и springdoc 2.9.1; H2 для части tests, JUnit 5/Mockito.
- Spring AI 1.1.8 закреплён BOM; OpenAI provider starter и ChatClient подключены.
- Для AI выбран OpenAI-compatible AIAI.BY и модель `gpt-4.1-mini`.

Точные зависимости: [pom.xml](pom.xml). Конфигурация AI: [AI](docs/AI.md).

## Architecture overview

Один Spring Boot application — modular monolith. REST controllers вызывают
application services; services читают JPA repositories. Будущий Telegram adapter
будет использовать те же services напрямую. У LLM нет SQL/JPA-доступа.
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

Stateless search **IMPLEMENTED**: Spring AI ChatClient использует только read-only
searchRestaurants через существующий Java search. Модель выбирает причины объяснения
через native Structured Output; Java проверяет план и формирует фактические карточки.
Invalid/failed explanation даёт Java fallback. До двух model calls и одного search
на turn. Menu/details tools, Telegram и memory остаются PLANNED: [AI](docs/AI.md).

## Database

Flyway V1–V4 создаёт каталог/меню и применяет локальные datasets; Hibernate использует
`validate`. У данных сохранены source и verifiedAt. Меню всегда PARTIAL; отсутствие
позиции в БД не доказывает её отсутствия в полном меню. Поиск не зависит от MenuItem.
Модель, provenance и import contract: [DATABASE](docs/DATABASE.md).

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
По умолчанию AI отключён; application bean пока не имеет HTTP/Telegram transport.
Секреты хранятся вне Git. Текущие и запланированные переменные: [DEPLOYMENT](docs/DEPLOYMENT.md#environment-configuration).

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
AI tool/plan guards и production SDK через offline loopback HTTP fixtures.
Обычные test/verify не вызывают live provider и не требуют API key.
Отдельный минимальный live smoke (один turn, максимум два платных calls), только
после проверки account budget/quotas и с AIAI_API_KEY в environment:

```powershell
.\mvnw.cmd "-Dtest=AiaiLiveSmokeIT" test
```

H2 не заменяет PostgreSQL acceptance. Команда проверки на отдельной пустой test БД:
[PostgreSQL acceptance](docs/DATABASE.md#postgresql-acceptance).

## Deployment

Docker Compose → Dokploy → VPS — **PLANNED**; Dockerfile/Compose и готового deployment
пока нет. Требования к runtime, сети, persistence и backup: [DEPLOYMENT](docs/DEPLOYMENT.md).

## Current MVP limitations

Каталог содержит три филиала; целевой объём — 10–12. Поддержаны Минск, BYN, 1–6 гостей,
сегодня и следующие шесть дней. REST не разбирает естественный язык.
DERIVED check — собственная ориентировочная оценка, не официальный средний чек.
Наличие столика, блюда, праздничные часы и тишина не гарантируются.
Telegram, ChatMemory, menu/details AI tools и Google ещё не интегрированы;
Google не участвует в ranking. AI search не использует предыдущие сообщения.
Booking, публичный admin/chat API, геопоиск и RAG/vector search вне MVP.
