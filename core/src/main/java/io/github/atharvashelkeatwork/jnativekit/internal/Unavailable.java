package io.github.atharvashelkeatwork.jnativekit.internal;

import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.Autostart;
import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Notifications;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.Tray;
import io.github.atharvashelkeatwork.jnativekit.TrayHandle;
import io.github.atharvashelkeatwork.jnativekit.TrayIcon;

/**
 * The fallback facades used when no provider supports the current platform.
 *
 * <p>Their capability is {@code UNSUPPORTED_OS} with {@link Reason#UNSUPPORTED_PLATFORM}, and
 * every action returns {@code UNAVAILABLE} without attempting anything. Caller input is still
 * validated, so the usual null and format rules hold.
 */
public final class Unavailable {

    private static final String PROVIDERS_REMEDY =
            "Add the jnativekit-providers artifact to the runtime classpath and make sure it "
                    + "supports this platform";
    private static final String OTHER_PLATFORM_REMEDY =
            "jnativekit supports Linux, Windows and macOS only";

    private Unavailable() {
    }

    /**
     * Creates the fallback notifications facade.
     *
     * @param platform the platform that no provider supports
     * @return the fallback facade
     */
    public static Notifications notifications(Platform platform) {
        return new UnavailableNotifications(platform);
    }

    /**
     * Creates the fallback autostart facade.
     *
     * @param platform the platform that no provider supports
     * @return the fallback facade
     */
    public static Autostart autostart(Platform platform) {
        return new UnavailableAutostart(platform);
    }

    /**
     * Creates the fallback tray facade.
     *
     * @param platform the platform that no provider supports
     * @return the fallback facade
     */
    public static Tray tray(Platform platform) {
        return new UnavailableTray(platform);
    }

    private static Capability capability(String feature, Platform platform) {
        return new Capability(
                Capability.Status.UNSUPPORTED_OS,
                Reason.UNSUPPORTED_PLATFORM,
                "No " + feature + " provider supports " + platform,
                remedy(platform));
    }

    private static <T> Result<T> unavailable(String feature, Platform platform) {
        return new Result<>(
                Result.Status.UNAVAILABLE,
                null,
                null,
                Reason.UNSUPPORTED_PLATFORM,
                "No " + feature + " provider supports " + platform);
    }

    private static String remedy(Platform platform) {
        return switch (platform) {
            case LINUX, WINDOWS, MACOS -> PROVIDERS_REMEDY;
            case OTHER -> OTHER_PLATFORM_REMEDY;
        };
    }

    private static final class UnavailableNotifications implements Notifications {

        private final Platform platform;

        private UnavailableNotifications(Platform platform) {
            this.platform = Objects.requireNonNull(platform, "platform");
        }

        @Override
        public Capability capability() {
            return Unavailable.capability("notifications", platform);
        }

        @Override
        public Result<Void> show(Notification notification) {
            Objects.requireNonNull(notification, "notification");
            return Unavailable.unavailable("notifications", platform);
        }
    }

    private static final class UnavailableAutostart implements Autostart {

        private final Platform platform;

        private UnavailableAutostart(Platform platform) {
            this.platform = Objects.requireNonNull(platform, "platform");
        }

        @Override
        public Capability capability() {
            return Unavailable.capability("autostart", platform);
        }

        @Override
        public Result<Void> enable(AutostartEntry entry) {
            Objects.requireNonNull(entry, "entry");
            return Unavailable.unavailable("autostart", platform);
        }

        @Override
        public Result<Void> disable(String appId) {
            Validation.requireAppId(appId);
            return Unavailable.unavailable("autostart", platform);
        }

        @Override
        public Result<Boolean> isEnabled(String appId) {
            Validation.requireAppId(appId);
            return Unavailable.unavailable("autostart", platform);
        }
    }

    private static final class UnavailableTray implements Tray {

        private final Platform platform;

        private UnavailableTray(Platform platform) {
            this.platform = Objects.requireNonNull(platform, "platform");
        }

        @Override
        public Capability capability() {
            return Unavailable.capability("tray", platform);
        }

        @Override
        public Result<TrayHandle> open(TrayIcon icon) {
            Objects.requireNonNull(icon, "icon");
            return Unavailable.unavailable("tray", platform);
        }
    }
}
