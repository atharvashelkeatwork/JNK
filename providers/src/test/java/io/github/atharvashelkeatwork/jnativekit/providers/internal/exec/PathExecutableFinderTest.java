package io.github.atharvashelkeatwork.jnativekit.providers.internal.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class PathExecutableFinderTest {

    @Test
    void findsTheCurrentJvmExecutableOnPath() {
        Path javaBin = Path.of(System.getProperty("java.home"), "bin");
        String executableName = Files.isExecutable(javaBin.resolve("java")) ? "java" : "java.exe";
        Path expected = javaBin.resolve(executableName).toAbsolutePath().normalize();
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(expected),
                "the current JVM executable could not be found");

        PathExecutableFinder finder = new PathExecutableFinder(
                Map.of("PATH", javaBin.toString()), File.pathSeparator);

        assertEquals(Optional.of(expected), finder.find(executableName));
    }

    @Test
    void ignoresBlankPathEntriesAndStillFindsTheTool() {
        Path javaBin = Path.of(System.getProperty("java.home"), "bin");
        String executableName = Files.isExecutable(javaBin.resolve("java")) ? "java" : "java.exe";
        Path expected = javaBin.resolve(executableName).toAbsolutePath().normalize();
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(expected),
                "the current JVM executable could not be found");
        String path = File.pathSeparator + javaBin + File.pathSeparator;

        PathExecutableFinder finder = new PathExecutableFinder(
                Map.of("PATH", path), File.pathSeparator);

        assertEquals(Optional.of(expected), finder.find(executableName));
    }

    @Test
    void skipsAnInvalidPathEntryAndKeepsLooking() {
        Path javaBin = Path.of(System.getProperty("java.home"), "bin");
        String executableName = Files.isExecutable(javaBin.resolve("java")) ? "java" : "java.exe";
        Path expected = javaBin.resolve(executableName).toAbsolutePath().normalize();
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isExecutable(expected),
                "the current JVM executable could not be found");
        String path = "\u0000" + File.pathSeparator + javaBin;

        PathExecutableFinder finder = new PathExecutableFinder(
                Map.of("PATH", path), File.pathSeparator);

        assertEquals(Optional.of(expected), finder.find(executableName));
    }

    @Test
    void returnsEmptyWhenTheToolIsMissing() {
        PathExecutableFinder finder = new PathExecutableFinder(
                Map.of("PATH", System.getProperty("java.io.tmpdir")), File.pathSeparator);

        assertTrue(finder.find("jnativekit-tool-that-does-not-exist").isEmpty());
    }

    @Test
    void returnsEmptyWhenPathIsNotSet() {
        PathExecutableFinder finder = new PathExecutableFinder(Map.of(), File.pathSeparator);

        assertTrue(finder.find("notify-send").isEmpty());
    }

    @Test
    void returnsEmptyForAnEmptyName() {
        PathExecutableFinder finder = new PathExecutableFinder(
                Map.of("PATH", System.getProperty("java.io.tmpdir")), File.pathSeparator);

        assertTrue(finder.find("").isEmpty());
    }

    @Test
    void rejectsANullName() {
        PathExecutableFinder finder = new PathExecutableFinder(Map.of(), File.pathSeparator);

        assertThrows(NullPointerException.class, () -> finder.find(null));
    }
}
