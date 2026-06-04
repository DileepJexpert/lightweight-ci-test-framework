package com.example.lightweight.kafka;

import com.example.lightweight.domain.CustomerEvent;
import com.example.lightweight.domain.PaymentEvent;

public interface EventPublisher {
    void publishCustomerEvent(String topic, CustomerEvent event);

    void publishPaymentEvent(String topic, PaymentEvent event);
}
