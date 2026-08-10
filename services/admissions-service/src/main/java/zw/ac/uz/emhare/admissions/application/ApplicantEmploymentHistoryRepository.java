package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ApplicantEmploymentHistoryRepository extends JpaRepository<ApplicantEmploymentHistory, UUID> {
    List<ApplicantEmploymentHistory> findAllByApplicantIdAndDeletedAtIsNullOrderByStartedOnDesc(UUID applicantId);
    Optional<ApplicantEmploymentHistory> findByIdAndApplicantIdAndDeletedAtIsNull(UUID id, UUID applicantId);
    long countByApplicantIdAndDeletedAtIsNull(UUID applicantId);
}
