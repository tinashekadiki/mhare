package zw.ac.uz.emhare.examstimetabling;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;
import zw.ac.uz.emhare.common.messaging.StudentRegistrationConfirmedEvent;
import zw.ac.uz.emhare.common.messaging.StudentRegistrationConfirmedEvent.RegisteredModule;
import zw.ac.uz.emhare.common.persistence.AuditableEntity;

/** Explicit confirmed-registration evidence shared by exam workflow tests. @author Tinashe K */
public final class ExamTestData {
  public static final Instant NOW = Instant.parse("2026-08-30T08:00:00Z");
  public static final LocalDate START = LocalDate.of(2026, 8, 30);
  public static final UUID PERIOD = UUID.randomUUID();
  public static final UUID ACTOR = UUID.randomUUID();

  private ExamTestData() {}

  public static <T extends AuditableEntity> T identified(T record) {
    if (record.getId() == null) ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
    return record;
  }

  public static RegisteredModule module(UUID id, String code) {
    return new RegisteredModule(
        UUID.randomUUID(),
        UUID.randomUUID(),
        id,
        code,
        code + " module",
        "COMPULSORY",
        BigDecimal.TEN,
        new BigDecimal("50"));
  }

  public static final class RegistrationEvidence {
    public UUID eventId = UUID.randomUUID();
    public int schemaVersion = StudentRegistrationConfirmedEvent.CURRENT_SCHEMA_VERSION;
    public UUID registrationId = UUID.randomUUID();
    public UUID studentId = UUID.randomUUID();
    public String studentNumber = " R260001 ";
    public UUID enrolmentId = UUID.randomUUID();
    public UUID programmeId = UUID.randomUUID();
    public UUID programmeVersionId = UUID.randomUUID();
    public UUID periodId = PERIOD;
    public String periodCode = " 2026-S2 ";
    public String periodName = " Semester Two ";
    public LocalDate startsOn = START.minusMonths(1);
    public LocalDate endsOn = START.plusMonths(3);
    public List<RegisteredModule> modules = List.of(module(UUID.randomUUID(), "CSC101"));

    public StudentRegistrationConfirmedEvent event() {
      return new StudentRegistrationConfirmedEvent(
          eventId,
          schemaVersion,
          NOW,
          registrationId,
          studentId,
          studentNumber,
          enrolmentId,
          programmeId,
          programmeVersionId,
          null,
          null,
          null,
          null,
          null,
          null,
          periodId,
          periodCode,
          periodName,
          startsOn,
          endsOn,
          1,
          modules);
    }
  }
}
