package io.github.atharvashelkeatwork.jnativekit.internal;

import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Notifications;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.spi.NotificationsProvider;

/**
 * Wraps the selected notifications provider so an unexpected failure becomes a
 * {@code FAILED(BACKEND_ERROR)} result instead of escaping to the caller.
 */
public final class GuardedNotifications extends GuardedFeature<NotificationsProvider>
        implements Notifications {

    /**
     * Creates the guard.
     *
     * @param provider the selected provider; never {@code null}
     */
    public GuardedNotifications(NotificationsProvider provider) {
        super(provider, provider::id);
    }

    @Override
    public Result<Void> show(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        return call("show", () -> provider.show(notification));
    }
}
