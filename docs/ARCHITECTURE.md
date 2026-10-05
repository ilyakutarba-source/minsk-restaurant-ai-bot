# Архитектура

Один Spring Boot application, один Maven module, modular monolith и одна PostgreSQL.
Продуктовый scope: [PRODUCT](PRODUCT.md). AI contracts: [AI](AI.md).
Схема и provenance: [DATABASE](DATABASE.md). Runtime: [DEPLOYMENT](DEPLOYMENT.md).

## Реализованная архитектура — IMPLEMENTED

```mermaid
flowchart TD
    TELEGRAM[Telegram private text message] --> TRANSPORT[Telegram adapter / Pengrad long polling]
    TRANSPORT --> AI
    REST[REST controllers / Swagger] --> SERVICES[RestaurantService / MenuService / RestaurantSearchService]
    AI[Spring AI ChatClient / bounded search adapter] --> TOOL[Validated single searchRestaurants request]
    TOOL --> SERVICES
    SERVICES --> CARDS[Java factual renderer / validated ExplanationPlan or fallback]
    CARDS --> SEND[Telegram sendMessage / plain text]
    SERVICES --> DTO[Own DTO / ordered search candidates]
    SERVICES --> JPA[JPA repositories]
    JPA --> PG[(PostgreSQL)]
    FLYWAY[Flyway V1–V4] --> PG
```

Пакет `catalog` содержит Restaurant, menu, persistence и services; `search` — criteria,
filters/ranking и Clock configuration; `api` — тонкие REST controllers; `ai` —
stateless ChatClient adapter, search tool, plan validation и Java renderer;
`telegram` — private text filtering, library polling lifecycle и plain-text sendMessage.
Controllers не выполняют поиск и не возвращают JPA entities. Own DTO создаются внутри
read-only transactions, `open-in-view=false`. Денежные значения — BigDecimal.

