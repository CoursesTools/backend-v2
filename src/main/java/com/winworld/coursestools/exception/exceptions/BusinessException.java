package com.winworld.coursestools.exception.exceptions;

public class BusinessException extends RuntimeException {
    public BusinessException(String emailAlreadyExists) {
        super(emailAlreadyExists);
    }
}
