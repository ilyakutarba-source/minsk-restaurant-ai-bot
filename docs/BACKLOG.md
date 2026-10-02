# Backlog и порядок реализации

Редакция: 2026-10-02. TASK-00 принят пользователем, TASK-01 и TASK-02 DONE. TASK-03–TASK-14 не начаты. [Feasibility report](TASK-00-FEASIBILITY.md) сохраняет исторические результаты и ограничения первоначальной проверки.

TASK-00–TASK-14 — 15 задач с сохранённой запрошенной нумерацией, вместо прежних 23 TASK-00–TASK-22. CI — отдельное SHOULD после MVP, без обязательной TASK-15.

В каждом task перечислены только его изменения; полные contracts/rules находятся в документах-владельцах. Tests выполняются вместе с функцией, TASK-12 собирает итоговую проверку.

## TASK-00 — Feasibility gate

**Status:** DONE — результат и выбранный stack приняты пользователем.

**Goal:** подтвердить минимальные предпосылки до bootstrap.  
**Why:** версия курса, provider и источники могут изменить реализацию и conditional Google scope.  
**Scope:** требования курса/сроки; сравнение Boot 3.5 + AI 1.1 и Boot 4 + AI 2.0; compatible Telegram/springdoc/test dependencies; одна модель с native tools/structured output; первые 3 реальных ресторана с own check/hours и partial menus; Google access/billing/policy/field masks.  
**Dependencies:** проверенный пользователем scope и доступ к требованиям курса/API без помещения секретов в Git.

**Acceptance Criteria:**

- Выбрана совместимая линия, зафиксированы точные versions/BOM/starter и official docs выбранной версии.
- Provider/model доступен из реальной среды, подтверждены tool calling и structured explanation output, технические call limits. Способ оплаты документирован; неизвестные account tariff/budget/quotas явно классифицированы до регулярных AI calls в TASK-05, не объявлены PASS.
- Подтверждён способ ограничить turn одним tool и максимум двумя model calls, без скрытых retries.
- Есть данные 3 реальных заведений: источники, положительный check на гостя, расписание; меню может загружаться отдельно от search.
- Google решение оформлено PASS / DEFERRED / EXCLUDED / FAIL с evidence. DEFERRED блокирует только TASK-10, не bootstrap/core slice; до TASK-10 нужен live pass либо явное documented exclusion.
- Deadline/weekly time записаны либо остаются USER_INPUT_REQUIRED / PLANNING_INPUT для оценки scope; их отсутствие не блокирует технический bootstrap. Остальные gates имеют evidence status, классификацию и task, где потребуются; неизвестное не объявлено PASS.

**Tests:** отдельные ручные API/capability probes и проверка источников с записанным результатом; это не production application.  
**Out of Scope:** production Maven/Spring application, код бота, project migrations, Docker config, новый PM-анализ, полный каталог. Отдельные disposable test-only Maven probes разрешены для capability checks.

## TASK-01 — Project bootstrap

**Status:** DONE — 2026-10-02.

**Goal:** создать воспроизводимую учебную основу.  
**Why:** закрепить подтверждённый dependency set.  
**Scope:** один Maven module; Boot/JPA/PostgreSQL/H2/Flyway/springdoc; profiles/config; Java 21; Git и исключение secrets.  
**Dependencies:** утверждённый пользователем результат TASK-00 и scope/stack; нет открытых BLOCKER_FOR_TASK_01. PLANNING_INPUT и DEFERRED_TO_LATER_TASK не требуют закрытия перед bootstrap.

**Acceptance Criteria:**

- Clean checkout собирается на выбранной JDK, версии pinned.
- Test profile не вызывает внешние AI/Telegram/Google API.
- DB config задаётся извне; локальные development credentials через environment/config допустимы, секреты не tracked. Production DB credentials не являются prerequisite TASK-01; реальный PostgreSQL migration acceptance остаётся TASK-02, H2 его не заменяет.
- Минимальный application smoke проходит.

**Tests:** build и целевой context smoke; без искусственных tests для getters/config каждого bean.  
**Out of Scope:** каталог, AI tools, Telegram, Docker/Dokploy.  

## TASK-02 — Restaurant catalog

