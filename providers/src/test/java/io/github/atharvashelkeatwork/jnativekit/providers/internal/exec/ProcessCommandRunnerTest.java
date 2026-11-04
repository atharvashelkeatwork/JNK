package io.github.atharvashelkeatwork.jnativekit.providers.internal.exec;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Covers the caller-input rules of the real runner. The process behaviour itself is covered by
 * {@link ProcessCommandRunnerIntegrationTest}, which only runs in the integration profile.
 */
class ProcessCommandRunnerTest {

    private final ProcessCommandRunner runner = new ProcessCommandRunner();

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> runner.run(null, Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class, () -> runner.run(List.of("a"), null));
    }

    @Test
    void rejectsEmptyCommandAndNonPositiveTimeout() {
        assertThrows(IllegalArgumentException.class, () -> runner.run(List.of(), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> runner.run(List.of("a"), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> runner.run(List.of("a"), Duration.ofSeconds(-1)));
    }
}
