-- Author: Tinashe K

ALTER TABLE programmes
    ADD CONSTRAINT ck_programmes_code_length CHECK (char_length(code) <= 5);
