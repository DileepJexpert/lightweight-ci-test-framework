package component.validation;

import com.example.lightweight.domain.CustomerEvent;
import com.example.lightweight.domain.EventType;
import com.example.lightweight.validation.EventValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("component")
@Tag("validation")
class EventValidationTest {
    private final EventValidator validator = new EventValidator();

    @Test
    void validCustomerCreatedEventPasses() {
        assertThat(validator.isValidCustomerCreatedEvent(new CustomerEvent("evt-1", EventType.CUSTOMER_CREATED, "corr-1", "cust-1"))).isTrue();
    }

    @Test
    void missingCorrelationIdFails() {
        assertThat(validator.isValidCustomerCreatedEvent(new CustomerEvent("evt-2", EventType.CUSTOMER_CREATED, "", "cust-1"))).isFalse();
    }

    @Test
    void unsupportedEventTypeFailsCustomerCreatedValidation() {
        assertThat(validator.isValidCustomerCreatedEvent(new CustomerEvent("evt-3", EventType.CUSTOMER_VERIFIED, "corr-3", "cust-3"))).isFalse();
    }
}
