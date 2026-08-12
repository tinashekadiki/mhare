package zw.ac.uz.emhare.finance.payment.provider;

import zw.ac.uz.emhare.finance.payment.provider.infrastructure.persistence.model.CbzIveriLiteTransactionStatus;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

/** Queries the provider directly before Finance accepts an online payment result. @author Tinashe K */
@Component
public class CbzIveriLiteTransactionStatusClient {

    private static final Pattern INPUT_PATTERN = Pattern.compile(
            "<input\\b[^>]*\\bname=\"([^\"]+)\"[^>]*\\bvalue=\"([^\"]*)\"[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter[] TRANSACTION_DATE_FORMATS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyyMMdd HHmmss"),
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
    };

    private final RestClient restClient;
    private final CbzIveriLiteProperties properties;

    public CbzIveriLiteTransactionStatusClient(CbzIveriLiteProperties properties) {
        this.restClient = RestClient.create();
        this.properties = properties;
    }

    public CbzIveriLiteTransactionStatus query(String merchantTrace) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("Lite_Merchant_ApplicationId", properties.applicationId().trim());
        form.add("Lite_Merchant_Trace", merchantTrace);
        String response = restClient.post()
                .uri(authorisationInformationUrl(properties.gatewayUrl()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        Map<String, String> fields = parseResponse(response);
        String returnedTrace = required(fields, "Lite_Merchant_Trace");
        if (!merchantTrace.equals(returnedTrace)) {
            throw new IllegalStateException("The provider status response does not match the payment attempt.");
        }
        return new CbzIveriLiteTransactionStatus(
                returnedTrace,
                required(fields, "Lite_Payment_Card_Status"),
                fields.get("Lite_Result_Description"),
                fields.get("Lite_TransactionIndex"),
                fields.get("Lite_BankReference"),
                fields.get("MerchantReference"),
                minorUnits(fields.get("Lite_Order_Amount")),
                transactionDate(fields.get("Lite_TransactionDate")));
    }

    static String authorisationInformationUrl(String gatewayUrl) {
        URI gatewayUri = URI.create(gatewayUrl.trim());
        if (gatewayUri.getScheme() == null || gatewayUri.getRawAuthority() == null) {
            throw new IllegalArgumentException("The online payment gateway URL is invalid.");
        }
        return gatewayUri.getScheme() + "://" + gatewayUri.getRawAuthority() + "/Lite/AuthoriseInfo.aspx";
    }

    static Map<String, String> parseResponse(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("The provider returned an empty transaction status.");
        }
        Map<String, String> fields = new LinkedHashMap<>();
        Matcher matcher = INPUT_PATTERN.matcher(response);
        while (matcher.find()) {
            fields.put(HtmlUtils.htmlUnescape(matcher.group(1)), HtmlUtils.htmlUnescape(matcher.group(2)));
        }
        return Map.copyOf(fields);
    }

    private static String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("The provider status response omitted " + name + ".");
        }
        return value.trim();
    }

    private static BigDecimal minorUnits(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value.trim()).movePointLeft(2);
    }

    private static Instant transactionDate(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmedValue = value.trim();
        try {
            return Instant.parse(trimmedValue);
        } catch (DateTimeParseException ignored) {
            for (DateTimeFormatter formatter : TRANSACTION_DATE_FORMATS) {
                try {
                    return LocalDateTime.parse(trimmedValue, formatter).toInstant(ZoneOffset.UTC);
                } catch (DateTimeParseException ignoredFormat) {
                    // Try the next documented provider date representation.
                }
            }
        }
        throw new IllegalStateException("The provider returned an invalid transaction date.");
    }
}
