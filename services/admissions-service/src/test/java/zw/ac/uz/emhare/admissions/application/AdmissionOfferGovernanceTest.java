package zw.ac.uz.emhare.admissions.application;

import zw.ac.uz.emhare.admissions.domain.model.AdmissionCycle;
import zw.ac.uz.emhare.admissions.domain.model.AdmissionOffer;
import zw.ac.uz.emhare.admissions.domain.model.Applicant;
import zw.ac.uz.emhare.admissions.domain.model.Application;
import zw.ac.uz.emhare.admissions.domain.model.ApplicationProgrammeChoice;
import zw.ac.uz.emhare.admissions.domain.model.OfferBatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import zw.ac.uz.emhare.admissions.domain.model.OfferBatchStatus;
import zw.ac.uz.emhare.admissions.domain.model.OfferStatus;
import zw.ac.uz.emhare.admissions.domain.model.OfferType;
import zw.ac.uz.emhare.admissions.domain.model.ProgrammeChoiceDecision;
import zw.ac.uz.emhare.admissions.domain.model.DecisionOutcome;
import zw.ac.uz.emhare.admissions.domain.model.OfferDocumentVersion;
import zw.ac.uz.emhare.admissions.domain.model.OfferPublication;
import zw.ac.uz.emhare.admissions.domain.model.OfferResponseType;

/** @author Tinashe K */
class AdmissionOfferGovernanceTest {

    @Test
    void offerCannotBeApprovedOrDispatchedUntilItsStoredLetterIsLinked() {
        Instant now = Instant.parse("2028-01-10T08:00:00Z");
        AdmissionOffer offer = draftOffer(now);

        assertThatThrownBy(() -> offer.approve(UUID.randomUUID(), now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stored generated offer document");
        assertThatThrownBy(() -> offer.markSent(now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approved offer");

        offer.linkGeneratedDocument(UUID.randomUUID());
        offer.approve(UUID.randomUUID(), now);
        offer.markSent(now.plusSeconds(60));

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.SENT);
        assertThat(offer.getSentAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void offerSummaryIncludesTheApplicantsFullDisplayName() {
        AdmissionOffer offer = mock(AdmissionOffer.class);
        OfferBatch batch = mock(OfferBatch.class);
        Application application = mock(Application.class);
        Applicant applicant = mock(Applicant.class);
        ApplicationProgrammeChoice choice = mock(ApplicationProgrammeChoice.class);
        when(offer.getApplication()).thenReturn(application);
        when(offer.getOfferBatch()).thenReturn(batch);
        when(offer.getProgrammeChoice()).thenReturn(choice);
        when(offer.getOfferType()).thenReturn(OfferType.FIRM);
        when(offer.getStatus()).thenReturn(OfferStatus.DRAFT);
        when(application.getApplicant()).thenReturn(applicant);
        when(applicant.getApplicantNumber()).thenReturn("APP-00000762");
        when(applicant.getDisplayName()).thenReturn("Jemima Megan Lindsey Stevens");

        AdmissionOfferSummary summary = AdmissionOfferSummary.from(offer, List.of(), null);

        assertThat(summary.applicantNumber()).isEqualTo("APP-00000762");
        assertThat(summary.applicantName()).isEqualTo("Jemima Megan Lindsey Stevens");
    }

    @Test
    void publishedDirectOfferRequiresReplacementPublicationAfterTermsChange() {
        Instant now = Instant.parse("2028-01-10T08:00:00Z");
        AdmissionCycle cycle = mock(AdmissionCycle.class); when(cycle.getIntakeId()).thenReturn(UUID.randomUUID());
        Application application = mock(Application.class); UUID applicationId = UUID.randomUUID();
        when(application.getId()).thenReturn(applicationId); when(application.getAdmissionCycle()).thenReturn(cycle);
        ApplicationProgrammeChoice choice = mock(ApplicationProgrammeChoice.class); UUID choiceId = UUID.randomUUID();
        when(choice.getId()).thenReturn(choiceId); when(choice.getApplication()).thenReturn(application);
        when(choice.getProgrammeId()).thenReturn(UUID.randomUUID()); when(choice.getProgrammeVersionId()).thenReturn(UUID.randomUUID());
        when(choice.getProgrammeCode()).thenReturn("BSC-CS"); when(choice.getProgrammeName()).thenReturn("Computer Science");
        ProgrammeChoiceDecision decision = mock(ProgrammeChoiceDecision.class);
        when(decision.getDecision()).thenReturn(DecisionOutcome.ADMIT); when(decision.getProgrammeChoice()).thenReturn(choice);
        AdmissionOffer offer = new AdmissionOffer(application, choice, decision, "OFR-2028-0002");
        offer.updateTerms(OfferType.FIRM, null, now.plusSeconds(86_400), null, null,
                LocalDate.parse("2028-08-25"), now);
        OfferDocumentVersion document = new OfferDocumentVersion(offer, 1, UUID.randomUUID(), now);
        document.store(UUID.randomUUID(), "OFFER-OFR-2028-0002-V1", "documents",
                "official-offers/APP/OFFER-OFR-2028-0002-V1.pdf", "a".repeat(64), now.plusSeconds(10));
        offer.linkCurrentDocumentVersion(document);
        OfferPublication publication = new OfferPublication(offer, document, 1, UUID.randomUUID(), UUID.randomUUID(), now.plusSeconds(20));
        offer.publish(publication, UUID.randomUUID(), now.plusSeconds(20));

        offer.updateTerms(OfferType.FIRM, null, now.plusSeconds(172_800), null, null,
                LocalDate.parse("2028-08-25"), now.plusSeconds(30));

        assertThat(offer.getOfferBatch()).isNull();
        assertThat(offer.isAmendmentPending()).isTrue();
        assertThatThrownBy(() -> offer.respond(OfferResponseType.ACCEPTED))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("published");
    }

    private AdmissionOffer draftOffer(Instant now) {
        AdmissionCycle cycle = mock(AdmissionCycle.class);
        when(cycle.getIntakeId()).thenReturn(UUID.randomUUID());
        Application application = mock(Application.class);
        UUID applicationId = UUID.randomUUID();
        when(application.getId()).thenReturn(applicationId);
        when(application.getAdmissionCycle()).thenReturn(cycle);
        ApplicationProgrammeChoice choice = mock(ApplicationProgrammeChoice.class);
        when(choice.getApplication()).thenReturn(application);
        when(choice.getProgrammeId()).thenReturn(UUID.randomUUID());
        when(choice.getProgrammeVersionId()).thenReturn(UUID.randomUUID());
        when(choice.getProgrammeCode()).thenReturn("BSC-CS");
        when(choice.getProgrammeName()).thenReturn("Computer Science");
        OfferBatch batch = mock(OfferBatch.class);
        when(batch.getStatus()).thenReturn(OfferBatchStatus.APPROVED);

        return new AdmissionOffer(
                application,
                choice,
                batch,
                "OFR-2028-0001",
                OfferType.FIRM,
                null,
                now.plusSeconds(86_400),
                LocalDate.parse("2028-08-15"),
                LocalDate.parse("2028-08-20"),
                LocalDate.parse("2028-08-25"),
                null,
                now);
    }
}
