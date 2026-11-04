package io.github.atharvashelkeatwork.jnativekit.providers.internal.exec;

import java.time.Duration;
import java.util.List;

/**
 * Runs one external process without a shell.
 *
 * <p>Implementations must pass the argument list to {@link ProcessBuilder} unchanged, must bound
 * the captured output, must enforce the timeout with
 * {@link Process#destroyForcibly()} and must never throw because the process failed.
 */
public interface CommandRunner {

    /**
     * Runs the command and waits for it to finish or to exceed the timeout.
     *
     * @param argv    the executable and its arguments, one element per token; never empty
     * @param timeout the maximum time to wait; must be positive
     * @return the captured outcome; never {@code null}
     * @throws NullPointerException     if {@code argv} or {@code timeout} is {@code null}
     * @throws IllegalArgumentException if {@code argv} is empty or {@code timeout} is not positive
     */
    CommandResult run(List<String> argv, Duration timeout);
}
