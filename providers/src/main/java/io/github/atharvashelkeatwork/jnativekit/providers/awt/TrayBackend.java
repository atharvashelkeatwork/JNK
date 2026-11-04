package io.github.atharvashelkeatwork.jnativekit.providers.awt;

import java.awt.Image;
import java.util.List;
import java.util.Objects;

/**
 * Minimal AWT tray backend used by {@link AwtSystemTrayProvider}.
 *
 * <p>Internal to the {@code providers} module. The interface exists so the provider can be tested
 * without a display and so the AWT event-thread handling stays separate from result mapping and
 * callback dispatch.
 */
public interface TrayBackend {

    /**
     * Returns whether the JVM is running in a headless AWT environment.
     *
     * @return {@code true} when AWT is headless
     */
    boolean isHeadless();

    /**
     * Returns whether the AWT system tray is supported in this environment.
     *
     * @return {@code true} when an icon can be added to the system tray
     */
    boolean isSupported();

    /**
     * Adds one icon with its popup menu to the system tray.
     *
     * @param image   the decoded icon image; never {@code null}
     * @param tooltip the tooltip text; never {@code null}, may be empty
     * @param menu    the menu entries in display order; never {@code null}
     * @return a handle that removes the icon again; never {@code null}
     * @throws RuntimeException when AWT rejects the icon or the event thread fails
     */
    IconHandle add(Image image, String tooltip, List<MenuEntry> menu);

    /**
     * Handle to one icon that was added to the system tray.
     */
    interface IconHandle {

        /**
         * Removes the icon from the system tray. Repeated calls are no-ops.
         *
         * @throws RuntimeException when AWT cannot remove the icon
         */
        void remove();
    }

    /**
     * One popup-menu entry handed to the backend.
     *
     * @param label  the entry label; empty for a separator
     * @param action the action to run when the user selects the entry; {@code null} for a
     *               separator
     */
    record MenuEntry(String label, Runnable action) {

        /**
         * Creates a menu entry or separator and enforces the basic shape.
         *
         * @param label  the entry label; empty for a separator
         * @param action the action to run; {@code null} for a separator
         * @throws NullPointerException     if {@code label} is null
         * @throws IllegalArgumentException if a separator has a non-empty label, or an item has a
         *        blank label or a null action
         */
        public MenuEntry {
            Objects.requireNonNull(label, "label");
            if (action == null) {
                if (!label.isEmpty()) {
                    throw new IllegalArgumentException("a separator must have an empty label");
                }
            } else if (label.isBlank()) {
                throw new IllegalArgumentException("an item label must not be blank");
            }
        }

        /**
         * Returns whether this entry is a separator.
         *
         * @return {@code true} when the entry has no action
         */
        public boolean isSeparator() {
            return action == null;
        }
    }
}
