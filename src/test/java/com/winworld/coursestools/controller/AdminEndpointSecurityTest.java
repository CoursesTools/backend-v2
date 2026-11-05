package com.winworld.coursestools.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Settings and system alerts must only ever be readable or changeable by admins (not partners).
 * Fails the build if an endpoint is added to these controllers without the ADMIN check.
 */
class AdminEndpointSecurityTest {

    @Test
    void everySettingsAndOpsAlertEndpointRequiresTheAdminRole() {
        for (Class<?> controller : List.of(AdminSettingsController.class, AdminOpsAlertController.class)) {
            List<Method> endpoints = Arrays.stream(controller.getDeclaredMethods())
                    .filter(AdminEndpointSecurityTest::isEndpoint)
                    .toList();
            assertFalse(endpoints.isEmpty(), controller.getSimpleName() + " has no endpoints");
            for (Method endpoint : endpoints) {
                PreAuthorize preAuthorize = endpoint.getAnnotation(PreAuthorize.class);
                assertEquals("hasRole('ADMIN')", preAuthorize == null ? null : preAuthorize.value(),
                        controller.getSimpleName() + "." + endpoint.getName() + " must be ADMIN-only");
            }
        }
    }

    private static boolean isEndpoint(Method method) {
        return method.isAnnotationPresent(GetMapping.class) || method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class) || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class) || method.isAnnotationPresent(RequestMapping.class);
    }
}
