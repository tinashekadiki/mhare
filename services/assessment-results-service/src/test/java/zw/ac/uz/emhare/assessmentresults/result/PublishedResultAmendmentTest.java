package zw.ac.uz.emhare.assessmentresults.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** @author Tinashe K */
class PublishedResultAmendmentTest {

    private UUID requesterUserId;
    private UUID reviewerUserId;
    private UUID approverUserId;
    private UUID applicationUserId;
    private PublishedResultAmendment amendment;

    @BeforeEach
    void createRequestedAmendment() {
        requesterUserId = UUID.randomUUID();
        reviewerUserId = UUID.randomUUID();
        approverUserId = UUID.randomUUID();
        applicationUserId = UUID.randomUUID();
        ResultBatch replacementBatch = mock(ResultBatch.class);
        ModuleResult replacementResult = mock(ModuleResult.class);
        when(replacementResult.getResultBatch()).thenReturn(replacementBatch);
        when(replacementResult.getFinalMark()).thenReturn(new BigDecimal("72.00"));
        when(replacementResult.getGrade()).thenReturn("C");
        when(replacementResult.getRemark()).thenReturn("Credit");
        amendment = new PublishedResultAmendment(
                "AMEND-TEST-001",
                mock(PublishedResult.class),
                replacementResult,
                "Submitted mark amendment changed the approved total.",
                requesterUserId,
                Instant.parse("2027-09-01T08:00:00Z"));
    }

    @Test
    void enforcesIndependentReviewApprovalAndApplicationActors() {
        assertThrows(IllegalStateException.class, () -> amendment.review(
                requesterUserId, "Self review", Instant.parse("2027-09-01T09:00:00Z"), 0));

        amendment.review(
                reviewerUserId,
                "Replacement calculation evidence verified.",
                Instant.parse("2027-09-01T09:00:00Z"),
                0);
        assertEquals(PublishedResultAmendment.Status.REVIEWED, amendment.getStatus());
        assertThrows(IllegalStateException.class, () -> amendment.approve(
                reviewerUserId, "Self approval", Instant.parse("2027-09-01T10:00:00Z"), 0));

        amendment.approve(
                approverUserId,
                "Faculty result board approved the correction.",
                Instant.parse("2027-09-01T10:00:00Z"),
                0);
        assertEquals(PublishedResultAmendment.Status.APPROVED, amendment.getStatus());
        assertThrows(IllegalStateException.class, () -> amendment.apply(
                approverUserId, "Self application", Instant.parse("2027-09-01T11:00:00Z"), 0));

        amendment.apply(
                applicationUserId,
                "Registry released the corrected publication version.",
                Instant.parse("2027-09-01T11:00:00Z"),
                0);
        assertEquals(PublishedResultAmendment.Status.APPLIED, amendment.getStatus());
    }

    @Test
    void preventsRequesterFromRejectingOwnAmendment() {
        assertThrows(IllegalStateException.class, () -> amendment.reject(
                requesterUserId,
                "Requester attempted withdrawal through the decision endpoint.",
                Instant.parse("2027-09-01T09:00:00Z"),
                0));

        amendment.reject(
                reviewerUserId,
                "Replacement evidence does not support the requested correction.",
                Instant.parse("2027-09-01T09:00:00Z"),
                0);
        assertEquals(PublishedResultAmendment.Status.REJECTED, amendment.getStatus());
    }
}
