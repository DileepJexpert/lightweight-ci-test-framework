package com.example.lightweight.config;

import com.example.lightweight.client.RestClientKycClient;
import com.example.lightweight.client.ThirdPartyKycClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the real service-to-service KYC client.
 *
 * <p>Active only when {@code kyc.service.base-url} is provided (QA / SIT / EKS). When the
 * property is absent — for example the local demo — this bean is skipped and the in-memory
 * stub in {@link LocalDemoConfiguration} is used instead. The two beans are mutually
 * exclusive on the same property, so there is never a conflict regardless of config order.
 *
 * <p>Example (Kubernetes internal Service DNS):
 * {@code --kyc.service.base-url=http://kyc-service.loan-namespace.svc.cluster.local:8080}
 */
@Configuration
public class ServiceClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "kyc.service.base-url")
    ThirdPartyKycClient thirdPartyKycClient(
            @Value("${kyc.service.base-url}") String baseUrl,
            RestClient.Builder restClientBuilder
    ) {
        return new RestClientKycClient(restClientBuilder, baseUrl);
    }
}
