package io.github.atharvashelkeatwork.jnativekit.providers.internal.exec;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Real {@link CommandRunner}: {@link ProcessBuilder} with an argument list, bounded output capture
 * and a hard timeout.
 *
 * <p>No shell is involved at any point. Standard output and standard error are drained on separate
 * daemon threads so a chatty child cannot deadlock on a full pipe. At most
 * {@link #MAX_CAPTURED_BYTES} bytes are kept per stream; the remainder is read and discarded.
 *
 * <p>The child's standard input is closed immediately, so a tool that waits for input sees EOF
 * instead of hanging until the timeout.
 */
public final class ProcessCommandRunner implements CommandRunner {

    /** Maximum number of bytes captured from each of standard output and standard error. */
    public static final int MAX_CAPTURED_BYTES = 64 * 1024;

    private static final int READ_BUFFER_BYTES = 8 * 1024;
    private static final long TERMINATION_GRACE_MILLIS = 2_000;
    private static final long READER_JOIN_MILLIS = 2_000;

    private static final System.Logger LOG = System.getLogger(ProcessCommandRunner.class.getName());

    /**
     * Creates a runner that starts real processes through {@link ProcessBuilder}.
     */
    public ProcessCommandRunner() {
    }

    @Override
    public CommandResult run(List<String> argv, Duration timeout) {
        Objects.requireNonNull(argv, "argv");
        Objects.requireNonNull(timeout, "timeout");
        List<String> command = List.copyOf(argv);
        if (command.isEmpty()) {
            throw new IllegalArgumentException("argv must not be empty");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }

        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException | SecurityException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Failed to start process " + command.get(0), e);
            return new CommandResult(CommandResult.LAUNCH_FAILED_EXIT_CODE, "", e.toString(), false);
        }

        closeImmediately(process.getOutputStream(), command.get(0));

        Capture stdout = new Capture(process.getInputStream());
        Capture stderr = new Capture(process.getErrorStream());
        Thread stdoutThread = readerThread(process, stdout, "stdout");
        Thread stderrThread = readerThread(process, stderr, "stderr");
        stdoutThread.start();
        stderrThread.start();

        boolean finished;
        try {
            finished = process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(System.Logger.Level.WARNING,
                    "Interrupted while waiting for process " + command.get(0) + "; destroying it", e);
            destroy(process, stdoutThread, stderrThread);
            return new CommandResult(
                    CommandResult.LAUNCH_FAILED_EXIT_CODE, stdout.text(), stderr.text(), true);
        }

        if (!finished) {
            destroy(process, stdoutThread, stderrThread);
            return new CommandResult(
                    CommandResult.LAUNCH_FAILED_EXIT_CODE, stdout.text(), stderr.text(), true);
        }

        joinReader(stdoutThread, command.get(0) + " stdout");
        joinReader(stderrThread, command.get(0) + " stderr");
        return new CommandResult(process.exitValue(), stdout.text(), stderr.text(), false);
    }

    private static Thread readerThread(Process process, Capture capture, String stream) {
        Thread thread = new Thread(capture,
                "jnativekit-exec-" + process.pid() + "-" + stream);
        thread.setDaemon(true);
        return thread;
    }

    private static void destroy(Process process, Thread stdoutThread, Thread stderrThread) {
        process.destroyForcibly();
        try {
            if (!process.waitFor(TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)) {
                LOG.log(System.Logger.Level.WARNING,
                        "Process " + process.pid() + " was still alive after destroyForcibly()");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(System.Logger.Level.WARNING,
                    "Interrupted while waiting for a destroyed process to exit", e);
        }
        joinReader(stdoutThread, "stdout of process " + process.pid());
        joinReader(stderrThread, "stderr of process " + process.pid());
    }

    private static void joinReader(Thread thread, String description) {
        try {
            thread.join(READER_JOIN_MILLIS);
            if (thread.isAlive()) {
                thread.interrupt();
                LOG.log(System.Logger.Level.WARNING,
                        "Output reader for " + description + " did not finish in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            thread.interrupt();
            LOG.log(System.Logger.Level.WARNING,
                    "Interrupted while waiting for the output reader of " + description, e);
        }
    }

    private static void closeImmediately(OutputStream input, String command) {
        try {
            input.close();
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Failed to close stdin of process " + command, e);
        }
    }

    private static final class Capture implements Runnable {

        private final InputStream input;
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        private int stored;

        private Capture(InputStream input) {
            this.input = input;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[READ_BUFFER_BYTES];
            try (InputStream stream = input) {
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    int remaining = MAX_CAPTURED_BYTES - stored;
                    if (remaining > 0) {
                        int take = Math.min(read, remaining);
                        captured.write(buffer, 0, take);
                        stored += take;
                    }
                }
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "Failed to read process output", e);
            }
        }

        private String text() {
            synchronized (captured) {
                return captured.toString(StandardCharsets.UTF_8);
            }
        }
    }
}
