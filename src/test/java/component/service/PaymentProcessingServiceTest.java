package component.service;

import com.example.lightweight.domain.EventType;
import com.example.lightweight.domain.PaymentEvent;
import com.example.lightweight.kafka.EventPublisher;
import com.example.lightweight.repository.ProcessedEventRepository;
import com.example.lightweight.service.PaymentProcessingService;
import com.example.lightweight.validation.EventValidator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("component")
@Tag("service")
class PaymentProcessingServiceTest {
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
    private final PaymentProcessingService service = new PaymentProcessingService(publisher, repository, new EventValidator(), "payment-processed");

    @Test
    void validPaymentPublishesProcessedEvent() {
        PaymentEvent input = new PaymentEvent("evt-pay-1", EventType.PAYMENT_SUCCESS, "corr-pay-1", "pay-1", BigDecimal.TEN);

        assertThat(service.process(input).processed()).isTrue();

        ArgumentCaptor<PaymentEvent> output = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(publisher).publishPaymentEvent(eq("payment-processed"), output.capture());
        assertThat(output.getValue().eventType()).isEqualTo(EventType.PAYMENT_PROCESSED);
        assertThat(output.getValue().correlationId()).isEqualTo("corr-pay-1");
        assertThat(output.getValue().paymentId()).isEqualTo("pay-1");
    }

    @Test
    void duplicatePaymentDoesNotPublishAgain() {
        PaymentEvent input = new PaymentEvent("evt-pay-2", EventType.PAYMENT_SUCCESS, "corr-pay-2", "pay-2", BigDecimal.ONE);
        when(repository.existsByEventId("evt-pay-2")).thenReturn(true);

        assertThat(service.process(input).processed()).isFalse();

        verify(publisher, never()).publishPaymentEvent(any(), any());
    }
}
