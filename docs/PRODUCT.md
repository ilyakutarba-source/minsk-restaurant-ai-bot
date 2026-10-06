# Продукт и границы MVP

Minsk Restaurant AI Bot помогает выбрать ресторан из собственного ограниченного
каталога Минска и получить сведения о выбранном заведении. Основной целевой UI —
русскоязычный Telegram private chat; поиск с уточнениями доступен в Telegram,
структурированный поиск и чтение каталога — через REST/Swagger.

## Реализованные возможности — IMPLEMENTED

- 10 конкретных активных филиалов: четыре Васильки, пять Pizza Tempo и одна Хинкальня.
- Собственные адреса, cuisine/tags, часы и ориентировочный чек на одного гостя.
- По шесть сохранённых menu items на ресторан (60 всего), с source/date и PARTIAL coverage.
- Java Restaurant Search по полным структурированным критериям посещения.
- До трёх кандидатов в стабильном порядке с подтверждёнными reason codes.
- REST catalog/details/menu/recommendations и Swagger.
- Stateless Spring AI search по полному natural-language запросу, controlled
  ExplanationPlan и Java factual card/fallback; [AI contract](AI.md).
- Telegram private text messages и long polling: полный запрос → существующий AI
  search → собственная PostgreSQL → Java factual card → sendMessage.
- Bounded PostgreSQL conversation memory, сохранение и продолжение критериев, базовый `/new`.
- Текущая успешно показанная подборка по chatId и Java foundation для ordinal/last/exact-name references.
- Telegram menu/details follow-up: ReferenceResolver → MenuService/RestaurantService,
  supported PASTA/price filter, PARTIAL caveat и свежие собственные facts в Java response.
- `/start` и `/help` с примерами реального поиска и follow-up; Java-only commands.
- Controlled unsupported booking/city/currency, ошибки provider/DB/Telegram,
  input limits и lossless split длинного plain-text ответа.

Чек, расписание и меню имеют отдельные источники и даты проверки. Поиск работает
при частичном или отсутствующем меню. [Модель и происхождение данных](DATABASE.md).

## Core user journey — IMPLEMENTED

1. Пользователь: «Сегодня в 21:00 нас двое, до 150 BYN, итальянская кухня, хочется спокойно».
2. AI извлекает критерии; при недостающих или неоднозначных данных задаётся уточнение.
3. Java search возвращает подходящие рестораны; пользователь видит короткое
   объяснение, фактические карточки и ориентировочную сумму на указанное число гостей.
4. «Что по меню у второго?» обращается к конкретному ресторану из показанной подборки.
5. «До скольки первый?» перечитывает собственные часы; запрос рейтинга получает
   сообщение о его недоступности.
6. «А если нас четверо?» меняет гостей, сохраняя остальные критерии.
7. `/new` очищает разговор, критерии и последнюю подборку.

Полный self-contained запрос и короткие уточнения работают в Telegram. Валидные
частичные критерии сохраняются; Java спрашивает оставшиеся обязательные или
неоднозначные поля. Java resolution по последней подборке и menu/details tools реализованы,
включая шаги 4–5 для собственных данных. `/start` и `/help` реализованы;
Google rating исключён из текущего MVP.
Правила tools/memory/reference resolution: [AI](AI.md).

## MVP — MUST

| Возможность | Продуктовая граница | Состояние |
|---|---|---|
| Каталог | 10–12 реальных заведений, конкретные филиалы | IMPLEMENTED: 10 |
| Посещение | Минск, BYN, 1–6 гостей, сегодня и следующие 6 дней | IMPLEMENTED в Java search и Telegram slice |
| Поиск | Гости, общий бюджет, дата/время, optional cuisine/tags; до 3 вариантов | IMPLEMENTED |
| Чек | Проверяемый ориентир на гостя с type/source/date | IMPLEMENTED |
| Меню | 5–10 позиций на заведение, только PARTIAL | IMPLEMENTED: по 6 для всех 10 филиалов |
| Telegram | Личные чаты, русский язык, long polling | IMPLEMENTED: search/menu/details, commands, controlled errors/limits |
| AI | Один provider/model, Spring AI Tool Calling и Structured Output | IMPLEMENTED: ровно 3 tools, Java factual rendering |
| Контекст | Ограниченная ChatMemory и последняя показанная подборка по chatId | Memory/criteria/selection и Java ReferenceResolver IMPLEMENTED |
| Команды | `/start`, `/help`, `/new` | IMPLEMENTED; без model/tool calls |
| Проверка backend | Четыре REST endpoints, Swagger, H2 и PostgreSQL tests | IMPLEMENTED |
| Доставка | Docker Compose, Dokploy/VPS и persistent PostgreSQL | IMPLEMENTED; текущая remote acceptance — [status/runbook](DEPLOYMENT.md) |

