package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionCycleRepository;

import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient;
import zw.ac.uz.emhare.admissions.integration.AcademicSetupCatalogueClient.AcademicAdmissionsIntake;

/**
 * Maintains the private compatibility projection used by existing Admissions
 * relationships while Academic Setup remains the sole intake source of truth.
 *
 * @author Tinashe K
 */
@Service
public class AdmissionsIntakeProjectionService {

    private final AdmissionCycleRepository admissionCycleRepository;
    private final AcademicSetupCatalogueClient academicSetupCatalogueClient;
    private final Clock clock;

    public AdmissionsIntakeProjectionService(
            AdmissionCycleRepository admissionCycleRepository,
            AcademicSetupCatalogueClient academicSetupCatalogueClient,
            Clock clock) {
        this.admissionCycleRepository = admissionCycleRepository;
        this.academicSetupCatalogueClient = academicSetupCatalogueClient;
        this.clock = clock;
    }

    public List<AcademicAdmissionsIntake> openIntakes() {
        return academicSetupCatalogueClient.getOpenAdmissionsIntakes();
    }

    @Transactional
    public ResolvedAdmissionsIntake requireOpenIntake(UUID intakeId) {
        AcademicAdmissionsIntake intake = academicSetupCatalogueClient.getAdmissionsIntake(intakeId);
        LocalDate today = LocalDate.now(clock);
        if (!"OPEN".equals(intake.status())
                || today.isBefore(intake.startsOn())
                || today.isAfter(intake.endsOn())) {
            throw new IllegalStateException("Intake is not open for applications.");
        }
        return synchronizeOpenProjection(intake);
    }

    @Transactional
    public AdmissionCycle requireProjection(UUID intakeId) {
        return admissionCycleRepository.findByIntakeIdAndDeletedAtIsNull(intakeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Admissions activity has not been initialized for this intake."));
    }

    public AcademicAdmissionsIntake requireIntake(UUID intakeId) {
        return academicSetupCatalogueClient.getAdmissionsIntake(intakeId);
    }

    private ResolvedAdmissionsIntake synchronizeOpenProjection(AcademicAdmissionsIntake intake) {
        ZoneId zone = clock.getZone();
        Instant opensAt = intake.startsOn().atStartOfDay(zone).toInstant();
        Instant closesAt = intake.endsOn().plusDays(1).atStartOfDay(zone).minusNanos(1).toInstant();
        AdmissionCycle projection = admissionCycleRepository
                .findByIntakeIdAndDeletedAtIsNull(intake.intakeId())
                .orElseGet(() -> new AdmissionCycle(
                        intake.academicYearId(), intake.intakeId(), intake.code(), intake.name(), opensAt, closesAt));
        projection.synchronizeIntakeProjection(
                intake.academicYearId(), intake.code(), intake.name(), opensAt, closesAt,
                intake.maximumProgrammeChoices());
        projection.synchronizeOpenApplicationWindow();
        AdmissionCycle savedProjection = admissionCycleRepository.saveAndFlush(projection);
        return new ResolvedAdmissionsIntake(savedProjection, intake);
    }

    public record ResolvedAdmissionsIntake(
            AdmissionCycle projection,
            AcademicAdmissionsIntake intake) {
    }
}
