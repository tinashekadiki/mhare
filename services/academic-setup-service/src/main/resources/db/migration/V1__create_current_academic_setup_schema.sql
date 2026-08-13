-- Author: Tinashe K
-- Canonical clean-slate baseline for academic-setup-service.

--
--


-- Dumped from database version 18.4 (Debian 18.4-1.pgdg13+1)
-- Dumped by pg_dump version 18.4 (Debian 18.4-1.pgdg13+1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: btree_gist; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;


--
-- Name: EXTENSION btree_gist; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION btree_gist IS 'support for indexing common datatypes in GiST';


--
-- Name: prevent_children_on_owning_academic_unit(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_children_on_owning_academic_unit() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.parent_id IS NOT NULL AND (
        EXISTS (SELECT 1 FROM programmes WHERE owning_academic_unit_id = NEW.parent_id AND deleted_at IS NULL)
        OR EXISTS (SELECT 1 FROM modules WHERE owning_academic_unit_id = NEW.parent_id AND deleted_at IS NULL)
    ) THEN
        RAISE EXCEPTION 'An academic unit that owns programmes or Modules cannot receive child units.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: protect_active_academic_period_identity(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_active_academic_period_identity() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status <> 'DRAFT'
       AND (NEW.academic_year_id <> OLD.academic_year_id
            OR NEW.academic_period_type_id <> OLD.academic_period_type_id
            OR NEW.code <> OLD.code) THEN
        RAISE EXCEPTION 'An open or closed academic period cannot change year, type, or code.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: protect_active_intake_identity(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_active_intake_identity() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status <> 'DRAFT'
       AND (NEW.academic_year_id <> OLD.academic_year_id OR NEW.code <> OLD.code) THEN
        RAISE EXCEPTION 'An open or closed intake cannot change academic year or code.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: protect_active_programme_identity(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_active_programme_identity() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status <> 'DRAFT'
       AND (NEW.owning_academic_unit_id <> OLD.owning_academic_unit_id OR NEW.code <> OLD.code) THEN
        RAISE EXCEPTION 'A programme that has left draft cannot change owning academic unit or code.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: protect_approved_curriculum(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_approved_curriculum() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    parent_version_id uuid;
    parent_status varchar(20);
BEGIN
    parent_version_id := CASE
        WHEN TG_OP = 'INSERT' THEN NEW.programme_version_id
        ELSE OLD.programme_version_id
    END;

    SELECT status INTO parent_status
    FROM programme_versions
    WHERE id = parent_version_id;

    IF parent_status IS NULL THEN
        RAISE EXCEPTION 'The parent programme version does not exist.' USING ERRCODE = '23503';
    END IF;

    IF parent_status = 'RETIRED' THEN
        RAISE EXCEPTION 'A retired curriculum is immutable.' USING ERRCODE = '23514';
    END IF;

    IF parent_status NOT IN ('DRAFT', 'APPROVED') THEN
        RAISE EXCEPTION 'Curriculum records can only change while the programme version is DRAFT or APPROVED.' USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'UPDATE' AND (
        NEW.programme_version_id IS DISTINCT FROM OLD.programme_version_id
        OR NEW.module_id IS DISTINCT FROM OLD.module_id
    ) THEN
        RAISE EXCEPTION 'A curriculum Module identity cannot be changed; amend its governed placement instead.' USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'DELETE' AND parent_status = 'APPROVED' THEN
        RAISE EXCEPTION 'Approved curriculum Modules must use the governed soft-removal workflow.' USING ERRCODE = '23514';
    END IF;

    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;


--
-- Name: protect_intake_programme_level_target_removal(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_intake_programme_level_target_removal() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.deleted_at IS NULL AND (TG_OP = 'DELETE' OR NEW.deleted_at IS NOT NULL)
       AND EXISTS (
           SELECT 1
           FROM intake_programme_targets specific_target
           JOIN programmes programme ON programme.id = specific_target.programme_id
           WHERE specific_target.intake_id = OLD.intake_id
             AND specific_target.deleted_at IS NULL
             AND programme.programme_level_id = OLD.programme_level_id
             AND programme.deleted_at IS NULL
       ) THEN
        RAISE EXCEPTION 'Remove specific Programmes before removing their Programme Level from the intake.' USING ERRCODE = '23514';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;


--
-- Name: protect_intake_programme_target_draft(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_intake_programme_target_draft() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    governed_intake_id uuid;
    governed_intake_status varchar(20);
BEGIN
    governed_intake_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.intake_id ELSE NEW.intake_id END;
    SELECT status INTO governed_intake_status
    FROM intakes
    WHERE id = governed_intake_id AND deleted_at IS NULL;

    IF governed_intake_status IS NULL THEN
        RAISE EXCEPTION 'The governed intake does not exist.' USING ERRCODE = '23503';
    END IF;
    IF governed_intake_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Programme eligibility can only be changed while the intake is in draft.' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.intake_id IS DISTINCT FROM OLD.intake_id THEN
        RAISE EXCEPTION 'An intake programme target cannot move to another intake.' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'intake_programme_level_targets'
       AND (to_jsonb(NEW) ->> 'programme_level_id')
           IS DISTINCT FROM (to_jsonb(OLD) ->> 'programme_level_id') THEN
        RAISE EXCEPTION 'An intake Programme Level target identity cannot change.' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'intake_programme_type_targets'
       AND (to_jsonb(NEW) ->> 'programme_type_id')
           IS DISTINCT FROM (to_jsonb(OLD) ->> 'programme_type_id') THEN
        RAISE EXCEPTION 'An intake Programme Type target identity cannot change.' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'UPDATE' AND TG_TABLE_NAME = 'intake_programme_targets'
       AND (to_jsonb(NEW) ->> 'programme_id')
           IS DISTINCT FROM (to_jsonb(OLD) ->> 'programme_id') THEN
        RAISE EXCEPTION 'An intake Programme target identity cannot change.' USING ERRCODE = '23514';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;


--
-- Name: protect_intake_programme_type_target_removal(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_intake_programme_type_target_removal() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.deleted_at IS NULL AND (TG_OP = 'DELETE' OR NEW.deleted_at IS NOT NULL)
       AND EXISTS (
           SELECT 1
           FROM intake_programme_targets specific_target
           JOIN programmes programme ON programme.id = specific_target.programme_id
           WHERE specific_target.intake_id = OLD.intake_id
             AND specific_target.deleted_at IS NULL
             AND programme.programme_type_id = OLD.programme_type_id
             AND programme.deleted_at IS NULL
       ) THEN
        RAISE EXCEPTION 'Remove specific Programmes before removing their Programme Type from the intake.' USING ERRCODE = '23514';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;


--
-- Name: protect_programme_version_history(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_programme_version_history() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status = 'RETIRED' THEN
        RAISE EXCEPTION 'A retired programme version is immutable.' USING ERRCODE = '23514';
    END IF;
    IF OLD.status = 'APPROVED' AND (
        NEW.programme_id <> OLD.programme_id
        OR NEW.version_code <> OLD.version_code
        OR NEW.effective_from <> OLD.effective_from
        OR NEW.status NOT IN ('APPROVED', 'RETIRED')
    ) THEN
        RAISE EXCEPTION 'Approved programme version history is immutable.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: require_intake_programme_level_before_open(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.require_intake_programme_level_before_open() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.status = 'OPEN' AND OLD.status <> 'OPEN'
       AND NOT EXISTS (
           SELECT 1
           FROM intake_programme_level_targets target
           WHERE target.intake_id = NEW.id AND target.deleted_at IS NULL
       ) THEN
        RAISE EXCEPTION 'Select at least one Programme Level before opening the intake.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_academic_owner_is_leaf(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_academic_owner_is_leaf() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    leaf_allowed boolean;
    unit_status varchar(20);
BEGIN
    SELECT unit_type.is_leaf_allowed, unit.status
      INTO leaf_allowed, unit_status
    FROM academic_units unit
    JOIN academic_unit_types unit_type ON unit_type.id = unit.academic_unit_type_id
    WHERE unit.id = NEW.owning_academic_unit_id
      AND unit.deleted_at IS NULL
      AND unit_type.deleted_at IS NULL;

    IF leaf_allowed IS DISTINCT FROM true OR unit_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Programme and Module owners must be active leaf-eligible academic units.' USING ERRCODE = '23514';
    END IF;
    IF EXISTS (
        SELECT 1 FROM academic_units
        WHERE parent_id = NEW.owning_academic_unit_id AND deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'An academic unit with child units cannot own programmes or Modules.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_academic_unit_hierarchy(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_academic_unit_hierarchy() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    child_level integer;
    parent_level integer;
    parent_status varchar(20);
    cycle_found boolean;
BEGIN
    SELECT level_order INTO child_level
    FROM academic_unit_types
    WHERE id = NEW.academic_unit_type_id AND deleted_at IS NULL AND status = 'ACTIVE';

    IF child_level IS NULL THEN
        RAISE EXCEPTION 'Academic unit type must be active.' USING ERRCODE = '23514';
    END IF;

    IF NEW.parent_id IS NULL THEN
        IF EXISTS (
            SELECT 1 FROM academic_unit_types
            WHERE deleted_at IS NULL AND status = 'ACTIVE' AND level_order < child_level
        ) THEN
            RAISE EXCEPTION 'Only the highest configured academic unit type may be used at the root.' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;

    SELECT unit_type.level_order, parent.status
      INTO parent_level, parent_status
    FROM academic_units parent
    JOIN academic_unit_types unit_type ON unit_type.id = parent.academic_unit_type_id
    WHERE parent.id = NEW.parent_id AND parent.deleted_at IS NULL;

    IF parent_level IS NULL OR parent_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'Parent academic unit must exist and be active.' USING ERRCODE = '23514';
    END IF;
    IF child_level <> parent_level + 1 THEN
        RAISE EXCEPTION 'Academic unit hierarchy must follow configured type order.' USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        WITH RECURSIVE descendants AS (
            SELECT id FROM academic_units WHERE parent_id = NEW.id AND deleted_at IS NULL
            UNION ALL
            SELECT child.id
            FROM academic_units child
            JOIN descendants descendant ON child.parent_id = descendant.id
            WHERE child.deleted_at IS NULL
        )
        SELECT EXISTS (SELECT 1 FROM descendants WHERE id = NEW.parent_id) INTO cycle_found;
        IF cycle_found THEN
            RAISE EXCEPTION 'Academic unit hierarchy cannot contain a cycle.' USING ERRCODE = '23514';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_calendar_child_dates(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_calendar_child_dates() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    year_start date;
    year_end date;
BEGIN
    SELECT start_date, end_date INTO year_start, year_end
    FROM academic_years
    WHERE id = NEW.academic_year_id AND deleted_at IS NULL AND status <> 'ARCHIVED';
    IF year_start IS NULL OR NEW.start_date < year_start OR NEW.end_date > year_end THEN
        RAISE EXCEPTION 'Academic period dates must be contained by the selected academic year.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_intake_dates(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_intake_dates() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    year_start date;
    year_end date;
BEGIN
    SELECT start_date, end_date INTO year_start, year_end
    FROM academic_years
    WHERE id = NEW.academic_year_id AND deleted_at IS NULL AND status <> 'ARCHIVED';
    IF year_start IS NULL OR NEW.starts_on < year_start OR NEW.ends_on > year_end THEN
        RAISE EXCEPTION 'Intake dates must be contained by the selected academic year.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_intake_specific_programme_target(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_intake_specific_programme_target() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    selected_programme_level_id uuid;
BEGIN
    IF NEW.deleted_at IS NOT NULL THEN
        RETURN NEW;
    END IF;

    SELECT programme_level_id INTO selected_programme_level_id
    FROM programmes
    WHERE id = NEW.programme_id
      AND deleted_at IS NULL
      AND status = 'ACTIVE';

    IF selected_programme_level_id IS NULL THEN
        RAISE EXCEPTION 'A specific intake Programme must be active.' USING ERRCODE = '23514';
    END IF;
    IF NOT EXISTS (
        SELECT 1
        FROM intake_programme_level_targets target
        WHERE target.intake_id = NEW.intake_id
          AND target.programme_level_id = selected_programme_level_id
          AND target.deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'A specific Programme must belong to a selected intake Programme Level.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: academic_period_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_period_types (
    id uuid NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(120) NOT NULL,
    sort_order integer NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    change_reason character varying(1000) DEFAULT 'Initial record creation.'::character varying NOT NULL,
    CONSTRAINT ck_academic_period_type_change_reason CHECK ((length(TRIM(BOTH FROM change_reason)) >= 10)),
    CONSTRAINT ck_academic_period_types_code CHECK (((code)::text ~ '^[A-Z][A-Z0-9_-]*$'::text)),
    CONSTRAINT ck_academic_period_types_sort_order CHECK ((sort_order > 0)),
    CONSTRAINT ck_academic_period_types_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: academic_period_types_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_period_types_aud (
    id uuid NOT NULL,
    code character varying(40),
    name character varying(120),
    sort_order integer,
    status character varying(20),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint,
    change_reason character varying(1000)
);


--
-- Name: academic_periods; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_periods (
    id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    academic_period_type_id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(150) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    change_reason character varying(1000) DEFAULT 'Initial record creation.'::character varying NOT NULL,
    CONSTRAINT ck_academic_period_change_reason CHECK ((length(TRIM(BOTH FROM change_reason)) >= 10)),
    CONSTRAINT ck_academic_periods_code CHECK (((code)::text ~ '^[A-Z0-9][A-Z0-9_-]*$'::text)),
    CONSTRAINT ck_academic_periods_dates CHECK ((end_date >= start_date)),
    CONSTRAINT ck_academic_periods_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'OPEN'::character varying, 'CLOSED'::character varying, 'ARCHIVED'::character varying])::text[])))
);


--
-- Name: academic_periods_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_periods_aud (
    id uuid NOT NULL,
    academic_year_id uuid,
    academic_period_type_id uuid,
    code character varying(50),
    name character varying(150),
    start_date date,
    end_date date,
    status character varying(20),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint,
    change_reason character varying(1000)
);


--
-- Name: academic_unit_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_unit_types (
    id uuid NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(120) NOT NULL,
    level_order integer NOT NULL,
    is_leaf_allowed boolean NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_academic_unit_types_code CHECK (((code)::text ~ '^[A-Z][A-Z0-9_-]*$'::text)),
    CONSTRAINT ck_academic_unit_types_level_order CHECK ((level_order > 0)),
    CONSTRAINT ck_academic_unit_types_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: academic_unit_types_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_unit_types_aud (
    id uuid NOT NULL,
    code character varying(40),
    name character varying(120),
    level_order integer,
    is_leaf_allowed boolean,
    status character varying(20),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint
);


--
-- Name: academic_units; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_units (
    id uuid NOT NULL,
    academic_unit_type_id uuid NOT NULL,
    parent_id uuid,
    code character varying(50) NOT NULL,
    name character varying(180) NOT NULL,
    status character varying(20) NOT NULL,
    legacy_faculty_code character varying(50),
    legacy_department_code character varying(50),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_academic_units_code CHECK (((code)::text ~ '^[A-Z][A-Z0-9_-]*$'::text)),
    CONSTRAINT ck_academic_units_not_own_parent CHECK (((parent_id IS NULL) OR (parent_id <> id))),
    CONSTRAINT ck_academic_units_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: academic_units_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_units_aud (
    id uuid NOT NULL,
    academic_unit_type_id uuid,
    parent_id uuid,
    code character varying(50),
    name character varying(180),
    status character varying(20),
    legacy_faculty_code character varying(50),
    legacy_department_code character varying(50),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint
);


--
-- Name: academic_years; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_years (
    id uuid NOT NULL,
    name character varying(50) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    change_reason character varying(1000) DEFAULT 'Initial record creation.'::character varying NOT NULL,
    CONSTRAINT ck_academic_year_change_reason CHECK ((length(TRIM(BOTH FROM change_reason)) >= 10)),
    CONSTRAINT ck_academic_years_dates CHECK ((end_date >= start_date)),
    CONSTRAINT ck_academic_years_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'OPEN'::character varying, 'CLOSED'::character varying, 'ARCHIVED'::character varying])::text[])))
);


--
-- Name: academic_years_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_years_aud (
    id uuid NOT NULL,
    name character varying(50),
    start_date date,
    end_date date,
    status character varying(20),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint,
    change_reason character varying(1000)
);


--
-- Name: curriculum_modules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.curriculum_modules (
    id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    module_id uuid NOT NULL,
    period_number integer NOT NULL,
    module_type character varying(20) NOT NULL,
    credit_value numeric(6,2) NOT NULL,
    minimum_mark_required numeric(5,2),
    sort_order integer NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_curriculum_modules_credit_value CHECK ((credit_value > (0)::numeric)),
    CONSTRAINT ck_curriculum_modules_minimum_mark CHECK (((minimum_mark_required IS NULL) OR ((minimum_mark_required >= (0)::numeric) AND (minimum_mark_required <= (100)::numeric)))),
    CONSTRAINT ck_curriculum_modules_period CHECK ((period_number > 0)),
    CONSTRAINT ck_curriculum_modules_sort_order CHECK ((sort_order > 0)),
    CONSTRAINT ck_curriculum_modules_type CHECK (((module_type)::text = ANY ((ARRAY['COMPULSORY'::character varying, 'ELECTIVE'::character varying, 'OPTIONAL'::character varying])::text[])))
);


--
-- Name: curriculum_modules_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.curriculum_modules_aud (
    id uuid NOT NULL,
    programme_version_id uuid,
    module_id uuid,
    period_number integer,
    module_type character varying(20),
    credit_value numeric(6,2),
    minimum_mark_required numeric(5,2),
    sort_order integer,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint
);


--
-- Name: intake_programme_level_targets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.intake_programme_level_targets (
    id uuid NOT NULL,
    intake_id uuid NOT NULL,
    programme_level_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: intake_programme_level_targets_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.intake_programme_level_targets_aud (
    id uuid NOT NULL,
    intake_id uuid,
    programme_level_id uuid,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint
);


--
-- Name: intake_programme_targets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.intake_programme_targets (
    id uuid NOT NULL,
    intake_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: intake_programme_targets_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.intake_programme_targets_aud (
    id uuid NOT NULL,
    intake_id uuid,
    programme_id uuid,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint
);


--
-- Name: intake_programme_type_targets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.intake_programme_type_targets (
    id uuid NOT NULL,
    intake_id uuid NOT NULL,
    programme_type_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL
);


--
-- Name: TABLE intake_programme_type_targets; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.intake_programme_type_targets IS 'Deprecated intake eligibility targets retained for historical migration traceability. Programme Levels govern new eligibility.';


--
-- Name: intake_programme_type_targets_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.intake_programme_type_targets_aud (
    id uuid NOT NULL,
    intake_id uuid,
    programme_type_id uuid,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint
);


--
-- Name: intakes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.intakes (
    id uuid NOT NULL,
    academic_year_id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(150) NOT NULL,
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    change_reason character varying(1000) DEFAULT 'Initial record creation.'::character varying NOT NULL,
    maximum_programme_choices integer DEFAULT 3 NOT NULL,
    CONSTRAINT ck_intake_change_reason CHECK ((length(TRIM(BOTH FROM change_reason)) >= 10)),
    CONSTRAINT ck_intakes_code CHECK (((code)::text ~ '^[A-Z0-9][A-Z0-9_-]*$'::text)),
    CONSTRAINT ck_intakes_dates CHECK ((ends_on >= starts_on)),
    CONSTRAINT ck_intakes_maximum_programme_choices CHECK (((maximum_programme_choices >= 1) AND (maximum_programme_choices <= 20))),
    CONSTRAINT ck_intakes_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'OPEN'::character varying, 'CLOSED'::character varying, 'ARCHIVED'::character varying])::text[])))
);


