-- Author: Tinashe K
-- Retains the complete governed input used to reproduce every official offer-letter version.

ALTER TABLE offer_letter_projections
    ADD COLUMN content_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE offer_letter_projections_aud
    ADD COLUMN content_snapshot jsonb;

ALTER TABLE offer_letter_projections
    ALTER COLUMN content_snapshot DROP DEFAULT;
