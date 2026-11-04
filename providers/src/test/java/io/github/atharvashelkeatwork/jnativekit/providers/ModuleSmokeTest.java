package io.github.atharvashelkeatwork.jnativekit.providers;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Milestone 0 smoke test: proves this module has a working test classpath and JUnit 5 execution,
 * and that the build really runs on Java 17 or newer (the library's minimum runtime).
 *
 * <p>Real provider tests arrive in later milestones; nothing here claims anything about an OS
 * feature.
 */
class ModuleSmokeTest {

    @Test
    void runsOnJava17OrNewer() {
        assertTrue(Runtime.version().feature() >= 17,
                "jnativekit requires Java 17 or newer, found " + Runtime.version());
    }
}