--
-- Name: intakes_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.intakes_aud (
    id uuid NOT NULL,
    academic_year_id uuid,
    code character varying(50),
    name character varying(150),
    starts_on date,
    ends_on date,
    status character varying(20),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint,
    change_reason character varying(1000),
    maximum_programme_choices integer
);


--
-- Name: modules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.modules (
    id uuid NOT NULL,
    owning_academic_unit_id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(2000) NOT NULL,
    credit_value numeric(6,2) NOT NULL,
    academic_level integer NOT NULL,
    status character varying(20) NOT NULL,
    legacy_course_code character varying(50),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_modules_academic_level CHECK ((academic_level > 0)),
    CONSTRAINT ck_modules_code CHECK (((code)::text ~ '^[A-Z0-9][A-Z0-9_-]*$'::text)),
    CONSTRAINT ck_modules_credit_value CHECK ((credit_value > (0)::numeric)),
    CONSTRAINT ck_modules_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'RETIRED'::character varying])::text[])))
);


--
-- Name: modules_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.modules_aud (
    id uuid NOT NULL,
    owning_academic_unit_id uuid,
    code character varying(50),
    name character varying(200),
    description character varying(2000),
    credit_value numeric(6,2),
    academic_level integer,
    status character varying(20),
    legacy_course_code character varying(50),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint
);


