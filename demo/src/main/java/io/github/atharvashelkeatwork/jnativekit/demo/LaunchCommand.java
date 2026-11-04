package io.github.atharvashelkeatwork.jnativekit.demo;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;

/**
 * Builds the autostart command that relaunches this demo at login.
 *
 * <p>The command uses the current JVM executable, so it does not depend on a shell or on the
 * user's PATH. When the demo runs from a jar, the command uses {@code -jar}; when it runs from
 * {@code target/classes}, the command uses the current classpath. This class is demo-only and is
 * not part of the published library API.
 */
final class LaunchCommand {

    private LaunchCommand() {
    }

    /**
     * Builds the autostart entry for the running demo.
     *
     * @param javaHome   the current {@code java.home} directory
     * @param windows    whether the current platform is Windows
     * @param codeSource the directory or jar containing the demo classes
     * @param classpath  the current {@code java.class.path}; used for a directory launch
     * @return a validated autostart entry
     */
    static AutostartEntry demoEntry(Path javaHome, boolean windows, Path codeSource, String classpath) {
        return new AutostartEntry(
                DemoAssets.appId(),
                DemoAssets.displayName(),
                javaExecutable(javaHome, windows),
                arguments(codeSource, classpath));
    }

    /**
     * Returns the JVM executable for the current runtime.
     *
     * @param javaHome the current {@code java.home} directory
     * @param windows  whether to use {@code java.exe}
     * @return the absolute path to the JVM executable
     */
    static Path javaExecutable(Path javaHome, boolean windows) {
        Objects.requireNonNull(javaHome, "javaHome");
        return javaHome.resolve("bin").resolve(windows ? "java.exe" : "java");
    }

    /**
     * Builds the arguments that relaunch the demo in autostart mode.
     *
     * @param codeSource the directory or jar containing the demo classes
     * @param classpath  the current {@code java.class.path}
     * @return the launch arguments
     */
    static List<String> arguments(Path codeSource, String classpath) {
        List<String> arguments = new ArrayList<>();
        if (isJar(codeSource)) {
            arguments.add("-jar");
            arguments.add(codeSource.toString());
        } else {
            arguments.add("-cp");
            arguments.add(classpath == null ? "" : classpath);
            arguments.add(DemoAssets.mainClassName());
        }
        arguments.add("--autostart");
        return List.copyOf(arguments);
    }

    /**
     * Returns the code source of the running demo, or {@code null} when it cannot be determined.
     *
     * @return the directory or jar containing the demo classes, or {@code null}
     */
    static Path currentCodeSource() {
        try {
            var location = Demo.class.getProtectionDomain().getCodeSource();
            if (location == null) {
                return null;
            }
            return Path.of(location.getLocation().toURI());
        } catch (URISyntaxException | IllegalArgumentException | SecurityException e) {
            return null;
        }
    }

    private static boolean isJar(Path codeSource) {
        if (codeSource == null) {
            return false;
        }
        Path fileName = codeSource.getFileName();
        return fileName != null && fileName.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar");
    }
}
