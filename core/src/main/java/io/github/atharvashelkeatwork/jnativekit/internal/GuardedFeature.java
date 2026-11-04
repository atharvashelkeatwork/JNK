package io.github.atharvashelkeatwork.jnativekit.internal;

import java.util.Objects;
import java.util.function.Supplier;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Feature;
import io.github.atharvashelkeatwork.jnativekit.Result;

/**
 * Shared provider boundary used by the three guards.
 *
 * <p>Implementation detail. It validates caller input through its subclasses, resolves the provider
 * identifier once, and converts every non-fatal provider failure into a value:
 * <ul>
 *   <li>a throwing or null {@code capability()} becomes a non-available {@link Capability};</li>
 *   <li>a throwing or null action becomes {@code FAILED(BACKEND_ERROR)};</li>
 *   <li>a successful status without the documented value becomes {@code FAILED(BACKEND_ERROR)}.</li>
 * </ul>
 *
 * @param <P> the provider type
 */
abstract class GuardedFeature<P extends Feature> implements Feature {

    private static final System.Logger LOG = System.getLogger(GuardedFeature.class.getName());

    final P provider;
    final String id;

    GuardedFeature(P provider, Supplier<String> idSupplier) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.id = ProviderFailure.id(this.provider, idSupplier);
    }

    @Override
    public final Capability capability() {
        try {
            Capability capability = provider.capability();
            if (capability == null) {
                LOG.log(System.Logger.Level.WARNING,
                        "Provider " + id + " returned null from capability()");
                return ProviderFailure.nullCapability(id);
            }
            return capability;
        } catch (RuntimeException e) {
            return failedCapability(e);
        } catch (Error e) {
            if (Errors.isFatal(e)) {
                throw e;
            }
            return failedCapability(e);
        }
    }

    /**
     * Calls a provider action and converts every non-fatal failure into a result.
     *
     * @param action     the public action name, used in logs and details
     * @param actionCall the provider call
     * @param <T>        the result type
     * @return the provider result, or {@code FAILED(BACKEND_ERROR)}
     */
    final <T> Result<T> call(String action, Supplier<Result<T>> actionCall) {
        try {
            Result<T> result = actionCall.get();
            if (result == null) {
                LOG.log(System.Logger.Level.WARNING,
                        "Provider " + id + " returned null from " + action + "()");
                return ProviderFailure.nullResult(id, action);
            }
            return result;
        } catch (RuntimeException e) {
            return failedResult(action, e);
        } catch (Error e) {
            if (Errors.isFatal(e)) {
                throw e;
            }
            return failedResult(action, e);
        }
    }

    /**
     * Enforces the documented value invariant on a successful result.
     *
     * @param action the public action name
     * @param result the provider result
     * @param <T>    the result type
     * @return the result, or {@code FAILED(BACKEND_ERROR)} when its value is missing
     */
    final <T> Result<T> requireValue(String action, Result<T> result) {
        if (result.value() == null
                && (result.isConfirmed() || result.isDispatchedUnverified())) {
            LOG.log(System.Logger.Level.WARNING,
                    "Provider " + id + " returned " + result.status()
                            + " with a null value from " + action + "()");
            return ProviderFailure.missingValue(id, action);
        }
        return result;
    }

    /**
     * Converts a failure that happened before an action could be dispatched.
     *
     * @param action  the public action name
     * @param failure the unexpected failure
     * @param <T>     the result type
     * @return {@code FAILED(BACKEND_ERROR)}
     */
    final <T> Result<T> failedResult(String action, Throwable failure) {
        LOG.log(System.Logger.Level.WARNING,
                "Provider " + id + " threw during " + action + "(); converting to FAILED",
                failure);
        return ProviderFailure.result(id, action, failure);
    }

    private Capability failedCapability(Throwable failure) {
        LOG.log(System.Logger.Level.WARNING,
                "Provider " + id
                        + " threw during capability(); converting to a non-available capability",
                failure);
        return ProviderFailure.capability(id, failure);
    }
}
