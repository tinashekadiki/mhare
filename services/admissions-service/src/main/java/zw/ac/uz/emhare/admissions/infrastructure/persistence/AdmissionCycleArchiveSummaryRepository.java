package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycleArchiveSummary;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AdmissionCycleArchiveSummaryRepository extends JpaRepository<AdmissionCycleArchiveSummary, UUID> {
    Optional<AdmissionCycleArchiveSummary> findByAdmissionCycleId(UUID admissionCycleId);
}
