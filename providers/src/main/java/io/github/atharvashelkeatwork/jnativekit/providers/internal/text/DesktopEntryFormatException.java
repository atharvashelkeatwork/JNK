package io.github.atharvashelkeatwork.jnativekit.providers.internal.text;

import java.io.IOException;

/**
 * Signals that a Desktop Entry file could not be parsed.
 *
 * <p>Internal to the {@code providers} module. Extends {@link IOException} so callers can map
 * unreadable and malformed stores to the same {@code FAILED(IO_ERROR)} result.
 */
public final class DesktopEntryFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message a human-readable description of the problem
     */
    public DesktopEntryFormatException(String message) {
        super(message);
    }
}
