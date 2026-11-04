package io.github.atharvashelkeatwork.jnativekit.providers.macos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Desktop;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.paths.TestPathProvider;

/**
 * Tier T3: a real plist write, read-back and delete in a temporary home directory on macOS.
 *
 * <p>Runs only on macOS. It never touches the real {@code ~/Library/LaunchAgents} and never calls
 * {@code launchctl}. This proves the filesystem round trip on the CI macOS machine, not that
 * {@code launchd} starts the application at login.
 */
@Tag("integration")
class MacLaunchAgentAutostartIntegrationTest {

    @Test
    void writesReadsAndRemovesARealPlist(@TempDir Path home) throws Exception {
        Assumptions.assumeTrue(Desktop.platform() == Platform.MACOS,
                "macOS-only integration test");
        MacLaunchAgentAutostartProvider provider =
                new MacLaunchAgentAutostartProvider(new TestPathProvider(home));
        AutostartEntry entry = new AutostartEntry(
                "com.example.integration",
                "Integration App",
                home.resolve("My App.app").resolve("Contents").resolve("MacOS").resolve("app"),
                List.of("--flag", "two words", "a&b"));

        Result<Void> enabled = provider.enable(entry);

        assertTrue(enabled.isConfirmed(), enabled::detail);
        Path file = home.resolve("Library").resolve("LaunchAgents")
                .resolve("com.example.integration.plist");
        assertTrue(Files.isRegularFile(file), "expected " + file);
        String content = Files.readString(file);
        assertTrue(content.contains("<key>Label</key>"), content);
        assertTrue(content.contains("a&amp;b"), content);

        Result<Boolean> isEnabled = provider.isEnabled("com.example.integration");
        assertTrue(isEnabled.isConfirmed(), isEnabled::detail);
        assertEquals(Boolean.TRUE, isEnabled.value());

        Result<Void> disabled = provider.disable("com.example.integration");
        assertTrue(disabled.isConfirmed(), disabled::detail);
        assertFalse(Files.exists(file));

        Result<Boolean> after = provider.isEnabled("com.example.integration");
        assertTrue(after.isConfirmed(), after::detail);
        assertEquals(Boolean.FALSE, after.value());
    }
}
