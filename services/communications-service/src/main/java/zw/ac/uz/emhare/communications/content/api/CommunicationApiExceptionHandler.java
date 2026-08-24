package zw.ac.uz.emhare.communications.content.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import zw.ac.uz.emhare.communications.content.application.CommunicationNotFoundException;

/** Maps non-visible Communications resources to a safe 404 response. @author Tinashe K */
@RestControllerAdvice
public class CommunicationApiExceptionHandler {

  @ExceptionHandler(CommunicationNotFoundException.class)
  ProblemDetail handleNotFound(
      CommunicationNotFoundException exception, HttpServletRequest request) {
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    detail.setTitle("Not found");
    detail.setType(URI.create("https://emhare.uz.ac.zw/problems/404"));
    detail.setInstance(URI.create(request.getRequestURI()));
    return detail;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleInvalidRequest(
      IllegalArgumentException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "Invalid request",
        "https://emhare.uz.ac.zw/problems/invalid-request",
        exception.getMessage(),
        request);
  }

  @ExceptionHandler({IllegalStateException.class, ObjectOptimisticLockingFailureException.class})
  ProblemDetail handleConflict(RuntimeException exception, HttpServletRequest request) {
    return problem(
        HttpStatus.CONFLICT,
        "Workflow conflict",
        "https://emhare.uz.ac.zw/problems/workflow-conflict",
        exception.getMessage(),
        request);
  }

  private ProblemDetail problem(
      HttpStatus status, String title, String type, String message, HttpServletRequest request) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
    detail.setTitle(title);
    detail.setType(URI.create(type));
    detail.setInstance(URI.create(request.getRequestURI()));
    return detail;
  }
}
