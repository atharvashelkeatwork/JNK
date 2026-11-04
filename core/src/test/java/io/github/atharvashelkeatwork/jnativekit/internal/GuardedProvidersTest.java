package io.github.atharvashelkeatwork.jnativekit.internal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.TrayHandle;
import io.github.atharvashelkeatwork.jnativekit.TrayIcon;

/**
 * Covers the guard that converts unexpected provider failures into
 * {@code FAILED(BACKEND_ERROR)} or non-available values.
 */
class GuardedProvidersTest {

    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
    };

    private static Notification notification() {
        return Notification.of("Title", "Body");
    }

    private static TrayIcon icon() {
        return new TrayIcon(PNG, "tip", List.of());
    }

    private static AutostartEntry entry() {
        return new AutostartEntry(
                "com.example.app",
                "Example",
                Path.of("").toAbsolutePath().resolve("opt").resolve("example").resolve("app"),
                List.of());
    }

    @Test
    void notificationsPassesNormalResultThrough() {
        FakeProviders.NotificationsStub provider = FakeProviders.notifications("linux.notify-send", 1);
        GuardedNotifications guarded = new GuardedNotifications(provider);

        assertSame(provider.capability(), guarded.capability());
        Result<Void> result = guarded.show(notification());
        assertTrue(result.isDispatchedUnverified());
        assertEquals("linux.notify-send", result.provider());
        assertEquals("Title", provider.lastNotification().title());
    }

    @Test
    void notificationsConvertsThrowingShowToFailed() {
        FakeProviders.NotificationsStub provider = FakeProviders.notifications("linux.notify-send", 1)
                .failShow(new IllegalStateException("boom"));
        GuardedNotifications guarded = new GuardedNotifications(provider);

        Result<Void> result = guarded.show(notification());

        assertTrue(result.isFailed());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertEquals("linux.notify-send", result.provider());
        assertNull(result.value());
        assertTrue(result.detail().contains("boom"));
    }

    @Test
    void notificationsConvertsThrowingCapabilityToUnavailableCapability() {
        FakeProviders.NotificationsStub provider = FakeProviders.notifications("linux.notify-send", 1)
                .failCapability(new IllegalStateException("boom"));
        GuardedNotifications guarded = new GuardedNotifications(provider);

        Capability capability = guarded.capability();

        assertFalse(capability.status() == Capability.Status.AVAILABLE);
        assertEquals(Reason.BACKEND_ERROR, capability.reason());
        assertTrue(capability.detail().contains("linux.notify-send"));
        assertNull(capability.remedy());
    }

    @Test
    void providerIdFailureFallsBackToClassName() {
        FakeProviders.NotificationsStub provider = FakeProviders.notifications("linux.notify-send", 1)
                .failId(new IllegalStateException("no id"))
                .failShow(new IllegalStateException("boom"));
        GuardedNotifications guarded = new GuardedNotifications(provider);

        Result<Void> result = guarded.show(notification());

        assertTrue(result.isFailed());
        assertTrue(result.provider().contains(FakeProviders.NotificationsStub.class.getSimpleName()));
    }

    @Test
    void providerIdIsResolvedOncePerGuard() {
        FakeProviders.NotificationsStub provider = FakeProviders.notifications("linux.notify-send", 1);
        GuardedNotifications guarded = new GuardedNotifications(provider);

        guarded.capability();
        guarded.show(notification());

        assertEquals(1, provider.idCalls());
    }

    @Test
    void nullProviderResultsBecomeFailed() {
        FakeProviders.NotificationsStub provider = FakeProviders.notifications("linux.notify-send", 1)
                .showResult(null)
                .capability(null);
        GuardedNotifications guarded = new GuardedNotifications(provider);

        assertTrue(guarded.show(notification()).isFailed());
        assertEquals(Reason.BACKEND_ERROR, guarded.capability().reason());
    }

    @Test
    void notificationsStillRejectsNullCallerInput() {
        FakeProviders.NotificationsStub provider = FakeProviders.notifications("linux.notify-send", 1);
        GuardedNotifications guarded = new GuardedNotifications(provider);

        assertThrows(NullPointerException.class, () -> guarded.show(null));
        assertEquals(0, provider.showCalls());
    }

    @Test
    void notificationsConvertsErrorFromProviderToFailed() {
        FakeProviders.NotificationsStub provider = FakeProviders.notifications("linux.notify-send", 1)
                .failShow(new LinkageError("backend module missing"));
        GuardedNotifications guarded = new GuardedNotifications(provider);

        Result<Void> result = guarded.show(notification());

        assertTrue(result.isFailed());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertEquals("linux.notify-send", result.provider());
        assertTrue(result.detail().contains("LinkageError"));
    }

    @Test
    void notificationsConvertsErrorFromCapabilityToUnavailableCapability() {
        FakeProviders.NotificationsStub provider = FakeProviders.notifications("linux.notify-send", 1)
                .failCapability(new LinkageError("backend module missing"));
        GuardedNotifications guarded = new GuardedNotifications(provider);

        Capability capability = guarded.capability();

        assertFalse(capability.status() == Capability.Status.AVAILABLE);
        assertEquals(Reason.BACKEND_ERROR, capability.reason());
        assertTrue(capability.detail().contains("linux.notify-send"));
    }

    @Test
    void autostartConvertsThrowingActionsToFailed() {
        FakeProviders.AutostartStub provider = FakeProviders.autostart("linux.desktop-entry", 1)
                .failEnable(new IllegalStateException("enable boom"))
                .failDisable(new IllegalStateException("disable boom"))
                .failIsEnabled(new IllegalStateException("read boom"));
        GuardedAutostart guarded = new GuardedAutostart(provider);

        assertTrue(guarded.enable(entry()).isFailed());
        assertTrue(guarded.disable("com.example.app").isFailed());
        Result<Boolean> read = guarded.isEnabled("com.example.app");
        assertTrue(read.isFailed());
        assertEquals(Reason.BACKEND_ERROR, read.reason());
    }

    @Test
    void autostartConvertsErrorToFailed() {
        FakeProviders.AutostartStub provider = FakeProviders.autostart("linux.desktop-entry", 1)
                .failEnable(new AssertionError("enable exploded"));
        GuardedAutostart guarded = new GuardedAutostart(provider);

        assertTrue(guarded.enable(entry()).isFailed());
        assertEquals(Reason.BACKEND_ERROR, guarded.enable(entry()).reason());
    }

    @Test
    void autostartCapabilityErrorBecomesUnavailableCapability() {
        FakeProviders.AutostartStub provider = FakeProviders.autostart("linux.desktop-entry", 1)
                .failCapability(new AssertionError("capability exploded"));
        GuardedAutostart guarded = new GuardedAutostart(provider);

        Capability capability = guarded.capability();

        assertFalse(capability.status() == Capability.Status.AVAILABLE);
        assertEquals(Reason.BACKEND_ERROR, capability.reason());
        assertTrue(capability.detail().contains("linux.desktop-entry"));
    }

    @Test
    void autostartRequiresValueForSuccessStatus() {
        FakeProviders.AutostartStub provider = FakeProviders.autostart("linux.desktop-entry", 1)
                .isEnabledResult(new Result<>(
                        Result.Status.CONFIRMED, null, "linux.desktop-entry", Reason.NONE, "bad"));
        GuardedAutostart guarded = new GuardedAutostart(provider);

        Result<Boolean> read = guarded.isEnabled("com.example.app");

        assertTrue(read.isFailed());
        assertEquals(Reason.BACKEND_ERROR, read.reason());
    }

    @Test
    void autostartValidatesCallerInputBeforeTheProvider() {
        FakeProviders.AutostartStub provider = FakeProviders.autostart("linux.desktop-entry", 1);
        GuardedAutostart guarded = new GuardedAutostart(provider);

        assertThrows(NullPointerException.class, () -> guarded.enable(null));
        assertThrows(NullPointerException.class, () -> guarded.disable(null));
        assertThrows(NullPointerException.class, () -> guarded.isEnabled(null));
        assertThrows(IllegalArgumentException.class, () -> guarded.disable("bad app id"));
        assertThrows(IllegalArgumentException.class, () -> guarded.isEnabled("-bad"));
        assertEquals(0, provider.enableCalls());
        assertEquals(0, provider.disableCalls());
        assertEquals(0, provider.isEnabledCalls());
    }

    @Test
    void autostartPassesNormalValuesThrough() {
        FakeProviders.AutostartStub provider = FakeProviders.autostart("linux.desktop-entry", 1);
        GuardedAutostart guarded = new GuardedAutostart(provider);

        assertTrue(guarded.enable(entry()).isConfirmed());
        assertTrue(guarded.disable("com.example.app").isConfirmed());
        assertEquals(Boolean.TRUE, guarded.isEnabled("com.example.app").value());
        assertEquals("com.example.app", provider.lastEntry().appId());
        assertEquals("com.example.app", provider.lastAppId());
    }

    @Test
    void trayConvertsThrowingOpenToFailed() {
        FakeProviders.TrayStub provider = FakeProviders.tray("awt.systemtray", 1)
                .failOpen(new IllegalStateException("boom"));
        GuardedTray guarded = new GuardedTray(provider);

        Result<TrayHandle> result = guarded.open(icon());

        assertTrue(result.isFailed());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertEquals("awt.systemtray", result.provider());
        assertThrows(NullPointerException.class, () -> guarded.open(null));
    }

    @Test
    void trayConvertsThrowingCapabilityToUnavailableCapability() {
        FakeProviders.TrayStub provider = FakeProviders.tray("awt.systemtray", 1)
                .failCapability(new AssertionError("capability exploded"));
        GuardedTray guarded = new GuardedTray(provider);

        Capability capability = guarded.capability();

        assertFalse(capability.status() == Capability.Status.AVAILABLE);
        assertEquals(Reason.BACKEND_ERROR, capability.reason());
        assertTrue(capability.detail().contains("awt.systemtray"));
    }

    @Test
    void trayOpenWhenCapabilityIsNotAvailableReturnsUnavailable() {
        FakeProviders.TrayStub provider = FakeProviders.tray("awt.systemtray", 1)
                .capability(new Capability(
                        Capability.Status.UNSUPPORTED_ENVIRONMENT,
                        Reason.BACKEND_ERROR,
                        "no tray backend",
                        null));
        GuardedTray guarded = new GuardedTray(provider);

        Result<TrayHandle> result = guarded.open(icon());

        assertTrue(result.isUnavailable());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertNull(provider.lastIcon());
    }

    @Test
    void trayWrapsSuccessfulHandleAndContainsCloseFailures() {
        FakeProviders.TrayStub provider = FakeProviders.tray("awt.systemtray", 1)
                .openResult(new Result<>(
                        Result.Status.CONFIRMED,
                        new ThrowingTrayHandle(),
                        "awt.systemtray",
                        Reason.NONE,
                        "opened"));
        GuardedTray guarded = new GuardedTray(provider);

        Result<TrayHandle> result = guarded.open(icon());

        assertTrue(result.isConfirmed());
        assertInstanceOf(GuardedTrayHandle.class, result.value());
        GuardedTrayHandle handle = (GuardedTrayHandle) result.value();
        assertTrue(handle.isOpen());
        assertDoesNotThrow(handle::close);
        assertFalse(handle.isOpen());
    }

    private static final class ThrowingTrayHandle implements TrayHandle {

        private boolean closed;

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            throw new IllegalStateException("close boom");
        }
    }
}
