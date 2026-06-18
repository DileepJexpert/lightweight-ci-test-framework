package com.example.lightweight.client;

import com.example.lightweight.domain.KycRequest;
import com.example.lightweight.domain.KycResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Real service-to-service implementation of {@link ThirdPartyKycClient}.
 *
 * <p>Service A (this application) calls Service B (the KYC service) over HTTP. In an EKS
 * cluster the {@code baseUrl} is the Kubernetes internal Service DNS name, for example
 * {@code http://kyc-service.loan-namespace.svc.cluster.local:8080}, so the request is
 * resolved by CoreDNS and routed to a healthy Service B pod — real in-cluster traffic.
 *
 * <p>A Karate smoke test never sees this call: it only talks to Service A. When Service A
 * receives the request it makes this synchronous downstream call, meaning the Karate
 * scenario exercises a true end-to-end A &rarr; B integration. If Service B is unreachable or
 * returns 5xx, {@link RestClient} throws a {@code RestClientException} (a
 * {@code RuntimeException}) which the calling service already treats as a retryable failure.
 */
public class RestClientKycClient implements ThirdPartyKycClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(RestClientKycClient.class);

    private final RestClient restClient;

    public RestClientKycClient(RestClient.Builder builder, String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
        LOGGER.info("KYC client configured for downstream service at {}", baseUrl);
    }

    @Override
    public KycResponse validate(KycRequest request) {
        LOGGER.debug("Calling downstream KYC service for correlationId={} customerId={}",
                request.correlationId(), request.customerId());
        return restClient.post()
                .uri("/kyc/validate")
                .header("x-correlation-id", request.correlationId())
                .body(request)
                .retrieve()
                .body(KycResponse.class);
    }
}
