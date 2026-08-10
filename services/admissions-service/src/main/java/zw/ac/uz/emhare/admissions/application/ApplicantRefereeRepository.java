package zw.ac.uz.emhare.admissions.application;

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
