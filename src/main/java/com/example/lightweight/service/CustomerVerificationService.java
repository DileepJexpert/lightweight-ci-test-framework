package com.example.lightweight.service;

import com.example.lightweight.client.ThirdPartyKycClient;
import com.example.lightweight.domain.CustomerEvent;
import com.example.lightweight.domain.CustomerResult;
import com.example.lightweight.domain.CustomerStatus;
import com.example.lightweight.domain.EventType;
import com.example.lightweight.domain.KycRequest;
import com.example.lightweight.domain.KycResponse;
import com.example.lightweight.kafka.EventPublisher;
import com.example.lightweight.repository.ProcessedEventRepository;
import com.example.lightweight.validation.EventValidator;

import java.net.SocketTimeoutException;
import java.util.UUID;

public class CustomerVerificationService {
    private final ThirdPartyKycClient kycClient;
    private final EventPublisher eventPublisher;
    private final ProcessedEventRepository repository;
    private final EventValidator validator;
    private final EventDecisionService decisionService;
    private final String verifiedTopic;
    private final String rejectedTopic;
    private final String errorTopic;

    public CustomerVerificationService(
            ThirdPartyKycClient kycClient,
            EventPublisher eventPublisher,
            ProcessedEventRepository repository,
            EventValidator validator,
            EventDecisionService decisionService,
            String verifiedTopic,
            String rejectedTopic,
            String errorTopic
    ) {
        this.kycClient = kycClient;
        this.eventPublisher = eventPublisher;
        this.repository = repository;
        this.validator = validator;
        this.decisionService = decisionService;
        this.verifiedTopic = verifiedTopic;
        this.rejectedTopic = rejectedTopic;
        this.errorTopic = errorTopic;
    }

    public CustomerResult handleCustomerCreated(CustomerEvent inputEvent) {
        if (!validator.isValidCustomerCreatedEvent(inputEvent)) {
            publish(inputEvent, EventType.CUSTOMER_ERROR, errorTopic);
            return new CustomerResult(correlationId(inputEvent), customerId(inputEvent), CustomerStatus.INVALID);
        }
        if (repository.existsByEventId(inputEvent.eventId())) {
            return new CustomerResult(inputEvent.correlationId(), inputEvent.customerId(), CustomerStatus.DUPLICATE);
        }

        try {
            KycResponse response = kycClient.validate(new KycRequest(inputEvent.correlationId(), inputEvent.customerId()));
            EventType outputType = decisionService.decideCustomerOutput(response);
            if (outputType == EventType.CUSTOMER_VERIFIED) {
                publish(inputEvent, outputType, verifiedTopic);
                repository.markProcessed(inputEvent.eventId());
                return new CustomerResult(inputEvent.correlationId(), inputEvent.customerId(), CustomerStatus.VERIFIED);
            }
            if (outputType == EventType.CUSTOMER_REJECTED) {
                publish(inputEvent, outputType, rejectedTopic);
                repository.markProcessed(inputEvent.eventId());
                return new CustomerResult(inputEvent.correlationId(), inputEvent.customerId(), CustomerStatus.REJECTED);
            }
            publish(inputEvent, EventType.CUSTOMER_ERROR, errorTopic);
            return new CustomerResult(inputEvent.correlationId(), inputEvent.customerId(), CustomerStatus.PENDING_RETRY);
        } catch (RuntimeException ex) {
            publish(inputEvent, EventType.CUSTOMER_ERROR, errorTopic);
            return new CustomerResult(inputEvent.correlationId(), inputEvent.customerId(), CustomerStatus.PENDING_RETRY);
        }
    }

    public CustomerResult handleThirdPartyTimeout(CustomerEvent inputEvent, SocketTimeoutException timeout) {
        publish(inputEvent, EventType.CUSTOMER_ERROR, errorTopic);
        return new CustomerResult(inputEvent.correlationId(), inputEvent.customerId(), CustomerStatus.PENDING_RETRY);
    }

    private void publish(CustomerEvent inputEvent, EventType outputType, String topic) {
        eventPublisher.publishCustomerEvent(topic, new CustomerEvent(
                "evt-" + UUID.randomUUID(),
                outputType,
                correlationId(inputEvent),
                customerId(inputEvent)
        ));
    }

    private String correlationId(CustomerEvent event) {
        return event == null ? "missing-correlation-id" : event.correlationId();
    }

    private String customerId(CustomerEvent event) {
        return event == null ? "missing-customer-id" : event.customerId();
    }
}
