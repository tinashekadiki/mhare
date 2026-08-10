package zw.ac.uz.emhare.finance.collections;

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
class FinanceCollectionsMigrationTest {
    @Container static final PostgreSQLContainer POSTGRESQL=new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).withDatabaseName("emhare_finance").withUsername("emhare_service").withPassword("emhare_test_password");
    private Connection connection;
    @BeforeAll static void migrate(){Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(),POSTGRESQL.getUsername(),POSTGRESQL.getPassword()).locations("classpath:db/migration").load().migrate();}
    @BeforeEach void connect() throws SQLException {connection=DriverManager.getConnection(POSTGRESQL.getJdbcUrl(),POSTGRESQL.getUsername(),POSTGRESQL.getPassword());connection.setAutoCommit(false);}
    @AfterEach void rollback() throws SQLException {if(connection!=null){connection.rollback();connection.close();}}

    @Test void createsCollectionsCorrectionsAndAuditTables() throws SQLException {assertCount(16,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('student_account_payments','student_payment_suspense_resolutions','student_payment_receipts','student_payment_allocations','student_payment_allocation_reversals','student_payment_reversals','finance_credit_notes','finance_credit_note_lines','student_account_payments_aud','student_payment_suspense_resolutions_aud','student_payment_receipts_aud','student_payment_allocations_aud','student_payment_allocation_reversals_aud','student_payment_reversals_aud','finance_credit_notes_aud','finance_credit_note_lines_aud')");}

    @Test
    void enforcesIndependentNonOverlappingExchangeRateApproval() throws SQLException {
        UUID selfApprovalPreparer = UUID.randomUUID();
        UUID selfApprovalRate = insertDraftRate(selfApprovalPreparer, "2027-01-01T00:00:00Z", "2027-06-01T00:00:00Z");
        SQLException selfApprovalFailure = assertThrows(SQLException.class, () -> execute(
                "UPDATE exchange_rates SET status='ACTIVE',approved_by_user_id=?,approved_at=now(),approval_reason='Self approved',updated_at=now() WHERE id=?",
                selfApprovalPreparer, selfApprovalRate));
        assertEquals("23514", selfApprovalFailure.getSQLState());
        connection.rollback();

        UUID independentlyPreparedRate = insertDraftRate(UUID.randomUUID(), "2027-01-01T00:00:00Z", "2027-06-01T00:00:00Z");
        execute(
                "UPDATE exchange_rates SET status='ACTIVE',approved_by_user_id=?,approved_at=now(),approval_reason='Independent approval',updated_at=now() WHERE id=?",
                UUID.randomUUID(), independentlyPreparedRate);
        UUID conflictingRate = insertDraftRate(UUID.randomUUID(), "2027-02-01T00:00:00Z", "2027-04-01T00:00:00Z");
        SQLException overlapFailure = assertThrows(SQLException.class, () -> execute(
                "UPDATE exchange_rates SET status='ACTIVE',approved_by_user_id=?,approved_at=now(),approval_reason='Overlapping approval',updated_at=now() WHERE id=?",
                UUID.randomUUID(), conflictingRate));
        assertEquals("P0001", overlapFailure.getSQLState());
    }

    @Test
    void blocksSelfReconciliationAndUnratedReconciliation() throws SQLException {
        UUID captureActor = UUID.randomUUID();
        UUID unratedPayment = insertPayment(null, captureActor, "ZWG", null, null, "UNRATED");
        SQLException unratedFailure = assertThrows(SQLException.class, () -> execute(
                "UPDATE student_account_payments SET reconciliation_status='RECONCILED',reconciled_by_user_id=?,reconciled_at=now(),reconciliation_reason='Premature',updated_at=now() WHERE id=?",
                UUID.randomUUID(), unratedPayment));
        assertEquals("23514", unratedFailure.getSQLState());
        connection.rollback();

        UUID selfReconciledPayment = insertPayment(null, captureActor, "USD", null, 100, "RATED");
        SQLException selfReconciliationFailure = assertThrows(SQLException.class, () -> execute(
                "UPDATE student_account_payments SET reconciliation_status='RECONCILED',reconciled_by_user_id=?,reconciled_at=now(),reconciliation_reason='Self reconciliation',updated_at=now() WHERE id=?",
                captureActor, selfReconciledPayment));
        assertEquals("23514", selfReconciliationFailure.getSQLState());
    }

    @Test
    void ratesAnUnratedPaymentLaterWithoutChangingProviderEvidence() throws SQLException {
        UUID paymentId = insertPayment(null, UUID.randomUUID(), "ZWG", null, null, "UNRATED");
        UUID activeRateId = insertDraftRate(UUID.randomUUID(), "2020-01-01T00:00:00Z", "2030-01-01T00:00:00Z");
        execute(
                "UPDATE exchange_rates SET status='ACTIVE',approved_by_user_id=?,approved_at=now(),approval_reason='Treasury approval',updated_at=now() WHERE id=?",
                UUID.randomUUID(), activeRateId);

        UUID incorrectlyRatedPaymentId = paymentId;
        UUID incorrectConversionRateId = activeRateId;
        SQLException incorrectConversion = assertThrows(SQLException.class, () -> execute(
                "UPDATE student_account_payments SET exchange_rate_id=?,base_amount=99,rating_status='RATED',rating_applied_by_user_id=?,rating_applied_at=now(),updated_at=now() WHERE id=?",
                incorrectConversionRateId, UUID.randomUUID(), incorrectlyRatedPaymentId));
        assertEquals("P0001", incorrectConversion.getSQLState());
        connection.rollback();

        paymentId = insertPayment(null, UUID.randomUUID(), "ZWG", null, null, "UNRATED");
        activeRateId = insertDraftRate(UUID.randomUUID(), "2020-01-01T00:00:00Z", "2030-01-01T00:00:00Z");
        execute(
                "UPDATE exchange_rates SET status='ACTIVE',approved_by_user_id=?,approved_at=now(),approval_reason='Treasury approval',updated_at=now() WHERE id=?",
                UUID.randomUUID(), activeRateId);
        execute(
                "UPDATE student_account_payments SET exchange_rate_id=?,base_amount=4,rating_status='RATED',rating_applied_by_user_id=?,rating_applied_at=now(),updated_at=now() WHERE id=?",
                activeRateId, UUID.randomUUID(), paymentId);

        UUID ratedPaymentId = paymentId;
        SQLException providerEvidenceMutation = assertThrows(SQLException.class, () -> execute(
                "UPDATE student_account_payments SET provider_transaction_reference='REWRITTEN',updated_at=now() WHERE id=?",
                ratedPaymentId));
        assertEquals("P0001", providerEvidenceMutation.getSQLState());
    }

    @Test
    void boundsAllocationsAndRequiresIndependentReversal() throws SQLException {
        CollectionFixture excessiveAllocationFixture = postedInvoiceFixture();
        UUID captureActor = UUID.randomUUID();
        UUID excessiveAllocationPayment = insertPayment(excessiveAllocationFixture.accountId(), captureActor, "USD", null, 100, "RATED");
        UUID reconciler = UUID.randomUUID();
        execute(
                "UPDATE student_account_payments SET reconciliation_status='RECONCILED',reconciled_by_user_id=?,reconciled_at=now(),reconciliation_reason='Bank statement reconciled',updated_at=now() WHERE id=?",
                reconciler, excessiveAllocationPayment);
        execute(
                "INSERT INTO student_payment_receipts(id,payment_id,receipt_number,student_finance_account_id,payer_name,transaction_currency_code,transaction_amount,base_currency_code,base_amount,issued_by_user_id,issued_at,created_at,updated_at,version) VALUES (?,?,?,?,'Test payer','USD',100,'USD',100,?,now(),now(),now(),0)",
                UUID.randomUUID(), excessiveAllocationPayment, "RCT-TEST", excessiveAllocationFixture.accountId(), reconciler);
        SQLException excessiveAllocationFailure = assertThrows(SQLException.class, () -> insertAllocation(
                excessiveAllocationPayment, excessiveAllocationFixture.invoiceId(), 101, UUID.randomUUID()));
        assertEquals("P0001", excessiveAllocationFailure.getSQLState());
        connection.rollback();

        CollectionFixture reversalFixture = postedInvoiceFixture();
        UUID reversalPayment = insertPayment(reversalFixture.accountId(), captureActor, "USD", null, 100, "RATED");
        execute(
                "UPDATE student_account_payments SET reconciliation_status='RECONCILED',reconciled_by_user_id=?,reconciled_at=now(),reconciliation_reason='Reconciled',updated_at=now() WHERE id=?",
                reconciler, reversalPayment);
        UUID allocator = UUID.randomUUID();
        UUID allocationId = insertAllocation(reversalPayment, reversalFixture.invoiceId(), 75, allocator);
        SQLException selfReversalFailure = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO student_payment_allocation_reversals(id,reversal_number,allocation_id,reversed_by_user_id,reversed_at,reversal_reason,created_at,updated_at,version) VALUES (?,?,?,?,now(),'Self reversal',now(),now(),0)",
                UUID.randomUUID(), "REV-SELF", allocationId, allocator));
        assertEquals("P0001", selfReversalFailure.getSQLState());
        connection.rollback();
    }

    @Test
    void postsBalancedImmutableCreditNotes() throws SQLException {
        CollectionFixture fixture = postedInvoiceFixture();
        UUID noteId = UUID.randomUUID();
        UUID preparer = UUID.randomUUID();
        execute(
                "INSERT INTO finance_credit_notes(id,credit_note_number,invoice_id,transaction_currency_code,transaction_amount,base_currency_code,base_amount,credit_note_date,status,prepared_by_user_id,prepared_at,preparation_reason,created_at,updated_at,version) VALUES (?,?,?,'USD',100,'USD',100,current_date,'DRAFT',?,now(),'Charge correction submitted',now(),now(),0)",
                noteId, "CRN-TEST", fixture.invoiceId(), preparer);
        execute(
                "INSERT INTO finance_credit_note_lines(id,credit_note_id,line_number,invoice_line_id,transaction_amount,base_amount,reason,created_at,updated_at,version) VALUES (?,?,1,?,100,100,'Approved charge correction',now(),now(),0)",
                UUID.randomUUID(), noteId, fixture.invoiceLineId());
        execute(
                "UPDATE finance_credit_notes SET status='POSTED',posted_by_user_id=?,posted_at=now(),posting_reason='Independent approval',updated_at=now() WHERE id=?",
                UUID.randomUUID(), noteId);
        connection.commit();
        connection.setAutoCommit(false);
        SQLException immutable = assertThrows(SQLException.class, () -> execute(
                "UPDATE finance_credit_notes SET transaction_amount=99,updated_at=now() WHERE id=?", noteId));
        assertEquals("P0001", immutable.getSQLState());
    }

    private CollectionFixture postedInvoiceFixture() throws SQLException {UUID studentId=UUID.randomUUID();String studentNumber="R"+studentId.toString().substring(0,7);UUID accountId=UUID.randomUUID();execute("INSERT INTO student_finance_accounts(id,account_number,student_id,student_number,user_id,source_offer_id,primary_email,base_currency_code,status,opened_at,created_at,updated_at,version) VALUES (?,?,?,?,?,?,?,'USD','ACTIVE',now(),now(),now(),0)",accountId,"SFA-"+studentId.toString().substring(0,8),studentId,studentNumber,UUID.randomUUID(),UUID.randomUUID(),studentNumber+"@test");UUID catalogueId=UUID.randomUUID();UUID preparer=UUID.randomUUID();String feeCode="FEE-"+catalogueId.toString().substring(0,8);execute("INSERT INTO finance_fee_catalogues(id,code,name,charge_type,receivable_account_code,revenue_account_code,base_currency_code,status,prepared_by_user_id,activated_by_user_id,activated_at,activation_reason,created_at,updated_at,version) VALUES (?,?,'Tuition','PROGRAMME','1100-AR','4100-REV','USD','ACTIVE',?,?,now(),'Approved',now(),now(),0)",catalogueId,feeCode,preparer,UUID.randomUUID());UUID ruleId=UUID.randomUUID();execute("INSERT INTO finance_fee_rules(id,fee_catalogue_id,rule_version,transaction_currency_code,transaction_amount,base_currency_code,base_amount,rating_status,effective_from,effective_until,status,prepared_by_user_id,created_at,updated_at,version) VALUES (?,?,1,'USD',250,'USD',250,'RATED',now()-interval '1 day',now()+interval '30 days','DRAFT',?,now(),now(),0)",ruleId,catalogueId,UUID.randomUUID());execute("INSERT INTO finance_fee_rule_scopes(id,fee_rule_id,scope_dimension,created_at,updated_at,version) VALUES (?,?,'GLOBAL',now(),now(),0)",UUID.randomUUID(),ruleId);execute("UPDATE finance_fee_rules SET status='APPROVED',approved_by_user_id=?,approved_at=now(),approval_reason='Approved',updated_at=now() WHERE id=?",UUID.randomUUID(),ruleId);UUID eventId=UUID.randomUUID();execute("INSERT INTO finance_billing_events(id,event_number,source_service,source_event_type,source_event_id,source_aggregate_type,source_aggregate_id,source_line_reference,student_finance_account_id,student_id,student_number,fee_catalogue_id,fee_rule_id,description,quantity,transaction_currency_code,transaction_unit_amount,transaction_amount,base_currency_code,base_unit_amount,base_amount,effective_at,status,prepared_by_user_id,submitted_at,created_at,updated_at,version) VALUES (?,?, 'test','test.event.v1',?,'TEST',?,'LINE',?,?,?,?,?,'Tuition',1,'USD',250,250,'USD',250,250,now(),'PENDING_APPROVAL',?,now(),now(),now(),0)",eventId,"BLE-"+eventId.toString().substring(0,8),UUID.randomUUID(),UUID.randomUUID(),accountId,studentId,studentNumber,catalogueId,ruleId,UUID.randomUUID());execute("UPDATE finance_billing_events SET status='APPROVED',approved_by_user_id=?,approved_at=now(),approval_reason='Approved',updated_at=now() WHERE id=?",UUID.randomUUID(),eventId);UUID invoiceId=UUID.randomUUID();execute("INSERT INTO finance_invoices(id,invoice_number,student_finance_account_id,student_id,student_number,transaction_currency_code,base_currency_code,gross_transaction_amount,gross_base_amount,invoice_date,due_date,status,posted_by_user_id,posted_at,posting_reason,created_at,updated_at,version) VALUES (?,?,?,?,?,'USD','USD',250,250,current_date,current_date+30,'POSTED',?,now(),'Posted',now(),now(),0)",invoiceId,"INV-"+invoiceId.toString().substring(0,8),accountId,studentId,studentNumber,UUID.randomUUID());UUID lineId=UUID.randomUUID();execute("INSERT INTO finance_invoice_lines(id,invoice_id,line_number,billing_event_id,fee_catalogue_id,fee_rule_id,fee_code,description,quantity,transaction_currency_code,transaction_unit_amount,transaction_amount,base_currency_code,base_unit_amount,base_amount,receivable_account_code,revenue_account_code,created_at,updated_at,version) VALUES (?,?,1,?,?,?,?, 'Tuition',1,'USD',250,250,'USD',250,250,'1100-AR','4100-REV',now(),now(),0)",lineId,invoiceId,eventId,catalogueId,ruleId,feeCode);execute("UPDATE finance_billing_events SET status='INVOICED',invoiced_at=now(),updated_at=now() WHERE id=?",eventId);connection.commit();connection.setAutoCommit(false);return new CollectionFixture(accountId,invoiceId,lineId);}
    private UUID insertDraftRate(UUID preparer,String from,String until) throws SQLException {UUID id=UUID.randomUUID();execute("INSERT INTO exchange_rates(id,source_currency_code,base_currency_code,rate_to_base,effective_from,effective_to,source_name,source_reference,status,prepared_by_user_id,created_at,updated_at,version) VALUES (?,'ZWG','USD',0.04,?::timestamptz,?::timestamptz,'RBZ','TEST','DRAFT',?,now(),now(),0)",id,from,until,preparer);return id;}
    private UUID insertPayment(UUID accountId,UUID captureActor,String currency,UUID rateId,Integer baseAmount,String ratingStatus) throws SQLException {UUID id=UUID.randomUUID();UUID ratingActor="RATED".equals(ratingStatus)?captureActor:null;Timestamp ratingTimestamp=ratingActor==null?null:Timestamp.from(java.time.Instant.now());execute("INSERT INTO student_account_payments(id,payment_number,student_finance_account_id,payer_name,provider_code,provider_transaction_reference,payment_channel,transaction_currency_code,transaction_amount,base_currency_code,exchange_rate_id,base_amount,rating_status,rating_applied_by_user_id,rating_applied_at,paid_at,provider_event_fingerprint,reconciliation_status,captured_by_user_id,captured_at,created_at,updated_at,version) VALUES (?,?,?,'Test payer','BANK',?,'BANK_TRANSFER',?,100,'USD',?,?,?,?,?,now(),?,'PENDING',?,now(),now(),now(),0)",id,"PAY-"+id.toString().substring(0,8),accountId,"TX-"+id,currency,rateId,baseAmount,ratingStatus,ratingActor,ratingTimestamp,"FP-"+id,captureActor);return id;}
    private UUID insertAllocation(UUID paymentId,UUID invoiceId,int amount,UUID allocator) throws SQLException {UUID id=UUID.randomUUID();execute("INSERT INTO student_payment_allocations(id,allocation_number,payment_id,invoice_id,transaction_currency_code,transaction_amount,base_currency_code,payment_base_amount,invoice_base_amount,realised_exchange_difference,allocated_by_user_id,allocated_at,allocation_reason,created_at,updated_at,version) VALUES (?,?,?,?,'USD',?,'USD',?,?,0,?,now(),'Invoice allocation',now(),now(),0)",id,"ALL-"+id.toString().substring(0,8),paymentId,invoiceId,amount,amount,amount,allocator);return id;}
    private void assertCount(int expected,String sql) throws SQLException {try(Statement statement=connection.createStatement();ResultSet result=statement.executeQuery(sql)){result.next();assertEquals(expected,result.getInt(1));}}
    private void execute(String sql,Object... values) throws SQLException {try(PreparedStatement statement=connection.prepareStatement(sql)){for(int index=0;index<values.length;index++)statement.setObject(index+1,values[index]);statement.executeUpdate();}}
    private record CollectionFixture(UUID accountId,UUID invoiceId,UUID invoiceLineId) {}
}
