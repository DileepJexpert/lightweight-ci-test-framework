package karate.runner;

import com.intuit.karate.junit5.Karate;

/**
 * Production smoke test runner — executes ONLY @prod-safe tagged features.
 *
 * Safe to run against live production environments:
 *   - No POST / PUT / DELETE calls
 *   - No Kafka events published
 *   - No business data created or modified
 *
 * GoCD production pipeline command:
 *   mvn verify -Psmoke
 *     -Dservice.base-url=https://loan-api.company.com
 *     -Dkarate.env=production
 *     -Dauth.token=${EKS_AUTH_TOKEN}
 *
 * Features run: prod-smoke/ folder only (health, auth, connectivity, SLA)
 * Features skipped: business/, rest/ (write tests — never run in production)
 */
class KarateProdSmokeRunner {

    @Karate.Test
    Karate runProdSafeFeatures() {
        return Karate.run("classpath:karate/features/prod-smoke")
                .tags("@prod-safe")
                .relativeTo(getClass());
    }
}
