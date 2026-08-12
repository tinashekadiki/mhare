package zw.ac.uz.emhare.accommodation;

import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationApplication;
import zw.ac.uz.emhare.accommodation.operations.domain.model.AccommodationRate;
import zw.ac.uz.emhare.accommodation.operations.domain.model.RoomAllocation;
import zw.ac.uz.emhare.accommodation.operations.domain.model.RoomAllocationEvent;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationApplicationPeriod;
import zw.ac.uz.emhare.accommodation.setup.domain.model.AccommodationRoom;
import zw.ac.uz.emhare.accommodation.setup.domain.model.ResidenceHall;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import zw.ac.uz.emhare.accommodation.operations.*;
import zw.ac.uz.emhare.accommodation.operations.api.model.AccommodationOperationsApiModels.*;
import zw.ac.uz.emhare.accommodation.setup.*;
import zw.ac.uz.emhare.accommodation.setup.api.model.AccommodationSetupApiModels.*;

/** @author Tinashe K */
@Testcontainers
@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:65535/test-jwks",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class AccommodationLifecycleIntegrationTest {
    private static final UUID PREPARER = UUID.fromString("30000000-0000-4000-8000-000000000001");
    private static final UUID APPROVER = UUID.fromString("30000000-0000-4000-8000-000000000002");
    private static final UUID CHECK_IN_OPERATOR = UUID.fromString("30000000-0000-4000-8000-000000000003");
    private static final UUID CHECK_OUT_OPERATOR = UUID.fromString("30000000-0000-4000-8000-000000000004");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine")
            .withDatabaseName("emhare_accommodation_lifecycle")
            .withUsername("emhare_service")
            .withPassword("emhare_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired AccommodationSetupService setupService;
    @Autowired AccommodationOperationsService operationsService;

    @Test
    void persistsTheGovernedApplicationToCheckoutLifecycleWithImmutableEvidence() {
        Instant now = Instant.now();
        LocalDate occupancyStart = LocalDate.now().plusDays(2);
        LocalDate occupancyEnd = occupancyStart.plusMonths(4);

        PremiseSummary premise = setupService.createPremise(new CreatePremise(
                "UZ-MP", "Mount Pleasant", "630 Churchill Avenue", "Mount Pleasant", null, null));
        RoomTypeSummary roomType = setupService.createRoomType(new CreateRoomType(
                "SINGLE", "Single room", "Single resident room", 1));
        ResidenceHallSummary hall = setupService.createResidenceHall(new CreateResidenceHall(
                premise.id(), "SWINTON", "Swinton Hall", ResidenceHall.ResidentGenderPolicy.FEMALE,
                "Residence Warden", "+263 000 000"));
        RoomSummary room = setupService.createRoom(new CreateRoom(hall.id(), roomType.id(), "A-101",
                "First", 1, false, AccommodationRoom.ConditionStatus.AVAILABLE, null, null));
        ApplicationPeriodSummary period = setupService.createApplicationPeriod(new CreateApplicationPeriod(
                UUID.randomUUID(), "2027-S1", "RES-2027-S1", "Semester 1 residences",
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), occupancyStart,
                occupancyEnd, now.plus(2, ChronoUnit.DAYS)), PREPARER);
        period = setupService.transitionApplicationPeriod(period.id(), new PeriodTransition(
                AccommodationApplicationPeriod.Status.APPLICATION_OPEN, "Inventory and dates approved",
                period.version()), APPROVER);

        RateSummary rate = operationsService.createRate(new CreateRate(period.id(), roomType.id(), 1,
                UUID.randomUUID(), "USD", new BigDecimal("950.00"), null, null,
                now.minus(2, ChronoUnit.DAYS), null), PREPARER);
        rate = operationsService.transitionRate(rate.id(), new RateTransition(AccommodationRate.Status.ACTIVE,
                "Finance fee and USD rate verified", rate.version()), APPROVER);

        ApplicationSummary application = operationsService.submitApplication(new SubmitApplication(period.id(),
                UUID.randomUUID(), "R271234A", "Example Student", "student@example.test", "FEMALE",
                null, "ZWE", "HARARE", UUID.randomUUID(), "BSC-CS", "BSc Computer Science", 1,
                null, AccommodationApplication.PaymentState.PAID, roomType.id(), null));
        application = operationsService.evaluateApplication(application.id(), new EvaluateApplication(
                AccommodationApplication.Status.ELIGIBLE, 80, null,
                "Eligibility, payment, and residence policy verified", application.version()), APPROVER);

        period = setupService.transitionApplicationPeriod(period.id(), new PeriodTransition(
                AccommodationApplicationPeriod.Status.APPLICATION_CLOSED, "Application intake closed",
                period.version()), APPROVER);
        setupService.transitionApplicationPeriod(period.id(), new PeriodTransition(
                AccommodationApplicationPeriod.Status.ALLOCATION_ACTIVE, "Allocation controls approved",
                period.version()), APPROVER);

        AllocationSummary allocation = operationsService.proposeAllocation(new ProposeAllocation(application.id(),
                room.id(), rate.id(), occupancyStart, occupancyEnd,
                "Room type, gender policy, capacity, and active rate verified"), PREPARER);
        allocation = operationsService.approveAllocation(allocation.id(), new AllocationAction(
                "Independent allocation review completed", allocation.version()), APPROVER);
        allocation = operationsService.checkIn(allocation.id(), new AllocationAction(
                "Identity, keys, and room inventory verified", allocation.version()), CHECK_IN_OPERATOR);
        allocation = operationsService.checkOut(allocation.id(), new AllocationAction(
                "Independent inspection and key return completed", allocation.version()), CHECK_OUT_OPERATOR);

        OperationsRegister register = operationsService.register();
        assertEquals(RoomAllocation.Status.CHECKED_OUT, allocation.status());
        assertEquals(AccommodationApplication.Status.ALLOCATED, register.applications().getFirst().status());
        assertEquals(4, register.allocationEvents().size());
        assertEquals(RoomAllocationEvent.EventType.CHECKED_OUT, register.allocationEvents().getFirst().eventType());
    }
}
