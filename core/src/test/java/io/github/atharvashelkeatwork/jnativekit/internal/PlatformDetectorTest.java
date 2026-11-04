package io.github.atharvashelkeatwork.jnativekit.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.atharvashelkeatwork.jnativekit.Platform;

/**
 * Covers the platform detection rules from {@code docs/ARCHITECTURE.md}.
 */
class PlatformDetectorTest {

    static Stream<Arguments> cases() {
        return Stream.of(
                Arguments.of("Linux", Platform.LINUX),
                Arguments.of("linux", Platform.LINUX),
                Arguments.of("Linux Mint 21", Platform.LINUX),
                Arguments.of("Windows 10", Platform.WINDOWS),
                Arguments.of("Windows 11", Platform.WINDOWS),
                Arguments.of("Mac OS X", Platform.MACOS),
                Arguments.of("Darwin", Platform.MACOS),
                Arguments.of("darwin", Platform.MACOS),
                Arguments.of("FreeBSD", Platform.OTHER),
                Arguments.of("", Platform.OTHER),
                Arguments.of((Object) null, Platform.OTHER));
    }

    @ParameterizedTest(name = "os.name \"{0}\" -> {1}")
    @MethodSource("cases")
    void detectsDocumentedPlatforms(String osName, Platform expected) {
        assertEquals(expected, PlatformDetector.detect(osName));
    }

    @Test
    void darwinIsNotMistakenForWindows() {
        assertEquals(Platform.MACOS, PlatformDetector.detect("Darwin"));
    }
}
