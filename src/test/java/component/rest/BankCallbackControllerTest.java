package component.rest;

import com.example.lightweight.controller.BankCallbackController;
import com.example.lightweight.domain.CustomerResult;
import com.example.lightweight.domain.CustomerStatus;
import com.example.lightweight.service.CustomerVerificationService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
class BankCallbackControllerTest {
    private final CustomerVerificationService service = mock(CustomerVerificationService.class);
    private final MockMvc mockMvc = standaloneSetup(new BankCallbackController(service)).build();

    @Test
    void validBankCallbackReturnsOk() throws Exception {
        when(service.handleCustomerCreated(any())).thenReturn(new CustomerResult("corr-bank-1", "cust-bank-1", CustomerStatus.VERIFIED));

        mockMvc.perform(post("/callbacks/bank/verifications")
                        .header("x-correlation-id", "corr-bank-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "corr-bank-1",
                                  "customerId": "cust-bank-1",
                                  "accountNumber": "acct-1",
                                  "requestType": "CUSTOMER_VERIFICATION"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId").value("corr-bank-1"));
    }

    @Test
    void validationFailureDoesNotCallService() throws Exception {
        mockMvc.perform(post("/callbacks/bank/verifications")
                        .header("x-correlation-id", "corr-bank-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "correlationId": "corr-bank-2",
                                  "accountNumber": "",
                                  "requestType": "CUSTOMER_VERIFICATION"
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(service, never()).handleCustomerCreated(any());
    }
}
