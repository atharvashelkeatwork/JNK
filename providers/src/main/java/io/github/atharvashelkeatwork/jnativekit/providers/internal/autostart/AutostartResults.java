package io.github.atharvashelkeatwork.jnativekit.providers.internal.autostart;

import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;

/**
 * Builds the autostart results shared by the three providers.
 *
 * <p>Internal to the {@code providers} module. Keeping the construction here makes the status,
 * reason, provider and value invariants identical across the OS implementations.
 */
public final class AutostartResults {

    private static final int MAX_DETAIL_CHARS = 512;

    private AutostartResults() {
    }

    /**
     * Builds a {@code CONFIRMED} action result.
     *
     * @param provider the provider identifier; never {@code null}
     * @param detail   the human-readable detail; never {@code null}
     * @param <T>      the result value type
     * @return the result; never {@code null}
     * @throws NullPointerException if an argument is {@code null}
     */
    public static <T> Result<T> confirmed(String provider, String detail) {
        return new Result<>(
                Result.Status.CONFIRMED,
                null,
                Objects.requireNonNull(provider, "provider"),
                Reason.NONE,
                Objects.requireNonNull(detail, "detail"));
    }

    /**
     * Builds a {@code FAILED} action result.
     *
     * @param provider the provider identifier; never {@code null}
     * @param reason   the failure reason; must not be {@link Reason#NONE}
     * @param detail   the human-readable detail; never {@code null}
     * @param <T>      the result value type
     * @return the result; never {@code null}
     * @throws NullPointerException if an argument is {@code null}
     */
    public static <T> Result<T> failed(String provider, Reason reason, String detail) {
        return new Result<>(
                Result.Status.FAILED,
                null,
                Objects.requireNonNull(provider, "provider"),
                Objects.requireNonNull(reason, "reason"),
                Objects.requireNonNull(detail, "detail"));
    }

    /**
     * Builds a {@code CONFIRMED} query result.
     *
     * @param provider the provider identifier; never {@code null}
     * @param value    the found value
     * @param detail   the human-readable detail; never {@code null}
     * @return the result; never {@code null}
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Result<Boolean> found(String provider, boolean value, String detail) {
        return new Result<>(
                Result.Status.CONFIRMED,
                value,
                Objects.requireNonNull(provider, "provider"),
                Reason.NONE,
                Objects.requireNonNull(detail, "detail"));
    }

    /**
     * Builds an {@code UNAVAILABLE} result from a capability.
     *
     * <p>{@link Result} has no remedy field, so the capability's detail and remedy are joined for
     * humans. The text is never parsed by code.
     *
     * @param provider   the provider identifier; never {@code null}
     * @param capability the unavailable capability; never {@code null}
     * @param <T>        the result value type
     * @return the result; never {@code null}
     * @throws NullPointerException if an argument is {@code null}
     */
    public static <T> Result<T> unavailable(String provider, Capability capability) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(capability, "capability");
        String remedy = capability.remedy();
        String detail = remedy == null
                ? capability.detail()
                : capability.detail() + "; " + remedy;
        return new Result<>(
                Result.Status.UNAVAILABLE,
                null,
                provider,
                capability.reason(),
                detail);
    }

    /**
     * Turns command standard error into a short suffix for a detail string.
     *
     * @param stderr the captured standard error; never {@code null}
     * @return an empty string, or {@code ": "} followed by the first line, bounded in length
     * @throws NullPointerException if {@code stderr} is {@code null}
     */
    public static String stderrSuffix(String stderr) {
        Objects.requireNonNull(stderr, "stderr");
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
