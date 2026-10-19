# POST-V2-01 — Brand diversity acceptance

Дата: 2026-10-08, Europe/Minsk. Baseline: V2 `FULL_V2_RELEASE_PASS`, main `1b73be8`.
Ветка: `codex/post-v2-01-brand-diversity`. Проверки выполнялись локально;
production DB/VPS не подключались, merge/push/deploy не выполнялись.

## Изменения

- Обязательный внутренний `Restaurant.brandKey`, явное назначение и canonical validation.
- V12 с точным mapping 30 существующих заведений → 17 брендов; NOT NULL/CHECK, без default.
- Общий search service выбирает разные бренды после прежних hard filters/ranking.
- Строгий controlled import принимает явный mapping batch; исторический V11 path сохранён.
- V1–V11, immutable seeds и ORDER comparator не изменены, проверено diff against baseline.
- AI tools/prompts/integration, Telegram и Mini App production-код/API не изменены.

Правила поиска: [ARCHITECTURE](../ARCHITECTURE.md#deterministic-ranking).
Модель, governance брендов и import compatibility:
[DATABASE](../DATABASE.md#post-v2-01--brand-membership).
POST-V2-02 не выполнялся.

## Проверки

| Проверка | Результат |
|---|---|
| Полный финальный `clean test`, H2 | PASS: 566 tests, failures/errors/skipped=0 |
| Полный `clean verify`, H2 | PASS: 566 tests, failures/errors/skipped=0; JAR assembled |
| Полный PostgreSQL regression suite | PASS: 565 tests, failures/errors/skipped=0 |
| Fresh PostgreSQL V1→V12 | PASS |
| Upgrade PostgreSQL V11→V12 | PASS; выполнена только V12 |
| Hibernate validate на fresh и upgraded schema | PASS |
| Flyway validate / reapply | PASS / 0 migrations |
| Точный mapping каждого seedKey | PASS: 30 заведений / 17 брендов |
| Все прежние значения восьми таблиц до/после upgrade | PASS |
| Неизвестное заведение без mapping | V12 отклонена; PostgreSQL откатил всю V12, осталась V11 |
| DB constraints / explicit new import | PASS |
| Telegram ↔ structured Mini App ↔ natural Mini App | PASS: одинаковые ID, порядок и суммы для 1/2/3 брендов |
| Historical migrations/seeds и comparator | Unchanged |

PostgreSQL 17.10: отдельный созданный для задачи local cluster, loopback `127.0.0.1:55436`,
пустая БД `post_v2_01_final`. Bootstrap H2-specific `RestaurantBotApplicationTest`
исключён из PostgreSQL run, остальные tests выполнены без skipped.
Migration acceptance использует отдельные автоматически созданные schemas;
после suite их осталось 0. Финальный каталог: 30 active / 17 brands / 89 menu items /
205 intervals, Flyway V1–V12 success. Временный PostgreSQL cluster остановлен.

Upgrade fixture включает synthetic conversation criteria, selection, chat memory и
существующий inactive flag. Сравниваются все прежние колонки restaurants и все строки
restaurant_cuisines, restaurant_tags, opening_intervals, menu_items, conversation_state,
selection_items, spring_ai_chat_memory. Production conversation data не использовались.

Новые tests: `BrandDiversityTest`, `BrandDiversityMigrationTest`,
`CuratedCatalogBrandImportTest`, `MiniAppBrandDiversityIntegrationTest`.
Проверены один/два/три бренда, продолжение после первых трёх филиалов сети,
лучший филиал по всем уровням ranking, hard filters, детерминизм и отсутствие name heuristics.
Existing regression expectations обновлены только там, где они требовали несколько
филиалов сети или explanation на прежнее число позиций. Follow-up position-two eval
сохранён на подборке нескольких брендов.

## Исходный итальянский запрос

«Сегодня, 21:00, двое, 150 BYN, итальянская кухня» на собственном V12 каталоге:
**1 результат — Pizza Tempo, Минск, ул. Карла Маркса, 26**,
`seedKey=pizza-tempo-karla-marksa-26`, `brandKey=pizza-tempo`, ID=2,
estimated total **65.40 BYN** (32.70 × 2), matchCount=0 при отсутствии matched tags.
Пять итальянских филиалов принадлежат одному бренду; при равных score/total побеждает
минимальный ID. Другие кухни не добавляются. Проверено offline production adapter/
Java service/DB/transport fixtures с фиксированными Clock; live production-запрос не выполнялся.

## Повторение

При Java 21:

```powershell
.\mvnw.cmd -o clean test
.\mvnw.cmd -o clean verify
```

Только для созданной отдельно пустой local acceptance DB, с выключенными external AI
и без настоящего Telegram token:

```powershell
$env:SPRING_DATASOURCE_PASSWORD=''
.\mvnw.cmd -o test '-Dtest=*Test,!RestaurantBotApplicationTest' `
  '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55436/post_v2_01_final' `
  '-Dspring.datasource.username=postgres' `
  '-Dspring.datasource.driver-class-name=org.postgresql.Driver' `
  '-Dspring.datasource.hikari.minimum-idle=0' `
  '-Dspring.datasource.hikari.maximum-pool-size=4'
```

Все model/Telegram/provider tests используют offline fixtures. Ограничение пулов
относится только к test run. Первый PostgreSQL full run исчерпал connections из-за
нескольких cached Spring contexts; повтор на новой пустой БД с указанными test pools PASS.
Sandbox Mockito attach/initdb запуск не завершился; собственные процессы остановлены,
локальные проверки успешно выполнены с разрешением на необходимые процессы.

Локальные логи исключены из Git: `probes/post-v2-01-evidence/clean-test-final.log`,
`clean-verify.log`, `postgresql-final-tests.log`, `postgresql-migration-final.log`,
`postgresql-final-snapshot.txt`. После исправления quoting временной schema новый
standalone Hibernate acceptance повторён на H2 и PostgreSQL: по 3 tests PASS.

## Риски и границы

- Смысловая правильность brand membership требует ручного review; SQL проверяет формат/наличие.
- Новая неизвестная запись без mapping намеренно останавливает V12. Production применение
  потребует отдельного разрешения и учёта DDL lock; в этой задаче не выполнялось.
- Исторический двухаргументный importer после V12 не может добавлять unbranded venues;
  для будущего импорта обязателен строгий overload.
- Старые сохранённые selection не переписываются; новый поиск формирует подборку по новому правилу.
- Меньше трёх результатов при малом количестве брендов — ожидаемое поведение.

Все проверки завершены: **PASS**. Разрешён только локальный feature commit.
Main остаётся `1b73be8`; production и каталог POST-V2-02 вне scope.