--
-- Name: programme_entry_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.programme_entry_options (
    id uuid NOT NULL,
    programme_version_id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(200) NOT NULL,
    description character varying(1000),
    sort_order integer NOT NULL,
    is_active boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_programme_entry_options_code CHECK (((code)::text ~ '^[A-Z0-9][A-Z0-9_-]*$'::text)),
    CONSTRAINT ck_programme_entry_options_sort CHECK ((sort_order > 0))
);


--
-- Name: programme_entry_options_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.programme_entry_options_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    programme_version_id uuid,
    code character varying(50),
    name character varying(200),
    description character varying(1000),
    sort_order integer,
    is_active boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: programme_levels; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.programme_levels (
    id uuid NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(120) NOT NULL,
    sort_order integer NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_programme_levels_code CHECK (((code)::text ~ '^[A-Z][A-Z0-9_-]*$'::text)),
    CONSTRAINT ck_programme_levels_sort_order CHECK ((sort_order > 0)),
    CONSTRAINT ck_programme_levels_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: programme_levels_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.programme_levels_aud (
    id uuid NOT NULL,
    code character varying(40),
    name character varying(120),
    sort_order integer,
    status character varying(20),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint
);


--
-- Name: programme_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.programme_types (
    id uuid NOT NULL,
    code character varying(40) NOT NULL,
    name character varying(120) NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_programme_types_code CHECK (((code)::text ~ '^[A-Z][A-Z0-9_-]*$'::text)),
    CONSTRAINT ck_programme_types_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: programme_types_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.programme_types_aud (
    id uuid NOT NULL,
    code character varying(40),
    name character varying(120),
    status character varying(20),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint
);


--
-- Name: programme_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.programme_versions (
    id uuid NOT NULL,
    programme_id uuid NOT NULL,
    version_code character varying(40) NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    status character varying(20) NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    minimum_entry_option_selections integer DEFAULT 0 NOT NULL,
    maximum_entry_option_selections integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_programme_version_entry_option_limits CHECK (((minimum_entry_option_selections >= 0) AND (maximum_entry_option_selections >= minimum_entry_option_selections))),
    CONSTRAINT ck_programme_versions_approval CHECK (((((status)::text = 'DRAFT'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL)) OR (((status)::text = ANY ((ARRAY['APPROVED'::character varying, 'RETIRED'::character varying])::text[])) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL)))),
    CONSTRAINT ck_programme_versions_dates CHECK (((effective_to IS NULL) OR (effective_to >= effective_from))),
    CONSTRAINT ck_programme_versions_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'RETIRED'::character varying])::text[])))
);


