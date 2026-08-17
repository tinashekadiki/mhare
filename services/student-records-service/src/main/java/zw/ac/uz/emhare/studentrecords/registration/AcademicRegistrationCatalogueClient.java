package zw.ac.uz.emhare.studentrecords.registration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import zw.ac.uz.emhare.common.web.ServiceDependencyUnavailableException;
import zw.ac.uz.emhare.studentrecords.integration.http.AcademicSetupRegistrationHttpService;

/**
 * @author Tinashe K
 */
@Component
public class AcademicRegistrationCatalogueClient {

  private final AcademicSetupRegistrationHttpService academicSetupRegistrationHttpService;

  public AcademicRegistrationCatalogueClient(
      AcademicSetupRegistrationHttpService academicSetupRegistrationHttpService) {
    this.academicSetupRegistrationHttpService = academicSetupRegistrationHttpService;
  }

  public RegistrationCatalogue getRegistrationCatalogue(
      UUID academicPeriodId, UUID programmeVersionId, int programmePeriodNumber) {
    try {
      RegistrationCatalogue catalogue =
          academicSetupRegistrationHttpService.getRegistrationCatalogue(
              bearerAuthorization(), academicPeriodId, programmeVersionId, programmePeriodNumber);
      if (catalogue == null) {
        throw new ServiceDependencyUnavailableException(
            "Academic Setup returned an empty registration catalogue.", null);
      }
      return catalogue;
    } catch (ServiceDependencyUnavailableException exception) {
      throw exception;
    } catch (RestClientResponseException exception) {
      String detail = rejectionDetail(exception);
      if (exception.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
        throw new IllegalArgumentException(detail, exception);
      }
      if (exception.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
        throw new IllegalStateException(detail, exception);
      }
      throw unavailable(exception);
    } catch (RestClientException exception) {
      throw unavailable(exception);
    }
  }

  private String bearerAuthorization() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
      throw new IllegalStateException(
          "JWT authentication is required for governed registration catalogue access.");
    }
    return "Bearer " + jwtAuthenticationToken.getToken().getTokenValue();
  }

  private ServiceDependencyUnavailableException unavailable(RestClientException exception) {
    return new ServiceDependencyUnavailableException(
        "Academic Setup is unavailable, so registration cannot be safely validated.", exception);
  }

  private String rejectionDetail(RestClientResponseException exception) {
    try {
      ProblemDetail problemDetail = exception.getResponseBodyAs(ProblemDetail.class);
      if (problemDetail != null
          && problemDetail.getDetail() != null
          && !problemDetail.getDetail().isBlank()) {
        return problemDetail.getDetail();
      }
    } catch (RestClientException ignored) {
      // Use the stable local message when the dependency returns a non-standard body.
    }
    return "Academic Setup rejected the registration catalogue request.";
  }

  public record RegistrationCatalogue(
      UUID academicPeriodId,
      String academicPeriodCode,
      String academicPeriodName,
      LocalDate academicPeriodStartsOn,
      LocalDate academicPeriodEndsOn,
      UUID programmeVersionId,
      UUID programmeId,
      String programmeCode,
      String programmeName,
      String programmeVersionCode,
      UUID owningAcademicUnitId,
      String owningAcademicUnitCode,
      String owningAcademicUnitName,
      UUID programmeLevelId,
      String programmeLevelCode,
      String programmeLevelName,
      int periodNumber,
      List<RegistrationModuleOption> modules) {}

  public record RegistrationModuleOption(
      UUID curriculumModuleId,
      UUID moduleId,
      String moduleCode,
      String moduleName,
      String moduleType,
      BigDecimal creditValue,
      BigDecimal minimumMarkRequired,
      int sortOrder) {}
}
