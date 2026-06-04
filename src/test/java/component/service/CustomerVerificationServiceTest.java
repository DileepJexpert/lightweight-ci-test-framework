package component.service;

import com.example.lightweight.client.ThirdPartyKycClient;
import com.example.lightweight.domain.CustomerEvent;
import com.example.lightweight.domain.CustomerResult;
import com.example.lightweight.domain.CustomerStatus;
import com.example.lightweight.domain.EventType;
import com.example.lightweight.domain.KycResponse;
import com.example.lightweight.domain.KycStatus;
import com.example.lightweight.kafka.EventPublisher;
import com.example.lightweight.repository.ProcessedEventRepository;
import com.example.lightweight.service.CustomerVerificationService;
import com.example.lightweight.service.EventDecisionService;
import com.example.lightweight.validation.EventValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("component")
@Tag("service")
class CustomerVerificationServiceTest {
    private final ThirdPartyKycClient kycClient = mock(ThirdPartyKycClient.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
    private CustomerVerificationService service;

    @BeforeEach
    void setUp() {
        service = new CustomerVerificationService(
                kycClient,
                publisher,
                repository,
                new EventValidator(),
                new EventDecisionService(),
                "customer-verified",
                "customer-rejected",
                "customer-error"
        );
    }

    @Test
    void verifiedKycPublishesCustomerVerifiedEventWithCorrelationId() {
        CustomerEvent input = customerCreated();
        when(repository.existsByEventId(input.eventId())).thenReturn(false);
        when(kycClient.validate(any())).thenReturn(new KycResponse(KycStatus.VERIFIED, null));

        CustomerResult result = service.handleCustomerCreated(input);

        assertThat(result.status()).isEqualTo(CustomerStatus.VERIFIED);
        ArgumentCaptor<CustomerEvent> output = ArgumentCaptor.forClass(CustomerEvent.class);
        verify(publisher).publishCustomerEvent(eq("customer-verified"), output.capture());
        assertThat(output.getValue().eventType()).isEqualTo(EventType.CUSTOMER_VERIFIED);
        assertThat(output.getValue().correlationId()).isEqualTo(input.correlationId());
        verify(repository).markProcessed(input.eventId());
    }

    @Test
    void rejectedKycPublishesCustomerRejectedEvent() {
        CustomerEvent input = customerCreated();
        when(kycClient.validate(any())).thenReturn(new KycResponse(KycStatus.REJECTED, "DOCUMENT_MISMATCH"));

        CustomerResult result = service.handleCustomerCreated(input);

        assertThat(result.status()).isEqualTo(CustomerStatus.REJECTED);
        verify(publisher).publishCustomerEvent(eq("customer-rejected"), any());
    }

    @Test
    void thirdPartyFailurePublishesErrorEventAndMarksPendingRetry() {
        CustomerEvent input = customerCreated();
        when(kycClient.validate(any())).thenThrow(new RuntimeException("500 from third party"));

        CustomerResult result = service.handleCustomerCreated(input);

        assertThat(result.status()).isEqualTo(CustomerStatus.PENDING_RETRY);
        verify(publisher).publishCustomerEvent(eq("customer-error"), any());
        verify(repository, never()).markProcessed(input.eventId());
    }

    @Test
    void timeoutPublishesErrorEventAndMarksPendingRetry() {
        CustomerEvent input = customerCreated();

        CustomerResult result = service.handleThirdPartyTimeout(input, new SocketTimeoutException("timeout"));

        assertThat(result.status()).isEqualTo(CustomerStatus.PENDING_RETRY);
        verify(publisher).publishCustomerEvent(eq("customer-error"), any());
    }

    @Test
    void duplicateEventDoesNotPublishOutputEvent() {
        CustomerEvent input = customerCreated();
        when(repository.existsByEventId(input.eventId())).thenReturn(true);

        CustomerResult result = service.handleCustomerCreated(input);

        assertThat(result.status()).isEqualTo(CustomerStatus.DUPLICATE);
        verify(publisher, never()).publishCustomerEvent(any(), any());
    }

    private CustomerEvent customerCreated() {
        return new CustomerEvent("evt-1", EventType.CUSTOMER_CREATED, "corr-1", "cust-1");
    }
}