--
-- Name: programme_versions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.programme_versions_aud (
    id uuid NOT NULL,
    programme_id uuid,
    version_code character varying(40),
    effective_from date,
    effective_to date,
    status character varying(20),
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint,
    minimum_entry_option_selections integer,
    maximum_entry_option_selections integer
);


--
-- Name: programmes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.programmes (
    id uuid NOT NULL,
    owning_academic_unit_id uuid NOT NULL,
    programme_type_id uuid NOT NULL,
    programme_level_id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(200) NOT NULL,
    award_name character varying(200) NOT NULL,
    minimum_duration_periods integer NOT NULL,
    maximum_duration_periods integer NOT NULL,
    status character varying(20) NOT NULL,
    legacy_programme_code character varying(50),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    change_reason character varying(1000) DEFAULT 'Initial record creation.'::character varying NOT NULL,
    CONSTRAINT ck_programmes_change_reason CHECK ((length(TRIM(BOTH FROM change_reason)) >= 10)),
    CONSTRAINT ck_programmes_code CHECK (((code)::text ~ '^[A-Z0-9][A-Z0-9_-]*$'::text)),
    CONSTRAINT ck_programmes_code_length CHECK ((char_length((code)::text) <= 5)),
    CONSTRAINT ck_programmes_duration CHECK (((minimum_duration_periods > 0) AND (maximum_duration_periods >= minimum_duration_periods))),
    CONSTRAINT ck_programmes_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'INACTIVE'::character varying, 'RETIRED'::character varying])::text[])))
);


