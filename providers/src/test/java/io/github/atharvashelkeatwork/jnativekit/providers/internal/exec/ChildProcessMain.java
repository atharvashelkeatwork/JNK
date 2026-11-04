package io.github.atharvashelkeatwork.jnativekit.providers.internal.exec;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/**
 * Portable child process used by the {@link ProcessCommandRunner} integration tests.
 *
 * <p>It has no dependency outside the JDK so it can run from the test-classes directory with a
 * plain {@code java -cp} command on Linux, Windows and macOS.
 */
public final class ChildProcessMain {

    private ChildProcessMain() {
    }

    /**
     * Runs the child behaviour named by the first argument.
     *
     * @param args {@code exit <code>}, {@code block <pid-file>} or {@code flood <bytes>}
     * @throws Exception when the requested behaviour fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("missing child mode");
        }
        switch (args[0]) {
            case "exit" -> System.exit(Integer.parseInt(args[1]));
            case "block" -> block(args[1]);
            case "flood" -> flood(Long.parseLong(args[1]));
            default -> throw new IllegalArgumentException("unknown child mode: " + args[0]);
        }
    }

    private static void block(String pidFile) throws Exception {
        Path path = Path.of(pidFile);
        Files.writeString(path, Long.toString(ProcessHandle.current().pid()), StandardCharsets.UTF_8);
        new CountDownLatch(1).await();
    }

    private static void flood(long bytes) throws IOException {
        byte[] chunk = new byte[8 * 1024];
        Arrays.fill(chunk, (byte) 'x');
        OutputStream stdout = System.out;
        OutputStream stderr = System.err;
        long remaining = bytes;
        while (remaining > 0) {
            int length = (int) Math.min(chunk.length, remaining);
            stdout.write(chunk, 0, length);
            stderr.write(chunk, 0, length);
            remaining -= length;
        }
        stdout.flush();
        stderr.flush();
    }
}
