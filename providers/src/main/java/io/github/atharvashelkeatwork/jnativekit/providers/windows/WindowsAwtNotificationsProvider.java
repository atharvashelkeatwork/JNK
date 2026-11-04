package io.github.atharvashelkeatwork.jnativekit.providers.windows;

import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.providers.awt.BalloonBackend;
import io.github.atharvashelkeatwork.jnativekit.providers.awt.SystemTrayBalloonBackend;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.CapabilityDetails;
import io.github.atharvashelkeatwork.jnativekit.spi.NotificationsProvider;

/**
 * Windows notifications through an AWT tray balloon.
 *
 * <p>The provider uses {@link io.github.atharvashelkeatwork.jnativekit.providers.awt.BalloonBackend}
 * so the AWT code is shared and remains testable without a display. A successful dispatch is
 * {@link Result.Status#DISPATCHED_UNVERIFIED}: AWT accepted the balloon, but Windows Focus Assist
 * and per-app notification settings can still suppress it.
 *
 * <p>This is the v0.1 baseline provider. Real Windows toast notifications require an application
 * identity and are not a silent upgrade of this provider.
 */
public final class WindowsAwtNotificationsProvider implements NotificationsProvider {

    private static final String ID = "windows.awt-balloon";
    private static final int PRIORITY = 100;

    private static final System.Logger LOG =
            System.getLogger(WindowsAwtNotificationsProvider.class.getName());

    private final BalloonBackend backend;

    /**
     * Creates the provider with the real AWT tray backend.
     */
    public WindowsAwtNotificationsProvider() {
        this(new SystemTrayBalloonBackend());
    }

    WindowsAwtNotificationsProvider(BalloonBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.WINDOWS;
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
                    "AWT is headless; Windows tray balloons need a graphical session",
                    "Run the application in a normal desktop session");
        }
        if (!backend.isSupported()) {
            return new Capability(
                    Capability.Status.UNSUPPORTED_ENVIRONMENT,
                    Reason.BACKEND_MISSING,
                    "The AWT system tray is not supported in this environment",
                    "Run in a desktop session that provides a system tray");
        }
        return new Capability(
                Capability.Status.AVAILABLE,
                Reason.NONE,
                "AWT tray balloons are available",
                null);
    }

    @Override
    public Result<Void> show(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        Capability capability = capability();
        if (capability.status() != Capability.Status.AVAILABLE) {
            return new Result<>(
                    Result.Status.UNAVAILABLE,
                    null,
                    ID,
                    capability.reason(),
                    CapabilityDetails.unavailableDetail(capability));
        }

        try {
            backend.display(notification.title(), notification.body());
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "AWT tray balloon notification failed", e);
            return new Result<>(
                    Result.Status.FAILED,
                    null,
                    ID,
                    Reason.BACKEND_ERROR,
                    "AWT tray balloon failed: " + e.getClass().getSimpleName()
                            + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
        return new Result<>(
                Result.Status.DISPATCHED_UNVERIFIED,
                null,
                ID,
                Reason.NONE,
                "AWT accepted the tray balloon; visibility depends on Windows notification settings");
    }
}
