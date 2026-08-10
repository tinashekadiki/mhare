-- Author: Tinashe K

ALTER TABLE admission_cycles
    ADD COLUMN change_reason varchar(1000) NOT NULL DEFAULT 'Initial record creation.';

ALTER TABLE admission_cycles_aud
    ADD COLUMN change_reason varchar(1000);
