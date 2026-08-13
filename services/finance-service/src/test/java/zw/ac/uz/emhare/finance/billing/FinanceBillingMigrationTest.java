package zw.ac.uz.emhare.finance.billing;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** @author Tinashe K */
@Testcontainers
class FinanceBillingMigrationTest {
    @Container static final PostgreSQLContainer POSTGRESQL=new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_finance").withUsername("emhare_service").withPassword("emhare_test_password");
    private Connection connection;
    @BeforeAll static void migrate(){Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(),POSTGRESQL.getUsername(),POSTGRESQL.getPassword()).locations("classpath:db/migration").load().migrate();}
    @BeforeEach void connect() throws SQLException {connection=DriverManager.getConnection(POSTGRESQL.getJdbcUrl(),POSTGRESQL.getUsername(),POSTGRESQL.getPassword());connection.setAutoCommit(false);}
    @AfterEach void rollback() throws SQLException {if(connection!=null){connection.rollback();connection.close();}}

    @Test void createsBillingInvoiceAndAuditTables() throws SQLException {
        assertCount(8,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('finance_billing_events','finance_billing_event_scopes','finance_invoices','finance_invoice_lines','finance_billing_events_aud','finance_billing_event_scopes_aud','finance_invoices_aud','finance_invoice_lines_aud')");
    }

    @Test void createsGovernedBillingPolicyAndAuditTables() throws SQLException {
        assertCount(2,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('finance_billing_policies','finance_billing_policies_aud')");
    }

    @Test void rejectsSelfActivationAndOverlappingRegistrationPolicies() throws SQLException {
        BillingFixture fixture=fixture(true);UUID selfApprover=UUID.randomUUID();UUID selfApprovedPolicyId=insertDraftPolicy(fixture.catalogueId(),"REGISTRATION-TUITION",1,selfApprover);
        SQLException selfApproval=assertThrows(SQLException.class,()->execute("UPDATE finance_billing_policies SET status='ACTIVE',activated_by_user_id=?,activated_at=now(),activation_reason='Self approval',updated_at=now() WHERE id=?",selfApprover,selfApprovedPolicyId));
        assertEquals("23514",selfApproval.getSQLState());connection.rollback();
        fixture=fixture(true);UUID preparer=UUID.randomUUID();UUID first=insertDraftPolicy(fixture.catalogueId(),"REGISTRATION-TUITION",1,preparer);
        execute("UPDATE finance_billing_policies SET status='ACTIVE',activated_by_user_id=?,activated_at=now(),activation_reason='Independent policy approval',updated_at=now() WHERE id=?",UUID.randomUUID(),first);
        UUID second=insertDraftPolicy(fixture.catalogueId(),"REGISTRATION-TUITION",2,UUID.randomUUID());
        UUID overlappingPolicyId=second;
        SQLException overlap=assertThrows(SQLException.class,()->execute("UPDATE finance_billing_policies SET status='ACTIVE',activated_by_user_id=?,activated_at=now(),activation_reason='Conflicting policy approval',updated_at=now() WHERE id=?",UUID.randomUUID(),overlappingPolicyId));
        assertEquals("P0001",overlap.getSQLState());
    }

    @Test void rejectsBillingAgainstUnapprovedPricing() throws SQLException {
        BillingFixture fixture=fixture(false);UUID eventId=UUID.randomUUID();
        SQLException failure=assertThrows(SQLException.class,()->insertBillingEvent(eventId,fixture,UUID.randomUUID()));
        assertEquals("P0001",failure.getSQLState());
    }

    @Test void requiresIndependentBillingApproval() throws SQLException {
        BillingFixture fixture=fixture(true);UUID preparer=UUID.randomUUID();UUID eventId=UUID.randomUUID();insertBillingEvent(eventId,fixture,preparer);
        execute("INSERT INTO finance_billing_event_scopes(id,billing_event_id,scope_dimension,created_at,updated_at,version) VALUES (?,?,'GLOBAL',now(),now(),0)",UUID.randomUUID(),eventId);
        SQLException failure=assertThrows(SQLException.class,()->execute("UPDATE finance_billing_events SET status='APPROVED',approved_by_user_id=?,approved_at=now(),approval_reason='Self approved',updated_at=now() WHERE id=?",preparer,eventId));
        assertEquals("23514",failure.getSQLState());
    }

    @Test void postsOnlyReconciledImmutableInvoiceEvidence() throws SQLException {
        BillingFixture fixture=fixture(true);UUID eventId=UUID.randomUUID();insertBillingEvent(eventId,fixture,UUID.randomUUID());
        execute("INSERT INTO finance_billing_event_scopes(id,billing_event_id,scope_dimension,created_at,updated_at,version) VALUES (?,?,'GLOBAL',now(),now(),0)",UUID.randomUUID(),eventId);
        execute("UPDATE finance_billing_events SET status='APPROVED',approved_by_user_id=?,approved_at=now(),approval_reason='Independent source and price check',updated_at=now() WHERE id=?",UUID.randomUUID(),eventId);
        UUID invoiceId=UUID.randomUUID();
        execute("INSERT INTO finance_invoices(id,invoice_number,student_finance_account_id,student_id,student_number,transaction_currency_code,base_currency_code,gross_transaction_amount,gross_base_amount,invoice_date,due_date,status,posted_by_user_id,posted_at,posting_reason,created_at,updated_at,version) VALUES (?,?,?,?,?,'USD','USD',250,250,current_date,current_date+30,'POSTED',?,now(),'Approved billing evidence posted',now(),now(),0)",invoiceId,"INV-TEST-1",fixture.accountId(),fixture.studentId(),fixture.studentNumber(),UUID.randomUUID());
        execute("INSERT INTO finance_invoice_lines(id,invoice_id,line_number,billing_event_id,fee_catalogue_id,fee_rule_id,fee_code,description,quantity,transaction_currency_code,transaction_unit_amount,transaction_amount,base_currency_code,base_unit_amount,base_amount,receivable_account_code,revenue_account_code,created_at,updated_at,version) VALUES (?,?,1,?,?,?,?, 'Registration tuition',2,'USD',125,250,'USD',125,250,'1100-AR','4100-TUITION',now(),now(),0)",UUID.randomUUID(),invoiceId,eventId,fixture.catalogueId(),fixture.ruleId(),fixture.feeCode());
        execute("UPDATE finance_billing_events SET status='INVOICED',invoiced_at=now(),updated_at=now() WHERE id=?",eventId);
        connection.commit();
        connection.setAutoCommit(false);
        SQLException immutable=assertThrows(SQLException.class,()->execute("UPDATE finance_invoices SET gross_base_amount=249,updated_at=now() WHERE id=?",invoiceId));
        assertEquals("P0001",immutable.getSQLState());
    }

    @Test void rejectsPostedInvoiceWhoseTotalsDoNotReconcile() throws SQLException {
        BillingFixture fixture=fixture(true);UUID eventId=UUID.randomUUID();insertBillingEvent(eventId,fixture,UUID.randomUUID());
        execute("UPDATE finance_billing_events SET status='APPROVED',approved_by_user_id=?,approved_at=now(),approval_reason='Independent approval',updated_at=now() WHERE id=?",UUID.randomUUID(),eventId);
        UUID invoiceId=UUID.randomUUID();
        execute("INSERT INTO finance_invoices(id,invoice_number,student_finance_account_id,student_id,student_number,transaction_currency_code,base_currency_code,gross_transaction_amount,gross_base_amount,invoice_date,due_date,status,posted_by_user_id,posted_at,posting_reason,created_at,updated_at,version) VALUES (?,?,?,?,?,'USD','USD',251,250,current_date,current_date,'POSTED',?,now(),'Deliberately mismatched total',now(),now(),0)",invoiceId,"INV-TEST-MISMATCH",fixture.accountId(),fixture.studentId(),fixture.studentNumber(),UUID.randomUUID());
        execute("INSERT INTO finance_invoice_lines(id,invoice_id,line_number,billing_event_id,fee_catalogue_id,fee_rule_id,fee_code,description,quantity,transaction_currency_code,transaction_unit_amount,transaction_amount,base_currency_code,base_unit_amount,base_amount,receivable_account_code,revenue_account_code,created_at,updated_at,version) VALUES (?,?,1,?,?,?,?, 'Registration tuition',2,'USD',125,250,'USD',125,250,'1100-AR','4100-TUITION',now(),now(),0)",UUID.randomUUID(),invoiceId,eventId,fixture.catalogueId(),fixture.ruleId(),fixture.feeCode());
        execute("UPDATE finance_billing_events SET status='INVOICED',invoiced_at=now(),updated_at=now() WHERE id=?",eventId);
        SQLException failure=assertThrows(SQLException.class,connection::commit);
        assertEquals("P0001",failure.getSQLState());
    }

    private BillingFixture fixture(boolean approveRule) throws SQLException {
        UUID studentId=UUID.randomUUID();String studentNumber="R"+studentId.toString().substring(0,7);UUID accountId=UUID.randomUUID();
        execute("INSERT INTO student_finance_accounts(id,account_number,student_id,student_number,user_id,source_offer_id,primary_email,base_currency_code,status,opened_at,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,'USD','ACTIVE',now(),now(),now(),0)",accountId,studentNumber,studentId,studentNumber,UUID.randomUUID(),UUID.randomUUID(),studentNumber+"@example.test");
        UUID preparer=UUID.randomUUID();UUID catalogueId=UUID.randomUUID();
        String feeCode="TUITION-"+catalogueId.toString().substring(0,8);execute("INSERT INTO finance_fee_catalogues(id,code,name,charge_type,receivable_account_code,revenue_account_code,base_currency_code,status,prepared_by_user_id,activated_by_user_id,activated_at,activation_reason,created_at,updated_at,version) VALUES (?,?,'Registration tuition','PROGRAMME','1100-AR','4100-TUITION','USD','ACTIVE',?,?,now(),'Accounts verified',now(),now(),0)",catalogueId,feeCode,preparer,UUID.randomUUID());
        UUID ruleId=UUID.randomUUID();
        execute("INSERT INTO finance_fee_rules(id,fee_catalogue_id,rule_version,transaction_currency_code,transaction_amount,base_currency_code,base_amount,rating_status,effective_from,effective_until,status,prepared_by_user_id,created_at,updated_at,version) VALUES (?,?,1,'USD',125,'USD',125,'RATED',now()-interval '1 day',now()+interval '30 days','DRAFT',?,now(),now(),0)",ruleId,catalogueId,UUID.randomUUID());
        execute("INSERT INTO finance_fee_rule_scopes(id,fee_rule_id,scope_dimension,created_at,updated_at,version) VALUES (?,?,'GLOBAL',now(),now(),0)",UUID.randomUUID(),ruleId);
        if(approveRule)execute("UPDATE finance_fee_rules SET status='APPROVED',approved_by_user_id=?,approved_at=now(),approval_reason='Independent rule approval',updated_at=now() WHERE id=?",UUID.randomUUID(),ruleId);
        return new BillingFixture(accountId,studentId,studentNumber,catalogueId,ruleId,feeCode);
    }
    private void insertBillingEvent(UUID eventId,BillingFixture fixture,UUID preparer) throws SQLException {execute("INSERT INTO finance_billing_events(id,event_number,source_service,source_event_type,source_event_id,source_aggregate_type,source_aggregate_id,source_line_reference,student_finance_account_id,student_id,student_number,fee_catalogue_id,fee_rule_id,description,quantity,transaction_currency_code,transaction_unit_amount,transaction_amount,base_currency_code,base_unit_amount,base_amount,effective_at,status,prepared_by_user_id,submitted_at,created_at,updated_at,version) VALUES (?,?, 'student-records-service','student-records.registration-confirmed.v1',?,'REGISTRATION',?,'PROGRAMME',?,?,?,?,?,'Registration tuition',2,'USD',125,250,'USD',125,250,now(),'PENDING_APPROVAL',?,now(),now(),now(),0)",eventId,"BLE-"+eventId.toString().substring(0,8),UUID.randomUUID(),UUID.randomUUID(),fixture.accountId(),fixture.studentId(),fixture.studentNumber(),fixture.catalogueId(),fixture.ruleId(),preparer);}
    private UUID insertDraftPolicy(UUID catalogueId,String code,int version,UUID preparer) throws SQLException {UUID id=UUID.randomUUID();execute("INSERT INTO finance_billing_policies(id,code,policy_version,name,source_event_type,fee_catalogue_id,line_basis,quantity_basis,fixed_quantity,effective_from,effective_until,status,prepared_by_user_id,created_at,updated_at,version) VALUES (?,?,?,'Registration tuition policy','student-records.registration-confirmed.v1',?,'REGISTRATION','FIXED',1,now()-interval '1 day',now()+interval '30 days','DRAFT',?,now(),now(),0)",id,code,version,catalogueId,preparer);return id;}
    private void assertCount(int expected,String sql) throws SQLException {try(Statement statement=connection.createStatement();ResultSet result=statement.executeQuery(sql)){result.next();assertEquals(expected,result.getInt(1));}}
    private void execute(String sql,Object... values) throws SQLException {try(PreparedStatement statement=connection.prepareStatement(sql)){for(int index=0;index<values.length;index++)statement.setObject(index+1,values[index]);statement.executeUpdate();}}
    private record BillingFixture(UUID accountId,UUID studentId,String studentNumber,UUID catalogueId,UUID ruleId,String feeCode) {}
}
