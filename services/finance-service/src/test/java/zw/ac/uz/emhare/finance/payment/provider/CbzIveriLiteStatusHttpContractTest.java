package zw.ac.uz.emhare.finance.payment.provider;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises Finance's actual HTTP client against a local provider stub. @author Tinashe K */
class CbzIveriLiteStatusHttpContractTest {
  private final AtomicReference<String> response = new AtomicReference<>();
  private final AtomicReference<String> requestBody = new AtomicReference<>();
  private final AtomicReference<String> requestMethod = new AtomicReference<>();
  private final AtomicReference<String> contentType = new AtomicReference<>();
  private HttpServer server;
  private CbzIveriLiteTransactionStatusClient client;

  @BeforeEach
  void startLocalProviderStub() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/Lite/AuthoriseInfo.aspx",
        exchange -> {
          requestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          requestMethod.set(exchange.getRequestMethod());
          contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
          byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    client =
        new CbzIveriLiteTransactionStatusClient(
            new CbzIveriLiteProperties(
                true,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/Lite/Authorise.aspx",
                " merchant-application ",
                "",
                "USD",
                "",
                "",
                "",
                ""));
    response.set(requiredFields("trace-1", "0"));
  }

  @AfterEach
  void stopLocalProviderStub() {
    if (server != null) server.stop(0);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-08-30T10:00:00Z",
        "2026-08-30 10:00:00",
        "20260830 100000",
        "20260830100000"
      })
  void verifiesTraceAndParsesDocumentedProviderDatesAndMinorUnits(String date) {
    response.set(
        requiredFields("trace-1", "0")
            + field("Lite_Order_Amount", " 2500 ")
            + field("Lite_TransactionDate", date)
            + field("Lite_Result_Description", "Approved &amp; settled")
            + field("Lite_TransactionIndex", "TX-1"));
    var status = client.query("trace-1");
    assertTrue(status.approved());
    assertEquals("trace-1", status.merchantTrace());
    assertEquals(new BigDecimal("25.00"), status.amount());
    assertEquals(Instant.parse("2026-08-30T10:00:00Z"), status.paidAt());
    assertEquals("Approved & settled", status.resultDescription());
    assertEquals("TX-1", status.providerTransactionReference());
    assertEquals("POST", requestMethod.get());
    assertTrue(contentType.get().startsWith("application/x-www-form-urlencoded"));
    assertEquals(
        "Lite_Merchant_ApplicationId=merchant-application&Lite_Merchant_Trace=trace-1",
        requestBody.get());
  }

  @Test
  void pendingStatusPreservesAbsentOptionalAmountAndDate() {
    response.set(requiredFields("trace-1", "9"));
    var status = client.query("trace-1");
    assertFalse(status.approved());
    assertNull(status.amount());
    assertNull(status.paidAt());
    assertNull(status.providerTransactionReference());
  }

  @Test
  void blankOptionalValuesRemainAbsentAndBankReferenceIsFallback() {
    response.set(
        requiredFields("trace-1", "1")
            + field("Lite_Order_Amount", " ")
            + field("Lite_TransactionDate", " ")
            + field("Lite_TransactionIndex", " ")
            + field("Lite_BankReference", "BANK-1"));
    var status = client.query("trace-1");
    assertNull(status.amount());
    assertNull(status.paidAt());
    assertEquals("BANK-1", status.providerTransactionReference());
  }

  @Test
  void merchantReferenceIsLastProviderReferenceFallback() {
    response.set(
        requiredFields("trace-1", "0")
            + field("Lite_BankReference", " ")
            + field("MerchantReference", "MERCHANT-1"));
    assertEquals("MERCHANT-1", client.query("trace-1").providerTransactionReference());
  }

  @Test
  void statusForAnotherAttemptNeverClearsCurrentPayment() {
    response.set(requiredFields("different-trace", "0"));
    assertTrue(
        assertThrows(IllegalStateException.class, () -> client.query("trace-1"))
            .getMessage()
            .contains("does not match"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"missing-trace", "blank-trace", "missing-status", "blank-status"})
  void requiresTraceAndProviderStatus(String invalid) {
    response.set(
        "missing-trace".equals(invalid)
            ? field("Lite_Payment_Card_Status", "0")
            : "blank-trace".equals(invalid)
                ? requiredFields(" ", "0")
                : "missing-status".equals(invalid)
                    ? field("Lite_Merchant_Trace", "trace-1")
                    : requiredFields("trace-1", " "));
    assertTrue(
        assertThrows(IllegalStateException.class, () -> client.query("trace-1"))
            .getMessage()
            .contains("omitted"));
  }

  @Test
  void invalidTransactionDateAndAmountFailClosed() {
    response.set(requiredFields("trace-1", "0") + field("Lite_TransactionDate", "not-a-date"));
    assertThrows(IllegalStateException.class, () -> client.query("trace-1"));
    response.set(requiredFields("trace-1", "0") + field("Lite_Order_Amount", "unknown"));
    assertThrows(NumberFormatException.class, () -> client.query("trace-1"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " "})
  void emptyProviderResponsesAreNotAccepted(String body) {
    assertThrows(
        IllegalStateException.class, () -> CbzIveriLiteTransactionStatusClient.parseResponse(body));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/Lite/Authorise.aspx", "https:/missing-authority"})
  void invalidGatewayUrlsCannotBeUsedForServerVerification(String gateway) {
    assertThrows(
        IllegalArgumentException.class,
        () -> CbzIveriLiteTransactionStatusClient.authorisationInformationUrl(gateway));
  }

  private String requiredFields(String trace, String status) {
    return field("Lite_Merchant_Trace", trace) + field("Lite_Payment_Card_Status", status);
  }

  private String field(String name, String value) {
    return "<input type=\"hidden\" name=\"" + name + "\" value=\"" + value + "\">";
  }
}
