package com.winworld.coursestools.exception;

import com.winworld.coursestools.exception.exceptions.BusinessException;
import com.winworld.coursestools.exception.exceptions.ConflictException;
import com.winworld.coursestools.exception.exceptions.DataValidationException;
import com.winworld.coursestools.exception.exceptions.EntityAlreadyExistException;
import com.winworld.coursestools.exception.exceptions.EntityNotFoundException;
import com.winworld.coursestools.exception.exceptions.ExternalServiceException;
import com.winworld.coursestools.exception.exceptions.PaymentProcessingException;
import com.winworld.coursestools.exception.exceptions.SecurityException;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse exception(final MethodArgumentNotValidException e) {
        var errors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new ErrorDto(
                        fieldError.getField(),
                        fieldError.getCode(),
                        fieldError.getDefaultMessage(),
                        fieldError.getRejectedValue().toString()
                ))
                .toList();
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "Validation failed",
                errors
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handle(final MissingRequestCookieException e) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Missing required cookie",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handle(final DataValidationException e) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Data validation exception",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handle(final BusinessException e) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Business Rule Violation",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handle(final EntityAlreadyExistException e) {
        return new ErrorResponse(
                HttpStatus.CONFLICT,
                "Entity already exist",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handle(final EntityNotFoundException e) {
        return new ErrorResponse(
                HttpStatus.NOT_FOUND,
                "Entity not found",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handle(final ConflictException e) {
        return new ErrorResponse(
                HttpStatus.CONFLICT,
                "Business Rule Conflict",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ErrorResponse handle(final ExternalServiceException e) {
        return new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "External service error",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handle(final SecurityException e) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Security error",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handle(final HttpMessageNotReadableException e) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Http message not readable",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handle(final HttpRequestMethodNotSupportedException e) {
        return new ErrorResponse(
                HttpStatus.BAD_REQUEST,
                "Http method error",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handle(final NoResourceFoundException e) {
        return new ErrorResponse(
                HttpStatus.NOT_FOUND,
                e.getMessage(),
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorResponse handle(final PaymentProcessingException e) {
        return new ErrorResponse(
                HttpStatus.BAD_GATEWAY,
                "Payment processing error",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handle(final AccessDeniedException e) {
        return new ErrorResponse(
                HttpStatus.FORBIDDEN,
                "Access denied",
                e.getMessage()
        );
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handle(final Exception e) {
        log.error(e.getMessage(), e);
        return new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                e.getMessage()
        );
    }
}
