package io.github.atharvashelkeatwork.jnativekit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.atharvashelkeatwork.jnativekit.internal.Validation;

/**
 * Covers the {@code AutostartEntry} validation rules and defensive copying.
 */
class AutostartEntryTest {

    private static final String VALID_APP_ID = "com.example.notes";

    private Path absoluteApp() {
        return Path.of("").toAbsolutePath().resolve("opt").resolve("My App").resolve("app");
    }

    @Test
    void acceptsValidEntryAndCopiesArguments() {
        List<String> source = new java.util.ArrayList<>(List.of("--minimized", "with space"));
        AutostartEntry entry = new AutostartEntry(VALID_APP_ID, "Example Notes", absoluteApp(), source);
        assertEquals(VALID_APP_ID, entry.appId());
        assertEquals("Example Notes", entry.displayName());
        assertEquals(absoluteApp(), entry.executable());
        assertEquals(2, entry.arguments().size());
        source.add("late");
        assertEquals(2, entry.arguments().size());
        assertThrows(UnsupportedOperationException.class, () -> entry.arguments().add("late"));
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class,
                () -> new AutostartEntry(VALID_APP_ID, "Example Notes", absoluteApp(), null));
    }

    @Test
    void rejectsRelativeExecutable() {
        assertThrows(IllegalArgumentException.class,
                () -> Validation.requireAbsoluteExecutable(Path.of("app")));
    }

    @Test
    void acceptsAbsoluteExecutable() {
        assertEquals(absoluteApp(), Validation.requireAbsoluteExecutable(absoluteApp()));
    }

    static Stream<Arguments> validAppIdCases() {
        return Stream.of(
                Arguments.of((Object) "a", (Object) "a"),
                Arguments.of((Object) "A0", (Object) "A0"),
                Arguments.of((Object) "com.example.app_1-x", (Object) "com.example.app_1-x"));
    }

    static Stream<Arguments> invalidAppIdCases() {
        return Stream.of(
                Arguments.of((Object) null, (Object) NullPointerException.class),
                Arguments.of((Object) "", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "-abc", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "_abc", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "abc def", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "abc/def", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "x".repeat(65), (Object) IllegalArgumentException.class),
                Arguments.of((Object) "é", (Object) IllegalArgumentException.class));
    }

    static Stream<Arguments> validDisplayNameCases() {
        return Stream.of(
                Arguments.of((Object) "Example Notes", (Object) "Example Notes"),
                Arguments.of((Object) "A".repeat(128), (Object) "A".repeat(128)));
    }

    static Stream<Arguments> invalidDisplayNameCases() {
        return Stream.of(
                Arguments.of((Object) null, (Object) NullPointerException.class),
                Arguments.of((Object) "", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "   ", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "n".repeat(129), (Object) IllegalArgumentException.class),
                Arguments.of((Object) "\u0007", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "\n", (Object) IllegalArgumentException.class));
    }

    static Stream<Arguments> invalidArgumentCases() {
        return Stream.of(
                Arguments.of((Object) "a\u0000b", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "a\rb", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "a\nb", (Object) IllegalArgumentException.class));
    }

    @ParameterizedTest(name = "valid appId case {index}")
    @MethodSource("validAppIdCases")
    void acceptsValidAppIds(Object appId, Object expected) {
        assertEquals(expected, appId);
        String value = (String) appId;
        assertTrue(Validation.requireAppId(value).equals(value));
    }

    @ParameterizedTest(name = "invalid appId case {index}")
    @MethodSource("invalidAppIdCases")
    void rejectsInvalidAppIds(Object appId, Object expected) {
        assertThrows((Class<? extends Throwable>) expected, () -> Validation.requireAppId((String) appId));
    }

    @ParameterizedTest(name = "valid displayName case {index}")
    @MethodSource("validDisplayNameCases")
    void acceptsValidDisplayNames(Object displayName, Object expected) {
        assertEquals(expected, displayName);
        String value = (String) displayName;
        assertTrue(Validation.requireDisplayName(value).equals(value));
    }

    @ParameterizedTest(name = "invalid displayName case {index}")
    @MethodSource("invalidDisplayNameCases")
    void rejectsInvalidDisplayNames(Object displayName, Object expected) {
        assertThrows((Class<? extends Throwable>) expected, () -> Validation.requireDisplayName((String) displayName));
    }

    @ParameterizedTest(name = "invalid argument case {index}")
    @MethodSource("invalidArgumentCases")
    void rejectsInvalidArguments(Object argument, Object expected) {
        assertThrows((Class<? extends Throwable>) expected, () -> Validation.requireArgument((String) argument));
    }
}
