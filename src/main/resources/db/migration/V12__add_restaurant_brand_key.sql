-- Explicit reviewed membership for the V11 catalog: 30 venues, 17 brands.
-- No defaults or name/prefix heuristics; an unmapped row fails NOT NULL below.
ALTER TABLE restaurants ADD COLUMN brand_key VARCHAR(100);

UPDATE restaurants SET brand_key = CASE seed_key
    WHEN 'vasilki-nezavisimosti-16' THEN 'vasilki'
    WHEN 'vasilki-nezavisimosti-58' THEN 'vasilki'
    WHEN 'vasilki-yakuba-kolasa-37' THEN 'vasilki'
    WHEN 'vasilki-nezavisimosti-89' THEN 'vasilki'
    WHEN 'pizza-tempo-karla-marksa-26' THEN 'pizza-tempo'
    WHEN 'pizza-tempo-nezavisimosti-18' THEN 'pizza-tempo'
    WHEN 'pizza-tempo-nezavisimosti-78' THEN 'pizza-tempo'
    WHEN 'pizza-tempo-pobediteley-84' THEN 'pizza-tempo'
    WHEN 'pizza-tempo-bobruyskaya-6' THEN 'pizza-tempo'
    WHEN 'khinkalnya-dzerzhinskogo-104' THEN 'khinkalnya'
    WHEN 'kuhmistr-karla-marksa-40' THEN 'kuhmistr'
    WHEN 'chumaczki-myasnikova-34' THEN 'chumaczki-shlyah'
    WHEN 'berezka-nezavisimosti-40' THEN 'berezka'
    WHEN 'druzya-kulman-40' THEN 'druzya'
    WHEN 'ganbei-pritytskogo-156' THEN 'gan-bei'
    WHEN 'ganbei-mstislavtsa-11' THEN 'gan-bei'
    WHEN 'ganbei-timiryazeva-74a' THEN 'gan-bei'
    WHEN 'embassy-karla-marksa-6' THEN 'coffee-embassy'
    WHEN 'embassy-mira-1' THEN 'coffee-embassy'
    WHEN 'embassy-pobediteley-115' THEN 'coffee-embassy'
    WHEN 'litviny-revolyutsionnaya-26' THEN 'litviny'
    WHEN 'litviny-pritytskogo-156-1' THEN 'litviny'
    WHEN 'litviny-pobediteley-119' THEN 'litviny'
    WHEN 'beans-leaves-pobediteley-9' THEN 'beans-leaves'
    WHEN 'bardot-pobediteley-9' THEN 'bardot-xx1'
    WHEN 'tashkent-lenina-3' THEN 'tashkent'
    WHEN 'ember-pobediteley-9' THEN 'ember'
    WHEN 'mlyn-ruzh-kalvariyskaya-1' THEN 'mlyn-ruzh'
    WHEN 'chaika-dzerzhinskogo-104' THEN 'chaika'
    WHEN 'mimino-kirova-13' THEN 'mimino'
END;

ALTER TABLE restaurants ALTER COLUMN brand_key SET NOT NULL;
ALTER TABLE restaurants ADD CONSTRAINT ck_restaurant_brand_key CHECK (
    REGEXP_LIKE(brand_key, '^[a-z0-9]+(-[a-z0-9]+)*$')
    AND NOT REGEXP_LIKE(brand_key, '[^a-z0-9-]'));
