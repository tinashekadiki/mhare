-- Author: Tinashe K
-- Canonical clean-slate baseline for finance-service.

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
-- Name: enforce_exchange_rate_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_exchange_rate_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE conflicting_rate uuid;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Exchange-rate evidence cannot be deleted'; END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status='RETIRED' THEN RAISE EXCEPTION 'Retired exchange-rate evidence is immutable'; END IF;
        IF OLD.status='ACTIVE' AND ROW(NEW.source_currency_code,NEW.base_currency_code,NEW.rate_to_base,NEW.effective_from,NEW.effective_to,NEW.source_name,NEW.source_reference,NEW.prepared_by_user_id) IS DISTINCT FROM ROW(OLD.source_currency_code,OLD.base_currency_code,OLD.rate_to_base,OLD.effective_from,OLD.effective_to,OLD.source_name,OLD.source_reference,OLD.prepared_by_user_id) THEN RAISE EXCEPTION 'Active exchange-rate evidence is immutable'; END IF;
        IF NOT ((OLD.status='DRAFT' AND NEW.status IN ('DRAFT','ACTIVE')) OR (OLD.status='ACTIVE' AND NEW.status IN ('ACTIVE','RETIRED'))) THEN RAISE EXCEPTION 'Invalid exchange-rate status transition'; END IF;
    END IF;
    IF NEW.status='ACTIVE' AND (TG_OP='INSERT' OR OLD.status IS DISTINCT FROM 'ACTIVE') THEN
        SELECT id INTO conflicting_rate FROM exchange_rates WHERE id<>NEW.id AND source_currency_code=NEW.source_currency_code AND base_currency_code='USD' AND status='ACTIVE' AND deleted_at IS NULL AND tstzrange(effective_from,coalesce(effective_to,'infinity'::timestamptz),'[)') && tstzrange(NEW.effective_from,coalesce(NEW.effective_to,'infinity'::timestamptz),'[)') LIMIT 1;
        IF conflicting_rate IS NOT NULL THEN RAISE EXCEPTION 'Active exchange rate overlaps another rate for this currency'; END IF;
    END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_finance_billing_event_discount_evidence(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_billing_event_discount_evidence() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE rule_status varchar(20); rule_code varchar(50); rule_percentage numeric(7,4);
BEGIN
    IF TG_OP='INSERT' THEN
        NEW.gross_transaction_amount=coalesce(NEW.gross_transaction_amount,NEW.transaction_amount);
        NEW.gross_base_amount=coalesce(NEW.gross_base_amount,NEW.base_amount);
        NEW.transaction_discount_amount=coalesce(NEW.transaction_discount_amount,0);
        NEW.base_discount_amount=coalesce(NEW.base_discount_amount,0);
    END IF;
    IF TG_OP='UPDATE' AND ROW(NEW.discount_rule_id,NEW.discount_rule_code,NEW.discount_percentage,
            NEW.gross_transaction_amount,NEW.transaction_discount_amount,NEW.gross_base_amount,NEW.base_discount_amount)
        IS DISTINCT FROM ROW(OLD.discount_rule_id,OLD.discount_rule_code,OLD.discount_percentage,
            OLD.gross_transaction_amount,OLD.transaction_discount_amount,OLD.gross_base_amount,OLD.base_discount_amount) THEN
        RAISE EXCEPTION 'Submitted billing-event discount evidence is immutable';
    END IF;
    IF TG_OP='INSERT' AND NEW.discount_rule_id IS NOT NULL THEN
        SELECT status,code,discount_percentage INTO rule_status,rule_code,rule_percentage
          FROM finance_student_discount_rules WHERE id=NEW.discount_rule_id AND deleted_at IS NULL;
        IF rule_status IS DISTINCT FROM 'ACTIVE' OR rule_code IS DISTINCT FROM NEW.discount_rule_code
            OR rule_percentage IS DISTINCT FROM NEW.discount_percentage THEN
            RAISE EXCEPTION 'Billing discount snapshot must equal one active student discount rule';
        END IF;
    END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_finance_billing_event_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_billing_event_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE account_student_id uuid; account_student_number varchar(40); account_status varchar(30);
DECLARE catalogue_status varchar(20); rule_status varchar(20); rule_catalogue_id uuid;
DECLARE rule_currency varchar(3); rule_transaction_amount numeric(16,2); rule_base_currency varchar(3);
DECLARE rule_exchange_rate_id uuid; rule_base_amount numeric(16,2); rule_from timestamptz; rule_until timestamptz;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Billing-event evidence cannot be deleted'; END IF;
    IF TG_OP='INSERT' THEN
        SELECT student_id,student_number,status INTO account_student_id,account_student_number,account_status
          FROM student_finance_accounts WHERE id=NEW.student_finance_account_id AND deleted_at IS NULL;
        IF account_status IS DISTINCT FROM 'ACTIVE' OR account_student_id IS DISTINCT FROM NEW.student_id
            OR account_student_number IS DISTINCT FROM NEW.student_number THEN
            RAISE EXCEPTION 'Billing event requires the matching active student finance account';
        END IF;
        SELECT status INTO catalogue_status FROM finance_fee_catalogues
          WHERE id=NEW.fee_catalogue_id AND deleted_at IS NULL;
        SELECT status,fee_catalogue_id,transaction_currency_code,transaction_amount,base_currency_code,
               exchange_rate_id,base_amount,effective_from,effective_until
          INTO rule_status,rule_catalogue_id,rule_currency,rule_transaction_amount,rule_base_currency,
               rule_exchange_rate_id,rule_base_amount,rule_from,rule_until
          FROM finance_fee_rules WHERE id=NEW.fee_rule_id AND deleted_at IS NULL;
        IF catalogue_status IS DISTINCT FROM 'ACTIVE' OR rule_status IS DISTINCT FROM 'APPROVED'
            OR rule_catalogue_id IS DISTINCT FROM NEW.fee_catalogue_id
            OR NEW.effective_at<rule_from OR (rule_until IS NOT NULL AND NEW.effective_at>=rule_until) THEN
            RAISE EXCEPTION 'Billing event requires one approved effective price from an active fee definition';
        END IF;
        IF NEW.transaction_currency_code IS DISTINCT FROM rule_currency
            OR NEW.transaction_unit_amount IS DISTINCT FROM rule_transaction_amount
            OR NEW.base_currency_code IS DISTINCT FROM rule_base_currency
            OR NEW.exchange_rate_id IS DISTINCT FROM rule_exchange_rate_id
            OR NEW.base_unit_amount IS DISTINCT FROM rule_base_amount THEN
            RAISE EXCEPTION 'Billing event pricing snapshot must equal the approved fee rule';
        END IF;
    ELSE
        IF OLD.status IN ('REJECTED','INVOICED') THEN RAISE EXCEPTION 'Final billing-event evidence is immutable'; END IF;
        IF ROW(NEW.event_number,NEW.source_service,NEW.source_event_type,NEW.source_event_id,
                NEW.source_aggregate_type,NEW.source_aggregate_id,NEW.source_line_reference,
                NEW.student_finance_account_id,NEW.student_id,NEW.student_number,NEW.fee_catalogue_id,
                NEW.fee_rule_id,NEW.description,NEW.quantity,NEW.transaction_currency_code,
                NEW.transaction_unit_amount,NEW.transaction_amount,NEW.base_currency_code,NEW.exchange_rate_id,
                NEW.base_unit_amount,NEW.base_amount,NEW.effective_at,NEW.prepared_by_user_id,NEW.submitted_at)
            IS DISTINCT FROM ROW(OLD.event_number,OLD.source_service,OLD.source_event_type,OLD.source_event_id,
                OLD.source_aggregate_type,OLD.source_aggregate_id,OLD.source_line_reference,
                OLD.student_finance_account_id,OLD.student_id,OLD.student_number,OLD.fee_catalogue_id,
                OLD.fee_rule_id,OLD.description,OLD.quantity,OLD.transaction_currency_code,
                OLD.transaction_unit_amount,OLD.transaction_amount,OLD.base_currency_code,OLD.exchange_rate_id,
                OLD.base_unit_amount,OLD.base_amount,OLD.effective_at,OLD.prepared_by_user_id,OLD.submitted_at) THEN
            RAISE EXCEPTION 'Submitted billing-event source and pricing evidence is immutable';
        END IF;
        IF NOT ((OLD.status='PENDING_APPROVAL' AND NEW.status IN ('PENDING_APPROVAL','APPROVED','REJECTED'))
            OR (OLD.status='APPROVED' AND NEW.status IN ('APPROVED','INVOICED'))) THEN
            RAISE EXCEPTION 'Invalid billing-event status transition';
        END IF;
        IF NEW.status='INVOICED' AND NOT EXISTS (
            SELECT 1 FROM finance_invoice_lines WHERE billing_event_id=NEW.id) THEN
            RAISE EXCEPTION 'Billing event cannot be marked invoiced without an immutable invoice line';
        END IF;
    END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_finance_billing_event_scope_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_billing_event_scope_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE event_status varchar(30);
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Billing-event scope evidence is immutable'; END IF;
    SELECT status INTO event_status FROM finance_billing_events WHERE id=NEW.billing_event_id;
    IF event_status IS DISTINCT FROM 'PENDING_APPROVAL' THEN
        RAISE EXCEPTION 'Scopes can only be captured with a pending billing event';
    END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_finance_billing_policy_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_billing_policy_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE catalogue_status varchar(20); conflicting_policy uuid;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Billing-policy evidence cannot be deleted'; END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status='RETIRED' THEN RAISE EXCEPTION 'Retired billing-policy evidence is immutable'; END IF;
        IF OLD.status='ACTIVE' AND ROW(NEW.code,NEW.policy_version,NEW.name,NEW.source_event_type,NEW.fee_catalogue_id,
                NEW.line_basis,NEW.quantity_basis,NEW.fixed_quantity,NEW.effective_from,NEW.effective_until,NEW.prepared_by_user_id)
            IS DISTINCT FROM ROW(OLD.code,OLD.policy_version,OLD.name,OLD.source_event_type,OLD.fee_catalogue_id,
                OLD.line_basis,OLD.quantity_basis,OLD.fixed_quantity,OLD.effective_from,OLD.effective_until,OLD.prepared_by_user_id) THEN
            RAISE EXCEPTION 'Active billing-policy definition is immutable';
        END IF;
        IF NOT ((OLD.status='DRAFT' AND NEW.status IN ('DRAFT','ACTIVE'))
            OR (OLD.status='ACTIVE' AND NEW.status IN ('ACTIVE','RETIRED'))) THEN
            RAISE EXCEPTION 'Invalid billing-policy status transition';
        END IF;
    END IF;
    IF NEW.status='ACTIVE' AND (TG_OP='INSERT' OR OLD.status IS DISTINCT FROM 'ACTIVE') THEN
        SELECT status INTO catalogue_status FROM finance_fee_catalogues WHERE id=NEW.fee_catalogue_id AND deleted_at IS NULL;
        IF catalogue_status IS DISTINCT FROM 'ACTIVE' THEN RAISE EXCEPTION 'Billing policy requires an active fee definition'; END IF;
        SELECT id INTO conflicting_policy FROM finance_billing_policies
         WHERE id<>NEW.id AND source_event_type=NEW.source_event_type AND fee_catalogue_id=NEW.fee_catalogue_id
           AND line_basis=NEW.line_basis AND status='ACTIVE' AND deleted_at IS NULL
           AND tstzrange(effective_from,coalesce(effective_until,'infinity'::timestamptz),'[)')
               && tstzrange(NEW.effective_from,coalesce(NEW.effective_until,'infinity'::timestamptz),'[)') LIMIT 1;
        IF conflicting_policy IS NOT NULL THEN RAISE EXCEPTION 'Active billing policy overlaps another policy for the same source, fee, and line basis'; END IF;
    END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_finance_credit_note(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_credit_note() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Credit-note evidence is immutable';END IF;
IF TG_OP='INSERT' THEN IF NEW.status<>'DRAFT' THEN RAISE EXCEPTION 'A credit note must enter the independent posting workflow as a draft';END IF;
ELSE
    IF OLD.status<>'DRAFT' OR NEW.status<>'POSTED' THEN RAISE EXCEPTION 'Only a draft credit note can transition once to posted';END IF;
    IF ROW(NEW.credit_note_number,NEW.invoice_id,NEW.transaction_currency_code,NEW.transaction_amount,NEW.base_currency_code,NEW.base_amount,NEW.credit_note_date,NEW.prepared_by_user_id,NEW.prepared_at,NEW.preparation_reason) IS DISTINCT FROM ROW(OLD.credit_note_number,OLD.invoice_id,OLD.transaction_currency_code,OLD.transaction_amount,OLD.base_currency_code,OLD.base_amount,OLD.credit_note_date,OLD.prepared_by_user_id,OLD.prepared_at,OLD.preparation_reason) THEN RAISE EXCEPTION 'Submitted credit-note amount and source evidence is immutable';END IF;
END IF;RETURN NEW;END $$;


--
-- Name: enforce_finance_credit_note_line(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_credit_note_line() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE note_invoice uuid;note_currency varchar(3);note_status varchar(20);line_invoice uuid;line_amount numeric(16,2);line_base numeric(16,2);credited numeric(16,2);credited_base numeric(16,2);
BEGIN IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Submitted credit-note-line evidence is immutable';END IF;SELECT invoice_id,transaction_currency_code,status INTO note_invoice,note_currency,note_status FROM finance_credit_notes WHERE id=NEW.credit_note_id;SELECT invoice_id,transaction_amount,base_amount INTO line_invoice,line_amount,line_base FROM finance_invoice_lines WHERE id=NEW.invoice_line_id;SELECT coalesce(sum(line.transaction_amount),0),coalesce(sum(line.base_amount),0) INTO credited,credited_base FROM finance_credit_note_lines line JOIN finance_credit_notes note ON note.id=line.credit_note_id WHERE line.invoice_line_id=NEW.invoice_line_id AND note.status='POSTED';IF note_status IS DISTINCT FROM 'DRAFT' OR note_invoice IS DISTINCT FROM line_invoice OR NEW.transaction_amount>line_amount-credited OR NEW.base_amount>line_base-credited_base THEN RAISE EXCEPTION 'Draft credit-note line exceeds the remaining posted amount of its invoice line';END IF;RETURN NEW;END $$;


--
-- Name: enforce_finance_fee_catalogue_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_fee_catalogue_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Finance fee catalogue evidence cannot be deleted'; END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status='RETIRED' THEN RAISE EXCEPTION 'Retired finance fee catalogue evidence is immutable'; END IF;
        IF OLD.status='ACTIVE' AND ROW(NEW.code,NEW.name,NEW.description,NEW.charge_type,NEW.receivable_account_code,
                NEW.revenue_account_code,NEW.tax_code,NEW.base_currency_code,NEW.prepared_by_user_id)
            IS DISTINCT FROM ROW(OLD.code,OLD.name,OLD.description,OLD.charge_type,OLD.receivable_account_code,
                OLD.revenue_account_code,OLD.tax_code,OLD.base_currency_code,OLD.prepared_by_user_id) THEN
            RAISE EXCEPTION 'Active finance fee catalogue definition is immutable';
        END IF;
        IF NOT ((OLD.status='DRAFT' AND NEW.status IN ('DRAFT','ACTIVE'))
            OR (OLD.status='ACTIVE' AND NEW.status IN ('ACTIVE','RETIRED'))) THEN
            RAISE EXCEPTION 'Invalid finance fee catalogue status transition';
        END IF;
    END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_finance_fee_rule_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_fee_rule_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE catalogue_status varchar(20); canonical_scope text; conflicting_rule uuid;
DECLARE rate_currency varchar(3); rate_base varchar(3); rate_status varchar(20); rate_from timestamptz; rate_until timestamptz;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Finance fee rule evidence cannot be deleted'; END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status='RETIRED' THEN RAISE EXCEPTION 'Retired finance fee rule evidence is immutable'; END IF;
        IF OLD.status='APPROVED' AND ROW(NEW.fee_catalogue_id,NEW.rule_version,NEW.transaction_currency_code,
                NEW.transaction_amount,NEW.base_currency_code,NEW.exchange_rate_id,NEW.base_amount,NEW.rating_status,
                NEW.effective_from,NEW.effective_until,NEW.scope_signature,NEW.prepared_by_user_id)
            IS DISTINCT FROM ROW(OLD.fee_catalogue_id,OLD.rule_version,OLD.transaction_currency_code,
                OLD.transaction_amount,OLD.base_currency_code,OLD.exchange_rate_id,OLD.base_amount,OLD.rating_status,
                OLD.effective_from,OLD.effective_until,OLD.scope_signature,OLD.prepared_by_user_id) THEN
            RAISE EXCEPTION 'Approved finance fee rule pricing evidence is immutable';
        END IF;
        IF NOT ((OLD.status IN ('DRAFT','PENDING_RATE') AND NEW.status IN ('DRAFT','PENDING_RATE','APPROVED'))
            OR (OLD.status='APPROVED' AND NEW.status IN ('APPROVED','RETIRED'))) THEN
            RAISE EXCEPTION 'Invalid finance fee rule status transition';
        END IF;
    END IF;
    IF NEW.exchange_rate_id IS NOT NULL THEN
        SELECT source_currency_code,base_currency_code,status,effective_from,effective_to
          INTO rate_currency,rate_base,rate_status,rate_from,rate_until FROM exchange_rates WHERE id=NEW.exchange_rate_id;
        IF rate_currency IS DISTINCT FROM NEW.transaction_currency_code OR rate_base IS DISTINCT FROM 'USD'
            OR rate_status IS DISTINCT FROM 'ACTIVE' OR rate_from>NEW.effective_from
            OR (rate_until IS NOT NULL AND rate_until<=NEW.effective_from) THEN
            RAISE EXCEPTION 'Fee rule exchange rate must be active and effective for its transaction currency and start time';
        END IF;
    END IF;
    IF NEW.status='APPROVED' AND (TG_OP='INSERT' OR OLD.status IS DISTINCT FROM 'APPROVED') THEN
        IF NEW.rating_status<>'RATED' THEN RAISE EXCEPTION 'Unrated fee rules cannot be approved for billing'; END IF;
        SELECT status INTO catalogue_status FROM finance_fee_catalogues WHERE id=NEW.fee_catalogue_id AND deleted_at IS NULL;
        IF catalogue_status IS DISTINCT FROM 'ACTIVE' THEN RAISE EXCEPTION 'Fee rule catalogue must be active before rule approval'; END IF;
        SELECT string_agg(scope_dimension||':'||coalesce(reference_id::text,upper(reference_code),'*'),'|' ORDER BY scope_dimension)
          INTO canonical_scope FROM finance_fee_rule_scopes WHERE fee_rule_id=NEW.id AND deleted_at IS NULL;
        IF canonical_scope IS NULL THEN RAISE EXCEPTION 'Fee rule requires at least one explicit scope before approval'; END IF;
        NEW.scope_signature=canonical_scope;
        SELECT id INTO conflicting_rule FROM finance_fee_rules
         WHERE fee_catalogue_id=NEW.fee_catalogue_id AND status='APPROVED' AND deleted_at IS NULL AND id<>NEW.id
           AND scope_signature=canonical_scope
           AND tstzrange(effective_from,coalesce(effective_until,'infinity'::timestamptz),'[)')
               && tstzrange(NEW.effective_from,coalesce(NEW.effective_until,'infinity'::timestamptz),'[)') LIMIT 1;
        IF conflicting_rule IS NOT NULL THEN RAISE EXCEPTION 'Approved fee rule overlaps another rule for the same catalogue and scope'; END IF;
    END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_finance_fee_rule_scope_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_fee_rule_scope_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE rule_status varchar(20);
BEGIN
    SELECT status INTO rule_status FROM finance_fee_rules WHERE id=COALESCE(NEW.fee_rule_id,OLD.fee_rule_id);
    IF rule_status NOT IN ('DRAFT','PENDING_RATE') THEN
        RAISE EXCEPTION 'Approved finance fee rule scope evidence is immutable';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;


--
-- Name: enforce_finance_fee_structure_attachment_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_fee_structure_attachment_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Fee-structure attachments are legacy evidence; configure student discounts in the standalone discount register';
END $$;


--
-- Name: enforce_finance_fee_structure_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_fee_structure_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE conflicting_structure uuid;
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Finance fee structure evidence cannot be deleted'; END IF;
    IF TG_OP='UPDATE' THEN
        IF OLD.status='RETIRED' THEN RAISE EXCEPTION 'Retired finance fee structure evidence is immutable'; END IF;
        IF OLD.status='ACTIVE' AND ROW(NEW.code,NEW.name,NEW.description,NEW.fee_context,NEW.scope_type,
                NEW.scope_reference_id,NEW.scope_reference_code,NEW.scope_reference_name,
                NEW.programme_level_id,NEW.programme_level_code,NEW.programme_level_name,
                NEW.academic_period_id,NEW.academic_period_code,NEW.academic_period_name,
                NEW.programme_period_number,NEW.applicant_category_code,NEW.transaction_currency_code,
                NEW.effective_from,NEW.effective_until,NEW.prepared_by_user_id)
            IS DISTINCT FROM ROW(OLD.code,OLD.name,OLD.description,OLD.fee_context,OLD.scope_type,
                OLD.scope_reference_id,OLD.scope_reference_code,OLD.scope_reference_name,
                OLD.programme_level_id,OLD.programme_level_code,OLD.programme_level_name,
                OLD.academic_period_id,OLD.academic_period_code,OLD.academic_period_name,
                OLD.programme_period_number,OLD.applicant_category_code,OLD.transaction_currency_code,
                OLD.effective_from,OLD.effective_until,OLD.prepared_by_user_id) THEN
            RAISE EXCEPTION 'Active finance fee structure definition is immutable';
        END IF;
        IF NOT ((OLD.status='DRAFT' AND NEW.status IN ('DRAFT','ACTIVE'))
            OR (OLD.status='ACTIVE' AND NEW.status IN ('ACTIVE','RETIRED'))) THEN
            RAISE EXCEPTION 'Invalid finance fee structure status transition';
        END IF;
    END IF;
    IF NEW.status='ACTIVE' AND (TG_OP='INSERT' OR OLD.status IS DISTINCT FROM 'ACTIVE') THEN
        SELECT id INTO conflicting_structure FROM finance_fee_structures
         WHERE id<>NEW.id AND status='ACTIVE' AND deleted_at IS NULL
           AND fee_context=NEW.fee_context AND scope_type=NEW.scope_type
           AND upper(programme_level_code)=upper(NEW.programme_level_code)
           AND scope_reference_id IS NOT DISTINCT FROM NEW.scope_reference_id
           AND upper(coalesce(scope_reference_code,''))=upper(coalesce(NEW.scope_reference_code,''))
           AND upper(coalesce(applicant_category_code,''))=upper(coalesce(NEW.applicant_category_code,''))
           AND tstzrange(effective_from,coalesce(effective_until,'infinity'::timestamptz),'[)')
               && tstzrange(NEW.effective_from,coalesce(NEW.effective_until,'infinity'::timestamptz),'[)') LIMIT 1;
        IF conflicting_structure IS NOT NULL THEN
            RAISE EXCEPTION 'An active fee structure already covers this programme level, scope, and effective window';
        END IF;
    END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_finance_invoice_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_invoice_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Posted invoice evidence is immutable; use a credit note or reversal'; END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_finance_invoice_line_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_invoice_line_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE event_status varchar(30); event_account_id uuid; invoice_account_id uuid; invoice_currency varchar(3);
DECLARE event_record finance_billing_events%ROWTYPE; catalogue_record finance_fee_catalogues%ROWTYPE;
BEGIN
    IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Posted invoice-line evidence is immutable; use a credit note or reversal'; END IF;
    SELECT * INTO event_record FROM finance_billing_events WHERE id=NEW.billing_event_id;
    SELECT student_finance_account_id,transaction_currency_code INTO invoice_account_id,invoice_currency
      FROM finance_invoices WHERE id=NEW.invoice_id;
    SELECT * INTO catalogue_record FROM finance_fee_catalogues WHERE id=NEW.fee_catalogue_id;
    NEW.gross_transaction_amount=coalesce(NEW.gross_transaction_amount,event_record.gross_transaction_amount);
    NEW.transaction_discount_amount=coalesce(NEW.transaction_discount_amount,event_record.transaction_discount_amount);
    NEW.gross_base_amount=coalesce(NEW.gross_base_amount,event_record.gross_base_amount);
    NEW.base_discount_amount=coalesce(NEW.base_discount_amount,event_record.base_discount_amount);
    NEW.discount_rule_id=coalesce(NEW.discount_rule_id,event_record.discount_rule_id);
    NEW.discount_rule_code=coalesce(NEW.discount_rule_code,event_record.discount_rule_code);
    NEW.discount_percentage=coalesce(NEW.discount_percentage,event_record.discount_percentage);
    IF event_record.status IS DISTINCT FROM 'APPROVED'
        OR event_record.student_finance_account_id IS DISTINCT FROM invoice_account_id
        OR event_record.transaction_currency_code IS DISTINCT FROM invoice_currency THEN
        RAISE EXCEPTION 'Invoice line requires an approved billing event for the same account and currency';
    END IF;
    IF ROW(NEW.fee_catalogue_id,NEW.fee_rule_id,NEW.description,NEW.quantity,NEW.transaction_currency_code,
            NEW.transaction_unit_amount,NEW.gross_transaction_amount,NEW.transaction_discount_amount,
            NEW.transaction_amount,NEW.base_currency_code,NEW.exchange_rate_id,NEW.base_unit_amount,
            NEW.gross_base_amount,NEW.base_discount_amount,NEW.base_amount,NEW.discount_rule_id,
            NEW.discount_rule_code,NEW.discount_percentage)
        IS DISTINCT FROM ROW(event_record.fee_catalogue_id,event_record.fee_rule_id,event_record.description,
            event_record.quantity,event_record.transaction_currency_code,event_record.transaction_unit_amount,
            event_record.gross_transaction_amount,event_record.transaction_discount_amount,event_record.transaction_amount,
            event_record.base_currency_code,event_record.exchange_rate_id,event_record.base_unit_amount,
            event_record.gross_base_amount,event_record.base_discount_amount,event_record.base_amount,
            event_record.discount_rule_id,event_record.discount_rule_code,event_record.discount_percentage) THEN
        RAISE EXCEPTION 'Invoice line must preserve the exact approved billing-event price and discount snapshot';
    END IF;
    IF ROW(NEW.fee_code,NEW.receivable_account_code,NEW.revenue_account_code,NEW.tax_code)
        IS DISTINCT FROM ROW(catalogue_record.code,catalogue_record.receivable_account_code,
            catalogue_record.revenue_account_code,catalogue_record.tax_code) THEN
        RAISE EXCEPTION 'Invoice line must preserve the fee-definition posting accounts';
    END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_finance_student_discount_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_student_discount_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE conflicting_discount_code varchar(50);
BEGIN
    IF TG_OP = 'DELETE' THEN RAISE EXCEPTION 'Student discount evidence cannot be deleted'; END IF;
    IF TG_OP = 'UPDATE' THEN
        IF OLD.status = 'RETIRED' THEN RAISE EXCEPTION 'Retired student discount evidence is immutable'; END IF;
        IF OLD.status = 'ACTIVE' AND ROW(
                NEW.code, NEW.name, NEW.scope_type, NEW.scope_reference_id, NEW.scope_reference_code,
                NEW.scope_reference_name, NEW.scope_depth, NEW.academic_unit_id, NEW.academic_unit_code,
                NEW.academic_unit_name, NEW.programme_id, NEW.programme_code, NEW.programme_name,
                NEW.programme_level_id, NEW.programme_level_code, NEW.programme_level_name,
                NEW.programme_study_level, NEW.target_type, NEW.fee_catalogue_id,
                NEW.discount_percentage, NEW.authority_reference, NEW.effective_from, NEW.effective_until,
                NEW.prepared_by_user_id)
            IS DISTINCT FROM ROW(
                OLD.code, OLD.name, OLD.scope_type, OLD.scope_reference_id, OLD.scope_reference_code,
                OLD.scope_reference_name, OLD.scope_depth, OLD.academic_unit_id, OLD.academic_unit_code,
                OLD.academic_unit_name, OLD.programme_id, OLD.programme_code, OLD.programme_name,
                OLD.programme_level_id, OLD.programme_level_code, OLD.programme_level_name,
                OLD.programme_study_level, OLD.target_type, OLD.fee_catalogue_id,
                OLD.discount_percentage, OLD.authority_reference, OLD.effective_from, OLD.effective_until,
                OLD.prepared_by_user_id) THEN
            RAISE EXCEPTION 'Active student discount definition is immutable';
        END IF;
        IF NOT ((OLD.status = 'DRAFT' AND NEW.status IN ('DRAFT','ACTIVE'))
            OR (OLD.status = 'ACTIVE' AND NEW.status IN ('ACTIVE','RETIRED'))) THEN
            RAISE EXCEPTION 'Invalid student discount status transition';
        END IF;
    END IF;

    IF NEW.status = 'ACTIVE' AND (TG_OP = 'INSERT' OR OLD.status <> 'ACTIVE') THEN
        SELECT existing.code INTO conflicting_discount_code
          FROM finance_student_discount_rules existing
         WHERE existing.id <> NEW.id
           AND existing.deleted_at IS NULL
           AND existing.status = 'ACTIVE'
           AND existing.programme_level_id = NEW.programme_level_id
           AND existing.programme_level_code = NEW.programme_level_code
           AND existing.programme_study_level = NEW.programme_study_level
           AND existing.target_type = NEW.target_type
           AND existing.fee_catalogue_id IS NOT DISTINCT FROM NEW.fee_catalogue_id
           AND existing.effective_from < COALESCE(NEW.effective_until, 'infinity'::timestamptz)
           AND NEW.effective_from < COALESCE(existing.effective_until, 'infinity'::timestamptz)
           AND (CASE WHEN existing.programme_id IS NOT NULL THEN 3
                     WHEN existing.academic_unit_id IS NOT NULL THEN 2 ELSE 1 END)
               = (CASE WHEN NEW.programme_id IS NOT NULL THEN 3
                       WHEN NEW.academic_unit_id IS NOT NULL THEN 2 ELSE 1 END)
           AND (
               (NEW.programme_id IS NOT NULL AND existing.programme_id = NEW.programme_id
                    AND (existing.academic_unit_id IS NULL OR NEW.academic_unit_id IS NULL
                         OR existing.academic_unit_id = NEW.academic_unit_id))
               OR (NEW.programme_id IS NULL AND NEW.academic_unit_id IS NOT NULL
                    AND existing.programme_id IS NULL AND existing.academic_unit_id = NEW.academic_unit_id)
               OR (NEW.programme_id IS NULL AND NEW.academic_unit_id IS NULL
                    AND existing.programme_id IS NULL AND existing.academic_unit_id IS NULL))
         LIMIT 1;
        IF conflicting_discount_code IS NOT NULL THEN
            RAISE EXCEPTION 'Student discount conflicts with active discount % at equal priority', conflicting_discount_code;
        END IF;
    END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_finance_student_discount_programme_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_student_discount_programme_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE rule_status varchar(20);
BEGIN
    SELECT status INTO rule_status FROM finance_student_discount_rules
     WHERE id=COALESCE(NEW.discount_rule_id,OLD.discount_rule_id);
    IF rule_status<>'DRAFT' THEN
        RAISE EXCEPTION 'Discount programme applicability is immutable after activation';
    END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_finance_student_discount_programme_period_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_finance_student_discount_programme_period_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE rule_status varchar(20);
BEGIN
    SELECT discount_rule.status INTO rule_status
      FROM finance_student_discount_rule_programmes programme
      JOIN finance_student_discount_rules discount_rule ON discount_rule.id = programme.discount_rule_id
     WHERE programme.id = COALESCE(NEW.discount_rule_programme_id, OLD.discount_rule_programme_id);
    IF rule_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Discount programme-period applicability is immutable after activation';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_student_allocation_reversal(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_student_allocation_reversal() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE allocator uuid;BEGIN IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Allocation-reversal evidence is immutable';END IF;SELECT allocated_by_user_id INTO allocator FROM student_payment_allocations WHERE id=NEW.allocation_id;IF allocator IS NULL OR allocator=NEW.reversed_by_user_id THEN RAISE EXCEPTION 'Allocation reversal requires an independent Finance operator';END IF;RETURN NEW;END $$;


--
-- Name: enforce_student_finance_account_status_transition(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_student_finance_account_status_transition() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status = 'CLOSED' AND NEW.status <> 'CLOSED' THEN
        RAISE EXCEPTION 'A closed student finance account cannot be reopened by mutation';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: enforce_student_payment_allocation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_student_payment_allocation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE payment_status varchar(30);payment_currency varchar(3);payment_amount numeric(16,2);payment_base numeric(16,2);payment_account uuid;invoice_currency varchar(3);invoice_account uuid;invoice_amount numeric(16,2);invoice_base numeric(16,2);payment_allocated numeric(16,2);payment_base_allocated numeric(16,2);invoice_allocated numeric(16,2);invoice_base_allocated numeric(16,2);invoice_credited numeric(16,2);invoice_base_credited numeric(16,2);expected_payment_base numeric(16,2);expected_invoice_base numeric(16,2);
BEGIN IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Payment-allocation evidence is immutable; use an allocation reversal';END IF;
SELECT reconciliation_status,transaction_currency_code,transaction_amount,base_amount,coalesce(student_finance_account_id,(SELECT student_finance_account_id FROM student_payment_suspense_resolutions WHERE payment_id=NEW.payment_id)) INTO payment_status,payment_currency,payment_amount,payment_base,payment_account FROM student_account_payments WHERE id=NEW.payment_id;
SELECT transaction_currency_code,student_finance_account_id,gross_transaction_amount,gross_base_amount INTO invoice_currency,invoice_account,invoice_amount,invoice_base FROM finance_invoices WHERE id=NEW.invoice_id;
SELECT coalesce(sum(a.transaction_amount),0),coalesce(sum(a.payment_base_amount),0) INTO payment_allocated,payment_base_allocated FROM student_payment_allocations a WHERE a.payment_id=NEW.payment_id AND NOT EXISTS(SELECT 1 FROM student_payment_allocation_reversals r WHERE r.allocation_id=a.id);
SELECT coalesce(sum(a.transaction_amount),0),coalesce(sum(a.invoice_base_amount),0) INTO invoice_allocated,invoice_base_allocated FROM student_payment_allocations a WHERE a.invoice_id=NEW.invoice_id AND NOT EXISTS(SELECT 1 FROM student_payment_allocation_reversals r WHERE r.allocation_id=a.id);
SELECT coalesce(sum(c.transaction_amount),0),coalesce(sum(c.base_amount),0) INTO invoice_credited,invoice_base_credited FROM finance_credit_notes c WHERE c.invoice_id=NEW.invoice_id AND c.status='POSTED';
IF NEW.transaction_amount=payment_amount-payment_allocated THEN expected_payment_base=payment_base-payment_base_allocated;ELSE expected_payment_base=round(NEW.transaction_amount*payment_base/payment_amount,2);END IF;
IF NEW.transaction_amount=invoice_amount-invoice_allocated-invoice_credited THEN expected_invoice_base=invoice_base-invoice_base_allocated-invoice_base_credited;ELSE expected_invoice_base=round(NEW.transaction_amount*invoice_base/invoice_amount,2);END IF;
IF payment_status IS DISTINCT FROM 'RECONCILED' OR payment_account IS NULL OR payment_account IS DISTINCT FROM invoice_account OR payment_currency IS DISTINCT FROM invoice_currency OR NEW.transaction_currency_code IS DISTINCT FROM payment_currency OR NEW.base_currency_code IS DISTINCT FROM 'USD' OR EXISTS(SELECT 1 FROM student_payment_reversals WHERE payment_id=NEW.payment_id) OR NEW.transaction_amount>payment_amount-payment_allocated OR NEW.payment_base_amount>payment_base-payment_base_allocated OR NEW.transaction_amount>invoice_amount-invoice_allocated-invoice_credited OR NEW.invoice_base_amount>invoice_base-invoice_base_allocated-invoice_base_credited OR NEW.payment_base_amount IS DISTINCT FROM expected_payment_base OR NEW.invoice_base_amount IS DISTINCT FROM expected_invoice_base OR NEW.realised_exchange_difference IS DISTINCT FROM NEW.payment_base_amount-NEW.invoice_base_amount THEN RAISE EXCEPTION 'Payment allocation must match available account, currency, payment basis, invoice basis, and realised exchange difference';END IF;RETURN NEW;END $$;


--
-- Name: enforce_student_payment_governance(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_student_payment_governance() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE account_status varchar(30);rate_currency varchar(3);rate_status varchar(20);rate_from timestamptz;rate_until timestamptz;rate_to_base_value numeric(20,10);
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION 'Student payment evidence cannot be deleted'; END IF;
    IF TG_OP='INSERT' THEN
        IF NEW.student_finance_account_id IS NOT NULL THEN SELECT status INTO account_status FROM student_finance_accounts WHERE id=NEW.student_finance_account_id AND deleted_at IS NULL;IF account_status IS DISTINCT FROM 'ACTIVE' THEN RAISE EXCEPTION 'Payment account must be active';END IF;END IF;
    ELSE
        IF OLD.reconciliation_status<>'PENDING' THEN RAISE EXCEPTION 'Reconciled or rejected payment evidence is immutable'; END IF;
        IF ROW(NEW.payment_number,NEW.student_finance_account_id,NEW.payer_name,NEW.provider_code,NEW.provider_transaction_reference,NEW.payment_channel,NEW.transaction_currency_code,NEW.transaction_amount,NEW.base_currency_code,NEW.paid_at,NEW.provider_event_fingerprint,NEW.captured_by_user_id,NEW.captured_at) IS DISTINCT FROM ROW(OLD.payment_number,OLD.student_finance_account_id,OLD.payer_name,OLD.provider_code,OLD.provider_transaction_reference,OLD.payment_channel,OLD.transaction_currency_code,OLD.transaction_amount,OLD.base_currency_code,OLD.paid_at,OLD.provider_event_fingerprint,OLD.captured_by_user_id,OLD.captured_at) THEN RAISE EXCEPTION 'Captured payment provider and transaction evidence is immutable'; END IF;
        IF ROW(NEW.exchange_rate_id,NEW.base_amount,NEW.rating_status,NEW.rating_applied_by_user_id,NEW.rating_applied_at) IS DISTINCT FROM ROW(OLD.exchange_rate_id,OLD.base_amount,OLD.rating_status,OLD.rating_applied_by_user_id,OLD.rating_applied_at)
            AND NOT (OLD.rating_status='UNRATED' AND OLD.exchange_rate_id IS NULL AND OLD.base_amount IS NULL
                AND OLD.rating_applied_by_user_id IS NULL AND OLD.rating_applied_at IS NULL AND NEW.rating_status='RATED'
                AND NEW.exchange_rate_id IS NOT NULL AND NEW.base_amount IS NOT NULL
                AND NEW.rating_applied_by_user_id IS NOT NULL AND NEW.rating_applied_at IS NOT NULL)
        THEN RAISE EXCEPTION 'Payment rating may only transition once from unrated to rated'; END IF;
        IF NEW.reconciliation_status NOT IN ('PENDING','RECONCILED','REJECTED') THEN RAISE EXCEPTION 'Invalid payment reconciliation transition'; END IF;
    END IF;
    IF NEW.exchange_rate_id IS NOT NULL THEN
        SELECT source_currency_code,status,effective_from,effective_to,rate_to_base INTO rate_currency,rate_status,rate_from,rate_until,rate_to_base_value FROM exchange_rates WHERE id=NEW.exchange_rate_id;
        IF rate_currency IS DISTINCT FROM NEW.transaction_currency_code OR rate_status IS DISTINCT FROM 'ACTIVE' OR rate_from>NEW.paid_at OR (rate_until IS NOT NULL AND rate_until<=NEW.paid_at) THEN RAISE EXCEPTION 'Payment exchange rate must be active and effective when paid';END IF;
        IF NEW.base_amount IS DISTINCT FROM round(NEW.transaction_amount*rate_to_base_value,2) THEN RAISE EXCEPTION 'Payment base amount must equal the transaction amount converted using its effective rate';END IF;
    END IF;
    RETURN NEW;
END $$;


--
-- Name: enforce_student_payment_receipt(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_student_payment_receipt() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE payment_record student_account_payments%ROWTYPE;effective_account uuid;
BEGIN IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Issued receipt evidence is immutable';END IF;SELECT * INTO payment_record FROM student_account_payments WHERE id=NEW.payment_id;SELECT coalesce(payment_record.student_finance_account_id,(SELECT student_finance_account_id FROM student_payment_suspense_resolutions WHERE payment_id=NEW.payment_id)) INTO effective_account;IF payment_record.reconciliation_status<>'RECONCILED' OR payment_record.rating_status<>'RATED' OR EXISTS(SELECT 1 FROM student_payment_reversals WHERE payment_id=NEW.payment_id) OR ROW(NEW.student_finance_account_id,NEW.payer_name,NEW.transaction_currency_code,NEW.transaction_amount,NEW.base_currency_code,NEW.base_amount) IS DISTINCT FROM ROW(effective_account,payment_record.payer_name,payment_record.transaction_currency_code,payment_record.transaction_amount,payment_record.base_currency_code,payment_record.base_amount) THEN RAISE EXCEPTION 'Receipt must preserve the exact reconciled payment evidence';END IF;RETURN NEW;END $$;


--
-- Name: enforce_student_payment_reversal(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_student_payment_reversal() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE capturer uuid;reconciler uuid;unreversed_allocations integer;BEGIN IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Payment-reversal evidence is immutable';END IF;SELECT captured_by_user_id,reconciled_by_user_id INTO capturer,reconciler FROM student_account_payments WHERE id=NEW.payment_id AND reconciliation_status='RECONCILED';SELECT count(*) INTO unreversed_allocations FROM student_payment_allocations a WHERE a.payment_id=NEW.payment_id AND NOT EXISTS(SELECT 1 FROM student_payment_allocation_reversals r WHERE r.allocation_id=a.id);IF reconciler IS NULL OR reconciler=NEW.reversed_by_user_id OR capturer=NEW.reversed_by_user_id OR unreversed_allocations>0 THEN RAISE EXCEPTION 'Payment reversal requires an independent operator and reversal of every active allocation';END IF;RETURN NEW;END $$;


--
-- Name: enforce_student_payment_suspense_resolution(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_student_payment_suspense_resolution() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE payment_status varchar(30);original_account uuid;account_status varchar(30);capturer uuid;
BEGIN IF TG_OP<>'INSERT' THEN RAISE EXCEPTION 'Payment suspense-resolution evidence is immutable';END IF;SELECT reconciliation_status,student_finance_account_id,captured_by_user_id INTO payment_status,original_account,capturer FROM student_account_payments WHERE id=NEW.payment_id;SELECT status INTO account_status FROM student_finance_accounts WHERE id=NEW.student_finance_account_id AND deleted_at IS NULL;IF payment_status IS DISTINCT FROM 'RECONCILED' OR original_account IS NOT NULL OR account_status IS DISTINCT FROM 'ACTIVE' OR capturer=NEW.resolved_by_user_id OR EXISTS(SELECT 1 FROM student_payment_reversals WHERE payment_id=NEW.payment_id) THEN RAISE EXCEPTION 'Only an independently resolved, unreversed, reconciled suspense payment can be assigned to an active account';END IF;RETURN NEW;END $$;


--
-- Name: normalize_finance_invoice_discount_totals(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.normalize_finance_invoice_discount_totals() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.transaction_discount_amount=coalesce(NEW.transaction_discount_amount,0);
    NEW.base_discount_amount=coalesce(NEW.base_discount_amount,0);
    NEW.net_transaction_amount=coalesce(NEW.net_transaction_amount,NEW.gross_transaction_amount-NEW.transaction_discount_amount);
    NEW.net_base_amount=coalesce(NEW.net_base_amount,NEW.gross_base_amount-NEW.base_discount_amount);
    RETURN NEW;
END $$;


--
-- Name: prevent_student_finance_account_identity_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_student_finance_account_identity_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF ROW(
            NEW.account_number,
            NEW.student_id,
            NEW.student_number,
            NEW.user_id,
            NEW.source_offer_id,
            NEW.base_currency_code,
            NEW.opened_at
        ) IS DISTINCT FROM ROW(
            OLD.account_number,
            OLD.student_id,
            OLD.student_number,
            OLD.user_id,
            OLD.source_offer_id,
            OLD.base_currency_code,
            OLD.opened_at
        ) THEN
        RAISE EXCEPTION 'Student finance account identity and USD base currency are immutable';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: validate_finance_credit_note(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_finance_credit_note() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE line_count integer;transaction_total numeric(16,2);base_total numeric(16,2);invoice_currency varchar(3);BEGIN SELECT count(*),coalesce(sum(transaction_amount),0),coalesce(sum(base_amount),0) INTO line_count,transaction_total,base_total FROM finance_credit_note_lines WHERE credit_note_id=NEW.id;SELECT transaction_currency_code INTO invoice_currency FROM finance_invoices WHERE id=NEW.invoice_id;IF line_count=0 OR transaction_total IS DISTINCT FROM NEW.transaction_amount OR base_total IS DISTINCT FROM NEW.base_amount OR invoice_currency IS DISTINCT FROM NEW.transaction_currency_code THEN RAISE EXCEPTION 'Credit-note lines, totals, and invoice currency must reconcile exactly';END IF;IF NEW.status='POSTED' AND EXISTS(SELECT 1 FROM finance_invoice_lines invoice_line WHERE invoice_line.invoice_id=NEW.invoice_id AND (SELECT coalesce(sum(note_line.transaction_amount),0) FROM finance_credit_note_lines note_line JOIN finance_credit_notes note ON note.id=note_line.credit_note_id WHERE note_line.invoice_line_id=invoice_line.id AND note.status='POSTED')>invoice_line.transaction_amount) THEN RAISE EXCEPTION 'Posted credit notes exceed an invoice line balance';END IF;RETURN NULL;END $$;


--
-- Name: validate_finance_posted_invoice(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_finance_posted_invoice() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE line_count integer; gross_transaction_total numeric(16,2); transaction_discount_total numeric(16,2);
DECLARE net_transaction_total numeric(16,2); gross_base_total numeric(16,2); base_discount_total numeric(16,2);
DECLARE net_base_total numeric(16,2); uninvoiced_count integer;
BEGIN
    SELECT count(*),coalesce(sum(line.gross_transaction_amount),0),coalesce(sum(line.transaction_discount_amount),0),
           coalesce(sum(line.transaction_amount),0),coalesce(sum(line.gross_base_amount),0),
           coalesce(sum(line.base_discount_amount),0),coalesce(sum(line.base_amount),0),
           count(*) FILTER (WHERE event.status<>'INVOICED')
      INTO line_count,gross_transaction_total,transaction_discount_total,net_transaction_total,
           gross_base_total,base_discount_total,net_base_total,uninvoiced_count
      FROM finance_invoice_lines line JOIN finance_billing_events event ON event.id=line.billing_event_id
     WHERE line.invoice_id=NEW.id;
    IF line_count=0 OR gross_transaction_total IS DISTINCT FROM NEW.gross_transaction_amount
        OR transaction_discount_total IS DISTINCT FROM NEW.transaction_discount_amount
        OR net_transaction_total IS DISTINCT FROM NEW.net_transaction_amount
        OR gross_base_total IS DISTINCT FROM NEW.gross_base_amount
        OR base_discount_total IS DISTINCT FROM NEW.base_discount_amount
        OR net_base_total IS DISTINCT FROM NEW.net_base_amount OR uninvoiced_count<>0 THEN
        RAISE EXCEPTION 'Posted invoice totals and billing-event discount evidence must reconcile exactly';
    END IF;
    RETURN NULL;
END $$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: application_payment_provider_attempts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_payment_provider_attempts (
    id uuid NOT NULL,
    payment_reference_id uuid CONSTRAINT application_payment_provider_atte_payment_reference_id_not_null NOT NULL,
    source_application_id uuid CONSTRAINT application_payment_provider_att_source_application_id_not_null NOT NULL,
    provider_code character varying(50) NOT NULL,
    merchant_trace character varying(64) NOT NULL,
    merchant_reference character varying(80) CONSTRAINT application_payment_provider_attemp_merchant_reference_not_null NOT NULL,
    return_nonce_hash character varying(64) CONSTRAINT application_payment_provider_attempt_return_nonce_hash_not_null NOT NULL,
    transaction_currency_code character varying(3) CONSTRAINT application_payment_provider_transaction_currency_code_not_null NOT NULL,
    transaction_amount numeric(12,2) CONSTRAINT application_payment_provider_attemp_transaction_amount_not_null NOT NULL,
    gateway_url character varying(500) NOT NULL,
    status character varying(30) NOT NULL,
    provider_transaction_reference character varying(160),
    provider_status_code character varying(30),
    provider_result_description character varying(500),
    expires_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_application_payment_provider_attempts_amount CHECK ((transaction_amount > (0)::numeric)),
    CONSTRAINT ck_application_payment_provider_attempts_currency CHECK (((transaction_currency_code)::text = upper((transaction_currency_code)::text))),
    CONSTRAINT ck_application_payment_provider_attempts_status CHECK (((status)::text = ANY ((ARRAY['INITIATED'::character varying, 'PENDING_CONFIRMATION'::character varying, 'CONFIRMED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'EXPIRED'::character varying])::text[])))
);


--
-- Name: application_payment_provider_attempts_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_payment_provider_attempts_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    payment_reference_id uuid,
    source_application_id uuid,
    provider_code character varying(50),
    merchant_trace character varying(64),
    merchant_reference character varying(80),
    return_nonce_hash character varying(64),
    transaction_currency_code character varying(3),
    transaction_amount numeric(12,2),
    gateway_url character varying(500),
    status character varying(30),
    provider_transaction_reference character varying(160),
    provider_status_code character varying(30),
    provider_result_description character varying(500),
    expires_at timestamp with time zone,
    completed_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: application_payment_reference_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.application_payment_reference_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: application_payment_references; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_payment_references (
    id uuid NOT NULL,
    source_application_id uuid NOT NULL,
    applicant_user_id uuid NOT NULL,
    applicant_keycloak_user_id uuid CONSTRAINT application_payment_referen_applicant_keycloak_user_id_not_null NOT NULL,
    reference character varying(80) NOT NULL,
    amount_due numeric(12,2) NOT NULL,
    currency_code character varying(3) NOT NULL,
    base_currency_code character varying(3) DEFAULT 'USD'::character varying NOT NULL,
    exchange_rate_id uuid,
    base_amount_due numeric(12,2),
    rating_status character varying(20) NOT NULL,
    status character varying(30) NOT NULL,
    required_for_submission boolean NOT NULL,
    expires_at timestamp with time zone,
    paid_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    state_sequence bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_application_payment_references_amount CHECK ((amount_due > (0)::numeric)),
    CONSTRAINT ck_application_payment_references_base_amount CHECK (((((currency_code)::text = 'USD'::text) AND (exchange_rate_id IS NULL) AND (base_amount_due = amount_due) AND ((rating_status)::text = 'RATED'::text)) OR (((currency_code)::text <> 'USD'::text) AND (exchange_rate_id IS NULL) AND (base_amount_due IS NULL) AND ((rating_status)::text = 'UNRATED'::text)) OR (((currency_code)::text <> 'USD'::text) AND (exchange_rate_id IS NOT NULL) AND (base_amount_due IS NOT NULL) AND ((rating_status)::text = 'RATED'::text)))),
    CONSTRAINT ck_application_payment_references_currency CHECK ((((currency_code)::text = upper((currency_code)::text)) AND ((base_currency_code)::text = 'USD'::text))),
    CONSTRAINT ck_application_payment_references_paid_at CHECK (((((status)::text = 'PAID'::text) AND (paid_at IS NOT NULL)) OR ((status)::text <> 'PAID'::text))),
    CONSTRAINT ck_application_payment_references_rating_status CHECK (((rating_status)::text = ANY ((ARRAY['RATED'::character varying, 'UNRATED'::character varying])::text[]))),
    CONSTRAINT ck_application_payment_references_state_sequence CHECK ((state_sequence >= 0)),
    CONSTRAINT ck_application_payment_references_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PAID'::character varying, 'EXPIRED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: application_payment_references_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_payment_references_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    source_application_id uuid,
    applicant_user_id uuid,
    applicant_keycloak_user_id uuid,
    reference character varying(80),
    amount_due numeric(12,2),
    currency_code character varying(3),
    base_currency_code character varying(3),
    exchange_rate_id uuid,
    base_amount_due numeric(12,2),
    rating_status character varying(20),
    status character varying(30),
    required_for_submission boolean,
    expires_at timestamp with time zone,
    paid_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    state_sequence bigint
);


--
-- Name: application_payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_payments (
    id uuid NOT NULL,
    payment_reference_id uuid NOT NULL,
    source_application_id uuid NOT NULL,
    provider_code character varying(50) NOT NULL,
    provider_transaction_reference character varying(160) NOT NULL,
    amount numeric(12,2) NOT NULL,
    currency_code character varying(3) NOT NULL,
    base_currency_code character varying(3) DEFAULT 'USD'::character varying NOT NULL,
    exchange_rate_id uuid,
    base_amount numeric(12,2),
    rating_status character varying(20) NOT NULL,
    paid_at timestamp with time zone NOT NULL,
    confirmed_at timestamp with time zone,
    status character varying(30) NOT NULL,
    provider_event_fingerprint character varying(128) NOT NULL,
    failure_reason character varying(500),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_application_payments_amount CHECK ((amount > (0)::numeric)),
    CONSTRAINT ck_application_payments_base_amount CHECK (((((currency_code)::text = 'USD'::text) AND (exchange_rate_id IS NULL) AND (base_amount = amount) AND ((rating_status)::text = 'RATED'::text)) OR (((currency_code)::text <> 'USD'::text) AND (exchange_rate_id IS NULL) AND (base_amount IS NULL) AND ((rating_status)::text = 'UNRATED'::text)) OR (((currency_code)::text <> 'USD'::text) AND (exchange_rate_id IS NOT NULL) AND (base_amount IS NOT NULL) AND ((rating_status)::text = 'RATED'::text)))),
    CONSTRAINT ck_application_payments_currency CHECK ((((currency_code)::text = upper((currency_code)::text)) AND ((base_currency_code)::text = 'USD'::text))),
    CONSTRAINT ck_application_payments_rating_status CHECK (((rating_status)::text = ANY ((ARRAY['RATED'::character varying, 'UNRATED'::character varying])::text[]))),
    CONSTRAINT ck_application_payments_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'CONFIRMED'::character varying, 'FAILED'::character varying, 'REVERSED'::character varying])::text[])))
);


--
-- Name: application_payments_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.application_payments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    payment_reference_id uuid,
    source_application_id uuid,
    provider_code character varying(50),
    provider_transaction_reference character varying(160),
    amount numeric(12,2),
    currency_code character varying(3),
    base_currency_code character varying(3),
    exchange_rate_id uuid,
    base_amount numeric(12,2),
    rating_status character varying(20),
    paid_at timestamp with time zone,
    confirmed_at timestamp with time zone,
    status character varying(30),
    provider_event_fingerprint character varying(128),
    failure_reason character varying(500),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: exchange_rates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exchange_rates (
    id uuid NOT NULL,
    source_currency_code character varying(3) NOT NULL,
    base_currency_code character varying(3) DEFAULT 'USD'::character varying NOT NULL,
    rate_to_base numeric(20,8) NOT NULL,
    effective_from timestamp with time zone NOT NULL,
    effective_to timestamp with time zone,
    source_name character varying(120) NOT NULL,
    source_reference character varying(160),
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
    prepared_by_user_id uuid NOT NULL,
    approval_reason character varying(1000),
    retired_by_user_id uuid,
    retired_at timestamp with time zone,
    retirement_reason character varying(1000),
    CONSTRAINT ck_exchange_rate_actor_separation CHECK (((approved_by_user_id IS NULL) OR (approved_by_user_id <> prepared_by_user_id))),
    CONSTRAINT ck_exchange_rate_governed_workflow CHECK (((((status)::text = 'DRAFT'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL) AND (retired_by_user_id IS NULL) AND (retired_at IS NULL) AND (retirement_reason IS NULL)) OR (((status)::text = 'ACTIVE'::text) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (retired_by_user_id IS NULL) AND (retired_at IS NULL) AND (retirement_reason IS NULL)) OR (((status)::text = 'RETIRED'::text) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (retired_by_user_id IS NOT NULL) AND (retired_at IS NOT NULL) AND (length(TRIM(BOTH FROM retirement_reason)) > 0)))),
    CONSTRAINT ck_exchange_rates_approval CHECK (((((status)::text = 'ACTIVE'::text) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL)) OR ((status)::text <> 'ACTIVE'::text))),
    CONSTRAINT ck_exchange_rates_currency_codes CHECK ((((source_currency_code)::text = upper((source_currency_code)::text)) AND ((base_currency_code)::text = 'USD'::text) AND ((source_currency_code)::text <> (base_currency_code)::text))),
    CONSTRAINT ck_exchange_rates_effective_range CHECK (((effective_to IS NULL) OR (effective_to > effective_from))),
    CONSTRAINT ck_exchange_rates_positive_rate CHECK ((rate_to_base > (0)::numeric)),
    CONSTRAINT ck_exchange_rates_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'RETIRED'::character varying])::text[])))
);


--
-- Name: exchange_rates_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.exchange_rates_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    source_currency_code character varying(3),
    base_currency_code character varying(3),
    rate_to_base numeric(20,8),
    effective_from timestamp with time zone,
    effective_to timestamp with time zone,
    source_name character varying(120),
    source_reference character varying(160),
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
    prepared_by_user_id uuid,
    approval_reason character varying(1000),
    retired_by_user_id uuid,
    retired_at timestamp with time zone,
    retirement_reason character varying(1000)
);


--
-- Name: finance_billing_event_number_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finance_billing_event_number_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finance_billing_event_scopes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_billing_event_scopes (
    id uuid NOT NULL,
    billing_event_id uuid NOT NULL,
    scope_dimension character varying(40) NOT NULL,
    reference_id uuid,
    reference_code character varying(80),
    reference_name character varying(200),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_finance_billing_event_scope_dimension CHECK (((scope_dimension)::text = ANY ((ARRAY['GLOBAL'::character varying, 'INSTITUTION'::character varying, 'ACADEMIC_UNIT'::character varying, 'ACADEMIC_PERIOD'::character varying, 'PROGRAMME_PERIOD'::character varying, 'APPLICATION_TYPE'::character varying, 'PROGRAMME_LEVEL'::character varying, 'PROGRAMME_TYPE'::character varying, 'APPLICANT_CATEGORY'::character varying, 'PROGRAMME'::character varying, 'MODULE'::character varying, 'ACCOMMODATION_TYPE'::character varying, 'DINING_PLAN'::character varying, 'GRADUATION'::character varying])::text[]))),
    CONSTRAINT ck_finance_billing_event_scope_reference CHECK (((((scope_dimension)::text = 'GLOBAL'::text) AND (reference_id IS NULL) AND (reference_code IS NULL) AND (reference_name IS NULL)) OR (((scope_dimension)::text <> 'GLOBAL'::text) AND ((reference_id IS NOT NULL) OR (length(TRIM(BOTH FROM reference_code)) > 0)) AND (length(TRIM(BOTH FROM reference_name)) > 0))))
);


--
-- Name: finance_billing_event_scopes_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_billing_event_scopes_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    billing_event_id uuid,
    scope_dimension character varying(40),
    reference_id uuid,
    reference_code character varying(80),
    reference_name character varying(200),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: finance_billing_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_billing_events (
    id uuid NOT NULL,
    event_number character varying(40) NOT NULL,
    source_service character varying(80) NOT NULL,
    source_event_type character varying(160) NOT NULL,
    source_event_id uuid NOT NULL,
    source_aggregate_type character varying(80) NOT NULL,
    source_aggregate_id uuid NOT NULL,
    source_line_reference character varying(160) NOT NULL,
    student_finance_account_id uuid NOT NULL,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    fee_catalogue_id uuid NOT NULL,
    fee_rule_id uuid NOT NULL,
    description character varying(500) NOT NULL,
    quantity numeric(12,4) NOT NULL,
    transaction_currency_code character varying(3) NOT NULL,
    transaction_unit_amount numeric(16,2) NOT NULL,
    transaction_amount numeric(16,2) NOT NULL,
    base_currency_code character varying(3) DEFAULT 'USD'::character varying NOT NULL,
    exchange_rate_id uuid,
    base_unit_amount numeric(16,2) NOT NULL,
    base_amount numeric(16,2) NOT NULL,
    effective_at timestamp with time zone NOT NULL,
    status character varying(30) NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    submitted_at timestamp with time zone NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    rejected_by_user_id uuid,
    rejected_at timestamp with time zone,
    rejection_reason character varying(1000),
    invoiced_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    discount_rule_id uuid,
    discount_rule_code character varying(50),
    discount_percentage numeric(7,4),
    gross_transaction_amount numeric(16,2) NOT NULL,
    transaction_discount_amount numeric(16,2) DEFAULT 0 NOT NULL,
    gross_base_amount numeric(16,2) NOT NULL,
    base_discount_amount numeric(16,2) DEFAULT 0 NOT NULL,
    CONSTRAINT ck_finance_billing_event_actor_separation CHECK ((((approved_by_user_id IS NULL) OR (approved_by_user_id <> prepared_by_user_id)) AND ((rejected_by_user_id IS NULL) OR (rejected_by_user_id <> prepared_by_user_id)))),
    CONSTRAINT ck_finance_billing_event_amounts CHECK (((quantity > (0)::numeric) AND (transaction_unit_amount > (0)::numeric) AND (gross_transaction_amount = round((transaction_unit_amount * quantity), 2)) AND (transaction_discount_amount >= (0)::numeric) AND (transaction_amount = (gross_transaction_amount - transaction_discount_amount)) AND (base_unit_amount > (0)::numeric) AND (gross_base_amount = round((base_unit_amount * quantity), 2)) AND (base_discount_amount >= (0)::numeric) AND (base_amount = (gross_base_amount - base_discount_amount)) AND (transaction_amount > (0)::numeric) AND (base_amount > (0)::numeric))),
    CONSTRAINT ck_finance_billing_event_currency CHECK ((((transaction_currency_code)::text = upper((transaction_currency_code)::text)) AND ((transaction_currency_code)::text ~ '^[A-Z]{3}$'::text) AND ((base_currency_code)::text = 'USD'::text))),
    CONSTRAINT ck_finance_billing_event_description CHECK ((length(TRIM(BOTH FROM description)) > 0)),
    CONSTRAINT ck_finance_billing_event_discount CHECK ((((discount_rule_id IS NULL) AND (discount_rule_code IS NULL) AND (discount_percentage IS NULL) AND (transaction_discount_amount = (0)::numeric) AND (base_discount_amount = (0)::numeric)) OR ((discount_rule_id IS NOT NULL) AND (length(TRIM(BOTH FROM discount_rule_code)) > 0) AND (discount_percentage > (0)::numeric) AND (discount_percentage < (100)::numeric) AND (transaction_discount_amount = round(((gross_transaction_amount * discount_percentage) / (100)::numeric), 2)) AND (base_discount_amount = round(((gross_base_amount * discount_percentage) / (100)::numeric), 2))))),
    CONSTRAINT ck_finance_billing_event_quantity CHECK ((quantity > (0)::numeric)),
    CONSTRAINT ck_finance_billing_event_rating CHECK (((((transaction_currency_code)::text = 'USD'::text) AND (exchange_rate_id IS NULL) AND (base_unit_amount = transaction_unit_amount) AND (base_amount = transaction_amount)) OR (((transaction_currency_code)::text <> 'USD'::text) AND (exchange_rate_id IS NOT NULL) AND (base_unit_amount > (0)::numeric) AND (base_amount > (0)::numeric)))),
    CONSTRAINT ck_finance_billing_event_source CHECK (((length(TRIM(BOTH FROM source_service)) > 0) AND (length(TRIM(BOTH FROM source_event_type)) > 0) AND (length(TRIM(BOTH FROM source_aggregate_type)) > 0) AND (length(TRIM(BOTH FROM source_line_reference)) > 0))),
    CONSTRAINT ck_finance_billing_event_status CHECK (((status)::text = ANY ((ARRAY['PENDING_APPROVAL'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'INVOICED'::character varying])::text[]))),
    CONSTRAINT ck_finance_billing_event_workflow CHECK (((((status)::text = 'PENDING_APPROVAL'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL) AND (rejected_by_user_id IS NULL) AND (rejected_at IS NULL) AND (rejection_reason IS NULL) AND (invoiced_at IS NULL)) OR (((status)::text = 'APPROVED'::text) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (rejected_by_user_id IS NULL) AND (rejected_at IS NULL) AND (rejection_reason IS NULL) AND (invoiced_at IS NULL)) OR (((status)::text = 'REJECTED'::text) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL) AND (rejected_by_user_id IS NOT NULL) AND (rejected_at IS NOT NULL) AND (length(TRIM(BOTH FROM rejection_reason)) > 0) AND (invoiced_at IS NULL)) OR (((status)::text = 'INVOICED'::text) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (rejected_by_user_id IS NULL) AND (rejected_at IS NULL) AND (rejection_reason IS NULL) AND (invoiced_at IS NOT NULL))))
);


--
-- Name: finance_billing_events_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_billing_events_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    event_number character varying(40),
    source_service character varying(80),
    source_event_type character varying(160),
    source_event_id uuid,
    source_aggregate_type character varying(80),
    source_aggregate_id uuid,
    source_line_reference character varying(160),
    student_finance_account_id uuid,
    student_id uuid,
    student_number character varying(40),
    fee_catalogue_id uuid,
    fee_rule_id uuid,
    description character varying(500),
    quantity numeric(12,4),
    transaction_currency_code character varying(3),
    transaction_unit_amount numeric(16,2),
    transaction_amount numeric(16,2),
    base_currency_code character varying(3),
    exchange_rate_id uuid,
    base_unit_amount numeric(16,2),
    base_amount numeric(16,2),
    effective_at timestamp with time zone,
    status character varying(30),
    prepared_by_user_id uuid,
    submitted_at timestamp with time zone,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    rejected_by_user_id uuid,
    rejected_at timestamp with time zone,
    rejection_reason character varying(1000),
    invoiced_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    discount_rule_id uuid,
    discount_rule_code character varying(50),
    discount_percentage numeric(7,4),
    gross_transaction_amount numeric(16,2),
    transaction_discount_amount numeric(16,2),
    gross_base_amount numeric(16,2),
    base_discount_amount numeric(16,2)
);


--
-- Name: finance_billing_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_billing_policies (
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    policy_version integer NOT NULL,
    name character varying(160) NOT NULL,
    source_event_type character varying(160) NOT NULL,
    fee_catalogue_id uuid NOT NULL,
    line_basis character varying(40) NOT NULL,
    quantity_basis character varying(40) NOT NULL,
    fixed_quantity numeric(12,4),
    effective_from timestamp with time zone NOT NULL,
    effective_until timestamp with time zone,
    status character varying(20) NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    activated_by_user_id uuid,
    activated_at timestamp with time zone,
    activation_reason character varying(1000),
    retired_by_user_id uuid,
    retired_at timestamp with time zone,
    retirement_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_finance_billing_policy_actor_separation CHECK (((activated_by_user_id IS NULL) OR (activated_by_user_id <> prepared_by_user_id))),
    CONSTRAINT ck_finance_billing_policy_effectivity CHECK (((effective_until IS NULL) OR (effective_until > effective_from))),
    CONSTRAINT ck_finance_billing_policy_line_basis CHECK (((line_basis)::text = ANY ((ARRAY['REGISTRATION'::character varying, 'REGISTERED_MODULE'::character varying])::text[]))),
    CONSTRAINT ck_finance_billing_policy_quantity CHECK (((((quantity_basis)::text = 'FIXED'::text) AND (fixed_quantity IS NOT NULL) AND (fixed_quantity > (0)::numeric)) OR (((quantity_basis)::text = 'MODULE_CREDIT_VALUE'::text) AND (fixed_quantity IS NULL) AND ((line_basis)::text = 'REGISTERED_MODULE'::text)))),
    CONSTRAINT ck_finance_billing_policy_quantity_basis CHECK (((quantity_basis)::text = ANY ((ARRAY['FIXED'::character varying, 'MODULE_CREDIT_VALUE'::character varying])::text[]))),
    CONSTRAINT ck_finance_billing_policy_source CHECK ((length(TRIM(BOTH FROM source_event_type)) > 0)),
    CONSTRAINT ck_finance_billing_policy_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'RETIRED'::character varying])::text[]))),
    CONSTRAINT ck_finance_billing_policy_version CHECK ((policy_version > 0)),
    CONSTRAINT ck_finance_billing_policy_workflow CHECK (((((status)::text = 'DRAFT'::text) AND (activated_by_user_id IS NULL) AND (activated_at IS NULL) AND (activation_reason IS NULL) AND (retired_by_user_id IS NULL) AND (retired_at IS NULL) AND (retirement_reason IS NULL)) OR (((status)::text = 'ACTIVE'::text) AND (activated_by_user_id IS NOT NULL) AND (activated_at IS NOT NULL) AND (length(TRIM(BOTH FROM activation_reason)) > 0) AND (retired_by_user_id IS NULL) AND (retired_at IS NULL) AND (retirement_reason IS NULL)) OR (((status)::text = 'RETIRED'::text) AND (activated_by_user_id IS NOT NULL) AND (activated_at IS NOT NULL) AND (length(TRIM(BOTH FROM activation_reason)) > 0) AND (retired_by_user_id IS NOT NULL) AND (retired_at IS NOT NULL) AND (length(TRIM(BOTH FROM retirement_reason)) > 0))))
);


