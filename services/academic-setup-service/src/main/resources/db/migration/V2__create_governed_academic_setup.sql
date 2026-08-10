CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE academic_unit_types (
    id uuid PRIMARY KEY,
    code varchar(40) NOT NULL,
    name varchar(120) NOT NULL,
    level_order integer NOT NULL,
    is_leaf_allowed boolean NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_academic_unit_types_code UNIQUE (code),
    CONSTRAINT uk_academic_unit_types_level_order UNIQUE (level_order),
    CONSTRAINT ck_academic_unit_types_code CHECK (code ~ '^[A-Z][A-Z0-9_-]*$'),
    CONSTRAINT ck_academic_unit_types_level_order CHECK (level_order > 0),
    CONSTRAINT ck_academic_unit_types_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE academic_units (
    id uuid PRIMARY KEY,
    academic_unit_type_id uuid NOT NULL REFERENCES academic_unit_types (id),
    parent_id uuid REFERENCES academic_units (id),
    code varchar(50) NOT NULL,
    name varchar(180) NOT NULL,
    status varchar(20) NOT NULL,
    legacy_faculty_code varchar(50),
    legacy_department_code varchar(50),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_academic_units_code UNIQUE (code),
    CONSTRAINT ck_academic_units_code CHECK (code ~ '^[A-Z][A-Z0-9_-]*$'),
    CONSTRAINT ck_academic_units_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_academic_units_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX ix_academic_units_parent ON academic_units (parent_id) WHERE deleted_at IS NULL;

CREATE TABLE academic_years (
    id uuid PRIMARY KEY,
    name varchar(50) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_academic_years_name UNIQUE (name),
    CONSTRAINT ck_academic_years_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_academic_years_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT ex_academic_years_effectivity EXCLUDE USING gist (
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (deleted_at IS NULL AND status <> 'ARCHIVED')
);

CREATE TABLE academic_period_types (
    id uuid PRIMARY KEY,
    code varchar(40) NOT NULL,
    name varchar(120) NOT NULL,
    sort_order integer NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_academic_period_types_code UNIQUE (code),
    CONSTRAINT uk_academic_period_types_sort_order UNIQUE (sort_order),
    CONSTRAINT ck_academic_period_types_code CHECK (code ~ '^[A-Z][A-Z0-9_-]*$'),
    CONSTRAINT ck_academic_period_types_sort_order CHECK (sort_order > 0),
    CONSTRAINT ck_academic_period_types_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE academic_periods (
    id uuid PRIMARY KEY,
    academic_year_id uuid NOT NULL REFERENCES academic_years (id),
    academic_period_type_id uuid NOT NULL REFERENCES academic_period_types (id),
    code varchar(50) NOT NULL,
    name varchar(150) NOT NULL,
    start_date date NOT NULL,
    end_date date NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_academic_periods_code UNIQUE (code),
    CONSTRAINT ck_academic_periods_code CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]*$'),
    CONSTRAINT ck_academic_periods_dates CHECK (end_date >= start_date),
    CONSTRAINT ck_academic_periods_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT ex_academic_periods_effectivity EXCLUDE USING gist (
        academic_year_id WITH =,
        academic_period_type_id WITH =,
        daterange(start_date, end_date, '[]') WITH &&
    ) WHERE (deleted_at IS NULL AND status <> 'ARCHIVED')
);

CREATE TABLE intakes (
    id uuid PRIMARY KEY,
    academic_year_id uuid NOT NULL REFERENCES academic_years (id),
    code varchar(50) NOT NULL,
    name varchar(150) NOT NULL,
    starts_on date NOT NULL,
    ends_on date NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_intakes_code UNIQUE (code),
    CONSTRAINT ck_intakes_code CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]*$'),
    CONSTRAINT ck_intakes_dates CHECK (ends_on >= starts_on),
    CONSTRAINT ck_intakes_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'ARCHIVED'))
);

