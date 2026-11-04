package io.github.atharvashelkeatwork.jnativekit.providers.linux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.CommandResult;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.CommandRunner;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.ExecutableFinder;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.NotificationCommandResult;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.CapabilityDetails;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.PathExecutableFinder;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.ProcessCommandRunner;
import io.github.atharvashelkeatwork.jnativekit.spi.NotificationsProvider;

/**
 * Linux notifications through {@code notify-send} from libnotify.
 *
 * <p>The provider passes the executable, a {@code --} option terminator, the title and the body as
 * separate argument-list entries. No shell is involved and the title and body are never parsed as
 * options or concatenated into a command string.
 *
 * <p>Capability is re-evaluated on every call: it requires a graphical session ({@code DISPLAY} or
 * {@code WAYLAND_DISPLAY} set) and {@code notify-send} on {@code PATH}. A successful invocation is
 * reported as {@link Result.Status#DISPATCHED_UNVERIFIED}; visibility depends on the desktop and
 * its notification settings.
 */
public final class LinuxNotifySendNotificationsProvider implements NotificationsProvider {

    private static final String ID = "linux.notify-send";
    private static final String TOOL = "notify-send";
    private static final int PRIORITY = 100;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final CommandRunner runner;
    private final ExecutableFinder executables;
    private final Map<String, String> environment;
    private final Duration timeout;

    /**
     * Creates the provider with the live process environment and the real command runner.
     */
    public LinuxNotifySendNotificationsProvider() {
        this(new ProcessCommandRunner(), new PathExecutableFinder(), System.getenv());
    }

    LinuxNotifySendNotificationsProvider(
            CommandRunner runner,
            ExecutableFinder executables,
            Map<String, String> environment) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.executables = Objects.requireNonNull(executables, "executables");
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        this.timeout = DEFAULT_TIMEOUT;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.LINUX;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Capability capability() {
        if (!hasGraphicalSession()) {
            return new Capability(
                    Capability.Status.UNSUPPORTED_ENVIRONMENT,
                    Reason.NO_GRAPHICAL_SESSION,
                    "No DISPLAY or WAYLAND_DISPLAY environment variable is set",
                    "Run the application inside a graphical desktop session");
        }
        if (executables.find(TOOL).isEmpty()) {
            return new Capability(
                    Capability.Status.MISSING_DEPENDENCY,
                    Reason.BACKEND_MISSING,
                    "notify-send was not found on PATH",
                    "Install libnotify-bin or the package that provides notify-send");
        }
        return new Capability(
                Capability.Status.AVAILABLE,
                Reason.NONE,
                "notify-send is available in a graphical session",
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

        CommandResult result = runner.run(
                List.of(TOOL, "--", notification.title(), notification.body()),
                timeout);
        return NotificationCommandResult.map(ID, TOOL, result);
    }

    private boolean hasGraphicalSession() {
        return isSet(environment.get("DISPLAY")) || isSet(environment.get("WAYLAND_DISPLAY"));
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
