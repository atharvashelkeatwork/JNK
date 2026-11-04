package io.github.atharvashelkeatwork.jnativekit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

/**
 * Covers the {@code Result} invariant table of {@code docs/API.md} with a parameterized test.
 */
class ResultTest {

    static Stream<Arguments> validCases() {
        return Stream.of(
                Arguments.of((Object) Result.Status.CONFIRMED, (Object) "value", (Object) "linux.notify-send",
                        (Object) Reason.NONE, (Object) "accepted"),
                Arguments.of((Object) Result.Status.CONFIRMED, (Object) null, (Object) "linux.notify-send",
                        (Object) Reason.NONE, (Object) "ok"),
                Arguments.of((Object) Result.Status.DISPATCHED_UNVERIFIED, (Object) null, (Object) "linux.notify-send",
                        (Object) Reason.NONE, (Object) "accepted"),
                Arguments.of((Object) Result.Status.UNAVAILABLE, (Object) null, (Object) null,
                        (Object) Reason.BACKEND_MISSING, (Object) "notify-send not found"),
                Arguments.of((Object) Result.Status.UNAVAILABLE, (Object) null, (Object) "linux.notify-send",
                        (Object) Reason.UNSUPPORTED_PLATFORM, (Object) "no provider"),
                Arguments.of((Object) Result.Status.FAILED, (Object) null, (Object) "linux.notify-send",
                        (Object) Reason.TIMEOUT, (Object) "timed out"));
    }

    static Stream<Arguments> invalidCases() {
        return Stream.of(
                Arguments.of((Object) null, (Object) null, (Object) null, (Object) null, (Object) null, (Object) NullPointerException.class),
                Arguments.of((Object) Result.Status.CONFIRMED, (Object) "value", (Object) null,
                        (Object) Reason.NONE, (Object) "ok", (Object) NullPointerException.class),
                Arguments.of((Object) Result.Status.CONFIRMED, (Object) "value", (Object) "linux.notify-send",
                        (Object) Reason.BACKEND_ERROR, (Object) "tool failed", (Object) IllegalArgumentException.class),
                Arguments.of((Object) Result.Status.DISPATCHED_UNVERIFIED, (Object) null, (Object) "linux.notify-send",
                        (Object) Reason.IO_ERROR, (Object) "io failed", (Object) IllegalArgumentException.class),
                Arguments.of((Object) Result.Status.UNAVAILABLE, (Object) "value", (Object) null,
                        (Object) Reason.BACKEND_MISSING, (Object) "missing", (Object) IllegalArgumentException.class),
                Arguments.of((Object) Result.Status.UNAVAILABLE, (Object) null, (Object) "linux.notify-send",
                        (Object) Reason.NONE, (Object) "missing", (Object) IllegalArgumentException.class),
                Arguments.of((Object) Result.Status.FAILED, (Object) null, (Object) null,
                        (Object) Reason.TIMEOUT, (Object) "timed out", (Object) NullPointerException.class),
                Arguments.of((Object) Result.Status.FAILED, (Object) "value", (Object) "linux.notify-send",
                        (Object) Reason.TIMEOUT, (Object) "timed out", (Object) IllegalArgumentException.class),
                Arguments.of((Object) Result.Status.CONFIRMED, (Object) "value", (Object) "linux.notify-send",
                        (Object) null, (Object) "ok", (Object) NullPointerException.class),
                Arguments.of((Object) Result.Status.CONFIRMED, (Object) "value", (Object) "linux.notify-send",
                        (Object) Reason.NONE, (Object) null, (Object) NullPointerException.class));
    }

    @ParameterizedTest(name = "valid case {index}")
    @MethodSource("validCases")
    void acceptsDocumentedInvariants(Result.Status status, Object value, String provider, Reason reason, String detail) {
        Result<Object> result = new Result<>(status, value, provider, reason, detail);
        assertEquals(isConfirmed(status), result.isConfirmed());
        assertEquals(isDispatchedUnverified(status), result.isDispatchedUnverified());
        assertEquals(isUnavailable(status), result.isUnavailable());
        assertEquals(isFailed(status), result.isFailed());
    }

    @ParameterizedTest(name = "invalid case {index}")
    @MethodSource("invalidCases")
    void rejectsInvalidInvariants(Result.Status status, Object value, String provider, Reason reason, String detail, Class<? extends Throwable> expected) {
        assertThrows(expected, () -> new Result<>(status, value, provider, reason, detail));
    }

    static boolean isConfirmed(Result.Status status) {
        return switch (status) {
            case CONFIRMED -> true;
            default -> false;
        };
    }

    static boolean isDispatchedUnverified(Result.Status status) {
        return switch (status) {
            case DISPATCHED_UNVERIFIED -> true;
            default -> false;
        };
    }

    static boolean isUnavailable(Result.Status status) {
        return switch (status) {
            case UNAVAILABLE -> true;
            default -> false;
        };
    }

    static boolean isFailed(Result.Status status) {
        return switch (status) {
            case FAILED -> true;
            default -> false;
        };
    }
}
