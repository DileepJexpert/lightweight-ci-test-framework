package component.rest;

import com.example.lightweight.controller.SalesforceCallbackController;
import com.example.lightweight.domain.CustomerResult;
import com.example.lightweight.domain.CustomerStatus;
import com.example.lightweight.service.CustomerVerificationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@Tag("component")
@Tag("rest")
class SalesforceCallbackControllerTest {
    private final CustomerVerificationService service = mock(CustomerVerificationService.class);
    private final MockMvc mockMvc = standaloneSetup(new SalesforceCallbackController(service)).build();

    @Test
    void validRequestReturnsAcceptedAndCallsServiceWithCorrelationId() throws Exception {
        when(service.handleCustomerCreated(any())).thenReturn(new CustomerResult("corr-1", "cust-1", CustomerStatus.VERIFIED));

        mockMvc.perform(post("/callbacks/salesforce/customers")
                        .header("Authorization", "Bearer token")
                        .header("x-correlation-id", "corr-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "corr-1",
                                  "customerId": "cust-1",
                                  "firstName": "Asha",
                                  "lastName": "Raman",
                                  "email": "asha@example.test",
                                  "source": "SALESFORCE"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.correlationId").value("corr-1"))
                .andExpect(jsonPath("$.status").value("VERIFIED"));

        ArgumentCaptor<com.example.lightweight.domain.CustomerEvent> event = ArgumentCaptor.forClass(com.example.lightweight.domain.CustomerEvent.class);
        verify(service).handleCustomerCreated(event.capture());
        assertThat(event.getValue().correlationId()).isEqualTo("corr-1");
        assertThat(event.getValue().customerId()).isEqualTo("cust-1");
    }

    @Test
    void invalidRequestReturnsBadRequestAndDoesNotCallService() throws Exception {
        mockMvc.perform(post("/callbacks/salesforce/customers")
                        .header("Authorization", "Bearer token")
                        .header("x-correlation-id", "corr-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "corr-2",
                                  "customerId": "",
                                  "email": "not-an-email",
                                  "source": "SALESFORCE"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(service, never()).handleCustomerCreated(any());
    }

    @Test
    void invalidAuthorizationReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/callbacks/salesforce/customers")
                        .header("Authorization", "Basic wrong")
                        .header("x-correlation-id", "corr-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "corr-3",
                                  "customerId": "cust-3",
                                  "firstName": "Asha",
                                  "lastName": "Raman",
                                  "email": "asha@example.test",
                                  "source": "SALESFORCE"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }
}
