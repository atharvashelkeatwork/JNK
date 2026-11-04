package io.github.atharvashelkeatwork.jnativekit.providers.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.TrayHandle;
import io.github.atharvashelkeatwork.jnativekit.TrayIcon;
import io.github.atharvashelkeatwork.jnativekit.TrayMenuItem;

/**
 * Tier T2: the AWT tray provider against a fake backend.
 *
 * <p>These tests prove result mapping, PNG decoding, menu wiring and handle lifecycle. They say
 * nothing about a real desktop: no icon is displayed here.
 */
class AwtSystemTrayProviderTest {

    private static final byte[] PNGS = png(3, 4);

    private static final Executor DIRECT = Runnable::run;

    @Test
    void reportsItsIdentityAndSupportsEveryPlatform() {
        AwtSystemTrayProvider provider = provider(new FakeTrayBackend(), DIRECT);

        assertEquals("awt.systemtray", provider.id());
        assertTrue(provider.supports(Platform.LINUX));
        assertTrue(provider.supports(Platform.WINDOWS));
        assertTrue(provider.supports(Platform.MACOS));
        assertTrue(provider.supports(Platform.OTHER));
        assertTrue(provider.priority() > 0);
        assertThrows(NullPointerException.class, () -> provider.supports(null));
    }

    @Test
    void capabilityIsAvailableWhenTheTrayIsSupported() {
        Capability capability = provider(new FakeTrayBackend(), DIRECT).capability();

        assertEquals(Capability.Status.AVAILABLE, capability.status());
        assertEquals(Reason.NONE, capability.reason());
        assertNull(capability.remedy());
    }

    @Test
    void aHeadlessJvmReportsNoGraphicalSession() {
        Capability capability =
                provider(new FakeTrayBackend().headless(true), DIRECT).capability();

        assertEquals(Capability.Status.UNSUPPORTED_ENVIRONMENT, capability.status());
        assertEquals(Reason.NO_GRAPHICAL_SESSION, capability.reason());
        assertTrue(capability.remedy().toLowerCase().contains("desktop"));
    }

    @Test
    void anUnsupportedTrayReportsAnUnsupportedEnvironment() {
        Capability capability =
                provider(new FakeTrayBackend().supported(false), DIRECT).capability();

        assertEquals(Capability.Status.UNSUPPORTED_ENVIRONMENT, capability.status());
        assertEquals(Reason.BACKEND_MISSING, capability.reason());
        assertTrue(capability.remedy().toLowerCase().contains("system tray"));
    }

    @Test
    void openDecodesThePngAndForwardsTooltipAndMenu() {
        FakeTrayBackend backend = new FakeTrayBackend();
        AwtSystemTrayProvider provider = provider(backend, DIRECT);
        AtomicInteger quitCalls = new AtomicInteger();
        TrayIcon icon = new TrayIcon(PNGS, "Example Notes", List.of(
                TrayMenuItem.item("Quit", quitCalls::incrementAndGet),
                TrayMenuItem.separator(),
                TrayMenuItem.item("Do \"it\" 100%", () -> { })));

        Result<TrayHandle> result = provider.open(icon);

        assertEquals(Result.Status.CONFIRMED, result.status());
        assertEquals(Reason.NONE, result.reason());
        assertEquals("awt.systemtray", result.provider());
        assertTrue(result.detail().toLowerCase().contains("visibility"));
        assertTrue(result.value().isOpen());

        assertEquals(1, backend.icons().size());
        FakeTrayBackend.Added added = backend.icon(0);
        Image image = added.image();
        assertEquals(3, image.getWidth(null));
        assertEquals(4, image.getHeight(null));
        assertEquals("Example Notes", added.tooltip());
        assertEquals(List.of("Quit", "", "Do \"it\" 100%"),
                added.menu().stream().map(TrayBackend.MenuEntry::label).toList());
        assertTrue(added.menu().get(1).isSeparator());

        backend.click(0, 0);
        assertEquals(1, quitCalls.get());
    }

