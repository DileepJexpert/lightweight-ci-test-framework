package component.validation;

import com.example.lightweight.domain.CustomerCallbackRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("component")
@Tag("validation")
class RequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validCustomerCallbackHasNoViolations() {
        CustomerCallbackRequest request = new CustomerCallbackRequest("corr-1", "cust-1", "Asha", "Raman", "asha@example.test", "SALESFORCE");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void missingCorrelationIdAndInvalidEmailAreRejected() {
        CustomerCallbackRequest request = new CustomerCallbackRequest("", "cust-1", "Asha", "Raman", "bad-email", "SALESFORCE");

        assertThat(validator.validate(request)).hasSize(2);
    }
}
