package zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.uz.emhare.documentsreporting.infrastructure.persistence.projection.model.PublishedOfferLetterProjection;

/** @author Tinashe K */
public interface PublishedOfferLetterProjectionRepository extends JpaRepository<PublishedOfferLetterProjection,UUID> {
    Optional<PublishedOfferLetterProjection> findBySourceEventId(UUID eventId);
    Optional<PublishedOfferLetterProjection> findByOfferIdAndCurrentPublicationTrue(UUID offerId);
    Optional<PublishedOfferLetterProjection> findByGeneratedDocumentIdAndCurrentPublicationTrue(UUID generatedDocumentId);
    List<PublishedOfferLetterProjection> findAllByIntakeIdAndProgrammeIdAndCurrentPublicationTrueAndOfferStatusNotOrderByApplicantNameAscApplicationNumberAsc(
            UUID intakeId, UUID programmeId, String excludedStatus);
}
