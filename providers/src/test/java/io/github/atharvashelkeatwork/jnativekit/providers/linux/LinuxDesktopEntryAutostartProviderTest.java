package io.github.atharvashelkeatwork.jnativekit.providers.linux;

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
import io.github.atharvashelkeatwork.jnativekit.providers.internal.text.DesktopEntry;

/**
 * Tier T2: the Linux Desktop Entry provider against a temporary home directory.
 *
 * <p>These tests prove which file is written, what it contains, and how failures are reported.
 * They do not prove that a Cinnamon, GNOME or KDE session launches the entry; that is the T4
 * manual checklist.
 */
class LinuxDesktopEntryAutostartProviderTest {

    @Test
    void reportsItsIdentity(@TempDir Path home) {
        LinuxDesktopEntryAutostartProvider provider = provider(home);

        assertEquals("linux.desktop-entry", provider.id());
        assertTrue(provider.supports(Platform.LINUX));
        assertFalse(provider.supports(Platform.WINDOWS));
        assertFalse(provider.supports(Platform.MACOS));
        assertEquals(100, provider.priority());
    }

    @Test
    void writesTheXdgAutostartFileAndReadsItBack(@TempDir Path home) throws IOException {
        LinuxDesktopEntryAutostartProvider provider = provider(home);
        AutostartEntry entry = entry(home, "com.example.app",
                List.of("--flag", "two words", "100%", "back\\slash", "\"quoted\""));

        Result<Void> enabled = provider.enable(entry);

        assertTrue(enabled.isConfirmed(), enabled::detail);
        assertEquals("linux.desktop-entry", enabled.provider());
        Path file = home.resolve(".config").resolve("autostart").resolve("com.example.app.desktop");
        assertTrue(Files.isRegularFile(file), "expected " + file);

        DesktopEntry.Parsed parsed = DesktopEntry.parse(Files.readString(file, StandardCharsets.UTF_8));
        assertEquals("Example App", parsed.name());
        assertEquals(List.of(entry.executable().toString(), "--flag", "two words", "100%",
                "back\\slash", "\"quoted\""), parsed.exec());

        Result<Boolean> isEnabled = provider.isEnabled("com.example.app");
        assertTrue(isEnabled.isConfirmed(), isEnabled::detail);
        assertEquals(Boolean.TRUE, isEnabled.value());
    }

    @Test
    void usesXdgConfigHomeWhenThePathProviderSuppliesIt(@TempDir Path tempDir) throws IOException {
        Path home = tempDir.resolve("home");
        Path xdg = tempDir.resolve("xdg");
        LinuxDesktopEntryAutostartProvider provider =
                new LinuxDesktopEntryAutostartProvider(new TestPathProvider(home, xdg));

        Result<Void> result = provider.enable(entry(home, "com.example.xdg", List.of()));

        assertTrue(result.isConfirmed(), result::detail);
        assertTrue(Files.isRegularFile(xdg.resolve("autostart").resolve("com.example.xdg.desktop")));
        assertFalse(Files.exists(home.resolve(".config").resolve("autostart")));
    }

    @Test
    void disableRemovesTheEntryAndIsIdempotent(@TempDir Path home) {
        LinuxDesktopEntryAutostartProvider provider = provider(home);
        provider.enable(entry(home, "com.example.app", List.of()));

        Result<Void> first = provider.disable("com.example.app");
        Result<Void> second = provider.disable("com.example.app");

        assertTrue(first.isConfirmed(), first::detail);
        assertTrue(second.isConfirmed(), second::detail);
        Path file = home.resolve(".config").resolve("autostart").resolve("com.example.app.desktop");
        assertFalse(Files.exists(file));
        Result<Boolean> isEnabled = provider.isEnabled("com.example.app");
        assertTrue(isEnabled.isConfirmed(), isEnabled::detail);
        assertEquals(Boolean.FALSE, isEnabled.value());
    }

    @Test
    void isEnabledIsFalseOnlyWhenTheFileIsMissing(@TempDir Path home) {
        LinuxDesktopEntryAutostartProvider provider = provider(home);

        Result<Boolean> result = provider.isEnabled("com.example.missing");

        assertTrue(result.isConfirmed(), result::detail);
        assertEquals(Boolean.FALSE, result.value());
    }

