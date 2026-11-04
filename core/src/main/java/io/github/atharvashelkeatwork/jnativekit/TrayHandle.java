package io.github.atharvashelkeatwork.jnativekit;

/**
 * Owns the lifecycle of a single open tray icon.
 */
public interface TrayHandle extends AutoCloseable {

    /**
     * Returns whether the icon is currently open.
     *
     * @return {@code true} while the icon is open, {@code false} after a close
     */
    boolean isOpen();

    /**
     * Closes the icon and stops accepting callbacks. Closing an already closed handle is a no-op.
     * Repeated calls never throw.
     */
    @Override
    void close();
}