**Status:** DONE — 2026-10-02. Schema/JPA/services/DTO и оба GET реализованы. `clean test` и `clean verify`: 28 tests, 0 failures/errors/skipped. PostgreSQL 17.10: 27 catalog tests, 0 failures/errors/skipped; Flyway на пустой БД/reapply и Hibernate validate PASS. У всех трёх филиалов сохранены положительный check per guest, DERIVED type, источники и даты; применимость общего сетевого меню двух филиалов подтверждена. Расписание, включая overnight, сохранено. Все Acceptance Criteria PASS. Суммы, методика и граница сетевого evidence: [DATABASE.md](DATABASE.md#estimated-check-strategy).

**Goal:** хранить и читать проверенные рестораны.  
**Why:** факты должны существовать до подключения AI.  
**Scope:** Restaurant, cuisine/tags, opening hours, independent estimated check, Flyway schema/JPA/services/DTO; GET catalog/details; первые 3 own restaurant records. Модель: [DATABASE.md](DATABASE.md).  
**Dependencies:** TASK-01 и подготовленные в TASK-00 данные.

**Acceptance Criteria:**

- PostgreSQL migration создаёт нужную схему; Hibernate validate проходит.
- Sources/dates и check per guest сохранены; overnight hours представимы.
- SeedKey предотвращает дубли; restaurant name не unique.
- Два GET endpoints видны в Swagger; known ID читается, unknown ID → 404.
- Entities не выходят в API, own details не зависят от Google.

**Tests:** JPA relationships/constraints, service/REST mapping и 404; PostgreSQL migration smoke.  
**Out of Scope:** MenuItem, Google live, admin CRUD, TelegramUser, profile/history.  

## TASK-03 — Partial menu и controlled import

**Goal:** поддержать реальные menu questions.  
**Why:** сохранённые блюда нужны для follow-up, а не для обязательного расчёта чека.  
**Scope:** MenuItem/schema; 5–10 полезных позиций первых 3 ресторанов; source/verifiedAt/PARTIAL; один простой JSON seed/import; Menu service и menu GET endpoint.  
**Dependencies:** TASK-02. Не является зависимостью TASK-04–TASK-06; при желании выполнить после первого slice.

**Acceptance Criteria:**

- Меню трёх заведений читается с metadata.
- Положительные BYN-цены и supported types валидируются.
- Повторный import не создаёт дубли; invalid import не оставляет частичное обновление.
- Отсутствие позиции означает «не найдено в сохранённой части», не отсутствие блюда вообще.
- Restaurant search работает без MenuItem rows.

**Tests:** import validation/transaction, menu filters, empty partial result, REST metadata.  
**Out of Scope:** полное меню, scraping, automatic sync, allergens inference, basket estimation.  

## TASK-04 — Restaurant search

**Goal:** детерминированно подбирать заведения в Java.  
**Why:** AI tool должен вызывать готовую проверяемую логику.  
**Scope:** normalized criteria, hard filters, check × guests, own hours, simple MatchCount/tie-break, ≤3 candidates, POST recommendations. Правила: [ARCHITECTURE.md](ARCHITECTURE.md#правила-поиска-и-рекомендаций).  
**Dependencies:** TASK-02; TASK-03 не требуется.

**Acceptance Criteria:**

- Hard constraints не ослабляются; неизвестный check/hours не подтверждает условие.
- Бюджет использует чек на одного гостя и общий лимит.
- Дневные/overnight intervals и supported dates корректны.
- Одинаковый вход/каталог дают одинаковый порядок.
- POST recommendations доступен в Swagger без AI; пустой результат — корректный 200.
- Частичное/отсутствующее меню не меняет eligibility.

**Tests:** check/budget boundary, guest validation, strict cuisine, soft tags, tie-break, overnight/exact closing boundary, empty result, отсутствующее меню.  
**Out of Scope:** weighted score, geographic search, Google ranking, basket calculator.  

## TASK-05 — Spring AI и search tool

**Goal:** получить реальный tool calling и контролируемое объяснение.  
**Why:** проверить центральную интеграцию рано.  
**Scope:** ChatClient adapter, initial prompt, search tool schema/validation, trusted DTO, controlled optional second explanation call и Java factual renderer; ограничения из [AI.md](AI.md).  
**Dependencies:** TASK-04 и provider/version gate TASK-00.

**Acceptance Criteria:**

- Модель выбирает searchRestaurants и services получают проверенные criteria.
- AI не имеет repositories/SQL и не формирует restaurant factual fields.
- Один tool execution на turn; неизвестный/multiple requests отклоняются.
- ExplanationPlan с valid reasons может сопровождать карточку; invalid/failed explanation даёт Java fallback.
- Второй model call не может вызвать tools; общий лимит calls соблюдён.
- Full criteria request работает без persistent memory.

**Tests:** mocked tool adapter/guards/renderer; первые eval cases 1/11/12; отдельный реальный capability smoke.  
**Out of Scope:** Telegram transport, persistent memory, другие tools, autonomous loop.  

## TASK-06 — Telegram vertical slice

**Goal:** замкнуть первый end-to-end пользовательский сценарий.  
**Why:** увидеть главный продукт до расширения каталога/memory/Google.  
**Scope:** minimal private-chat long polling; complete request → AI search → PostgreSQL → explanation + Java card → sendMessage на 3 собственных заведениях.  
**Dependencies:** TASK-05; каталог TASK-02. Меню TASK-03 не блокирует slice.

**Acceptance Criteria:**

- Полный natural-language запрос проходит Telegram → AI → tool → services → PostgreSQL → Telegram.
- В ответе только до 3 подходящих собственных ID; порядок неизменен.
- Чек подписан как ориентировочный на указанное число гостей.
- Ошибка explanation не ломает фактическую выдачу.
- Повторяемый demo запрос учитывает собственное расписание.
- Slice реально работает до перехода к TASK-07/Google.

**Tests:** mocked Telegram orchestration/escaping и один live end-to-end smoke.  
**Out of Scope:** memory/state, menu/details tools, весь каталог, Google, callbacks.  

## TASK-07 — Conversation memory и criteria state

**Goal:** поддержать уточнения и независимые продолжения.  
**Why:** краткие реплики требуют устойчивого контекста.  
**Scope:** PostgreSQL ChatMemory/window; ConversationState; derived conversationId; criteria merge/clarification; /new; schema через Flyway.  
**Dependencies:** успешная TASK-06.

**Acceptance Criteria:**

- Разные chatId имеют независимые memory и criteria.
- Memory/currentCriteria переживают restart.
- Короткий ответ дополняет недостающее поле, не сбрасывая остальные.
- Неоднозначное время/бюджет уточняется.
- /new очищает memory/criteria и меняет generation; selection будет очищаться после TASK-08.
- Google/tool protocol не попадают в persistent transcript; нет двойного memory write.

**Tests:** criteria merge/missing fields, date/time ambiguity, два chatId, PostgreSQL restart, reset.  
**Out of Scope:** TelegramUser, полный chat archive, TTL/scheduled cleanup, профили/preferences.  

## TASK-08 — Selection context и ReferenceResolver

**Goal:** надёжно сопоставлять «у второго» с показанным ID.  
**Why:** текстовая chat memory недостаточна.  
**Scope:** selectionVersion, minimal SelectionItem, transaction замены после успешного sendMessage, ordinal/last/exact name resolution.  
**Dependencies:** TASK-07; каталожные names/IDs TASK-02.

**Acceptance Criteria:**

- Первый/второй/третий/последний разрешаются по текущему порядку данного chatId.
- Ordinal вне показанного списка и ambiguous name требуют уточнения.
- Успешная новая выдача заменяет selection, пустая — очищает.
- Явный send failure не заменяет прежнюю selection.
- /new очищает selection; другой чат не имеет к ней доступа.
- Нет check/tag snapshots, expiry и delivery state machine.

**Tests:** resolver на 1/2/3 candidates, empty/ambiguous name, two chats, reset, send success/failure, consistent selection replacement.  
**Out of Scope:** сложные semantic references, cheapest/comparison, history, exactly-once, outbox.  

## TASK-09 — Menu и details tools

**Goal:** завершить основной conversational follow-up.  
**Why:** выбор ресторана продолжается вопросами о конкретном месте.  
**Scope:** getRestaurantMenu/getRestaurantDetails adapters; reference resolution; Java menu filters, own hours/contacts; safe projections/rendering. Contracts: [AI.md](AI.md#общие-tool-contracts).  
**Dependencies:** TASK-03 и TASK-08.

**Acceptance Criteria:**

- «Меню второго» читает нужный MenuItem dataset.
- «Есть паста у первого?» использует supported dishType и partial wording.
- Details question читает собственные факты заново, не memory.
- Menu/details не запускают search.
- Всего tools ровно 3; DTO не включают entities/provider payload.
- Неоднозначный/несуществующий reference даёт controlled result.

**Tests:** Menu service, обе tool adapters, resolver interaction, partial unknown, menu/details conversational cases.  
**Out of Scope:** Google live implementation, comparison tool, новые tools, гарантии наличия.  

## TASK-10 — Google Places enrichment

**Goal:** добавить разрешённые live сведения.  
**Why:** рейтинг/текущие часы полезны как дополнительный источник.  
**Scope:** known place mapping, minimal mask, rating/count/current hours/Maps link, source/attribution, timeout/errors, Google projection только renderer.  
**Dependencies:** TASK-09 и пройденный Google gate TASK-00.

**Acceptance Criteria:**

- Подтверждён live Place Details для конкретного собственного филиала.
- Доступные fields показаны с источником; missing fields не заменяются выдумкой.
- Search/ranking не вызывают Google и не зависят от rating.
- Timeout/403/429 оставляют own details доступными.
- Google data не отправляется модели и не сохраняется в memory/каталоге.
- Attribution/Terms/Privacy соответствуют принятому gate решению.

**Tests:** adapter mapping, missing fields, errors, сохранность own details; отдельный live smoke без API key в отчёте.  
**Out of Scope:** Google catalog import, reviews text/cache, menu retrieval, wildcard field mask.

При DEFERRED gate задача ожидает закрытия Google gate; это не блокирует bootstrap/core slice. При явном documented exclusion задача получает статус **исключена из MVP по TASK-00 decision**, а не «выполнена». Failure ветка и честное отсутствие live data остаются проверяемыми.


## TASK-11 — Telegram finishing и controlled errors

**Goal:** сделать законченный понятный интерфейс.  
**Why:** ошибки и команды не должны приводить к неверному выбору.  
**Scope:** /start, /help, окончательная /new; private chat handling, formatting/length limits; per-chat sequential processing; provider/DB/Telegram failures; финальная проверка budgets. Offset — сначала поведение выбранной library, минимальное собственное хранение только если необходимо.  
**Dependencies:** TASK-09; TASK-10 завершена либо официально исключена.

**Acceptance Criteria:**

- Команды и unsupported booking/city/currency дают понятные ответы.
- Message/input и execution limits действительно применяются.
- Модель не повторяет search после пустой выдачи; SDK retries не обходят лимит.
- Явный send failure не выдаётся за показанный результат.
- Long polling/update behavior documented; нет custom checkpoint state machine.
- Один poller и последовательность одного чата соблюдаются.

**Tests:** commands/errors, malformed/multi-tool reply, provider timeouts, failed send, concurrent updates одного чата, library offset behavior smoke.  
**Out of Scope:** webhook, replicas, distributed rate limiter, обязательные callbacks, guaranteed delivery.  

## TASK-12 — Итоговые данные, tests и AI eval

**Goal:** подтвердить готовность функций на итоговом каталоге.  
**Why:** unit tests и пример на 3 местах не заменяют проверку всего MVP.  
**Scope:** расширение до 10–12 реальных мест и partial menus; meaningful JUnit 5/Mockito; PostgreSQL acceptance; 12 conversational + 5 adversarial cases из [AI.md](AI.md#ai-eval); корректность Google mapping или исключения.  
**Dependencies:** TASK-11; первая data expansion только после successful TASK-06.

**Acceptance Criteria:**

- 10–12 реальных заведений, own checks/hours/sources, 5–10 menu items на место.
- Tests покрывают search/budget/hours, reference, menu, tools, isolation, Google mapping/errors, Telegram orchestration.
- H2 tests не подменяют PostgreSQL migration/state/memory restart checks.
- Основной eval ≥11/12; adversarial 5/5; критические assertions не имеют допустимых ошибок.
- Results зафиксированы; найденные failures исправлены либо scope пересмотрен явно.

**Tests:** unit/integration suite + отдельный live eval run и PostgreSQL acceptance. Внешние API не вызываются из обычных unit tests.  
**Out of Scope:** coverage quota, AI QA platform, performance tuning без измерений, полный каталог.  

## TASK-13 — Docker Compose и Dokploy/VPS

**Goal:** воспроизводимо разместить проект.  
**Why:** диплом должен запускаться вне IDE и сохранять данные.  
**Scope:** Dockerfile, Compose app/postgres, env mapping, volume/healthchecks, Dokploy Compose project, private HTTP/DB, one poller, restart/redeploy smoke и документированный backup. Инструкции: [DEPLOYMENT.md](DEPLOYMENT.md).  
**Dependencies:** TASK-12, VPS access; conditional Google config по TASK-00.

**Acceptance Criteria:**

- Clean checkout/build image работает; Compose стартует и Flyway выполняется.
- Secrets не включены в image/Git; env из Dokploy явно передаются контейнерам.
- Telegram работает с VPS; REST/Swagger и PostgreSQL не публичны.
- DB volume переживает restart/redeploy; приложение поднимается после restart.
- Backup способ документирован; deployment commit и необходимые settings записаны.

**Tests:** local container smoke и remote Telegram/DB persistence smoke.  
**Out of Scope:** Spring Security, CI blocker, HA/zero downtime, RPO/RTO, обязательный restore drill.  

## TASK-14 — Final review и дипломная демонстрация

**Goal:** завершить объяснимый и воспроизводимый диплом.  
**Why:** итог должен соответствовать scope и быть понятен проверяющему.  
**Scope:** code review, README, актуальные docs/ADR/diagrams, DoD evidence, 8–10 minute demo и репетиция.  
**Dependencies:** TASK-13; TASK-10 successful либо documented exclusion.

**Acceptance Criteria:**

- DoD [PRODUCT.md](PRODUCT.md#definition-of-done) проверен по фактическим результатам.
- README описывает версии, configuration, data sources, запуск, limitations и Google gate outcome.
- Нет смешения facts/memory/model prose и дублирующих docs.
- Сценарий защиты повторяется и показывает meaningful Spring AI role.
- Студент объясняет архитектуру, budget/reference logic и external failures.
- Scope не расширяется новыми features перед завершением.

**Tests:** одна полная rehearsal и review критических границ; не повторять все checks без новой причины.  
**Out of Scope:** новые user features, CI/CD как обязательный пункт, production reliability audit.  

## Milestones

| Milestone | Tasks | Проверяемый результат |
|---|---|---|
| M0 Preflight | 00 | Данные, версии/model и Google decision |
| M1 Backend foundation | 01–04 | Own каталог, partial menu, Java search + Swagger |
| M2 First AI vertical slice | 05–06 | Telegram → AI search → PostgreSQL → ответ |
| M3 Conversational assistant | 07–09 | Memory, criteria, selection, menu/details follow-ups |
| M4 External enrichment | 10 | Live Google либо documented exclusion |
| M5 Quality | 11–12 | Завершённый transport, итоговые данные/tests/eval |
| M6 Delivery | 13–14 | Compose/Dokploy, persistent DB, защита |

TASK-03 можно выполнить позже без задержки M2: критический путь slice — 00 → 01 → 02 → 04 → 05 → 06. Последовательность задач не требует делать весь набор данных до первого рабочего сценария.

## First vertical slice

Три реальных ресторана, один search tool, полный запрос:

> Сегодня в 21:00 нас двое, бюджет 150 BYN, хочется итальянскую кухню.

```text
Telegram
→ Spring AI
→ searchRestaurants
→ Java hard filters / ranking
→ PostgreSQL: каталог из 3 мест
→ до 3 подходящих candidates
→ controlled AI explanation + Java factual cards
→ Telegram
```

Три места в БД не гарантируют три совпадения по Italian/budget/hours. Для demo выбрать критерии/время, при которых есть реальный результат; не добавлять неподходящие места ради количества.

До slice нужны только bootstrap/БД, 3 own ресторанных записи с check/hours, search service, один tool и Telegram. Persistent memory, SelectionItem, menu/details tools, Google и расширенный каталог появляются позже. Если slice не работает, не переходить к memory/Google; сначала исправить центральную цепочку.

## Open Gates

Реестр наследует исходные NEED IDs. Evidence status и блокирующее значение разделены: перенос gate не означает PASS. Классификация пересмотрена по запросу пользователя после успешного live AIAI probe.

- **BLOCKER_FOR_TASK_01** — обязательная нерешённая техническая предпосылка bootstrap. В текущем evidence таких remaining gates нет.
- **DEFERRED_TO_LATER_TASK** — prerequisite конкретной последующей реализации/acceptance, а не создания проекта.
- **PLANNING_INPUT** — сведения для scope, сроков или оценки диплома; отсутствие не доказывает техническую несовместимость bootstrap.

| Gate | Evidence status | Classification | Обоснование / когда закрыть |
|---|---|---|---|
| N1 demand | UNKNOWN | PLANNING_INPUT | Востребованность не подтверждена; bootstrap учебного проекта не зависит от интервью |
| N2 own data | PASS для каталога первых 3 филиалов; остальное PARTIAL | DEFERRED_TO_LATER_TASK | TASK-02: checks/hours/sources/dates и применимость сетевого меню подтверждены; [источники и методика](DATABASE.md#estimated-check-strategy). Menu import — TASK-03; расширение каталога — последующие задачи |
| N3 Google | DEFERRED | DEFERRED_TO_LATER_TASK | Только TASK-10: live PASS либо explicit documented exclusion; собственный каталог/поиск независимы |
| N4 account tariff/budget/quotas | PARTIAL; центральные AI capabilities PASS | DEFERRED_TO_LATER_TASK | AIAI auth/model/Spring/tools/native output/1 tool–2 calls подтверждены. Тариф и account caps уточнить перед регулярными AI calls в TASK-05; production/eval error-path retry checks — TASK-05/TASK-12 |
| N5 VPS | UNKNOWN | DEFERRED_TO_LATER_TASK | Remote AI подтверждён, локальный bootstrap не зависит от VPS. Resources/access/backup нужны в TASK-13 |
| N6 deadline/weekly time | USER_INPUT_REQUIRED | PLANNING_INPUT | Нужны для оценки scope/темпа, не для сборки Maven и application smoke |
| N7 external course rubric | USER_INPUT_REQUIRED; проектные требования достаточны | PLANNING_INPUT | Java 21 и обязательный stack зафиксированы в рабочих docs и stack evidence TASK-00; line A с Boot 3.5.16/AI 1.1.8 проверена. Дополнительная rubric не найдена, но документированного противоречия нет; отдельное подтверждение курса не technical blocker |
| PostgreSQL live acceptance | PASS для каталога TASK-02 на PostgreSQL 17.10 | DEFERRED_TO_LATER_TASK | Flyway, Hibernate validate, seed и constraints проверены на отдельной пустой БД. Memory restart — TASK-07; production/deployment credentials — TASK-13 |

Обоснование N7 и версии: [TASK-00 sections 2–4](TASK-00-FEASIBILITY.md#2-course-requirements); [Architecture ADR](ARCHITECTURE.md#adr-summary), [AI version gate](AI.md#version-gate-перед-bootstrap), [Product MUST](PRODUCT.md#mvp--must). Требования проекта не выдаются за найденную rubric курса. Рекомендованный stack A подготовлен для утверждения пользователя; автоматического утверждения Spring AI 1.1.8 нет.

**TASK-00 принят; TASK-01 и TASK-02 DONE.** Data acceptance первых трёх каталожных записей закрыт. Остальные PARTIAL/UNKNOWN/USER_INPUT_REQUIRED сохраняются для указанных будущих задач и planning inputs.

## SHOULD после MVP

CI, retention cleanup, restore test, callbacks и review texts находятся в [PRODUCT.md](PRODUCT.md#should-have-после-работающего-mvp). CI scope и target deployment описаны только в [DEPLOYMENT.md](DEPLOYMENT.md#optional-ci). Новые задачи для них добавляются после готового MVP, не на критический путь.
