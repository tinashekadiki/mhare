package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdmissionCycleRepository extends JpaRepository<AdmissionCycle, UUID> {
    Optional<AdmissionCycle> findByIntakeIdAndDeletedAtIsNull(UUID intakeId);
}
