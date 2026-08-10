package zw.ac.uz.emhare.dining;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import zw.ac.uz.emhare.dining.operations.*;
import zw.ac.uz.emhare.dining.operations.DiningOperationsContracts.*;
import zw.ac.uz.emhare.dining.setup.*;
import zw.ac.uz.emhare.dining.setup.DiningSetupContracts.*;

/** @author Tinashe K */
@Testcontainers
@SpringBootTest(properties={"spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:65535/test-jwks","spring.rabbitmq.listener.simple.auto-startup=false"})
class DiningLifecycleIntegrationTest {
    private static final UUID MAKER=UUID.fromString("40000000-0000-4000-8000-000000000001");
    private static final UUID CHECKER=UUID.fromString("40000000-0000-4000-8000-000000000002");
    private static final UUID ATTENDANT=UUID.fromString("40000000-0000-4000-8000-000000000003");
    private static final UUID SUPERVISOR=UUID.fromString("40000000-0000-4000-8000-000000000004");
    @Container static final PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:18-alpine").withDatabaseName("emhare_dining_lifecycle").withUsername("emhare_service").withPassword("emhare_test_password");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).locations("classpath:db/migration").load().migrate();registry.add("spring.datasource.url",postgres::getJdbcUrl);registry.add("spring.datasource.username",postgres::getUsername);registry.add("spring.datasource.password",postgres::getPassword);}
    @Autowired DiningSetupService setup; @Autowired DiningOperationsService operations;
    @Test void persistsEntitlementAdmissionReversalAndReconciliationWithEvidence(){
        LocalDate today=LocalDate.now();Instant now=Instant.now();
        HallSummary hall=setup.createHall(new CreateHall("MAIN","Main Dining Hall","Main campus",100));
        MealOptionSummary meal=setup.createOption(new CreateMealOption("LUNCH","Lunch","Midday meal",MealOption.Category.LUNCH));
        setup.createTime(new CreateServiceTime(hall.id(),meal.id(),today.getDayOfWeek().getValue(),LocalTime.of(11,30),LocalTime.of(14,0),LocalTime.of(14,15)));
        HallAssignmentRuleSummary routingRule=setup.createAssignmentRule(new CreateHallAssignmentRule(
                hall.id(),DiningHallAssignmentRule.Dimension.SURNAME_PREFIX,
                DiningHallAssignmentRule.Operator.STARTS_WITH,"A-M",10));
        AttendantAssignmentSummary attendantAssignment=setup.createAttendantAssignment(new CreateAttendantAssignment(
                hall.id(),ATTENDANT,"STAFF-001","Dining Attendant",today,null,DiningAttendantAssignment.Role.ATTENDANT));
        PlanSummary plan=setup.createPlan(new CreatePlan("FULL",1,"Full board","Seven-day meal plan",UUID.randomUUID(),today.minusDays(1),today.plusMonths(6)),MAKER);
        setup.addPlanMeal(plan.id(),new AddPlanMeal(meal.id(),1,true,true,true,true,true,true,true));
        plan=setup.transitionPlan(plan.id(),new PlanTransition(DiningPlan.Status.ACTIVE,"Finance fee and meal entitlement verified",plan.version()),CHECKER);
        UUID studentId=UUID.randomUUID();
        AssignmentSummary assignment=operations.prepareAssignment(new PrepareAssignment(studentId,"R271234A","Example Student",UUID.randomUUID(),"2027-S1","BACC","YEAR-1",hall.id(),plan.id(),null,today,today.plusMonths(4)),MAKER);
        assignment=operations.assignmentAction(assignment.id(),"activate",new AssignmentAction("Registration, plan, and dining hall eligibility verified",assignment.version()),CHECKER);
        DietarySummary dietary=operations.recordDietary(new RecordDietaryRequirement(studentId,"R271234A","PEANUT_ALLERGY","Severe peanut allergy",StudentDietaryRequirement.Severity.CRITICAL,UUID.randomUUID(),today,null),MAKER);
        SessionSummary session=operations.planSession(new PlanMealSession(hall.id(),meal.id(),today,now.minusSeconds(1800),now.plusSeconds(3600),80),MAKER);
        session=operations.sessionAction(session.id(),"open",new SessionAction("Kitchen readiness and attendant controls verified",session.version()),ATTENDANT);
        CaptureAttendance capture=new CaptureAttendance(session.id(),studentId,"R271234A","Example Student",MealAttendanceEvent.CaptureChannel.ONLINE,"GATE-01","scan-0001");
        AttendanceSummary admitted=operations.captureAttendance(capture,ATTENDANT);
        assertEquals(MealAttendanceEvent.Outcome.ADMITTED,admitted.outcome());
        assertEquals(admitted.id(),operations.captureAttendance(capture,ATTENDANT).id());
        ReversalSummary reversal=operations.reverseAttendance(admitted.id(),new ReverseAttendance("CAPTURE_ERROR","Wrong identity token selected"),SUPERVISOR);
        assertEquals(admitted.id(),reversal.attendanceEventId());
        session=operations.sessionAction(session.id(),"close",new SessionAction("Service window ended and gates secured",session.version()),ATTENDANT);
        session=operations.reconcileSession(session.id(),new ReconcileSession(0,"Physical count reconciled to the reversed admission",session.version()),SUPERVISOR);
        dietary=operations.resolveDietary(dietary.id(),new ResolveDietaryRequirement(StudentDietaryRequirement.Status.RESOLVED,"Clinical restriction formally withdrawn",dietary.version()),CHECKER);
        OperationsRegister register=operations.register();
        assertEquals(MealServiceSession.Status.RECONCILED,session.status());assertEquals(0,session.netAdmitted());
        assertEquals(StudentDiningAssignment.Status.ACTIVE,register.assignments().getFirst().status());
        assertEquals(StudentDietaryRequirement.Status.RESOLVED,dietary.status());assertEquals(1,register.reversals().size());
        assertEquals(8,register.workflowEvents().size());
        assertTrue(register.attendanceStatistics().stream().anyMatch(item->item.dimension().equals("PROGRAMME")&&item.groupCode().equals("BACC")&&item.admitted()==1&&item.netAdmitted()==0));
        SetupRegister setupRegister=setup.register();
        assertEquals(routingRule.id(),setupRegister.hallAssignmentRules().getFirst().id());
        assertEquals(attendantAssignment.id(),setupRegister.attendantAssignments().getFirst().id());
    }
}
