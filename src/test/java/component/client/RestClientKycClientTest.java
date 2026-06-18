package component.client;

import com.example.lightweight.client.RestClientKycClient;
import com.example.lightweight.client.ThirdPartyKycClient;
import com.example.lightweight.domain.KycRequest;
import com.example.lightweight.domain.KycResponse;
import com.example.lightweight.domain.KycStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * Verifies the real service-to-service KYC client (Service A -> Service B) entirely
 * in-process using {@link MockRestServiceServer} — no Docker, no WireMock, no real socket.
 * This is the unit-level proof that Service A serialises the request, targets the right
 * downstream path, and surfaces downstream failures as exceptions the caller can retry on.
 */
@Tag("component")
@Tag("client")
class RestClientKycClientTest {

    private static final String BASE_URL = "http://kyc-service.loan-namespace.svc.cluster.local:8080";

    @Test
    void callsDownstreamServiceAndDeserialisesVerifiedResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ThirdPartyKycClient client = new RestClientKycClient(builder, BASE_URL);

        server.expect(requestTo(BASE_URL + "/kyc/validate"))
                .andExpect(method(POST))
                .andExpect(header("x-correlation-id", "corr-1"))
                .andExpect(jsonPath("$.customerId").value("cust-1"))
                .andRespond(withSuccess("{\"status\":\"VERIFIED\",\"reason\":null}", MediaType.APPLICATION_JSON));

        KycResponse response = client.validate(new KycRequest("corr-1", "cust-1"));

        assertThat(response.status()).isEqualTo(KycStatus.VERIFIED);
        server.verify();
    }

    @Test
    void downstreamServerErrorPropagatesAsRestClientException() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ThirdPartyKycClient client = new RestClientKycClient(builder, BASE_URL);

        server.expect(requestTo(BASE_URL + "/kyc/validate"))
                .andRespond(withServerError());

        // The caller (CustomerVerificationService) already catches RuntimeException and
        // maps it to PENDING_RETRY, so a downstream Service B outage degrades gracefully.
        assertThatThrownBy(() -> client.validate(new KycRequest("corr-2", "cust-2")))
                .isInstanceOf(RestClientException.class);
        server.verify();
    }
}
