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

Статус: TASK-01 DONE; доменная схема начинается в TASK-02.
Flyway включён без миграций; Hibernate уже использует `validate`.
H2 smoke не подтверждает PostgreSQL acceptance.
Основная документация: [Product](docs/PRODUCT.md), [Architecture](docs/ARCHITECTURE.md),
[AI](docs/AI.md), [Database](docs/DATABASE.md), [Backlog](docs/BACKLOG.md),
[TASK-00 evidence](docs/TASK-00-FEASIBILITY.md).