    @Test
    void hostileTooltipAndLabelsReachTheBackendUnchanged() {
        FakeTrayBackend backend = new FakeTrayBackend();
        String tooltip = "tip \"quoted\" \\ $HOME %s 日本語";
        String label = "-label \"quoted\" \\ $(whoami) 100%";
        TrayIcon icon = new TrayIcon(PNGS, tooltip,
                List.of(TrayMenuItem.item(label, () -> { })));

        Result<TrayHandle> result = provider(backend, DIRECT).open(icon);

        assertTrue(result.isConfirmed(), result::detail);
        assertEquals(tooltip, backend.icon(0).tooltip());
        assertEquals(label, backend.icon(0).menu().get(0).label());
    }

    @Test
    void anInvalidPngFailsBeforeTheBackendIsTouched() {
        FakeTrayBackend backend = new FakeTrayBackend();
        byte[] signatureOnly = {
                (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3, 4
        };

        Result<TrayHandle> result = provider(backend, DIRECT)
                .open(new TrayIcon(signatureOnly, "tip", List.of()));

        assertEquals(Result.Status.FAILED, result.status());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertEquals("awt.systemtray", result.provider());
        assertTrue(result.detail().contains("PNG"), result.detail());
        assertTrue(backend.icons().isEmpty());
    }

    @Test
    void unavailableCapabilitiesReturnUnavailableWithoutDecodingOrAdding() {
        FakeTrayBackend headless = new FakeTrayBackend().headless(true);
        FakeTrayBackend unsupported = new FakeTrayBackend().supported(false);
        TrayIcon icon = new TrayIcon(PNGS, "tip", List.of());

        Result<TrayHandle> first = provider(headless, DIRECT).open(icon);
        Result<TrayHandle> second = provider(unsupported, DIRECT).open(icon);

        assertEquals(Result.Status.UNAVAILABLE, first.status());
        assertEquals(Reason.NO_GRAPHICAL_SESSION, first.reason());
        assertEquals("awt.systemtray", first.provider());
        assertTrue(first.detail().contains("Run the application"));
        assertEquals(Result.Status.UNAVAILABLE, second.status());
        assertEquals(Reason.BACKEND_MISSING, second.reason());
        assertTrue(second.detail().contains("system tray"));
        assertTrue(headless.icons().isEmpty());
        assertTrue(unsupported.icons().isEmpty());
    }

    @Test
    void aBackendAddFailureBecomesFailedBackendError() {
        FakeTrayBackend backend = new FakeTrayBackend()
                .addFailure(new IllegalStateException("tray exploded"));

        Result<TrayHandle> result = provider(backend, DIRECT)
                .open(new TrayIcon(PNGS, "tip", List.of()));

        assertEquals(Result.Status.FAILED, result.status());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertEquals("awt.systemtray", result.provider());
        assertNull(result.value());
        assertTrue(result.detail().contains("tray exploded"));
    }

    @Test
    void rejectsANullIconWithoutTouchingTheBackend() {
        FakeTrayBackend backend = new FakeTrayBackend();

        assertThrows(NullPointerException.class, () -> provider(backend, DIRECT).open(null));
        assertTrue(backend.icons().isEmpty());
    }

    @Test
    void aThrowingCallbackIsContainedAndTheTrayKeepsWorking() {
        FakeTrayBackend backend = new FakeTrayBackend();
        AtomicInteger goodCalls = new AtomicInteger();
        TrayIcon icon = new TrayIcon(PNGS, "tip", List.of(
                TrayMenuItem.item("Bad", () -> {
                    throw new IllegalStateException("callback boom");
                }),
                TrayMenuItem.item("Good", goodCalls::incrementAndGet)));

        Result<TrayHandle> result = provider(backend, DIRECT).open(icon);

        assertTrue(result.isConfirmed(), result::detail);
        backend.click(0, 0);
        backend.click(0, 1);
        assertEquals(1, goodCalls.get());
        assertTrue(result.value().isOpen());
    }

    @Test
    void closeRemovesTheIconAndRejectsLaterCallbacks() {
        FakeTrayBackend backend = new FakeTrayBackend();
        AtomicInteger calls = new AtomicInteger();
        TrayIcon icon = new TrayIcon(PNGS, "tip",
                List.of(TrayMenuItem.item("Quit", calls::incrementAndGet)));
        Result<TrayHandle> result = provider(backend, DIRECT).open(icon);
        TrayHandle handle = result.value();

        handle.close();

        assertFalse(handle.isOpen());
        assertEquals(1, backend.icon(0).handle().removals());
        backend.click(0, 0);
        assertEquals(0, calls.get());

        handle.close();
        assertEquals(1, backend.icon(0).handle().removals(),
                "double close must not remove the icon twice");
    }

    @Test
    void aQueuedCallbackDoesNotRunAfterClose() {
        QueuedExecutor queued = new QueuedExecutor();
        FakeTrayBackend backend = new FakeTrayBackend();
        AtomicInteger calls = new AtomicInteger();
        TrayIcon icon = new TrayIcon(PNGS, "tip",
                List.of(TrayMenuItem.item("Quit", calls::incrementAndGet)));
        TrayHandle handle = provider(backend, queued).open(icon).value();

        backend.click(0, 0);
        assertEquals(1, queued.pending());

        handle.close();
        queued.runAll();

        assertEquals(0, calls.get(), "a callback queued before close must not run afterwards");
    }

    @Test
    void twoHandlesAreIndependentAndCloseIsPerHandle() {
        FakeTrayBackend backend = new FakeTrayBackend();
        AwtSystemTrayProvider provider = provider(backend, DIRECT);
        Result<TrayHandle> firstResult = provider.open(new TrayIcon(PNGS, "tip", List.of()));
        Result<TrayHandle> secondResult = provider.open(new TrayIcon(PNGS, "tip", List.of()));
        TrayHandle first = firstResult.value();
        TrayHandle second = secondResult.value();

        first.close();

        assertFalse(first.isOpen());
        assertTrue(second.isOpen());
        assertEquals(2, backend.icons().size());
        assertEquals(1, backend.icon(0).handle().removals());
        assertEquals(0, backend.icon(1).handle().removals());
    }

    @Test
    void openAfterCloseReturnsANewIndependentHandle() {
        FakeTrayBackend backend = new FakeTrayBackend();
        AwtSystemTrayProvider provider = provider(backend, DIRECT);
        TrayHandle first = provider.open(new TrayIcon(PNGS, "tip", List.of())).value();

        first.close();
        TrayHandle second = provider.open(new TrayIcon(PNGS, "tip", List.of())).value();

        assertNotSame(first, second);
        assertFalse(first.isOpen());
        assertTrue(second.isOpen());
        assertEquals(2, backend.icons().size());
    }

    @Test
    void aRemoveFailureDoesNotEscapeClose() {
        FakeTrayBackend backend = new FakeTrayBackend()
                .removeFailure(new IllegalStateException("remove boom"));
        TrayHandle handle = provider(backend, DIRECT)
                .open(new TrayIcon(PNGS, "tip", List.of())).value();

        handle.close();

        assertFalse(handle.isOpen());
        assertEquals(1, backend.icon(0).handle().removals());
    }

    private static AwtSystemTrayProvider provider(FakeTrayBackend backend, Executor callbacks) {
        return new AwtSystemTrayProvider(backend, callbacks);
    }

    private static byte[] png(int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Executor that holds submitted tasks until the test runs them, so a close can be interleaved.
     */
    private static final class QueuedExecutor implements Executor {

        private final List<Runnable> tasks = new ArrayList<>();

        @Override
        public synchronized void execute(Runnable command) {
            tasks.add(command);
        }

        private synchronized int pending() {
            return tasks.size();
        }

        private synchronized void runAll() {
            List<Runnable> copy = List.copyOf(tasks);
            tasks.clear();
            copy.forEach(Runnable::run);
        }
    }
}
