package io.github.atharvashelkeatwork.jnativekit.providers.internal.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real-process tests for {@link ProcessCommandRunner}.
 *
 * <p>Tier: ci-integration. The child is the JVM itself ({@code java -version}) or
 * {@link ChildProcessMain} on the test classpath, so the same test runs on Linux, Windows and macOS
 * without a shell. These tests prove that the timeout really kills the child and that captured
 * output is bounded; they say nothing about the desktop features built in later milestones.
 */
@Tag("integration")
class ProcessCommandRunnerIntegrationTest {

    private final ProcessCommandRunner runner = new ProcessCommandRunner();

    @Test
    void runsTheJvmItself() {
        CommandResult result = runner.run(List.of(javaExecutable(), "-version"), Duration.ofSeconds(30));

        assertFalse(result.timedOut(), "java -version must finish before the timeout");
        assertEquals(0, result.exitCode());
        String combined = (result.stdout() + result.stderr()).toLowerCase(Locale.ROOT);
        assertTrue(combined.contains("version"), "expected the JVM version banner, got: " + combined);
    }

    @Test
    void reportsTheRealExitCode() {
        CommandResult result = runner.run(child("exit", "3"), Duration.ofSeconds(30));

        assertFalse(result.timedOut());
        assertEquals(3, result.exitCode());
    }

    @Test
    void reportsLaunchFailureWithoutThrowing(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("jnativekit-missing-executable");
        CommandResult result = runner.run(List.of(missing.toString()), Duration.ofSeconds(10));

        assertTrue(result.isLaunchFailure(), "a missing executable must be a launch failure");
        assertFalse(result.timedOut());
        assertFalse(result.stderr().isEmpty(), "the launch error must be reported");
    }

    @Test
    void boundsStdoutAndStderr() {
        int floodBytes = 1024 * 1024;

        CommandResult result = runner.run(
                child("flood", Integer.toString(floodBytes)), Duration.ofSeconds(30));

        assertFalse(result.timedOut(), "the runner must keep draining output so the child does not deadlock");
        assertEquals(0, result.exitCode());
        assertEquals(ProcessCommandRunner.MAX_CAPTURED_BYTES, result.stdout().length());
        assertEquals(ProcessCommandRunner.MAX_CAPTURED_BYTES, result.stderr().length());
    }

    @Test
    void timeoutKillsTheChildProcess(@TempDir Path tempDir) throws IOException {
        Path pidFile = tempDir.resolve("child.pid");

        CommandResult result = runner.run(
                child("block", pidFile.toString()), Duration.ofSeconds(5));

        assertTrue(result.timedOut(), "the child must be reported as timed out");
        assertTrue(result.isLaunchFailure(), "a destroyed child has no real exit code");
        assertTrue(Files.exists(pidFile), "the child must have written its pid before blocking");

        long pid = Long.parseLong(Files.readString(pidFile, StandardCharsets.UTF_8).trim());
        boolean stillAlive = ProcessHandle.of(pid)
                .map(ProcessHandle::isAlive)
                .orElse(false);
        assertFalse(stillAlive, "the runner must destroyForcibly() the child on timeout");
    }

    private static List<String> child(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.add("-cp");
        command.add(testClassesDirectory());
        command.add(ChildProcessMain.class.getName());
        command.addAll(List.of(arguments));
        return command;
    }

    private static String javaExecutable() {
        String osName = System.getProperty("os.name", "");
        boolean windows = osName.toLowerCase(Locale.ROOT).contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
    }

    private static String testClassesDirectory() {
        try {
            return Path.of(ChildProcessMain.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the test classes directory", e);
        }
    }
}
