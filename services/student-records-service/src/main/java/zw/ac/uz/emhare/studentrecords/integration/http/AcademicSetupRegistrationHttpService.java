package zw.ac.uz.emhare.studentrecords.integration.http;

import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import zw.ac.uz.emhare.studentrecords.registration.AcademicRegistrationCatalogueClient.RegistrationCatalogue;

/** Consumer-owned Student Records view of Academic Setup registration data. @author Tinashe K */
@HttpExchange(accept = "application/json")
public interface AcademicSetupRegistrationHttpService {

  @GetExchange("/api/academic/registration-catalogue")
  RegistrationCatalogue getRegistrationCatalogue(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @RequestParam("academicPeriodId") UUID academicPeriodId,
      @RequestParam("programmeVersionId") UUID programmeVersionId,
      @RequestParam("periodNumber") int programmePeriodNumber);
}
