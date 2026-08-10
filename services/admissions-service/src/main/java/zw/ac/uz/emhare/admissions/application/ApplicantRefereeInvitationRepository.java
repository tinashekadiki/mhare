package zw.ac.uz.emhare.admissions.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface ApplicantRefereeInvitationRepository extends JpaRepository<ApplicantRefereeInvitation, UUID> {
    Optional<ApplicantRefereeInvitation> findByTokenHashAndDeletedAtIsNull(String tokenHash);
    List<ApplicantRefereeInvitation> findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID applicationId);
    List<ApplicantRefereeInvitation> findAllByApplicationIdAndRefereeIdAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID applicationId, UUID refereeId);
}
