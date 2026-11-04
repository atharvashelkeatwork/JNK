package io.github.atharvashelkeatwork.jnativekit;

import java.util.Objects;

/**
 * The outcome of a feature check or feature action.
 *
 * <p>One of four statuses is set:
 * <ul>
 *   <li>{@link Status#CONFIRMED}: the action was performed and its postcondition, as documented
 *       for the feature, was verified.</li>
 *   <li>{@link Status#DISPATCHED_UNVERIFIED}: the action was accepted; the final user-visible
 *       effect could not be verified.</li>
 *   <li>{@link Status#UNAVAILABLE}: the action cannot be performed here; nothing was attempted.</li>
 *   <li>{@link Status#FAILED}: the action was attempted and failed; state may be partly changed.</li>
 * </ul>
 *
 * <p>Deliberately there is no {@code isSuccess()}. Callers must choose how to treat
 * {@code CONFIRMED} versus {@code DISPATCHED_UNVERIFIED}.
 *
 * <p>Invariants enforced by the constructor:
 * <ul>
 *   <li>{@code status}, {@code reason} and {@code detail} must not be {@code null}; {@code detail}
 *       may be empty.</li>
 *   <li>{@code CONFIRMED} and {@code DISPATCHED_UNVERIFIED} require {@code reason} equal to
 *       {@link Reason#NONE} and a non-null {@code provider}.</li>
 *   <li>{@code UNAVAILABLE} requires a reason other than {@link Reason#NONE} and a {@code null}
 *       value; the {@code provider} may be {@code null} when no provider was chosen.</li>
 *   <li>{@code FAILED} requires a reason other than {@link Reason#NONE} and a {@code null} value,
 *       and a non-null {@code provider}.</li>
 * </ul>
 *
 * @param <T>    the value produced by a confirmed action; {@code null} for plain actions
 * @param status the outcome status
 * @param value  the produced value; {@code null} unless the status allows a value
 * @param provider the name of the implementation used, for example
 *        {@code linux.notify-send}; {@code null} when no provider was chosen
 * @param reason why the outcome is not a success or why it failed; never {@code NONE} for
 *        {@code UNAVAILABLE} or {@code FAILED}
 * @param detail human-readable text about the outcome; never {@code null}; never parsed by code
 */
public record Result<T>(Status status, T value, String provider, Reason reason, String detail) {

    /**
     * Outcome of a feature check or feature action.
     */
    public enum Status {

        /** The action was performed and its postcondition was verified. */
        CONFIRMED,

        /** The action was dispatched and accepted; final visibility is not verifiable. */
        DISPATCHED_UNVERIFIED,

        /** The action cannot be performed in this environment; nothing was attempted. */
        UNAVAILABLE,

        /** The action was attempted and failed; state may be partly changed. */
        FAILED
    }

    /**
     * Creates a result and enforces the invariants.
     *
     * @param status the outcome status
     * @param value  the produced value; {@code null} unless the status allows a value
     * @param provider the name of the implementation used, for example
     *        {@code linux.notify-send}; {@code null} when no provider was chosen
     * @param reason why the outcome is not a success or why it failed; never {@code NONE} for
     *        {@code UNAVAILABLE} or {@code FAILED}
     * @param detail human-readable text about the outcome; never {@code null}; never parsed by code
     * @throws NullPointerException     if {@code status}, {@code reason}, {@code detail} or a
     *                                  {@code provider} required by the status is null
     * @throws IllegalArgumentException if the status and reason combination violates the invariants,
     *                                  or if a value was supplied for {@code UNAVAILABLE} or
     *                                  {@code FAILED}
     */
    public Result {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(detail, "detail");
        switch (status) {
            case CONFIRMED, DISPATCHED_UNVERIFIED -> {
                if (reason != Reason.NONE) {
                    throw new IllegalArgumentException("CONFIRMED and DISPATCHED_UNVERIFIED require reason NONE");
                }
                Objects.requireNonNull(provider, "provider");
            }
            case UNAVAILABLE -> {
                if (reason == Reason.NONE) {
                    throw new IllegalArgumentException("UNAVAILABLE requires a reason other than NONE");
                }
                if (value != null) {
                    throw new IllegalArgumentException("UNAVAILABLE must not carry a value");
                }
            }
            case FAILED -> {
                if (reason == Reason.NONE) {
                    throw new IllegalArgumentException("FAILED requires a reason other than NONE");
                }
                if (value != null) {
                    throw new IllegalArgumentException("FAILED must not carry a value");
                }
                Objects.requireNonNull(provider, "provider");
            }
        }
    }

    /**
     * Returns whether this result is {@code CONFIRMED}.
     *
     * @return {@code true} if this result has status {@code CONFIRMED}
     */
    public boolean isConfirmed() {
        return status == Status.CONFIRMED;
    }

    /**
     * Returns whether this result is {@code DISPATCHED_UNVERIFIED}.
     *
     * @return {@code true} if this result has status {@code DISPATCHED_UNVERIFIED}
     */
    public boolean isDispatchedUnverified() {
        return status == Status.DISPATCHED_UNVERIFIED;
    }

    /**
     * Returns whether this result is {@code UNAVAILABLE}.
     *
     * @return {@code true} if this result has status {@code UNAVAILABLE}
     */
    public boolean isUnavailable() {
        return status == Status.UNAVAILABLE;
    }

    /**
     * Returns whether this result is {@code FAILED}.
     *
     * @return {@code true} if this result has status {@code FAILED}
     */
    public boolean isFailed() {
        return status == Status.FAILED;
    }
}