--
-- Name: finance_billing_policies_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_billing_policies_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(50),
    policy_version integer,
    name character varying(160),
    source_event_type character varying(160),
    fee_catalogue_id uuid,
    line_basis character varying(40),
    quantity_basis character varying(40),
    fixed_quantity numeric(12,4),
    effective_from timestamp with time zone,
    effective_until timestamp with time zone,
    status character varying(20),
    prepared_by_user_id uuid,
    activated_by_user_id uuid,
    activated_at timestamp with time zone,
    activation_reason character varying(1000),
    retired_by_user_id uuid,
    retired_at timestamp with time zone,
    retirement_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: finance_credit_note_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_credit_note_lines (
    id uuid NOT NULL,
    credit_note_id uuid NOT NULL,
    line_number integer NOT NULL,
    invoice_line_id uuid NOT NULL,
    transaction_amount numeric(16,2) NOT NULL,
    base_amount numeric(16,2) NOT NULL,
    reason character varying(500) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_finance_credit_note_line_amount CHECK (((line_number > 0) AND (transaction_amount > (0)::numeric) AND (base_amount > (0)::numeric))),
    CONSTRAINT ck_finance_credit_note_line_reason CHECK ((length(TRIM(BOTH FROM reason)) > 0))
);


--
-- Name: finance_credit_note_lines_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_credit_note_lines_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    credit_note_id uuid,
    line_number integer,
    invoice_line_id uuid,
    transaction_amount numeric(16,2),
    base_amount numeric(16,2),
    reason character varying(500),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: finance_credit_note_number_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finance_credit_note_number_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finance_credit_notes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_credit_notes (
    id uuid NOT NULL,
    credit_note_number character varying(40) NOT NULL,
    invoice_id uuid NOT NULL,
    transaction_currency_code character varying(3) NOT NULL,
    transaction_amount numeric(16,2) NOT NULL,
    base_currency_code character varying(3) NOT NULL,
    base_amount numeric(16,2) NOT NULL,
    credit_note_date date NOT NULL,
    status character varying(20) NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    prepared_at timestamp with time zone NOT NULL,
    preparation_reason character varying(1000) NOT NULL,
    posted_by_user_id uuid,
    posted_at timestamp with time zone,
    posting_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_finance_credit_note_amount CHECK (((transaction_amount > (0)::numeric) AND (base_amount > (0)::numeric) AND ((base_currency_code)::text = 'USD'::text))),
    CONSTRAINT ck_finance_credit_note_reason CHECK ((length(TRIM(BOTH FROM preparation_reason)) > 0)),
    CONSTRAINT ck_finance_credit_note_status CHECK (((((status)::text = 'DRAFT'::text) AND (posted_by_user_id IS NULL) AND (posted_at IS NULL) AND (posting_reason IS NULL)) OR (((status)::text = 'POSTED'::text) AND (posted_by_user_id IS NOT NULL) AND (posted_at IS NOT NULL) AND (length(TRIM(BOTH FROM posting_reason)) > 0) AND (posted_by_user_id <> prepared_by_user_id))))
);


