package io.github.atharvashelkeatwork.jnativekit.providers.macos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.internal.GuardedAutostart;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.paths.PathProvider;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.paths.TestPathProvider;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.text.LaunchAgentPlist;

/**
 * Tier T2: the macOS launch agent provider against a temporary home directory.
 *
 * <p>These tests prove which plist is written, what it contains, and how failures are reported.
 * They do not prove that {@code launchd} loads the job or starts the application at login; that is
 * the T4 manual checklist, and no macOS machine has run this code yet.
 */
class MacLaunchAgentAutostartProviderTest {

    @Test
    void reportsItsIdentity(@TempDir Path home) {
        MacLaunchAgentAutostartProvider provider = provider(home);

        assertEquals("macos.launch-agent", provider.id());
        assertTrue(provider.supports(Platform.MACOS));
        assertFalse(provider.supports(Platform.LINUX));
        assertFalse(provider.supports(Platform.WINDOWS));
        assertEquals(100, provider.priority());
    }

    @Test
    void writesTheLaunchAgentAndReadsItBack(@TempDir Path home) throws IOException {
        MacLaunchAgentAutostartProvider provider = provider(home);
        AutostartEntry entry = entry(home, "com.example.app",
                List.of("--flag", "two words", "100%", "back\\slash", "a&b", "<xml>"));

        Result<Void> enabled = provider.enable(entry);

        assertTrue(enabled.isConfirmed(), enabled::detail);
        assertEquals("macos.launch-agent", enabled.provider());
        Path file = home.resolve("Library").resolve("LaunchAgents").resolve("com.example.app.plist");
        assertTrue(Files.isRegularFile(file), "expected " + file);

        LaunchAgentPlist.Content parsed =
                LaunchAgentPlist.parse(Files.readString(file, StandardCharsets.UTF_8));
        assertEquals("com.example.app", parsed.label());
        assertEquals(List.of(entry.executable().toString(), "--flag", "two words", "100%",
                "back\\slash", "a&b", "<xml>"), parsed.programArguments());
        assertTrue(parsed.runAtLoad());

        Result<Boolean> isEnabled = provider.isEnabled("com.example.app");
        assertTrue(isEnabled.isConfirmed(), isEnabled::detail);
        assertEquals(Boolean.TRUE, isEnabled.value());
    }

    @Test
    void disableRemovesThePlistAndIsIdempotent(@TempDir Path home) {
        MacLaunchAgentAutostartProvider provider = provider(home);
        provider.enable(entry(home, "com.example.app", List.of()));

        Result<Void> first = provider.disable("com.example.app");
        Result<Void> second = provider.disable("com.example.app");

        assertTrue(first.isConfirmed(), first::detail);
        assertTrue(second.isConfirmed(), second::detail);
        Path file = home.resolve("Library").resolve("LaunchAgents").resolve("com.example.app.plist");
        assertFalse(Files.exists(file));
        Result<Boolean> isEnabled = provider.isEnabled("com.example.app");
        assertTrue(isEnabled.isConfirmed(), isEnabled::detail);
        assertEquals(Boolean.FALSE, isEnabled.value());
    }

    @Test
    void isEnabledIsFalseOnlyWhenTheFileIsMissing(@TempDir Path home) {
        MacLaunchAgentAutostartProvider provider = provider(home);

        Result<Boolean> result = provider.isEnabled("com.example.missing");

        assertTrue(result.isConfirmed(), result::detail);
        assertEquals(Boolean.FALSE, result.value());
    }

    @Test
    void isEnabledReportsAnUnreadableStoreAsFailedNotFalse(@TempDir Path home) throws IOException {
        MacLaunchAgentAutostartProvider provider = provider(home);
        Path launchAgents =
                Files.createDirectories(home.resolve("Library").resolve("LaunchAgents"));
        Files.createDirectory(launchAgents.resolve("com.example.directory.plist"));

        Result<Boolean> result = provider.isEnabled("com.example.directory");

        assertTrue(result.isFailed(), "an unreadable store must not be CONFIRMED(false): " + result);
        assertEquals(Reason.IO_ERROR, result.reason());
        assertFalse(result.isConfirmed());
    }

