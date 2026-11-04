package io.github.atharvashelkeatwork.jnativekit.providers.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.providers.awt.FakeBalloonBackend;

class WindowsAwtNotificationsProviderTest {

    private static final Notification NOTIFICATION =
            Notification.of("Backup done", "42 files synced");

    @Test
    void reportsItsIdentityAndPlatform() {
        WindowsAwtNotificationsProvider provider = provider(new FakeBalloonBackend());

        assertEquals("windows.awt-balloon", provider.id());
        assertTrue(provider.supports(Platform.WINDOWS));
        assertFalse(provider.supports(Platform.LINUX));
        assertFalse(provider.supports(Platform.MACOS));
        assertTrue(provider.priority() > 0);
    }

    @Test
    void capabilityIsAvailableWhenTheTrayIsSupported() {
        Capability capability = provider(new FakeBalloonBackend()).capability();

        assertEquals(Capability.Status.AVAILABLE, capability.status());
        assertEquals(Reason.NONE, capability.reason());
        assertNull(capability.remedy());
    }

    @Test
    void aHeadlessJvmReportsNoGraphicalSession() {
        Capability capability = provider(new FakeBalloonBackend().headless(true)).capability();

        assertEquals(Capability.Status.UNSUPPORTED_ENVIRONMENT, capability.status());
        assertEquals(Reason.NO_GRAPHICAL_SESSION, capability.reason());
    }

    @Test
    void anUnsupportedTrayReportsAnUnsupportedEnvironment() {
        Capability capability = provider(new FakeBalloonBackend().supported(false)).capability();

        assertEquals(Capability.Status.UNSUPPORTED_ENVIRONMENT, capability.status());
        assertEquals(Reason.BACKEND_MISSING, capability.reason());
        assertTrue(capability.remedy().toLowerCase().contains("system tray"));
    }

    @Test
    void showDisplaysTheBalloonAndReportsDispatchedUnverified() {
        FakeBalloonBackend backend = new FakeBalloonBackend();
        WindowsAwtNotificationsProvider provider = provider(backend);

        Result<Void> result = provider.show(NOTIFICATION);

        assertEquals(Result.Status.DISPATCHED_UNVERIFIED, result.status());
        assertEquals(Reason.NONE, result.reason());
        assertEquals("windows.awt-balloon", result.provider());
        assertNull(result.value());
        assertEquals(List.of(new FakeBalloonBackend.Message("Backup done", "42 files synced")),
                backend.messages());
    }

    @Test
    void hostileTitleAndBodyReachTheBackendUnchanged() {
        String title = "-Title \"quoted\" \\ backslash $HOME %s 日本語";
        String body = "Body\nwith\r\nnewlines, \"quotes\", \\backslash and $(whoami)";
        FakeBalloonBackend backend = new FakeBalloonBackend();

        Result<Void> result = provider(backend).show(Notification.of(title, body));

        assertEquals(Result.Status.DISPATCHED_UNVERIFIED, result.status());
        assertEquals(List.of(new FakeBalloonBackend.Message(title, body)), backend.messages());
    }

    @Test
    void aBackendFailureBecomesFailedBackendError() {
        FakeBalloonBackend backend = new FakeBalloonBackend()
                .failure(new IllegalStateException("tray exploded"));
        WindowsAwtNotificationsProvider provider = provider(backend);

        Result<Void> result = provider.show(NOTIFICATION);

        assertEquals(Result.Status.FAILED, result.status());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertEquals("windows.awt-balloon", result.provider());
        assertNull(result.value());
        assertTrue(result.detail().contains("tray exploded"));
    }

    @Test
    void showDoesNotTouchTheBackendWhenTheCapabilityIsUnavailable() {
        FakeBalloonBackend headless = new FakeBalloonBackend().headless(true);
        FakeBalloonBackend unsupported = new FakeBalloonBackend().supported(false);

        Result<Void> first = provider(headless).show(NOTIFICATION);
        Result<Void> second = provider(unsupported).show(NOTIFICATION);

        assertEquals(Result.Status.UNAVAILABLE, first.status());
        assertEquals(Reason.NO_GRAPHICAL_SESSION, first.reason());
        assertEquals(Result.Status.UNAVAILABLE, second.status());
        assertEquals(Reason.BACKEND_MISSING, second.reason());
        assertTrue(first.detail().contains("Run the application"));
        assertTrue(headless.messages().isEmpty());
        assertTrue(unsupported.messages().isEmpty());
    }

    @Test
    void rejectsANullNotificationWithoutTouchingTheBackend() {
        FakeBalloonBackend backend = new FakeBalloonBackend();

        assertThrows(NullPointerException.class, () -> provider(backend).show(null));
        assertTrue(backend.messages().isEmpty());
    }

    private static WindowsAwtNotificationsProvider provider(FakeBalloonBackend backend) {
        return new WindowsAwtNotificationsProvider(backend);
    }
}
