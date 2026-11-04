package io.github.atharvashelkeatwork.jnativekit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.github.atharvashelkeatwork.jnativekit.internal.FakeProviders;

/**
 * Covers the {@code Desktop} facade: lazy singletons, unavailable fallbacks, provider selection and
 * the runtime-exception guard.
 */
class DesktopTest {

    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'
    };

    @AfterEach
    void resetFacade() {
        Desktop.resetForTests();
    }

    private static AutostartEntry entry() {
        return new AutostartEntry(
                "com.example.app",
                "Example",
                Path.of("").toAbsolutePath().resolve("opt").resolve("example").resolve("app"),
                List.of());
    }

    private static TrayIcon icon() {
        return new TrayIcon(PNG, "tip", List.of());
    }

    @Test
    void noProvidersReturnsUnavailableFacades() {
        Desktop.resetForTests(Platform.LINUX, List.of());

        Capability notifications = Desktop.notifications().capability();
        assertEquals(Capability.Status.UNSUPPORTED_OS, notifications.status());
        assertEquals(Reason.UNSUPPORTED_PLATFORM, notifications.reason());
        assertNotNull(notifications.remedy());

        Result<Void> shown = Desktop.notifications().show(Notification.of("Title", "Body"));
        assertTrue(shown.isUnavailable());
        assertEquals(Reason.UNSUPPORTED_PLATFORM, shown.reason());
        assertNull(shown.provider());

        assertTrue(Desktop.autostart().enable(entry()).isUnavailable());
        assertTrue(Desktop.autostart().disable("com.example.app").isUnavailable());
        assertTrue(Desktop.autostart().isEnabled("com.example.app").isUnavailable());
        assertTrue(Desktop.tray().open(icon()).isUnavailable());
        assertEquals(Capability.Status.UNSUPPORTED_OS, Desktop.tray().capability().status());
        assertNotNull(Desktop.autostart().capability().remedy());
    }

    @Test
    void unavailableFacadesStillValidateCallerInput() {
        Desktop.resetForTests(Platform.OTHER, List.of());

        assertThrows(NullPointerException.class, () -> Desktop.notifications().show(null));
        assertThrows(NullPointerException.class, () -> Desktop.autostart().enable(null));
        assertThrows(NullPointerException.class, () -> Desktop.autostart().disable(null));
        assertThrows(NullPointerException.class, () -> Desktop.autostart().isEnabled(null));
        assertThrows(IllegalArgumentException.class, () -> Desktop.autostart().disable("bad app id"));
        assertThrows(IllegalArgumentException.class, () -> Desktop.autostart().isEnabled("-bad"));
        assertThrows(NullPointerException.class, () -> Desktop.tray().open(null));
    }

    @Test
    void highestPriorityNotificationsProviderIsSelected() {
        FakeProviders.NotificationsStub low = FakeProviders.notifications("linux.low", 1);
        FakeProviders.NotificationsStub high = FakeProviders.notifications("linux.high", 10);
        Desktop.resetForTests(Platform.LINUX, List.of(low, high));

        Result<Void> result = Desktop.notifications().show(Notification.of("Title", "Body"));

        assertTrue(result.isDispatchedUnverified());
        assertEquals("linux.high", result.provider());
        assertEquals(0, low.showCalls());
        assertEquals(1, high.showCalls());
    }

    @Test
    void everyFeatureIsResolvedIndependently() {
        FakeProviders.NotificationsStub notifications = FakeProviders.notifications("linux.notify-send", 1);
        FakeProviders.AutostartStub autostart = FakeProviders.autostart("linux.desktop-entry", 1);
        FakeProviders.TrayStub tray = FakeProviders.tray("awt.systemtray", 1);
        Desktop.resetForTests(Platform.LINUX, List.of(notifications, autostart, tray));

        assertEquals("linux.notify-send",
                Desktop.notifications().show(Notification.of("Title", "Body")).provider());
        assertEquals("linux.desktop-entry", Desktop.autostart().enable(entry()).provider());
        assertEquals("awt.systemtray", Desktop.tray().open(icon()).provider());
    }

    @Test
    void accessorsReturnStableSingletons() {
        FakeProviders.NotificationsStub provider = FakeProviders.notifications("linux.notify-send", 1);
        Desktop.resetForTests(Platform.LINUX, List.of(provider));

        assertSame(Desktop.notifications(), Desktop.notifications());
        assertSame(Desktop.autostart(), Desktop.autostart());
        assertSame(Desktop.tray(), Desktop.tray());
        assertSame(Desktop.platform(), Desktop.platform());
    }

    @Test
    void unsupportedOnlyProvidersFallBackToUnavailable() {
        FakeProviders.NotificationsStub windowsOnly =
                FakeProviders.notifications("windows.balloon", 100).supports(false);
        Desktop.resetForTests(Platform.LINUX, List.of(windowsOnly));

        assertTrue(Desktop.notifications().show(Notification.of("Title", "Body")).isUnavailable());
        assertEquals(0, windowsOnly.showCalls());
    }

    @Test
    void providerThrowingAtCallTimeDoesNotEscapeDesktop() {
        FakeProviders.NotificationsStub broken = FakeProviders.notifications("linux.broken", 1)
                .failShow(new IllegalStateException("boom"));
        Desktop.resetForTests(Platform.LINUX, List.of(broken));

        Result<Void> result = Desktop.notifications().show(Notification.of("Title", "Body"));

        assertTrue(result.isFailed());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertEquals("linux.broken", result.provider());
    }

    @Test
    void platformComesFromTheInjectedValue() {
        Desktop.resetForTests(Platform.MACOS, List.of());

        assertEquals(Platform.MACOS, Desktop.platform());
        assertNotNull(Desktop.notifications());
        assertFalse(Desktop.notifications().capability().status() == Capability.Status.AVAILABLE);
    }

    @Test
    void resetRestoresAutomaticPlatformDetection() {
        Desktop.resetForTests(Platform.MACOS, List.of());
        assertEquals(Platform.MACOS, Desktop.platform());

        Desktop.resetForTests();

        assertNotNull(Desktop.platform());
    }

    @Test
    void providerDiscoveryDoesNotDependOnTheCallingThreadClassLoader() {
        Desktop.resetForTests();
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new RestrictedClassLoader());
        try {
            assertTrue(Desktop.notifications().capability().status() == Capability.Status.AVAILABLE);
        } finally {
            Thread.currentThread().setContextClassLoader(original);
            Desktop.resetForTests();
        }
    }

    @Test
    void providerSelectionFailureFallsBackToTheUnavailableFacade() {
        FakeProviders.NotificationsStub broken = FakeProviders.notifications("linux.broken", 1)
                .failSupports(new LinkageError("backend module missing"));
        Desktop.resetForTests(Platform.LINUX, List.of(broken));

        Capability capability = Desktop.notifications().capability();
        Result<Void> result = Desktop.notifications().show(Notification.of("Title", "Body"));

        assertTrue(capability.status() == Capability.Status.UNSUPPORTED_OS);
        assertTrue(result.isUnavailable());
    }

    /**
     * A context class loader that sees none of the application classes; used to prove that provider
     * discovery no longer depends on it.
     */
    private static final class RestrictedClassLoader extends ClassLoader {

        private RestrictedClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
