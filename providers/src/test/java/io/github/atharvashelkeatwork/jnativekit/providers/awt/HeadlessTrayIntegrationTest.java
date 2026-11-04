package io.github.atharvashelkeatwork.jnativekit.providers.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Tier T3: a real JVM started with {@code -Djava.awt.headless=true}.
 *
 * <p>Proves that the registered AWT tray provider reports
 * {@code UNSUPPORTED_ENVIRONMENT NO_GRAPHICAL_SESSION} through {@code Desktop.tray()} in a genuinely
 * headless process. It runs on Linux, Windows and macOS and says nothing about a real desktop.
 */
@Tag("integration")
class HeadlessTrayIntegrationTest {

    @Test
    void aHeadlessJvmReportsNoGraphicalSessionThroughDesktop() throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                javaExecutable(),
                "-Djava.awt.headless=true",
                "-cp",
                System.getProperty("java.class.path"),
                HeadlessTrayProbe.class.getName())
                .redirectErrorStream(true)
                .start();

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertTrue(finished, "the headless probe did not finish in time: " + output);
        assertEquals(0, process.exitValue(), output.trim());
        assertTrue(output.contains("UNSUPPORTED_ENVIRONMENT NO_GRAPHICAL_SESSION"), output.trim());
    }

    private static String javaExecutable() {
        String osName = System.getProperty("os.name", "");
        boolean windows = osName.toLowerCase(Locale.ROOT).contains("win");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java")
                .toString();
    }
}
