package io.github.atharvashelkeatwork.jnativekit.providers.internal.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Covers the test double itself, so provider tests can trust it.
 */
class FakeCommandRunnerTest {

    @Test
    void recordsArgvAndTimeout() {
        FakeCommandRunner runner = new FakeCommandRunner();

        CommandResult result = runner.run(List.of("notify-send", "--", "-title", "body"), Duration.ofSeconds(5));

        assertEquals(0, result.exitCode());
        FakeCommandRunner.Invocation invocation = runner.lastInvocation();
        assertEquals(List.of("notify-send", "--", "-title", "body"), invocation.argv());
        assertEquals(Duration.ofSeconds(5), invocation.timeout());
        assertEquals(1, runner.invocations().size());
    }

    @Test
    void returnsScriptedResultsInOrderThenTheDefault() {
        FakeCommandRunner runner = new FakeCommandRunner()
                .enqueue(new CommandResult(1, "out", "err", false),
                        new CommandResult(-1, "", "", true))
                .defaultResult(new CommandResult(7, "default", "", false));

        assertEquals(1, runner.run(List.of("a"), Duration.ofSeconds(1)).exitCode());
        assertEquals(-1, runner.run(List.of("b"), Duration.ofSeconds(1)).exitCode());
        assertEquals(7, runner.run(List.of("c"), Duration.ofSeconds(1)).exitCode());
    }

    @Test
    void throwsScriptedRuntimeExceptions() {
        FakeCommandRunner runner = new FakeCommandRunner()
                .enqueue(new IllegalStateException("boom"));

        assertThrows(IllegalStateException.class, () -> runner.run(List.of("a"), Duration.ofSeconds(1)));
    }

    @Test
    void validatesItsOwnContract() {
        FakeCommandRunner runner = new FakeCommandRunner();

        assertThrows(NullPointerException.class, () -> runner.run(null, Duration.ofSeconds(1)));
        assertThrows(NullPointerException.class, () -> runner.run(List.of("a"), null));
        assertThrows(IllegalArgumentException.class, () -> runner.run(List.of(), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> runner.run(List.of("a"), Duration.ZERO));
        assertNull(runner.lastInvocation());
    }
}
