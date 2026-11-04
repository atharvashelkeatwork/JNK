package io.github.atharvashelkeatwork.jnativekit.internal;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.spi.NotificationsProvider;

/**
 * A registered provider used to test the real {@code ServiceLoader} path.
 *
 * <p>Test helper only. It is listed in a test-scope service file so the provider is visible to the
 * application class loader and absent from a deliberately restricted context class loader.
 */
public final class CoreTestNotificationsProvider implements NotificationsProvider {

    @Override
    public String id() {
        return "core.test";
    }

    @Override
    public boolean supports(Platform platform) {
        return true;
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public Capability capability() {
        return new Capability(Capability.Status.AVAILABLE, Reason.NONE, "test provider", null);
    }

    @Override
    public Result<Void> show(Notification notification) {
        return new Result<>(
                Result.Status.DISPATCHED_UNVERIFIED,
                null,
                id(),
                Reason.NONE,
                "test dispatch");
    }
}
