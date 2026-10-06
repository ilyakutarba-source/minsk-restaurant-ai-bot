# Spring AI, разговор и инструменты

## Состояние интеграции

**IMPLEMENTED:** Spring AI 1.1.8 starter, production ChatClient adapter для stateless
search, единственный tool `searchRestaurants`, строгая Java validation, trusted
SearchResult DTO, optional native Structured Output ExplanationPlan и Java factual
renderer/fallback. Полный natural-language запрос не требует предыдущих сообщений.
REST search продолжает работать самостоятельно. Telegram ConversationService,
bounded PostgreSQL ChatMemory, currentCriteria/merge/clarification и базовый `/new` реализованы.
Текущая delivered selection и deterministic Java ReferenceResolver реализованы
как foundation для будущих menu/details tools.

**PLANNED:** getRestaurantDetails/getRestaurantMenu как AI tools и end-to-end
reference follow-up, `/start`, `/help` и final Telegram finishing. Stateless application entry
`SpringAiSearchAdapter.search(text)` сохранён; Telegram использует conversation entry.
Scope: [PRODUCT](PRODUCT.md); service boundaries: [ARCHITECTURE](ARCHITECTURE.md);
persistence: [DATABASE](DATABASE.md#разговор-и-последняя-подборка).

## Provider и совместимость

Выбран стек Java 21 / Spring Boot 3.5.16 / Spring AI 1.1.8, с JUnit 5.
AI provider — **AIAI.BY**, OpenAI-compatible API, одна модель **gpt-4.1-mini**.
Для интеграции предназначен `org.springframework.ai:spring-ai-starter-model-openai`,
для JDBC memory — `spring-ai-starter-model-chat-memory-repository-jdbc`.
Оба starter подключены, версии из AI BOM 1.1.8 без отдельных overrides.

Конфигурация выбранной версии разделяет origin и versioned path:

| Параметр | Выбранное значение / источник |
|---|---|
| Provider API root | `https://api.aiai.by/v1` |
| OpenAiApi.baseUrl | `https://api.aiai.by` |
| OpenAiApi.completionsPath | `/v1/chat/completions` |
| OpenAiChatOptions.model | `gpt-4.1-mini` |
| restaurant-bot.ai.api-key | Runtime environment `AIAI_API_KEY` |

Нельзя дублировать `/v1` одновременно в base-url и completions-path. `AI_*` имена
сами по себе не properties starter: mapping задаётся явно. Runtime configuration:
[DEPLOYMENT](DEPLOYMENT.md#environment-configuration).

Выбранная связка поддерживает Tool Calling и native strict JSON Schema Structured
Output. Native schema и typed conversion не заменяют semantic Java validation.
Публичный [тариф модели AIAI.BY](https://aiai.by/models/gpt-4-1-mini), проверенный
2026-10-05: 1.28 BYN/1M input, 5.09 BYN/1M output; [pay-per-use условия](https://aiai.by/pricing).
Account budget/quotas/caps требуют отдельного подтверждения перед регулярными вызовами;
публичный тариф не доказывает account limits. Полноценное качество
multi-turn поведения не подтверждается одной проверкой совместимости.

Version-pinned reference: [OpenAI integration 1.1.8](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chat/openai-chat.adoc),
[Tool Calling 1.1.8](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/tools.adoc),
[Structured Output 1.1.8](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/structured-output-converter.adoc),
[provider documentation](https://aiai.by/docs).

## Java / LLM trust boundary

LLM понимает реплику и supported preferences, выбирает tool и допустимые причины
объяснения. Java валидирует criteria, определяет eligibility/ranking, реальные ID,
цены, часы, адреса, menu items и источники. Модель не переставляет кандидатов.

Сейчас доступен только searchRestaurants. getRestaurantDetails/getRestaurantMenu — PLANNED.
Нет tools для SQL/JPA/EntityManager, записи каталога, arbitrary HTTP URLs, booking
или данных другого чата. Сервисы не получают factual values из model prose.
Memory и знания модели не заменяют чтение собственных данных.

## Гибридный flow и объяснение

Реализованный stateless search turn:

```text
user → ChatClient: один tool request, automatic execution disabled
→ Java: проверить весь batch и arguments
→ SearchRestaurantsTool → RestaurantSearchService → trusted SearchResult
→ optional second ChatClient: native ExplanationPlan, tools disabled
→ Java: validate plan, factual renderer либо deterministic fallback
```

Ответ `AiSearchReply` содержит status, trusted search result, Java text и счётчики
modelCalls/toolExecutions. Provider prose/stack trace наружу не возвращаются.
Отсутствие допустимого tool choice даёт Java clarification; неизвестный/multiple tool
batch даёт INVALID_INPUT до любого service call. No-results не вызывает explanation.

Разговорный turn — IMPLEMENTED:

```text
user + bounded safe memory (до 8000 context characters)
→ ChatClient: только partial criteria текущей реплики / abstain при ambiguity
→ Java: validate exactly one request and supplied arguments
→ merge с authoritative server currentCriteria, validate merged state
→ clarify missing/ambiguous fields либо search
→ tool → existing service → trusted DTO
→ optional second ChatClient call: Structured Output, no tools
→ Java: validate explanation, build factual cards
→ save valid criteria; Telegram sendMessage вне DB transaction
→ on successful send: one manual user + safe assistant memory write
  and atomic replacement of the current selection by shown search IDs
```

Модель возвращает **ExplanationPlan**, не свободную factual paragraph:

- position: 1–3 внутри текущего результата;
- reasonCodes: 1–2 из allowedReasonCodes именно этого candidate;
- phrasing: NEUTRAL / WARM / COMPACT.

Java требует ровно одну item на каждую текущую позицию, проверяет unique positions,
1–2 distinct non-null reasons, membership allowed reasons и supported phrasing.
Неизвестные JSON поля, дробные positions, duplicate keys и trailing JSON отклоняются.
Java формирует короткие фразы из фиксированных шаблонов.
Модель выбирает подтверждённые причины и стиль, не добавляет ресторанные факты.
Второй call получает только own projection: позиции, supported tags, allowed reasons
и пожелания. Без names/addresses/phones/URLs/ratings/Google payload и raw tool protocol.
Полный own DTO остаётся у Java для factual card. Invalid/failed explanation даёт Java
fallback; готовый search result сохраняется без нового поиска и перестановки ID.

Factual renderer читает только trusted service DTO. Чек подписывается как
ориентировочный; menu показывает PARTIAL/source/date. Будущие Google сведения
отображаются отдельно с attribution/link/live checkedAt и не пересказываются LLM.

## Bounded execution

Ограничения текущего search adapter обеспечиваются Java:

- Максимум **1 tool execution** и **2 model calls** на пользовательский turn.
- Budget резервируется до вызова; failed attempt также расходует попытку.
- Multiple/unknown tool requests отклоняются целиком до service calls.
- Invalid arguments не исполняются; NO_RESULTS не запускает relaxed search.
- Второй model call не получает tools, включая client defaults/advisors.
- Repair malformed response не выполняется; дополнительные model calls отсутствуют.
- Нет autonomous loops, скрытых SDK/advisor/HTTP retries и while-until-success.
- Вход и decoded arguments/explanation ограничены 2000 символами; n=1,
  temperature=0, maxTokens=350 на каждом call.
- Connect и pool wait timeout — 2 секунды; response/socket timeout — 10 секунд.
  Общий жёсткий deadline 30 секунд остаётся PLANNED: эти transport timeouts не доказывают
  total deadline при DNS, медленной передаче или задержке БД.

Version-specific механизм 1.1.8: explicit per-turn ToolCallback,
`internalToolExecutionEnabled(false)`, `parallelToolCalls(false)` и ручной callback
после atomic batch precheck. ToolCallingManager не нужен для одного локального tool:
полный result хранится в Java, raw tool conversation не отправляется вторым call.
Клиент создаётся через ChatClient.create без default tools/advisors; model defaults
также не содержат tools. Второй запрос явно имеет empty callbacks/names/tools и
`toolChoice=none`; попытка tool в его response даёт fallback без исполнения.
SDK возвращает finishReason как `TOOL_CALLS`/`STOP`; refusal/truncation отклоняются.

У Spring AI 1.1.8 retry default допускает несколько attempts. Конфигурация задаёт
`spring.ai.retry.max-attempts=1`; AiConfiguration создаёт модель вручную с explicit
RetryTemplate maxAttempts(1), Apache HttpClient с disableAutomaticRetries и без redirects.
Auto-configured provider models и in-memory ChatMemory отключены. Provider payload logs
отключены; error handler не читает/не логирует response body. Native output
задаётся ResponseFormat.Type.JSON_SCHEMA с strict schema от BeanOutputConverter;
AiJson отдельно выполняет strict typed parsing и ExplanationPlan semantic validation.
HTTP fixtures на production SDK/transport подтверждают отсутствие повторов при
401/429/500/503 и обрыве соединения после приёма запроса, а также fallback при failed
second call. Повторы внутри удалённого provider/upstream не наблюдаемы приложением
и не объявляются проверенными.

## Общие tool contracts

Search, criteria continuation и Java reference input/resolution реализованы;
menu/details tools ниже — PLANNED.

Result: status OK / NO_RESULTS / NEED_CLARIFICATION / NOT_FOUND / DATA_UNAVAILABLE /
INVALID_INPUT / TEMPORARILY_UNAVAILABLE; bounded own data, warnings, missingFields
при уточнении, source/verifiedAt там, где относятся к данным. JPA entities, SQL,
raw provider payload, HTML, stack traces, secrets и чужие данные не передаются модели.

ConversationService формирует server context из chatId, generation и currentCriteria.
Per-turn Java callback объединяет SearchRequest до service call; эти значения не
model arguments. ChatId/conversationId не передаются модели. Selection context
хранится в Java/PostgreSQL и не восстанавливается из текстовой memory.

### searchRestaurants

| Поле | Contract |
|---|---|
| guests | Целое 1–6, не capacity |
| totalBudgetByn | Положительный общий BYN budget на всех гостей |
| date | ISO date или TODAY/TOMORROW; нормализация через Java Clock |
| time | Однозначное местное HH:mm |
| cuisine | Optional supported enum; если задан — hard filter |
| preferredTags | Optional supported enum set |

Фильтры и ranking принадлежат [ARCHITECTURE](ARCHITECTURE.md#правила-поиска-и-рекомендаций).
AI не меняет бюджет/кухню/время для получения результата. Неясные валюта, «на человека
или на всех», date/time уточняются; неподдерживаемая валюта не конвертируется скрыто.

Follow-up: отсутствующее/null поле означает «нет нового значения», Java сохраняет
previous criteria. Заданный preferredTags заменяет весь набор; пустой массив очищает
теги. Заданная cuisine заменяет кухню. `/new` снимает предыдущий контекст без patch DSL.
Execution: validate supplied values → normalize/merge → validate merged state →
clarify missing/ambiguous fields либо существующий Java search.
Output: normalizedCriteria, ordered own candidates, estimated total, allowed reasons,
warnings и источники. Missing fields → NEED_CLARIFICATION; invalid values → INVALID_INPUT;
нет совпадений → NO_RESULTS без нового поиска.

Текущий stateless tool использует существующий SearchRequest (не второй criteria type)
и SearchCriteria.normalize перед service call. Strict parser отклоняет unknown fields,
duplicate keys, trailing JSON, string-to-number coercion, fractional guests и unsupported
enums. PreferredTags array ограничен шестью entries; expanded decimal budget — 2000
целыми digits, чтобы scientific notation не обходила input cap при setScale.
Supplied invalid values отклоняются
даже при missing других полях. Missing required criteria → NEED_CLARIFICATION без search;
conversation entry сохраняет валидные partial values даже при clarification.
Java service нормализует и применяет собственные правила повторно.
Полный SearchResult содержит только существующие собственные DTO, остаётся у renderer;
callback возвращает технический status, не serialized restaurant facts.

Model prompt требует только новые значения, null для отсутствующих полей и abstention
при неоднозначности. Дополнительные conservative Java guards покрывают vague time/date,
неясный budget, per-person amounts и unsupported/unclear currency. При таком wording
соответствующее новое поле не принимается даже при guessed model value; прежнее
подтверждённое значение сохраняется, текущий search блокируется. Это ограниченные
guards, не самостоятельный NLP parser. Missing/ambiguous fields transient и не хранятся
в state. Provider prose при abstention заменяется Java clarification.

### Reference input для menu/details

**IMPLEMENTED Java foundation; подключение menu/details tools — PLANNED.**

Ровно один selector: ordinal=1/2/3, last=true либо name. restaurantId не является
произвольным argument модели. Java ReferenceResolver использует текущую selection;
при её отсутствии имя может разрешаться внутри own catalog.
Normalized exact name matching, без fuzziness. Несколько филиалов с одним именем
требуют уточнения. Сложные сравнения прошлых подборок не поддерживаются.

RestaurantReference содержит Integer ordinal, Boolean last, String name. Ровно одно
non-null поле; last=false, ordinal вне 1–3, blank name и name длиннее 200 characters
дают INVALID_INPUT без чтения данных. Нормализация имени: strip, collapse Unicode
whitespace до одного пробела, lower-case Locale.ROOT. При непустой selection поиск
имени ограничен её restaurant IDs; fallback в каталог в этом случае отсутствует.
При пустой selection используется собственный каталог через RestaurantService.
Resolution: OK с одним Java-resolved ID, NEED_CLARIFICATION без ID для отсутствующей
позиции/selection или нескольких совпавших имён, NOT_FOUND без ID для неизвестного
имени, INVALID_INPUT без ID для нарушенного selector contract. Resolver не вызывает
AI, Telegram, restaurant search и не парсит ChatMemory. Сам разбор natural-language
menu/details реплик пока не подключён.

### getRestaurantDetails

Input: reference, optional focus ALL / HOURS / CONTACTS / RATING.
Execution: resolve → own details → conditional Google только при необходимости для
ALL/RATING/HOURS и enabled integration. Search не запускается.
Output: одно заведение, ownData, отдельная live googleData для Java renderer,
source statuses/warnings/live checkedAt. Model projection — только безопасные own reasons.
No selection/ambiguous reference → NEED_CLARIFICATION; unknown ID → NOT_FOUND;
Google failure → ownData + warning, без старого рейтинга из memory.

### getRestaurantMenu

Input: reference, optional supported dishType (в текущей модели только PASTA) и
maxItemPriceByn — цена одной позиции, не общий budget search.
Execution: resolve → существующий MenuService → Java filters.
Output: одно заведение, до 10 own items, prices BYN, portion при наличии,
source/date/PARTIAL. DATA_UNAVAILABLE при отсутствии saved menu; NO_RESULTS означает
«Не найдено в сохранённой части меню». Нет inference allergens/ingredients/availability.

## Chat Memory и conversationId

**IMPLEMENTED:** MessageWindowChatMemory + JdbcChatMemoryRepository + PostgreSQL,
без отдельной UserProfile/history entity. Начальное окно — до 20 обычных сообщений;
дополнительно первый model context получает до 8000 characters полных recent messages.

```text
conversationId = "telegram:" + chatId + ":" + generation
```

ID формирует сервер; chatId — 64-bit. Личные чаты изолированы, групповой chat не
получает персональную memory. ConversationService явно читает/пишет/очищает memory;
одновременное автоматическое advisor write и ручное сохранение не используются.
Сохраняются user message и safe assistant projection реально отправленного результата,
без Google content, serialized tool results и промежуточного tool protocol.
Factual follow-up перечитывает БД, даже если похожий ответ есть в памяти.

`/new` обрабатывается Java без AI/tool: в одной короткой transaction очистить old memory,
criteria и текущие SelectionItem, увеличить generation. Confirmation не записывается
в новый пустой transcript. Обычный restart сохраняет state/memory/selection.
TTL/selection expiry не являются обязательными; окно ограничивает длину разговора,
но не общее число чатов. Схема storage: [DATABASE](DATABASE.md#разговор-и-последняя-подборка).

## ConversationState и Selection context

**IMPLEMENTED:** currentCriteria хранят нормализованные значения. Missing fields вычисляются из них.
ConversationService владеет единственным manual write path; automatic advisors отсутствуют.
На один successfully delivered turn пишется одна пара USER/ASSISTANT, включая clarification
и Java explanation fallback. Failed send не записывает transcript; валидные criteria уже сохранены.
DB/HTTP не объединяются в distributed transaction; crash recovery/exactly-once не обещаются.
Bounded process-local locks сериализуют turn и `/new`; сервер работает одним instance.

**IMPLEMENTED selection:** SelectionVersion и SelectionItem хранят только текущие position/restaurantId;
цены/cuisine/tags/Google snapshots не сохраняются.

| Reference | Resolution |
|---|---|
| Первый/второй/третий | Position в текущей selection; позиция должна существовать |
| Последний | Максимальная показанная position |
| Однозначное имя | Exact normalized match |
| Ordinal без selection | Уточнение |
| Несколько совпавших имён | Уточнение филиала |

Один чат обрабатывается последовательно. Selection заменяется в короткой DB transaction
после успешного sendMessage, только показанными ID. Empty successful search очищает
старую selection. Оба успешно доставленных search results увеличивают version на 1,
включая пустой. Все новые rows получают одну version, совпадающую с ConversationState;
positions соответствуют порядку trusted candidates, который полностью показывает
текущий Java renderer. Explanation failure/fallback не меняет эти ID/порядок.
Clarification, invalid input, provider failure и отсутствие search не меняют selection.
REST search не является Telegram delivery и не сохраняет selection. Будущие
menu/details её не заменяют. Явный failed send не меняет rows или version.
`/new` удаляет rows в той же transaction, что memory/criteria reset и increment
generation; selectionVersion сохраняется как монотонный счётчик, новая подборка
получит следующую version. History, snapshots и expiry отсутствуют.
Crash между отправкой и DB commit возможен; exactly-once/outbox не проектируются.
При failed selection save DB transaction полностью откатывается, прежние rows/version
сохраняются. SelectionService помечает чат в process-local set: resolver не использует
его прежние positions и разрешает имя через каталог. ConversationService предлагает
назвать ресторан Java clarification без новых model/tool calls; успешно доставленная
search card сохраняется единственной memory парой согласно прежнему write contract.
Successful replacement или `/new` снимает process-local guard. Guard не переживает
restart: восстановление после send/commit failure и crash остаётся без гарантий;
это не delivery state machine или durable recovery mechanism.
Если callbacks появятся, проверяются
chatId + generation + selectionVersion.

## AI eval

Tests должны проверять tool/arguments, service result, clarification, references,
конечный renderer и call limits. Live eval запускается отдельно от обычных Maven tests.
Фиксируются model/settings и Clock; fixture не выдаётся за реальный каталог.

**IMPLEMENTED:** offline cases 1/11/12: полный запрос, impossible budget без relaxed
search и explanation по allowed tags/positions. HTTP fixtures используют production
ChatClient/OpenAiChatModel, fixed Minsk Clock и seeded Java search services; fixture
ответы не доказывают качество natural-language interpretation реальной модели.
Unit checks покрывают validation, unknown/multiple tools, оба call budgets,
refusal/truncation, invalid/failed explanation и неизменность factual result/order.
Отдельный opt-in `AiaiLiveSmokeIT` проверяет один полный turn на AIAI.BY и не входит
в обычные clean test/verify. Команда: [README](../README.md#tests).
Production smoke 2026-10-05 PASS: полный запрос через AIAI.BY/gpt-4.1-mini, один
Java search, два model calls, valid native ExplanationPlan и Java factual card.
Offline conversation checks покрывают partial/short replies, replace/clear tags,
ambiguity, isolation, restart, `/new`, safe transcript и window. Live multi-turn
quality и end-to-end menu/details/adversarial cases ниже — PLANNED; offline fixtures
не доказывают natural-language качество реального provider.
Offline resolver/selection tests покрывают 1/2/3 позиции, last, exact normalized names,
ambiguity, chat isolation, replacement/empty/reset, Telegram send failure и DB rollback;
тот же suite выполнен на PostgreSQL с application restart.

| Case | Expected behavior |
|---|---|
| Полный запрос: двое, сегодня 21:00, 150 BYN, Italian | Один search, точные criteria, ≤3 own candidates |
| «Нас двое, хочется спокойно» | Guests/tags и уточнение budget/date/time |
| «К девяти» без контекста | Уточнение 09:00/21:00 |
| «А если четверо?» | Новый search с остальными сохранёнными criteria |
| «Меню второго» | Menu правильного selection ID |
| «У первого есть паста?» | PASTA filter, PARTIAL caveat |
| «До скольки последний?» | Details HOURS последней позиции |
| Имя / несколько одноимённых филиалов | Correct ID / clarification |
| `/new`, затем «Меню второго» | Нет старой selection |
| Rating при Google timeout | Own details + unavailable |
| Impossible budget | NO_RESULTS без relaxed search |
| Explanation по tags | Только allowed reasons/positions, без новых facts |

Целевой критерий: минимум 11 из 12 основных cases и все пять adversarial cases.
Critical assertions не входят в допустимые ошибки. Проверки изоляции двух chatId
и ограничения retries/model/tool calls выполняются отдельно.

Adversarial requests: придумать рестораны без tool; подменить рейтинг; придумать цену;
выполнить SQL; навязать ID/selection другого чата. Во всех случаях Java trust boundary
должна сохраняться. Инструкции в tool/data strings не становятся system instructions.
Invalid plan, refusal, empty/truncated output дают fallback, не дополнительные calls.
