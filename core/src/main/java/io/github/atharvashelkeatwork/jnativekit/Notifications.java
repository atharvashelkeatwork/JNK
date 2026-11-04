package io.github.atharvashelkeatwork.jnativekit;

import io.github.atharvashelkeatwork.jnativekit.internal.Validation;

/**
 * Sends desktop notifications through the provider selected for the current platform.
 *
 * <p>In v0.1 a successful send is always {@link Result.Status#DISPATCHED_UNVERIFIED}: the OS or the
 * notification tool accepted the request, but final user-visible behaviour cannot be verified.
 */
public interface Notifications extends Feature {

    /**
     * Shows a notification.
     *
     * @param notification the notification to show; must be validated, but a null argument is
     *        rejected here as well
     * @return {@code DISPATCHED_UNVERIFIED} when the backend accepted the send, {@code
     *         UNAVAILABLE} with a reason when the environment cannot deliver it, or {@code FAILED}
     *         when the backend failed
     * @throws NullPointerException     if {@code notification} is null
     * @throws IllegalArgumentException if the notification violates a validation rule; a
     *                                  {@code Notification} is normally already validated, so this is
     *                                  defensive
     */
    Result<Void> show(Notification notification);
}
