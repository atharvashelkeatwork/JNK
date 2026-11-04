package io.github.atharvashelkeatwork.jnativekit.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Milestone 0 smoke test: proves the demo module builds and its tests run. The demo application
 * itself is Milestone 6, so the module ships the runnable demo with the capability report and menu.
 */
class ModuleSmokeTest {

    @Test
    void runsOnJava17OrNewer() {
        assertTrue(Runtime.version().feature() >= 17,
                "jnativekit requires Java 17 or newer, found " + Runtime.version());
    }
}
