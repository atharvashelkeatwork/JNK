package io.github.atharvashelkeatwork.jnativekit.providers.linux;

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
 * Tier T3: a real file write, read-back and delete in a temporary home directory on Linux.
 *
 * <p>Runs only on Linux. It never touches the real {@code ~/.config/autostart}. This proves the
 * filesystem round trip on the CI Linux machine, not that a desktop session starts the entry.
 */
@Tag("integration")
class LinuxDesktopEntryAutostartIntegrationTest {

    @Test
    void writesReadsAndRemovesARealEntry(@TempDir Path home) throws Exception {
        Assumptions.assumeTrue(Desktop.platform() == Platform.LINUX,
                "Linux-only integration test");
        LinuxDesktopEntryAutostartProvider provider =
                new LinuxDesktopEntryAutostartProvider(new TestPathProvider(home));
        AutostartEntry entry = new AutostartEntry(
                "com.example.integration",
                "Integration App",
                home.resolve("My App").resolve("app"),
                List.of("--flag", "two words", "100%"));

        Result<Void> enabled = provider.enable(entry);

        assertTrue(enabled.isConfirmed(), enabled::detail);
        Path file = home.resolve(".config").resolve("autostart")
                .resolve("com.example.integration.desktop");
        assertTrue(Files.isRegularFile(file), "expected " + file);
        String content = Files.readString(file);
        assertTrue(content.contains("Exec="), content);
        assertTrue(content.contains("two words"), content);

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
