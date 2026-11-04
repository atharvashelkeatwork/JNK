package io.github.atharvashelkeatwork.jnativekit.providers.awt;

import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.TrayHandle;
import io.github.atharvashelkeatwork.jnativekit.TrayIcon;
import io.github.atharvashelkeatwork.jnativekit.TrayMenuItem;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.CapabilityDetails;
import io.github.atharvashelkeatwork.jnativekit.spi.TrayProvider;

/**
 * System-tray provider for Linux, Windows and macOS based on AWT {@link java.awt.SystemTray}.
 *
 * <p>Capability is re-evaluated on every call: a headless JVM is
 * {@link Reason#NO_GRAPHICAL_SESSION} and an environment without a supported system tray is
 * {@link Reason#BACKEND_MISSING}. {@link #open(TrayIcon)} decodes the PNG with
 * {@link ImageIO}, hands icon and menu creation to the AWT event thread, and returns
 * {@link Result.Status#CONFIRMED} once AWT accepted the icon. {@code CONFIRMED} never means that
 * the icon is visible: several Linux desktops hide legacy tray icons.
 *
 * <p>Menu callbacks never run on the AWT event thread. They are serialized on the daemon thread
 * named {@code jnativekit-callbacks}; a throwing callback is logged and does not stop the tray.
 * Closing a handle removes the icon and rejects later callbacks.
 */
public final class AwtSystemTrayProvider implements TrayProvider {

    private static final String ID = "awt.systemtray";
    private static final int PRIORITY = 100;
    private static final int MAX_DETAIL_CHARS = 512;

    private static final System.Logger LOG =
            System.getLogger(AwtSystemTrayProvider.class.getName());

    private final TrayBackend backend;
    private final Executor callbacks;

    /**
     * Creates the provider with the real AWT backend and the shared callback executor.
     */
    public AwtSystemTrayProvider() {
        this(new AwtTrayBackend(), CallbackExecutor.shared());
    }

