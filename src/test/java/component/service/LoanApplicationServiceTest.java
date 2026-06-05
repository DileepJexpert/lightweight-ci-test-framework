package component.service;

import com.example.lightweight.domain.LoanApplicationRequest;
import com.example.lightweight.domain.LoanApplicationResult;
import com.example.lightweight.domain.LoanDecision;
import com.example.lightweight.domain.LoanStatus;
import com.example.lightweight.repository.LoanApplicationRepository;
import com.example.lightweight.service.LoanApplicationService;
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
        @Override
        public void save(LoanApplicationResult result) {
            store.put(result.loanId(), result);
        }

        @Override
        public Optional<LoanApplicationResult> findByLoanId(String loanId) {
            return Optional.ofNullable(store.get(loanId));
        }
    });

    @Test
    void approvedLoanMovesThroughAllBusinessSteps() {
        LoanApplicationResult result = service.initiate(new LoanApplicationRequest(
                "corr-loan-approved",
                "cust-approved",
                BigDecimal.valueOf(750000),
                760,
                BigDecimal.valueOf(90000),
                BigDecimal.valueOf(18000)
        ));

        assertThat(result.status()).isEqualTo(LoanStatus.APPROVED);
        assertThat(result.decision()).isEqualTo(LoanDecision.APPROVED);
        assertThat(result.timeline()).extracting("name")
                .containsExactly("LOAN_INITIATED", "ELIGIBILITY_CHECK", "UNDERWRITING", "FINAL_DECISION");
        assertThat(service.find(result.loanId()).correlationId()).isEqualTo("corr-loan-approved");
    }

    @Test
    void rejectedLoanRecordsFailedPolicySteps() {
        LoanApplicationResult result = service.initiate(new LoanApplicationRequest(
                "corr-loan-rejected",
                "cust-rejected",
                BigDecimal.valueOf(1500000),
                650,
                BigDecimal.valueOf(45000),
                BigDecimal.valueOf(25000)
        ));

        assertThat(result.status()).isEqualTo(LoanStatus.REJECTED);
        assertThat(result.decision()).isEqualTo(LoanDecision.REJECTED);
        assertThat(result.timeline()).extracting("outcome")
                .containsExactly("PASS", "FAIL", "FAIL", "REJECTED");
    }
}
