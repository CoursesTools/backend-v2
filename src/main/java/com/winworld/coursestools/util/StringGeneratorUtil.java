package com.winworld.coursestools.util;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StringGeneratorUtil {

    public String generatePassword() {
        return UUID.randomUUID()
                .toString()
                .replaceAll("-", "")
                .substring(0, 10);
    }

    public String generatePartnerCode() {
        return UUID.randomUUID()
                .toString()
                .replaceAll("-", "")
                .substring(0, 7);
    }

    public String generateToken() {
        return UUID.randomUUID()
                .toString()
                .replaceAll("-", "")
                .substring(0, 8);
    }
}
