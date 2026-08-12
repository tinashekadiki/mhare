package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.ApplicantReferee;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ApplicantRefereeRepository extends JpaRepository<ApplicantReferee, UUID> {
    List<ApplicantReferee> findAllByApplicantIdAndDeletedAtIsNullOrderByFullNameAsc(UUID applicantId);
    Optional<ApplicantReferee> findByIdAndApplicantIdAndDeletedAtIsNull(UUID id, UUID applicantId);
    long countByApplicantIdAndDeletedAtIsNull(UUID applicantId);
}