    @Test
    void isEnabledReportsMalformedContentAsFailedNotFalse(@TempDir Path home) throws IOException {
        MacLaunchAgentAutostartProvider provider = provider(home);
        Path launchAgents =
                Files.createDirectories(home.resolve("Library").resolve("LaunchAgents"));
        Files.writeString(launchAgents.resolve("com.example.bad.plist"), "not a plist",
                StandardCharsets.UTF_8);

        Result<Boolean> result = provider.isEnabled("com.example.bad");

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.IO_ERROR, result.reason());
    }

    @Test
    void isEnabledReportsAWelcomeLabelMismatchAsFailed(@TempDir Path home) throws IOException {
        MacLaunchAgentAutostartProvider provider = provider(home);
        Path launchAgents =
                Files.createDirectories(home.resolve("Library").resolve("LaunchAgents"));
        Files.writeString(launchAgents.resolve("com.example.mismatch.plist"), """
                <plist version="1.0"><dict>
                    <key>Label</key><string>com.example.other</string>
                    <key>ProgramArguments</key><array><string>/bin/app</string></array>
                </dict></plist>
                """, StandardCharsets.UTF_8);

        Result<Boolean> result = provider.isEnabled("com.example.mismatch");

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.IO_ERROR, result.reason());
    }

    @Test
    void enableReportsAnUnwritableStoreAsFailedNotAnException(@TempDir Path home) throws IOException {
        MacLaunchAgentAutostartProvider provider = provider(home);
        Files.writeString(home.resolve("Library"), "I am a file", StandardCharsets.UTF_8);

        Result<Void> result = provider.enable(entry(home, "com.example.blocked", List.of()));

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.IO_ERROR, result.reason());
        assertNotNull(result.detail());
    }

    @Test
    void capabilityIsAvailableForAWritableTemporaryHome(@TempDir Path home) {
        Capability capability = provider(home).capability();

        assertEquals(Capability.Status.AVAILABLE, capability.status());
        assertEquals(Reason.NONE, capability.reason());
    }

    @Test
    void capabilityIsPermissionDeniedWhenLaunchAgentsIsAFile(@TempDir Path home) throws IOException {
        Path library = Files.createDirectories(home.resolve("Library"));
        Files.writeString(library.resolve("LaunchAgents"), "I am a file", StandardCharsets.UTF_8);

        Capability capability = provider(home).capability();

        assertEquals(Capability.Status.PERMISSION_DENIED, capability.status());
        assertEquals(Reason.PERMISSION_DENIED, capability.reason());
        assertNotNull(capability.remedy());
    }

    @Test
    void validatesCallerInput(@TempDir Path home) {
        MacLaunchAgentAutostartProvider provider = provider(home);

        assertThrows(NullPointerException.class, () -> provider.enable(null));
        assertThrows(IllegalArgumentException.class, () -> provider.disable("../escape"));
        assertThrows(IllegalArgumentException.class, () -> provider.isEnabled("bad id"));
        assertThrows(NullPointerException.class, () -> provider.isEnabled(null));
        assertThrows(NullPointerException.class, () -> provider.disable(null));
    }

    @Test
    void guardedFacadeConvertsAnUnexpectedRuntimeException(@TempDir Path home) {
        PathProvider throwing = new PathProvider() {
            @Override
            public Path home() {
                throw new IllegalStateException("boom");
            }

            @Override
            public Path configDirectory() {
                return home;
            }
        };
        GuardedAutostart guarded =
                new GuardedAutostart(new MacLaunchAgentAutostartProvider(throwing));

        Result<Boolean> isEnabled = guarded.isEnabled("com.example.app");
        Capability capability = guarded.capability();

        assertTrue(isEnabled.isFailed(), isEnabled.toString());
        assertEquals(Reason.BACKEND_ERROR, isEnabled.reason());
        assertTrue(capability.status() != Capability.Status.AVAILABLE);
    }

    private static MacLaunchAgentAutostartProvider provider(Path home) {
        return new MacLaunchAgentAutostartProvider(new TestPathProvider(home));
    }

    private static AutostartEntry entry(Path home, String appId, List<String> arguments) {
        return new AutostartEntry(appId, "Example App", home.resolve("My App").resolve("app"), arguments);
    }
}
