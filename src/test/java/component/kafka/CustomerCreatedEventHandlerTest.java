package component.kafka;

import com.example.lightweight.domain.CustomerEvent;
import com.example.lightweight.domain.CustomerResult;
import com.example.lightweight.domain.CustomerStatus;
import com.example.lightweight.domain.EventType;
import com.example.lightweight.kafka.CustomerCreatedEventHandler;
import com.example.lightweight.service.CustomerVerificationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("component")
@Tag("kafka-handler")
class CustomerCreatedEventHandlerTest {
    private final CustomerVerificationService service = mock(CustomerVerificationService.class);
    private final CustomerCreatedEventHandler handler = new CustomerCreatedEventHandler(service);

    @Test
    void invokesServiceDirectlyWithoutKafkaBroker() {
        CustomerEvent event = new CustomerEvent("evt-1", EventType.CUSTOMER_CREATED, "corr-1", "cust-1");
        when(service.handleCustomerCreated(event)).thenReturn(new CustomerResult("corr-1", "cust-1", CustomerStatus.VERIFIED));

        CustomerResult result = handler.onMessage(event);

        assertThat(result.status()).isEqualTo(CustomerStatus.VERIFIED);
        verify(service).handleCustomerCreated(event);
    }
}
