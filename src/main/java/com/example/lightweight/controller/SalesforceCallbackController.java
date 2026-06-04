package com.example.lightweight.controller;

import com.example.lightweight.domain.CustomerCallbackRequest;
import com.example.lightweight.domain.CustomerEvent;
import com.example.lightweight.domain.CustomerResult;
import com.example.lightweight.domain.EventType;
import com.example.lightweight.service.CustomerVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/callbacks/salesforce")
public class SalesforceCallbackController {
    private final CustomerVerificationService service;

    public SalesforceCallbackController(CustomerVerificationService service) {
        this.service = service;
    }

    @PostMapping("/customers")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("x-correlation-id") String correlationId,
            @Valid @org.springframework.web.bind.annotation.RequestBody CustomerCallbackRequest request
    ) {
        if (!authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("status", "UNAUTHORIZED"));
        }
        CustomerResult result = service.handleCustomerCreated(new CustomerEvent(
                "rest-" + UUID.randomUUID(),
                EventType.CUSTOMER_CREATED,
                correlationId,
                request.customerId()
        ));
        return ResponseEntity.accepted().body(Map.of(
                "correlationId", result.correlationId(),
                "customerId", result.customerId(),
                "status", result.status().name()
        ));
    }
}
