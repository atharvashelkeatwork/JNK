package io.github.atharvashelkeatwork.jnativekit;

import java.util.Objects;

/**
 * A single entry of a tray menu: a label with an action, or a separator.
 *
 * <p>An item requires a non-blank label and a non-null action. A separator is represented by an
 * empty label and a null action; it is created with {@link #separator()}.
 *
 * @param label the menu entry label; blank for a separator, non-blank for an item
 * @param action the action to run when the user selects the entry; {@code null} for a separator
 */
public record TrayMenuItem(String label, Runnable action) {

    /**
     * Creates a menu item or separator and enforces the invariants.
     *
     * @param label the menu entry label; blank for a separator, non-blank for an item
     * @param action the action to run; {@code null} for a separator
     * @throws NullPointerException     if {@code label} is null
     * @throws IllegalArgumentException if an item has a blank label, or a separator has a
     *        non-empty label
     */
    public TrayMenuItem {
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
     * Creates a menu item that runs the given action when selected.
     *
     * @param label the item label; must be non-blank
     * @param action the action to run; must not be null
     * @return a validated menu item
     * @throws NullPointerException     if the label or the action is null
     * @throws IllegalArgumentException if the label is blank
     */
    public static TrayMenuItem item(String label, Runnable action) {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(action, "action");
        return new TrayMenuItem(label, action);
    }

    /**
     * Creates a menu separator.
     *
     * @return a menu item with an empty label and a null action
     */
    public static TrayMenuItem separator() {
        return new TrayMenuItem("", null);
    }
}
