package karate.runner;

import com.intuit.karate.junit5.Karate;

class KarateSmokeTestRunner {

    @Karate.Test
    Karate runSmokeFeatures() {
        return Karate.run("classpath:karate/features").relativeTo(getClass());
    }
}
