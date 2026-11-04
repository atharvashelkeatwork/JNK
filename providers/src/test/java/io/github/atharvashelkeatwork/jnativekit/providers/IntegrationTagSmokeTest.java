package io.github.atharvashelkeatwork.jnativekit.providers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Proves the {@code integration} Maven profile wiring: this test only runs with
 * {@code mvn -B verify -Pintegration}, so CI shows that the ci-integration tier actually executes
 * on ubuntu, windows and macos.
 *
 * <p>It is deliberately feature-free: it asserts nothing about notifications, autostart or tray.
 * It only records that the real CI OS is reachable from that tier.
 */
@Tag("integration")
class IntegrationTagSmokeTest {

    @Test
    void ciIntegrationTierRunsOnARealOs() {
        String osName = System.getProperty("os.name");
        assertNotNull(osName, "os.name must be set on a real JVM");
        assertFalse(osName.isBlank(), "os.name must not be blank");
    }
}
