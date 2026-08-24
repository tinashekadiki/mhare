package zw.ac.uz.emhare.admissions.integration.http;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PutExchange;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreCurrentUserProfile;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.CoreInstitutionProfile;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.OfficialNameSynchronizationRequest;
import zw.ac.uz.emhare.admissions.integration.CoreIdentityClient.OfficialNameSynchronizationResponse;

/** Consumer-owned Admissions view of Core Identity. @author Tinashe K */
@HttpExchange(accept = "application/json")
public interface CoreIdentityHttpService {

  @GetExchange("/api/core/me")
  CoreCurrentUserProfile syncCurrentUser(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

  @GetExchange("/api/core/institution-profile")
  CoreInstitutionProfile institutionProfile(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);

  @PutExchange("/api/core/users/{userId}/official-name")
  OfficialNameSynchronizationResponse synchronizeOfficialName(
      @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
      @PathVariable("userId") java.util.UUID userId,
      @RequestBody OfficialNameSynchronizationRequest request);
}
