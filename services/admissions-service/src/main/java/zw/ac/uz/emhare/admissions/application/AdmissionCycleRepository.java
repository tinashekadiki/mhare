package zw.ac.uz.emhare.admissions.application;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionCycleRepository extends JpaRepository<AdmissionCycle, UUID> {
    Optional<AdmissionCycle> findByIntakeIdAndDeletedAtIsNull(UUID intakeId);
}
