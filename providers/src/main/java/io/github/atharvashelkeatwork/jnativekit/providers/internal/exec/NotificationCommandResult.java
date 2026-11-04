package io.github.atharvashelkeatwork.jnativekit.providers.internal.exec;

import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;

/**
 * Maps an external notification tool's command result to the public result model.
 *
 * <p>Internal to the {@code providers} module. The mapping is shared by the Linux and macOS
 * notification providers so both report timeouts, launch failures and non-zero exits identically.
 *
 * <p>A zero exit code is only ever {@link Result.Status#DISPATCHED_UNVERIFIED}: the tool accepted
 * the request, but no process exit code can prove that the user saw a notification.
 */
public final class NotificationCommandResult {

    private static final int MAX_DETAIL_CHARS = 512;

    private NotificationCommandResult() {
    }

    /**
     * Maps a notification tool invocation to a result.
     *
     * @param providerId the provider identifier reported to the caller; never {@code null}
     * @param toolName   the human-readable tool name, for example {@code notify-send}; never
     *                   {@code null}
     * @param result     the captured command outcome; never {@code null}
     * @return the mapped result; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public static Result<Void> map(String providerId, String toolName, CommandResult result) {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(toolName, "toolName");
        Objects.requireNonNull(result, "result");

        if (result.timedOut()) {
            return failed(providerId, Reason.TIMEOUT,
                    toolName + " timed out and was destroyed before it accepted the notification");
        }
        if (result.isLaunchFailure()) {
            return failed(providerId, Reason.BACKEND_ERROR,
                    toolName + " could not be started" + stderrSuffix(result.stderr()));
        }
        if (result.exitCode() == 0) {
            return new Result<>(
                    Result.Status.DISPATCHED_UNVERIFIED,
                    null,
                    providerId,
                    Reason.NONE,
                    toolName + " accepted the notification; visibility depends on the desktop");
        }
        return failed(providerId, Reason.BACKEND_ERROR,
                toolName + " exited with code " + result.exitCode() + stderrSuffix(result.stderr()));
    }

    private static Result<Void> failed(String providerId, Reason reason, String detail) {
        return new Result<>(Result.Status.FAILED, null, providerId, reason, detail);
    }

    private static String stderrSuffix(String stderr) {
        String singleLine = stderr.replace('\r', ' ').replace('\n', ' ').trim();
        if (singleLine.isEmpty()) {
            return "";
        }
        String bounded = singleLine.length() <= MAX_DETAIL_CHARS
                ? singleLine
                : singleLine.substring(0, MAX_DETAIL_CHARS) + "...";
        return ": " + bounded;
    }
}
