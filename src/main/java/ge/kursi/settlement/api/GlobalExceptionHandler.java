package ge.kursi.settlement.api;

import ge.kursi.settlement.api.dto.ApiErrorResponse;
import ge.kursi.settlement.service.FundingRequestNotFoundException;
import ge.kursi.settlement.service.InvalidFundingRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every failure to a consistent JSON error body with a descriptive message */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest request) {
        List<String> details = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::describe)
                .sorted()
                .forEach(details::add);
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> details.add(error.getDefaultMessage()));
        return badRequest("Request validation failed", details, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleParameterValidation(HandlerMethodValidationException ex,
                                                                      HttpServletRequest request) {
        List<String> details = ex.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .sorted()
                .toList();
        return badRequest("Request validation failed", details, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                      HttpServletRequest request) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .sorted()
                .toList();
        return badRequest("Request validation failed", details, request);
    }

    @ExceptionHandler(InvalidFundingRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(InvalidFundingRequestException ex,
                                                                 HttpServletRequest request) {
        return badRequest(ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                                 HttpServletRequest request) {
        String reason = ex.getMostSpecificCause().getMessage();
        String message = "Malformed request body" + (reason == null ? "" : ": " + firstLine(reason));
        return badRequest(message, List.of(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                               HttpServletRequest request) {
        String expected = ex.getRequiredType() == null ? "expected type" : ex.getRequiredType().getSimpleName();
        String message = "Parameter '" + ex.getName() + "' has invalid value '" + ex.getValue()
                + "': expected a valid " + expected;
        return badRequest(message, List.of(), request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                   HttpServletRequest request) {
        return badRequest("Missing required parameter '" + ex.getParameterName() + "'", List.of(), request);
    }

    @ExceptionHandler(FundingRequestNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(FundingRequestNotFoundException ex,
                                                           HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex,
                                                             HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "No endpoint at " + request.getRequestURI(), List.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                     HttpServletRequest request) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex,
                                                            HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", List.of(), request);
    }

    private static String describe(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }

    private static ResponseEntity<ApiErrorResponse> badRequest(String message, List<String> details,
                                                               HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, message, details, request);
    }

    private static ResponseEntity<ApiErrorResponse> error(HttpStatus status, String message, List<String> details,
                                                          HttpServletRequest request) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }
}
