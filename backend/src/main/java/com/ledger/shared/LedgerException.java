package com.ledger.shared;

import org.springframework.http.HttpStatus;

public class LedgerException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public LedgerException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public static LedgerException businessRule(String code, String message) {
        return new LedgerException(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    public static LedgerException badRequest(String code, String message) {
        return new LedgerException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static LedgerException notFound(String message) {
        return new LedgerException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static LedgerException unauthorized(String message) {
        return new LedgerException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static LedgerException forbidden(String message) {
        return new LedgerException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static LedgerException conflict(String code, String message) {
        return new LedgerException(HttpStatus.CONFLICT, code, message);
    }

    public static LedgerException tooManyRequests(String message) {
        return new LedgerException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", message);
    }
}
