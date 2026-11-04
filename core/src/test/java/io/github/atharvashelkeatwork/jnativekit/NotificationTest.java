package io.github.atharvashelkeatwork.jnativekit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Covers the {@code Notification} validation rules with a parameterized test.
 */
class NotificationTest {

    static Stream<Arguments> validCases() {
        return Stream.of(
                Arguments.of((Object) "Backup done", (Object) "42 files synced"),
                Arguments.of((Object) "Hello\nWorld", (Object) "line1\nline2"),
                Arguments.of((Object) "T".repeat(256), (Object) "b".repeat(4096)),
                Arguments.of((Object) "Backup done", (Object) ""));
    }

    static Stream<Arguments> invalidCases() {
        return Stream.of(
                Arguments.of((Object) null, (Object) "42 files synced", (Object) NullPointerException.class),
                Arguments.of((Object) "Backup done", (Object) null, (Object) NullPointerException.class),
                Arguments.of((Object) "   ", (Object) "", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "T".repeat(257), (Object) "b", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "Title\u0000", (Object) "body", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "Title", (Object) "body\u0000", (Object) IllegalArgumentException.class),
                Arguments.of((Object) "Title", (Object) "b".repeat(4097), (Object) IllegalArgumentException.class));
    }

    @ParameterizedTest(name = "valid case {index}")
    @MethodSource("validCases")
    void acceptsValidNotifications(String title, String body) {
        Notification notification = new Notification(title, body);
        assertEquals(title, notification.title());
        assertEquals(body, notification.body());
    }

    @ParameterizedTest(name = "invalid case {index}")
    @MethodSource("invalidCases")
    void rejectsInvalidNotifications(String title, String body, Class<? extends Throwable> expected) {
        assertThrows(expected, () -> new Notification(title, body));
    }

    @Test
    void ofCreatesValidatedNotification() {
        Notification notification = Notification.of("Backup done", "42 files synced");
        assertEquals("Backup done", notification.title());
        assertEquals("42 files synced", notification.body());
    }

    @Test
    void blankTitleIsRejectedByFactory() {
        assertThrows(IllegalArgumentException.class, () -> Notification.of("   ", "body"));
    }
}
