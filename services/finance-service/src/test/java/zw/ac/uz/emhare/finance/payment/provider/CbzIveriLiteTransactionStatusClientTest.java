package zw.ac.uz.emhare.finance.payment.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** @author Tinashe K */
class CbzIveriLiteTransactionStatusClientTest {

    @Test
    void derivesTheAuthorisationInformationEndpointFromTheConfiguredGateway() {
        assertEquals(
                "https://portal.host.iveri.com/Lite/AuthoriseInfo.aspx",
                CbzIveriLiteTransactionStatusClient.authorisationInformationUrl(
                        "https://portal.host.iveri.com/Lite/Authorise.aspx"));
    }

    @Test
    void parsesOnlyNamedHiddenInputsFromTheProviderResponse() {
        Map<String, String> fields = CbzIveriLiteTransactionStatusClient.parseResponse("""
                <html><body><form>
                <input name="Lite_Merchant_Trace" type="hidden" value="trace-1">
                <input name="Lite_Payment_Card_Status" type="hidden" value="0">
                <input name="Lite_Result_Description" type="hidden" value="Approved &amp; settled">
                </form></body></html>
                """);

        assertEquals("trace-1", fields.get("Lite_Merchant_Trace"));
        assertEquals("0", fields.get("Lite_Payment_Card_Status"));
        assertEquals("Approved & settled", fields.get("Lite_Result_Description"));
    }
}
