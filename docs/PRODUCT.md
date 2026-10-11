# Продукт и границы MVP

Minsk Restaurant AI Bot помогает выбрать ресторан из собственного ограниченного
каталога Минска и получить сведения о выбранном заведении. Основной UI —
русскоязычный Telegram private chat; Telegram Mini App предлагает форму поиска,
независимый AI-поиск, карточки, details и сохранённое меню.

## Текущее состояние production

С 2026-10-11 production release **PASS**: `POST_V2_V13_PRODUCTION_RELEASE_PASS`,
commit `6b2de35`, Flyway V13. Каталог: **36 активных адресных заведений, 23 бренда,
101 позиция меню, 247 интервалов работы**. Telegram Bot и Mini App работают,
публичный HTTPS обеспечивается Dokploy/Traefik.
Результаты и границы проверки: [DEPLOYMENT](DEPLOYMENT.md#production-release--2026-10-11).

## POST-V2-02C — локальное расширение каталога

История этапа: шесть проверенных заведений были добавлены и приняты локально,
затем включены в production release V13 2026-10-11.
Состав, источники и ограничения: [DATABASE — V13](DATABASE.md#post-v2-02c--каталог-v13).
Telegram и оба способа поиска Mini App используют общий Java service.
Часы зала подтверждают прибытие, не гарантируют горячий заказ или свободный столик.

## POST-V2-01 — разнообразие брендов

Реализовано через обязательный явный `brandKey` и применено в production с V12.
Подборка содержит максимум три разных ресторанных бренда; для каждого выбирается
лучший подходящий филиал по ranking. Если брендов меньше трёх, карточек меньше:
кухня, бюджет и часы не ослабляются. Правило едино для Telegram, structured и natural
Mini App search. Алгоритм: [ARCHITECTURE](ARCHITECTURE.md#deterministic-ranking);
brand membership: [DATABASE](DATABASE.md#post-v2-01--brand-membership).

## Telegram Bot

Личный чат принимает полный запрос обычным языком, задаёт уточнения по отсутствующим
или неоднозначным критериям и сохраняет валидные частичные критерии.
«А если нас четверо?» продолжает поиск с прежними бюджетом, датой, временем и кухней.
«Меню второго», «Есть паста у первого?» и «До скольки последний?» читают свежие
собственные данные по текущей успешно показанной подборке. Номер должен существовать;
неоднозначное имя требует уточнения.

`/start` объясняет возможности, `/help` даёт примеры, `/new` очищает memory,
criteria и selection. Команды выполняет Java без AI calls.
Разговоры изолированы по server-side chatId; memory и selection хранятся в PostgreSQL.
Вход ограничен 2000 символами; длинные plain-text ответы делятся на части.
Failed/partial send не обновляет transcript и selection; гарантированной доставки нет.
Contracts: [AI](AI.md), transport: [DEPLOYMENT](DEPLOYMENT.md#telegram-long-polling--implemented).

## V2-04 — Telegram Mini App

Реализованный mobile-first интерфейс `/miniapp/` на HTML/CSS/vanilla JavaScript:
Search → до трёх Results → Details / Menu, с возвратом назад и темой Telegram.
Форма принимает 1–6 гостей, общий BYN-бюджет, дату/время Минска, optional cuisine.
Карточки показывают адрес, кухни, ориентировочную сумму и подтверждённые причины.
Details содержит недельные часы, источники и даты. Меню обозначено PARTIAL;
отсутствие сохранённых данных не означает отсутствие меню у ресторана.

Второй способ поиска — «Или опишите своими словами» / «Найти с AI».
Каждый запрос независим и требует полных критериев. NEED_CLARIFICATION предлагает
повторить полный уточнённый запрос; chat UI и продолжение критериев отсутствуют.
Форма, details и menu работают при отключённом или недоступном AI.
Реализованы loading, clarification, empty и controlled error states.
API проверяет подписанный Telegram initData; token остаётся на сервере.
[API/auth boundary](ARCHITECTURE.md#v2-04--mini-app-boundary),
[локальное demo](DEPLOYMENT.md#v2-04--mini-app-local-demo-и-live-gate).

## Core user journey — IMPLEMENTED

1. «Сегодня в 21:00 нас двое, общий бюджет 150 BYN, итальянская кухня».
2. AI извлекает критерии; Java валидирует их и при необходимости уточняет.
3. Java возвращает до трёх подходящих разных брендов, адреса и ориентировочные суммы.
4. «Меню второго» читает частичное меню именно второй показанной позиции.
5. «До скольки первый?» читает собственные часы; рейтинг честно недоступен.
6. «А если нас четверо?» меняет гостей с сохранением остальных критериев в Telegram.
7. `/new` начинает новый разговор.

В Mini App шаги поиска/details/menu доступны через форму, AI-запрос и кнопки;
шаги продолжения разговора относятся к Telegram chat.

## MVP — MUST

| Возможность | Реализованная граница |
|---|---|
| Каталог | 36 конкретных активных заведений / 23 бренда, собственные источники |
| Посещение | Минск, BYN, 1–6 гостей, сегодня и следующие 6 дней |
| Поиск | Гости, общий бюджет, дата/время, optional cuisine/tags; до трёх разных брендов |
| Чек | Ориентир на гостя с type/source/date, независимый от полноты меню |
| Меню | PARTIAL, максимум 10 позиций на заведение; saved menu может отсутствовать |
| Telegram | Private chats, long polling, search/continuation/menu/details, команды |
| Mini App | Форма, stateless AI search, cards/details/menu, server-side initData validation |
| AI | Один provider/model, ровно три read-only tools, Structured Output и Java rendering |
| Контекст | Bounded memory и текущая показанная selection в Telegram |
| Backend | Четыре private REST endpoints, Swagger, H2 и PostgreSQL tests |
| Deployment | Docker Compose → Dokploy/VPS, HTTPS через Traefik, persistent PostgreSQL |

## Пользовательское поведение и ограничения

Hard constraints не ослабляются автоматически. Неизвестные чек/часы не подтверждают
соответствие. Нет совпадений — пустой результат с честным сообщением и предложением
изменить критерии.

Бюджет относится ко всем гостям. `DERIVED` — собственный ориентир по выбранным
опубликованным ценам, не официальный средний чек и не цена любой корзины.
Число гостей не проверяет capacity. Недельные часы проверяют прибытие, не длительность
ужина, праздничные исключения или последний горячий заказ. Теги субъективны.

«Не найдено в сохранённой части меню» не доказывает отсутствие блюда вообще.
Наличие блюда, аллергены и неуказанные ингредиенты не выводятся из названия.
Поиск работает и без сохранённого меню; меню и ориентир чека имеют отдельные источники.

Ordinal/last references используют текущую успешно показанную подборку конкретного
чата. Пустой успешно отправленный search очищает selection, failed send оставляет
прежнюю. Memory не является источником ресторанных фактов.
Ошибка объяснения AI оставляет Java factual cards; ошибка интерпретации/provider
даёт controlled response. Structured search не зависит от AI.

## Google Places — EXCLUDED FROM CURRENT MVP

Google Places enrichment исключён из scope 2026-10-06 и не реализован.
Google rating/live hours не используются; Google key для запуска не нужен.
Каталог, поиск, menu/details работают на собственной БД.

## OUT OF SCOPE

- Полный каталог Минска, полные меню и автоматическая синхронизация/scraping.
- Google Places, live ratings/reviews, геолокация/distance и другие города/валюты.
- Booking, оплата, favorites, профили и постоянные preferences.
- История рекомендаций и сложные сравнения прошлых подборок.
- Публичный admin/chat API, несколько AI providers, SQL/JPA-доступ модели.
- Embeddings, vector DB, RAG, ML ranking, autonomous loops.
- Microservices, Redis/Kafka, distributed delivery и exactly-once guarantees.

## Возможные расширения

CI, callbacks, retention cleanup, праздничные часы и внешнее хранение backup
могут рассматриваться отдельно. Это идеи, не реализованные возможности.
Автоматического допуска непроверенных внешних данных в каталог нет.

## Definition of Done

Production release подтверждён 2026-10-11; это результат проверенных сценариев,
а не гарантия будущих ответов модели или безотказности.

- [x] Java 21 build, pinned dependencies, executable JAR.
- [x] PostgreSQL, Flyway V1–V13, Hibernate validate, данные с source/date.
- [x] Детерминированный поиск и дедупликация брендов; меню не влияет на eligibility.
- [x] Telegram search/continuation/menu/details, команды, изоляция и persistence.
- [x] Mini App structured/natural API, Java cards, server-side auth.
- [x] Три read-only tools, bounded calls, validated explanation/fallback.
- [x] H2 regression и отдельная PostgreSQL migration/search acceptance.
- [x] Dokploy production release, HTTPS boundary, один poller, persistent volume.
- [x] Backup и фактический restore/migration rehearsal в изолированной БД.
- [x] Production Telegram AI search/follow-up и Mini App launch/form/menu/details.
- [ ] Репетиция защиты и объяснение студентом границ AI/Java/DB отдельно от release.

Проверки: [README](../README.md#tests), [DATABASE](DATABASE.md#postgresql-acceptance),
[AI eval](AI.md#ai-eval), [DEPLOYMENT](DEPLOYMENT.md#production-release--2026-10-11).
Отдельный production AI-запрос Mini App и launch в mobile client не проверялись.

## История релизов

| Этап | Состояние на дату этапа |
|---|---|
| Первоначальный MVP, 2026-10-06 | V9, 10 филиалов, 60 menu items, 70 интервалов; Telegram и private HTTP |
| V2, 2026-10-08 | V11, 30 заведений / 17 брендов / 89 menu items / 205 интервалов; Mini App и HTTPS |
| POST-V2, 2026-10-11 | V12 brandKey + V13 expansion; текущие 36 / 23 / 101 / 247 |

Локальные feature acceptance описывают состояние до соответствующего release.
Изначальные требования 10–12 мест и 5–10 позиций относились к первому MVP;
текущий curated каталог допускает несколько проверенных позиций или отсутствие меню.

## Diploma demo — 8–10 минут

| Время | Что показать |
|---|---|
| 0:00–1:00 | Задача, ограниченный каталог, источники и PARTIAL/DERIVED |
| 1:00–2:00 | Telegram/Mini App → AI interpretation → Java services → PostgreSQL |
| 2:00–5:00 | Поиск разных брендов, «Меню второго», изменение гостей, часы |
| 5:00–7:00 | Mini App форма, cards/details/menu, отличие stateless AI search |
| 7:00–9:30 | Tests/eval, HTTPS/private boundary, persistence и ограничения |

Перед Telegram demo `/new` очищает старый контекст. Дата/время должны попадать в
поддержанный диапазон и сохранённое расписание. При недоступном provider использовать
локальный structured поиск и явно отделять сохранённые результаты от live response.
