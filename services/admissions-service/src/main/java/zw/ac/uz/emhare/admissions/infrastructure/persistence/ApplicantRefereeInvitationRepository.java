package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.ApplicantRefereeInvitation;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

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
