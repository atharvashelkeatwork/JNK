package io.github.atharvashelkeatwork.jnativekit.internal;

import java.util.function.Supplier;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;

/**
 * Builds the fallback values used when a provider breaks its contract.
 *
 * <p>Package-private implementation detail. Every fallback is logged at {@code WARNING} with the
 * original stack trace by the caller, except for a provider that returns a blank identifier, which
 * is logged without a stack trace because no exception occurred.
 */
final class ProviderFailure {

    private static final System.Logger LOG = System.getLogger(ProviderFailure.class.getName());

    private ProviderFailure() {
    }

    /**
     * Returns a stable identifier for a provider even when its {@code id()} method itself throws.
     *
     * <p>The failure is logged at {@code WARNING}; a provider with a broken {@code id()} must not
     * disappear silently.
     *
     * @param provider   the provider; never {@code null}
     * @param idSupplier the provider's id supplier
     * @return the provider id, or the provider class name when the id cannot be read
     */
    static String id(Object provider, Supplier<String> idSupplier) {
        String className = provider.getClass().getName();
        try {
            String id = idSupplier.get();
            if (id != null && !id.isBlank()) {
                return id;
            }
            LOG.log(System.Logger.Level.WARNING,
                    "Provider " + className + " returned a null or blank id(); using the class name");
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Provider " + className + " threw from id(); using the class name",
                    e);
        } catch (Error e) {
            if (Errors.isFatal(e)) {
                throw e;
            }
            LOG.log(System.Logger.Level.WARNING,
                    "Provider " + className + " threw from id(); using the class name",
                    e);
        }
        return className;
    }

    /**
     * Builds a capability for a provider whose {@code capability()} threw.
     *
     * @param id      the stable provider identifier
     * @param failure the unexpected failure
     * @return a non-available capability that names the provider and the failure
     */
    static Capability capability(String id, Throwable failure) {
        return new Capability(
                Capability.Status.UNSUPPORTED_ENVIRONMENT,
                Reason.BACKEND_ERROR,
                "Provider " + id + " failed during capability(): " + Errors.describe(failure),
                null);
    }

    /**
     * Builds a capability for a provider that returned {@code null} from {@code capability()}.
     *
     * @param id the stable provider identifier
     * @return a non-available capability that names the provider
     */
    static Capability nullCapability(String id) {
        return new Capability(
                Capability.Status.UNSUPPORTED_ENVIRONMENT,
                Reason.BACKEND_ERROR,
                "Provider " + id + " returned null from capability()",
                null);
    }

    /**
     * Builds a {@code FAILED(BACKEND_ERROR)} result for a broken provider action.
     *
     * @param id      the stable provider identifier
     * @param action  the public action name, for example {@code show}
     * @param failure the unexpected failure
     * @param <T>     the result type
     * @return the failed result
     */
    static <T> Result<T> result(String id, String action, Throwable failure) {
        return new Result<>(
                Result.Status.FAILED,
                null,
                id,
                Reason.BACKEND_ERROR,
                "Provider " + id + " failed during " + action + "(): " + Errors.describe(failure));
    }

    /**
     * Builds a {@code FAILED(BACKEND_ERROR)} result for a provider action that returned
     * {@code null}.
     *
     * @param id     the stable provider identifier
     * @param action the public action name
     * @param <T>    the result type
     * @return the failed result
     */
    static <T> Result<T> nullResult(String id, String action) {
        return new Result<>(
                Result.Status.FAILED,
                null,
                id,
                Reason.BACKEND_ERROR,
                "Provider " + id + " returned null from " + action + "()");
    }

    /**
     * Builds a {@code FAILED(BACKEND_ERROR)} result for a successful status that carried no value.
     *
     * <p>{@link Result} documents that a value is present whenever the status is
     * {@code CONFIRMED} or {@code DISPATCHED_UNVERIFIED} and the type is not {@code Void}; the
     * constructor cannot enforce that for a nullable generic type, so the guard does.
     *
     * @param id     the stable provider identifier
     * @param action the public action name
     * @param <T>    the result type
     * @return the failed result
     */
    static <T> Result<T> missingValue(String id, String action) {
        return new Result<>(
                Result.Status.FAILED,
                null,
                id,
                Reason.BACKEND_ERROR,
                "Provider " + id + " returned a successful status with a null value from "
                        + action + "()");
    }
}
