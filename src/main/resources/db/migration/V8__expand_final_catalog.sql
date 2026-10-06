-- Verified against official branch lists and the common dine-in menus on 2026-10-06.
-- Existing V1-V7 datasets stay immutable. Names deliberately repeat across branches.
INSERT INTO restaurants (seed_key, name, address, active, catalog_source, catalog_verified_at,
    estimated_check_per_guest, check_estimation_type, check_source, check_verified_at,
    hours_source, hours_verified_at)
SELECT b.seed_key, r.name, b.address, TRUE, b.site, DATE '2026-10-06',
    r.estimated_check_per_guest, r.check_estimation_type,
    REPLACE(r.check_source, b.old_address, b.source_address), DATE '2026-10-06',
    b.site || ' — опубликованное расписание конкретного филиала: ' || b.source_address, DATE '2026-10-06'
FROM (VALUES
 ('vasilki-nezavisimosti-58', 'vasilki-nezavisimosti-16', 'Минск, проспект Независимости, 58', 'https://vasilki.by/', 'Независимости, 16', 'Независимости, 58'),
 ('vasilki-yakuba-kolasa-37', 'vasilki-nezavisimosti-16', 'Минск, ул. Якуба Коласа, 37, МЦ «Айсберг»', 'https://vasilki.by/', 'Независимости, 16', 'Я. Коласа, 37, МЦ «Айсберг»'),
 ('vasilki-nezavisimosti-89', 'vasilki-nezavisimosti-16', 'Минск, проспект Независимости, 89', 'https://vasilki.by/', 'Независимости, 16', 'Независимости, 89'),
 ('pizza-tempo-nezavisimosti-18', 'pizza-tempo-karla-marksa-26', 'Минск, проспект Независимости, 18', 'https://tempo.by/', 'ул. Маркса, 26', 'пр-т Независимости, 18'),
 ('pizza-tempo-nezavisimosti-78', 'pizza-tempo-karla-marksa-26', 'Минск, проспект Независимости, 78', 'https://tempo.by/', 'ул. Маркса, 26', 'пр-т Независимости, 78'),
 ('pizza-tempo-pobediteley-84', 'pizza-tempo-karla-marksa-26', 'Минск, проспект Победителей, 84, ТРЦ «ARENAcity»', 'https://tempo.by/', 'ул. Маркса, 26', 'пр-т Победителей, 84, ТРЦ «ARENAcity»'),
 ('pizza-tempo-bobruyskaya-6', 'pizza-tempo-karla-marksa-26', 'Минск, ул. Бобруйская, 6, ТРЦ «Galileo»', 'https://tempo.by/', 'ул. Маркса, 26', 'ул. Бобруйская, 6, ТРЦ «Galileo»')
) AS b(seed_key, original_key, address, site, old_address, source_address)
JOIN restaurants r ON r.seed_key = b.original_key ORDER BY b.seed_key;

INSERT INTO restaurant_cuisines (restaurant_id, cuisine)
SELECT id, CASE WHEN seed_key LIKE 'vasilki-%' THEN 'BELARUSIAN' ELSE 'ITALIAN' END
FROM restaurants WHERE catalog_verified_at = DATE '2026-10-06'
  AND seed_key IN ('vasilki-nezavisimosti-58', 'vasilki-yakuba-kolasa-37', 'vasilki-nezavisimosti-89',
  'pizza-tempo-nezavisimosti-18', 'pizza-tempo-nezavisimosti-78', 'pizza-tempo-pobediteley-84', 'pizza-tempo-bobruyskaya-6');

-- Monday=0 on the official sites; [6,3] means Sunday through Thursday, [4,5] Friday/Saturday.
INSERT INTO opening_intervals (restaurant_id, weekday, opens_at, closes_at, closes_next_day)
SELECT r.id, d.weekday,
 CASE WHEN r.seed_key IN ('vasilki-nezavisimosti-58','vasilki-nezavisimosti-89') THEN TIME '08:00:00'
      WHEN r.seed_key = 'vasilki-yakuba-kolasa-37' THEN TIME '11:00:00'
      WHEN r.seed_key = 'pizza-tempo-nezavisimosti-18' THEN TIME '09:00:00'
      ELSE TIME '10:00:00' END,
 CASE WHEN r.seed_key = 'vasilki-yakuba-kolasa-37' AND d.weekday IN ('FRIDAY','SATURDAY') THEN TIME '02:00:00'
      WHEN (r.seed_key IN ('vasilki-nezavisimosti-58','pizza-tempo-nezavisimosti-18') AND d.weekday IN ('FRIDAY','SATURDAY'))
           OR r.seed_key = 'pizza-tempo-bobruyskaya-6' THEN TIME '00:00:00'
      WHEN r.seed_key = 'pizza-tempo-pobediteley-84' THEN TIME '22:00:00'
      ELSE TIME '23:00:00' END,
 (r.seed_key IN ('vasilki-nezavisimosti-58','vasilki-yakuba-kolasa-37','pizza-tempo-nezavisimosti-18')
    AND d.weekday IN ('FRIDAY','SATURDAY')) OR r.seed_key = 'pizza-tempo-bobruyskaya-6'
FROM restaurants r CROSS JOIN (VALUES ('MONDAY'),('TUESDAY'),('WEDNESDAY'),('THURSDAY'),('FRIDAY'),('SATURDAY'),('SUNDAY')) AS d(weekday)
WHERE r.seed_key IN ('vasilki-nezavisimosti-58', 'vasilki-yakuba-kolasa-37', 'vasilki-nezavisimosti-89',
  'pizza-tempo-nezavisimosti-18', 'pizza-tempo-nezavisimosti-78', 'pizza-tempo-pobediteley-84', 'pizza-tempo-bobruyskaya-6');