--
-- Name: finance_credit_notes_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_credit_notes_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    credit_note_number character varying(40),
    invoice_id uuid,
    transaction_currency_code character varying(3),
    transaction_amount numeric(16,2),
    base_currency_code character varying(3),
    base_amount numeric(16,2),
    credit_note_date date,
    status character varying(20),
    prepared_by_user_id uuid,
    prepared_at timestamp with time zone,
    preparation_reason character varying(1000),
    posted_by_user_id uuid,
    posted_at timestamp with time zone,
    posting_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: finance_fee_catalogues; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_fee_catalogues (
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(160) NOT NULL,
    description character varying(1000),
    charge_type character varying(30) NOT NULL,
    receivable_account_code character varying(50) NOT NULL,
    revenue_account_code character varying(50) NOT NULL,
    tax_code character varying(30),
    base_currency_code character varying(3) DEFAULT 'USD'::character varying NOT NULL,
    status character varying(20) NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    activated_by_user_id uuid,
    activated_at timestamp with time zone,
    activation_reason character varying(1000),
    retired_by_user_id uuid,
    retired_at timestamp with time zone,
    retirement_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_finance_fee_catalogue_activation CHECK (((((status)::text = 'DRAFT'::text) AND (activated_by_user_id IS NULL) AND (activated_at IS NULL) AND (activation_reason IS NULL) AND (retired_by_user_id IS NULL) AND (retired_at IS NULL) AND (retirement_reason IS NULL)) OR (((status)::text = 'ACTIVE'::text) AND (activated_by_user_id IS NOT NULL) AND (activated_at IS NOT NULL) AND (length(TRIM(BOTH FROM activation_reason)) > 0) AND (retired_by_user_id IS NULL) AND (retired_at IS NULL) AND (retirement_reason IS NULL)) OR (((status)::text = 'RETIRED'::text) AND (activated_by_user_id IS NOT NULL) AND (activated_at IS NOT NULL) AND (length(TRIM(BOTH FROM activation_reason)) > 0) AND (retired_by_user_id IS NOT NULL) AND (retired_at IS NOT NULL) AND (length(TRIM(BOTH FROM retirement_reason)) > 0)))),
    CONSTRAINT ck_finance_fee_catalogue_actor_separation CHECK (((activated_by_user_id IS NULL) OR (activated_by_user_id <> prepared_by_user_id))),
    CONSTRAINT ck_finance_fee_catalogue_charge_type CHECK (((charge_type)::text = ANY ((ARRAY['APPLICATION'::character varying, 'PROGRAMME'::character varying, 'MODULE'::character varying, 'ACCOMMODATION'::character varying, 'DINING'::character varying, 'GRADUATION'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT ck_finance_fee_catalogue_currency CHECK (((base_currency_code)::text = 'USD'::text)),
    CONSTRAINT ck_finance_fee_catalogue_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'RETIRED'::character varying])::text[])))
);


