# TASK-00 — Feasibility evidence

Дата проверки: **2026-10-02**, Europe/Minsk; дополнено проверкой AIAI.BY. Отчёт фиксирует результаты исследования до реализации и их ограничения, а не готовность всего MVP. Состояния ниже относятся к feasibility-проверке; текущий прогресс реализации принадлежит [BACKLOG.md](BACKLOG.md). Действующие contracts остаются в документах-владельцах.

## 1. Executive Result

```text
TASK-00 STATUS: READY FOR USER APPROVAL
TASK-00 RECOMMENDATION: READY FOR USER APPROVAL
TECHNICAL READINESS FOR TASK-01: READY
```

Технический путь подтверждён: обе линии Maven dependencies собираются на Java 21; AIAI.BY authentication **PASS / HTTP 200**, фактический ID **gpt-4.1-mini** найден через `/v1/models`. Basic completion, raw tool calling и реальная цепочка **Spring AI 1.1.8 → AIAI.BY → модель → Java tool → native structured output: PASS**. Успешный turn: 2 model calls, 1 tool execution, 2 chat HTTP requests, без наблюдаемых лишних вызовов. **AIAI_API_KEY = SET** в live probe, значение и фрагменты не сохранялись. После отдельного пересмотра remaining gates **обязательных технических blocker для TASK-01 нет; TASK-00 READY FOR USER APPROVAL**. Account tariff/quotas, branch data и PostgreSQL acceptance относятся к последующим задачам; deadline/weekly time и дополнительная rubric курса — planning inputs. Требования проекта достаточны для рекомендованной line A, без выдуманного course approval. Google DEFERRED только до TASK-10. TASK-00 не DONE, stack/scope не утверждены автоматически, TASK-01 не начата. При пересмотре API probes не повторялись.

Уровни evidence нельзя смешивать:

| Проверка | Результат | Граница подтверждения |
|---|---|---|
| A: dependency resolution, Java compilation, 10 tests | PASS | Изолированный probe; не production context/Swagger endpoint |
| B: dependency resolution, Java compilation, 3 tests | PASS | API/class smoke; не полный аналог capability suite A |
| Spring AI tool dispatch, native format, guards, retry | PASS | Синтетические ответы и loopback HTTP; не выбор tool моделью |
| AIAI credential presence | PASS | AIAI_API_KEY = SET; значения/фрагменты не выводились |
| AIAI HTTPS/authentication check | PASS | GET /v1/models HTTP 200; discovered gpt-4.1-mini |
| Live AI / native schema adherence | PASS | Basic/raw PASS; Spring live JUnit 1/0/0/0, 2 calls + 1 tool; один успешный сценарий, не полный eval |
| JDBC ChatMemory capability | PASS | PostgreSQL dialect существует; H2 window/isolation/clear работают |
| Реальный PostgreSQL acceptance | BLOCKED_BY_CREDENTIALS | Локальный сервер требует пароль; H2 не заменяет acceptance |
| Рестораны/меню | PARTIAL | Источники и цены проверены; сетевые branch applicability остаются открытыми |
| Google docs/pricing | PASS | Live gate BLOCKED_BY_CREDENTIALS, итог DEFERRED |
| Дополнительная course rubric / deadline | USER_INPUT_REQUIRED | N7 / N6 — PLANNING_INPUT; проектные требования и совместимый candidate stack уже зафиксированы |

Проверки выполнялись отдельно от production application. Их результаты, версии, сценарии и границы подтверждения приведены в этом отчёте. Публичная документация не зависит от исследовательских scripts, dependency trees и временных logs. Результаты feasibility не подменяют tests приложения и PostgreSQL acceptance.

## 2. Course Requirements

Проверены проектные требования, шесть рабочих docs, историческая спецификация и доступные локальные учебные материалы. При пересмотре N7 заново сопоставлены существующие проектные требования, без API probes. Отдельного syllabus/rubric с обязательными версиями не найдено; этот факт сохраняется, но **не является техническим blocker bootstrap**. Соседний учебный pom с Boot 4 не доказывает разрешение курса на Boot 4. Исторические рекомендации не заменяют действующие docs.

