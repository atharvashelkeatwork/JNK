package io.github.atharvashelkeatwork.jnativekit.internal;

import java.util.Locale;
import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.Platform;

/**
 * Detects the current platform from {@code os.name}.
 *
 * <p>This is the only place in the library that reads {@code os.name}. Tests inject a
 * {@link Platform} instead of relying on the machine that runs them.
 */
public final class PlatformDetector {

    private PlatformDetector() {
    }

    /**
     * Detects the platform of the running JVM.
     *
     * @return the detected platform; never {@code null}
     */
    public static Platform detect() {
        return detect(System.getProperty("os.name"));
    }

    /**
     * Detects a platform from an operating system name.
     *
     * <p>Matching is case-insensitive. {@code mac} and {@code darwin} are checked before
     * {@code win}, because {@code darwin} itself contains {@code win}.
     *
     * @param osName the value of {@code os.name}; may be {@code null} or blank
     * @return the matching platform, or {@link Platform#OTHER} when nothing matches
     */
    static Platform detect(String osName) {
        if (osName == null) {
            return Platform.OTHER;
        }
        String lower = osName.toLowerCase(Locale.ROOT);
        if (lower.contains("mac") || lower.contains("darwin")) {
            return Platform.MACOS;
        }
        if (lower.contains("win")) {
            return Platform.WINDOWS;
        }
        if (lower.contains("linux")) {
            return Platform.LINUX;
        }
        return Platform.OTHER;
    }
}
