package io.github.atharvashelkeatwork.jnativekit.internal;

/**
 * Helpers for the non-fatal failures that may cross the provider boundary.
 *
 * <p>Implementation detail. The public API promises that a broken provider never escapes as an
 * exception, but a provider can still throw an {@link Error} such as {@code AWTError} or
 * {@code LinkageError} when a native backend or an optional JDK module is missing. Those errors are
 * converted to {@code Capability} or {@code Result} values. Failures that mean the JVM itself is
 * unusable, such as {@link VirtualMachineError}, are always rethrown.
 */
public final class Errors {

    private static final int MAX_DESCRIPTION_LENGTH = 512;

    private Errors() {
    }

    /**
     * Returns whether a failure is fatal and must never be converted to a value.
     *
     * @param failure the failure to inspect; never {@code null}
     * @return {@code true} for {@link VirtualMachineError} and {@link ThreadDeath}
     */
    public static boolean isFatal(Throwable failure) {
        return failure instanceof VirtualMachineError || failure instanceof ThreadDeath;
    }

    /**
     * Builds a short, single-line description of a failure for a {@code Capability} or
     * {@code Result} detail.
     *
     * <p>The provider message is truncated to 512 characters so a hostile or runaway provider
     * cannot inflate the public detail text.
     *
     * @param failure the failure to describe; never {@code null}
     * @return the failure class name, followed by its message when one is present
     */
    public static String describe(Throwable failure) {
        String message = failure.getMessage();
        String description = failure.getClass().getSimpleName();
        if (message != null && !message.isBlank()) {
            description = description + ": " + message;
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            description = description.substring(0, MAX_DESCRIPTION_LENGTH) + "...";
        }
        return description;
    }
}
