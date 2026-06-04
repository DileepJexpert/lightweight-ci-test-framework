package component.kafka;

import com.example.lightweight.domain.EventType;
import com.example.lightweight.domain.PaymentEvent;
import com.example.lightweight.domain.PaymentResult;
import com.example.lightweight.kafka.PaymentSuccessEventHandler;
import com.example.lightweight.service.PaymentProcessingService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("component")
@Tag("kafka-handler")
class PaymentSuccessEventHandlerTest {
    private final PaymentProcessingService service = mock(PaymentProcessingService.class);
    private final PaymentSuccessEventHandler handler = new PaymentSuccessEventHandler(service);

    @Test
    void invokesPaymentServiceDirectlyWithoutKafkaBroker() {
        PaymentEvent event = new PaymentEvent("evt-pay", EventType.PAYMENT_SUCCESS, "corr-pay", "pay-1", BigDecimal.TEN);
        when(service.process(event)).thenReturn(new PaymentResult("corr-pay", "pay-1", true));

        PaymentResult result = handler.onMessage(event);

        assertThat(result.processed()).isTrue();
        verify(service).process(event);
    }
}
