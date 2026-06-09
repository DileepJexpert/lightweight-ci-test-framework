package karate.runner;

import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KarateSmokeTestRunner {

    @Test
    void runSmokeFeatures() {
        Results results = Runner.path("classpath:karate/features")
                .tags("@smoke", "~@ignore")
                .parallel(1);
        assertEquals(0, results.getFailCount(), results.getErrorMessages());
    }
}