--
-- Name: finance_fee_catalogues_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_fee_catalogues_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(50),
    name character varying(160),
    description character varying(1000),
    charge_type character varying(30),
    receivable_account_code character varying(50),
    revenue_account_code character varying(50),
    tax_code character varying(30),
    base_currency_code character varying(3),
    status character varying(20),
    prepared_by_user_id uuid,
    activated_by_user_id uuid,
    activated_at timestamp with time zone,
    activation_reason character varying(1000),
    retired_by_user_id uuid,
    retired_at timestamp with time zone,
    retirement_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: finance_fee_rule_scopes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_fee_rule_scopes (
    id uuid NOT NULL,
    fee_rule_id uuid NOT NULL,
    scope_dimension character varying(40) NOT NULL,
    reference_id uuid,
    reference_code character varying(80),
    reference_name character varying(200),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_finance_fee_rule_scope_dimension CHECK (((scope_dimension)::text = ANY ((ARRAY['GLOBAL'::character varying, 'INSTITUTION'::character varying, 'ACADEMIC_UNIT'::character varying, 'ACADEMIC_PERIOD'::character varying, 'PROGRAMME_PERIOD'::character varying, 'APPLICATION_TYPE'::character varying, 'PROGRAMME_LEVEL'::character varying, 'PROGRAMME_TYPE'::character varying, 'APPLICANT_CATEGORY'::character varying, 'PROGRAMME'::character varying, 'MODULE'::character varying, 'ACCOMMODATION_TYPE'::character varying, 'DINING_PLAN'::character varying, 'GRADUATION'::character varying])::text[]))),
    CONSTRAINT ck_finance_fee_rule_scope_reference CHECK (((((scope_dimension)::text = 'GLOBAL'::text) AND (reference_id IS NULL) AND (reference_code IS NULL) AND (reference_name IS NULL)) OR (((scope_dimension)::text <> 'GLOBAL'::text) AND ((reference_id IS NOT NULL) OR (length(TRIM(BOTH FROM reference_code)) > 0)) AND (length(TRIM(BOTH FROM reference_name)) > 0))))
);


