package zw.ac.uz.emhare.finance.payment.provider;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Returns the contained payment result to the applicant portal without exposing provider branding. @author Tinashe K */
@RestController
public class CbzIveriLiteReturnController {

    private final CbzIveriLiteReturnService returnService;
    private final SecureRandom secureRandom = new SecureRandom();

    public CbzIveriLiteReturnController(CbzIveriLiteReturnService returnService) {
        this.returnService = returnService;
    }

    @RequestMapping(
            path = "/api/finance/application-payment-returns/{outcome}",
            method = {RequestMethod.GET, RequestMethod.POST},
            produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> handleReturn(
            @PathVariable("outcome") String outcome,
            @RequestParam("attemptId") UUID attemptId,
            @RequestParam("nonce") String nonce,
            @RequestParam MultiValueMap<String, String> parameters) {
        Map<String, String> result;
        try {
            result = returnService.processReturn(attemptId, nonce, outcome, parameters);
        } catch (IllegalArgumentException exception) {
            result = Map.of(
                    "Lite_Payment_Card_Status", "255",
                    "Lite_Result_Description", "The payment result could not be verified.");
        }
        String scriptNonce = randomNonce();
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header("Content-Security-Policy",
                        "default-src 'none'; script-src 'nonce-" + scriptNonce + "'; frame-ancestors *")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(returnPage(result, scriptNonce));
    }

    private String returnPage(Map<String, String> result, String scriptNonce) {
        String formEncodedResult = result.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
        String encodedResult = Base64.getEncoder().encodeToString(
                formEncodedResult.getBytes(StandardCharsets.UTF_8));
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>Payment result</title></head>"
                + "<body><p>Returning to your application...</p><script nonce=\"" + scriptNonce + "\">"
                + "const result=Object.fromEntries(new URLSearchParams(atob('" + encodedResult + "')));"
                + "window.parent.postMessage(JSON.stringify(result),'*');</script></body></html>";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String randomNonce() {
        byte[] nonceBytes = new byte[18];
        secureRandom.nextBytes(nonceBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
    }
}
