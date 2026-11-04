package io.github.atharvashelkeatwork.jnativekit.internal;

import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.Tray;
import io.github.atharvashelkeatwork.jnativekit.TrayHandle;
import io.github.atharvashelkeatwork.jnativekit.TrayIcon;
import io.github.atharvashelkeatwork.jnativekit.spi.TrayProvider;

/**
 * Wraps the selected tray provider so an unexpected failure becomes a
 * {@code FAILED(BACKEND_ERROR)} result instead of escaping to the caller.
 *
 * <p>{@link #open(TrayIcon)} also enforces the documented rule that opening a tray when
 * {@link #capability()} is not {@code AVAILABLE} returns {@code UNAVAILABLE} with the capability's
 * reason, and wraps the returned handle so its methods never throw.
 */
public final class GuardedTray extends GuardedFeature<TrayProvider> implements Tray {

    /**
     * Creates the guard.
     *
     * @param provider the selected provider; never {@code null}
     */
    public GuardedTray(TrayProvider provider) {
        super(provider, provider::id);
    }

    @Override
    public Result<TrayHandle> open(TrayIcon icon) {
        Objects.requireNonNull(icon, "icon");

        Capability current;
        try {
            current = provider.capability();
        } catch (RuntimeException e) {
            return failedResult("open", e);
        } catch (Error e) {
            if (Errors.isFatal(e)) {
                throw e;
            }
            return failedResult("open", e);
        }

        if (current == null) {
            current = ProviderFailure.nullCapability(id);
        }
        if (current.status() != Capability.Status.AVAILABLE) {
            return new Result<>(
                    Result.Status.UNAVAILABLE,
                    null,
                    id,
                    current.reason(),
                    current.detail());
        }

        Result<TrayHandle> result = call("open", () -> provider.open(icon));
        if (!result.isConfirmed() && !result.isDispatchedUnverified()) {
            return result;
        }
        if (result.value() == null) {
            return requireValue("open", result);
        }
        return new Result<>(
                result.status(),
                new GuardedTrayHandle(id, result.value()),
                result.provider(),
                result.reason(),
                result.detail());
    }
}
