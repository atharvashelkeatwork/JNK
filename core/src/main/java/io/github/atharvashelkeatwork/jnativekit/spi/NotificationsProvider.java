package io.github.atharvashelkeatwork.jnativekit.spi;

import io.github.atharvashelkeatwork.jnativekit.Notifications;
import io.github.atharvashelkeatwork.jnativekit.Platform;

/**
 * Provider SPI for {@link Notifications}.
 *
 * <p>Implementations are registered in
 * {@code META-INF/services/io.github.atharvashelkeatwork.jnativekit.spi.NotificationsProvider}
 * and are selected by the core resolver.
 */
public interface NotificationsProvider extends Notifications {

    /**
     * Stable identifier of this implementation, for example {@code linux.notify-send}.
     *
     * @return the implementation identifier; never {@code null}
     */
    String id();

    /**
     * Returns whether this provider supports the given platform. The check must be cheap and must
     * not have side effects.
     *
     * @param platform the platform to test; never {@code null}
     * @return {@code true} when this provider can run on the platform
     */
    boolean supports(Platform platform);

    /**
     * Selection priority. When several providers support the same platform, the highest priority
     * wins. Ties are broken alphabetically by {@link #id()}.
     *
     * @return the selection priority; higher wins
     */
    int priority();
}
