package zw.ac.uz.emhare.admissions.integration;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;

/** @author Tinashe K */
@Component
public class DocumentsReportingClient {
    private final RestClient restClient;

    public DocumentsReportingClient(
            RestClient.Builder restClientBuilder,
            @Value("${emhare.documents-reporting.url:http://localhost:8090}") String documentsReportingUrl) {
        restClient = restClientBuilder.baseUrl(documentsReportingUrl).build();
    }

    public UploadedDocumentSnapshot getUploadedDocument(UUID documentId) {
        try {
            UploadedDocumentSnapshot snapshot = restClient.get()
                    .uri("/api/documents/uploads/{documentId}", documentId)
                    .headers(headers -> headers.setBearerAuth(currentBearerToken()))
                    .retrieve()
                    .body(UploadedDocumentSnapshot.class);
            if (snapshot == null) {
                throw new ServiceDependencyUnavailableException(
                        "Documents/Reporting returned an empty document record.", null);
            }
            return snapshot;
        } catch (ServiceDependencyUnavailableException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()
                    || exception.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new IllegalArgumentException("Uploaded document was not found.", exception);
            }
            if (exception.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "The uploaded document is not accessible to this user.", exception);
            }
            throw unavailable(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    public List<UploadedDocumentSnapshot> getUploadedDocuments(String ownerType, UUID ownerId) {
        try {
            UploadedDocumentSnapshot[] snapshots = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/documents/uploads")
                            .queryParam("ownerType", ownerType)
                            .queryParam("ownerId", ownerId)
                            .build())
                    .headers(headers -> headers.setBearerAuth(currentBearerToken()))
                    .retrieve()
                    .body(UploadedDocumentSnapshot[].class);
            return snapshots == null ? List.of() : List.copyOf(Arrays.asList(snapshots));
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private ServiceDependencyUnavailableException unavailable(RestClientException exception) {
        return new ServiceDependencyUnavailableException(
                "Documents/Reporting is unavailable, so document evidence cannot be safely linked.", exception);
    }

    private String currentBearerToken() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
            return authentication.getToken().getTokenValue();
        }
        throw new IllegalStateException("JWT authentication is required for Documents/Reporting access.");
    }

    public record UploadedDocumentSnapshot(
            UUID id,
            String ownerType,
            UUID ownerId,
            String documentTypeCode,
            String originalFileName,
            String mimeType,
            long fileSizeBytes,
            String checksumSha256,
            UUID uploadedByUserId,
            Instant uploadedAt,
            String verificationStatus,
            UUID verifiedByUserId,
            Instant verifiedAt,
            String verificationComment,
            String rejectionReason,
            UUID replacesDocumentId,
            long version) {
    }
}
