package io.github.atharvashelkeatwork.jnativekit;

import java.util.Objects;

/**
 * Best assessment of a single feature for the current environment.
 *
 * <p>A capability describes the environment, not the outcome of an action. A {@code status} of
 * {@code AVAILABLE} implies {@code reason} equal to {@link Reason#NONE}; every other status pairs
 * with a reason other than {@link Reason#NONE}.
 *
 * <p>The {@code detail} is human-readable text for humans and logs; it is never parsed by code.
 * The {@code remedy} is human-readable remediation advice; it must be {@code null} when the status
 * is {@code AVAILABLE}, or when no useful advice can be given.
 *
 * @param status      the best assessment right now
 * @param reason      why the feature is not {@code AVAILABLE}, or {@code NONE} when it is
 * @param detail      human text describing the assessment; may be empty; never parsed by code
 * @param remedy      human remediation advice, or {@code null}
 */
public record Capability(Status status, Reason reason, String detail, String remedy) {

    /**
     * Possible outcomes of a capability assessment.
     */
    public enum Status {

        /** The feature is available in this environment. */
        AVAILABLE,

        /** No provider supports this operating system. */
        UNSUPPORTED_OS,

        /** The environment does not allow the feature (headless session, and so on). */
        UNSUPPORTED_ENVIRONMENT,

        /** A required dependency is missing. */
        MISSING_DEPENDENCY,

        /** A permission was denied. */
        PERMISSION_DENIED
    }

    /**
     * Creates a capability and enforces the invariants.
     *
     * @param status  the best assessment right now
     * @param reason  why the feature is not {@code AVAILABLE}, or {@code NONE} when it is
     * @param detail  human text describing the assessment; never {@code null}; may be empty
     * @param remedy  human remediation advice; must be {@code null} when the status is
     *        {@code AVAILABLE}
     * @throws NullPointerException     if {@code status}, {@code reason} or {@code detail} is null
     * @throws IllegalArgumentException if the status and reason combination violates the
     *        invariants, or if the remedy is set while the status is {@code AVAILABLE}
     */
    public Capability {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(detail, "detail");
        if ((status == Status.AVAILABLE) != (reason == Reason.NONE)) {
            throw new IllegalArgumentException(
                    "status AVAILABLE requires reason NONE and every other status requires a reason other than NONE");
        }
        if (status == Status.AVAILABLE && remedy != null) {
            throw new IllegalArgumentException("remedy must be null when status is AVAILABLE");
        }
    }
}
