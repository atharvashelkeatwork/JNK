package io.github.atharvashelkeatwork.jnativekit.providers.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Desktop;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Result;

/**
 * Tier T3: a real round trip through {@code HKCU\Software\Microsoft\Windows\CurrentVersion\Run}
 * on Windows.
 *
 * <p>Runs only on Windows. The value name is unique per run and is always deleted in a
 * {@code finally} block. This proves that a real {@code reg.exe} writes and reads back the value
 * on the CI Windows machine, not that Windows starts the application at login.
 */
@Tag("integration")
class WindowsRegAutostartIntegrationTest {

    @Test
    void writesReadsAndAlwaysRemovesAUniqueRunValue() {
        Assumptions.assumeTrue(Desktop.platform() == Platform.WINDOWS,
                "Windows-only integration test");
        WindowsRegAutostartProvider provider = new WindowsRegAutostartProvider();
        Capability capability = provider.capability();
        Assumptions.assumeTrue(capability.status() == Capability.Status.AVAILABLE,
                () -> "reg.exe is not available: " + capability.detail());

        String appId = "jnativekit.test." + UUID.randomUUID().toString().replace("-", "");
        Path executable = Path.of(System.getProperty("java.home")).resolve("bin").resolve("java.exe");
        AutostartEntry entry = new AutostartEntry(
                appId, "jnativekit integration test", executable, List.of("--test-arg", "two words"));

        try {
            Result<Void> enabled = provider.enable(entry);
            assertTrue(enabled.isConfirmed(), enabled::detail);

            Result<Boolean> isEnabled = provider.isEnabled(appId);
            assertTrue(isEnabled.isConfirmed(), isEnabled::detail);
            assertEquals(Boolean.TRUE, isEnabled.value());

            Result<Void> disabled = provider.disable(appId);
            assertTrue(disabled.isConfirmed(), disabled::detail);

            Result<Boolean> after = provider.isEnabled(appId);
            assertTrue(after.isConfirmed(), after::detail);
            assertEquals(Boolean.FALSE, after.value());
        } finally {
            provider.disable(appId);
        }
    }
}
