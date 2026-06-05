package com.example.lightweight.config;

import com.example.lightweight.client.ThirdPartyKycClient;
import com.example.lightweight.domain.KycResponse;
import com.example.lightweight.domain.KycStatus;
import com.example.lightweight.kafka.EventPublisher;
import com.example.lightweight.repository.ProcessedEventRepository;
import com.example.lightweight.service.CustomerVerificationService;
import com.example.lightweight.service.EventDecisionService;
import com.example.lightweight.service.PaymentProcessingService;
import com.example.lightweight.validation.EventValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class LocalDemoConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalDemoConfiguration.class);

    @Bean
    EventValidator eventValidator() {
        return new EventValidator();
    }

    @Bean
    EventDecisionService eventDecisionService() {
        return new EventDecisionService();
    }

    @Bean
    ThirdPartyKycClient thirdPartyKycClient() {
        return request -> new KycResponse(KycStatus.VERIFIED, null);
    }

    @Bean
    EventPublisher eventPublisher() {
        return new EventPublisher() {
            @Override
            public void publishCustomerEvent(String topic, com.example.lightweight.domain.CustomerEvent event) {
                LOGGER.info("Local demo customer event published to topic {}: {}", topic, event);
            }

            @Override
            public void publishPaymentEvent(String topic, com.example.lightweight.domain.PaymentEvent event) {
                LOGGER.info("Local demo payment event published to topic {}: {}", topic, event);
            }
        };
    }

    @Bean
    ProcessedEventRepository processedEventRepository() {
        Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
        return new ProcessedEventRepository() {
            @Override
            public boolean existsByEventId(String eventId) {
                return processedEventIds.contains(eventId);
            }

            @Override
            public void markProcessed(String eventId) {
                processedEventIds.add(eventId);
            }
        };
    }

    @Bean
    CustomerVerificationService customerVerificationService(
            ThirdPartyKycClient kycClient,
            EventPublisher eventPublisher,
            ProcessedEventRepository repository,
            EventValidator validator,
            EventDecisionService decisionService
    ) {
        return new CustomerVerificationService(
                kycClient,
                eventPublisher,
                repository,
                validator,
                decisionService,
                "customer-verified",
                "customer-rejected",
                "customer-error"
        );
    }

    @Bean
    PaymentProcessingService paymentProcessingService(
            EventPublisher eventPublisher,
            ProcessedEventRepository repository,
            EventValidator validator
    ) {
        return new PaymentProcessingService(eventPublisher, repository, validator, "payment-processed");
    }
}
