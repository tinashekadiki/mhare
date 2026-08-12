package zw.ac.uz.emhare.admissions.infrastructure.persistence;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionOffer;

import zw.ac.uz.emhare.admissions.application.*;
import zw.ac.uz.emhare.admissions.domain.model.*;
import zw.ac.uz.emhare.admissions.infrastructure.messaging.model.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** @author Tinashe K */
public interface AdmissionOfferRepository extends JpaRepository<AdmissionOffer, UUID> {
    List<AdmissionOffer> findAllByDeletedAtIsNullOrderByCreatedAtDesc();
    List<AdmissionOffer> findAllByApplicationIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID applicationId);
    List<AdmissionOffer> findAllByApplicationApplicantUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
    List<AdmissionOffer> findAllByOfferBatchIdAndDeletedAtIsNull(UUID offerBatchId);
    Optional<AdmissionOffer> findByIdAndApplicationApplicantUserIdAndDeletedAtIsNull(UUID id, UUID userId);
    Optional<AdmissionOffer> findByProgrammeChoiceIdAndDeletedAtIsNull(UUID programmeChoiceId);
}