--
-- Name: programmes_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.programmes_aud (
    id uuid NOT NULL,
    owning_academic_unit_id uuid,
    programme_type_id uuid,
    programme_level_id uuid,
    code character varying(50),
    name character varying(200),
    award_name character varying(200),
    minimum_duration_periods integer,
    maximum_duration_periods integer,
    status character varying(20),
    legacy_programme_code character varying(50),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    rev integer NOT NULL,
    revtype smallint,
    change_reason character varying(1000)
);


--
-- Name: revinfo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.revinfo (
    rev integer NOT NULL,
    revtstmp bigint NOT NULL,
    actor_user_id uuid,
    service_name character varying(100) DEFAULT 'academic-setup-service'::character varying NOT NULL,
    correlation_id character varying(100),
    reason character varying(500)
);


--
-- Name: revinfo_rev_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.revinfo ALTER COLUMN rev ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.revinfo_rev_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Data for Name: academic_period_types; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_period_types_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_periods; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_periods_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_unit_types; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_unit_types_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_units; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_units_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_years; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: academic_years_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: curriculum_modules; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: curriculum_modules_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: intake_programme_level_targets; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: intake_programme_level_targets_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: intake_programme_targets; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: intake_programme_targets_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: intake_programme_type_targets; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: intake_programme_type_targets_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: intakes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: intakes_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: modules; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: modules_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: programme_entry_options; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: programme_entry_options_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: programme_levels; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: programme_levels_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: programme_types; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: programme_types_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: programme_versions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: programme_versions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: programmes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: programmes_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: revinfo; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: revinfo_rev_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revinfo_rev_seq', 1, false);


--
-- Name: academic_period_types_aud academic_period_types_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_period_types_aud
    ADD CONSTRAINT academic_period_types_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: academic_period_types academic_period_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_period_types
    ADD CONSTRAINT academic_period_types_pkey PRIMARY KEY (id);


--
-- Name: academic_periods_aud academic_periods_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_periods_aud
    ADD CONSTRAINT academic_periods_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: academic_periods academic_periods_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_periods
    ADD CONSTRAINT academic_periods_pkey PRIMARY KEY (id);


--
-- Name: academic_unit_types_aud academic_unit_types_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_unit_types_aud
    ADD CONSTRAINT academic_unit_types_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: academic_unit_types academic_unit_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_unit_types
    ADD CONSTRAINT academic_unit_types_pkey PRIMARY KEY (id);


--
-- Name: academic_units_aud academic_units_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_units_aud
    ADD CONSTRAINT academic_units_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: academic_units academic_units_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_units
    ADD CONSTRAINT academic_units_pkey PRIMARY KEY (id);


--
-- Name: academic_years_aud academic_years_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_years_aud
    ADD CONSTRAINT academic_years_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: academic_years academic_years_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_years
    ADD CONSTRAINT academic_years_pkey PRIMARY KEY (id);


--
-- Name: curriculum_modules_aud curriculum_modules_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_modules_aud
    ADD CONSTRAINT curriculum_modules_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: curriculum_modules curriculum_modules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_modules
    ADD CONSTRAINT curriculum_modules_pkey PRIMARY KEY (id);


--
-- Name: academic_periods ex_academic_periods_effectivity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_periods
    ADD CONSTRAINT ex_academic_periods_effectivity EXCLUDE USING gist (academic_year_id WITH =, academic_period_type_id WITH =, daterange(start_date, end_date, '[]'::text) WITH &&) WHERE (((deleted_at IS NULL) AND ((status)::text <> 'ARCHIVED'::text)));


--
-- Name: academic_years ex_academic_years_effectivity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_years
    ADD CONSTRAINT ex_academic_years_effectivity EXCLUDE USING gist (daterange(start_date, end_date, '[]'::text) WITH &&) WHERE (((deleted_at IS NULL) AND ((status)::text <> 'ARCHIVED'::text)));


--
-- Name: programme_versions ex_approved_programme_version_effectivity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_versions
    ADD CONSTRAINT ex_approved_programme_version_effectivity EXCLUDE USING gist (programme_id WITH =, daterange(effective_from, COALESCE(effective_to, 'infinity'::date), '[]'::text) WITH &&) WHERE (((deleted_at IS NULL) AND ((status)::text = 'APPROVED'::text)));


