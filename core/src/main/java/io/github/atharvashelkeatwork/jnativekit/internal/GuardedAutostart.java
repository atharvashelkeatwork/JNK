package io.github.atharvashelkeatwork.jnativekit.internal;

import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.Autostart;
import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.spi.AutostartProvider;

/**
 * Wraps the selected autostart provider so an unexpected failure becomes a
 * {@code FAILED(BACKEND_ERROR)} result instead of escaping to the caller.
 *
 * <p>Caller input is validated before the provider is called, so invalid arguments still throw as
 * the public contract promises.
 */
public final class GuardedAutostart extends GuardedFeature<AutostartProvider>
        implements Autostart {

    /**
     * Creates the guard.
     *
     * @param provider the selected provider; never {@code null}
     */
    public GuardedAutostart(AutostartProvider provider) {
        super(provider, provider::id);
    }

    @Override
    public Result<Void> enable(AutostartEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return call("enable", () -> provider.enable(entry));
    }

    @Override
    public Result<Void> disable(String appId) {
        Validation.requireAppId(appId);
        return call("disable", () -> provider.disable(appId));
    }

    @Override
    public Result<Boolean> isEnabled(String appId) {
        Validation.requireAppId(appId);
        return requireValue("isEnabled", call("isEnabled", () -> provider.isEnabled(appId)));
    }
}
