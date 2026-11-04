package io.github.atharvashelkeatwork.jnativekit.providers.internal.text;

/**
 * Signals that a property list could not be parsed.
 *
 * <p>Internal to the {@code providers} module. Extends {@link java.io.IOException} so callers can
 * map unreadable and malformed stores to the same {@code FAILED(IO_ERROR)} result.
 */
public final class PlistFormatException extends java.io.IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message a human-readable description of the problem
     */
    public PlistFormatException(String message) {
        super(message);
    }
}