--
-- Name: intake_programme_level_targets_aud intake_programme_level_targets_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_level_targets_aud
    ADD CONSTRAINT intake_programme_level_targets_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: intake_programme_level_targets intake_programme_level_targets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_level_targets
    ADD CONSTRAINT intake_programme_level_targets_pkey PRIMARY KEY (id);


--
-- Name: intake_programme_targets_aud intake_programme_targets_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_targets_aud
    ADD CONSTRAINT intake_programme_targets_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: intake_programme_targets intake_programme_targets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_targets
    ADD CONSTRAINT intake_programme_targets_pkey PRIMARY KEY (id);


--
-- Name: intake_programme_type_targets_aud intake_programme_type_targets_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_type_targets_aud
    ADD CONSTRAINT intake_programme_type_targets_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: intake_programme_type_targets intake_programme_type_targets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_type_targets
    ADD CONSTRAINT intake_programme_type_targets_pkey PRIMARY KEY (id);


--
-- Name: intakes_aud intakes_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intakes_aud
    ADD CONSTRAINT intakes_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: intakes intakes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intakes
    ADD CONSTRAINT intakes_pkey PRIMARY KEY (id);


--
-- Name: modules_aud modules_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modules_aud
    ADD CONSTRAINT modules_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: modules modules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modules
    ADD CONSTRAINT modules_pkey PRIMARY KEY (id);


--
-- Name: programme_entry_options_aud programme_entry_options_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_entry_options_aud
    ADD CONSTRAINT programme_entry_options_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: programme_entry_options programme_entry_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_entry_options
    ADD CONSTRAINT programme_entry_options_pkey PRIMARY KEY (id);


--
-- Name: programme_levels_aud programme_levels_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_levels_aud
    ADD CONSTRAINT programme_levels_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: programme_levels programme_levels_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_levels
    ADD CONSTRAINT programme_levels_pkey PRIMARY KEY (id);


--
-- Name: programme_types_aud programme_types_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_types_aud
    ADD CONSTRAINT programme_types_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: programme_types programme_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_types
    ADD CONSTRAINT programme_types_pkey PRIMARY KEY (id);


--
-- Name: programme_versions_aud programme_versions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_versions_aud
    ADD CONSTRAINT programme_versions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: programme_versions programme_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_versions
    ADD CONSTRAINT programme_versions_pkey PRIMARY KEY (id);


--
-- Name: programmes_aud programmes_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programmes_aud
    ADD CONSTRAINT programmes_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: programmes programmes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programmes
    ADD CONSTRAINT programmes_pkey PRIMARY KEY (id);


--
-- Name: revinfo revinfo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revinfo
    ADD CONSTRAINT revinfo_pkey PRIMARY KEY (rev);


--
-- Name: academic_period_types uk_academic_period_types_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_period_types
    ADD CONSTRAINT uk_academic_period_types_code UNIQUE (code);


--
-- Name: academic_period_types uk_academic_period_types_sort_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_period_types
    ADD CONSTRAINT uk_academic_period_types_sort_order UNIQUE (sort_order);


--
-- Name: academic_periods uk_academic_periods_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_periods
    ADD CONSTRAINT uk_academic_periods_code UNIQUE (code);


--
-- Name: academic_unit_types uk_academic_unit_types_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_unit_types
    ADD CONSTRAINT uk_academic_unit_types_code UNIQUE (code);


--
-- Name: academic_unit_types uk_academic_unit_types_level_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_unit_types
    ADD CONSTRAINT uk_academic_unit_types_level_order UNIQUE (level_order);


--
-- Name: academic_units uk_academic_units_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_units
    ADD CONSTRAINT uk_academic_units_code UNIQUE (code);


--
-- Name: academic_years uk_academic_years_name; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_years
    ADD CONSTRAINT uk_academic_years_name UNIQUE (name);


--
-- Name: curriculum_modules uk_curriculum_modules_version_module; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_modules
    ADD CONSTRAINT uk_curriculum_modules_version_module UNIQUE (programme_version_id, module_id);


--
-- Name: curriculum_modules uk_curriculum_modules_version_sort; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_modules
    ADD CONSTRAINT uk_curriculum_modules_version_sort UNIQUE (programme_version_id, sort_order);


--
-- Name: intakes uk_intakes_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intakes
    ADD CONSTRAINT uk_intakes_code UNIQUE (code);


--
-- Name: modules uk_modules_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modules
    ADD CONSTRAINT uk_modules_code UNIQUE (code);


--
-- Name: programme_entry_options uk_programme_entry_options_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_entry_options
    ADD CONSTRAINT uk_programme_entry_options_code UNIQUE (programme_version_id, code);


--
-- Name: programme_entry_options uk_programme_entry_options_sort; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_entry_options
    ADD CONSTRAINT uk_programme_entry_options_sort UNIQUE (programme_version_id, sort_order);


--
-- Name: programme_levels uk_programme_levels_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_levels
    ADD CONSTRAINT uk_programme_levels_code UNIQUE (code);


--
-- Name: programme_levels uk_programme_levels_sort_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_levels
    ADD CONSTRAINT uk_programme_levels_sort_order UNIQUE (sort_order);


--
-- Name: programme_types uk_programme_types_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_types
    ADD CONSTRAINT uk_programme_types_code UNIQUE (code);


--
-- Name: programme_versions uk_programme_versions_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_versions
    ADD CONSTRAINT uk_programme_versions_code UNIQUE (programme_id, version_code);


--
-- Name: programmes uk_programmes_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programmes
    ADD CONSTRAINT uk_programmes_code UNIQUE (code);