Техническая архитектура и детерминированные правила: [ARCHITECTURE](ARCHITECTURE.md).
Provider/модель и доверенные tool contracts: [AI](AI.md).

## Google Places — EXCLUDED FROM CURRENT MVP

2026-10-06 пользователь явно исключил Google Places enrichment из текущего MVP.
Google implementation — NOT IMPLEMENTED; live Google gate — NOT PASSED.
Billing prerequisite недоступен в текущем setup; [техническая причина](DEPLOYMENT.md#google-places--excluded-from-current-mvp).

Core MVP не зависит от Google: собственные catalog/search/menu/details продолжают
работать. Google rating и live hours не входят в текущий scope. Возможность можно
пересмотреть позже при доступном billing/API access и новом явном решении о scope.

## Пользовательское поведение и ограничения

**Подбор.** Hard constraints не ослабляются автоматически. Неизвестные чек/часы не
подтверждают соответствие запросу. Пустой REST search возвращает пустой список;
Telegram возвращает честный NO_RESULTS и предлагает новый запрос с другими критериями.

**Бюджет.** Указанная сумма — общий бюджет на всех гостей. Ориентировочный чек не
гарантирует окончательную сумму заказа; DERIVED не означает официальный средний чек.
Количество гостей не проверяет вместимость или наличие столика.

**Обстановка.** Теги выражают субъективную характеристику каталога. Они не обещают
тишину, романтическую обстановку или доступность места в конкретный вечер.

**Меню.** «Не найдено в сохранённой части меню» не означает отсутствие блюда вообще.
Наличие блюда, аллергены и неуказанные ингредиенты не выводятся из его названия.

**Контекст — IMPLEMENTED.** «Первый/второй/третий/последний» ссылается только на текущую успешно
показанную подборку. Неоднозначное название требует уточнения; разные чаты изолированы.
Пустая успешно отправленная выдача очищает подборку; failed send сохраняет прежнюю.
Java resolver поддерживает exact normalized name; menu/details AI tools используют
его в Telegram follow-up, без нового поиска и замены текущей подборки.

**Ошибки AI — IMPLEMENTED для search/menu/details.** REST Java search работает
независимо от AI. При failed explanation остаётся Java factual card;
недоступный рейтинг
не придумывается и не восстанавливается из разговорной памяти.

Собственное недельное расписание не гарантирует праздничных исключений. Продукт
не бронирует столики, не принимает оплату и не обещает доступность блюда.

**Команды и ошибки — IMPLEMENTED.** `/start` объясняет возможности, `/help` показывает
примеры, `/new` очищает memory/criteria/selection и меняет generation. Help не меняет
текущую подборку. Java scope guards отклоняют явные booking/city/currency requests;
остальное неподдерживаемое wording требует abstention модели и Java clarification.
Пользователю предлагаются Минск и общий BYN budget, без скрытой конвертации/поиска.
Временные provider/DB ошибки не добавляют выдуманные факты. Если хотя бы одна часть
Telegram reply не отправлена, новый transcript и selection не сохраняются; ранее
сохранённые valid criteria могут остаться. Guaranteed delivery отсутствует.
Точные limits и guards: [AI](AI.md#bounded-execution); transport: [DEPLOYMENT](DEPLOYMENT.md#telegram-long-polling--implemented).

## OUT OF SCOPE первого релиза

- Google Places enrichment: rating, live hours и Google Maps сведения.
- Полный каталог Минска, полное меню, scraping и автоматическая синхронизация.
- Favorites, профиль, постоянные preferences и история рекомендаций.
- Геолокация/distance, другие города, несколько AI providers.
- Сложные сравнения прошлых подборок и произвольные selectors.
- Booking, оплата, публичный admin/chat API и отдельный web UI.
- Autonomous loops, AI SQL/JPA access, embeddings, vector DB, RAG и ML ranking.
- Microservices, Redis/Kafka, delivery state machine и exactly-once guarantees.

## Возможные расширения

После MVP: CI, ограниченные callbacks, retention cleanup, праздничные часы,
backup restore drill и Google reviews при проверенных условиях отображения.
Это будущие возможности, не часть текущей реализации.

Текущие инструкции запуска/тестов: [README](../README.md).
Требования deployment и secrets: [DEPLOYMENT](DEPLOYMENT.md).
