package com.hanjisang.pis.security;

public class P15BusinessException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public P15BusinessException(String errorCode, String message) {
        this(errorCode, message, 422);
    }

    public P15BusinessException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