--
-- Name: ix_academic_units_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_academic_units_parent ON public.academic_units USING btree (parent_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_curriculum_modules_programme_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_curriculum_modules_programme_version ON public.curriculum_modules USING btree (programme_version_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_intake_programme_level_targets_intake; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_intake_programme_level_targets_intake ON public.intake_programme_level_targets USING btree (intake_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_intake_programme_targets_intake; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_intake_programme_targets_intake ON public.intake_programme_targets USING btree (intake_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_intake_programme_type_targets_intake; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_intake_programme_type_targets_intake ON public.intake_programme_type_targets USING btree (intake_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_modules_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_modules_owner ON public.modules USING btree (owning_academic_unit_id) WHERE (deleted_at IS NULL);


--
-- Name: ix_programme_entry_options_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_programme_entry_options_version ON public.programme_entry_options USING btree (programme_version_id, sort_order) WHERE ((deleted_at IS NULL) AND is_active);


--
-- Name: ix_programmes_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_programmes_owner ON public.programmes USING btree (owning_academic_unit_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_intake_programme_level_targets_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_intake_programme_level_targets_active ON public.intake_programme_level_targets USING btree (intake_id, programme_level_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_intake_programme_targets_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_intake_programme_targets_active ON public.intake_programme_targets USING btree (intake_id, programme_id) WHERE (deleted_at IS NULL);


--
-- Name: uk_intake_programme_type_targets_active; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_intake_programme_type_targets_active ON public.intake_programme_type_targets USING btree (intake_id, programme_type_id) WHERE (deleted_at IS NULL);


--
-- Name: academic_units trg_prevent_children_on_academic_owner; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_prevent_children_on_academic_owner BEFORE INSERT OR UPDATE OF parent_id ON public.academic_units FOR EACH ROW EXECUTE FUNCTION public.prevent_children_on_owning_academic_unit();


--
-- Name: academic_periods trg_protect_academic_period_identity; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_academic_period_identity BEFORE UPDATE ON public.academic_periods FOR EACH ROW EXECUTE FUNCTION public.protect_active_academic_period_identity();


--
-- Name: curriculum_modules trg_protect_approved_curriculum; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_approved_curriculum BEFORE INSERT OR DELETE OR UPDATE ON public.curriculum_modules FOR EACH ROW EXECUTE FUNCTION public.protect_approved_curriculum();


--
-- Name: intakes trg_protect_intake_identity; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_intake_identity BEFORE UPDATE ON public.intakes FOR EACH ROW EXECUTE FUNCTION public.protect_active_intake_identity();


--
-- Name: intake_programme_level_targets trg_protect_intake_programme_level_target_draft; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_intake_programme_level_target_draft BEFORE INSERT OR DELETE OR UPDATE ON public.intake_programme_level_targets FOR EACH ROW EXECUTE FUNCTION public.protect_intake_programme_target_draft();


--
-- Name: intake_programme_level_targets trg_protect_intake_programme_level_target_removal; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_intake_programme_level_target_removal BEFORE DELETE OR UPDATE ON public.intake_programme_level_targets FOR EACH ROW EXECUTE FUNCTION public.protect_intake_programme_level_target_removal();


--
-- Name: intake_programme_targets trg_protect_intake_programme_target_draft; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_intake_programme_target_draft BEFORE INSERT OR DELETE OR UPDATE ON public.intake_programme_targets FOR EACH ROW EXECUTE FUNCTION public.protect_intake_programme_target_draft();


--
-- Name: intake_programme_type_targets trg_protect_intake_programme_type_target_draft; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_intake_programme_type_target_draft BEFORE INSERT OR DELETE OR UPDATE ON public.intake_programme_type_targets FOR EACH ROW EXECUTE FUNCTION public.protect_intake_programme_target_draft();


--
-- Name: intake_programme_type_targets trg_protect_intake_programme_type_target_removal; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_intake_programme_type_target_removal BEFORE DELETE OR UPDATE ON public.intake_programme_type_targets FOR EACH ROW EXECUTE FUNCTION public.protect_intake_programme_type_target_removal();


--
-- Name: programmes trg_protect_programme_identity; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_programme_identity BEFORE UPDATE ON public.programmes FOR EACH ROW EXECUTE FUNCTION public.protect_active_programme_identity();


--
-- Name: programme_versions trg_protect_programme_version_history; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_protect_programme_version_history BEFORE UPDATE ON public.programme_versions FOR EACH ROW EXECUTE FUNCTION public.protect_programme_version_history();


--
-- Name: intakes trg_require_intake_programme_level_before_open; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_require_intake_programme_level_before_open BEFORE UPDATE OF status ON public.intakes FOR EACH ROW EXECUTE FUNCTION public.require_intake_programme_level_before_open();


--
-- Name: academic_periods trg_validate_academic_period_dates; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_academic_period_dates BEFORE INSERT OR UPDATE OF academic_year_id, start_date, end_date ON public.academic_periods FOR EACH ROW EXECUTE FUNCTION public.validate_calendar_child_dates();


--
-- Name: academic_units trg_validate_academic_unit_hierarchy; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_academic_unit_hierarchy BEFORE INSERT OR UPDATE OF academic_unit_type_id, parent_id ON public.academic_units FOR EACH ROW EXECUTE FUNCTION public.validate_academic_unit_hierarchy();


--
-- Name: intakes trg_validate_intake_dates; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_intake_dates BEFORE INSERT OR UPDATE OF academic_year_id, starts_on, ends_on ON public.intakes FOR EACH ROW EXECUTE FUNCTION public.validate_intake_dates();


--
-- Name: intake_programme_targets trg_validate_intake_specific_programme_target; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_intake_specific_programme_target BEFORE INSERT OR UPDATE ON public.intake_programme_targets FOR EACH ROW EXECUTE FUNCTION public.validate_intake_specific_programme_target();


--
-- Name: modules trg_validate_module_owner; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_module_owner BEFORE INSERT OR UPDATE OF owning_academic_unit_id ON public.modules FOR EACH ROW EXECUTE FUNCTION public.validate_academic_owner_is_leaf();


--
-- Name: programmes trg_validate_programme_owner; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_validate_programme_owner BEFORE INSERT OR UPDATE OF owning_academic_unit_id ON public.programmes FOR EACH ROW EXECUTE FUNCTION public.validate_academic_owner_is_leaf();


--
-- Name: academic_period_types_aud academic_period_types_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_period_types_aud
    ADD CONSTRAINT academic_period_types_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: academic_periods academic_periods_academic_period_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_periods
    ADD CONSTRAINT academic_periods_academic_period_type_id_fkey FOREIGN KEY (academic_period_type_id) REFERENCES public.academic_period_types(id);


--
-- Name: academic_periods academic_periods_academic_year_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_periods
    ADD CONSTRAINT academic_periods_academic_year_id_fkey FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: academic_periods_aud academic_periods_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_periods_aud
    ADD CONSTRAINT academic_periods_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: academic_unit_types_aud academic_unit_types_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_unit_types_aud
    ADD CONSTRAINT academic_unit_types_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: academic_units academic_units_academic_unit_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_units
    ADD CONSTRAINT academic_units_academic_unit_type_id_fkey FOREIGN KEY (academic_unit_type_id) REFERENCES public.academic_unit_types(id);


--
-- Name: academic_units_aud academic_units_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_units_aud
    ADD CONSTRAINT academic_units_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: academic_units academic_units_parent_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_units
    ADD CONSTRAINT academic_units_parent_id_fkey FOREIGN KEY (parent_id) REFERENCES public.academic_units(id);


--
-- Name: academic_years_aud academic_years_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_years_aud
    ADD CONSTRAINT academic_years_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: curriculum_modules_aud curriculum_modules_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_modules_aud
    ADD CONSTRAINT curriculum_modules_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: curriculum_modules curriculum_modules_module_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_modules
    ADD CONSTRAINT curriculum_modules_module_id_fkey FOREIGN KEY (module_id) REFERENCES public.modules(id);


--
-- Name: curriculum_modules curriculum_modules_programme_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.curriculum_modules
    ADD CONSTRAINT curriculum_modules_programme_version_id_fkey FOREIGN KEY (programme_version_id) REFERENCES public.programme_versions(id);


--
-- Name: intake_programme_level_targets_aud intake_programme_level_targets_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_level_targets_aud
    ADD CONSTRAINT intake_programme_level_targets_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: intake_programme_level_targets intake_programme_level_targets_intake_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_level_targets
    ADD CONSTRAINT intake_programme_level_targets_intake_id_fkey FOREIGN KEY (intake_id) REFERENCES public.intakes(id);


--
-- Name: intake_programme_level_targets intake_programme_level_targets_programme_level_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_level_targets
    ADD CONSTRAINT intake_programme_level_targets_programme_level_id_fkey FOREIGN KEY (programme_level_id) REFERENCES public.programme_levels(id);


--
-- Name: intake_programme_targets_aud intake_programme_targets_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_targets_aud
    ADD CONSTRAINT intake_programme_targets_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: intake_programme_targets intake_programme_targets_intake_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_targets
    ADD CONSTRAINT intake_programme_targets_intake_id_fkey FOREIGN KEY (intake_id) REFERENCES public.intakes(id);


--
-- Name: intake_programme_targets intake_programme_targets_programme_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_targets
    ADD CONSTRAINT intake_programme_targets_programme_id_fkey FOREIGN KEY (programme_id) REFERENCES public.programmes(id);


--
-- Name: intake_programme_type_targets_aud intake_programme_type_targets_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_type_targets_aud
    ADD CONSTRAINT intake_programme_type_targets_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: intake_programme_type_targets intake_programme_type_targets_intake_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_type_targets
    ADD CONSTRAINT intake_programme_type_targets_intake_id_fkey FOREIGN KEY (intake_id) REFERENCES public.intakes(id);


--
-- Name: intake_programme_type_targets intake_programme_type_targets_programme_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intake_programme_type_targets
    ADD CONSTRAINT intake_programme_type_targets_programme_type_id_fkey FOREIGN KEY (programme_type_id) REFERENCES public.programme_types(id);


--
-- Name: intakes intakes_academic_year_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intakes
    ADD CONSTRAINT intakes_academic_year_id_fkey FOREIGN KEY (academic_year_id) REFERENCES public.academic_years(id);


--
-- Name: intakes_aud intakes_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.intakes_aud
    ADD CONSTRAINT intakes_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: modules_aud modules_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modules_aud
    ADD CONSTRAINT modules_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: modules modules_owning_academic_unit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.modules
    ADD CONSTRAINT modules_owning_academic_unit_id_fkey FOREIGN KEY (owning_academic_unit_id) REFERENCES public.academic_units(id);


--
-- Name: programme_entry_options_aud programme_entry_options_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_entry_options_aud
    ADD CONSTRAINT programme_entry_options_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: programme_entry_options programme_entry_options_programme_version_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_entry_options
    ADD CONSTRAINT programme_entry_options_programme_version_id_fkey FOREIGN KEY (programme_version_id) REFERENCES public.programme_versions(id);


--
-- Name: programme_levels_aud programme_levels_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_levels_aud
    ADD CONSTRAINT programme_levels_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: programme_types_aud programme_types_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_types_aud
    ADD CONSTRAINT programme_types_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: programme_versions_aud programme_versions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_versions_aud
    ADD CONSTRAINT programme_versions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: programme_versions programme_versions_programme_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programme_versions
    ADD CONSTRAINT programme_versions_programme_id_fkey FOREIGN KEY (programme_id) REFERENCES public.programmes(id);


--
-- Name: programmes_aud programmes_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programmes_aud
    ADD CONSTRAINT programmes_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: programmes programmes_owning_academic_unit_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programmes
    ADD CONSTRAINT programmes_owning_academic_unit_id_fkey FOREIGN KEY (owning_academic_unit_id) REFERENCES public.academic_units(id);


--
-- Name: programmes programmes_programme_level_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programmes
    ADD CONSTRAINT programmes_programme_level_id_fkey FOREIGN KEY (programme_level_id) REFERENCES public.programme_levels(id);


--
-- Name: programmes programmes_programme_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.programmes
    ADD CONSTRAINT programmes_programme_type_id_fkey FOREIGN KEY (programme_type_id) REFERENCES public.programme_types(id);


--
-- PostgreSQL database dump complete
--


