package com.example.lightweight.kafka;

import com.example.lightweight.domain.CustomerEvent;
import com.example.lightweight.domain.CustomerResult;
import com.example.lightweight.service.CustomerVerificationService;

public class CustomerCreatedEventHandler {
    private final CustomerVerificationService service;

    public CustomerCreatedEventHandler(CustomerVerificationService service) {
        this.service = service;
    }

    public CustomerResult onMessage(CustomerEvent event) {
        return service.handleCustomerCreated(event);
    }
}
