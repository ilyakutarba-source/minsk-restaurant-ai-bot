-- Coarse categories needed by the V2 curated subset; no new domain hierarchy.
ALTER TABLE restaurant_cuisines DROP CONSTRAINT ck_cuisine;
ALTER TABLE restaurant_cuisines ADD CONSTRAINT ck_cuisine
    CHECK (cuisine IN ('BELARUSIAN', 'ITALIAN', 'GEORGIAN', 'EUROPEAN', 'ASIAN'));