--
-- Name: finance_fee_rule_scopes_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_fee_rule_scopes_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    fee_rule_id uuid,
    scope_dimension character varying(40),
    reference_id uuid,
    reference_code character varying(80),
    reference_name character varying(200),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: finance_fee_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_fee_rules (
    id uuid NOT NULL,
    fee_catalogue_id uuid NOT NULL,
    rule_version integer NOT NULL,
    transaction_currency_code character varying(3) NOT NULL,
    transaction_amount numeric(14,2) NOT NULL,
    base_currency_code character varying(3) DEFAULT 'USD'::character varying NOT NULL,
    exchange_rate_id uuid,
    base_amount numeric(14,2),
    rating_status character varying(20) NOT NULL,
    effective_from timestamp with time zone NOT NULL,
    effective_until timestamp with time zone,
    scope_signature text,
    status character varying(20) NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    retired_by_user_id uuid,
    retired_at timestamp with time zone,
    retirement_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    fee_structure_id uuid,
    structure_line_number integer,
    structure_line_description character varying(500),
    CONSTRAINT ck_finance_fee_rule_actor_separation CHECK (((approved_by_user_id IS NULL) OR (approved_by_user_id <> prepared_by_user_id))),
    CONSTRAINT ck_finance_fee_rule_amount CHECK ((transaction_amount > (0)::numeric)),
    CONSTRAINT ck_finance_fee_rule_approval CHECK (((((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_RATE'::character varying])::text[])) AND (approved_by_user_id IS NULL) AND (approved_at IS NULL) AND (approval_reason IS NULL) AND (retired_by_user_id IS NULL) AND (retired_at IS NULL) AND (retirement_reason IS NULL)) OR (((status)::text = 'APPROVED'::text) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (scope_signature IS NOT NULL) AND (retired_by_user_id IS NULL) AND (retired_at IS NULL) AND (retirement_reason IS NULL)) OR (((status)::text = 'RETIRED'::text) AND (approved_by_user_id IS NOT NULL) AND (approved_at IS NOT NULL) AND (length(TRIM(BOTH FROM approval_reason)) > 0) AND (scope_signature IS NOT NULL) AND (retired_by_user_id IS NOT NULL) AND (retired_at IS NOT NULL) AND (length(TRIM(BOTH FROM retirement_reason)) > 0)))),
    CONSTRAINT ck_finance_fee_rule_currency CHECK ((((transaction_currency_code)::text = upper((transaction_currency_code)::text)) AND ((transaction_currency_code)::text ~ '^[A-Z]{3}$'::text) AND ((base_currency_code)::text = 'USD'::text))),
    CONSTRAINT ck_finance_fee_rule_effectivity CHECK (((effective_until IS NULL) OR (effective_until > effective_from))),
    CONSTRAINT ck_finance_fee_rule_rating CHECK (((((transaction_currency_code)::text = 'USD'::text) AND (exchange_rate_id IS NULL) AND (base_amount = transaction_amount) AND ((rating_status)::text = 'RATED'::text)) OR (((transaction_currency_code)::text <> 'USD'::text) AND (exchange_rate_id IS NULL) AND (base_amount IS NULL) AND ((rating_status)::text = 'UNRATED'::text)) OR (((transaction_currency_code)::text <> 'USD'::text) AND (exchange_rate_id IS NOT NULL) AND (base_amount IS NOT NULL) AND ((rating_status)::text = 'RATED'::text)))),
    CONSTRAINT ck_finance_fee_rule_rating_status CHECK (((rating_status)::text = ANY ((ARRAY['RATED'::character varying, 'UNRATED'::character varying])::text[]))),
    CONSTRAINT ck_finance_fee_rule_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PENDING_RATE'::character varying, 'APPROVED'::character varying, 'RETIRED'::character varying])::text[]))),
    CONSTRAINT ck_finance_fee_rule_status_rating CHECK (((((status)::text = 'PENDING_RATE'::text) AND ((rating_status)::text = 'UNRATED'::text)) OR ((status)::text <> 'PENDING_RATE'::text))),
    CONSTRAINT ck_finance_fee_rule_structure_line CHECK ((((fee_structure_id IS NULL) AND (structure_line_number IS NULL) AND (structure_line_description IS NULL)) OR ((fee_structure_id IS NOT NULL) AND (structure_line_number > 0) AND (length(TRIM(BOTH FROM structure_line_description)) > 0)))),
    CONSTRAINT ck_finance_fee_rule_version CHECK ((rule_version > 0))
);


--
-- Name: finance_fee_rules_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_fee_rules_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    fee_catalogue_id uuid,
    rule_version integer,
    transaction_currency_code character varying(3),
    transaction_amount numeric(14,2),
    base_currency_code character varying(3),
    exchange_rate_id uuid,
    base_amount numeric(14,2),
    rating_status character varying(20),
    effective_from timestamp with time zone,
    effective_until timestamp with time zone,
    scope_signature text,
    status character varying(20),
    prepared_by_user_id uuid,
    approved_by_user_id uuid,
    approved_at timestamp with time zone,
    approval_reason character varying(1000),
    retired_by_user_id uuid,
    retired_at timestamp with time zone,
    retirement_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    fee_structure_id uuid,
    structure_line_number integer,
    structure_line_description character varying(500)
);


--
-- Name: finance_fee_structure_attachments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_fee_structure_attachments (
    id uuid NOT NULL,
    fee_structure_id uuid NOT NULL,
    programme_id uuid NOT NULL,
    programme_code character varying(80) NOT NULL,
    programme_name character varying(200) NOT NULL,
    academic_period_id uuid NOT NULL,
    academic_period_code character varying(80) NOT NULL,
    academic_period_name character varying(200) NOT NULL,
    programme_period_number integer CONSTRAINT finance_fee_structure_attachme_programme_period_number_not_null NOT NULL,
    discount_type character varying(20),
    discount_value numeric(19,4),
    discount_reason character varying(500),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_finance_fee_structure_attachment_discount CHECK ((((discount_type IS NULL) AND (discount_value IS NULL) AND (discount_reason IS NULL)) OR (((discount_type)::text = 'PERCENTAGE'::text) AND (discount_value > (0)::numeric) AND (discount_value <= (100)::numeric) AND (length(TRIM(BOTH FROM discount_reason)) > 0)) OR (((discount_type)::text = 'AMOUNT'::text) AND (discount_value > (0)::numeric) AND (length(TRIM(BOTH FROM discount_reason)) > 0)))),
    CONSTRAINT ck_finance_fee_structure_attachment_discount_type CHECK (((discount_type IS NULL) OR ((discount_type)::text = ANY ((ARRAY['PERCENTAGE'::character varying, 'AMOUNT'::character varying])::text[])))),
    CONSTRAINT ck_finance_fee_structure_attachment_period_code CHECK ((((academic_period_code)::text = upper((academic_period_code)::text)) AND (length(TRIM(BOTH FROM academic_period_code)) > 0))),
    CONSTRAINT ck_finance_fee_structure_attachment_programme_code CHECK ((((programme_code)::text = upper((programme_code)::text)) AND (length(TRIM(BOTH FROM programme_code)) > 0))),
    CONSTRAINT ck_finance_fee_structure_attachment_programme_period CHECK ((programme_period_number > 0))
);


--
-- Name: finance_fee_structure_attachments_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_fee_structure_attachments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    fee_structure_id uuid,
    programme_id uuid,
    programme_code character varying(80),
    programme_name character varying(200),
    academic_period_id uuid,
    academic_period_code character varying(80),
    academic_period_name character varying(200),
    programme_period_number integer,
    discount_type character varying(20),
    discount_value numeric(19,4),
    discount_reason character varying(500),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: finance_fee_structures; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_fee_structures (
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(160) NOT NULL,
    description character varying(1000),
    fee_context character varying(30) NOT NULL,
    scope_type character varying(30) NOT NULL,
    scope_reference_id uuid,
    scope_reference_code character varying(80),
    scope_reference_name character varying(200),
    academic_period_id uuid,
    academic_period_code character varying(80),
    academic_period_name character varying(200),
    programme_period_number integer,
    applicant_category_code character varying(80),
    transaction_currency_code character varying(3) NOT NULL,
    effective_from timestamp with time zone NOT NULL,
    effective_until timestamp with time zone,
    status character varying(20) NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    activated_by_user_id uuid,
    activated_at timestamp with time zone,
    activation_reason character varying(1000),
    retired_by_user_id uuid,
    retired_at timestamp with time zone,
    retirement_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    programme_level_id uuid,
    programme_level_code character varying(80) NOT NULL,
    programme_level_name character varying(200) NOT NULL,
    CONSTRAINT ck_finance_application_structure_level_scope CHECK ((((fee_context)::text <> 'APPLICATION'::text) OR (((scope_type)::text = 'PROGRAMME_LEVEL'::text) AND (NOT (scope_reference_id IS DISTINCT FROM programme_level_id)) AND (upper((scope_reference_code)::text) = upper((programme_level_code)::text)) AND ((scope_reference_name)::text = (programme_level_name)::text)))),
    CONSTRAINT ck_finance_fee_structure_actor_separation CHECK (((activated_by_user_id IS NULL) OR (activated_by_user_id <> prepared_by_user_id))),
    CONSTRAINT ck_finance_fee_structure_applicant_category CHECK (((applicant_category_code IS NULL) OR ((applicant_category_code)::text = ANY ((ARRAY['LOCAL'::character varying, 'SADC'::character varying, 'INTERNATIONAL'::character varying, 'CLE'::character varying])::text[])))),
    CONSTRAINT ck_finance_fee_structure_context CHECK (((fee_context)::text = ANY ((ARRAY['ACADEMIC'::character varying, 'APPLICATION'::character varying, 'ACCOMMODATION'::character varying])::text[]))),
    CONSTRAINT ck_finance_fee_structure_context_scope CHECK (((((fee_context)::text = 'ACADEMIC'::text) AND ((scope_type)::text = ANY ((ARRAY['INSTITUTION'::character varying, 'ACADEMIC_UNIT'::character varying, 'PROGRAMME'::character varying])::text[])) AND (academic_period_id IS NULL) AND (academic_period_code IS NULL) AND (academic_period_name IS NULL) AND (programme_period_number IS NULL) AND (applicant_category_code IS NULL)) OR (((fee_context)::text = 'APPLICATION'::text) AND ((scope_type)::text = ANY ((ARRAY['PROGRAMME_LEVEL'::character varying, 'PROGRAMME_TYPE'::character varying])::text[])) AND (academic_period_id IS NULL) AND (academic_period_code IS NULL) AND (academic_period_name IS NULL) AND (programme_period_number IS NULL)) OR (((fee_context)::text = 'ACCOMMODATION'::text) AND ((scope_type)::text = 'GLOBAL'::text) AND (academic_period_id IS NULL) AND (academic_period_code IS NULL) AND (academic_period_name IS NULL) AND (programme_period_number IS NULL) AND (applicant_category_code IS NULL)))),
    CONSTRAINT ck_finance_fee_structure_currency CHECK ((((transaction_currency_code)::text = upper((transaction_currency_code)::text)) AND ((transaction_currency_code)::text ~ '^[A-Z]{3}$'::text))),
    CONSTRAINT ck_finance_fee_structure_effectivity CHECK (((effective_until IS NULL) OR (effective_until > effective_from))),
    CONSTRAINT ck_finance_fee_structure_programme_level CHECK ((((programme_level_code)::text = upper((programme_level_code)::text)) AND (length(TRIM(BOTH FROM programme_level_code)) > 0) AND (length(TRIM(BOTH FROM programme_level_name)) > 0))),
    CONSTRAINT ck_finance_fee_structure_programme_period CHECK (((programme_period_number IS NULL) OR (programme_period_number > 0))),
    CONSTRAINT ck_finance_fee_structure_scope_reference CHECK (((((scope_type)::text = ANY ((ARRAY['INSTITUTION'::character varying, 'GLOBAL'::character varying])::text[])) AND (scope_reference_id IS NULL) AND (scope_reference_code IS NULL) AND (scope_reference_name IS NULL)) OR (((scope_type)::text <> ALL ((ARRAY['INSTITUTION'::character varying, 'GLOBAL'::character varying])::text[])) AND ((scope_reference_id IS NOT NULL) OR (length(TRIM(BOTH FROM scope_reference_code)) > 0)) AND (length(TRIM(BOTH FROM scope_reference_name)) > 0)))),
    CONSTRAINT ck_finance_fee_structure_scope_type CHECK (((scope_type)::text = ANY ((ARRAY['INSTITUTION'::character varying, 'ACADEMIC_UNIT'::character varying, 'PROGRAMME'::character varying, 'PROGRAMME_LEVEL'::character varying, 'PROGRAMME_TYPE'::character varying, 'GLOBAL'::character varying])::text[]))),
    CONSTRAINT ck_finance_fee_structure_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'RETIRED'::character varying])::text[]))),
    CONSTRAINT ck_finance_fee_structure_workflow CHECK (((((status)::text = 'DRAFT'::text) AND (activated_by_user_id IS NULL) AND (activated_at IS NULL) AND (activation_reason IS NULL) AND (retired_by_user_id IS NULL) AND (retired_at IS NULL) AND (retirement_reason IS NULL)) OR (((status)::text = 'ACTIVE'::text) AND (activated_by_user_id IS NOT NULL) AND (activated_at IS NOT NULL) AND (length(TRIM(BOTH FROM activation_reason)) > 0) AND (retired_by_user_id IS NULL) AND (retired_at IS NULL) AND (retirement_reason IS NULL)) OR (((status)::text = 'RETIRED'::text) AND (activated_by_user_id IS NOT NULL) AND (activated_at IS NOT NULL) AND (length(TRIM(BOTH FROM activation_reason)) > 0) AND (retired_by_user_id IS NOT NULL) AND (retired_at IS NOT NULL) AND (length(TRIM(BOTH FROM retirement_reason)) > 0))))
);


--
-- Name: finance_fee_structures_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_fee_structures_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(50),
    name character varying(160),
    description character varying(1000),
    fee_context character varying(30),
    scope_type character varying(30),
    scope_reference_id uuid,
    scope_reference_code character varying(80),
    scope_reference_name character varying(200),
    academic_period_id uuid,
    academic_period_code character varying(80),
    academic_period_name character varying(200),
    programme_period_number integer,
    applicant_category_code character varying(80),
    transaction_currency_code character varying(3),
    effective_from timestamp with time zone,
    effective_until timestamp with time zone,
    status character varying(20),
    prepared_by_user_id uuid,
    activated_by_user_id uuid,
    activated_at timestamp with time zone,
    activation_reason character varying(1000),
    retired_by_user_id uuid,
    retired_at timestamp with time zone,
    retirement_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    programme_level_id uuid,
    programme_level_code character varying(80),
    programme_level_name character varying(200)
);


--
-- Name: finance_invoice_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_invoice_lines (
    id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    line_number integer NOT NULL,
    billing_event_id uuid NOT NULL,
    fee_catalogue_id uuid NOT NULL,
    fee_rule_id uuid NOT NULL,
    fee_code character varying(50) NOT NULL,
    description character varying(500) NOT NULL,
    quantity numeric(12,4) NOT NULL,
    transaction_currency_code character varying(3) NOT NULL,
    transaction_unit_amount numeric(16,2) NOT NULL,
    transaction_amount numeric(16,2) NOT NULL,
    base_currency_code character varying(3) NOT NULL,
    exchange_rate_id uuid,
    base_unit_amount numeric(16,2) NOT NULL,
    base_amount numeric(16,2) NOT NULL,
    receivable_account_code character varying(50) NOT NULL,
    revenue_account_code character varying(50) NOT NULL,
    tax_code character varying(30),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    discount_rule_id uuid,
    discount_rule_code character varying(50),
    discount_percentage numeric(7,4),
    gross_transaction_amount numeric(16,2) NOT NULL,
    transaction_discount_amount numeric(16,2) DEFAULT 0 NOT NULL,
    gross_base_amount numeric(16,2) NOT NULL,
    base_discount_amount numeric(16,2) DEFAULT 0 NOT NULL,
    CONSTRAINT ck_finance_invoice_line_amounts CHECK (((quantity > (0)::numeric) AND (transaction_unit_amount > (0)::numeric) AND (gross_transaction_amount = round((transaction_unit_amount * quantity), 2)) AND (transaction_amount = (gross_transaction_amount - transaction_discount_amount)) AND (transaction_amount > (0)::numeric) AND (base_unit_amount > (0)::numeric) AND (gross_base_amount = round((base_unit_amount * quantity), 2)) AND (base_amount = (gross_base_amount - base_discount_amount)) AND (base_amount > (0)::numeric))),
    CONSTRAINT ck_finance_invoice_line_currency CHECK ((((transaction_currency_code)::text = upper((transaction_currency_code)::text)) AND ((transaction_currency_code)::text ~ '^[A-Z]{3}$'::text) AND ((base_currency_code)::text = 'USD'::text))),
    CONSTRAINT ck_finance_invoice_line_description CHECK ((length(TRIM(BOTH FROM description)) > 0)),
    CONSTRAINT ck_finance_invoice_line_number CHECK ((line_number > 0))
);


--
-- Name: finance_invoice_lines_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_invoice_lines_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    invoice_id uuid,
    line_number integer,
    billing_event_id uuid,
    fee_catalogue_id uuid,
    fee_rule_id uuid,
    fee_code character varying(50),
    description character varying(500),
    quantity numeric(12,4),
    transaction_currency_code character varying(3),
    transaction_unit_amount numeric(16,2),
    transaction_amount numeric(16,2),
    base_currency_code character varying(3),
    exchange_rate_id uuid,
    base_unit_amount numeric(16,2),
    base_amount numeric(16,2),
    receivable_account_code character varying(50),
    revenue_account_code character varying(50),
    tax_code character varying(30),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    discount_rule_id uuid,
    discount_rule_code character varying(50),
    discount_percentage numeric(7,4),
    gross_transaction_amount numeric(16,2),
    transaction_discount_amount numeric(16,2),
    gross_base_amount numeric(16,2),
    base_discount_amount numeric(16,2)
);


--
-- Name: finance_invoice_number_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finance_invoice_number_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finance_invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_invoices (
    id uuid NOT NULL,
    invoice_number character varying(40) NOT NULL,
    student_finance_account_id uuid NOT NULL,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    transaction_currency_code character varying(3) NOT NULL,
    base_currency_code character varying(3) DEFAULT 'USD'::character varying NOT NULL,
    gross_transaction_amount numeric(16,2) NOT NULL,
    gross_base_amount numeric(16,2) NOT NULL,
    invoice_date date NOT NULL,
    due_date date NOT NULL,
    status character varying(20) NOT NULL,
    posted_by_user_id uuid NOT NULL,
    posted_at timestamp with time zone NOT NULL,
    posting_reason character varying(1000) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    transaction_discount_amount numeric(16,2) DEFAULT 0 NOT NULL,
    net_transaction_amount numeric(16,2) NOT NULL,
    base_discount_amount numeric(16,2) DEFAULT 0 NOT NULL,
    net_base_amount numeric(16,2) NOT NULL,
    CONSTRAINT ck_finance_invoice_amounts CHECK (((gross_transaction_amount > (0)::numeric) AND (transaction_discount_amount >= (0)::numeric) AND (net_transaction_amount = (gross_transaction_amount - transaction_discount_amount)) AND (net_transaction_amount > (0)::numeric) AND (gross_base_amount > (0)::numeric) AND (base_discount_amount >= (0)::numeric) AND (net_base_amount = (gross_base_amount - base_discount_amount)) AND (net_base_amount > (0)::numeric))),
    CONSTRAINT ck_finance_invoice_currency CHECK ((((transaction_currency_code)::text = upper((transaction_currency_code)::text)) AND ((transaction_currency_code)::text ~ '^[A-Z]{3}$'::text) AND ((base_currency_code)::text = 'USD'::text))),
    CONSTRAINT ck_finance_invoice_dates CHECK ((due_date >= invoice_date)),
    CONSTRAINT ck_finance_invoice_reason CHECK ((length(TRIM(BOTH FROM posting_reason)) > 0)),
    CONSTRAINT ck_finance_invoice_status CHECK (((status)::text = 'POSTED'::text))
);


--
-- Name: finance_invoices_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_invoices_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    invoice_number character varying(40),
    student_finance_account_id uuid,
    student_id uuid,
    student_number character varying(40),
    transaction_currency_code character varying(3),
    base_currency_code character varying(3),
    gross_transaction_amount numeric(16,2),
    gross_base_amount numeric(16,2),
    invoice_date date,
    due_date date,
    status character varying(20),
    posted_by_user_id uuid,
    posted_at timestamp with time zone,
    posting_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    transaction_discount_amount numeric(16,2),
    net_transaction_amount numeric(16,2),
    base_discount_amount numeric(16,2),
    net_base_amount numeric(16,2)
);


--
-- Name: finance_receipt_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finance_receipt_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 20;


--
-- Name: finance_receipts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_receipts (
    id uuid NOT NULL,
    application_payment_id uuid NOT NULL,
    receipt_number character varying(80) NOT NULL,
    document_id uuid,
    status character varying(30) NOT NULL,
    issued_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_finance_receipts_issued CHECK (((((status)::text = 'ISSUED'::text) AND (document_id IS NOT NULL) AND (issued_at IS NOT NULL)) OR ((status)::text <> 'ISSUED'::text))),
    CONSTRAINT ck_finance_receipts_status CHECK (((status)::text = ANY ((ARRAY['PENDING_GENERATION'::character varying, 'ISSUED'::character varying, 'VOIDED'::character varying])::text[])))
);


--
-- Name: finance_receipts_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_receipts_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    application_payment_id uuid,
    receipt_number character varying(80),
    document_id uuid,
    status character varying(30),
    issued_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: finance_reversal_number_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.finance_reversal_number_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: finance_student_discount_rule_programme_periods; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_student_discount_rule_programme_periods (
    id uuid NOT NULL,
    discount_rule_programme_id uuid CONSTRAINT finance_student_discount_ru_discount_rule_programme_id_not_null NOT NULL,
    programme_period_number integer CONSTRAINT finance_student_discount_rule__programme_period_number_not_null NOT NULL,
    created_at timestamp with time zone CONSTRAINT finance_student_discount_rule_programme_per_created_at_not_null NOT NULL,
    updated_at timestamp with time zone CONSTRAINT finance_student_discount_rule_programme_per_updated_at_not_null NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint CONSTRAINT finance_student_discount_rule_programme_period_version_not_null NOT NULL,
    CONSTRAINT ck_finance_student_discount_programme_period_number CHECK ((programme_period_number > 0))
);


--
-- Name: finance_student_discount_rule_programme_periods_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_student_discount_rule_programme_periods_aud (
    id uuid NOT NULL,
    rev integer CONSTRAINT finance_student_discount_rule_programme_periods_au_rev_not_null NOT NULL,
    revtype smallint,
    discount_rule_programme_id uuid,
    programme_period_number integer,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: finance_student_discount_rule_programmes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_student_discount_rule_programmes (
    id uuid NOT NULL,
    discount_rule_id uuid CONSTRAINT finance_student_discount_rule_program_discount_rule_id_not_null NOT NULL,
    programme_id uuid NOT NULL,
    programme_code character varying(80) CONSTRAINT finance_student_discount_rule_programme_programme_code_not_null NOT NULL,
    programme_name character varying(200) CONSTRAINT finance_student_discount_rule_programme_programme_name_not_null NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_finance_student_discount_programme_code CHECK ((((programme_code)::text = upper((programme_code)::text)) AND (length(TRIM(BOTH FROM programme_code)) > 0))),
    CONSTRAINT ck_finance_student_discount_programme_name CHECK ((length(TRIM(BOTH FROM programme_name)) > 0))
);


--
-- Name: finance_student_discount_rule_programmes_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_student_discount_rule_programmes_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    discount_rule_id uuid,
    programme_id uuid,
    programme_code character varying(80),
    programme_name character varying(200),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: finance_student_discount_rules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_student_discount_rules (
    id uuid NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(160) NOT NULL,
    scope_type character varying(30) NOT NULL,
    scope_reference_id uuid,
    scope_reference_code character varying(80),
    scope_reference_name character varying(200),
    scope_depth integer DEFAULT 0 NOT NULL,
    target_type character varying(30) NOT NULL,
    fee_catalogue_id uuid,
    academic_period_id uuid,
    academic_period_code character varying(80),
    academic_period_name character varying(200),
    programme_period_number integer,
    discount_percentage numeric(7,4) NOT NULL,
    authority_reference character varying(500) NOT NULL,
    effective_from timestamp with time zone NOT NULL,
    effective_until timestamp with time zone,
    status character varying(20) NOT NULL,
    prepared_by_user_id uuid NOT NULL,
    activated_by_user_id uuid,
    activated_at timestamp with time zone,
    activation_reason character varying(1000),
    retired_by_user_id uuid,
    retired_at timestamp with time zone,
    retirement_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    academic_unit_id uuid,
    academic_unit_code character varying(80),
    academic_unit_name character varying(200),
    programme_id uuid,
    programme_code character varying(80),
    programme_name character varying(200),
    programme_level_id uuid NOT NULL,
    programme_level_code character varying(80) NOT NULL,
    programme_level_name character varying(200) NOT NULL,
    programme_study_level character varying(20) NOT NULL,
    CONSTRAINT ck_finance_student_discount_academic_unit_snapshot CHECK ((((academic_unit_id IS NULL) AND (academic_unit_code IS NULL) AND (academic_unit_name IS NULL)) OR ((academic_unit_id IS NOT NULL) AND (length(TRIM(BOTH FROM academic_unit_code)) > 0) AND ((academic_unit_code)::text = upper((academic_unit_code)::text)) AND (length(TRIM(BOTH FROM academic_unit_name)) > 0)))),
    CONSTRAINT ck_finance_student_discount_actor_separation CHECK (((activated_by_user_id IS NULL) OR (activated_by_user_id <> prepared_by_user_id))),
    CONSTRAINT ck_finance_student_discount_authority CHECK ((length(TRIM(BOTH FROM authority_reference)) > 0)),
    CONSTRAINT ck_finance_student_discount_effectivity CHECK (((effective_until IS NULL) OR (effective_until > effective_from))),
    CONSTRAINT ck_finance_student_discount_explicit_scope CHECK ((((programme_id IS NOT NULL) AND ((scope_type)::text = 'PROGRAMME'::text) AND (scope_reference_id = programme_id) AND ((scope_reference_code)::text = (programme_code)::text) AND ((scope_reference_name)::text = (programme_name)::text)) OR ((programme_id IS NULL) AND (academic_unit_id IS NOT NULL) AND ((scope_type)::text = 'ACADEMIC_UNIT'::text) AND (scope_reference_id = academic_unit_id) AND ((scope_reference_code)::text = (academic_unit_code)::text) AND ((scope_reference_name)::text = (academic_unit_name)::text)) OR ((programme_id IS NULL) AND (academic_unit_id IS NULL) AND ((scope_type)::text = 'INSTITUTION'::text) AND (scope_reference_id IS NULL) AND (scope_reference_code IS NULL) AND (scope_reference_name IS NULL)))),
    CONSTRAINT ck_finance_student_discount_level CHECK ((((programme_level_code)::text = ANY ((ARRAY['UG'::character varying, 'PG'::character varying])::text[])) AND (length(TRIM(BOTH FROM programme_level_name)) > 0))),
    CONSTRAINT ck_finance_student_discount_percentage CHECK (((discount_percentage > (0)::numeric) AND (discount_percentage < (100)::numeric))),
    CONSTRAINT ck_finance_student_discount_period CHECK ((((academic_period_id IS NULL) AND (academic_period_code IS NULL) AND (academic_period_name IS NULL)) OR ((academic_period_id IS NOT NULL) AND (length(TRIM(BOTH FROM academic_period_code)) > 0) AND (length(TRIM(BOTH FROM academic_period_name)) > 0)))),
    CONSTRAINT ck_finance_student_discount_programme_period CHECK (((programme_period_number IS NULL) OR (programme_period_number > 0))),
    CONSTRAINT ck_finance_student_discount_programme_snapshot CHECK ((((programme_id IS NULL) AND (programme_code IS NULL) AND (programme_name IS NULL)) OR ((programme_id IS NOT NULL) AND (length(TRIM(BOTH FROM programme_code)) > 0) AND ((programme_code)::text = upper((programme_code)::text)) AND (length(TRIM(BOTH FROM programme_name)) > 0)))),
    CONSTRAINT ck_finance_student_discount_scope CHECK (((scope_type)::text = ANY ((ARRAY['INSTITUTION'::character varying, 'ACADEMIC_UNIT'::character varying, 'PROGRAMME'::character varying])::text[]))),
    CONSTRAINT ck_finance_student_discount_scope_reference CHECK (((((scope_type)::text = 'INSTITUTION'::text) AND (scope_reference_id IS NULL) AND (scope_reference_code IS NULL) AND (scope_reference_name IS NULL) AND (scope_depth = 0)) OR (((scope_type)::text = ANY ((ARRAY['ACADEMIC_UNIT'::character varying, 'PROGRAMME'::character varying])::text[])) AND (scope_reference_id IS NOT NULL) AND (length(TRIM(BOTH FROM scope_reference_code)) > 0) AND (length(TRIM(BOTH FROM scope_reference_name)) > 0) AND (scope_depth > 0)))),
    CONSTRAINT ck_finance_student_discount_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'ACTIVE'::character varying, 'RETIRED'::character varying])::text[]))),
    CONSTRAINT ck_finance_student_discount_study_level CHECK (((programme_study_level)::text ~ '^[1-9][0-9]*\.[1-9][0-9]*$'::text)),
    CONSTRAINT ck_finance_student_discount_target CHECK (((((target_type)::text = 'ALL_FEES'::text) AND (fee_catalogue_id IS NULL)) OR (((target_type)::text = 'FEE_LINE'::text) AND (fee_catalogue_id IS NOT NULL)))),
    CONSTRAINT ck_finance_student_discount_workflow CHECK (((((status)::text = 'DRAFT'::text) AND (activated_by_user_id IS NULL) AND (activated_at IS NULL) AND (activation_reason IS NULL) AND (retired_by_user_id IS NULL) AND (retired_at IS NULL) AND (retirement_reason IS NULL)) OR (((status)::text = 'ACTIVE'::text) AND (activated_by_user_id IS NOT NULL) AND (activated_at IS NOT NULL) AND (length(TRIM(BOTH FROM activation_reason)) > 0) AND (retired_by_user_id IS NULL) AND (retired_at IS NULL) AND (retirement_reason IS NULL)) OR (((status)::text = 'RETIRED'::text) AND (activated_by_user_id IS NOT NULL) AND (activated_at IS NOT NULL) AND (length(TRIM(BOTH FROM activation_reason)) > 0) AND (retired_by_user_id IS NOT NULL) AND (retired_at IS NOT NULL) AND (length(TRIM(BOTH FROM retirement_reason)) > 0))))
);