**N7 для выбора bootstrap stack: достаточно проектного evidence.** Проектный стек в sections 3–4 включает Java 21, один Boot application/Maven module, PostgreSQL/JPA/Hibernate/Flyway/REST/springdoc/Spring AI/H2. [Product MUST](PRODUCT.md#mvp--must) задаёт JUnit/Mockito и delivery; [Architecture ADR](ARCHITECTURE.md#adr-summary) закрепляет persistence/REST/deployment и выбор version line в TASK-00; [AI version gate](AI.md#version-gate-перед-bootstrap) требует exact compatible versions и предпочитает JUnit 5. Sections 3–4 дают проверенный candidate **Java 21 / Boot 3.5.16 / AI 1.1.8 / JUnit 5** и весь dependency set, live evidence подтверждает центральный AI flow. Документированного требования, противоречащего A, не найдено. Этого достаточно, чтобы вынести A на утверждение пользователя; отсутствие дополнительного course confirmation не оставляет N7 blocker.

| Requirement | Evidence в проекте | Status требования курса |
|---|---|---|
| Java | Проектный стек: Java 21 (section 3) | UNKNOWN — requires user/course confirmation |
| Разрешённые Boot versions | TASK-00 требует сравнить A/B | UNKNOWN — requires user/course confirmation |
| Обязателен Boot 3 | Нет course evidence | UNKNOWN — requires user/course confirmation |
| Разрешён Boot 4 | Нет course evidence | UNKNOWN — requires user/course confirmation |
| PostgreSQL | Выбран как main DB в DATABASE | UNKNOWN — requires user/course confirmation |
| H2 | Выбран для части тестов | UNKNOWN — requires user/course confirmation |
| REST | Четыре endpoints в ARCHITECTURE | UNKNOWN — requires user/course confirmation |
| Swagger | springdoc в проектном стеке | UNKNOWN — requires user/course confirmation |
| Flyway / Liquibase | Проект выбирает Flyway | UNKNOWN — requires user/course confirmation |
| Docker | DEPLOYMENT выбирает Docker | UNKNOWN — requires user/course confirmation |
| Docker Compose | DEPLOYMENT выбирает Compose | UNKNOWN — requires user/course confirmation |
| Spring AI | Проектное требование | UNKNOWN — requires user/course confirmation |
| JUnit | JUnit 5 желателен в текущих docs | UNKNOWN — requires user/course confirmation |
| Mockito | Предусмотрен backlog/tests | UNKNOWN — requires user/course confirmation |
| Spring Security | Не входит в текущий MVP | UNKNOWN — requires user/course confirmation |
| Deployment | Compose → Dokploy → VPS | UNKNOWN — requires user/course confirmation |
| CI/CD | CI — SHOULD после MVP | UNKNOWN — requires user/course confirmation |

Таблица выше описывает неизвестную **внешнюю rubric курса**, не отсутствие проектных требований. Её UNKNOWN не превращается в PASS. N7 сохраняет **USER_INPUT_REQUIRED** для дополнительной rubric, классификация **PLANNING_INPUT**; проектные требования достаточны для bootstrap candidate. Security/CI не добавлены. Утверждение stack/scope пользователем остаётся отдельным workflow step, не дополнительным техническим подтверждением курса.

## 3. Recommended Dependency Stack

**RECOMMENDED STACK: A, READY FOR USER APPROVAL.** Проектные требования и существующее compatibility/live evidence достаточны для Java 21 / Boot 3.5.16 / AI 1.1.8. Дополнительная rubric курса — PLANNING_INPUT, не prerequisite выбора bootstrap candidate. B остаётся проверенной альтернативой; оснований менять рекомендованную A нет. Это recommendation для утверждения пользователем, не автоматическое утверждение версии или объявление внешней rubric известной.

| Компонент | Exact version / Maven coordinate | Причина / official source |
|---|---|---|
| Java | 21; probe: Temurin 21.0.11+10 | Проектный выбор; [Boot 3.5 requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html) допускает Java 21 |
| Maven | 3.9.16, фактически выполнен | Выше минимального 3.6.3 из Boot requirements |
| Spring Boot | `org.springframework.boot:spring-boot-starter-parent:3.5.16` | Stable patch линии A; [официальный parent](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/3.5.16/spring-boot-starter-parent-3.5.16.pom) |
| Spring AI | `org.springframework.ai:spring-ai-bom:1.1.8`, import BOM | [Version-pinned getting started](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/getting-started.adoc); Boot 3.5, подтверждено probe |
| AI starter | `org.springframework.ai:spring-ai-starter-model-openai`, 1.1.8 из AI BOM | Единственный первоначальный provider starter; live AIAI access/tools/native schema PASS |
| JDBC memory starter | `org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc`, 1.1.8 | Framework memory, без новой платформы |
| springdoc | `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.9.1` | [v2 official docs](https://springdoc.org/v2/), [2.9.1 parent POM](https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi/2.9.1/springdoc-openapi-2.9.1.pom) использует Boot 3.5.16 |
| Spring Framework | 6.2.19 | Boot BOM, не отдельный override |
| Spring Data JPA | `org.springframework.boot:spring-boot-starter-data-jpa`; Data JPA 3.5.13 | Boot-managed Spring Data BOM 2025.0.13 |
| Hibernate | `org.hibernate.orm:hibernate-core:6.6.53.Final` | Boot BOM, совместно разрешён с JPA |
| PostgreSQL Driver | `org.postgresql:postgresql:42.7.11` | Boot BOM; [driver documentation](https://jdbc.postgresql.org/documentation/) |
| H2 | `com.h2database:h2:2.3.232`, test scope | Boot BOM; реальный JDBC memory test на H2 PASS |
| Flyway | `org.flywaydb:flyway-core:11.7.2` + `flyway-database-postgresql:11.7.2` | PostgreSQL support — отдельный module, обе версии из Boot BOM |
| JUnit | `org.junit.jupiter:junit-jupiter:5.12.2` через starter-test | Boot default, 10 тестов прошли |
| Mockito | `org.mockito:mockito-core:5.17.0` через starter-test | Boot default |
| Telegram | `com.github.pengrad:java-telegram-bot-api:10.1.0` | [Официальный repository](https://github.com/pengrad/java-telegram-bot-api); plain library, без Boot starter |
| Maven compiler / surefire | 3.14.1 / 3.5.6 | Boot parent; compile/test выполнены |
| Boot Maven plugin | 3.5.16 | Из parent; production repackage здесь не выполнялся |

Источник управляемых версий: [Boot 3.5.16 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/3.5.16/spring-boot-dependencies-3.5.16.pom). Фактически разрешённые версии линии A приведены в таблице выше. Зависимости Boot/JPA/Flyway/tests не пинить поверх BOM без доказанной причины; AI BOM импортировать отдельно, springdoc/Telegram фиксировать явно. Проверка содержала web/data-jpa/test starters и выполнялась отдельно от корневого application.

**Springdoc evidence boundary:** официальный matrix на v2 ещё перечисляет Boot 3.5 → springdoc 2.8.x; страница рекомендует текущую 2.9.1, её published POM использует именно Boot 3.5.16. Поэтому 2.9.1 выбрана по двум официальным свидетельствам и successful compile/class smoke. Работа `/v3/api-docs` и UI должна быть проверена context smoke в TASK-01, сейчас PASS только для dependency compatibility.

## 4. Version Decision

| Aspect | OPTION A | OPTION B |
|---|---|---|
| Java | 21, официально допустима | 21, официально допустима |
| Boot / AI | **3.5.16 / 1.1.8** | **4.1.1 / 2.0.1** |
| Framework / Data JPA | 6.2.19 / 3.5.13 | 7.0.9 / 4.1.1 |
| Hibernate | 6.6.53.Final | 7.4.5.Final |
| PG JDBC / H2 | 42.7.11 / 2.3.232 | 42.7.13 / 2.4.240 |
| Flyway core + PG module | 11.7.2 | 12.4.0 |
| springdoc | 2.9.1, v2 | 3.1.1, v3 |
| Telegram | Pengrad 10.1.0 | Pengrad 10.1.0 |
| Default JUnit / Mockito | **5.12.2 / 5.17.0** | **6.0.3 / 5.23.0** |
| Compiler / surefire | 3.14.1 / 3.5.6 | 3.15.0 / 3.5.6 |
| Tools API | model-level internal loop; manual manager supported | advisor-oriented orchestration; manager has per-tool/total limits |
| Native OpenAI schema API | `responseFormat(JSON_SCHEMA)` | `outputSchema(...)` exists |
| Probe | PASS, 10 tests | PASS, 3 API/class tests |

B официально поддерживает Boot 4.0/4.1: [AI getting started](https://docs.spring.io/spring-ai/reference/getting-started.html). [Boot 4 requirements](https://docs.spring.io/spring-boot/system-requirements.html) и [Boot 4.1.1 BOM](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom) согласуются с фактически разрешёнными версиями линии B в таблице выше. [springdoc v3](https://springdoc.org/) поддерживает Boot 4; [3.1.1 published POM](https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi/3.1.1/springdoc-openapi-3.1.1.pom) построен с Boot 4.1.0. Boot 4.1.1 + springdoc 3.1.1 прошли class/compile smoke, не endpoint smoke.

**JUnit 5 compatibility для B: PARTIAL.** Native Boot 4.1 starter-test разрешает JUnit 6, а не JUnit 5. Jupiter API похож, но это не проверка JUnit 5. Если курс строго требует пятую major, A удовлетворяет этому без overrides; ручной downgrade B здесь не подтверждался.

Breaking changes B: модульные Boot starters (probe использует `spring-boot-starter-webmvc`, `spring-boot-starter-flyway`), Jackson 3 как default, Hibernate 7, JUnit 6 и изменённые Spring AI tools/structured APIs. [Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide), [Spring AI upgrade notes](https://docs.spring.io/spring-ai/reference/upgrade-notes.html). Дополнительные built-in limits B полезны, но не убирают необходимость отклонять multiple calls до исполнения. Для простого учебного монолита A достаточно; переход ради новизны не нужен. API 2.x не переносится в код 1.1.

**Telegram comparison:**

| Library | Java/Boot | Возможности / offset | Maintenance, dependency, простота | Status |
|---|---|---|---|---|
| Pengrad 10.1.0 | Java 21 фактически tested в A и B; независим от Boot | `setUpdatesListener`, `GetUpdates`, `SendMessage`, `Update.callbackQuery`; listener подтверждает update ID / ALL / NONE | [README](https://github.com/pengrad/java-telegram-bot-api), [releases](https://github.com/pengrad/java-telegram-bot-api/releases); одна dependency `com.github.pengrad:java-telegram-bot-api` | PASS для локального API smoke; live polling DEFERRED |
| Rubenlagus TelegramBots 10.3.0 | Java floor 17 по [official parent](https://raw.githubusercontent.com/rubenlagus/TelegramBots/master/pom.xml), Java 21 допустима; plain modules независимы от Boot | longpolling application/session + client, callbacks/update consumers; library polling session управляет last update ID | [Repository/docs](https://github.com/rubenlagus/TelegramBots), [published metadata](https://repo.maven.apache.org/maven2/org/telegram/telegrambots-longpolling/maven-metadata.xml); `org.telegram:telegrambots-longpolling:10.3.0` + `telegrambots-client:10.3.0`; больше объектов | PARTIAL: docs/version проверены, отдельный runtime probe не выполнялся |

Выбор: **Pengrad 10.1.0**, минимальный plain client. Telegram Bot API уже 10.3; Pengrad 10.1.0 не объявляется покрывающим новые 10.3 features, они не нужны MVP. [getUpdates](https://core.telegram.org/bots/api#getupdates): подтверждение через offset = обработанный updateId + 1; README Pengrad описывает это и listener acknowledgment. Персистентность offset/restart/crash и реальные callbacks не доказаны DTO tests; проверить TASK-06/11. Собственный checkpoint заранее не вводить.

## 5. AI Provider Comparison

Цены ниже: USD за **1M input / output tokens**, без cache discount, batch, налогов и валютной конвертации, если явно не оговорено. Framework support проверяется по **1.1.8**, не по плавающему reference 2.x. AIAI live latency/usage измерены в sections 6–8/15; реальные account quotas/tariff/debit не подтверждены. Для остальных providers account/runtime access и latency остаются UNKNOWN.

| Provider / model | Spring AI support A | Tools | Structured output | Cost | Regional/access considerations | Status |
|---|---|---|---|---|---|---|
| **AIAI.BY / gpt-4.1-mini (discovered)** | Реальный OpenAiChatModel/ChatClient 1.1.8 PASS | Raw и Spring tool execution PASS | Native JSON_SCHEMA strict + DTO/semantic validation PASS | Usage получен; AIAI tariff/account debit PARTIAL | Key SET; GET /v1/models HTTP 200, live requests прошли из текущей среды; [provider docs](https://aiai.by/docs) | PASS capability; PARTIAL account economics |
| OpenAI `gpt-4.1-mini-2025-04-14` | `spring-ai-starter-model-openai:1.1.8`; API compiled/tested | Официально supported | Native JSON Schema strict; wire shape tested локально | **$0.40 / $1.60** | Belarus отсутствует в supported countries; реальный account/runtime country неизвестен | PARTIAL |
| Anthropic Claude Haiku 4.5 | `spring-ai-starter-model-anthropic:1.1.8` [pinned docs](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chat/anthropic-chat.adoc) | Supported | Native output API именно 1.1.8 для нынешних Claude моделей не подтверждён; JSON DTO conversion не доказательство | **$1 / $5** | Belarus отсутствует в [supported regions](https://platform.claude.com/docs/en/api/supported-regions); billing не проверен | PARTIAL |
| Google Gemini 3.1 Flash-Lite | `spring-ai-starter-model-google-genai:1.1.8` [pinned docs](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chat/google-genai-chat.adoc) | Provider supports function calling | Provider schema support существует; exact 1.1.8 wiring/current model live не tested | **$0.25 / $1.50** paid tier | Belarus отсутствует в [available regions](https://ai.google.dev/gemini-api/docs/available-regions); Maps billing — отдельный продукт | PARTIAL |
| DeepSeek `deepseek-flash` (V4.1 Flash) | `spring-ai-starter-model-deepseek:1.1.8` [pinned docs](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chat/deepseek-chat.adoc); новый model ID live не tested | Function calling; strict tools в beta endpoint | JSON mode документирован; native response JSON Schema для ExplanationPlan **не подтверждён**. Strict tool arguments не равны strict response schema | Peak cache-miss **$0.30 / $1.20**, off-peak **$0.15 / $0.60** | API/payment из среды пользователя UNKNOWN | PARTIAL; native explanation gate не пройден |
| Local Ollama `qwen3:8b` | `spring-ai-starter-model-ollama:1.1.8`; [Spring docs](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html) | Модель отмечена tools-capable | Ollama `format` JSON schema; actual model reliability не tested | Нет token API charge; требуется собственное оборудование | VPS RAM/CPU неизвестны; не заменяет remote provider автоматически | DEFERRED |

Official model/pricing evidence: [OpenAI model](https://developers.openai.com/api/docs/models/gpt-4.1-mini), [Anthropic pricing](https://platform.claude.com/docs/en/about-claude/pricing), [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing), [DeepSeek pricing](https://api-docs.deepseek.com/quick_start/pricing), [DeepSeek tools](https://api-docs.deepseek.com/guides/tool_calls), [JSON mode](https://api-docs.deepseek.com/guides/json_mode), [Ollama model](https://ollama.com/library/qwen3:8b), [Ollama schema](https://docs.ollama.com/capabilities/structured-outputs).

Limits / latency expectations:

- OpenAI model page: free tier unsupported, Tier 1 example 500 RPM / 10,000 RPD / 200,000 TPM. Это не лимиты пользователя. Модель без reasoning step — разумный кандидат для коротких двух вызовов; измерений latency нет.
- Anthropic limits зависят от account/model tier, [rate limits](https://platform.claude.com/docs/en/api/rate-limits); Haiku — кандидат для коротких запросов, не подтверждённый SLA. В reference AI 2.x появились иные native APIs — не считать их доступными в 1.1.8.
- Gemini limits project/model/tier-specific, [rate limits](https://ai.google.dev/gemini-api/docs/rate-limits); free tier и paid tier отличаются также условиями обработки данных. Фактические квоты UNKNOWN, latency не измерена.
- DeepSeek публикует [concurrency limits](https://api-docs.deepseek.com/quick_start/rate_limit) для Flash (2500), не универсальный RPM пользователя; очереди и latency не измерены. Старый `deepseek-chat` не выбран по памяти.
- Ollama ограничивается hardware/parallelism и длиной context; CPU inference может не уложиться в 30 секунд. RAM/latency benchmark отсутствует, локальная модель не выбрана.

Нет доказанного «минимально дешёвого и достаточного» model до eval. [GPT-4o-mini](https://developers.openai.com/api/docs/models/gpt-4o-mini) — более дешёвый non-reasoning вариант с tools/schema ($0.15 / $0.60), но качество русских criteria не сравнивалось; проверить его после доступа прежде, чем объявлять baseline минимально достаточным по цене. Более дешёвый OpenAI nano не выбран: [модель](https://developers.openai.com/api/docs/models/gpt-4.1-nano) deprecated и [shutdown](https://developers.openai.com/api/docs/deprecations) указан на 2026-10-23. GPT-4.1-mini на проверенной странице deprecated не помечен. Все даты lifecycle перепроверять перед запуском.

## 6. Selected AI Provider

```text
AI PROVIDER: AIAI.BY — provider запрошенного live gate
API ROOT: https://api.aiai.by/v1
MODEL: GPT-4.1 Mini; actual API model ID: gpt-4.1-mini (GET /v1/models)
SPRING AI STARTER: org.springframework.ai:spring-ai-starter-model-openai:1.1.8
WHY: проверить фактическую OpenAI-compatible интеграцию с полученным пользователем key
PRICE: фактический AIAI tariff/account debit не проверен
LIMITS: AIAI account-specific, не подтверждены
TOOL CALLING: PASS — raw + Spring AI 1.1.8 / Java execution
STRUCTURED OUTPUT: PASS — native JSON_SCHEMA strict, DTO and semantic checks
STATUS: PASS capability; PARTIAL account tariff/limits
```

Authenticated `GET https://api.aiai.by/v1/models` с Bearer key из process environment: **PASS, HTTP 200, 372 ms**. Ответ содержит ID **gpt-4.1-mini**; model access PASS. Один basic request без tools: **HTTP 200, 862 ms**, ответ соответствует `OK`, OpenAI-compatible `choices/message/content` и usage получены. Модель не подменялась и upstream snapshot ID не угадывался.

Java 21 / Spring RestClient в итоговом run: **GET HTTP 200, 725 ms**; затем OpenAiApi + OpenAiChatModel + ChatClient 1.1.8 выполнили реальный tool turn и structured response. Spring AI connectivity **PASS**. Итого 3 application HTTP attempts: 1 discovery + 2 chat requests. Сертификатная проверка включена; redirects/retries отключены. Отдельный HTTP status каждого Spring chat response probe не сохраняет; успешные mapped ChatResponse, tool/DTO и usage подтверждены.

История: предыдущая сессия имела TLS failures до HTTP (Python 7448 ms, Java 7689 ms). На повторном discovery соединение восстановилось; причина прежнего сбоя не установлена. Старые безключевые diagnostics сохранены как исторические, не текущий blocker. В этом продолжении два Spring runs остановились до Java execution на проверке criteria; подробности и явные повторы — section 7. Они не скрытые retries и не исключены из расхода.

[Provider docs](https://aiai.by/docs) задают API root и `/v1/models`; [pricing](https://aiai.by/pricing) описывает оплату BYN. Заявления не заменяют live probe и не подтверждают баланс/тариф пользователя. Прямой OpenAI из прежнего discovery остаётся сравнительным вариантом; его цены/региональные ограничения не выданы за AIAI account evidence.

**Version-specific base URL:** [Spring AI 1.1.8 docs](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chat/openai-chat.adoc) и [OpenAiApi source](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/api/OpenAiApi.java) подтверждают default completions path `/v1/chat/completions`. Future mapping: `spring.ai.openai.api-key` ← runtime `AIAI_API_KEY`; `spring.ai.openai.base-url=https://api.aiai.by`; `spring.ai.openai.chat.completions-path=/v1/chat/completions`; `spring.ai.openai.chat.options.model` ← discovered ID. Альтернатива: base-url с `/v1` и path `/chat/completions`; не соединять `/v1` base с default `/v1/...`. Probe compiled с origin base + explicit path, production properties/yml не создавались.

Environment проверялось только как SET / NOT_SET; локальный файл с environment variables не открывался, key уже доступен процессу. Key не передавался в CLI arguments, не сохранялся в project .env/report/README/config/logs. Ошибки сохранялись только как тип/фиксированная категория; raw body, headers и chain-of-thought не записывались.

Результаты discovery, basic completion, raw tools и Spring live проверки приведены в этой секции; tool/schema/limits подробно описаны в sections 7–10. Историческая TLS диагностика не повторялась; её failure не подменяет результат успешной повторной проверки. Значение ключа в отчёте отсутствует.

## 7. AI Capability Probe

**Local framework result: PASS. AIAI live model result: PASS** для одного русского сценария с синтетическим каталогом. Модель самостоятельно выбрала известный tool с ожидаемыми criteria; SQL/repositories не предоставлялись. Это не полный multi-turn eval и не доказательство качества на иных запросах.

Запрос: «Сегодня нас двое, хотим итальянскую кухню до 150 BYN.»

Fake tool `searchRestaurants(Criteria)` зарегистрирован через `@Tool(returnDirect=true)` и `ToolCallbacks.from(...)`; fictional in-memory result содержит ID 101. Типы test schema:

```json
{
  "type": "object",
  "properties": {
    "criteria": {
      "type": "object",
      "properties": {
        "guests": {"type": "integer"},
        "cuisine": {"type": "string"},
        "totalBudgetByn": {"type": "integer"}
      },
      "required": ["guests", "cuisine", "totalBudgetByn"]
    }
  },
  "required": ["criteria"]
}
```

Это краткое отображение generated test schema, не новый production contract. Actual **fixture** arguments: `{"criteria":{"guests":2,"cuisine":"ITALIAN","totalBudgetByn":150}}`. Manager передал их Java tool; выполнено ровно одно исполнение, вернулся `OK`, `fictionalIds:[101]`, `guests:2`, `returnDirect=true`. Guards отклонили два tool requests и `executeSQL` до исполнения; malformed JSON не вызвал fake service. В полном loopback flow выполнено **2 HTTP model requests + 1 tool**, во втором request tools отсутствуют. Реальных model calls: **0**. Chain-of-thought не запрашивался и не сохранён.

Результат отдельных capability tests линии A: **10 tests, 0 failures, 0 errors, 0 skipped**. Линия B: **3 / 0 / 0 / 0**. Это результаты feasibility-проверок, а не корневого test suite приложения.

Продолжение AIAI включало raw HTTP и отдельную Java/Spring AI проверку. Старые successful tests A/B не повторялись и не менялись. Java проверка compiled на JDK 21 с прежней line A; итоговый live run: **1 test, 0 failures, 0 errors, 0 skipped / BUILD SUCCESS**. Второй model request получает bounded conversation с фактическим синтетическим tool result и native schema, без tools; это capability probe, не изменение production ExplanationPlan.

Live запрос: «Сегодня в 21:00 нас двое, хотим итальянскую кухню, общий бюджет до 150 BYN. Подбери ресторан.» **Actual raw и Spring args:** guests=2, totalBudgetByn=150 (**общий**), cuisine=ITALIAN, time=21:00. Raw поля flat, Spring callback имеет `criteria` wrapper. Оба выбрали один `searchRestaurants`; finish reason `tool_calls` / mapped `TOOL_CALLS`. Choice auto, parallel=false. Raw: 0 Java executions, HTTP 200, 1671 ms. Spring: **1 Java execution**, returnDirect=true, fictional ID 101, tool-response latency 1874 ms. Repositories/SQL отсутствуют.

Basic / raw tools / Spring model tools: **PASS**. Первые два Spring runs завершились validation failure, modelCalls=1/toolExecutions=0 каждый. Во втором подтверждены one tool call, `CRITERIA_MISMATCH`, usage 107/38/145; произвольные невалидные строки не сохранялись, точная причина mismatched field не установлена. После этого в disposable DTO заменена свободная cuisine string на enum и добавлены field descriptions для TOTAL budget/HH:mm. Generated schema проверена offline, затем один изменённый live run прошёл. Не утверждается, что enum был единственной причиной прежнего mismatch. Всего в этом продолжении **6 chat requests**: basic 1 + raw 1 + failed Spring 1+1 + successful Spring 2. Счётчик ≤2 применяется к каждому отдельному turn, не ко всем probes вместе.

## 8. Structured Output Probe

**API/wire/parser probe: PASS. AIAI live schema adherence: PASS** для малого DTO в успешном Spring turn.

В **1.1.8** `BeanOutputConverter<ExplanationPlan>` создаёт schema для test DTO `reasonCodes`, `summary`; обязательность полей задана `@JsonProperty(required=true)`. Через `OpenAiChatOptions.responseFormat` отправляется `ResponseFormat.Type.JSON_SCHEMA`, именованная schema, `strict=true`. Loopback server получил эти поля; valid fixture конвертируется. Malformed fixture вызывает exception и подлежит Java fallback; автоматического repair/retry нет. Regex/извлечение JSON из прозы не используются.

Converter не является полной независимой schema validation и не подтверждает смысл reasonCodes. Перед renderer нужны membership/position/length checks из [AI.md](AI.md#гибридный-flow-и-объяснение); provider refusal, truncated/empty response и unknown reason ведут к fallback, а не третьему вызову. Test `summary` разрешён пользовательским заданием только для capability DTO, он не заменяет restricted production ExplanationPlan.

[Pinned 1.1.8 structured output docs](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/structured-output-converter.adoc), [OpenAI structured output](https://developers.openai.com/api/docs/guides/structured-outputs). B `OpenAiChatOptions.outputSchema(...)` отдельно compile-tested; из этого нельзя выводить provider adherence.

AIAI принял native `json_schema` strict через API 1.1.8. DTO имеет ровно `reasonCodes` + `summary`; JSON shape, typed conversion, nonblank summary и membership reasonCodes проверены без regex/parser repair. Фактические reasonCodes: `CUISINE_MATCH`, `BUDGET_MATCH`; произвольный summary не сохранялся. Latency **963 ms**, input/output/total **184/36/220**. Tool calls во втором response отсутствуют. Это подтверждает один успешный native-schema response, не общее качество explanation или refusal/fallback на всех ошибках.

## 9. Tool Execution Limits

**Strategy: PASS в локальном 1.1.8 flow.** Это Java enforcement, не обещание system prompt. [Pinned tools docs](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/tools.adoc).

1. Один turn хранит model-attempt budget 2 и tool budget 1; reservation происходит **до** соответствующего вызова. Ошибка также расходует попытку.
2. Первый `OpenAiChatOptions`: explicit tool callbacks, `internalToolExecutionEnabled(false)`, `parallelToolCalls(false)`. Tools не подключаются глобально через client defaults/advisors.
3. Получить `ChatResponse`, проверить одну generation и **ровно один** известный tool request до `ToolCallingManager.executeToolCalls(prompt,response)`. Проверить typed args и доменные ограничения. Multiple/unknown запросы целиком отклонить, не выполнить «первый подходящий».
4. `@Tool(returnDirect=true)` делает result terminal для framework auto-flow, но **сам по себе не ограничивает число заявленных tools**. Manual manager + precheck необходимы.
5. Второй вызов — explicit prompt/options без tools/default tools, с native explanation schema. Loopback test подтверждает отсутствие `tools` в HTTP JSON. Не отправлять всю tool conversation заново ради скрытого loop.
6. Если второй вызов потрачен на исправление первого malformed ответа, explanation пропустить; Java fallback. Нет while-until-success, третьего model call или повторного поиска после NO_RESULTS.

**Hidden retries:** выбранный `OpenAiChatModel` 1.1.8 вызывает OpenAiApi через Spring Retry, не официальный OpenAI Java SDK. [Exact source](https://github.com/spring-projects/spring-ai/blob/v1.1.8/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java) и [retry properties](https://github.com/spring-projects/spring-ai/blob/v1.1.8/auto-configurations/common/spring-ai-autoconfigure-retry/src/main/java/org/springframework/ai/retry/autoconfigure/SpringAiRetryProperties.java): default maxAttempts=10; 4xx обычно non-transient при onClientErrors=false, configurable code lists; server errors могут вызвать retry. Это способ обойти model budget, если оставить defaults.

В TASK-05 применить `spring.ai.retry.max-attempts=1`; при manual model construction передать `RetryTemplate.builder().maxAttempts(1).build()`. Не включать HTTP-client automatic retries, дополнительные retry interceptors/advisors. Probe с HTTP **503** и explicit template зафиксировал **ровно 1** сетевой request. Connect/read failure поведение конкретного будущего transport отдельно проверить; одного Java counter без отключения сетевых повторов недостаточно. Production config сейчас не создавалась.

B имеет `ToolCallingManager.builder().maxCallsPerTool(1).maxTotalToolCalls(1).resolutionFallbackEnabled(false)` — эти методы compile-tested в **2.0.1**, [official API](https://docs.spring.io/spring-ai/docs/2.0.1/api/org/springframework/ai/model/tool/DefaultToolCallingManager.Builder.html). Они не являются APIs A и не заменяют общий model counter или atomic precheck multiple requests. Validation advisors с [automatic retry](https://docs.spring.io/spring-ai/reference/api/structured-output/validation.html) не подключать без учёта бюджета. Не создавать универсальный agent framework.

Предыдущие local limits сохранены; **AIAI live limits PASS** в успешном turn: modelCalls=2, toolExecutions=1, chatRequests=2, unexpectedCalls=false. Использован прежний счётчик turn budget, explicit callbacks/manual manager/tool-free second options, maxAttempts=1 и JDK retry settings; механизм не переписывался. **Unexpected automatic retries: NO observed** во всех трёх Spring runs. Failure-path retry behaviour у provider остаётся **PARTIAL**: намеренные платные HTTP errors не провоцировались; старая 503 fixture PASS является отдельным local evidence. Interceptor считает application HTTP attempts, не внутренние действия инфраструктуры provider.

## 10. Chat Memory Feasibility

```text
CAPABILITY: PASS
Telegram chatId → server-derived conversationId → JDBC PostgreSQL ChatMemory
POSTGRESQL LIVE ACCEPTANCE: BLOCKED_BY_CREDENTIALS
```

Spring AI **1.1.8** предоставляет `ChatMemory`, `JdbcChatMemoryRepository`, `MessageWindowChatMemory`, `PostgresChatMemoryRepositoryDialect` и официальный PostgreSQL schema resource. Дополнительные Redis/vector store/agent technologies не нужны. [Pinned memory documentation](https://github.com/spring-projects/spring-ai/blob/v1.1.8/spring-ai-docs/src/main/antora/modules/ROOT/pages/api/chat-memory.adoc).

Conversation ID задаёт Java: например `telegram:<chatId>:<generation>`, не модель и не пользовательский request. Window planned 20 messages; probe использовал 2, чтобы проверить eviction, два chat IDs, reopening repository и clear. Это JDBC **H2 in-memory** test, не disk restart и не PostgreSQL persistence acceptance.

Tool protocol/intermediate tool messages имеют ограничения memory advisors. Для данного contract выбрать manual read/add/clear и сохранять только user + безопасную assistant own projection; Google payload и tool request/result protocol не записывать. Не подключать одновременно advisor write и manual write. Criteria/selection остаются отдельным state, не transcript. `/new` сбрасывает их и меняет generation.

Future Flyway создаёт official schema выбранной версии в TASK-07; отключить framework initialization: `spring.ai.chat.memory.repository.jdbc.initialize-schema=never`. Ни одной project migration здесь нет.

PG/JPA/Flyway/H2 dependency classes совместно загрузились на Java 21 (обе линии). Для PostgreSQL добавлен database-postgresql Flyway module. Локальный psql client **17.10**, процессы PostgreSQL обнаружены; read-only `SELECT version()` через 127.0.0.1 отказал: `fe_sendauth: no password supplied`. Версия server и его permissions не подтверждены. `DB_URL`, `DB_PASSWORD`, `PGPASSWORD`, project config и pgpass отсутствуют. H2 не доказывает Hibernate PostgreSQL DDL, Flyway migration, constraints или restart; эти acceptance остаются обязательными позже.

## 11. Restaurant Proof-of-Data

Все источники проверены **2026-10-02**; `checkVerifiedAt=2026-10-02`, `hoursVerifiedAt=2026-10-02`. Филиалы выбраны явно. Published average check не найден: все оценки **DERIVED**, не статистический средний чек. Cuisine — curator classification по официальной концепции/блюдам.

Одинаковая методика: **одна явно выбранная полноценная основная позиция + один суп, один гость**, без алкоголя, напитков, десерта, доставки и чаевых. Не median, не цена любого заказа, не runtime BudgetEstimator. Примеры намеренно сохраняют состав, чтобы проверять provenance. Чеки относятся к этому food-only сценарию, а не гарантируют полный ужин.

| Restaurant / branch | Address | Cuisine | Estimated check / type | Check source и формула | Hours | Hours source | Status |
|---|---|---|---|---|---|---|---|
| Васильки | Минск, пр-т Независимости, 16 | Белорусская | **39.80 BYN / DERIVED** | Драники с мачанкой 22.90 + щи с лесными грибами 16.90; [драники](https://vasilki.by/img/menu/Драники_1.jpg), [суп](https://vasilki.by/img/menu/Супы_1.jpg) | Вс–Чт 08:00–23:00; Пт–Сб 08:00–01:00 следующего дня | [Официальный сайт / карта филиалов](https://vasilki.by/), [public branch data](https://vasilki.by/russian.220fb00a74cd50b43533.js?9788f2ac0dbe8fb505be) | PARTIAL: own hours/ценовой сценарий найден; applicability общего сетевого меню к филиалу не объявлена явно |
| Pizza Tempo | Минск, ул. Карла Маркса, 26 (на сайте «ул. Маркса, 26») | Итальянская / pizza-pasta | **32.70 BYN / DERIVED** | Carbonara 19.50 + грибной суп-крем 13.20; [пасты](https://tempo.by/img/menu/пасты%201.jpg), [супы](https://tempo.by/img/menu/супы%201.jpg) | Пн–Вс 10:00–23:00 | [Официальный сайт / филиалы](https://tempo.by/), [public branch data](https://tempo.by/russian.32824809da6027f28665.js?7b715f94a909d10b8c3e) | PARTIAL: общий restaurant menu, точная branch applicability требует подтверждения |
| Хинкальня, ТЦ Титан | Минск, пр-т Дзержинского, 104 | Грузинская | **44.00 BYN / DERIVED** | Шашлык из свинины 25 + харчо 19; [официальное меню филиала](https://www.titanminsk.by/kafe-i-restoranyi/restoran/menyu/) | Пн–Вс 12:00–24:00 | [Страница ресторана Титан](https://www.titanminsk.by/kafe-i-restoranyi/restoran/) | PASS для proof-of-data; меню помечено действующим с 24.06.2026 |

В branch JS дней недели используется порядок Пн…Вс; диапазон `[6,3]` у Васильков означает Вс–Чт, `[4,5]` — Пт–Сб. Это проверка публичных данных сайта, не расписание Google. 24:00 моделируется 00:00 следующего дня; праздничные исключения этим недельным расписанием не доказаны.

**N2 = PARTIAL.** Три real datasets найдены, hours и пример чека воспроизводимы; до seed TASK-02 подтвердить применимость сетевых restaurant menus к выбранным филиалам либо использовать другой подтверждённый branch source. Не выдавать цены доставки за dine-in. Для demo Italian/150 BYN/двое есть Pizza Tempo кандидат по own baseline и времени 21:00; это не гарантия наличия блюда/столика и не real search execution.

## 12. Menu Proof-of-Data

Не импортировались полные меню, фотографии/описания не предназначены для продукта. Проверены только несколько страниц и достаточно позиций для feasibility TASK-03. Все цены — BYN по белорусским официальным menu sources; точка/запятая — decimal separator. `menuVerifiedAt=2026-10-02`. Размер порций берётся из конкретного канала: совпадающее название не означает одинаковую порцию в delivery menu.

**Васильки**

```text
MENU SOURCE: vasilki.by → основное меню (3 выбранные страницы)
STATUS: PARTIAL (branch applicability), readable/prices/items: PASS
NUMBER OF USABLE ITEMS: 6 ниже
PRICE FORMAT: decimal comma, BYN; restaurant menu image pages
BRANCH-SPECIFIC: NO — общий сетевой источник, не отдельное меню Независимости 16
NOTES: ручная транскрипция; HTML delivery minsk.vasilki.by не подменяет этот канал
```

| Item | BYN | Portion |
|---|---:|---|
| Драники с мачанкой по-белорусски | 22.90 | 550 г |
| Драники с гуляшом из говядины | 25.10 | 470 г |
| Драники со свининой и грибами | 23.00 | 465 г |
| Щи с лесными грибами | 16.90 | 460 г |
| Домашняя солянка | 13.40 | 330 г |
| Борщ с ребрышком и смальцем | 14.20 | 455 г |

Sources: [драники 1](https://vasilki.by/img/menu/Драники_1.jpg), [супы 1](https://vasilki.by/img/menu/Супы_1.jpg), [супы 2](https://vasilki.by/img/menu/Супы_2.jpg). Картинки визуально читаемы; для 5–10 items достаточно ручного JSON seed, OCR/scraper не нужен. Имена файлов и страницы сейчас доступны, неизменность URL/цен не гарантируется; перед demo сверить снова.

**Pizza Tempo**

```text
MENU SOURCE: tempo.by → основное меню, пасты 1 / супы 1
STATUS: PARTIAL (branch applicability), readable/prices/items: PASS
NUMBER OF USABLE ITEMS: 6 ниже
PRICE FORMAT: decimal dot, BYN; restaurant menu image pages
BRANCH-SPECIFIC: NO — общий menu source сети
NOTES: доставка pizzatempo.by имеет две ценовые колонки; они не используются в оценке
```

| Item | BYN | Portion |
|---|---:|---|
| Паста Карбонара | 19.50 | 295 г |
| Паста с кальмарами и креветками | 25.20 | 380 г |
| Паста Альфредо | 17.40 | 320 г |
| Суп-крем из шампиньонов с сыром с голубой плесенью | 13.20 | 265 г |
| Солянка | 11.90 | 280 г |
| Борщ с салом | 13.30 | 380 г |

Sources: [пасты](https://tempo.by/img/menu/пасты%201.jpg), [супы](https://tempo.by/img/menu/супы%201.jpg). Не брать вес 235 г из delivery страницы для soup 265 г в restaurant menu. URL доступен и читаем, будущая стабильность не гарантирована; ручного controlled seed достаточно.

**Хинкальня Титан**

```text
MENU SOURCE: https://www.titanminsk.by/kafe-i-restoranyi/restoran/menyu/
STATUS: PASS
NUMBER OF USABLE ITEMS: 6 ниже
PRICE FORMAT: decimal BYN («руб.» на сайте белорусского филиала)
BRANCH-SPECIFIC: YES — страница ресторана в ТЦ Титан
NOTES: readable HTML, меню действует с 24.06.2026; можно загрузить только выбранные позиции
```

| Item | BYN | Portion |
|---|---:|---|
| Шашлык из свинины | 25.00 | 200/60 г |
| Шашлык из курицы | 23.00 | 200/60 г |
| Люля-кебаб | 24.00 | 180/50 г |
| Харчо | 19.00 | 250/2 г |
| Чихиртма | 16.00 | 300 г |
| Сырный суп с хинкали | 15.00 | 300 г |

Source: [официальное меню](https://www.titanminsk.by/kafe-i-restoranyi/restoran/menyu/). Хинкали поштучно не использованы как «полный main course». Эти 18 позиций — proof, не готовый каталог. Хранить source URL/verifiedAt; не копировать полные чужие тексты/фото без необходимости. Разрешение на широкое переиспользование не установлено; юридическое толкование оставлено uncertainty, юридическое заключение не проводилось.

## 13. Google Places Gate

```text
Docs: PASS
Pricing: PASS (official published schedule)
Required fields: PASS (documented)
Live test: BLOCKED_BY_CREDENTIALS
GOOGLE_GATE: BLOCKED_BY_CREDENTIALS
Status: DEFERRED — blocker только TASK-10
```

[Places API (New) Place Details](https://developers.google.com/maps/documentation/places/web-service/place-details), не Legacy и не Google catalog/menu retrieval:

```text
GET https://places.googleapis.com/v1/places/{KNOWN_PLACE_ID}
X-Goog-Api-Key: <runtime secret, не логировать>
X-Goog-FieldMask: id,rating,userRatingCount,currentOpeningHours,googleMapsUri,attributions
```

Wildcard mask запрещён. `attributions` дополнительно запрашивается для применимого third-party credit (IDs Only, не повышает Enterprise SKU). Mapping place ID → конкретный собственный филиал нужно проверить вручную; place IDs не придуманы. `currentOpeningHours` не подменяет own weekly hours/ranking. Поля могут отсутствовать, нельзя гарантировать rating/hour availability по одному только документированному field name.

Mask включает Enterprise fields `rating`, `userRatingCount`, `currentOpeningHours`, поэтому highest applicable SKU — **Place Details Enterprise**, даже если `googleMapsUri` относится к Pro, а `id` к IDs Only. [Official pricing](https://developers.google.com/maps/billing-and-pricing/pricing): **1000 free monthly events для данного SKU**, затем первый paid tier **$20 / 1000**, т.е. $0.02/request. Reviews/Atmosphere не запрашиваются. Старый универсальный $200 credit не используется в расчёте.

Setup: отдельный Cloud project, billing account, enabled Places API (New), restricted API key и quota; [usage/billing](https://developers.google.com/maps/documentation/places/web-service/usage-and-billing). Наличие Gemini key не подтверждает Maps billing/access. Billing country/EEA terms и реальные project quotas UNKNOWN. Budget alert сам по себе не spending cap.

Storage/attribution: [Policies](https://developers.google.com/maps/documentation/places/web-service/policies), [Terms](https://cloud.google.com/maps-platform/terms), [Service-specific terms](https://cloud.google.com/maps-platform/terms/maps-service-terms). Place IDs разрешено хранить; остальные content не prefetch/cache/persist без применимого исключения. Наш contract строже: live projection только renderer, без DB/memory/model/log snapshots. Новая attribution — **Google Maps**, logo whenever possible, текст допустим при ограничениях интерфейса; link один не заменяет attribution. Нужны доступные Terms/Privacy и применимые third-party attributions. Способ выполнения требований текстовым Telegram UI требует проверки в TASK-10, не юридически гарантирован этим отчётом.

Live request **не отправлялся**: key и known verified place ID отсутствуют. HTTP status, latency, actual rating/count/hours/URI, missing fields — **UNKNOWN**, не 200/0ms/примерные значения. Estimated SKU документирован; фактических paid events 0. Решение — **DEFERRED**, не FAIL и не EXCLUDED. Core slice работает без Google. BACKLOG N3/AC минимально исправлен согласно текущему запросу; до TASK-10 требуется PASS либо явное documented exclusion. Остальные stack/data проверки продолжены.

## 14. VPS / Deployment Notes

**N5 = UNKNOWN**, допустимо отложить до TASK-13 при remote AI. Из docs не найдены hostname, tariff, RAM/CPU/disk, Dokploy version/access, backup destination или bill. Существующий VPS не оценён и не объявлен достаточным. Никаких подключений/установок/deployment не выполнялось.

Remote API не требует GPU для Spring Boot + PostgreSQL + long polling. [Dokploy requirements](https://docs.dokploy.com/docs/core/installation): minimum 2 GB RAM / 30 GB disk — floor самой платформы, не доказательство достаточности всей нагрузки. **ASSUMPTION для планирования:** 2 vCPU, 4 GB RAM, ≥40 GB disk + место backup/build — разумная отправная точка для малой demo, но подтвердить actual resource use в TASK-13. Maven/image build может требовать отдельного запаса RAM. Для PostgreSQL volume и backup нужен отдельный учёт пространства.

Ollama 8B: несколько GB weights плюс runtime/KV cache; **ASSUMPTION** 12–16 GB RAM с запасом для app/DB, CPU latency требует benchmark, GPU может помочь, но её наличие неизвестно. На типичный VPS 2–4 GB такая рекомендация не переносится. Local model не выбран: hardware неизвестен, remote AIAI capability подтверждена.

Среда проверки: Temurin **21.0.11+10**, explicit JAVA_HOME, Maven **3.9.16**. PATH Java **17.0.12** не использовалась для Maven проверок. Docker CLI присутствовал, Linux daemon был недоступен; контейнеры не запускались. PostgreSQL client **17.10**, server login blocked password. Эти результаты не подтверждают готовность deployment или PostgreSQL acceptance.

## 15. Cost Estimate

**AIAI live observation:**

| Request | Input tokens | Output tokens | Total tokens | Latency ms |
|---|---:|---:|---:|---:|
| Basic | 13 | 2 | 15 | 862 |
| Raw tool choice | 136 | 35 | 171 | 1671 |
| Spring tool choice, successful turn | 160 | 37 | 197 | 1874 |
| Spring native structured response | 184 | 36 | 220 | 963 |
| Failed Spring diagnostic attempt 2 | 107 | 38 | 145 | 1593 |

Успешный Spring turn: **344 input + 73 output = 417 tokens**, model response latency 2837 ms суммарно (не Telegram end-to-end). По пяти сохранённым responses: 600/148/748 tokens; usage первого failed Spring attempt не сохранён, поэтому полный расход всех шести chat requests неизвестен. Max output basic=8, остальные requests=180. Такая величина успешного turn приемлема по token volume для учебного demo; future memory/context увеличит её. Account debit не читался, точная финансовая доступность/квоты **PARTIAL**. Расчёты ниже используют **upstream OpenAI list price**, не подтверждённый AIAI tariff; по этим ценам 417-token turn был бы ~$0.0002544, это иллюстрация, не стоимость AIAI.

Это сценарии, не счёт пользователя. Remote API billing/payment пока UNKNOWN. [OpenAI rate](https://developers.openai.com/api/docs/models/gpt-4.1-mini), [Google schedule](https://developers.google.com/maps/billing-and-pricing/pricing).

**ASSUMPTION:** суммарно за turn (оба calls, включая schema/tool definitions/context) 3000 input + 400 output tokens, cache discount не учитывается:

```text
AI cost/turn = (3000 × 0.40 + 400 × 1.60) / 1,000,000 = $0.00184
```

| Сценарий | AI | Google Enterprise Details | VPS / other |
|---|---:|---:|---|
| Development, 100–500 такого размера turns | ~$0.18–$0.92 | 0 при disabled; небольшие будущие probes могут попасть в free threshold | Existing VPS tariff UNKNOWN |
| Monthly demo, 1000 turns, ≤1000 Google requests | ~$1.84 | $0 только если monthly SKU threshold ещё доступен account | VPS UNKNOWN |
| Monthly demo, 5000 turns, 2000 Google requests | ~$9.20 | ~$20 после 1000 free events | VPS UNKNOWN |

Память до 20 сообщений и schema могут увеличить токены; actual usage/latency надо измерить live. Верхняя цена не ограничена этим примером, нужны caps input/output и account budget. Два model calls не означают одинаковый размер requests. Tool execution в нашей Java БД не является платным OpenAI hosted tool. Налоги, валютные комиссии/пополнение, квоты других проектов и storage/backup тарифы не включены. Другие обязательные платные сервисы проектными docs не установлены; Dokploy/Telegram client не требуют здесь отдельной подтверждённой подписки.

## 16. Remaining Open Gates

Классификация принадлежит [BACKLOG Open Gates](BACKLOG.md#open-gates). Status отражает фактическое evidence; classification определяет, когда оно нужно. Пересмотр по запросу пользователя после успешного live AIAI probe не добавляет новых проверок и не подменяет PARTIAL/UNKNOWN на PASS. **BLOCKER_FOR_TASK_01: отсутствуют обязательные технические remaining gates.**

| Gate | Evidence status | Classification | Обоснование / следующий task |
|---|---|---|---|
| N1 demand | UNKNOWN | PLANNING_INPUT | Интервью/спрос не prerequisite учебного bootstrap; новый PM-анализ не проводился |
| N2 own data | PARTIAL | DEFERRED_TO_LATER_TASK | Первые 3 feasibility datasets уже существуют. Branch applicability до seed/check в TASK-02 и menu import в TASK-03; полный каталог — TASK-11. Не блокирует создание Maven project |
| N3 Google | DEFERRED | DEFERRED_TO_LATER_TASK | Только TASK-10: live fields/billing/attribution или explicit exclusion; core slice независим |
| N4 remaining account economics / error-path checks | PARTIAL; центральный AI gate PASS | DEFERRED_TO_LATER_TASK | Auth/model/AI 1.1.8/tools/native output/1 tool–2 calls подтверждены. Тариф/account caps перед регулярными calls в TASK-05; error-path checks TASK-05/TASK-12. Bootstrap не вызывает AI в tests |
| N5 VPS | UNKNOWN | DEFERRED_TO_LATER_TASK | TASK-13 resources/access/backup; remote AI не требует local inference на VPS |
| N6 deadline/weekly time | USER_INPUT_REQUIRED | PLANNING_INPUT | Определяют scope/темп/буфер, а не совместимость dependencies или application smoke |
| N7 external rubric | USER_INPUT_REQUIRED; проектный stack evidence достаточен | PLANNING_INPUT | Sections 2–4 и рабочие docs достаточны для A; дополнительное подтверждение курса не technical blocker |
| PostgreSQL live acceptance | BLOCKED_BY_CREDENTIALS для прежнего local-server probe | DEFERRED_TO_LATER_TASK | TASK-01 допускает локальные development credentials через environment/config вне Git. Отдельная разрешённая test DB для migrations/constraints TASK-02 и memory restart TASK-07; production credentials только deployment TASK-13. Acceptance не объявлен PASS |

N6 не найден ни в docs, ни в доступных notes. **Условная оценка, ASSUMPTION:** плановый диапазон 140–200 часов → при 10 ч/нед 14–20 недель, при 20 ч/нед 7–10 недель, без отдельного буфера. Это прежний ориентир более широкого scope, используемый только для иллюстрации зависимости, не новая подтверждённая оценка нынешнего MVP и не пользовательский график. После N6/N7 нужен реалистичный пересчёт.

**Technical readiness READY; TASK-00 READY FOR USER APPROVAL.** Это изменение verdict по обоснованной классификации remaining gates, не новый PASS для N2/N5/N6/N7/account economics/PostgreSQL. Известных технических prerequisite bootstrap больше нет; scope/рекомендованная A/результат TASK-00 ожидают утверждения пользователя. Deadline и rubric нужны для planning, остальные данные — перед указанными поздними задачами. PostgreSQL production credentials не требуются TASK-01; секреты не хранить в Git. Ни DB, ни Google, ни AIAI повторно не вызывались при этом review. TASK-00 не DONE, TASK-01 не начата.

## 17. Decision Log

| Decision | Evidence / status |
|---|---|
| Production implementation не начата | Только docs и изолированные test-only Maven probes; PASS |
| A рекомендована 3.5.16 / 1.1.8 | Рабочие проектные требования + exact trees/10 tests/live AI; достаточно для user approval, дополнительная rubric не blocker |
| B сохранена 4.1.1 / 2.0.1 | Exact resolution + 3 tests; JUnit 6 вместо 5; PARTIAL course suitability |
| Springdoc 2.9.1 / 3.1.1 | Official release/POM/compatibility + class smoke; endpoint проверка позже |
| Pengrad 10.1.0 | Минимальная plain library, работает на Java 21 с A/B; live polling позже |
| AIAI.BY — provider продолжения | Key SET; discovery HTTP 200; actual ID gpt-4.1-mini, tools/native output PASS; account tariff/quotas PARTIAL |
| Spring AIAI probe выполнен live | Compile PASS; итоговый JUnit 1/0/0/0, 2 model calls / 1 tool; два failed attempts сохранены отдельно |
| 1 tool / ≤2 calls | Прежний TurnBudget/manual manager/precheck; PASS synthetic и один реальный AIAI turn |
| Hidden retry defaults запрещены | Default 10 доказан; maxAttempts=1 + 503 one-request fixture PASS |
| JDBC memory подходит без новой infra | H2 lifecycle + PG dialect/version docs; PASS capability, PG live blocked |
| Derived food-only check | Main + soup одинаково для всех, source/date/formula сохранены; branch caveats открыты |
| Google deferred | Official docs/pricing проверены, key отсутствует; только TASK-10 blocker |
| Course/deadline не придуманы | N6/N7 USER_INPUT_REQUIRED / PLANNING_INPUT; project stack evidence достаточен, TASK-00 не DONE |
| Remaining gates пересмотрены | Обязательных technical BLOCKER_FOR_TASK_01 нет; deferred tasks и planning отделены, evidence statuses сохранены |

Проверены evidence, prompt/schema/limits приложения и технические probes; полный PM scoring не выполнялся. Provider discovery опирался на официальную документацию. Нерелевантные infrastructure шаблоны не применены.

## 18. Readiness Checklist

Отметка означает именно описанный уровень evidence; unchecked live gates нельзя подменять локальными fixtures.

- [x] stack pinned — рекомендованная A готова для user approval; дополнительная course rubric — planning input;
- [x] AI provider selected — AIAI.BY, actual gpt-4.1-mini, live access PASS; account tariff/quotas ещё PARTIAL;
- [x] tool calling confirmed — raw и Spring, правильные criteria, один Java tool execution;
- [x] structured output confirmed — native strict schema, typed/semantic validation PASS в одном сценарии;
- [x] limits strategy confirmed — local tests и live 2 model calls / 1 tool, no observed unexpected requests;
- [x] 3 restaurant datasets found;
- [ ] check data usable — branch applicability для двух сетевых оценок проверить до seed TASK-02; не blocker bootstrap;
- [x] opening hours usable — собственные недельные интервалы/overnight источники;
- [x] menu sources feasible — ≥5 читаемых items для каждого, branch caveats сохранены;
- [x] Google decided/deferred — DEFERRED, только TASK-10;
- [x] no critical architecture blocker — AI capability и compatible stack подтверждены; remaining data/deployment gates отложены, planning и user approval отдельно.

**TECHNICAL READINESS FOR TASK-01: READY. TASK-00 FULL STATUS: READY FOR USER APPROVAL.** Центральная AI feasibility и compatible line A подтверждены; обязательных технических blocker bootstrap нет. Remaining PARTIAL/UNKNOWN/USER_INPUT_REQUIRED/BLOCKED_BY_CREDENTIALS сохранены и классифицированы в section 16. Google только до TASK-10, VPS/production credentials до deployment, данные до seed/menu work, deadline/rubric для planning. Этот review изменил классификацию и verdict, без повторения probes. TASK-00 не DONE, stack/scope не утверждены автоматически, TASK-01 не начата.
