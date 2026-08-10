package zw.ac.uz.emhare.admissions.application;

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
