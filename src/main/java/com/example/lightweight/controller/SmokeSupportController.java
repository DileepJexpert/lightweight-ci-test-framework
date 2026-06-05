package com.example.lightweight.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class SmokeSupportController {

    @GetMapping("/customers/smoke/status")
    public Map<String, Object> customerStatus() {
        return Map.of(
                "service", "lightweight-ci-test-framework",
                "status", "READY"
        );
    }

    @GetMapping("/protected/profile")
    public ResponseEntity<Map<String, Object>> protectedProfile(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "UNAUTHORIZED"));
        }
        return ResponseEntity.ok(Map.of(
                "user", "local-smoke-user",
                "status", "AUTHORIZED"
        ));
    }
}
