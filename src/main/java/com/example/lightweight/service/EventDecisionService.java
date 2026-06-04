package com.example.lightweight.service;

import com.example.lightweight.domain.EventType;
import com.example.lightweight.domain.KycResponse;
import com.example.lightweight.domain.KycStatus;

public class EventDecisionService {
    public EventType decideCustomerOutput(KycResponse response) {
        if (response == null || response.status() == null) {
            return EventType.CUSTOMER_ERROR;
        }
        if (response.status() == KycStatus.VERIFIED) {
            return EventType.CUSTOMER_VERIFIED;
        }
        if (response.status() == KycStatus.REJECTED) {
            return EventType.CUSTOMER_REJECTED;
        }
        return EventType.CUSTOMER_ERROR;
    }
}
