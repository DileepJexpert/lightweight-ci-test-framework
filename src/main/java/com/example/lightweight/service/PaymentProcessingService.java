package com.example.lightweight.service;

import com.example.lightweight.domain.EventType;
import com.example.lightweight.domain.PaymentEvent;
import com.example.lightweight.domain.PaymentResult;
import com.example.lightweight.kafka.EventPublisher;
import com.example.lightweight.repository.ProcessedEventRepository;
import com.example.lightweight.validation.EventValidator;

import java.util.UUID;

public class PaymentProcessingService {
    private final EventPublisher eventPublisher;
    private final ProcessedEventRepository repository;
    private final EventValidator validator;
    private final String outputTopic;

    public PaymentProcessingService(EventPublisher eventPublisher, ProcessedEventRepository repository, EventValidator validator, String outputTopic) {
        this.eventPublisher = eventPublisher;
        this.repository = repository;
        this.validator = validator;
        this.outputTopic = outputTopic;
    }

    public PaymentResult process(PaymentEvent event) {
        if (!validator.isValidPaymentSuccessEvent(event)) {
            return new PaymentResult(event == null ? null : event.correlationId(), event == null ? null : event.paymentId(), false);
        }
        if (repository.existsByEventId(event.eventId())) {
            return new PaymentResult(event.correlationId(), event.paymentId(), false);
        }
        eventPublisher.publishPaymentEvent(outputTopic, new PaymentEvent(
                "evt-" + UUID.randomUUID(),
                EventType.PAYMENT_PROCESSED,
                event.correlationId(),
                event.paymentId(),
                event.amount()
        ));
        repository.markProcessed(event.eventId());
        return new PaymentResult(event.correlationId(), event.paymentId(), true);
    }
}
