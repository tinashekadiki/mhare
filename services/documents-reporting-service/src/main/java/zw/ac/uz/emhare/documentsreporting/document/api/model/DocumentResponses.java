package zw.ac.uz.emhare.documentsreporting.document.api.model;

import zw.ac.uz.emhare.documentsreporting.document.infrastructure.persistence.model.GeneratedDocument;

import zw.ac.uz.emhare.documentsreporting.document.*;

import java.time.Instant;
import java.util.UUID;

/** @author Tinashe K */
public final class DocumentResponses {
    private DocumentResponses() {
    }

    public record DocumentSummary(
            UUID id,
            String documentNumber,
            GeneratedDocument.DocumentType documentType,
            String studentNumber,
            String academicPeriodCode,
            String decisionCode,
            String decisionLabel,
            GeneratedDocument.Status status,
            String templateCode,
            int templateVersion,
            String checksumSha256,
            Long sizeBytes,
            Integer pageCount,
            Instant requestedAt,
            Instant generatedAt,
            int generationAttemptCount,
            boolean retryAvailable,
            String lastFailureReason,
            long version) {
    }

    public record DocumentDownload(
            UUID documentId,
            String documentNumber,
            String contentType,
            String checksumSha256,
            String downloadUrl,
            Instant expiresAt) {
    }

    public record RetryDocument(long expectedVersion) {
    }
}
