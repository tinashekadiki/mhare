package zw.ac.uz.emhare.common.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class EmhareApiExceptionHandlerTest {

  private final EmhareApiExceptionHandler exceptionHandler = new EmhareApiExceptionHandler();
  private final MockHttpServletRequest request =
      new MockHttpServletRequest("POST", "/api/admissions/applications");

  @BeforeEach
  void setUp() {
    MDC.put("correlationId", "test-correlation-id");
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void handleInvalidRequest_shouldReturnSafeCorrelatedProblemDetail() {
    ProblemDetail problemDetail =
        exceptionHandler.handleInvalidRequest(
            new IllegalArgumentException("Admission cycle not found."), request);

    assertEquals(HttpStatus.BAD_REQUEST.value(), problemDetail.getStatus());
    assertEquals("Invalid request", problemDetail.getTitle());
    assertEquals("Admission cycle not found.", problemDetail.getDetail());
    assertEquals("test-correlation-id", problemDetail.getProperties().get("correlationId"));
    assertEquals("/api/admissions/applications", problemDetail.getInstance().toString());
  }
}
