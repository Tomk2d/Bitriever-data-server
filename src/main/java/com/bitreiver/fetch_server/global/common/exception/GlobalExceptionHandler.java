package com.bitreiver.fetch_server.global.common.exception;

import com.bitreiver.fetch_server.global.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ApiResponse<?>> handleCustomException(CustomException e) {
        ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
            .code(e.getErrorCode().getCode())
            .message(e.getMessage())
            .field(e.getField())
            .build();
            
        return ResponseEntity
            .status(e.getErrorCode().getHttpStatus())
            .body(ApiResponse.error(errorDetail));
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ApiResponse<?>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));
            
        ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
            .code(ErrorCode.BAD_REQUEST.getCode())
            .message(message)
            .build();
            
        return ResponseEntity
            .status(ErrorCode.BAD_REQUEST.getHttpStatus())
            .body(ApiResponse.error(errorDetail));
    }
    
    @ExceptionHandler(MissingServletRequestParameterException.class)
    protected ResponseEntity<ApiResponse<?>> handleMissingParameterException(MissingServletRequestParameterException e) {
        String message = String.format("필수 파라미터 '%s'가 누락되었습니다.", e.getParameterName());
        
        ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
            .code(ErrorCode.BAD_REQUEST.getCode())
            .message(message)
            .field(e.getParameterName())
            .build();
            
        return ResponseEntity
            .status(ErrorCode.BAD_REQUEST.getHttpStatus())
            .body(ApiResponse.error(errorDetail));
    }
    
    @ExceptionHandler(AuthenticationException.class)
    protected ResponseEntity<ApiResponse<?>> handleAuthenticationException(AuthenticationException e) {
        ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
            .code(ErrorCode.UNAUTHORIZED.getCode())
            .message("인증에 실패했습니다.")
            .build();
            
        return ResponseEntity
            .status(ErrorCode.UNAUTHORIZED.getHttpStatus())
            .body(ApiResponse.error(errorDetail));
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<ApiResponse<?>> handleAccessDeniedException(AccessDeniedException e) {
        ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
            .code(ErrorCode.FORBIDDEN.getCode())
            .message("접근 권한이 없습니다.")
            .build();
            
        return ResponseEntity
            .status(ErrorCode.FORBIDDEN.getHttpStatus())
            .body(ApiResponse.error(errorDetail));
    }
    
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error("서버 오류 발생: {}", e.getMessage(), e);
        
        ApiResponse.ErrorDetail errorDetail = ApiResponse.ErrorDetail.builder()
            .code(ErrorCode.INTERNAL_ERROR.getCode())
            .message(e.getMessage() != null ? e.getMessage() : "서버 오류가 발생했습니다.")
            .build();
            
        return ResponseEntity
            .status(ErrorCode.INTERNAL_ERROR.getHttpStatus())
            .body(ApiResponse.error(errorDetail));
    }
}

