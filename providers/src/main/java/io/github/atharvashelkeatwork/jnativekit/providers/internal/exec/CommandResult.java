package io.github.atharvashelkeatwork.jnativekit.providers.internal.exec;

import java.util.Objects;

/**
 * The outcome of one external process invocation.
 *
 * <p>{@code timedOut} is {@code true} when the runner destroyed the process because it exceeded
 * its time limit; the {@code exitCode} is then
 * {@link CommandResult#LAUNCH_FAILED_EXIT_CODE} because no real exit code exists.
 *
 * @param exitCode the process exit code, or {@link #LAUNCH_FAILED_EXIT_CODE} when the process could
 *                 not be started or was destroyed before exiting
 * @param stdout   captured standard output, decoded as UTF-8 and bounded
 * @param stderr   captured standard error, decoded as UTF-8 and bounded
 * @param timedOut whether the process was destroyed for exceeding the time limit
 */
public record CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {

    /**
     * Exit code used when no real exit code exists: the process did not start, or it was destroyed
     * before it could exit.
     */
    public static final int LAUNCH_FAILED_EXIT_CODE = -1;

    /**
     * Creates a command result.
     *
     * @param exitCode the process exit code, or {@link #LAUNCH_FAILED_EXIT_CODE}
     * @param stdout   captured standard output; never {@code null}
     * @param stderr   captured standard error; never {@code null}
     * @param timedOut whether the process was destroyed for exceeding the time limit
     * @throws NullPointerException if {@code stdout} or {@code stderr} is {@code null}
     */
    public CommandResult {
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(stderr, "stderr");
    }

    /**
     * Returns whether the process could not be started or was destroyed before exiting.
     *
     * @return {@code true} when {@link #exitCode()} is {@link #LAUNCH_FAILED_EXIT_CODE}
     */
    public boolean isLaunchFailure() {
        return exitCode == LAUNCH_FAILED_EXIT_CODE;
    }
}
