-- Verified 2026-10-02. Curated food-only estimates: one main dish and one soup per guest.
-- Network sites publish one restaurant menu without branch-specific price selection.
INSERT INTO restaurants (seed_key, name, address, catalog_source, catalog_verified_at,
                         estimated_check_per_guest, check_estimation_type, check_source, check_verified_at,
                         hours_source, hours_verified_at)
VALUES
('vasilki-nezavisimosti-16', 'Васильки', 'Минск, проспект Независимости, 16',
 'https://vasilki.by/', DATE '2026-10-02', 39.80, 'DERIVED',
 'https://vasilki.by/ — единое меню сети без разделения по филиалам, в каталоге есть Независимости, 16; цены зала: https://vasilki.by/img/menu/Драники_1.jpg и https://vasilki.by/img/menu/Супы_1.jpg; один гость: драники с мачанкой по-белорусски 22.90 + щи с лесными грибами 16.90 BYN; без напитков, алкоголя, десерта, доставки и чаевых; собственная оценка, не официальный средний чек',
 DATE '2026-10-02',
 'https://vasilki.by/ — публичное расписание филиала Независимости, 16', DATE '2026-10-02'),
('pizza-tempo-karla-marksa-26', 'Pizza Tempo', 'Минск, ул. Карла Маркса, 26',
 'https://tempo.by/ — на сайте: ул. Маркса, 26', DATE '2026-10-02', 32.70, 'DERIVED',
 'https://tempo.by/ — единое меню сети без разделения по филиалам, в каталоге есть ул. Маркса, 26; цены зала: https://tempo.by/img/menu/пасты%201.jpg и https://tempo.by/img/menu/супы%201.jpg; один гость: паста Карбонара 19.50 + суп-крем из шампиньонов с сыром с голубой плесенью 13.20 BYN; без напитков, алкоголя, десерта, доставки и чаевых; собственная оценка, не официальный средний чек',
 DATE '2026-10-02',
 'https://tempo.by/ — публичное расписание филиала Маркса, 26', DATE '2026-10-02'),
('khinkalnya-dzerzhinskogo-104', 'Хинкальня', 'Минск, проспект Дзержинского, 104',
 'https://www.titanminsk.by/kafe-i-restoranyi/restoran/', DATE '2026-10-02',
 44.00, 'DERIVED',
 'https://www.titanminsk.by/kafe-i-restoranyi/restoran/menyu/ — один гость: шашлык из свинины 25.00 + харчо 19.00 BYN; цены зала, без напитков, алкоголя, доставки и чаевых; собственная оценка, не официальный средний чек',
 DATE '2026-10-02', 'https://www.titanminsk.by/kafe-i-restoranyi/restoran/', DATE '2026-10-02');

INSERT INTO restaurant_cuisines (restaurant_id, cuisine)
SELECT id, 'BELARUSIAN' FROM restaurants WHERE seed_key = 'vasilki-nezavisimosti-16';
INSERT INTO restaurant_cuisines (restaurant_id, cuisine)
SELECT id, 'ITALIAN' FROM restaurants WHERE seed_key = 'pizza-tempo-karla-marksa-26';
INSERT INTO restaurant_cuisines (restaurant_id, cuisine)
SELECT id, 'GEORGIAN' FROM restaurants WHERE seed_key = 'khinkalnya-dzerzhinskogo-104';

-- Curated tags from this branch's own description; other branches have no verified tags.
INSERT INTO restaurant_tags (restaurant_id, tag)
SELECT id, 'COZY' FROM restaurants WHERE seed_key = 'khinkalnya-dzerzhinskogo-104';
INSERT INTO restaurant_tags (restaurant_id, tag)
SELECT id, 'FRIENDS' FROM restaurants WHERE seed_key = 'khinkalnya-dzerzhinskogo-104';

INSERT INTO opening_intervals (restaurant_id, weekday, opens_at, closes_at, closes_next_day)
SELECT r.id, d.weekday,
       CASE WHEN r.seed_key = 'vasilki-nezavisimosti-16' THEN TIME '08:00:00'
            WHEN r.seed_key = 'pizza-tempo-karla-marksa-26' THEN TIME '10:00:00'
            ELSE TIME '12:00:00' END,
       CASE WHEN r.seed_key = 'khinkalnya-dzerzhinskogo-104' THEN TIME '00:00:00'
            WHEN r.seed_key = 'vasilki-nezavisimosti-16' AND d.weekday IN ('FRIDAY', 'SATURDAY')
                THEN TIME '01:00:00'
            ELSE TIME '23:00:00' END,
       r.seed_key = 'khinkalnya-dzerzhinskogo-104'
           OR (r.seed_key = 'vasilki-nezavisimosti-16' AND d.weekday IN ('FRIDAY', 'SATURDAY'))
FROM restaurants r
CROSS JOIN (VALUES ('MONDAY'), ('TUESDAY'), ('WEDNESDAY'), ('THURSDAY'),
                   ('FRIDAY'), ('SATURDAY'), ('SUNDAY')) AS d(weekday)
WHERE r.seed_key IN ('vasilki-nezavisimosti-16', 'pizza-tempo-karla-marksa-26', 'khinkalnya-dzerzhinskogo-104');
