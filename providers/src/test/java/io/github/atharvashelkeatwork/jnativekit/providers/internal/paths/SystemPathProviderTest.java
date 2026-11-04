package io.github.atharvashelkeatwork.jnativekit.providers.internal.paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Covers the XDG configuration directory rules from {@code docs/ARCHITECTURE.md} without touching
 * the real home directory.
 */
class SystemPathProviderTest {

    @Test
    void defaultsToDotConfigUnderTheHomeDirectory(@TempDir Path home) {
        SystemPathProvider provider = new SystemPathProvider(home, Map.of());

        assertEquals(home, provider.home());
        assertEquals(home.resolve(".config"), provider.configDirectory());
    }

    @Test
    void usesAbsoluteXdgConfigHome(@TempDir Path tempDir) {
        Path home = tempDir.resolve("home");
        Path xdg = tempDir.resolve("xdg-config");

        SystemPathProvider provider = new SystemPathProvider(home, Map.of("XDG_CONFIG_HOME", xdg.toString()));

        assertEquals(xdg, provider.configDirectory());
    }

    @Test
    void ignoresBlankXdgConfigHome(@TempDir Path home) {
        SystemPathProvider provider = new SystemPathProvider(home, Map.of("XDG_CONFIG_HOME", "   "));

        assertEquals(home.resolve(".config"), provider.configDirectory());
    }

    @Test
    void ignoresRelativeXdgConfigHome(@TempDir Path home) {
        SystemPathProvider provider = new SystemPathProvider(home, Map.of("XDG_CONFIG_HOME", "relative-config"));

        assertEquals(home.resolve(".config"), provider.configDirectory());
    }

    @Test
    void ignoresInvalidXdgConfigHome(@TempDir Path home) {
        SystemPathProvider provider = new SystemPathProvider(home, Map.of("XDG_CONFIG_HOME", "\u0000bad"));

        assertEquals(home.resolve(".config"), provider.configDirectory());
    }

    @Test
    void rejectsInvalidConstructorArguments(@TempDir Path home) {
        assertThrows(NullPointerException.class, () -> new SystemPathProvider(null, Map.of()));
        assertThrows(NullPointerException.class, () -> new SystemPathProvider(home, null));
        assertThrows(IllegalArgumentException.class,
                () -> new SystemPathProvider(Path.of("relative-home"), Map.of()));
    }

    @Test
    void productionProviderReturnsAbsoluteDirectories() {
        SystemPathProvider provider = new SystemPathProvider();

        assertTrue(provider.home().isAbsolute());
        assertTrue(provider.configDirectory().isAbsolute());
    }
}
