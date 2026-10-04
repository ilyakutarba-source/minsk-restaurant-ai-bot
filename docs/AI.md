# Spring AI, разговор и инструменты

## Состояние интеграции

**IMPLEMENTED:** Java catalog/search/menu services и доверенные собственные DTO.
Spring AI 1.1.8 закреплён через BOM в Maven.

**PLANNED:** provider starter, ChatClient, Tool Calling, Structured Output explanation,
Telegram orchestration, ChatMemory, criteria state и reference resolution. Ни один из
описанных ниже AI tools пока не подключён к application. REST search работает без AI.
Scope: [PRODUCT](PRODUCT.md); service boundaries: [ARCHITECTURE](ARCHITECTURE.md);
planned persistence: [DATABASE](DATABASE.md#разговор-и-последняя-подборка).

## Provider и совместимость

Выбран стек Java 21 / Spring Boot 3.5.16 / Spring AI 1.1.8, с JUnit 5.
AI provider — **AIAI.BY**, OpenAI-compatible API, одна модель **gpt-4.1-mini**.
Для интеграции предназначен `org.springframework.ai:spring-ai-starter-model-openai`,
для JDBC memory — `spring-ai-starter-model-chat-memory-repository-jdbc`; версии из AI BOM.
Эти starters ещё не добавлены в application dependencies.

Конфигурация выбранной версии разделяет origin и versioned path:

| Параметр | Выбранное значение / источник |
|---|---|
| Provider API root | `https://api.aiai.by/v1` |
| spring.ai.openai.base-url | `https://api.aiai.by` |
| spring.ai.openai.chat.completions-path | `/v1/chat/completions` |
| spring.ai.openai.chat.options.model | `gpt-4.1-mini` |
| spring.ai.openai.api-key | Runtime environment `AIAI_API_KEY` |

Нельзя дублировать `/v1` одновременно в base-url и completions-path. `AI_*` имена
сами по себе не properties starter: mapping задаётся явно. Runtime configuration:
[DEPLOYMENT](DEPLOYMENT.md#environment-configuration).

Выбранная связка поддерживает Tool Calling и native strict JSON Schema Structured
Output. Native schema и typed conversion не заменяют semantic Java validation.
Тариф, доступный баланс и account quotas требуют отдельного подтверждения перед
регулярными вызовами; upstream цены не являются ценами AIAI.BY. Полноценное качество
multi-turn поведения не подтверждается одной проверкой совместимости.

Version-pinned reference: [OpenAI integration 1.1.8](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chat/openai-chat.adoc),
[Tool Calling 1.1.8](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/tools.adoc),
[Structured Output 1.1.8](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/structured-output-converter.adoc),
[provider documentation](https://aiai.by/docs).

## Java / LLM trust boundary

LLM понимает реплику и supported preferences, выбирает tool и допустимые причины
объяснения. Java валидирует criteria, определяет eligibility/ranking, реальные ID,
цены, часы, адреса, menu items и источники. Модель не переставляет кандидатов.

Доступные tools — только searchRestaurants, getRestaurantDetails, getRestaurantMenu.
Нет tools для SQL/JPA/EntityManager, записи каталога, arbitrary HTTP URLs, booking
или данных другого чата. Сервисы не получают factual values из model prose.
Memory и знания модели не заменяют чтение собственных данных.

## Гибридный flow и объяснение

Планируемый turn:

```text
user + bounded memory + server criteria
→ ChatClient: tool choice / clarification
→ Java: validate exactly one request and arguments
→ tool → existing service → trusted DTO
→ optional second ChatClient call: Structured Output, no tools
→ Java: validate explanation, build factual cards
→ Telegram sendMessage
→ on success: save selection and safe memory
```

Модель возвращает **ExplanationPlan**, не свободную factual paragraph:

- position: 1–3 внутри текущего результата;
- reasonCodes: 1–2 из allowedReasonCodes именно этого candidate;
- phrasing: NEUTRAL / WARM / COMPACT.

Java проверяет position и membership reason codes и формирует 1–2 короткие фразы.
Модель выбирает подтверждённые причины и стиль, не добавляет ресторанные факты.
Второй call получает только own projection: позиции, supported tags, allowed reasons
и пожелания. Без names/addresses/phones/URLs/ratings/Google payload и raw tool protocol.
Полный own DTO остаётся у Java для factual card. Invalid/failed explanation даёт Java
fallback; готовый search result сохраняется без нового поиска и перестановки ID.

Factual renderer читает только trusted service DTO. Чек подписывается как
ориентировочный; menu показывает PARTIAL/source/date. Будущие Google сведения
отображаются отдельно с attribution/link/live checkedAt и не пересказываются LLM.

## Bounded execution

Ограничения планируемой orchestration обеспечиваются Java, а не текстовой инструкцией:

- Максимум **1 tool execution** и **2 model calls** на пользовательский turn.
- Budget резервируется до вызова; failed attempt также расходует попытку.
- Multiple/unknown tool requests отклоняются целиком до service calls.
- Invalid arguments не исполняются; NO_RESULTS не запускает relaxed search.
- Второй model call не получает tools, включая client defaults/advisors.
- Если второй call потрачен на repair первого malformed response, explanation
  заменяется Java fallback; третьего call нет.
- Нет autonomous loops, скрытых SDK/advisor/HTTP retries и while-until-success.
- Планируемый deadline — 30 секунд; timeout и input/output caps задаются в его пределах.
- Вход ограничивается разумной длиной, ориентир — 2000 символов.

Version-specific механизм 1.1.8: explicit callbacks, `internalToolExecutionEnabled(false)`,
`parallelToolCalls(false)` и ручной `ToolCallingManager.executeToolCalls` после atomic
precheck. `@Tool(returnDirect=true)` сам по себе не ограничивает число заявленных tools.
Tools не регистрируются глобально на клиенте второго запроса.

У Spring AI 1.1.8 retry default допускает несколько attempts; планируемая конфигурация
задаёт `spring.ai.retry.max-attempts=1`, при manual model construction — explicit
RetryTemplate maxAttempts(1), без дополнительных transport retries. Native output
задаётся ResponseFormat.Type.JSON_SCHEMA с strict schema; BeanOutputConverter
конвертирует DTO, но не подтверждает смысл reasons. APIs другой major версии сюда
не переносятся. Connect/read failure и refusal/truncation требуют отдельных checks.

## Общие tool contracts

Это planned DTO contracts, не текущие Java tool classes.

Result: status OK / NO_RESULTS / NEED_CLARIFICATION / NOT_FOUND / DATA_UNAVAILABLE /
INVALID_INPUT / TEMPORARILY_UNAVAILABLE; bounded own data, warnings, missingFields
при уточнении, source/verifiedAt там, где относятся к данным. JPA entities, SQL,
raw provider payload, HTML, stack traces, secrets и чужие данные не передаются модели.

Trusted ToolContext формируется сервером: chatId, generation, currentCriteria,
currentSelectionVersion, исходная реплика. Эти значения не model arguments;
доступ и scope проверяет Java.

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
Execution: normalize/merge → validate → clarify missing fields либо существующий Java search.
Output: normalizedCriteria, ordered own candidates, estimated total, allowed reasons,
warnings и источники. Missing fields → NEED_CLARIFICATION; invalid values → INVALID_INPUT;
нет совпадений → NO_RESULTS без нового поиска.

### Reference input для menu/details

Ровно один selector: ordinal=1/2/3, last=true либо name. restaurantId не является
произвольным argument модели. Java ReferenceResolver использует текущую selection;
при её отсутствии имя может разрешаться внутри own catalog.
Normalized exact name matching, без fuzziness. Несколько филиалов с одним именем
требуют уточнения. Сложные сравнения прошлых подборок не поддерживаются.

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

**PLANNED:** MessageWindowChatMemory + JdbcChatMemoryRepository + PostgreSQL,
без отдельной UserProfile/history entity. Начальное окно — до 20 обычных сообщений;
дополнительно ограничивается model context по token/input budget.

```text
conversationId = "telegram:" + chatId + ":" + generation
```

ID формирует сервер; chatId — 64-bit. Личные чаты изолированы, групповой chat не
получает персональную memory. ConversationService явно читает/пишет/очищает memory;
одновременное автоматическое advisor write и ручное сохранение не используются.
Сохраняются user message и safe assistant projection реально отправленного результата,
без Google content, serialized tool results и промежуточного tool protocol.
Factual follow-up перечитывает БД, даже если похожий ответ есть в памяти.

`/new` обрабатывается Java без AI: очистить old memory, criteria/selection,
увеличить generation. Обычный restart должен сохранять актуальный state/memory.
TTL/selection expiry не являются обязательными; окно ограничивает длину разговора,
но не общее число чатов. Схема planned storage: [DATABASE](DATABASE.md#разговор-и-последняя-подборка).

## ConversationState и Selection context

CurrentCriteria хранят нормализованные значения. Missing fields вычисляются из них.
SelectionVersion и SelectionItem хранят только текущие position/restaurantId;
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
старую selection; menu/details её не заменяют. Явный failed send не активирует новую.
Crash между отправкой и DB commit возможен; exactly-once/outbox не проектируются.
При failed selection save сомнительный контекст в этом процессе очищается и
пользователю предлагается назвать ресторан. Если callbacks появятся, проверяются
chatId + generation + selectionVersion.

## AI eval — PLANNED

Tests должны проверять tool/arguments, service result, clarification, references,
конечный renderer и call limits. Live eval запускается отдельно от обычных Maven tests.
Фиксируются model/settings и Clock; fixture не выдаётся за реальный каталог.

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
