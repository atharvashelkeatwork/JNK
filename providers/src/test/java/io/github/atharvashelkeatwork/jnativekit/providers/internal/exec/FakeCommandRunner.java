package io.github.atharvashelkeatwork.jnativekit.providers.internal.exec;

import java.time.Duration;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test double for {@link CommandRunner}: records every invocation and returns scripted results.
 *
 * <p>Test helper only; it is not shipped. Providers built in later milestones use it to assert the
 * exact argument list they pass without starting a real process.
 */
public final class FakeCommandRunner implements CommandRunner {

    /**
     * One recorded call.
     *
     * @param argv    the argument list as received
     * @param timeout the timeout as received
     */
    public record Invocation(List<String> argv, Duration timeout) {

        /**
         * Creates an immutable invocation record.
         *
         * @param argv    the argument list
         * @param timeout the timeout
         * @throws NullPointerException if an argument is {@code null}
         */
        public Invocation {
            argv = List.copyOf(Objects.requireNonNull(argv, "argv"));
            Objects.requireNonNull(timeout, "timeout");
        }
    }

    private final List<Invocation> invocations = new CopyOnWriteArrayList<>();
    private final Deque<Object> script = new ConcurrentLinkedDeque<>();
    private volatile CommandResult defaultResult = new CommandResult(0, "", "", false);

    /**
     * Queues results and failures in order. A {@link RuntimeException} element is thrown when its
     * turn arrives.
     *
     * @param results results or runtime failures
     * @return this runner
     */
    public FakeCommandRunner enqueue(Object... results) {
        Objects.requireNonNull(results, "results");
        for (Object result : results) {
            if (result == null) {
                throw new NullPointerException("scripted result must not be null");
            }
            script.add(result);
        }
        return this;
    }

    /**
     * Sets the result returned when the script is empty.
     *
     * @param result the default result
     * @return this runner
     */
    public FakeCommandRunner defaultResult(CommandResult result) {
        this.defaultResult = Objects.requireNonNull(result, "result");
        return this;
    }

    /**
     * Clears recorded invocations and the script.
     */
    public void clear() {
        invocations.clear();
        script.clear();
    }

    /**
     * Returns the recorded invocations.
     *
     * @return an immutable snapshot of the invocations
     */
    public List<Invocation> invocations() {
        return List.copyOf(invocations);
    }

    /**
     * Returns the most recent invocation, or {@code null} when nothing was run.
     *
     * @return the last invocation
     */
    public Invocation lastInvocation() {
        return invocations.isEmpty() ? null : invocations.get(invocations.size() - 1);
    }

    @Override
    public CommandResult run(List<String> argv, Duration timeout) {
        Objects.requireNonNull(argv, "argv");
        Objects.requireNonNull(timeout, "timeout");
        if (argv.isEmpty()) {
            throw new IllegalArgumentException("argv must not be empty");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        invocations.add(new Invocation(argv, timeout));
        Object next = script.poll();
        if (next == null) {
            return defaultResult;
        }
        if (next instanceof RuntimeException failure) {
            throw failure;
        }
        return (CommandResult) next;
    }
}
