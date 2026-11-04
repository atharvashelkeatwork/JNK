package io.github.atharvashelkeatwork.jnativekit.providers.macos;

import java.time.Duration;
import java.util.List;
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
 * macOS notifications through {@code osascript}.
 *
 * <p>The AppleScript is a fixed constant. The title and body are passed as separate argument-list
 * entries after the {@code --} option terminator and read by the script from {@code argv}. They are
 * never interpolated into script text, so quotes, backslashes and newlines cannot change the
 * script that runs.
 *
 * <p>Capability requires {@code osascript} on {@code PATH}. A successful invocation is reported as
 * {@link Result.Status#DISPATCHED_UNVERIFIED}; this provider has only unit-fake coverage in v0.1
 * and is not manually verified.
 */
public final class MacOsascriptNotificationsProvider implements NotificationsProvider {

    private static final String ID = "macos.osascript";
    private static final String TOOL = "osascript";
    private static final int PRIORITY = 100;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private static final String SCRIPT_START = "on run argv";
    private static final String SCRIPT_DISPLAY =
            "display notification ((item 2 of argv) as text) with title ((item 1 of argv) as text)";
    private static final String SCRIPT_END = "end run";

    private final CommandRunner runner;
    private final ExecutableFinder executables;
    private final Duration timeout;

    /**
     * Creates the provider with the live process environment and the real command runner.
     */
    public MacOsascriptNotificationsProvider() {
        this(new ProcessCommandRunner(), new PathExecutableFinder());
    }

    MacOsascriptNotificationsProvider(CommandRunner runner, ExecutableFinder executables) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.executables = Objects.requireNonNull(executables, "executables");
        this.timeout = DEFAULT_TIMEOUT;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.MACOS;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Capability capability() {
        if (executables.find(TOOL).isEmpty()) {
            return new Capability(
                    Capability.Status.MISSING_DEPENDENCY,
                    Reason.BACKEND_MISSING,
                    "osascript was not found on PATH",
                    "Restore the standard macOS osascript command");
        }
        return new Capability(
                Capability.Status.AVAILABLE,
                Reason.NONE,
                "osascript is available",
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
                List.of(
                        TOOL,
                        "-e", SCRIPT_START,
                        "-e", SCRIPT_DISPLAY,
                        "-e", SCRIPT_END,
                        "--",
                        notification.title(),
                        notification.body()),
                timeout);
        return NotificationCommandResult.map(ID, TOOL, result);
    }
}