CREATE TABLE programme_levels (
    id uuid PRIMARY KEY,
    code varchar(40) NOT NULL,
    name varchar(120) NOT NULL,
    sort_order integer NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_programme_levels_code UNIQUE (code),
    CONSTRAINT uk_programme_levels_sort_order UNIQUE (sort_order),
    CONSTRAINT ck_programme_levels_code CHECK (code ~ '^[A-Z][A-Z0-9_-]*$'),
    CONSTRAINT ck_programme_levels_sort_order CHECK (sort_order > 0),
    CONSTRAINT ck_programme_levels_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE programme_types (
    id uuid PRIMARY KEY,
    code varchar(40) NOT NULL,
    name varchar(120) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_programme_types_code UNIQUE (code),
    CONSTRAINT ck_programme_types_code CHECK (code ~ '^[A-Z][A-Z0-9_-]*$'),
    CONSTRAINT ck_programme_types_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE programmes (
    id uuid PRIMARY KEY,
    owning_academic_unit_id uuid NOT NULL REFERENCES academic_units (id),
    programme_type_id uuid NOT NULL REFERENCES programme_types (id),
    programme_level_id uuid NOT NULL REFERENCES programme_levels (id),
    code varchar(50) NOT NULL,
    name varchar(200) NOT NULL,
    award_name varchar(200) NOT NULL,
    minimum_duration_periods integer NOT NULL,
    maximum_duration_periods integer NOT NULL,
    status varchar(20) NOT NULL,
    legacy_programme_code varchar(50),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_programmes_code UNIQUE (code),
    CONSTRAINT ck_programmes_code CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]*$'),
    CONSTRAINT ck_programmes_duration CHECK (
        minimum_duration_periods > 0 AND maximum_duration_periods >= minimum_duration_periods
    ),
    CONSTRAINT ck_programmes_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'RETIRED'))
);

CREATE INDEX ix_programmes_owner ON programmes (owning_academic_unit_id) WHERE deleted_at IS NULL;

CREATE TABLE programme_versions (
    id uuid PRIMARY KEY,
    programme_id uuid NOT NULL REFERENCES programmes (id),
    version_code varchar(40) NOT NULL,
    effective_from date NOT NULL,
    effective_to date,
    status varchar(20) NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_programme_versions_code UNIQUE (programme_id, version_code),
    CONSTRAINT ck_programme_versions_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_programme_versions_status CHECK (status IN ('DRAFT', 'APPROVED', 'RETIRED')),
    CONSTRAINT ck_programme_versions_approval CHECK (
        (status = 'DRAFT' AND approved_by_user_id IS NULL AND approved_at IS NULL)
        OR (status IN ('APPROVED', 'RETIRED') AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT ex_approved_programme_version_effectivity EXCLUDE USING gist (
        programme_id WITH =,
        daterange(effective_from, COALESCE(effective_to, 'infinity'::date), '[]') WITH &&
    ) WHERE (deleted_at IS NULL AND status = 'APPROVED')
);

CREATE TABLE modules (
    id uuid PRIMARY KEY,
    owning_academic_unit_id uuid NOT NULL REFERENCES academic_units (id),
    code varchar(50) NOT NULL,
    name varchar(200) NOT NULL,
    description varchar(2000) NOT NULL,
    credit_value numeric(6, 2) NOT NULL,
    academic_level integer NOT NULL,
    status varchar(20) NOT NULL,
    legacy_course_code varchar(50),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_modules_code UNIQUE (code),
    CONSTRAINT ck_modules_code CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]*$'),
    CONSTRAINT ck_modules_credit_value CHECK (credit_value > 0),
    CONSTRAINT ck_modules_academic_level CHECK (academic_level > 0),
    CONSTRAINT ck_modules_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'RETIRED'))
);

CREATE INDEX ix_modules_owner ON modules (owning_academic_unit_id) WHERE deleted_at IS NULL;

CREATE TABLE curriculum_modules (
    id uuid PRIMARY KEY,
    programme_version_id uuid NOT NULL REFERENCES programme_versions (id),
    module_id uuid NOT NULL REFERENCES modules (id),
    period_number integer NOT NULL,
    module_type varchar(20) NOT NULL,
    credit_value numeric(6, 2) NOT NULL,
    minimum_mark_required numeric(5, 2),
    sort_order integer NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamptz,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT uk_curriculum_modules_version_module UNIQUE (programme_version_id, module_id),
    CONSTRAINT uk_curriculum_modules_version_sort UNIQUE (programme_version_id, sort_order),
    CONSTRAINT ck_curriculum_modules_period CHECK (period_number > 0),
    CONSTRAINT ck_curriculum_modules_type CHECK (module_type IN ('COMPULSORY', 'ELECTIVE', 'OPTIONAL')),
    CONSTRAINT ck_curriculum_modules_credit_value CHECK (credit_value > 0),
    CONSTRAINT ck_curriculum_modules_minimum_mark CHECK (
        minimum_mark_required IS NULL OR minimum_mark_required BETWEEN 0 AND 100
    ),
    CONSTRAINT ck_curriculum_modules_sort_order CHECK (sort_order > 0)
);