--
-- Name: finance_student_discount_rules_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.finance_student_discount_rules_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    code character varying(50),
    name character varying(160),
    scope_type character varying(30),
    scope_reference_id uuid,
    scope_reference_code character varying(80),
    scope_reference_name character varying(200),
    scope_depth integer,
    target_type character varying(30),
    fee_catalogue_id uuid,
    academic_period_id uuid,
    academic_period_code character varying(80),
    academic_period_name character varying(200),
    programme_period_number integer,
    discount_percentage numeric(7,4),
    authority_reference character varying(500),
    effective_from timestamp with time zone,
    effective_until timestamp with time zone,
    status character varying(20),
    prepared_by_user_id uuid,
    activated_by_user_id uuid,
    activated_at timestamp with time zone,
    activation_reason character varying(1000),
    retired_by_user_id uuid,
    retired_at timestamp with time zone,
    retirement_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint,
    academic_unit_id uuid,
    academic_unit_code character varying(80),
    academic_unit_name character varying(200),
    programme_id uuid,
    programme_code character varying(80),
    programme_name character varying(200),
    programme_level_id uuid,
    programme_level_code character varying(80),
    programme_level_name character varying(200),
    programme_study_level character varying(20)
);


--
-- Name: integration_inbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integration_inbox (
    event_id uuid NOT NULL,
    event_type character varying(160) NOT NULL,
    source_service character varying(100) NOT NULL,
    payload jsonb NOT NULL,
    received_at timestamp with time zone NOT NULL,
    processed_at timestamp with time zone
);


--
-- Name: integration_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.integration_outbox (
    id uuid NOT NULL,
    event_type character varying(160) NOT NULL,
    routing_key character varying(160) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    status character varying(20) NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    last_error character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_finance_outbox_attempt_count CHECK ((attempt_count >= 0)),
    CONSTRAINT ck_finance_outbox_status CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'DEAD'::character varying])::text[])))
);