Реализованный Telegram slice: private chat → TelegramUpdateHandler →
SpringAiSearchAdapter → searchRestaurants → RestaurantSearchService → PostgreSQL →
trusted SearchResult → optional explanation + existing Java renderer → sendMessage.
Handler не добавляет model calls, search или retries, не читает repository и не
сохраняет контекст. Только полное self-contained сообщение; неполный запрос получает
существующий controlled AI result. Runtime activation/polling: [DEPLOYMENT](DEPLOYMENT.md#telegram-long-polling--implemented).

## Планируемые компоненты — PLANNED

```text
Telegram private chat → ConversationService → Spring AI ChatClient
→ validated single tool request → existing application service → trusted DTO
→ optional Structured Output explanation → Java factual renderer → sendMessage
→ save selection and safe memory after successful send
```

| Компонент | Ответственность | Состояние |
|---|---|---|
| RestaurantSearchService | Валидация, hard filters, estimated total, ranking | IMPLEMENTED |
| RestaurantService | Own catalog/details | IMPLEMENTED |
| MenuService / MenuImportService | Partial menu reading и controlled import | IMPLEMENTED |
| REST adapter | DTO, HTTP mapping, Swagger | IMPLEMENTED |
| LLM / ChatClient | Stateless search interpretation, supported criteria, tool choice, причины объяснения | IMPLEMENTED; [limits/contract](AI.md) |
| ConversationService | Merge criteria, bounded turn, memory/state, отправка и selection | PLANNED |
| ReferenceResolver | Ordinal/name → restaurantId в текущем чате | PLANNED |
| Java renderer | Search factual cards и validated explanation/fallback | IMPLEMENTED для search |
| Telegram adapter | Private text check, long polling, plain-text send | IMPLEMENTED для stateless slice |
| Google adapter | Conditional Place Details enrichment известного ресторана | PLANNED, DEFERRED |

Adapters → application services → repositories/clients. Services поиска/меню/details
не зависят от Telegram, ConversationService или Spring AI SDK. Telegram вызывает
SpringAiSearchAdapter bean напрямую, без HTTP-запросов к собственному приложению.
LLM не получает repositories, EntityManager, SQL или доступ к БД.
HTTP/LLM calls выполняются вне DB transactions. Memory и reference contracts: [AI](AI.md).

## Правила поиска и рекомендаций

### Criteria

Поиск относится к конкретному посещению. Обязательны guests, totalBudgetByn, date и time;
cuisine и preferredTags опциональны. Поддержаны Минск, BYN, 1–6 гостей, сегодня и
следующие шесть дней. Clock определяет текущую дату в Europe/Minsk независимо от ОС.
REST принимает уже структурированный запрос, не разбирает естественный язык.
Неоднозначность бюджета/времени и missing fields требуют уточнения;
stateless Telegram slice не сохраняет предыдущие критерии. Merge остаётся PLANNED.

### Hard filters

1. Ресторан существует в собственном фиксированном каталоге, active=true.
2. При заданной cuisine ресторан содержит эту кухню.
3. Есть собственный положительный estimatedCheckPerGuest с type/source/date;
   `estimatedCheckPerGuest × guests <= totalBudgetByn`.
4. Сохранённое расписание с source/date содержит requested date + local arrival time.

`guests` — входная валидация и множитель бюджета, не самостоятельный фильтр ресторана.
Данных capacity/maxPartySize/reservation limits нет; наличие столика не проверяется.
Неизвестный check или неизвестные hours не подтверждают соответствующее условие.
MenuItem rows и menu metadata не влияют на eligibility. Ограничения не ослабляются
при пустой выдаче или высоком soft score.

### Opening hours

Существующие opening_intervals задают weekday, opensAt, closesAt и closesNextDay.
Интервалы имеют границы `[open, close)`: открытие включено, закрытие исключено.
Учитываются текущий день и продолжение overnight interval предыдущего weekday.
Например, Friday 18:00 → Saturday 02:00 включает Friday 23:00 и Saturday 01:00,
но исключает Saturday 02:00 и 03:00. Поддержаны несколько интервалов и переход недели.
Интервал open==close запрещён; scheduling/calendar subsystem отсутствует.
Проверяется прибытие, не длительность ужина. Недельные часы не гарантируют праздничных.

### Soft criteria

Поддержаны RestaurantTag: COZY, QUIET, ROMANTIC, CASUAL, FRIENDS, PREMIUM.
После hard filtering вычисляются совпадения запрошенных тегов с curated tags каталога.
Несовпавший тег не исключает ресторан. Теги не гарантируют обстановку при посещении.

### Deterministic ranking

- MatchCount = число совпавших requested tags, по +1 за уникальный тег.
- Кухня уже hard filter, дополнительного балла за неё нет.
- Порядок: `matchCount DESC → estimatedTotalByn ASC → restaurant ID ASC`.
- Limit применяется после сортировки: максимум 3 кандидата.
- Одинаковые criteria и каталог дают одинаковый порядок.
- Google rating, случайность, LLM ranking, weights, embeddings, RAG и ML не используются.

Pipeline: catalog → hard filters → eligible restaurants → soft tag matching →
deterministic ranking → result. Чек используется как собственная оценка, не цена любого заказа.

### REST search contract

RestaurantSearchService читает небольшой каталог через RestaurantRepository и
выполняет фильтры/ranking в Java. SearchRequest содержит Integer guests, BigDecimal
общий бюджет, date (ISO date или контрактные TODAY/TOMORROW), time (HH:mm), optional
cuisine/preferredTags. TODAY/TOMORROW — фиксированные токены, не NLP parser.
Положительный бюджет допускает до двух знаков после запятой и нормализуется до scale=2
без округления. Дробный JSON guests не усекается, а отклоняется. Null/отсутствующий
preferredTags становится пустым enum set; дубли не увеличивают score.

SearchResult содержит normalizedCriteria, currency=BYN, ordered candidates и warnings.
Candidate: собственный RestaurantDetails DTO, estimatedTotalByn, matchCount,
allowedReasonCodes. Причины: CUISINE_MATCH при заданной кухне, BUDGET_MATCH,
HOURS_MATCH и `<TAG>_TAG_MATCH` только для совпавших запрошенных тегов.
AI explanation использует только допустимые причины: [AI](AI.md#гибридный-flow-и-объяснение).
Новых search tables/migrations, menu dependency и внешних API calls нет.

## REST API

Все четыре endpoints реализованы. REST/Swagger — локальная проверка Java logic;
HTTP доступ ограничен loopback. Сетевые требования: [DEPLOYMENT](DEPLOYMENT.md#доступ-и-секреты).

| Method / path | Вход / результат | HTTP |
|---|---|---|
| GET /api/v1/restaurants | page ≥0, size 1–100; active own catalog, id ASC | 200, 400 |
| GET /api/v1/restaurants/{id} | Own details по known ID | 200, 404 |
| GET /api/v1/restaurants/{id}/menu | Optional dishType/maxItemPriceByn; own partial menu | 200, 400, 404 |
| POST /api/v1/recommendations | Полные criteria, до 3 кандидатов и причины | 200, 400, 503 |

GET catalog пока не принимает cuisine filter. Details пока не вызывает Google.
Search missing/invalid criteria → 400; отсутствие совпадений → 200 с пустым candidates;
DB/transaction failure → 503 без fallback facts. Entities/provider DTO наружу не выходят.
Menu status: AVAILABLE / NO_RESULTS / DATA_UNAVAILABLE. PARTIAL/source/date сохраняются
при пустом результате, если metadata есть; unknown restaurant → 404.
Menu filters и import semantics: [DATABASE](DATABASE.md#menu-data-strategy).

## Внешние интеграции

AI search — IMPLEMENTED: модель выбирает один searchRestaurants request и причины
объяснения; Java определяет реальные ID, цены, часы, адреса и порядок. Bounded adapter
вызывает application service напрямую, HTTP/model calls вне DB transactions.
Telegram stateless transport — IMPLEMENTED; другие tools и memory — PLANNED.
Call limits/configuration: [AI](AI.md).
Google enrichment допускается только в details известного филиала, по вручную
проверенному place ID; не формирует каталог/меню и не вызывается для search candidates.
Live payload не сохраняется в own DB/memory и не передаётся LLM. При Google failure
остаются own details; противоречивые часы показываются с источниками, без скрытой
смены search result. Google scope/availability/attribution: [DEPLOYMENT](DEPLOYMENT.md#google-places--planned-deferred).

## Ключевые архитектурные решения

| Решение | Причина |
|---|---|
| Один modular monolith | Простые границы через пакеты, одна сборка и доставка |
| Собственная PostgreSQL + JPA + Flyway | Проверяемые факты и явная схема |
| Независимый estimated check | Budget search работает без меню |
| PARTIAL menu | Ограниченный проверяемый набор позиций |
| MatchCount, сумма, ID | Понятный и тестируемый deterministic ranking |
| Три read-only tools, Java facts | Ограниченная поверхность AI и проверяемый результат |
| Memory отдельно от selection | Текст не является источником ID/порядка/фактов |
| Selection после успешного send | Reference относится к реально показанным вариантам |
| Google только enrichment | Core search независим от внешних ratings |
| Long polling, один instance | Без публичного webhook и распределённой доставки |

Система не требует интерфейса для каждого класса, event bus, универсального framework
или дополнительных сервисов. Внешние границы должны быть подменяемы в тестах.