CREATE INDEX ix_curriculum_modules_programme_version
    ON curriculum_modules (programme_version_id) WHERE deleted_at IS NULL;

CREATE OR REPLACE FUNCTION validate_academic_unit_hierarchy()
RETURNS trigger
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

CREATE TRIGGER trg_validate_academic_unit_hierarchy
BEFORE INSERT OR UPDATE OF academic_unit_type_id, parent_id ON academic_units
FOR EACH ROW EXECUTE FUNCTION validate_academic_unit_hierarchy();

CREATE OR REPLACE FUNCTION validate_academic_owner_is_leaf()
RETURNS trigger
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

CREATE TRIGGER trg_validate_programme_owner
BEFORE INSERT OR UPDATE OF owning_academic_unit_id ON programmes
FOR EACH ROW EXECUTE FUNCTION validate_academic_owner_is_leaf();

CREATE TRIGGER trg_validate_module_owner
BEFORE INSERT OR UPDATE OF owning_academic_unit_id ON modules
FOR EACH ROW EXECUTE FUNCTION validate_academic_owner_is_leaf();

CREATE OR REPLACE FUNCTION prevent_children_on_owning_academic_unit()
RETURNS trigger
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

CREATE TRIGGER trg_prevent_children_on_academic_owner
BEFORE INSERT OR UPDATE OF parent_id ON academic_units
FOR EACH ROW EXECUTE FUNCTION prevent_children_on_owning_academic_unit();

CREATE OR REPLACE FUNCTION validate_calendar_child_dates()
RETURNS trigger
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

CREATE TRIGGER trg_validate_academic_period_dates
BEFORE INSERT OR UPDATE OF academic_year_id, start_date, end_date ON academic_periods
FOR EACH ROW EXECUTE FUNCTION validate_calendar_child_dates();

CREATE OR REPLACE FUNCTION validate_intake_dates()
RETURNS trigger
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

CREATE TRIGGER trg_validate_intake_dates
BEFORE INSERT OR UPDATE OF academic_year_id, starts_on, ends_on ON intakes
FOR EACH ROW EXECUTE FUNCTION validate_intake_dates();

CREATE OR REPLACE FUNCTION protect_approved_curriculum()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    parent_version_id uuid;
    parent_status varchar(20);
BEGIN
    parent_version_id := COALESCE(NEW.programme_version_id, OLD.programme_version_id);
    SELECT status INTO parent_status FROM programme_versions WHERE id = parent_version_id;
    IF parent_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'Curriculum records can only change while the programme version is DRAFT.' USING ERRCODE = '23514';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER trg_protect_approved_curriculum
BEFORE INSERT OR UPDATE OR DELETE ON curriculum_modules
FOR EACH ROW EXECUTE FUNCTION protect_approved_curriculum();

CREATE OR REPLACE FUNCTION protect_programme_version_history()
RETURNS trigger
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

CREATE TRIGGER trg_protect_programme_version_history
BEFORE UPDATE ON programme_versions
FOR EACH ROW EXECUTE FUNCTION protect_programme_version_history();

DO $$
DECLARE
    business_table_name text;
    audit_table_name text;
BEGIN
    FOREACH business_table_name IN ARRAY ARRAY[
        'academic_unit_types', 'academic_units', 'academic_years', 'academic_period_types',
        'academic_periods', 'intakes', 'programme_levels', 'programme_types', 'programmes',
        'programme_versions', 'modules', 'curriculum_modules'
    ]
    LOOP
        audit_table_name := business_table_name || '_aud';
        EXECUTE format('CREATE TABLE %I AS TABLE %I WITH NO DATA', audit_table_name, business_table_name);
        EXECUTE format(
            'ALTER TABLE %I ADD COLUMN rev integer NOT NULL REFERENCES revinfo (rev), ADD COLUMN revtype smallint, ADD PRIMARY KEY (id, rev)',
            audit_table_name
        );
    END LOOP;
END;
$$;
