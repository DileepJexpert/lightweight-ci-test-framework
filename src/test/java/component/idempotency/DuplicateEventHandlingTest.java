package component.idempotency;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("component")
@Tag("idempotency")
class DuplicateEventHandlingTest {
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
    void duplicateEventSkipsThirdPartyAndPublisher() {
        CustomerEvent duplicate = new CustomerEvent("evt-dup", EventType.CUSTOMER_CREATED, "corr-dup", "cust-dup");
        when(repository.existsByEventId("evt-dup")).thenReturn(true);

        assertThat(service.handleCustomerCreated(duplicate).status()).isEqualTo(CustomerStatus.DUPLICATE);

        verify(client, never()).validate(any());
        verify(publisher, never()).publishCustomerEvent(any(), any());
    }
}
