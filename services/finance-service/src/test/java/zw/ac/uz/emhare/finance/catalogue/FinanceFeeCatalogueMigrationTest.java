package zw.ac.uz.emhare.finance.catalogue;

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
class FinanceFeeCatalogueMigrationTest {
    @Container static final PostgreSQLContainer POSTGRESQL=new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"))
            .withDatabaseName("emhare_finance").withUsername("emhare_service").withPassword("emhare_test_password");
    private Connection connection;
    @BeforeAll static void migrate(){Flyway.configure().dataSource(POSTGRESQL.getJdbcUrl(),POSTGRESQL.getUsername(),POSTGRESQL.getPassword()).locations("classpath:db/migration").load().migrate();}
    @BeforeEach void connect() throws SQLException {connection=DriverManager.getConnection(POSTGRESQL.getJdbcUrl(),POSTGRESQL.getUsername(),POSTGRESQL.getPassword());connection.setAutoCommit(false);}
    @AfterEach void rollback() throws SQLException {if(connection!=null){connection.rollback();connection.close();}}

    @Test void createsGovernedFeeCatalogueRuleScopeAndAuditTables() throws SQLException {
        assertCount(6,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('finance_fee_catalogues','finance_fee_rules','finance_fee_rule_scopes','finance_fee_catalogues_aud','finance_fee_rules_aud','finance_fee_rule_scopes_aud')");
    }

    @Test void createsHierarchicalFeeStructureAndAuditEvidence() throws SQLException {
        assertCount(2,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('finance_fee_structures','finance_fee_structures_aud')");
        assertCount(3,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='finance_fee_rules' AND column_name IN ('fee_structure_id','structure_line_number','structure_line_description')");
        assertCount(6,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('finance_fee_structures','finance_fee_structures_aud') AND column_name IN ('programme_level_id','programme_level_code','programme_level_name')");
    }

    @Test void rejectsApplicationFeesAtProgrammeScope() throws SQLException {
        UUID actor=UUID.randomUUID();
        SQLException applicationScope=assertThrows(SQLException.class,()->execute("INSERT INTO finance_fee_structures(id,code,name,fee_context,scope_type,scope_reference_id,scope_reference_name,programme_level_id,programme_level_code,programme_level_name,transaction_currency_code,effective_from,status,prepared_by_user_id,created_at,updated_at,version) VALUES (?,?,?,'APPLICATION','PROGRAMME',?,?,?,?,?,'USD',now(),'DRAFT',?,now(),now(),0)",UUID.randomUUID(),"APP-WRONG","Application fee",UUID.randomUUID(),"Programme",UUID.randomUUID(),"UG","Undergraduate",actor));
        assertEquals("23514",applicationScope.getSQLState());
    }

    @Test void acceptsProgrammeLevelApplicationFeesAndRejectsUnknownApplicantCategories() throws SQLException {
        UUID actor=UUID.randomUUID();
        UUID programmeLevelId=UUID.randomUUID();
        execute("INSERT INTO finance_fee_structures(id,code,name,fee_context,scope_type,scope_reference_id,scope_reference_code,scope_reference_name,programme_level_id,programme_level_code,programme_level_name,applicant_category_code,transaction_currency_code,effective_from,status,prepared_by_user_id,created_at,updated_at,version) VALUES (?,?,?,'APPLICATION','PROGRAMME_LEVEL',?,?,?,?,?,?,'LOCAL','USD',now(),'DRAFT',?,now(),now(),0)",UUID.randomUUID(),"APP-UG-LOCAL","Local undergraduate application",programmeLevelId,"UG","Undergraduate",programmeLevelId,"UG","Undergraduate",actor);
        SQLException applicantCategory=assertThrows(SQLException.class,()->execute("INSERT INTO finance_fee_structures(id,code,name,fee_context,scope_type,scope_reference_id,scope_reference_code,scope_reference_name,programme_level_id,programme_level_code,programme_level_name,applicant_category_code,transaction_currency_code,effective_from,status,prepared_by_user_id,created_at,updated_at,version) VALUES (?,?,?,'APPLICATION','PROGRAMME_LEVEL',?,?,?,?,?,?,'UNKNOWN','USD',now(),'DRAFT',?,now(),now(),0)",UUID.randomUUID(),"APP-UG-UNKNOWN","Unknown applicant category",programmeLevelId,"UG","Undergraduate",programmeLevelId,"UG","Undergraduate",actor));
        assertEquals("23514",applicantCategory.getSQLState());
    }

    @Test void requiresProgrammeLevelOnEveryFeeStructure() throws SQLException {
        UUID actor=UUID.randomUUID();
        SQLException missingProgrammeLevel=assertThrows(SQLException.class,()->execute("INSERT INTO finance_fee_structures(id,code,name,fee_context,scope_type,transaction_currency_code,effective_from,status,prepared_by_user_id,created_at,updated_at,version) VALUES (?,?,?,'ACADEMIC','INSTITUTION','USD',now(),'DRAFT',?,now(),now(),0)",UUID.randomUUID(),"UNCLASSIFIED","Unclassified fees",actor));
        assertEquals("23502",missingProgrammeLevel.getSQLState());
    }

    @Test void rejectsOverlappingStructuresForTheSameProgrammeLevelCode() throws SQLException {
        UUID firstPreparer=UUID.randomUUID();UUID firstApprover=UUID.randomUUID();
        execute("INSERT INTO finance_fee_structures(id,code,name,fee_context,scope_type,programme_level_id,programme_level_code,programme_level_name,transaction_currency_code,effective_from,status,prepared_by_user_id,activated_by_user_id,activated_at,activation_reason,created_at,updated_at,version) VALUES (?,?,?,'ACADEMIC','INSTITUTION',?,?,?,'USD',now(),'ACTIVE',?,?,now(),'Approved',now(),now(),0)",UUID.randomUUID(),"UG-ACTIVE","Active undergraduate fees",UUID.randomUUID(),"UG","Undergraduate",firstPreparer,firstApprover);
        UUID secondStructureId=UUID.randomUUID();UUID secondPreparer=UUID.randomUUID();
        execute("INSERT INTO finance_fee_structures(id,code,name,fee_context,scope_type,programme_level_id,programme_level_code,programme_level_name,transaction_currency_code,effective_from,status,prepared_by_user_id,created_at,updated_at,version) VALUES (?,?,?,'ACADEMIC','INSTITUTION',?,?,?,'USD',now(),'DRAFT',?,now(),now(),0)",secondStructureId,"UG-CONFLICT","Conflicting undergraduate fees",UUID.randomUUID(),"UG","Undergraduate",secondPreparer);
        SQLException overlap=assertThrows(SQLException.class,()->execute("UPDATE finance_fee_structures SET status='ACTIVE',activated_by_user_id=?,activated_at=now(),activation_reason='Conflicting approval',updated_at=now() WHERE id=?",UUID.randomUUID(),secondStructureId));
        assertEquals("P0001",overlap.getSQLState());
    }

    @Test void requiresAccommodationFeesToUseGlobalScope() throws SQLException {
        UUID actor=UUID.randomUUID();
        SQLException accommodationScope=assertThrows(SQLException.class,()->execute("INSERT INTO finance_fee_structures(id,code,name,fee_context,scope_type,scope_reference_id,scope_reference_name,programme_level_id,programme_level_code,programme_level_name,transaction_currency_code,effective_from,status,prepared_by_user_id,created_at,updated_at,version) VALUES (?,?,?,'ACCOMMODATION','ACADEMIC_UNIT',?,?,?,?,?,'USD',now(),'DRAFT',?,now(),now(),0)",UUID.randomUUID(),"ACCOM-WRONG","Accommodation",UUID.randomUUID(),"Faculty",UUID.randomUUID(),"UG","Undergraduate",actor));
        assertEquals("23514",accommodationScope.getSQLState());
    }

    @Test void rejectsCatalogueSelfActivation() throws SQLException {
        UUID actor=UUID.randomUUID();UUID catalogueId=insertDraftCatalogue(actor);
        SQLException exception=assertThrows(SQLException.class,()->execute("UPDATE finance_fee_catalogues SET status='ACTIVE',activated_by_user_id=?,activated_at=now(),activation_reason='Approved',updated_at=now() WHERE id=?",actor,catalogueId));
        assertEquals("23514",exception.getSQLState());
    }

    @Test void preventsUnratedForeignCurrencyRuleFromBecomingBillable() throws SQLException {
        UUID catalogueId=insertActiveCatalogue();UUID ruleId=UUID.randomUUID();UUID preparer=UUID.randomUUID();
        execute("INSERT INTO finance_fee_rules(id,fee_catalogue_id,rule_version,transaction_currency_code,transaction_amount,base_currency_code,rating_status,effective_from,status,prepared_by_user_id,created_at,updated_at,version) VALUES (?,?,1,'ZWG',2500,'USD','UNRATED',now(),'PENDING_RATE',?,now(),now(),0)",ruleId,catalogueId,preparer);
        execute("INSERT INTO finance_fee_rule_scopes(id,fee_rule_id,scope_dimension,created_at,updated_at,version) VALUES (?,?,'GLOBAL',now(),now(),0)",UUID.randomUUID(),ruleId);
        SQLException exception=assertThrows(SQLException.class,()->execute("UPDATE finance_fee_rules SET status='APPROVED',approved_by_user_id=?,approved_at=now(),approval_reason='Approve without rate',updated_at=now() WHERE id=?",UUID.randomUUID(),ruleId));
        assertEquals("P0001",exception.getSQLState());
    }

    @Test void locksApprovedRuleScopes() throws SQLException {
        UUID catalogueId=insertActiveCatalogue();UUID firstRule=insertApprovedGlobalRule(catalogueId,1,100);UUID scopeId=queryUuid("SELECT id FROM finance_fee_rule_scopes WHERE fee_rule_id=?",firstRule);
        SQLException immutableScope=assertThrows(SQLException.class,()->execute("DELETE FROM finance_fee_rule_scopes WHERE id=?",scopeId));
        assertEquals("P0001",immutableScope.getSQLState());
    }

    @Test void rejectsOverlappingApprovedPricesForTheSameScope() throws SQLException {
        UUID catalogueId=insertActiveCatalogue();insertApprovedGlobalRule(catalogueId,1,100);
        UUID secondRule=insertDraftUsdRule(catalogueId,2,125);execute("INSERT INTO finance_fee_rule_scopes(id,fee_rule_id,scope_dimension,created_at,updated_at,version) VALUES (?,?,'GLOBAL',now(),now(),0)",UUID.randomUUID(),secondRule);
        SQLException overlap=assertThrows(SQLException.class,()->execute("UPDATE finance_fee_rules SET status='APPROVED',approved_by_user_id=?,approved_at=now(),approval_reason='Conflicting approval',updated_at=now() WHERE id=?",UUID.randomUUID(),secondRule));
        assertEquals("P0001",overlap.getSQLState());
    }

    private UUID insertDraftCatalogue(UUID preparer) throws SQLException {UUID id=UUID.randomUUID();execute("INSERT INTO finance_fee_catalogues(id,code,name,charge_type,receivable_account_code,revenue_account_code,base_currency_code,status,prepared_by_user_id,created_at,updated_at,version) VALUES (?,?,?,'PROGRAMME','1100-AR','4100-TUITION','USD','DRAFT',?,now(),now(),0)",id,"FEE-"+id.toString().substring(0,8),"Programme tuition",preparer);return id;}
    private UUID insertActiveCatalogue() throws SQLException {UUID preparer=UUID.randomUUID();UUID id=insertDraftCatalogue(preparer);execute("UPDATE finance_fee_catalogues SET status='ACTIVE',activated_by_user_id=?,activated_at=now(),activation_reason='Independent finance approval',updated_at=now() WHERE id=?",UUID.randomUUID(),id);return id;}
    private UUID insertDraftUsdRule(UUID catalogueId,int version,int amount) throws SQLException {UUID id=UUID.randomUUID();execute("INSERT INTO finance_fee_rules(id,fee_catalogue_id,rule_version,transaction_currency_code,transaction_amount,base_currency_code,base_amount,rating_status,effective_from,effective_until,status,prepared_by_user_id,created_at,updated_at,version) VALUES (?,?,?,'USD',?,'USD',?,'RATED',now(),now()+interval '90 days','DRAFT',?,now(),now(),0)",id,catalogueId,version,amount,amount,UUID.randomUUID());return id;}
    private UUID insertApprovedGlobalRule(UUID catalogueId,int version,int amount) throws SQLException {UUID id=insertDraftUsdRule(catalogueId,version,amount);execute("INSERT INTO finance_fee_rule_scopes(id,fee_rule_id,scope_dimension,created_at,updated_at,version) VALUES (?,?,'GLOBAL',now(),now(),0)",UUID.randomUUID(),id);execute("UPDATE finance_fee_rules SET status='APPROVED',approved_by_user_id=?,approved_at=now(),approval_reason='Independent pricing approval',updated_at=now() WHERE id=?",UUID.randomUUID(),id);return id;}
    private UUID queryUuid(String sql,Object... values) throws SQLException {try(PreparedStatement statement=connection.prepareStatement(sql)){for(int index=0;index<values.length;index++)statement.setObject(index+1,values[index]);try(ResultSet result=statement.executeQuery()){assertTrue(result.next());return result.getObject(1,UUID.class);}}}
    private void assertCount(int expected,String sql) throws SQLException {try(Statement statement=connection.createStatement();ResultSet result=statement.executeQuery(sql)){result.next();assertEquals(expected,result.getInt(1));}}
    private void execute(String sql,Object... values) throws SQLException {try(PreparedStatement statement=connection.prepareStatement(sql)){for(int index=0;index<values.length;index++)statement.setObject(index+1,values[index]);statement.executeUpdate();}}
}
