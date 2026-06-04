package component.client;

import com.example.lightweight.client.ThirdPartyKycClient;
import com.example.lightweight.domain.CustomerEvent;
import com.example.lightweight.domain.CustomerStatus;
import com.example.lightweight.domain.EventType;
import com.example.lightweight.kafka.EventPublisher;
import com.example.lightweight.repository.ProcessedEventRepository;
import com.example.lightweight.service.CustomerVerificationService;
import com.example.lightweight.service.EventDecisionService;
import com.example.lightweight.validation.EventValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("component")
@Tag("service")
class ThirdPartyClientFailureHandlingTest {
    private final ThirdPartyKycClient client = mock(ThirdPartyKycClient.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
    private final CustomerVerificationService service = new CustomerVerificationService(
            client,
            publisher,
            repository,
            new EventValidator(),
            new EventDecisionService(),
            "customer-verified",
            "customer-rejected",
            "customer-error"
    );

    @Test
    void thirdPartyInternalServerErrorProducesErrorEvent() {
        CustomerEvent event = new CustomerEvent("evt-500", EventType.CUSTOMER_CREATED, "corr-500", "cust-500");
        when(client.validate(any())).thenThrow(new RuntimeException("HTTP 500"));

        assertThat(service.handleCustomerCreated(event).status()).isEqualTo(CustomerStatus.PENDING_RETRY);

        verify(publisher).publishCustomerEvent(eq("customer-error"), any());
    }

    @Test
    void malformedThirdPartyResponseProducesErrorEvent() {
        CustomerEvent event = new CustomerEvent("evt-bad-json", EventType.CUSTOMER_CREATED, "corr-bad-json", "cust-bad-json");
        when(client.validate(any())).thenReturn(null);

        assertThat(service.handleCustomerCreated(event).status()).isEqualTo(CustomerStatus.PENDING_RETRY);

        verify(publisher).publishCustomerEvent(eq("customer-error"), any());
    }
}
