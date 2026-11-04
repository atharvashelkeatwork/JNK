package io.github.atharvashelkeatwork.jnativekit;

/**
 * Machine-readable reasons why a capability check or an action could not be confirmed.
 */
public enum Reason {

    /** No reason; the check or the action succeeded. */
    NONE,

    /** No provider exists for this operating system. */
    UNSUPPORTED_PLATFORM,

    /** No graphical session is available (headless, no DISPLAY/WAYLAND_DISPLAY, and so on). */
    NO_GRAPHICAL_SESSION,

    /** The required external tool or service was not found. */
    BACKEND_MISSING,

    /** A permission was denied by the user or the operating system. */
    PERMISSION_DENIED,

    /** The feature state is not usable for this action (for example a closed tray handle). */
    INVALID_STATE,

    /** The external process exceeded its time limit. */
    TIMEOUT,

    /** A file or registry read or write failed. */
    IO_ERROR,

    /** The external tool ran and reported failure (non-zero exit code or exception). */
    BACKEND_ERROR,

    /** The action ran but the postcondition could not be verified. */
    VERIFICATION_FAILED
}
