package zw.ac.uz.emhare.documentsreporting.integration.http;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import zw.ac.uz.emhare.documentsreporting.integration.CoreIdentityClient.CoreCurrentUserProfile;

/** Consumer-owned Documents view of Core Identity. @author Tinashe K */
@HttpExchange(accept = "application/json")
public interface CoreIdentityHttpService {

    @GetExchange("/api/core/me")
    CoreCurrentUserProfile syncCurrentUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization);
}
