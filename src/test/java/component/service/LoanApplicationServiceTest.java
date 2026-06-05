package component.service;

import com.example.lightweight.domain.LoanApplicationRequest;
import com.example.lightweight.domain.LoanApplicationResult;
import com.example.lightweight.domain.LoanDecision;
import com.example.lightweight.domain.LoanStatus;
import com.example.lightweight.domain.ManualReviewRequest;
import com.example.lightweight.repository.LoanApplicationRepository;
import com.example.lightweight.service.LoanApplicationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("component")
@Tag("service")
class LoanApplicationServiceTest {
    private final Map<String, LoanApplicationResult> store = new HashMap<>();
    private final LoanApplicationService service = new LoanApplicationService(new LoanApplicationRepository() {
        public void save(LoanApplicationResult result) { store.put(result.loanId(), result); }
        public Optional<LoanApplicationResult> findByLoanId(String loanId) { return Optional.ofNullable(store.get(loanId)); }
        public Optional<LoanApplicationResult> findByIdempotencyKey(String key) {
            return store.values().stream().filter(result -> key.equals(result.idempotencyKey())).findFirst();
        }
    });

    @AfterEach
    void closeScheduler() {
        service.close();
    }

    @Test
    void straightThroughApprovalGeneratesOfferAndAuditTimeline() {
        LoanApplicationResult result = service.initiate(request("STANDARD", 760, 90000, 18000), "idem-1");

        assertThat(result.status()).isEqualTo(LoanStatus.APPROVED);
        assertThat(result.decision()).isEqualTo(LoanDecision.APPROVED);
        assertThat(result.offerId()).startsWith("offer-");
        assertThat(result.timeline()).extracting("name").contains(
                "KYC_VALIDATION", "CREDIT_BUREAU", "FRAUD_SCREENING", "UNDERWRITING", "OFFER_GENERATION");
    }

    @Test
    void duplicateSubmissionReturnsOriginalApplication() {
        LoanApplicationResult first = service.initiate(request("STANDARD", 760, 90000, 18000), "same-key");
        LoanApplicationResult duplicate = service.initiate(request("STANDARD", 760, 90000, 18000), "same-key");

        assertThat(duplicate.loanId()).isEqualTo(first.loanId());
        assertThat(store).hasSize(1);
    }

    @Test
    void manualReviewCanApproveBorderlineApplication() {
        LoanApplicationResult pending = service.initiate(request("MANUAL_REVIEW", 710, 65000, 22000), "idem-review");
        LoanApplicationResult approved = service.review(pending.loanId(), new ManualReviewRequest("manager-1", "APPROVE"));

        assertThat(pending.status()).isEqualTo(LoanStatus.MANUAL_REVIEW);
        assertThat(approved.status()).isEqualTo(LoanStatus.APPROVED);
        assertThat(approved.offerId()).isNotBlank();
    }

    @Test
    void offerFailureReversesApproval() {
        LoanApplicationResult result = service.initiate(request("OFFER_FAILURE", 770, 100000, 15000), "idem-compensation");

        assertThat(result.status()).isEqualTo(LoanStatus.APPROVAL_REVERSED);
        assertThat(result.decision()).isEqualTo(LoanDecision.REVERSED);
        assertThat(result.timeline()).extracting("name").contains("OFFER_GENERATION", "COMPENSATION");
    }

    private LoanApplicationRequest request(String mode, int creditScore, long income, long debt) {
        return new LoanApplicationRequest("corr-1", "cust-1", BigDecimal.valueOf(750000), creditScore,
                BigDecimal.valueOf(income), BigDecimal.valueOf(debt), mode);
    }
}
