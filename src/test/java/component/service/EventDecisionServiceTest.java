package component.service;

import com.example.lightweight.domain.EventType;
import com.example.lightweight.domain.KycResponse;
import com.example.lightweight.domain.KycStatus;
import com.example.lightweight.service.EventDecisionService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("component")
@Tag("service")
class EventDecisionServiceTest {
    private final EventDecisionService service = new EventDecisionService();

    @Test
    void mapsVerifiedKycToCustomerVerifiedEvent() {
        assertThat(service.decideCustomerOutput(new KycResponse(KycStatus.VERIFIED, null))).isEqualTo(EventType.CUSTOMER_VERIFIED);
    }

    @Test
    void mapsRejectedKycToCustomerRejectedEvent() {
        assertThat(service.decideCustomerOutput(new KycResponse(KycStatus.REJECTED, "bad docs"))).isEqualTo(EventType.CUSTOMER_REJECTED);
    }

    @Test
    void nullResponseMapsToErrorEvent() {
        assertThat(service.decideCustomerOutput(null)).isEqualTo(EventType.CUSTOMER_ERROR);
    }
}
