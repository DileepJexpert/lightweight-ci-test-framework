package com.example.lightweight.controller;

import com.example.lightweight.domain.BankVerificationRequest;
import com.example.lightweight.domain.CustomerEvent;
import com.example.lightweight.domain.CustomerResult;
import com.example.lightweight.domain.EventType;
import com.example.lightweight.service.CustomerVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/callbacks/bank")
public class BankCallbackController {
    private final CustomerVerificationService service;

    public BankCallbackController(CustomerVerificationService service) {
        this.service = service;
    }

    @PostMapping("/verifications")
    public ResponseEntity<Map<String, Object>> receive(
            @RequestHeader("x-correlation-id") String correlationId,
            @Valid @org.springframework.web.bind.annotation.RequestBody BankVerificationRequest request
    ) {
        CustomerResult result = service.handleCustomerCreated(new CustomerEvent(
                "bank-" + UUID.randomUUID(),
                EventType.CUSTOMER_CREATED,
                correlationId,
                request.customerId()
        ));
        return ResponseEntity.ok(Map.of(
                "correlationId", result.correlationId(),
                "customerId", result.customerId(),
                "status", result.status().name()
        ));
    }
}