    @Test
    void isEnabledReportsAnUnreadableStoreAsFailedNotFalse(@TempDir Path home) throws IOException {
        LinuxDesktopEntryAutostartProvider provider = provider(home);
        Path autostart = Files.createDirectories(home.resolve(".config").resolve("autostart"));
        Files.createDirectory(autostart.resolve("com.example.directory.desktop"));

        Result<Boolean> result = provider.isEnabled("com.example.directory");

        assertTrue(result.isFailed(), "an unreadable store must not be CONFIRMED(false): " + result);
        assertEquals(Reason.IO_ERROR, result.reason());
        assertFalse(result.isConfirmed());
    }

    @Test
    void isEnabledReportsMalformedContentAsFailedNotFalse(@TempDir Path home) throws IOException {
        LinuxDesktopEntryAutostartProvider provider = provider(home);
        Path autostart = Files.createDirectories(home.resolve(".config").resolve("autostart"));
        Files.writeString(autostart.resolve("com.example.bad.desktop"), "not a desktop file",
                StandardCharsets.UTF_8);

        Result<Boolean> result = provider.isEnabled("com.example.bad");

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.IO_ERROR, result.reason());
    }

    @Test
    void enableReportsAnUnwritableStoreAsFailedNotAnException(@TempDir Path home) throws IOException {
        LinuxDesktopEntryAutostartProvider provider = provider(home);
        Files.writeString(home.resolve(".config"), "I am a file", StandardCharsets.UTF_8);

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
    void capabilityIsPermissionDeniedWhenTheAutostartPathIsAFile(@TempDir Path home) throws IOException {
        Path config = Files.createDirectories(home.resolve(".config"));
        Files.writeString(config.resolve("autostart"), "I am a file", StandardCharsets.UTF_8);

        Capability capability = provider(home).capability();

        assertEquals(Capability.Status.PERMISSION_DENIED, capability.status());
        assertEquals(Reason.PERMISSION_DENIED, capability.reason());
        assertNotNull(capability.remedy());
    }

    @Test
    void validatesCallerInputAndNeverThrowsForStoreProblems(@TempDir Path home) {
        LinuxDesktopEntryAutostartProvider provider = provider(home);

        assertThrows(NullPointerException.class, () -> provider.enable(null));
        assertThrows(IllegalArgumentException.class, () -> provider.disable("../escape"));
        assertThrows(IllegalArgumentException.class, () -> provider.isEnabled("bad id"));
        assertThrows(NullPointerException.class, () -> provider.isEnabled(null));
        assertThrows(NullPointerException.class, () -> provider.disable(null));
    }

    @Test
    void neverEscapesTheAutostartDirectoryThroughTheAppId(@TempDir Path home) {
        LinuxDesktopEntryAutostartProvider provider = provider(home);

        assertThrows(IllegalArgumentException.class, () -> provider.isEnabled("../../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> provider.disable("/tmp/evil"));
        assertFalse(Files.exists(home.resolve(".config").resolve("autostart")));
    }

    @Test
    void guardedFacadeConvertsAnUnexpectedRuntimeException(@TempDir Path home) {
        PathProvider throwing = new PathProvider() {
            @Override
            public Path home() {
                return home;
            }

            @Override
            public Path configDirectory() {
                throw new IllegalStateException("boom");
            }
        };
        GuardedAutostart guarded =
                new GuardedAutostart(new LinuxDesktopEntryAutostartProvider(throwing));

        Result<Boolean> isEnabled = guarded.isEnabled("com.example.app");
        Capability capability = guarded.capability();

        assertTrue(isEnabled.isFailed(), isEnabled.toString());
        assertEquals(Reason.BACKEND_ERROR, isEnabled.reason());
        assertTrue(capability.status() != Capability.Status.AVAILABLE);
    }

    private static LinuxDesktopEntryAutostartProvider provider(Path home) {
        return new LinuxDesktopEntryAutostartProvider(new TestPathProvider(home));
    }

    private static AutostartEntry entry(Path home, String appId, List<String> arguments) {
        return new AutostartEntry(appId, "Example App", home.resolve("My App").resolve("app"), arguments);
    }
}
