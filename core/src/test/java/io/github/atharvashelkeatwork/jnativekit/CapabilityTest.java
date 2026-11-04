package io.github.atharvashelkeatwork.jnativekit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Covers the {@code Capability} invariants with a parameterized test.
 */
class CapabilityTest {

    static Stream<Arguments> validCases() {
        return Stream.of(
                Arguments.of((Object) Capability.Status.AVAILABLE, (Object) Reason.NONE, (Object) "ok", (Object) null, (Object) true),
                Arguments.of((Object) Capability.Status.UNSUPPORTED_OS, (Object) Reason.UNSUPPORTED_PLATFORM, (Object) "no provider",
                        (Object) "use a backend for this platform", (Object) false),
                Arguments.of((Object) Capability.Status.UNSUPPORTED_ENVIRONMENT, (Object) Reason.NO_GRAPHICAL_SESSION,
                        (Object) "headless", (Object) null, (Object) false),
                Arguments.of((Object) Capability.Status.MISSING_DEPENDENCY, (Object) Reason.BACKEND_MISSING, (Object) "notify-send not found",
                        (Object) "install libnotify-bin", (Object) false),
                Arguments.of((Object) Capability.Status.PERMISSION_DENIED, (Object) Reason.PERMISSION_DENIED, (Object) "denied",
                        (Object) null, (Object) false));
    }

    static Stream<Arguments> invalidCases() {
        return Stream.of(
                Arguments.of((Object) Capability.Status.AVAILABLE, (Object) Reason.BACKEND_MISSING, (Object) "ok", (Object) null, (Object) IllegalArgumentException.class),
                Arguments.of((Object) Capability.Status.AVAILABLE, (Object) Reason.NONE, (Object) "ok", (Object) "install something", (Object) IllegalArgumentException.class),
                Arguments.of((Object) Capability.Status.UNSUPPORTED_OS, (Object) Reason.NONE, (Object) "no provider", (Object) null, (Object) IllegalArgumentException.class),
                Arguments.of((Object) null, (Object) null, (Object) null, (Object) null, (Object) NullPointerException.class),
                Arguments.of((Object) Capability.Status.AVAILABLE, (Object) Reason.NONE, (Object) null, (Object) null, (Object) NullPointerException.class),
                Arguments.of((Object) Capability.Status.UNSUPPORTED_OS, (Object) Reason.UNSUPPORTED_PLATFORM, (Object) null, (Object) null, (Object) NullPointerException.class));
    }

    @ParameterizedTest(name = "valid case {index}")
    @MethodSource("validCases")
    void acceptsDocumentedInvariants(Capability.Status status, Reason reason, String detail, String remedy, boolean available) {
        Capability capability = new Capability(status, reason, detail, remedy);
        assertEquals(available, capability.status() == Capability.Status.AVAILABLE);
    }

    @ParameterizedTest(name = "invalid case {index}")
    @MethodSource("invalidCases")
    void rejectsInvalidInvariants(Capability.Status status, Reason reason, String detail, String remedy, Class<? extends Throwable> expected) {
        assertThrows(expected, () -> new Capability(status, reason, detail, remedy));
    }

    @Test
    void availableCapabilityRequiresNullRemedy() {
        Capability capability = new Capability(Capability.Status.AVAILABLE, Reason.NONE, "ok", null);
        assertEquals(Capability.Status.AVAILABLE, capability.status());
        assertEquals(Reason.NONE, capability.reason());
        assertEquals("ok", capability.detail());
        assertEquals(null, capability.remedy());
    }
}