    AwtSystemTrayProvider(TrayBackend backend, Executor callbacks) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.callbacks = Objects.requireNonNull(callbacks, "callbacks");
    }

    @Override
    public String id() {
        return ID;
    }

    /**
     * Returns whether AWT can serve this platform.
     *
     * <p>This provider is the same code on every platform; the per-call
     * {@link #capability()} decides whether the environment actually has a usable system tray.
     *
     * @param platform the platform to test; never {@code null}
     * @return always {@code true}
     */
    @Override
    public boolean supports(Platform platform) {
        Objects.requireNonNull(platform, "platform");
        return true;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Capability capability() {
        if (backend.isHeadless()) {
            return new Capability(
                    Capability.Status.UNSUPPORTED_ENVIRONMENT,
                    Reason.NO_GRAPHICAL_SESSION,
                    "AWT is headless; a tray icon needs a graphical session",
                    "Run the application in a normal desktop session");
        }
        if (!backend.isSupported()) {
            return new Capability(
                    Capability.Status.UNSUPPORTED_ENVIRONMENT,
                    Reason.BACKEND_MISSING,
                    "The AWT system tray is not supported in this environment",
                    "Use a desktop session that provides a system tray; some Wayland sessions do not");
        }
        return new Capability(
                Capability.Status.AVAILABLE,
                Reason.NONE,
                "The AWT system tray is available",
                null);
    }

    @Override
    public Result<TrayHandle> open(TrayIcon icon) {
        Objects.requireNonNull(icon, "icon");
        Capability capability = capability();
        if (capability.status() != Capability.Status.AVAILABLE) {
            return new Result<>(
                    Result.Status.UNAVAILABLE,
                    null,
                    ID,
                    capability.reason(),
                    CapabilityDetails.unavailableDetail(capability));
        }

        Image image;
        try {
            image = decode(icon.png());
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not decode a tray icon PNG", e);
            return failed("The PNG icon could not be decoded: " + describe(e));
        }

        Handle handle = new Handle();
        List<TrayBackend.MenuEntry> entries = new ArrayList<>(icon.menu().size());
        for (TrayMenuItem item : icon.menu()) {
            if (item.action() == null) {
                entries.add(new TrayBackend.MenuEntry("", null));
            } else {
                Runnable action = item.action();
                entries.add(new TrayBackend.MenuEntry(
                        item.label(), () -> handle.dispatch(action)));
            }
        }

        try {
            handle.attach(backend.add(image, icon.tooltip(), entries));
        } catch (RuntimeException e) {
            handle.close();
            LOG.log(System.Logger.Level.WARNING, "The AWT system tray rejected the icon", e);
            return failed("The AWT system tray rejected the icon: " + describe(e));
        }

        return new Result<>(
                Result.Status.CONFIRMED,
                handle,
                ID,
                Reason.NONE,
                "AWT accepted the tray icon; visibility depends on the desktop");
    }

    private static Image decode(byte[] png) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(png)) {
            Image image = ImageIO.read(input);
            if (image == null) {
                throw new IllegalArgumentException(
                        "no image reader could decode the PNG data");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalStateException("the PNG data could not be read", e);
        }
    }

    private static Result<TrayHandle> failed(String detail) {
        return new Result<>(
                Result.Status.FAILED,
                null,
                ID,
                Reason.BACKEND_ERROR,
                bounded(detail));
    }

    private static String describe(Throwable failure) {
        String message = failure.getMessage();
        String simpleName = failure.getClass().getSimpleName();
        return message == null || message.isBlank()
                ? simpleName
                : simpleName + ": " + message;
    }

    private static String bounded(String detail) {
        return detail.length() <= MAX_DETAIL_CHARS
                ? detail
                : detail.substring(0, MAX_DETAIL_CHARS) + "...";
    }

    /**
     * Lifecycle of one open icon: open flag, backend handle and callback dispatch.
     */
    private final class Handle implements TrayHandle {

        private final AtomicBoolean open = new AtomicBoolean(true);
        private final AtomicReference<TrayBackend.IconHandle> icon = new AtomicReference<>();

        private void attach(TrayBackend.IconHandle added) {
            Objects.requireNonNull(added, "added");
            if (!open.get()) {
                removeQuietly(added);
                return;
            }
            TrayBackend.IconHandle previous = icon.getAndSet(added);
            if (previous != null) {
                removeQuietly(previous);
            }
        }

        private void dispatch(Runnable action) {
            if (!open.get()) {
                return;
            }
            try {
                callbacks.execute(() -> runCallback(action));
            } catch (RuntimeException e) {
                logCallbackFailure("Could not schedule a tray menu callback", e);
            } catch (Error e) {
                if (isFatal(e)) {
                    logCallbackFailure("Could not schedule a tray menu callback", e);
                    throw e;
                }
                logCallbackFailure("Could not schedule a tray menu callback", e);
            }
        }

        private void runCallback(Runnable action) {
            if (!open.get()) {
                return;
            }
            runGuarded(action, "A tray menu callback threw");
        }

        private void runGuarded(Runnable action, String description) {
            try {
                action.run();
            } catch (RuntimeException e) {
                logCallbackFailure(description, e);
            } catch (Error e) {
                if (isFatal(e)) {
                    logCallbackFailure(description, e);
                    throw e;
                }
                logCallbackFailure(description, e);
            }
        }

        @Override
        public boolean isOpen() {
            return open.get();
        }

        @Override
        public void close() {
            if (!open.compareAndSet(true, false)) {
                return;
            }
            removeQuietly(icon.getAndSet(null));
        }

        private void removeQuietly(TrayBackend.IconHandle handle) {
            if (handle == null) {
                return;
            }
            try {
                handle.remove();
            } catch (RuntimeException e) {
                logCallbackFailure("Could not remove an AWT tray icon", e);
            }
        }

        private void logCallbackFailure(String message, Throwable failure) {
            LOG.log(System.Logger.Level.WARNING, message, failure);
        }

        private boolean isFatal(Throwable failure) {
            return failure instanceof VirtualMachineError || failure instanceof ThreadDeath;
        }
    }
}
