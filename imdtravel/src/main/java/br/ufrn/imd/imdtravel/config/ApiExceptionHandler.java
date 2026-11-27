package br.ufrn.imd.imdtravel.config;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import br.ufrn.imd.imdtravel.dtos.config.ErrorResponse;
import br.ufrn.imd.imdtravel.dtos.config.SaleUnknownResponse;
import br.ufrn.imd.imdtravel.exceptions.DeadlineExceededException;
import br.ufrn.imd.imdtravel.exceptions.InvalidRateException;
import br.ufrn.imd.imdtravel.exceptions.InvalidRequestException;
import br.ufrn.imd.imdtravel.exceptions.NotFoundException;
import br.ufrn.imd.imdtravel.exceptions.SaleUnknownException;
import br.ufrn.imd.imdtravel.exceptions.SoldOutException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({InvalidRequestException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> invalid(Exception e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("invalid-request"));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("not-found"));
    }

    @ExceptionHandler(SoldOutException.class)
    public ResponseEntity<ErrorResponse> soldOut(SoldOutException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("sold-out"));
    }

    @ExceptionHandler(SaleUnknownException.class)
    public ResponseEntity<SaleUnknownResponse> saleUnknown(SaleUnknownException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new SaleUnknownResponse("unknown", "sale-unknown"));
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> circuitOpen(CallNotPermittedException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse("service-unavailable"));
    }

    @ExceptionHandler(InvalidRateException.class)
    public ResponseEntity<ErrorResponse> invalidRate(InvalidRateException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse("service-unavailable"));
    }

    @ExceptionHandler(DeadlineExceededException.class)
    public ResponseEntity<ErrorResponse> deadlineExceeded(DeadlineExceededException e) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(new ErrorResponse("gateway-timeout"));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> resourceAccess(ResourceAccessException e) {
        if (hasTimeoutCause(e)) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(new ErrorResponse("gateway-timeout"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse("service-unavailable"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> internal(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("internal-error"));
    }

    private boolean hasTimeoutCause(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof HttpTimeoutException || cur instanceof SocketTimeoutException) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
