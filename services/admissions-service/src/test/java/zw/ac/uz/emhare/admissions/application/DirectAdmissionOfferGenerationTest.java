package zw.ac.uz.emhare.admissions.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionOffer;
import zw.ac.uz.emhare.admissions.domain.model.OfferDocumentVersion;
import zw.ac.uz.emhare.admissions.domain.model.OfferDocumentVersionStatus;
import zw.ac.uz.emhare.admissions.domain.model.OfferStatus;
import zw.ac.uz.emhare.admissions.domain.model.OfferType;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.AdmissionOfferRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferConditionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferDispatchRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferDocumentVersionRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferPublicationRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferResponseRepository;
import zw.ac.uz.emhare.admissions.infrastructure.persistence.OfferStatusEventRepository;
import zw.ac.uz.emhare.admissions.integration.AdmissionsIntegrationOutboxService;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient;
import zw.ac.uz.emhare.common.messaging.OfferLetterContentSnapshot.FeeScheduleSnapshot;

/** @author Tinashe K */
class DirectAdmissionOfferGenerationTest {
    @Test
    void reusesAnOutstandingVersionWithAuthoritativeFinanceAndCoreSnapshots() {
        Fixture fixture = fixture();
        OfferDocumentVersion existing = mock(OfferDocumentVersion.class);
        when(existing.getDocumentVersion()).thenReturn(3);
        when(existing.getStatus()).thenReturn(OfferDocumentVersionStatus.REQUESTED);
        when(existing.getRequestedAt()).thenReturn(fixture.now());
        when(fixture.documentRepository()
                .findFirstByOfferIdAndStatusAndDeletedAtIsNullOrderByDocumentVersionDesc(
                        fixture.offerId(), OfferDocumentVersionStatus.REQUESTED))
                .thenReturn(Optional.of(existing));
        FeeScheduleSnapshot schedule = mock(FeeScheduleSnapshot.class);
        CoreIdentityClient.CoreInstitutionProfile profile = mock(CoreIdentityClient.CoreInstitutionProfile.class);
        when(fixture.feeResolver().resolve(fixture.offer(), "Bearer token")).thenReturn(schedule);
        when(fixture.coreClient().institutionProfile("Bearer token")).thenReturn(profile);

        var result = fixture.service().generate(fixture.offerId(), fixture.actorId(), "Bearer token");

        assertEquals(3, result.documentVersion());
        verify(fixture.outbox()).enqueueOfferLetterRequested(
                fixture.offer(), 3, fixture.actorId(), schedule, profile);
    }

    @Test
    void compatibilityGenerationCreatesTheNextVersionWithoutInventingRemoteSnapshots() {
        Fixture fixture = fixture();
        when(fixture.documentRepository()
                .findFirstByOfferIdAndStatusAndDeletedAtIsNullOrderByDocumentVersionDesc(
                        fixture.offerId(), OfferDocumentVersionStatus.REQUESTED))
                .thenReturn(Optional.empty());
        when(fixture.documentRepository().countByOfferIdAndDeletedAtIsNull(fixture.offerId())).thenReturn(1);
        when(fixture.documentRepository().saveAndFlush(any(OfferDocumentVersion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = fixture.service().generate(fixture.offerId(), fixture.actorId());

        assertEquals(2, result.documentVersion());
        verify(fixture.feeResolver()).resolve(fixture.offer(), null);
        verify(fixture.coreClient(), never()).institutionProfile(any());
        verify(fixture.outbox()).enqueueOfferLetterRequested(
                eq(fixture.offer()), eq(2), eq(fixture.actorId()), eq(null), eq(null));
    }

    @Test
    void blankAuthorizationAlsoUsesTheCompatibilitySnapshotPath() {
        Fixture fixture = fixture();
        OfferDocumentVersion existing = mock(OfferDocumentVersion.class);
        when(existing.getDocumentVersion()).thenReturn(1);
        when(existing.getStatus()).thenReturn(OfferDocumentVersionStatus.REQUESTED);
        when(existing.getRequestedAt()).thenReturn(fixture.now());
        when(fixture.documentRepository()
                .findFirstByOfferIdAndStatusAndDeletedAtIsNullOrderByDocumentVersionDesc(
                        fixture.offerId(), OfferDocumentVersionStatus.REQUESTED))
                .thenReturn(Optional.of(existing));

        fixture.service().generate(fixture.offerId(), fixture.actorId(), " ");

        verify(fixture.coreClient(), never()).institutionProfile(any());
    }

    private Fixture fixture() {
        AdmissionOfferRepository offerRepository = mock(AdmissionOfferRepository.class);
        OfferResponseRepository responseRepository = mock(OfferResponseRepository.class);
        OfferDocumentVersionRepository documentRepository = mock(OfferDocumentVersionRepository.class);
        AdmissionsIntegrationOutboxService outbox = mock(AdmissionsIntegrationOutboxService.class);
        OfferLetterFeeScheduleResolver feeResolver = mock(OfferLetterFeeScheduleResolver.class);
        CoreIdentityClient coreClient = mock(CoreIdentityClient.class);
        AdmissionOffer offer = mock(AdmissionOffer.class);
        UUID offerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Instant now = Instant.parse("2028-01-10T08:00:00Z");
        when(offerRepository.findById(offerId)).thenReturn(Optional.of(offer));
        when(offer.getId()).thenReturn(offerId);
        when(offer.getStatus()).thenReturn(OfferStatus.APPROVED);
        when(offer.getOfferType()).thenReturn(OfferType.FIRM);
        when(offer.getAcceptanceDeadline()).thenReturn(now.plusSeconds(86_400));
        when(offer.getCommencementDate()).thenReturn(LocalDate.parse("2028-03-04"));
        when(responseRepository.findByOfferId(offerId)).thenReturn(Optional.empty());
        DirectAdmissionOfferService service = new DirectAdmissionOfferService(offerRepository, responseRepository,
                mock(OfferConditionRepository.class), documentRepository, mock(OfferPublicationRepository.class),
                mock(OfferDispatchRepository.class), mock(OfferStatusEventRepository.class), outbox, feeResolver,
                coreClient, Clock.fixed(now, ZoneOffset.UTC));
        return new Fixture(service, offerId, actorId, now, offer, documentRepository, outbox, feeResolver, coreClient);
    }

    private record Fixture(DirectAdmissionOfferService service, UUID offerId, UUID actorId, Instant now,
            AdmissionOffer offer, OfferDocumentVersionRepository documentRepository,
            AdmissionsIntegrationOutboxService outbox, OfferLetterFeeScheduleResolver feeResolver,
            CoreIdentityClient coreClient) { }
}
