package zw.ac.uz.emhare.admissions.integration;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;

/** @author Tinashe K */
@Component
public class AcademicSetupCatalogueClient {

    private final RestClient restClient;

    public AcademicSetupCatalogueClient(
            RestClient.Builder restClientBuilder,
            @Value("${emhare.academic-setup.url:http://localhost:8082}") String academicSetupUrl) {
        this.restClient = restClientBuilder.baseUrl(academicSetupUrl).build();
    }

    public AcademicAdmissionsCatalogue getAdmissionsCatalogue(UUID academicYearId, UUID intakeId) {
        try {
            AcademicAdmissionsCatalogue catalogue = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/academic/admissions-catalogue")
                            .queryParam("academicYearId", academicYearId)
                            .queryParam("intakeId", intakeId)
                            .build())
                    .headers(headers -> headers.setBearerAuth(currentBearerToken()))
                    .retrieve()
                    .body(AcademicAdmissionsCatalogue.class);
            if (catalogue == null) {
                throw new ServiceDependencyUnavailableException(
                        "Academic Setup returned an empty admissions catalogue.", null);
            }
            return catalogue;
        } catch (ServiceDependencyUnavailableException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            String rejectionDetail = catalogueRejectionDetail(exception);
            if (exception.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
                throw new IllegalArgumentException(rejectionDetail, exception);
            }
            if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new IllegalStateException(rejectionDetail, exception);
            }
            throw unavailable(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    public List<AcademicAdmissionsIntake> getOpenAdmissionsIntakes() {
        try {
            List<AcademicAdmissionsIntake> intakes = restClient.get()
                    .uri("/api/academic/admissions-intakes")
                    .headers(headers -> headers.setBearerAuth(currentBearerToken()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
            return intakes == null ? List.of() : intakes;
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    public AcademicAdmissionsIntake getAdmissionsIntake(UUID intakeId) {
        try {
            AcademicAdmissionsIntake intake = restClient.get()
                    .uri("/api/academic/admissions-intakes/{intakeId}", intakeId)
                    .headers(headers -> headers.setBearerAuth(currentBearerToken()))
                    .retrieve()
                    .body(AcademicAdmissionsIntake.class);
            if (intake == null) {
                throw new ServiceDependencyUnavailableException(
                        "Academic Setup returned an empty intake record.", null);
            }
            return intake;
        } catch (ServiceDependencyUnavailableException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
                throw new IllegalArgumentException(catalogueRejectionDetail(exception), exception);
            }
            throw unavailable(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    public ProgrammeHierarchyResolution getProgrammeHierarchy(UUID programmeId) {
        try {
            ProgrammeHierarchyResolution hierarchy = restClient.get()
                    .uri("/api/academic/programmes/{programmeId}/hierarchy", programmeId)
                    .headers(headers -> headers.setBearerAuth(currentBearerToken()))
                    .retrieve()
                    .body(ProgrammeHierarchyResolution.class);
            if (hierarchy == null || hierarchy.highestAcademicUnit() == null || hierarchy.ancestorPath() == null
                    || hierarchy.ancestorPath().isEmpty()) {
                throw new ServiceDependencyUnavailableException(
                        "Academic Setup returned an incomplete programme hierarchy.", null);
            }
            return hierarchy;
        } catch (ServiceDependencyUnavailableException exception) {
            throw exception;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()
                    || exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new IllegalStateException(catalogueRejectionDetail(exception), exception);
            }
            throw unavailable(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private ServiceDependencyUnavailableException unavailable(RestClientException exception) {
        return new ServiceDependencyUnavailableException(
                "Academic Setup is unavailable, so programme choices cannot be safely validated.", exception);
    }

    private String catalogueRejectionDetail(RestClientResponseException exception) {
        try {
            ProblemDetail problemDetail = exception.getResponseBodyAs(ProblemDetail.class);
            if (problemDetail != null && problemDetail.getDetail() != null && !problemDetail.getDetail().isBlank()) {
                return problemDetail.getDetail();
            }
        } catch (RestClientException ignored) {
            // Use the stable local message when a dependency returns a non-standard error body.
        }
        return "Academic Setup rejected the admissions catalogue request.";
    }

    private String currentBearerToken() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication) {
            return authentication.getToken().getTokenValue();
        }
        throw new IllegalStateException("JWT authentication is required for the Academic Setup catalogue.");
    }

    public record AcademicAdmissionsCatalogue(
            UUID academicYearId, String academicYearName,
            UUID intakeId, String intakeCode, String intakeName,
            LocalDate intakeStartsOn, LocalDate intakeEndsOn,
            List<AcademicProgrammeOption> programmes) {
    }

    public record AcademicAdmissionsIntake(
            UUID intakeId,
            UUID academicYearId,
            String academicYearName,
            String code,
            String name,
            LocalDate startsOn,
            LocalDate endsOn,
            String status,
            int maximumProgrammeChoices,
            List<AcademicProgrammeOption> programmes) {
    }

    public record AcademicProgrammeOption(
            UUID programmeId, String programmeCode, String programmeName, String awardName,
            UUID programmeVersionId, String programmeVersionCode,
            UUID owningAcademicUnitId, String owningAcademicUnitName,
            int minimumDurationPeriods, int maximumDurationPeriods) {
    }

    public record ProgrammeHierarchyResolution(
            UUID programmeId, String programmeCode, String programmeName,
            AcademicUnitHierarchyNode owningAcademicUnit,
            AcademicUnitHierarchyNode highestAcademicUnit,
            List<AcademicUnitHierarchyNode> ancestorPath) { }

    public record AcademicUnitHierarchyNode(
            UUID id, UUID academicUnitTypeId, String academicUnitTypeCode, UUID parentId,
            String code, String name, String status, String legacyFacultyCode,
            String legacyDepartmentCode, long version) { }
}
