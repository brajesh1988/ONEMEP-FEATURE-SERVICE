package com.netlink.onemep_feature.exception;

import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.common.dto.ApiResponse;
import com.netlink.onemep_feature.common.dto.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Central error → {@link ApiResponse} envelope translation (RFC 7807 disabled deliberately). */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

  private final ApiResponseAdaptor apiResponseAdaptor;

  @ExceptionHandler(ApplicationException.class)
  public ResponseEntity<ApiResponse<Void>> handleApplication(ApplicationException ex) {
    log.warn("ApplicationException: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(apiResponseAdaptor.error(ErrorCode.VALIDATION_FAILED, ex.getMessage(), true));
  }

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(apiResponseAdaptor.error(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), true));
  }

  @ExceptionHandler(DuplicateResourceException.class)
  public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateResourceException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(apiResponseAdaptor.error(ErrorCode.DUPLICATE_RESOURCE, ex.getMessage(), true));
  }

  @ExceptionHandler(ResourceInUseException.class)
  public ResponseEntity<ApiResponse<Void>> handleInUse(ResourceInUseException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(apiResponseAdaptor.error(ErrorCode.RESOURCE_IN_USE, ex.getMessage(), true));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream().map(FieldError::getDefaultMessage).toList();
    String first = errors.isEmpty() ? "Invalid request." : errors.get(0);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(apiResponseAdaptor.error(ErrorCode.VALIDATION_FAILED, first, errors, true));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            apiResponseAdaptor.error(
                ErrorCode.VALIDATION_FAILED, "Malformed or missing request body.", true));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingParam(
      MissingServletRequestParameterException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            apiResponseAdaptor.error(
                ErrorCode.VALIDATION_FAILED,
                "Missing required parameter: " + ex.getParameterName() + ".",
                true));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            apiResponseAdaptor.error(
                ErrorCode.VALIDATION_FAILED,
                "Invalid value for parameter '" + ex.getName() + "'.",
                true));
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodValidation(
      HandlerMethodValidationException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(
            apiResponseAdaptor.error(
                ErrorCode.VALIDATION_FAILED, "Invalid request parameters.", true));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(
            apiResponseAdaptor.error(
                ErrorCode.METHOD_NOT_ALLOWED,
                "HTTP method not supported for this endpoint.",
                true));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            apiResponseAdaptor.error(
                ErrorCode.RESOURCE_NOT_FOUND, "The requested resource was not found.", true));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(
            apiResponseAdaptor.error(
                ErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action.",
                true));
  }

  /**
   * FK / unique-constraint violations that slipped past service-layer checks (e.g. bad user id).
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiResponse<Void>> handleIntegrity(DataIntegrityViolationException ex) {
    log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            apiResponseAdaptor.error(
                ErrorCode.RESOURCE_IN_USE,
                "The request conflicts with existing data or references a record that does not"
                    + " exist.",
                true));
  }

  @ExceptionHandler(JwtException.class)
  public ResponseEntity<ApiResponse<Void>> handleJwt(JwtException ex) {
    log.warn("JWT error: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(
            apiResponseAdaptor.error(
                ErrorCode.TOKEN_INVALID, "Token is invalid or has expired.", true));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
    log.error("Unhandled exception", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            apiResponseAdaptor.error(
                ErrorCode.INTERNAL_ERROR, "An unexpected error occurred. Please try again.", true));
  }
}
