-- Author: Tinashe K
-- Hibernate 7 validates the pooled Envers revision sequence alongside the identity column.

CREATE SEQUENCE revinfo_seq
  START WITH 1
  INCREMENT BY 50
  NO MINVALUE
  NO MAXVALUE
  CACHE 1;
