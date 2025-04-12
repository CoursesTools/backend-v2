package com.winworld.coursestools.exception;

public class BusinessException extends RuntimeException {
    public BusinessException(String emailAlreadyExists) {
        super(emailAlreadyExists);
    }
}
