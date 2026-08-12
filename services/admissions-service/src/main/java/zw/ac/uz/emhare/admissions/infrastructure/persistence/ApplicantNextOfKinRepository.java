package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.ApplicantNextOfKin;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ApplicantNextOfKinRepository extends JpaRepository<ApplicantNextOfKin, UUID> {
    List<ApplicantNextOfKin> findAllByApplicantIdAndDeletedAtIsNullOrderByPrimaryDescFullNameAsc(UUID applicantId);
    Optional<ApplicantNextOfKin> findByIdAndApplicantIdAndDeletedAtIsNull(UUID id, UUID applicantId);
    long countByApplicantIdAndDeletedAtIsNull(UUID applicantId);
}
