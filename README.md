# Minsk Restaurant AI Bot

Дипломный Telegram AI-гид по ограниченному каталогу ресторанов Минска.
Поиск и факты принадлежат Java services и собственной БД.

Стек: Java 21, Spring Boot 3.5.16, PostgreSQL, JPA/Hibernate, Flyway,
springdoc 2.9.1, H2 для тестов, JUnit 5. Spring AI 1.1.8 закреплён через BOM;
provider starter и интеграция появятся в TASK-05.

Требования: JDK 21 (`JAVA_HOME`), Maven 3.9.16 через включённый Wrapper.
Первый запуск Wrapper и сборка требуют доступа к Maven Central.

Проверки из корня проекта (PowerShell):

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean verify
```

На Linux/macOS: `./mvnw clean test` и `./mvnw clean verify`.
Test profile использует H2, не требует внешних ключей и PostgreSQL.

Для локального запуска без внешних сервисов:

```powershell
.\mvnw.cmd spring-boot:test-run "-Dspring-boot.run.profiles=test"
```

Swagger: <http://127.0.0.1:8080/swagger-ui/index.html>,
OpenAPI: <http://127.0.0.1:8080/v3/api-docs>.
Default profile получает PostgreSQL connection из `DB_URL`, `DB_USER`, `DB_PASSWORD`;
реальные значения задаются вне Git. Конфигурация доступа: [DEPLOYMENT](docs/DEPLOYMENT.md).

Реализованы каталог первых трёх ресторанов, сохранённые partial menus и Java search.
Flyway V1–V4 создаёт schema и применяет локальные datasets; Hibernate использует `validate`.
Каталог/details доступны на `/api/v1/restaurants`; меню — `/api/v1/restaurants/{id}/menu`
с optional `dishType=PASTA` и `maxItemPriceByn`. Меню содержит coverage=PARTIAL,
source/date и BYN prices. Отсутствие блюда в БД не означает его отсутствия в полном меню.
JSON dataset и controlled import: [DATABASE](docs/DATABASE.md#controlled-seedimport).

`POST /api/v1/recommendations` принимает полный структурированный запрос посещения,
выполняет Java hard filters и возвращает до трёх кандидатов в стабильном порядке.
Пример тела для Swagger (TODAY нормализуется по текущей дате Минска):

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

Доступны ISO date/TODAY/TOMORROW, сегодня и следующие шесть дней, время HH:mm.
Невалидные/неполные criteria дают 400, отсутствие совпадений — 200 с пустым candidates.
Чек умножается на гостей через BigDecimal; меню для поиска не требуется.
Нормализация, фильтры, ranking и response contract: [ARCHITECTURE](docs/ARCHITECTURE.md#реализация-task-04).

H2 tests и PostgreSQL acceptance проверяются отдельно: [BACKLOG](docs/BACKLOG.md).
Основная документация: [Product](docs/PRODUCT.md), [Architecture](docs/ARCHITECTURE.md),
[AI](docs/AI.md), [Database](docs/DATABASE.md), [Backlog](docs/BACKLOG.md),
[TASK-00 evidence](docs/TASK-00-FEASIBILITY.md).
