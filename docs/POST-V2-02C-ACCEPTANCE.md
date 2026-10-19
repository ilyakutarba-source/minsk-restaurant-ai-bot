# POST-V2-02C — Catalog V13 acceptance

Дата: **2026-10-11**, Europe/Minsk. Статус: **PASS** после всех обязательных проверок.
Область: локальный feature checkout и собственный изолированный PostgreSQL 17.10.
Production, main, merge, push и deploy не изменялись.

## 1. Git и scope

- Ветка: `codex/post-v2-02c-catalog-v13`, создана от POST-V2-01
  `141a7c328ba1dd2a4e4525d413145474e104198e`.
- Проверенный implementation commit: `f01881ffc1472a28cadfcb593706acff5b72519d`.
- Локальный main до/после: `1b73be8f03e849126d69d8bde1605907677ac598`.
  Это проверка Git ref, не новый remote production audit.
- При старте tracked working tree чистое. Три существующих untracked исследовательских
  отчёта прочитаны и сохранены без изменения: POST-V2-02-CATALOG-AUDIT,
  POST-V2-02-VERIFIED-DATASET, POST-V2-02-FINAL-CANDIDATES.
- Реализация, tests и документы-владельцы зафиксированы отдельным feature commit;
  этот acceptance добавлен следующим commit. Hash самого отчёта можно получить
  командой `git log -1 --format=%H -- docs/POST-V2-02C-ACCEPTANCE.md`:
  собственный hash невозможно встроить в содержимое того же commit.

