package com.example.lightweight.kafka;

import com.example.lightweight.domain.PaymentEvent;
import com.example.lightweight.domain.PaymentResult;
import com.example.lightweight.service.PaymentProcessingService;

public class PaymentSuccessEventHandler {
    private final PaymentProcessingService service;

    public PaymentSuccessEventHandler(PaymentProcessingService service) {
        this.service = service;
    }

    public PaymentResult onMessage(PaymentEvent event) {
        return service.process(event);
    }
}
