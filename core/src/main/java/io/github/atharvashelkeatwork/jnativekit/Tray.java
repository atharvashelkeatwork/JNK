package io.github.atharvashelkeatwork.jnativekit;

/**
 * System tray support: opens one tray icon and returns a handle that owns its lifecycle.
 */
public interface Tray extends Feature {

    /**
     * Opens one tray icon for the given icon.
     *
     * <p>Each successful call returns a new, independent handle. Calling this method twice yields
     * two icons; the library never creates a duplicate icon on its own.
     *
     * @param icon the icon to open; validated by its constructor
     * @return the new handle, or {@code UNAVAILABLE} when no tray backend is available, or
     *         {@code FAILED} when the backend failed
     * @throws NullPointerException     if {@code icon} is null
     * @throws IllegalArgumentException if the icon violates a validation rule
     */
    Result<TrayHandle> open(TrayIcon icon);
}
