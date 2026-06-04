package com.example.lightweight.validation;

import com.example.lightweight.domain.CustomerEvent;
import com.example.lightweight.domain.EventType;
import com.example.lightweight.domain.PaymentEvent;

public class EventValidator {
    public boolean isValidCustomerCreatedEvent(CustomerEvent event) {
        return event != null
                && event.eventType() == EventType.CUSTOMER_CREATED
                && hasText(event.eventId())
                && hasText(event.correlationId())
                && hasText(event.customerId());
    }

    public boolean isValidPaymentSuccessEvent(PaymentEvent event) {
        return event != null
                && event.eventType() == EventType.PAYMENT_SUCCESS
                && hasText(event.eventId())
                && hasText(event.correlationId())
                && hasText(event.paymentId())
                && event.amount() != null
                && event.amount().signum() > 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