--
-- Name: revinfo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.revinfo (
    rev integer NOT NULL,
    revtstmp bigint NOT NULL,
    actor_user_id uuid,
    service_name character varying(100) DEFAULT 'finance-service'::character varying NOT NULL,
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
-- Name: revinfo_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.revinfo_seq
    START WITH 1
    INCREMENT BY 50
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: student_account_payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_account_payments (
    id uuid NOT NULL,
    payment_number character varying(40) NOT NULL,
    student_finance_account_id uuid,
    payer_name character varying(200) NOT NULL,
    provider_code character varying(50) NOT NULL,
    provider_transaction_reference character varying(160) CONSTRAINT student_account_payments_provider_transaction_referenc_not_null NOT NULL,
    payment_channel character varying(40) NOT NULL,
    transaction_currency_code character varying(3) NOT NULL,
    transaction_amount numeric(16,2) NOT NULL,
    base_currency_code character varying(3) DEFAULT 'USD'::character varying NOT NULL,
    exchange_rate_id uuid,
    base_amount numeric(16,2),
    rating_status character varying(20) NOT NULL,
    rating_applied_by_user_id uuid,
    rating_applied_at timestamp with time zone,
    paid_at timestamp with time zone NOT NULL,
    provider_event_fingerprint character varying(128) NOT NULL,
    reconciliation_status character varying(30) NOT NULL,
    captured_by_user_id uuid NOT NULL,
    captured_at timestamp with time zone NOT NULL,
    reconciled_by_user_id uuid,
    reconciled_at timestamp with time zone,
    reconciliation_reason character varying(1000),
    rejected_by_user_id uuid,
    rejected_at timestamp with time zone,
    rejection_reason character varying(1000),
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_student_account_payment_actor_separation CHECK ((((reconciled_by_user_id IS NULL) OR (reconciled_by_user_id <> captured_by_user_id)) AND ((rejected_by_user_id IS NULL) OR (rejected_by_user_id <> captured_by_user_id)))),
    CONSTRAINT ck_student_account_payment_amount CHECK ((transaction_amount > (0)::numeric)),
    CONSTRAINT ck_student_account_payment_channel CHECK (((payment_channel)::text = ANY ((ARRAY['CASH'::character varying, 'BANK_TRANSFER'::character varying, 'CARD'::character varying, 'MOBILE_MONEY'::character varying, 'ONLINE'::character varying, 'OTHER'::character varying])::text[]))),
    CONSTRAINT ck_student_account_payment_currency CHECK ((((transaction_currency_code)::text = upper((transaction_currency_code)::text)) AND ((transaction_currency_code)::text ~ '^[A-Z]{3}$'::text) AND ((base_currency_code)::text = 'USD'::text))),
    CONSTRAINT ck_student_account_payment_payer CHECK ((length(TRIM(BOTH FROM payer_name)) > 0)),
    CONSTRAINT ck_student_account_payment_rating CHECK (((((transaction_currency_code)::text = 'USD'::text) AND (exchange_rate_id IS NULL) AND (base_amount = transaction_amount) AND ((rating_status)::text = 'RATED'::text) AND (rating_applied_by_user_id IS NOT NULL) AND (rating_applied_at IS NOT NULL)) OR (((transaction_currency_code)::text <> 'USD'::text) AND (exchange_rate_id IS NULL) AND (base_amount IS NULL) AND ((rating_status)::text = 'UNRATED'::text) AND (rating_applied_by_user_id IS NULL) AND (rating_applied_at IS NULL)) OR (((transaction_currency_code)::text <> 'USD'::text) AND (exchange_rate_id IS NOT NULL) AND (base_amount IS NOT NULL) AND ((rating_status)::text = 'RATED'::text) AND (rating_applied_by_user_id IS NOT NULL) AND (rating_applied_at IS NOT NULL)))),
    CONSTRAINT ck_student_account_payment_rating_status CHECK (((rating_status)::text = ANY ((ARRAY['RATED'::character varying, 'UNRATED'::character varying])::text[]))),
    CONSTRAINT ck_student_account_payment_reconciliation CHECK (((((reconciliation_status)::text = 'PENDING'::text) AND (reconciled_by_user_id IS NULL) AND (reconciled_at IS NULL) AND (reconciliation_reason IS NULL) AND (rejected_by_user_id IS NULL) AND (rejected_at IS NULL) AND (rejection_reason IS NULL)) OR (((reconciliation_status)::text = 'RECONCILED'::text) AND ((rating_status)::text = 'RATED'::text) AND (reconciled_by_user_id IS NOT NULL) AND (reconciled_at IS NOT NULL) AND (length(TRIM(BOTH FROM reconciliation_reason)) > 0) AND (rejected_by_user_id IS NULL) AND (rejected_at IS NULL) AND (rejection_reason IS NULL)) OR (((reconciliation_status)::text = 'REJECTED'::text) AND (reconciled_by_user_id IS NULL) AND (reconciled_at IS NULL) AND (reconciliation_reason IS NULL) AND (rejected_by_user_id IS NOT NULL) AND (rejected_at IS NOT NULL) AND (length(TRIM(BOTH FROM rejection_reason)) > 0)))),
    CONSTRAINT ck_student_account_payment_reconciliation_status CHECK (((reconciliation_status)::text = ANY ((ARRAY['PENDING'::character varying, 'RECONCILED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: student_account_payments_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_account_payments_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    payment_number character varying(40),
    student_finance_account_id uuid,
    payer_name character varying(200),
    provider_code character varying(50),
    provider_transaction_reference character varying(160),
    payment_channel character varying(40),
    transaction_currency_code character varying(3),
    transaction_amount numeric(16,2),
    base_currency_code character varying(3),
    exchange_rate_id uuid,
    base_amount numeric(16,2),
    rating_status character varying(20),
    rating_applied_by_user_id uuid,
    rating_applied_at timestamp with time zone,
    paid_at timestamp with time zone,
    provider_event_fingerprint character varying(128),
    reconciliation_status character varying(30),
    captured_by_user_id uuid,
    captured_at timestamp with time zone,
    reconciled_by_user_id uuid,
    reconciled_at timestamp with time zone,
    reconciliation_reason character varying(1000),
    rejected_by_user_id uuid,
    rejected_at timestamp with time zone,
    rejection_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: student_finance_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_finance_accounts (
    id uuid NOT NULL,
    account_number character varying(50) NOT NULL,
    student_id uuid NOT NULL,
    student_number character varying(40) NOT NULL,
    user_id uuid NOT NULL,
    source_offer_id uuid NOT NULL,
    primary_email character varying(200) NOT NULL,
    base_currency_code character varying(3) NOT NULL,
    status character varying(30) NOT NULL,
    opened_at timestamp with time zone NOT NULL,
    closed_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_student_finance_accounts_closure CHECK (((((status)::text = 'CLOSED'::text) AND (closed_at IS NOT NULL)) OR (((status)::text <> 'CLOSED'::text) AND (closed_at IS NULL)))),
    CONSTRAINT ck_student_finance_accounts_currency CHECK (((base_currency_code)::text = 'USD'::text)),
    CONSTRAINT ck_student_finance_accounts_registration_number CHECK (((account_number)::text = (student_number)::text)),
    CONSTRAINT ck_student_finance_accounts_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'ON_HOLD'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: student_finance_accounts_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_finance_accounts_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    account_number character varying(50),
    student_id uuid,
    student_number character varying(40),
    user_id uuid,
    source_offer_id uuid,
    primary_email character varying(200),
    base_currency_code character varying(3),
    status character varying(30),
    opened_at timestamp with time zone,
    closed_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: student_payment_allocation_number_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.student_payment_allocation_number_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: student_payment_allocation_reversals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_payment_allocation_reversals (
    id uuid NOT NULL,
    reversal_number character varying(40) NOT NULL,
    allocation_id uuid NOT NULL,
    reversed_by_user_id uuid CONSTRAINT student_payment_allocation_reversa_reversed_by_user_id_not_null NOT NULL,
    reversed_at timestamp with time zone NOT NULL,
    reversal_reason character varying(1000) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_student_payment_allocation_reversal_reason CHECK ((length(TRIM(BOTH FROM reversal_reason)) > 0))
);


--
-- Name: student_payment_allocation_reversals_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_payment_allocation_reversals_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    reversal_number character varying(40),
    allocation_id uuid,
    reversed_by_user_id uuid,
    reversed_at timestamp with time zone,
    reversal_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: student_payment_allocations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_payment_allocations (
    id uuid NOT NULL,
    allocation_number character varying(40) NOT NULL,
    payment_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    transaction_currency_code character varying(3) NOT NULL,
    transaction_amount numeric(16,2) NOT NULL,
    base_currency_code character varying(3) NOT NULL,
    payment_base_amount numeric(16,2) NOT NULL,
    invoice_base_amount numeric(16,2) NOT NULL,
    realised_exchange_difference numeric(16,2) CONSTRAINT student_payment_allocations_realised_exchange_differen_not_null NOT NULL,
    allocated_by_user_id uuid NOT NULL,
    allocated_at timestamp with time zone NOT NULL,
    allocation_reason character varying(1000) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_student_payment_allocation_amount CHECK (((transaction_amount > (0)::numeric) AND (payment_base_amount > (0)::numeric) AND (invoice_base_amount > (0)::numeric) AND ((base_currency_code)::text = 'USD'::text))),
    CONSTRAINT ck_student_payment_allocation_exchange_difference CHECK ((realised_exchange_difference = (payment_base_amount - invoice_base_amount))),
    CONSTRAINT ck_student_payment_allocation_reason CHECK ((length(TRIM(BOTH FROM allocation_reason)) > 0))
);


--
-- Name: student_payment_allocations_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_payment_allocations_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    allocation_number character varying(40),
    payment_id uuid,
    invoice_id uuid,
    transaction_currency_code character varying(3),
    transaction_amount numeric(16,2),
    base_currency_code character varying(3),
    payment_base_amount numeric(16,2),
    invoice_base_amount numeric(16,2),
    realised_exchange_difference numeric(16,2),
    allocated_by_user_id uuid,
    allocated_at timestamp with time zone,
    allocation_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: student_payment_number_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.student_payment_number_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: student_payment_receipt_number_sequence; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.student_payment_receipt_number_sequence
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: student_payment_receipts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_payment_receipts (
    id uuid NOT NULL,
    payment_id uuid NOT NULL,
    receipt_number character varying(40) NOT NULL,
    student_finance_account_id uuid,
    payer_name character varying(200) NOT NULL,
    transaction_currency_code character varying(3) NOT NULL,
    transaction_amount numeric(16,2) NOT NULL,
    base_currency_code character varying(3) NOT NULL,
    base_amount numeric(16,2) NOT NULL,
    issued_by_user_id uuid NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_student_payment_receipt_amount CHECK (((transaction_amount > (0)::numeric) AND (base_amount > (0)::numeric) AND ((base_currency_code)::text = 'USD'::text)))
);


--
-- Name: student_payment_receipts_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_payment_receipts_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    payment_id uuid,
    receipt_number character varying(40),
    student_finance_account_id uuid,
    payer_name character varying(200),
    transaction_currency_code character varying(3),
    transaction_amount numeric(16,2),
    base_currency_code character varying(3),
    base_amount numeric(16,2),
    issued_by_user_id uuid,
    issued_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: student_payment_reversals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_payment_reversals (
    id uuid NOT NULL,
    reversal_number character varying(40) NOT NULL,
    payment_id uuid NOT NULL,
    reversed_by_user_id uuid NOT NULL,
    reversed_at timestamp with time zone NOT NULL,
    reversal_reason character varying(1000) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_student_payment_reversal_reason CHECK ((length(TRIM(BOTH FROM reversal_reason)) > 0))
);


--
-- Name: student_payment_reversals_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_payment_reversals_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    reversal_number character varying(40),
    payment_id uuid,
    reversed_by_user_id uuid,
    reversed_at timestamp with time zone,
    reversal_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Name: student_payment_suspense_resolutions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_payment_suspense_resolutions (
    id uuid NOT NULL,
    payment_id uuid NOT NULL,
    student_finance_account_id uuid CONSTRAINT student_payment_suspense_re_student_finance_account_id_not_null NOT NULL,
    resolved_by_user_id uuid CONSTRAINT student_payment_suspense_resolutio_resolved_by_user_id_not_null NOT NULL,
    resolved_at timestamp with time zone NOT NULL,
    resolution_reason character varying(1000) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint NOT NULL,
    CONSTRAINT ck_student_payment_suspense_reason CHECK ((length(TRIM(BOTH FROM resolution_reason)) > 0))
);


--
-- Name: student_payment_suspense_resolutions_aud; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_payment_suspense_resolutions_aud (
    id uuid NOT NULL,
    rev integer NOT NULL,
    revtype smallint,
    payment_id uuid,
    student_finance_account_id uuid,
    resolved_by_user_id uuid,
    resolved_at timestamp with time zone,
    resolution_reason character varying(1000),
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    created_by_user_id uuid,
    modified_by_user_id uuid,
    deleted_at timestamp with time zone,
    deleted_by_user_id uuid,
    version bigint
);


--
-- Data for Name: application_payment_provider_attempts; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_payment_provider_attempts_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_payment_references; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_payment_references_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_payments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: application_payments_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exchange_rates; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: exchange_rates_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_billing_event_scopes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_billing_event_scopes_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_billing_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_billing_events_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_billing_policies; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_billing_policies_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_credit_note_lines; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_credit_note_lines_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_credit_notes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_credit_notes_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_fee_catalogues; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_fee_catalogues_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_fee_rule_scopes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_fee_rule_scopes_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_fee_rules; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_fee_rules_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_fee_structure_attachments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_fee_structure_attachments_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_fee_structures; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_fee_structures_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_invoice_lines; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_invoice_lines_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_invoices; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_invoices_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_receipts; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_receipts_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_student_discount_rule_programme_periods; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_student_discount_rule_programme_periods_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_student_discount_rule_programmes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_student_discount_rule_programmes_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_student_discount_rules; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: finance_student_discount_rules_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_inbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: integration_outbox; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: revinfo; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_account_payments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_account_payments_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_finance_accounts; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_finance_accounts_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_payment_allocation_reversals; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_payment_allocation_reversals_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_payment_allocations; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_payment_allocations_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_payment_receipts; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_payment_receipts_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_payment_reversals; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_payment_reversals_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_payment_suspense_resolutions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: student_payment_suspense_resolutions_aud; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Name: application_payment_reference_sequence; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.application_payment_reference_sequence', 1, false);


--
-- Name: finance_billing_event_number_sequence; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.finance_billing_event_number_sequence', 1, false);


--
-- Name: finance_credit_note_number_sequence; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.finance_credit_note_number_sequence', 1, false);


--
-- Name: finance_invoice_number_sequence; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.finance_invoice_number_sequence', 1, false);


--
-- Name: finance_receipt_sequence; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.finance_receipt_sequence', 1, false);


--
-- Name: finance_reversal_number_sequence; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.finance_reversal_number_sequence', 1, false);


--
-- Name: revinfo_rev_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revinfo_rev_seq', 1, false);


--
-- Name: revinfo_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.revinfo_seq', 1, false);


--
-- Name: student_payment_allocation_number_sequence; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.student_payment_allocation_number_sequence', 1, false);


--
-- Name: student_payment_number_sequence; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.student_payment_number_sequence', 1, false);


--
-- Name: student_payment_receipt_number_sequence; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.student_payment_receipt_number_sequence', 1, false);


--
-- Name: application_payment_provider_attempts_aud application_payment_provider_attempts_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_provider_attempts_aud
    ADD CONSTRAINT application_payment_provider_attempts_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_payment_provider_attempts application_payment_provider_attempts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_provider_attempts
    ADD CONSTRAINT application_payment_provider_attempts_pkey PRIMARY KEY (id);


--
-- Name: application_payment_references_aud application_payment_references_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_references_aud
    ADD CONSTRAINT application_payment_references_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_payment_references application_payment_references_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_references
    ADD CONSTRAINT application_payment_references_pkey PRIMARY KEY (id);


--
-- Name: application_payments_aud application_payments_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payments_aud
    ADD CONSTRAINT application_payments_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: application_payments application_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payments
    ADD CONSTRAINT application_payments_pkey PRIMARY KEY (id);


--
-- Name: exchange_rates_aud exchange_rates_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exchange_rates_aud
    ADD CONSTRAINT exchange_rates_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: exchange_rates exchange_rates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exchange_rates
    ADD CONSTRAINT exchange_rates_pkey PRIMARY KEY (id);


--
-- Name: finance_billing_event_scopes_aud finance_billing_event_scopes_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_event_scopes_aud
    ADD CONSTRAINT finance_billing_event_scopes_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_billing_event_scopes finance_billing_event_scopes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_event_scopes
    ADD CONSTRAINT finance_billing_event_scopes_pkey PRIMARY KEY (id);


--
-- Name: finance_billing_events_aud finance_billing_events_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_events_aud
    ADD CONSTRAINT finance_billing_events_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_billing_events finance_billing_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_events
    ADD CONSTRAINT finance_billing_events_pkey PRIMARY KEY (id);


--
-- Name: finance_billing_policies_aud finance_billing_policies_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_policies_aud
    ADD CONSTRAINT finance_billing_policies_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_billing_policies finance_billing_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_policies
    ADD CONSTRAINT finance_billing_policies_pkey PRIMARY KEY (id);


--
-- Name: finance_credit_note_lines_aud finance_credit_note_lines_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_credit_note_lines_aud
    ADD CONSTRAINT finance_credit_note_lines_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_credit_note_lines finance_credit_note_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_credit_note_lines
    ADD CONSTRAINT finance_credit_note_lines_pkey PRIMARY KEY (id);


--
-- Name: finance_credit_notes_aud finance_credit_notes_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_credit_notes_aud
    ADD CONSTRAINT finance_credit_notes_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_credit_notes finance_credit_notes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_credit_notes
    ADD CONSTRAINT finance_credit_notes_pkey PRIMARY KEY (id);


--
-- Name: finance_fee_catalogues_aud finance_fee_catalogues_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_catalogues_aud
    ADD CONSTRAINT finance_fee_catalogues_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_fee_catalogues finance_fee_catalogues_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_catalogues
    ADD CONSTRAINT finance_fee_catalogues_pkey PRIMARY KEY (id);


--
-- Name: finance_fee_rule_scopes_aud finance_fee_rule_scopes_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_rule_scopes_aud
    ADD CONSTRAINT finance_fee_rule_scopes_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_fee_rule_scopes finance_fee_rule_scopes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_rule_scopes
    ADD CONSTRAINT finance_fee_rule_scopes_pkey PRIMARY KEY (id);


--
-- Name: finance_fee_rules_aud finance_fee_rules_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_rules_aud
    ADD CONSTRAINT finance_fee_rules_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_fee_rules finance_fee_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_rules
    ADD CONSTRAINT finance_fee_rules_pkey PRIMARY KEY (id);


--
-- Name: finance_fee_structure_attachments_aud finance_fee_structure_attachments_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_structure_attachments_aud
    ADD CONSTRAINT finance_fee_structure_attachments_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_fee_structure_attachments finance_fee_structure_attachments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_structure_attachments
    ADD CONSTRAINT finance_fee_structure_attachments_pkey PRIMARY KEY (id);


--
-- Name: finance_fee_structures_aud finance_fee_structures_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_structures_aud
    ADD CONSTRAINT finance_fee_structures_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_fee_structures finance_fee_structures_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_structures
    ADD CONSTRAINT finance_fee_structures_pkey PRIMARY KEY (id);


--
-- Name: finance_invoice_lines_aud finance_invoice_lines_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoice_lines_aud
    ADD CONSTRAINT finance_invoice_lines_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_invoice_lines finance_invoice_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoice_lines
    ADD CONSTRAINT finance_invoice_lines_pkey PRIMARY KEY (id);


--
-- Name: finance_invoices_aud finance_invoices_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoices_aud
    ADD CONSTRAINT finance_invoices_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_invoices finance_invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoices
    ADD CONSTRAINT finance_invoices_pkey PRIMARY KEY (id);


--
-- Name: finance_receipts_aud finance_receipts_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_receipts_aud
    ADD CONSTRAINT finance_receipts_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_receipts finance_receipts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_receipts
    ADD CONSTRAINT finance_receipts_pkey PRIMARY KEY (id);


--
-- Name: finance_student_discount_rule_programme_periods_aud finance_student_discount_rule_programme_periods_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_student_discount_rule_programme_periods_aud
    ADD CONSTRAINT finance_student_discount_rule_programme_periods_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_student_discount_rule_programme_periods finance_student_discount_rule_programme_periods_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_student_discount_rule_programme_periods
    ADD CONSTRAINT finance_student_discount_rule_programme_periods_pkey PRIMARY KEY (id);


--
-- Name: finance_student_discount_rule_programmes_aud finance_student_discount_rule_programmes_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_student_discount_rule_programmes_aud
    ADD CONSTRAINT finance_student_discount_rule_programmes_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_student_discount_rule_programmes finance_student_discount_rule_programmes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_student_discount_rule_programmes
    ADD CONSTRAINT finance_student_discount_rule_programmes_pkey PRIMARY KEY (id);


--
-- Name: finance_student_discount_rules_aud finance_student_discount_rules_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_student_discount_rules_aud
    ADD CONSTRAINT finance_student_discount_rules_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: finance_student_discount_rules finance_student_discount_rules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_student_discount_rules
    ADD CONSTRAINT finance_student_discount_rules_pkey PRIMARY KEY (id);


--
-- Name: integration_inbox integration_inbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_inbox
    ADD CONSTRAINT integration_inbox_pkey PRIMARY KEY (event_id);


--
-- Name: integration_outbox integration_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.integration_outbox
    ADD CONSTRAINT integration_outbox_pkey PRIMARY KEY (id);


--
-- Name: revinfo revinfo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.revinfo
    ADD CONSTRAINT revinfo_pkey PRIMARY KEY (rev);


--
-- Name: student_account_payments_aud student_account_payments_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_account_payments_aud
    ADD CONSTRAINT student_account_payments_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_account_payments student_account_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_account_payments
    ADD CONSTRAINT student_account_payments_pkey PRIMARY KEY (id);


--
-- Name: student_finance_accounts_aud student_finance_accounts_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_finance_accounts_aud
    ADD CONSTRAINT student_finance_accounts_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_finance_accounts student_finance_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_finance_accounts
    ADD CONSTRAINT student_finance_accounts_pkey PRIMARY KEY (id);


--
-- Name: student_payment_allocation_reversals_aud student_payment_allocation_reversals_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_allocation_reversals_aud
    ADD CONSTRAINT student_payment_allocation_reversals_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_payment_allocation_reversals student_payment_allocation_reversals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_allocation_reversals
    ADD CONSTRAINT student_payment_allocation_reversals_pkey PRIMARY KEY (id);


--
-- Name: student_payment_allocations_aud student_payment_allocations_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_allocations_aud
    ADD CONSTRAINT student_payment_allocations_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_payment_allocations student_payment_allocations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_allocations
    ADD CONSTRAINT student_payment_allocations_pkey PRIMARY KEY (id);


--
-- Name: student_payment_receipts_aud student_payment_receipts_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_receipts_aud
    ADD CONSTRAINT student_payment_receipts_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_payment_receipts student_payment_receipts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_receipts
    ADD CONSTRAINT student_payment_receipts_pkey PRIMARY KEY (id);


--
-- Name: student_payment_reversals_aud student_payment_reversals_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_reversals_aud
    ADD CONSTRAINT student_payment_reversals_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_payment_reversals student_payment_reversals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_reversals
    ADD CONSTRAINT student_payment_reversals_pkey PRIMARY KEY (id);


--
-- Name: student_payment_suspense_resolutions_aud student_payment_suspense_resolutions_aud_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_suspense_resolutions_aud
    ADD CONSTRAINT student_payment_suspense_resolutions_aud_pkey PRIMARY KEY (id, rev);


--
-- Name: student_payment_suspense_resolutions student_payment_suspense_resolutions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_suspense_resolutions
    ADD CONSTRAINT student_payment_suspense_resolutions_pkey PRIMARY KEY (id);


--
-- Name: application_payment_provider_attempts uk_application_payment_provider_attempts_trace; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_provider_attempts
    ADD CONSTRAINT uk_application_payment_provider_attempts_trace UNIQUE (provider_code, merchant_trace);


--
-- Name: application_payment_references uk_application_payment_references_application; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_references
    ADD CONSTRAINT uk_application_payment_references_application UNIQUE (source_application_id);


--
-- Name: application_payment_references uk_application_payment_references_reference; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_references
    ADD CONSTRAINT uk_application_payment_references_reference UNIQUE (reference);


--
-- Name: application_payments uk_application_payments_provider_transaction; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payments
    ADD CONSTRAINT uk_application_payments_provider_transaction UNIQUE (provider_code, provider_transaction_reference);


--
-- Name: finance_billing_events uk_finance_billing_event_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_events
    ADD CONSTRAINT uk_finance_billing_event_number UNIQUE (event_number);


--
-- Name: finance_billing_event_scopes uk_finance_billing_event_scope_dimension; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_event_scopes
    ADD CONSTRAINT uk_finance_billing_event_scope_dimension UNIQUE (billing_event_id, scope_dimension);


--
-- Name: finance_billing_events uk_finance_billing_event_source_line; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_events
    ADD CONSTRAINT uk_finance_billing_event_source_line UNIQUE (source_service, source_event_id, source_line_reference);


--
-- Name: finance_billing_policies uk_finance_billing_policy_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_policies
    ADD CONSTRAINT uk_finance_billing_policy_version UNIQUE (code, policy_version);


--
-- Name: finance_credit_note_lines uk_finance_credit_note_line_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_credit_note_lines
    ADD CONSTRAINT uk_finance_credit_note_line_number UNIQUE (credit_note_id, line_number);


--
-- Name: finance_credit_notes uk_finance_credit_note_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_credit_notes
    ADD CONSTRAINT uk_finance_credit_note_number UNIQUE (credit_note_number);


--
-- Name: finance_fee_rule_scopes uk_finance_fee_rule_scope_dimension; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_rule_scopes
    ADD CONSTRAINT uk_finance_fee_rule_scope_dimension UNIQUE (fee_rule_id, scope_dimension);


--
-- Name: finance_fee_rules uk_finance_fee_rule_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_rules
    ADD CONSTRAINT uk_finance_fee_rule_version UNIQUE (fee_catalogue_id, rule_version);


--
-- Name: finance_fee_structures uk_finance_fee_structure_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_structures
    ADD CONSTRAINT uk_finance_fee_structure_code UNIQUE (code);


--
-- Name: finance_invoice_lines uk_finance_invoice_line_billing_event; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoice_lines
    ADD CONSTRAINT uk_finance_invoice_line_billing_event UNIQUE (billing_event_id);


--
-- Name: finance_invoice_lines uk_finance_invoice_line_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoice_lines
    ADD CONSTRAINT uk_finance_invoice_line_number UNIQUE (invoice_id, line_number);


--
-- Name: finance_invoices uk_finance_invoice_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoices
    ADD CONSTRAINT uk_finance_invoice_number UNIQUE (invoice_number);


--
-- Name: finance_receipts uk_finance_receipts_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_receipts
    ADD CONSTRAINT uk_finance_receipts_number UNIQUE (receipt_number);


--
-- Name: finance_receipts uk_finance_receipts_payment; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_receipts
    ADD CONSTRAINT uk_finance_receipts_payment UNIQUE (application_payment_id);


--
-- Name: student_account_payments uk_student_account_payment_fingerprint; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_account_payments
    ADD CONSTRAINT uk_student_account_payment_fingerprint UNIQUE (provider_event_fingerprint);


--
-- Name: student_account_payments uk_student_account_payment_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_account_payments
    ADD CONSTRAINT uk_student_account_payment_number UNIQUE (payment_number);


--
-- Name: student_account_payments uk_student_account_payment_provider; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_account_payments
    ADD CONSTRAINT uk_student_account_payment_provider UNIQUE (provider_code, provider_transaction_reference);


--
-- Name: student_finance_accounts uk_student_finance_accounts_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_finance_accounts
    ADD CONSTRAINT uk_student_finance_accounts_number UNIQUE (account_number);


--
-- Name: student_finance_accounts uk_student_finance_accounts_offer; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_finance_accounts
    ADD CONSTRAINT uk_student_finance_accounts_offer UNIQUE (source_offer_id);


--
-- Name: student_finance_accounts uk_student_finance_accounts_student; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_finance_accounts
    ADD CONSTRAINT uk_student_finance_accounts_student UNIQUE (student_id);


--
-- Name: student_payment_allocations uk_student_payment_allocation_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_allocations
    ADD CONSTRAINT uk_student_payment_allocation_number UNIQUE (allocation_number);


--
-- Name: student_payment_allocation_reversals uk_student_payment_allocation_reversal; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_allocation_reversals
    ADD CONSTRAINT uk_student_payment_allocation_reversal UNIQUE (allocation_id);


--
-- Name: student_payment_allocation_reversals uk_student_payment_allocation_reversal_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_allocation_reversals
    ADD CONSTRAINT uk_student_payment_allocation_reversal_number UNIQUE (reversal_number);


--
-- Name: student_payment_receipts uk_student_payment_receipt_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_receipts
    ADD CONSTRAINT uk_student_payment_receipt_number UNIQUE (receipt_number);


--
-- Name: student_payment_receipts uk_student_payment_receipt_payment; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_receipts
    ADD CONSTRAINT uk_student_payment_receipt_payment UNIQUE (payment_id);


--
-- Name: student_payment_reversals uk_student_payment_reversal; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_reversals
    ADD CONSTRAINT uk_student_payment_reversal UNIQUE (payment_id);


--
-- Name: student_payment_reversals uk_student_payment_reversal_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_reversals
    ADD CONSTRAINT uk_student_payment_reversal_number UNIQUE (reversal_number);


--
-- Name: student_payment_suspense_resolutions uk_student_payment_suspense_resolution; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_suspense_resolutions
    ADD CONSTRAINT uk_student_payment_suspense_resolution UNIQUE (payment_id);


--
-- Name: idx_application_payment_provider_attempts_reference; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_application_payment_provider_attempts_reference ON public.application_payment_provider_attempts USING btree (payment_reference_id, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_application_payment_references_applicant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_application_payment_references_applicant ON public.application_payment_references USING btree (applicant_keycloak_user_id, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_application_payments_reconciliation; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_application_payments_reconciliation ON public.application_payments USING btree (status, rating_status, paid_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: idx_exchange_rates_effective_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_exchange_rates_effective_lookup ON public.exchange_rates USING btree (source_currency_code, base_currency_code, effective_from DESC) WHERE (((status)::text = 'ACTIVE'::text) AND (deleted_at IS NULL));


--
-- Name: idx_finance_billing_event_approval_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_billing_event_approval_queue ON public.finance_billing_events USING btree (status, submitted_at, event_number) WHERE (deleted_at IS NULL);


--
-- Name: idx_finance_billing_event_scope_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_billing_event_scope_lookup ON public.finance_billing_event_scopes USING btree (scope_dimension, reference_id, reference_code) WHERE (deleted_at IS NULL);


--
-- Name: idx_finance_billing_event_source; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_billing_event_source ON public.finance_billing_events USING btree (source_aggregate_type, source_aggregate_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_finance_billing_event_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_billing_event_student ON public.finance_billing_events USING btree (student_finance_account_id, effective_at) WHERE (deleted_at IS NULL);


--
-- Name: idx_finance_billing_policy_source_effective; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_billing_policy_source_effective ON public.finance_billing_policies USING btree (source_event_type, effective_from, effective_until) WHERE (((status)::text = 'ACTIVE'::text) AND (deleted_at IS NULL));


--
-- Name: idx_finance_credit_note_invoice; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_credit_note_invoice ON public.finance_credit_notes USING btree (invoice_id, credit_note_date);


--
-- Name: idx_finance_credit_note_line_invoice_line; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_credit_note_line_invoice_line ON public.finance_credit_note_lines USING btree (invoice_line_id);


--
-- Name: idx_finance_fee_catalogue_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_fee_catalogue_status ON public.finance_fee_catalogues USING btree (status, charge_type) WHERE (deleted_at IS NULL);


--
-- Name: idx_finance_fee_rule_effective_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_fee_rule_effective_lookup ON public.finance_fee_rules USING btree (fee_catalogue_id, effective_from, effective_until) WHERE (((status)::text = 'APPROVED'::text) AND (deleted_at IS NULL));


--
-- Name: idx_finance_fee_rule_rating_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_fee_rule_rating_queue ON public.finance_fee_rules USING btree (transaction_currency_code, effective_from) WHERE (((status)::text = 'PENDING_RATE'::text) AND (deleted_at IS NULL));


--
-- Name: idx_finance_fee_rule_scope_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_fee_rule_scope_lookup ON public.finance_fee_rule_scopes USING btree (scope_dimension, reference_id, reference_code) WHERE (deleted_at IS NULL);


--
-- Name: idx_finance_fee_structure_attachment_resolution; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_fee_structure_attachment_resolution ON public.finance_fee_structure_attachments USING btree (programme_id, academic_period_id, programme_period_number) WHERE (deleted_at IS NULL);


--
-- Name: idx_finance_fee_structure_resolution; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_fee_structure_resolution ON public.finance_fee_structures USING btree (fee_context, programme_level_id, programme_level_code, scope_type, effective_from, effective_until) WHERE (((status)::text = 'ACTIVE'::text) AND (deleted_at IS NULL));


--
-- Name: idx_finance_inbox_processed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_inbox_processed_at ON public.integration_inbox USING btree (processed_at);


--
-- Name: idx_finance_invoice_line_posting_accounts; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_invoice_line_posting_accounts ON public.finance_invoice_lines USING btree (receivable_account_code, revenue_account_code);


--
-- Name: idx_finance_invoice_student_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_invoice_student_date ON public.finance_invoices USING btree (student_finance_account_id, invoice_date, invoice_number) WHERE (deleted_at IS NULL);


--
-- Name: idx_finance_outbox_dispatch; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_outbox_dispatch ON public.integration_outbox USING btree (next_attempt_at, occurred_at) WHERE ((status)::text = 'PENDING'::text);


--
-- Name: idx_finance_student_discount_programme_period_resolution; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_student_discount_programme_period_resolution ON public.finance_student_discount_rule_programme_periods USING btree (discount_rule_programme_id, programme_period_number) WHERE (deleted_at IS NULL);


--
-- Name: idx_finance_student_discount_programme_resolution; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_student_discount_programme_resolution ON public.finance_student_discount_rule_programmes USING btree (programme_id, discount_rule_id) WHERE (deleted_at IS NULL);


--
-- Name: idx_finance_student_discount_resolution; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_finance_student_discount_resolution ON public.finance_student_discount_rules USING btree (status, programme_level_id, programme_level_code, programme_study_level, programme_id, academic_unit_id, target_type, fee_catalogue_id, effective_from, effective_until) WHERE (deleted_at IS NULL);


--
-- Name: idx_student_account_payment_account; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_account_payment_account ON public.student_account_payments USING btree (student_finance_account_id, paid_at) WHERE (deleted_at IS NULL);


--
-- Name: idx_student_account_payment_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_account_payment_queue ON public.student_account_payments USING btree (reconciliation_status, rating_status, paid_at) WHERE (deleted_at IS NULL);


--
-- Name: idx_student_payment_allocation_invoice; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_payment_allocation_invoice ON public.student_payment_allocations USING btree (invoice_id);


--
-- Name: idx_student_payment_allocation_payment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_student_payment_allocation_payment ON public.student_payment_allocations USING btree (payment_id);


--
-- Name: uk_finance_fee_catalogue_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_finance_fee_catalogue_code ON public.finance_fee_catalogues USING btree (lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_finance_fee_structure_attachment_period; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_finance_fee_structure_attachment_period ON public.finance_fee_structure_attachments USING btree (fee_structure_id, programme_id, academic_period_id, programme_period_number) WHERE (deleted_at IS NULL);


--
-- Name: uk_finance_fee_structure_line_number; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_finance_fee_structure_line_number ON public.finance_fee_rules USING btree (fee_structure_id, structure_line_number) WHERE ((fee_structure_id IS NOT NULL) AND (deleted_at IS NULL));


--
-- Name: uk_finance_student_discount_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_finance_student_discount_code ON public.finance_student_discount_rules USING btree (lower((code)::text)) WHERE (deleted_at IS NULL);


--
-- Name: uk_finance_student_discount_programme_period; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_finance_student_discount_programme_period ON public.finance_student_discount_rule_programme_periods USING btree (discount_rule_programme_id, programme_period_number) WHERE (deleted_at IS NULL);


--
-- Name: uk_finance_student_discount_rule_programme; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_finance_student_discount_rule_programme ON public.finance_student_discount_rule_programmes USING btree (discount_rule_id, programme_id) WHERE (deleted_at IS NULL);


--
-- Name: exchange_rates trg_exchange_rate_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_exchange_rate_governance BEFORE INSERT OR DELETE OR UPDATE ON public.exchange_rates FOR EACH ROW EXECUTE FUNCTION public.enforce_exchange_rate_governance();


--
-- Name: finance_billing_events trg_finance_billing_event_discount_evidence; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_billing_event_discount_evidence BEFORE INSERT OR UPDATE ON public.finance_billing_events FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_billing_event_discount_evidence();


--
-- Name: finance_billing_events trg_finance_billing_event_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_billing_event_governance BEFORE INSERT OR DELETE OR UPDATE ON public.finance_billing_events FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_billing_event_governance();


--
-- Name: finance_billing_event_scopes trg_finance_billing_event_scope_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_billing_event_scope_governance BEFORE INSERT OR DELETE OR UPDATE ON public.finance_billing_event_scopes FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_billing_event_scope_governance();


--
-- Name: finance_billing_policies trg_finance_billing_policy_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_billing_policy_governance BEFORE INSERT OR DELETE OR UPDATE ON public.finance_billing_policies FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_billing_policy_governance();


--
-- Name: finance_credit_notes trg_finance_credit_note; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_credit_note BEFORE INSERT OR DELETE OR UPDATE ON public.finance_credit_notes FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_credit_note();


--
-- Name: finance_credit_note_lines trg_finance_credit_note_line; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_credit_note_line BEFORE INSERT OR DELETE OR UPDATE ON public.finance_credit_note_lines FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_credit_note_line();


--
-- Name: finance_fee_catalogues trg_finance_fee_catalogue_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_fee_catalogue_governance BEFORE DELETE OR UPDATE ON public.finance_fee_catalogues FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_fee_catalogue_governance();


--
-- Name: finance_fee_rules trg_finance_fee_rule_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_fee_rule_governance BEFORE INSERT OR DELETE OR UPDATE ON public.finance_fee_rules FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_fee_rule_governance();


--
-- Name: finance_fee_rule_scopes trg_finance_fee_rule_scope_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_fee_rule_scope_governance BEFORE INSERT OR DELETE OR UPDATE ON public.finance_fee_rule_scopes FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_fee_rule_scope_governance();


--
-- Name: finance_fee_structure_attachments trg_finance_fee_structure_attachment_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_fee_structure_attachment_governance BEFORE INSERT OR DELETE OR UPDATE ON public.finance_fee_structure_attachments FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_fee_structure_attachment_governance();


--
-- Name: finance_fee_structures trg_finance_fee_structure_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_fee_structure_governance BEFORE DELETE OR UPDATE ON public.finance_fee_structures FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_fee_structure_governance();


--
-- Name: finance_invoices trg_finance_invoice_discount_totals; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_invoice_discount_totals BEFORE INSERT ON public.finance_invoices FOR EACH ROW EXECUTE FUNCTION public.normalize_finance_invoice_discount_totals();


--
-- Name: finance_invoices trg_finance_invoice_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_invoice_governance BEFORE INSERT OR DELETE OR UPDATE ON public.finance_invoices FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_invoice_governance();


--
-- Name: finance_invoice_lines trg_finance_invoice_line_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_invoice_line_governance BEFORE INSERT OR DELETE OR UPDATE ON public.finance_invoice_lines FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_invoice_line_governance();


--
-- Name: finance_student_discount_rules trg_finance_student_discount_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_student_discount_governance BEFORE DELETE OR UPDATE ON public.finance_student_discount_rules FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_student_discount_governance();


--
-- Name: finance_student_discount_rule_programmes trg_finance_student_discount_programme_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_student_discount_programme_governance BEFORE INSERT OR DELETE OR UPDATE ON public.finance_student_discount_rule_programmes FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_student_discount_programme_governance();


--
-- Name: finance_student_discount_rule_programme_periods trg_finance_student_discount_programme_period_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_finance_student_discount_programme_period_governance BEFORE INSERT OR DELETE OR UPDATE ON public.finance_student_discount_rule_programme_periods FOR EACH ROW EXECUTE FUNCTION public.enforce_finance_student_discount_programme_period_governance();


--
-- Name: student_payment_allocation_reversals trg_student_allocation_reversal; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_student_allocation_reversal BEFORE INSERT OR DELETE OR UPDATE ON public.student_payment_allocation_reversals FOR EACH ROW EXECUTE FUNCTION public.enforce_student_allocation_reversal();


--
-- Name: student_finance_accounts trg_student_finance_account_identity_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_student_finance_account_identity_immutable BEFORE UPDATE ON public.student_finance_accounts FOR EACH ROW EXECUTE FUNCTION public.prevent_student_finance_account_identity_mutation();


--
-- Name: student_finance_accounts trg_student_finance_account_status_transition; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_student_finance_account_status_transition BEFORE UPDATE OF status ON public.student_finance_accounts FOR EACH ROW EXECUTE FUNCTION public.enforce_student_finance_account_status_transition();


--
-- Name: student_payment_allocations trg_student_payment_allocation; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_student_payment_allocation BEFORE INSERT OR DELETE OR UPDATE ON public.student_payment_allocations FOR EACH ROW EXECUTE FUNCTION public.enforce_student_payment_allocation();


--
-- Name: student_account_payments trg_student_payment_governance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_student_payment_governance BEFORE INSERT OR DELETE OR UPDATE ON public.student_account_payments FOR EACH ROW EXECUTE FUNCTION public.enforce_student_payment_governance();


--
-- Name: student_payment_receipts trg_student_payment_receipt; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_student_payment_receipt BEFORE INSERT OR DELETE OR UPDATE ON public.student_payment_receipts FOR EACH ROW EXECUTE FUNCTION public.enforce_student_payment_receipt();


--
-- Name: student_payment_reversals trg_student_payment_reversal; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_student_payment_reversal BEFORE INSERT OR DELETE OR UPDATE ON public.student_payment_reversals FOR EACH ROW EXECUTE FUNCTION public.enforce_student_payment_reversal();


--
-- Name: student_payment_suspense_resolutions trg_student_payment_suspense_resolution; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_student_payment_suspense_resolution BEFORE INSERT OR DELETE OR UPDATE ON public.student_payment_suspense_resolutions FOR EACH ROW EXECUTE FUNCTION public.enforce_student_payment_suspense_resolution();


--
-- Name: finance_credit_notes trg_validate_finance_credit_note; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_validate_finance_credit_note AFTER INSERT OR UPDATE ON public.finance_credit_notes DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.validate_finance_credit_note();


--
-- Name: finance_invoices trg_validate_finance_posted_invoice; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_validate_finance_posted_invoice AFTER INSERT ON public.finance_invoices DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.validate_finance_posted_invoice();


--
-- Name: application_payment_provider_attempts_aud application_payment_provider_attempts_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_provider_attempts_aud
    ADD CONSTRAINT application_payment_provider_attempts_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_payment_provider_attempts application_payment_provider_attempts_payment_reference_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_provider_attempts
    ADD CONSTRAINT application_payment_provider_attempts_payment_reference_id_fkey FOREIGN KEY (payment_reference_id) REFERENCES public.application_payment_references(id);


--
-- Name: application_payment_references_aud application_payment_references_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_references_aud
    ADD CONSTRAINT application_payment_references_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_payment_references application_payment_references_exchange_rate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payment_references
    ADD CONSTRAINT application_payment_references_exchange_rate_id_fkey FOREIGN KEY (exchange_rate_id) REFERENCES public.exchange_rates(id);


--
-- Name: application_payments_aud application_payments_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payments_aud
    ADD CONSTRAINT application_payments_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: application_payments application_payments_exchange_rate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payments
    ADD CONSTRAINT application_payments_exchange_rate_id_fkey FOREIGN KEY (exchange_rate_id) REFERENCES public.exchange_rates(id);


--
-- Name: application_payments application_payments_payment_reference_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.application_payments
    ADD CONSTRAINT application_payments_payment_reference_id_fkey FOREIGN KEY (payment_reference_id) REFERENCES public.application_payment_references(id);


--
-- Name: exchange_rates_aud exchange_rates_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.exchange_rates_aud
    ADD CONSTRAINT exchange_rates_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_billing_event_scopes_aud finance_billing_event_scopes_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_event_scopes_aud
    ADD CONSTRAINT finance_billing_event_scopes_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_billing_event_scopes finance_billing_event_scopes_billing_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_event_scopes
    ADD CONSTRAINT finance_billing_event_scopes_billing_event_id_fkey FOREIGN KEY (billing_event_id) REFERENCES public.finance_billing_events(id);


--
-- Name: finance_billing_events_aud finance_billing_events_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_events_aud
    ADD CONSTRAINT finance_billing_events_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_billing_events finance_billing_events_discount_rule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_events
    ADD CONSTRAINT finance_billing_events_discount_rule_id_fkey FOREIGN KEY (discount_rule_id) REFERENCES public.finance_student_discount_rules(id);


--
-- Name: finance_billing_events finance_billing_events_exchange_rate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_events
    ADD CONSTRAINT finance_billing_events_exchange_rate_id_fkey FOREIGN KEY (exchange_rate_id) REFERENCES public.exchange_rates(id);


--
-- Name: finance_billing_events finance_billing_events_fee_catalogue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_events
    ADD CONSTRAINT finance_billing_events_fee_catalogue_id_fkey FOREIGN KEY (fee_catalogue_id) REFERENCES public.finance_fee_catalogues(id);


--
-- Name: finance_billing_events finance_billing_events_fee_rule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_events
    ADD CONSTRAINT finance_billing_events_fee_rule_id_fkey FOREIGN KEY (fee_rule_id) REFERENCES public.finance_fee_rules(id);


--
-- Name: finance_billing_events finance_billing_events_student_finance_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_events
    ADD CONSTRAINT finance_billing_events_student_finance_account_id_fkey FOREIGN KEY (student_finance_account_id) REFERENCES public.student_finance_accounts(id);


--
-- Name: finance_billing_policies_aud finance_billing_policies_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_policies_aud
    ADD CONSTRAINT finance_billing_policies_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_billing_policies finance_billing_policies_fee_catalogue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_billing_policies
    ADD CONSTRAINT finance_billing_policies_fee_catalogue_id_fkey FOREIGN KEY (fee_catalogue_id) REFERENCES public.finance_fee_catalogues(id);


--
-- Name: finance_credit_note_lines_aud finance_credit_note_lines_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_credit_note_lines_aud
    ADD CONSTRAINT finance_credit_note_lines_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_credit_note_lines finance_credit_note_lines_credit_note_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_credit_note_lines
    ADD CONSTRAINT finance_credit_note_lines_credit_note_id_fkey FOREIGN KEY (credit_note_id) REFERENCES public.finance_credit_notes(id);


--
-- Name: finance_credit_note_lines finance_credit_note_lines_invoice_line_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_credit_note_lines
    ADD CONSTRAINT finance_credit_note_lines_invoice_line_id_fkey FOREIGN KEY (invoice_line_id) REFERENCES public.finance_invoice_lines(id);


--
-- Name: finance_credit_notes_aud finance_credit_notes_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_credit_notes_aud
    ADD CONSTRAINT finance_credit_notes_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_credit_notes finance_credit_notes_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_credit_notes
    ADD CONSTRAINT finance_credit_notes_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.finance_invoices(id);


--
-- Name: finance_fee_catalogues_aud finance_fee_catalogues_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_catalogues_aud
    ADD CONSTRAINT finance_fee_catalogues_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_fee_rule_scopes_aud finance_fee_rule_scopes_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_rule_scopes_aud
    ADD CONSTRAINT finance_fee_rule_scopes_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_fee_rule_scopes finance_fee_rule_scopes_fee_rule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_rule_scopes
    ADD CONSTRAINT finance_fee_rule_scopes_fee_rule_id_fkey FOREIGN KEY (fee_rule_id) REFERENCES public.finance_fee_rules(id);


--
-- Name: finance_fee_rules_aud finance_fee_rules_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_rules_aud
    ADD CONSTRAINT finance_fee_rules_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_fee_rules finance_fee_rules_exchange_rate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_rules
    ADD CONSTRAINT finance_fee_rules_exchange_rate_id_fkey FOREIGN KEY (exchange_rate_id) REFERENCES public.exchange_rates(id);


--
-- Name: finance_fee_rules finance_fee_rules_fee_catalogue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_rules
    ADD CONSTRAINT finance_fee_rules_fee_catalogue_id_fkey FOREIGN KEY (fee_catalogue_id) REFERENCES public.finance_fee_catalogues(id);


--
-- Name: finance_fee_rules finance_fee_rules_fee_structure_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_rules
    ADD CONSTRAINT finance_fee_rules_fee_structure_id_fkey FOREIGN KEY (fee_structure_id) REFERENCES public.finance_fee_structures(id);


--
-- Name: finance_fee_structure_attachments_aud finance_fee_structure_attachments_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_structure_attachments_aud
    ADD CONSTRAINT finance_fee_structure_attachments_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_fee_structure_attachments finance_fee_structure_attachments_fee_structure_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_structure_attachments
    ADD CONSTRAINT finance_fee_structure_attachments_fee_structure_id_fkey FOREIGN KEY (fee_structure_id) REFERENCES public.finance_fee_structures(id);


--
-- Name: finance_fee_structures_aud finance_fee_structures_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_fee_structures_aud
    ADD CONSTRAINT finance_fee_structures_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_invoice_lines_aud finance_invoice_lines_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoice_lines_aud
    ADD CONSTRAINT finance_invoice_lines_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_invoice_lines finance_invoice_lines_billing_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoice_lines
    ADD CONSTRAINT finance_invoice_lines_billing_event_id_fkey FOREIGN KEY (billing_event_id) REFERENCES public.finance_billing_events(id);


--
-- Name: finance_invoice_lines finance_invoice_lines_discount_rule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoice_lines
    ADD CONSTRAINT finance_invoice_lines_discount_rule_id_fkey FOREIGN KEY (discount_rule_id) REFERENCES public.finance_student_discount_rules(id);


--
-- Name: finance_invoice_lines finance_invoice_lines_exchange_rate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoice_lines
    ADD CONSTRAINT finance_invoice_lines_exchange_rate_id_fkey FOREIGN KEY (exchange_rate_id) REFERENCES public.exchange_rates(id);


--
-- Name: finance_invoice_lines finance_invoice_lines_fee_catalogue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoice_lines
    ADD CONSTRAINT finance_invoice_lines_fee_catalogue_id_fkey FOREIGN KEY (fee_catalogue_id) REFERENCES public.finance_fee_catalogues(id);


--
-- Name: finance_invoice_lines finance_invoice_lines_fee_rule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoice_lines
    ADD CONSTRAINT finance_invoice_lines_fee_rule_id_fkey FOREIGN KEY (fee_rule_id) REFERENCES public.finance_fee_rules(id);


--
-- Name: finance_invoice_lines finance_invoice_lines_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoice_lines
    ADD CONSTRAINT finance_invoice_lines_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.finance_invoices(id);


--
-- Name: finance_invoices_aud finance_invoices_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoices_aud
    ADD CONSTRAINT finance_invoices_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_invoices finance_invoices_student_finance_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_invoices
    ADD CONSTRAINT finance_invoices_student_finance_account_id_fkey FOREIGN KEY (student_finance_account_id) REFERENCES public.student_finance_accounts(id);


--
-- Name: finance_receipts finance_receipts_application_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_receipts
    ADD CONSTRAINT finance_receipts_application_payment_id_fkey FOREIGN KEY (application_payment_id) REFERENCES public.application_payments(id);


--
-- Name: finance_receipts_aud finance_receipts_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_receipts_aud
    ADD CONSTRAINT finance_receipts_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_student_discount_rule_programme_periods finance_student_discount_rule_p_discount_rule_programme_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_student_discount_rule_programme_periods
    ADD CONSTRAINT finance_student_discount_rule_p_discount_rule_programme_id_fkey FOREIGN KEY (discount_rule_programme_id) REFERENCES public.finance_student_discount_rule_programmes(id);


--
-- Name: finance_student_discount_rule_programme_periods_aud finance_student_discount_rule_programme_periods_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_student_discount_rule_programme_periods_aud
    ADD CONSTRAINT finance_student_discount_rule_programme_periods_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_student_discount_rule_programmes_aud finance_student_discount_rule_programmes_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_student_discount_rule_programmes_aud
    ADD CONSTRAINT finance_student_discount_rule_programmes_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_student_discount_rule_programmes finance_student_discount_rule_programmes_discount_rule_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_student_discount_rule_programmes
    ADD CONSTRAINT finance_student_discount_rule_programmes_discount_rule_id_fkey FOREIGN KEY (discount_rule_id) REFERENCES public.finance_student_discount_rules(id);


--
-- Name: finance_student_discount_rules_aud finance_student_discount_rules_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_student_discount_rules_aud
    ADD CONSTRAINT finance_student_discount_rules_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: finance_student_discount_rules finance_student_discount_rules_fee_catalogue_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.finance_student_discount_rules
    ADD CONSTRAINT finance_student_discount_rules_fee_catalogue_id_fkey FOREIGN KEY (fee_catalogue_id) REFERENCES public.finance_fee_catalogues(id);


--
-- Name: student_account_payments_aud student_account_payments_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_account_payments_aud
    ADD CONSTRAINT student_account_payments_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_account_payments student_account_payments_exchange_rate_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_account_payments
    ADD CONSTRAINT student_account_payments_exchange_rate_id_fkey FOREIGN KEY (exchange_rate_id) REFERENCES public.exchange_rates(id);


--
-- Name: student_account_payments student_account_payments_student_finance_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_account_payments
    ADD CONSTRAINT student_account_payments_student_finance_account_id_fkey FOREIGN KEY (student_finance_account_id) REFERENCES public.student_finance_accounts(id);


--
-- Name: student_finance_accounts_aud student_finance_accounts_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_finance_accounts_aud
    ADD CONSTRAINT student_finance_accounts_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_payment_allocation_reversals student_payment_allocation_reversals_allocation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_allocation_reversals
    ADD CONSTRAINT student_payment_allocation_reversals_allocation_id_fkey FOREIGN KEY (allocation_id) REFERENCES public.student_payment_allocations(id);


--
-- Name: student_payment_allocation_reversals_aud student_payment_allocation_reversals_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_allocation_reversals_aud
    ADD CONSTRAINT student_payment_allocation_reversals_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_payment_allocations_aud student_payment_allocations_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_allocations_aud
    ADD CONSTRAINT student_payment_allocations_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_payment_allocations student_payment_allocations_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_allocations
    ADD CONSTRAINT student_payment_allocations_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.finance_invoices(id);


--
-- Name: student_payment_allocations student_payment_allocations_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_allocations
    ADD CONSTRAINT student_payment_allocations_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.student_account_payments(id);


--
-- Name: student_payment_receipts_aud student_payment_receipts_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_receipts_aud
    ADD CONSTRAINT student_payment_receipts_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_payment_receipts student_payment_receipts_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_receipts
    ADD CONSTRAINT student_payment_receipts_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.student_account_payments(id);


--
-- Name: student_payment_receipts student_payment_receipts_student_finance_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_receipts
    ADD CONSTRAINT student_payment_receipts_student_finance_account_id_fkey FOREIGN KEY (student_finance_account_id) REFERENCES public.student_finance_accounts(id);


--
-- Name: student_payment_reversals_aud student_payment_reversals_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_reversals_aud
    ADD CONSTRAINT student_payment_reversals_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_payment_reversals student_payment_reversals_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_reversals
    ADD CONSTRAINT student_payment_reversals_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.student_account_payments(id);


--
-- Name: student_payment_suspense_resolutions student_payment_suspense_resolu_student_finance_account_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_suspense_resolutions
    ADD CONSTRAINT student_payment_suspense_resolu_student_finance_account_id_fkey FOREIGN KEY (student_finance_account_id) REFERENCES public.student_finance_accounts(id);


--
-- Name: student_payment_suspense_resolutions_aud student_payment_suspense_resolutions_aud_rev_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_suspense_resolutions_aud
    ADD CONSTRAINT student_payment_suspense_resolutions_aud_rev_fkey FOREIGN KEY (rev) REFERENCES public.revinfo(rev);


--
-- Name: student_payment_suspense_resolutions student_payment_suspense_resolutions_payment_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_payment_suspense_resolutions
    ADD CONSTRAINT student_payment_suspense_resolutions_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.student_account_payments(id);


--
-- PostgreSQL database dump complete
--