Модель и ограничения принадлежат [DATABASE — V13](DATABASE.md#post-v2-02c--каталог-v13),
scope — [PRODUCT](PRODUCT.md#mvp--must),
неизменённый алгоритм — [ARCHITECTURE](ARCHITECTURE.md#deterministic-ranking).

## 2. Изменённые файлы

- [docs/DATABASE.md](../docs/DATABASE.md)
- [docs/POST-V2-02C-ACCEPTANCE.md](../docs/POST-V2-02C-ACCEPTANCE.md)
- [docs/PRODUCT.md](../docs/PRODUCT.md)
- [src/main/java/db/migration/V13__expand_verified_catalog.java](../src/main/java/db/migration/V13__expand_verified_catalog.java)
- [src/main/resources/db/seed/curated-catalog-v13.json](../src/main/resources/db/seed/curated-catalog-v13.json)
- [src/test/java/by/ilya/restaurantbot/RestaurantBotApplicationTest.java](../src/test/java/by/ilya/restaurantbot/RestaurantBotApplicationTest.java)
- [src/test/java/by/ilya/restaurantbot/ai/ConversationIntegrationTest.java](../src/test/java/by/ilya/restaurantbot/ai/ConversationIntegrationTest.java)
- [src/test/java/by/ilya/restaurantbot/ai/FinalAiEvalSupport.java](../src/test/java/by/ilya/restaurantbot/ai/FinalAiEvalSupport.java)
- [src/test/java/by/ilya/restaurantbot/ai/SpringAiSearchIntegrationTest.java](../src/test/java/by/ilya/restaurantbot/ai/SpringAiSearchIntegrationTest.java)
- [src/test/java/by/ilya/restaurantbot/ai/TelegramSearchIntegrationTest.java](../src/test/java/by/ilya/restaurantbot/ai/TelegramSearchIntegrationTest.java)
- [src/test/java/by/ilya/restaurantbot/catalog/BrandDiversityMigrationTest.java](../src/test/java/by/ilya/restaurantbot/catalog/BrandDiversityMigrationTest.java)
- [src/test/java/by/ilya/restaurantbot/catalog/CatalogExpansionDatasetTest.java](../src/test/java/by/ilya/restaurantbot/catalog/CatalogExpansionDatasetTest.java)
- [src/test/java/by/ilya/restaurantbot/catalog/CatalogExpansionMigrationTest.java](../src/test/java/by/ilya/restaurantbot/catalog/CatalogExpansionMigrationTest.java)
- [src/test/java/by/ilya/restaurantbot/catalog/CuratedCatalogAcceptanceTest.java](../src/test/java/by/ilya/restaurantbot/catalog/CuratedCatalogAcceptanceTest.java)
- [src/test/java/by/ilya/restaurantbot/catalog/CuratedCatalogBrandImportTest.java](../src/test/java/by/ilya/restaurantbot/catalog/CuratedCatalogBrandImportTest.java)
- [src/test/java/by/ilya/restaurantbot/catalog/FinalCatalogAcceptanceTest.java](../src/test/java/by/ilya/restaurantbot/catalog/FinalCatalogAcceptanceTest.java)
- [src/test/java/by/ilya/restaurantbot/catalog/PartialMenuTest.java](../src/test/java/by/ilya/restaurantbot/catalog/PartialMenuTest.java)
- [src/test/java/by/ilya/restaurantbot/catalog/RestaurantCatalogTest.java](../src/test/java/by/ilya/restaurantbot/catalog/RestaurantCatalogTest.java)
- [src/test/java/by/ilya/restaurantbot/miniapp/MiniAppAiIntegrationTest.java](../src/test/java/by/ilya/restaurantbot/miniapp/MiniAppAiIntegrationTest.java)
- [src/test/java/by/ilya/restaurantbot/miniapp/MiniAppBrandDiversityIntegrationTest.java](../src/test/java/by/ilya/restaurantbot/miniapp/MiniAppBrandDiversityIntegrationTest.java)
- [src/test/java/by/ilya/restaurantbot/search/CatalogExpansionAuditTest.java](../src/test/java/by/ilya/restaurantbot/search/CatalogExpansionAuditTest.java)
- [src/test/java/by/ilya/restaurantbot/search/RestaurantSearchTest.java](../src/test/java/by/ilya/restaurantbot/search/RestaurantSearchTest.java)

Единственные новые production resources — V13 Java migration и её immutable JSON.
Существующие CuratedCatalogDataset/Import, entities, search comparator/predicates,
AI tools/adapter, Telegram, Mini App и REST production-код не изменены.
V1–V12 и прежние SQL/JSON seeds сохранены byte-for-byte относительно POST-V2-01
(`git diff --exit-code` по историческим путям PASS).

Regression fixtures исходных трёх/десяти заведений теперь исключают и V13 в своих
локальных транзакциях. Историческая brand migration acceptance ограничена target=12.
Current catalog counts и seeded output expectations обновлены; guards и требования
не ослаблены. Explanation fixtures покрывают все возвращённые позиции и используют
COZY reason только у кандидата с подтверждённым тегом. Concurrency test сравнивает
selection после уточнения «четверо» с фактическими criteria, а не прежним запросом на двоих.
Проверка Telegram/structured/natural Mini App сохраняет варианты с 1/2/3 брендами
через бюджеты 80/120/150 и дополнительно ASIAN/ANY.

## 3. Pre-import verification gate

**Все 6 APPROVED_FOR_IMPORT повторно допущены; исключённых после gate нет.**
21 NEEDS_VERIFICATION и 2 REJECTED не импортированы. Повторный review источников
выполнен **2026-10-11 до создания V13**, прямыми HTTPS запросами с сохранением
HTML/JSON/PDF. HTTP 200 сам по себе не считался доказательством. Публичные страницы
представляют действующие залы с адресами, контактами и приглашением посетить ресторан;
физическое открытие дверей, наличие столика и блюда не проверялись.

`verifiedAt=2026-10-08` во всех новых catalog/hours/check/menu metadata сохранён
точно из подготовленного reviewed dataset. Это исходная дата review, не дата редакции
прайса и не timestamp импорта. Свежая проверка 2026-10-11 зафиксирована отдельно здесь
и в evidence manifest. Дата обновления основного прайса владельцем не установлена.

| Кандидат | Адрес/часы зала и опубликованное присутствие | Основной прайс и результат сверки |
|---|---|---|
| La Scala | [Официальный ресторан](https://la-scala.by/restaurant): Немига, 36; Пн–Вс 12:00–00:00 +1, кухня до 23:00 | [Основное меню](https://la-scala.by/menu/osnovnoe-menu), штатный read-only handler SubcategoryComponent::onShowSubcategory, категории 6/5: 52+24=76 BYN; не 75 |
| Мзиури | [Сайт](https://mziuri.by/): Притыцкого, 97б; Пн–Вс 12:00–00:00 +1; приняты прежние GEORGIAN/EUROPEAN | [Основное меню](https://mziuri.by/menu/): оджахури 24, харчо с шоти 17; 41 BYN; delivery link отдельно |
| Пекаридзе | [Контакты](https://pekaridze.by/contacty): Платонова, 45; ежедневно 10:00–22:00 | [Основное меню](https://pekaridze.by/menu_osnovnoe): 24+15=39 BYN. Проверены элементы одной карточки/блока; цена соседнего оджахури 23 не приписана остри. Число 350 без единицы не импортировано как порция |
| Сорренто | [Официальный Минск](https://sorrento.ru/sorrentominsk/): Интернациональная, 38; ежедневно 11:00–00:00 +1; итальянский зал | Ссылка «Кухня и бар» ведёт на [тот же PDF](https://familygarden.su/upload/iblock/1eb/l3e7kf2ncj9kwne0poflxylfq3315si3.pdf); SHA-256 совпал с POST-V2-02B. Обе страницы отрендерены и просмотрены. Карбонара 45+суп 20=65 |
| Da Claudio | [Сайт](https://daclaudio.by/): Карла Маркса, 25; Пн–Вс 12:00–23:00 | [HTML меню](https://daclaudio.by/menu) и [PDF, страницы 2–3](https://daclaudio.by/documents/menuKitchenPdf.pdf), визуально: тальятелле 39/450 г, гороховый суп 21/270 г; 60 BYN, без дневной скидки |
| Янцзы | [Сайт](https://yangtze.by/): площадь Свободы, 8; ежедневно 11:00–23:00 | Прямые HTML [птица](https://yangtze.by/nashe-menyu/blyuda-iz-pticy/), [рис](https://yangtze.by/nashe-menyu/garnir/), [суп](https://yangtze.by/nashe-menyu/supy/) содержат настоящие карточки: 30+3+10=43. Это содержимое меню, не защитная HTTP-страница; доставка отделена внешней ссылкой |

В Сорренто знак «Р» интерпретирован как BYN по явной связи этого основного PDF
с минским рестораном; отдельного ISO обозначения валюты на листе нет. Это
обоснованная локальная интерпретация, не конверсия российского прайса. У Янцзы
«р» относится к основному меню собственного минского сайта; BYN также принят
из местного контекста. В Da Claudio и Пекаридзе BYN прямо указан на HTML карточках.
Все цены зала, delivery/ланч/самовывоз/акции не подставлялись.

HTTPS transport для первого набора прямых captures запускался с
SkipCertificateCheck, включая La Scala; исправность TLS этим не подтверждается.
Поздние контакты Пекаридзе, главная Янцзы и PDF Da Claudio прочитаны с обычной
проверкой сертификата. Публичный контент и его provenance сохранены;
платные AI API, Firecrawl, production secrets не использовались.

Local-only evidence: `probes/post-v2-02c-evidence/pre-import-manifest.json` содержит
URL, review date, размер, SHA-256 и флаг certificateValidationDisabled для captures.
HTML/JSON/PDF/PNG находятся рядом. Пользовательские прежние evidence не перезаписывались.
PDF read-only QA выполнена по локальному skill `pdf`.

## 4. Итоговый imported dataset

Все записи active=true, MANUALLY_VERIFIED, DERIVED, PARTIAL, tags empty.
ID ниже относятся к fresh acceptance DB; перенос на другую БД не предполагает
следующее identity value без проверки. Все старые IDs сохранены.
Каждый график содержит все семь weekdays; +1 = closesNextDay=true.

| ID | name / точный address | seedKey / brandKey | cuisines | Чек/гость / двое BYN | Пн–Вс зал |
|---|---|---|---|---:|---|
| 31 | La Scala Trattoria Ignazio / Минск, ул. Немига, 36 | `la-scala-nemiga-36` / `la-scala` | ITALIAN | 76.00 / 152.00 | 12:00–00:00 +1 |
| 32 | Мзиури / Минск, ул. Притыцкого, 97б | `mziuri-pritytskogo-97b` / `mziuri` | GEORGIAN, EUROPEAN | 41.00 / 82.00 | 12:00–00:00 +1 |
| 33 | Пекаридзе / Минск, ул. Платонова, 45 | `pekaridze-platonova-45` / `pekaridze` | GEORGIAN | 39.00 / 78.00 | 10:00–22:00 |
| 34 | Сорренто / Минск, ул. Интернациональная, 38 | `sorrento-internatsionalnaya-38` / `sorrento` | ITALIAN | 65.00 / 130.00 | 11:00–00:00 +1 |
| 35 | Da Claudio / Минск, ул. Карла Маркса, 25 | `da-claudio-karla-marksa-25` / `da-claudio` | ITALIAN | 60.00 / 120.00 | 12:00–23:00 |
| 36 | Янцзы / Минск, площадь Свободы, 8 | `yangtze-svobody-8` / `yangtze` | ASIAN | 43.00 / 86.00 | 11:00–23:00 |

Все четыре provenance-набора и исходные даты находятся в JSON. `check_source`
сохраняет основной URL вместе с объяснением расчёта. У Янцзы там остаются все три
URL и состав 30+3+10; рис не включён в PARTIAL меню, поскольку GARNISH отсутствует.

| item seedKey | Блюдо | category / dishType | BYN | portion |
|---|---|---|---:|---|
| `la-scala-linguine-turiddu` | Лингвини с морепродуктами от моряка Туридду | PASTA / PASTA | 52.00 | null |
| `la-scala-tomato-cream-soup` | Томатный крем-суп | SOUP / null | 24.00 | null |
| `mziuri-odjahuri` | Оджахури | MAIN / null | 24.00 | 300 г |
| `mziuri-kharcho` | Харчо | SOUP / null | 17.00 | 300/125 г |
| `pekaridze-ostri-beef` | Остри с говядиной | MAIN / null | 24.00 | null |
| `pekaridze-kharcho` | Харчо | SOUP / null | 15.00 | null |
| `sorrento-spaghetti-carbonara` | Спагетти карбонара с беконом в сливочном соусе | PASTA / PASTA | 45.00 | null |
| `sorrento-chicken-noodle-soup` | Куриный с лапшой | SOUP / null | 20.00 | null |
| `da-claudio-tagliatelle-sausage-mushroom` | Тальятелле с миксом грибов, мясом сицилийских колбасок и ароматом трюфеля | PASTA / PASTA | 39.00 | 450 г |
| `da-claudio-pea-shrimp-soup` | Крем-суп из гороха с креветками | SOUP / null | 21.00 | 270 г |
| `yangtze-gong-bao-chicken` | Куриное филе с арахисом — Гонг Бао Ти Дин | MAIN / null | 30.00 | 300 г |
| `yangtze-chicken-shiitake-soup` | Суп с куриным филе и грибами шиитаки — Ди Пьан Тан | SOUP / null | 10.00 | 230 г |

Неподтверждённые порции, ингредиенты, категории и блюда не добавлены. У супа Янцзы
свой item source `/supy/`, отличный от menu source `/blyuda-iz-pticy/`.

## 5. Flyway / PostgreSQL / Hibernate acceptance

PostgreSQL **17.10**; новый cluster `probes/post-v2-02c-evidence/pgdata`, только
`127.0.0.1:55440`, собственная пустая БД `post_v2_02c_acceptance`. Другие local DB
и production не подключались. Cluster после acceptance остановлен, файлы evidence
сохранены; generated acceptance schemas после проверки **0**.

| Проверка | Фактический результат |
|---|---|
| Fresh пустая schema → V13 | PASS; 13 versioned migrations |
| Flyway validate после fresh | PASS |
| Hibernate validate fresh | PASS; catalog + conversation entities |
| Upgrade подготовленная V12 → V13 | PASS; ровно 1 migration |
| Исходные 30 restaurants и IDs | PASS; полное сравнение старых строк/полей, включая brand_key и synthetic inactive flag |
| Прежние cuisines/tags/menu/opening intervals | PASS; полное сравнение строк/IDs/полей |
| Conversation state / selection / JDBC memory | PASS; synthetic criteria/generation/version, selection и private memory rows сохранены |
| Hibernate validate upgrade | PASS |
| Flyway validate upgrade | PASS |
| Повтор Flyway migrate | PASS; 0 migrations, snapshot восьми таблиц не изменился |
| Повтор direct curated import | Контролируемый duplicate rejection; данные не изменились |
| Invalid late venue / unknown JSON field / unknown enum / отрицательная цена | Контролируемый отказ; before-write validation |
| Duplicate seedKey в batch и existing seed в последней записи | Контролируемый отказ без частичных строк |
| Normalized name/address, включая inactive existing venue | Контролируемый отказ до первой записи |
| Missing/неканонический brand assignment | Контролируемый отказ до первой записи |
| Реальный SQL failure в последнем меню V13 | PASS; все ранее вставленные новые venues/menus/intervals/cuisines откатились; schema/history остались V12 |
| Повтор после снятия тестового fault constraint | PASS; V13 применена целиком |

SQL fault введён только в собственную generated schema: временный CHECK отклоняет
`yangtze-chicken-shiitake-soup`, когда пять новых ресторанов уже вставлены в
Flyway transaction. Снимок восьми таблиц до/после failure совпал. PostgreSQL
identity sequence может иметь пропуски после rollback; сохранность существующих IDs
не означает откат счётчика sequence. Новые IDs после retry не обязаны быть 31–36.

Brand registry/entity не создавалась. Семантическое членство шести брендов задаёт
reviewed mapping V13; общий importer проверяет точное покрытие batch и canonical
формат, а не знает реестр всех ресторанных брендов мира.

Первый PostgreSQL run имел один failure в новом test harness: после корректного
rollback до V12 `validate` без target проверял ещё pending V13. Проверка исправлена
на target=12, затем выполнены retry V13 и её обычный validate. Финальный PostgreSQL
run **12/12 PASS**, неудачный промежуточный лог сохранён отдельно. Production
migration/import code ради этого не менялся.

## 6. Итоговые counts

| Показатель | V12 baseline | Fresh V13 |
|---|---:|---:|
| Active venues | 30 | 36 |
| Unique brands | 17 | 23 |
| Menu items | 89 | 101 |
| Weekly opening intervals | 205 | 247 |
| ITALIAN venues / brands | 5 / 1 | 8 / 4 |
| GEORGIAN venues / brands | 2 / 2 | 4 / 4 |
| ASIAN venues / brands | 5 / 3 | 6 / 4 |
| EUROPEAN venues / brands | 8 / 6 | 9 / 7 |
| BELARUSIAN venues / brands | 13 / 8 | 13 / 8 |

Фактические counts совпали с прогнозом; искусственного дополнения menu/hours rows нет.
SQL snapshot сохранён в `postgresql-snapshot.txt`, Flyway history содержит V1–V13,
каждая version success=true.

## 7. Regression tests

Java **21.0.11**; `mvnw.cmd -o`, pinned зависимости проекта. Без реальных платных
AI calls: offline SDK/HTTP/provider fixtures, mocked ChatModel/Telegram transport.
Live `*IT` smoke tests не запускались. Числа разных запусков пересекаются, не
суммируются как число уникальных tests.

| Запуск | Результат |
|---|---|
| Targeted catalog/import/search/brand/Telegram/Mini App/SDK/eval/conversation | PASS: 342 tests, failures/errors/skipped=0 |
| PostgreSQL catalog expansion/migration/audit/shared Telegram + Mini App | PASS: 12 tests, failures/errors/skipped=0 |
| Полный `clean test`, H2 | PASS: 575 tests, failures/errors/skipped=0 |
| Полный `clean verify`, H2 | PASS: 575 tests, failures/errors/skipped=0; executable JAR assembled |
| Historical main paths и `git diff --check` | PASS |

Последние логи: `targeted-final.log`, `postgresql-acceptance-final.log`, `clean-test.log`,
`clean-verify.log` в `probes/post-v2-02c-evidence/`. Исходные промежуточные failures
были устаревшими seeded expectations/fixtures; они исправлены в рамках расширения
каталога. Ranking, hard filters и product contracts не изменялись.
Полный PostgreSQL regression suite не запускался: PostgreSQL покрыта отдельной
миграционной acceptance, 135-сценарным audit и общими transport integration cases.
Это не заменяется прежними 566 tests POST-V2-01.

## 8. Повторный audit 135 сценариев

Настоящий `RestaurantSearchService`, `RestaurantRepository`/Hibernate и локальная
PostgreSQL. Clock **2026-10-08T09:00:00Z**, Europe/Minsk (12:00 местного времени),
как baseline. Даты 2026-10-09/10/11; времена 13:00/19:00/21:00; guests=2;
5 cuisines × 3 budgets (80/150/250 BYN общий бюджет) × 3 dates × 3 times;
preferredTags empty. Все даты входят в supported range.

135 уникальных запросов вызваны дважды, **270 Java search calls**; повторяемость PASS.
Диагностика hard filters вызывает через reflection существующие private
`hasVerifiedCheck`, `estimatedTotal`, `isOpen`, с теми же active/cuisine условиями.
Она не заменяет service: returned IDs/order берутся из настоящего `search` и
сравниваются с eligible brands и неизменённым ranking при пустых tags.
Для каждой строки PASS: размер = min(3, eligibleBrands), разные бренды, бюджет,
кухня, часы и детерминированный порядок. JSON evidence — `audit-final.json`.

| Results/scenario | V12 baseline | V13 fact |
|---|---:|---:|
| 0 | 18 | 9 |
| 1 | 45 | 27 |
| 2 | 32 | 9 |
| 3 | 40 | 90 |

**40/135 (29.63%) → 90/135 (66.67%)**, +50 сценариев, +37.04 процентного пункта.
Прогноз **90/135 совпал с реальным Java/PostgreSQL audit**; расхождений нет.
Это показатель покрытия каталога, а не «90 тестов PASS».

| Cuisine | V13 scenarios с 3 results / 27 |
|---|---:|
| BELARUSIAN | 18 |
| ITALIAN | 18 |
| GEORGIAN | 18 |
| EUROPEAN | 18 |
| ASIAN | 18 |

При 80 BYN остаются ограничения: ITALIAN — один бренд, GEORGIAN — один,
EUROPEAN — один, ASIAN — ноль, BELARUSIAN — два. Hard filters не ослаблялись.

### Полная матрица

H = restaurants после hard filters; U = unique eligible brands; R = results.
Порядок brandKey и IDs в двух последних колонках — фактическая выдача Java,
не алфавитная сортировка и не assumed production IDs.

| # | Cuisine | Date | Time | Budget BYN | H | U | R | returned brandKey, в порядке | returned IDs, в порядке |
|---|---|---|---|---:|---:|---:|---:|---|---|
| 1 | BELARUSIAN | 2026-10-09 | 13:00 | 80 | 5 | 2 | 2 | vasilki, chaika | 1, 29 |
| 2 | BELARUSIAN | 2026-10-09 | 19:00 | 80 | 5 | 2 | 2 | vasilki, chaika | 1, 29 |
| 3 | BELARUSIAN | 2026-10-09 | 21:00 | 80 | 5 | 2 | 2 | vasilki, chaika | 1, 29 |
| 4 | BELARUSIAN | 2026-10-09 | 13:00 | 150 | 11 | 6 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 5 | BELARUSIAN | 2026-10-09 | 19:00 | 150 | 12 | 7 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 6 | BELARUSIAN | 2026-10-09 | 21:00 | 150 | 12 | 7 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 7 | BELARUSIAN | 2026-10-09 | 13:00 | 250 | 12 | 7 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 8 | BELARUSIAN | 2026-10-09 | 19:00 | 250 | 13 | 8 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 9 | BELARUSIAN | 2026-10-09 | 21:00 | 250 | 13 | 8 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 10 | BELARUSIAN | 2026-10-10 | 13:00 | 80 | 5 | 2 | 2 | vasilki, chaika | 1, 29 |
| 11 | BELARUSIAN | 2026-10-10 | 19:00 | 80 | 5 | 2 | 2 | vasilki, chaika | 1, 29 |
| 12 | BELARUSIAN | 2026-10-10 | 21:00 | 80 | 5 | 2 | 2 | vasilki, chaika | 1, 29 |
| 13 | BELARUSIAN | 2026-10-10 | 13:00 | 150 | 11 | 6 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 14 | BELARUSIAN | 2026-10-10 | 19:00 | 150 | 12 | 7 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 15 | BELARUSIAN | 2026-10-10 | 21:00 | 150 | 12 | 7 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 16 | BELARUSIAN | 2026-10-10 | 13:00 | 250 | 12 | 7 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 17 | BELARUSIAN | 2026-10-10 | 19:00 | 250 | 13 | 8 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 18 | BELARUSIAN | 2026-10-10 | 21:00 | 250 | 13 | 8 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 19 | BELARUSIAN | 2026-10-11 | 13:00 | 80 | 5 | 2 | 2 | vasilki, chaika | 1, 29 |
| 20 | BELARUSIAN | 2026-10-11 | 19:00 | 80 | 5 | 2 | 2 | vasilki, chaika | 1, 29 |
| 21 | BELARUSIAN | 2026-10-11 | 21:00 | 80 | 5 | 2 | 2 | vasilki, chaika | 1, 29 |
| 22 | BELARUSIAN | 2026-10-11 | 13:00 | 150 | 11 | 6 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 23 | BELARUSIAN | 2026-10-11 | 19:00 | 150 | 12 | 7 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 24 | BELARUSIAN | 2026-10-11 | 21:00 | 150 | 12 | 7 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 25 | BELARUSIAN | 2026-10-11 | 13:00 | 250 | 12 | 7 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 26 | BELARUSIAN | 2026-10-11 | 19:00 | 250 | 13 | 8 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 27 | BELARUSIAN | 2026-10-11 | 21:00 | 250 | 13 | 8 | 3 | vasilki, chaika, chumaczki-shlyah | 1, 29, 12 |
| 28 | ITALIAN | 2026-10-09 | 13:00 | 80 | 5 | 1 | 1 | pizza-tempo | 2 |
| 29 | ITALIAN | 2026-10-09 | 19:00 | 80 | 5 | 1 | 1 | pizza-tempo | 2 |
| 30 | ITALIAN | 2026-10-09 | 21:00 | 80 | 5 | 1 | 1 | pizza-tempo | 2 |
| 31 | ITALIAN | 2026-10-09 | 13:00 | 150 | 7 | 3 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 32 | ITALIAN | 2026-10-09 | 19:00 | 150 | 7 | 3 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 33 | ITALIAN | 2026-10-09 | 21:00 | 150 | 7 | 3 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 34 | ITALIAN | 2026-10-09 | 13:00 | 250 | 8 | 4 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 35 | ITALIAN | 2026-10-09 | 19:00 | 250 | 8 | 4 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 36 | ITALIAN | 2026-10-09 | 21:00 | 250 | 8 | 4 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 37 | ITALIAN | 2026-10-10 | 13:00 | 80 | 5 | 1 | 1 | pizza-tempo | 2 |
| 38 | ITALIAN | 2026-10-10 | 19:00 | 80 | 5 | 1 | 1 | pizza-tempo | 2 |
| 39 | ITALIAN | 2026-10-10 | 21:00 | 80 | 5 | 1 | 1 | pizza-tempo | 2 |
| 40 | ITALIAN | 2026-10-10 | 13:00 | 150 | 7 | 3 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 41 | ITALIAN | 2026-10-10 | 19:00 | 150 | 7 | 3 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 42 | ITALIAN | 2026-10-10 | 21:00 | 150 | 7 | 3 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 43 | ITALIAN | 2026-10-10 | 13:00 | 250 | 8 | 4 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 44 | ITALIAN | 2026-10-10 | 19:00 | 250 | 8 | 4 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 45 | ITALIAN | 2026-10-10 | 21:00 | 250 | 8 | 4 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 46 | ITALIAN | 2026-10-11 | 13:00 | 80 | 5 | 1 | 1 | pizza-tempo | 2 |
| 47 | ITALIAN | 2026-10-11 | 19:00 | 80 | 5 | 1 | 1 | pizza-tempo | 2 |
| 48 | ITALIAN | 2026-10-11 | 21:00 | 80 | 5 | 1 | 1 | pizza-tempo | 2 |
| 49 | ITALIAN | 2026-10-11 | 13:00 | 150 | 7 | 3 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 50 | ITALIAN | 2026-10-11 | 19:00 | 150 | 7 | 3 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 51 | ITALIAN | 2026-10-11 | 21:00 | 150 | 7 | 3 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 52 | ITALIAN | 2026-10-11 | 13:00 | 250 | 8 | 4 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 53 | ITALIAN | 2026-10-11 | 19:00 | 250 | 8 | 4 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 54 | ITALIAN | 2026-10-11 | 21:00 | 250 | 8 | 4 | 3 | pizza-tempo, da-claudio, sorrento | 2, 35, 34 |
| 55 | GEORGIAN | 2026-10-09 | 13:00 | 80 | 1 | 1 | 1 | pekaridze | 33 |
| 56 | GEORGIAN | 2026-10-09 | 19:00 | 80 | 1 | 1 | 1 | pekaridze | 33 |
| 57 | GEORGIAN | 2026-10-09 | 21:00 | 80 | 1 | 1 | 1 | pekaridze | 33 |
| 58 | GEORGIAN | 2026-10-09 | 13:00 | 150 | 3 | 3 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 59 | GEORGIAN | 2026-10-09 | 19:00 | 150 | 3 | 3 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 60 | GEORGIAN | 2026-10-09 | 21:00 | 150 | 3 | 3 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 61 | GEORGIAN | 2026-10-09 | 13:00 | 250 | 4 | 4 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 62 | GEORGIAN | 2026-10-09 | 19:00 | 250 | 4 | 4 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 63 | GEORGIAN | 2026-10-09 | 21:00 | 250 | 4 | 4 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 64 | GEORGIAN | 2026-10-10 | 13:00 | 80 | 1 | 1 | 1 | pekaridze | 33 |
| 65 | GEORGIAN | 2026-10-10 | 19:00 | 80 | 1 | 1 | 1 | pekaridze | 33 |
| 66 | GEORGIAN | 2026-10-10 | 21:00 | 80 | 1 | 1 | 1 | pekaridze | 33 |
| 67 | GEORGIAN | 2026-10-10 | 13:00 | 150 | 3 | 3 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 68 | GEORGIAN | 2026-10-10 | 19:00 | 150 | 3 | 3 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 69 | GEORGIAN | 2026-10-10 | 21:00 | 150 | 3 | 3 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 70 | GEORGIAN | 2026-10-10 | 13:00 | 250 | 4 | 4 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 71 | GEORGIAN | 2026-10-10 | 19:00 | 250 | 4 | 4 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 72 | GEORGIAN | 2026-10-10 | 21:00 | 250 | 4 | 4 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 73 | GEORGIAN | 2026-10-11 | 13:00 | 80 | 1 | 1 | 1 | pekaridze | 33 |
| 74 | GEORGIAN | 2026-10-11 | 19:00 | 80 | 1 | 1 | 1 | pekaridze | 33 |
| 75 | GEORGIAN | 2026-10-11 | 21:00 | 80 | 1 | 1 | 1 | pekaridze | 33 |
| 76 | GEORGIAN | 2026-10-11 | 13:00 | 150 | 3 | 3 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 77 | GEORGIAN | 2026-10-11 | 19:00 | 150 | 3 | 3 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 78 | GEORGIAN | 2026-10-11 | 21:00 | 150 | 3 | 3 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 79 | GEORGIAN | 2026-10-11 | 13:00 | 250 | 4 | 4 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 80 | GEORGIAN | 2026-10-11 | 19:00 | 250 | 4 | 4 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 81 | GEORGIAN | 2026-10-11 | 21:00 | 250 | 4 | 4 | 3 | pekaridze, mziuri, khinkalnya | 33, 32, 3 |
| 82 | EUROPEAN | 2026-10-09 | 13:00 | 80 | 3 | 1 | 1 | coffee-embassy | 19 |
| 83 | EUROPEAN | 2026-10-09 | 19:00 | 80 | 3 | 1 | 1 | coffee-embassy | 19 |
| 84 | EUROPEAN | 2026-10-09 | 21:00 | 80 | 3 | 1 | 1 | coffee-embassy | 19 |
| 85 | EUROPEAN | 2026-10-09 | 13:00 | 150 | 7 | 5 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 86 | EUROPEAN | 2026-10-09 | 19:00 | 150 | 8 | 6 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 87 | EUROPEAN | 2026-10-09 | 21:00 | 150 | 8 | 6 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 88 | EUROPEAN | 2026-10-09 | 13:00 | 250 | 8 | 6 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 89 | EUROPEAN | 2026-10-09 | 19:00 | 250 | 9 | 7 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 90 | EUROPEAN | 2026-10-09 | 21:00 | 250 | 9 | 7 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 91 | EUROPEAN | 2026-10-10 | 13:00 | 80 | 3 | 1 | 1 | coffee-embassy | 19 |
| 92 | EUROPEAN | 2026-10-10 | 19:00 | 80 | 3 | 1 | 1 | coffee-embassy | 19 |
| 93 | EUROPEAN | 2026-10-10 | 21:00 | 80 | 3 | 1 | 1 | coffee-embassy | 19 |
| 94 | EUROPEAN | 2026-10-10 | 13:00 | 150 | 7 | 5 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 95 | EUROPEAN | 2026-10-10 | 19:00 | 150 | 8 | 6 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 96 | EUROPEAN | 2026-10-10 | 21:00 | 150 | 8 | 6 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 97 | EUROPEAN | 2026-10-10 | 13:00 | 250 | 8 | 6 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 98 | EUROPEAN | 2026-10-10 | 19:00 | 250 | 9 | 7 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 99 | EUROPEAN | 2026-10-10 | 21:00 | 250 | 9 | 7 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 100 | EUROPEAN | 2026-10-11 | 13:00 | 80 | 3 | 1 | 1 | coffee-embassy | 19 |
| 101 | EUROPEAN | 2026-10-11 | 19:00 | 80 | 3 | 1 | 1 | coffee-embassy | 19 |
| 102 | EUROPEAN | 2026-10-11 | 21:00 | 80 | 3 | 1 | 1 | coffee-embassy | 19 |
| 103 | EUROPEAN | 2026-10-11 | 13:00 | 150 | 7 | 5 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 104 | EUROPEAN | 2026-10-11 | 19:00 | 150 | 8 | 6 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 105 | EUROPEAN | 2026-10-11 | 21:00 | 150 | 8 | 6 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 106 | EUROPEAN | 2026-10-11 | 13:00 | 250 | 8 | 6 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 107 | EUROPEAN | 2026-10-11 | 19:00 | 250 | 9 | 7 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 108 | EUROPEAN | 2026-10-11 | 21:00 | 250 | 9 | 7 | 3 | coffee-embassy, mziuri, beans-leaves | 19, 32, 24 |
| 109 | ASIAN | 2026-10-09 | 13:00 | 80 | 0 | 0 | 0 | — | — |
| 110 | ASIAN | 2026-10-09 | 19:00 | 80 | 0 | 0 | 0 | — | — |
| 111 | ASIAN | 2026-10-09 | 21:00 | 80 | 0 | 0 | 0 | — | — |
| 112 | ASIAN | 2026-10-09 | 13:00 | 150 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 113 | ASIAN | 2026-10-09 | 19:00 | 150 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 114 | ASIAN | 2026-10-09 | 21:00 | 150 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 115 | ASIAN | 2026-10-09 | 13:00 | 250 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 116 | ASIAN | 2026-10-09 | 19:00 | 250 | 6 | 4 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 117 | ASIAN | 2026-10-09 | 21:00 | 250 | 6 | 4 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 118 | ASIAN | 2026-10-10 | 13:00 | 80 | 0 | 0 | 0 | — | — |
| 119 | ASIAN | 2026-10-10 | 19:00 | 80 | 0 | 0 | 0 | — | — |
| 120 | ASIAN | 2026-10-10 | 21:00 | 80 | 0 | 0 | 0 | — | — |
| 121 | ASIAN | 2026-10-10 | 13:00 | 150 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 122 | ASIAN | 2026-10-10 | 19:00 | 150 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 123 | ASIAN | 2026-10-10 | 21:00 | 150 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 124 | ASIAN | 2026-10-10 | 13:00 | 250 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 125 | ASIAN | 2026-10-10 | 19:00 | 250 | 6 | 4 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 126 | ASIAN | 2026-10-10 | 21:00 | 250 | 6 | 4 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 127 | ASIAN | 2026-10-11 | 13:00 | 80 | 0 | 0 | 0 | — | — |
| 128 | ASIAN | 2026-10-11 | 19:00 | 80 | 0 | 0 | 0 | — | — |
| 129 | ASIAN | 2026-10-11 | 21:00 | 80 | 0 | 0 | 0 | — | — |
| 130 | ASIAN | 2026-10-11 | 13:00 | 150 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 131 | ASIAN | 2026-10-11 | 19:00 | 150 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 132 | ASIAN | 2026-10-11 | 21:00 | 150 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 133 | ASIAN | 2026-10-11 | 13:00 | 250 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 134 | ASIAN | 2026-10-11 | 19:00 | 250 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |
| 135 | ASIAN | 2026-10-11 | 21:00 | 250 | 5 | 3 | 3 | yangtze, gan-bei, tashkent | 36, 15, 26 |

## 9. Исходный итальянский запрос

«Сегодня, 21:00, двое, 150 BYN, итальянская кухня» проверен с конкретными датами
2026-10-09, 2026-10-10 и 2026-10-11, без динамического today, tags empty.
Во всех трёх случаях H=7, U=3, R=3:

| Position | Заведение / address | brandKey | ID | Ориентир на двоих BYN |
|---|---|---|---:|---:|
| 1 | Pizza Tempo / Минск, ул. Карла Маркса, 26 | pizza-tempo | 2 | 65.40 |
| 2 | Da Claudio / Минск, ул. Карла Маркса, 25 | da-claudio | 35 | 120.00 |
| 3 | Сорренто / Минск, ул. Интернациональная, 38 | sorrento | 34 | 130.00 |

La Scala **76×2=152 >150** и исключена бюджетом во всех соответствующих сценариях.
При 250 она проходит hard filters (ITALIAN H=8, U=4), но уступает по estimated
sum трём брендам выше и не входит в top-3. Пять Pizza Tempo не заполняют подборку
пятью филиалами. Telegram, structured и natural Mini App возвращают тот же search
result через общий service; offline shared integration PASS на H2 и PostgreSQL.

## 10. Воспроизведение

Java 21, без live keys:

```powershell
.\mvnw.cmd -o clean test
.\mvnw.cmd -o clean verify
```

Для собственной отдельно созданной PostgreSQL 17.10 на указанном loopback port,
пустой БД `post_v2_02c_acceptance`, username=postgres, пустой test password:

```powershell
.\mvnw.cmd -o '-Dtest=CatalogExpansion*,MiniAppBrandDiversityIntegrationTest' `
  '-Dcatalog.acceptance.url=jdbc:postgresql://127.0.0.1:55440/post_v2_02c_acceptance' `
  '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55440/post_v2_02c_acceptance' `
  '-Dspring.datasource.username=postgres' '-Dspring.datasource.password=' `
  '-Dspring.datasource.driver-class-name=org.postgresql.Driver' `
  '-Dspring.datasource.hikari.minimum-idle=0' '-Dspring.datasource.hikari.maximum-pool-size=4' `
  '-Dcatalog.audit.output=probes/post-v2-02c-evidence/audit-repeat.json' test
```

При повторе suite создаёт/удаляет только собственные generated schemas. Public
schema отдельной acceptance DB используется для seeded audit/transport cases.
Production connection string сюда не передавать; migration test и evidence writer
проверяют точный адрес выделенной local acceptance DB.

## 11. Ограничения и итоговый Git status

- Пять kitchen stop-times UNKNOWN. Arrival по залу в 21:00 не обещает горячий заказ.
  La Scala публикует кухню до 23:00; модель и фильтры не добавляют kitchen subsystem.
- Чеки DERIVED/food-only — ориентиры, не верхняя граница любой корзины. У Сорренто
  депозит/условия неизвестны; возможные депозиты Янцзы также не оценены по сумме.
- Меню PARTIAL, по две позиции; null portion честно сохраняется. Рис Янцзы входит
  в provenance чека, но не в enum MAIN. Последующая актуальность прайсов не гарантируется.
- Недельные часы не учитывают праздничные исключения, длительность ужина и наличие места.
- Внешние menu revision dates неизвестны; source review не означает проверку в зале.
- Live AI/Telegram/production smoke в этой задаче не выполнялись по offline scope.

После feature commits tracked working tree чистое. Сохранены только прежние
untracked документы пользователя:

```text
?? docs/POST-V2-02-CATALOG-AUDIT.md
?? docs/POST-V2-02-FINAL-CANDIDATES.md
?? docs/POST-V2-02-VERIFIED-DATASET.md
```

Новые evidence находятся в локально исключённом из Git `probes/`; старые evidence
и отчёты не удалялись. Все обязательные проверки PASS. Merge/push/deploy не выполнены,
production не изменён. Работа завершена на feature branch.
