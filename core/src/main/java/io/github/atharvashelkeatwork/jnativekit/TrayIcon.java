package io.github.atharvashelkeatwork.jnativekit;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.internal.Validation;

/**
 * A tray icon requested by the application, together with its tooltip and its fixed menu.
 *
 * <p>Immutable and validated on construction; the icon bytes and the menu are copied, and the
 * icon bytes are copied again on every access so that callers cannot mutate library state.
 *
 * <p>Menu rules:
 * <ul>
 *   <li>the menu may hold at most 20 entries, separators included;</li>
 *   <li>an item label must be non-blank, a separator must be an empty label with a null action;</li>
 *   <li>the menu is fixed at open time.</li>
 * </ul>
 *
 * <p>The PNG format is only signature-checked here; the decoding happens when the tray provider
 * opens the icon.
 *
 * @param png  the icon bytes; a non-empty PNG with a valid signature, copied on construction and
 *             on every access
 * @param tooltip the icon tooltip; non-null, at most 128 characters, may be empty
 * @param menu  the menu entries; at most 20 entries, unmodifiable copy
 */
public record TrayIcon(byte[] png, String tooltip, List<TrayMenuItem> menu) {

    /** Maximum number of menu entries accepted when opening a tray icon. */
    private static final int MAX_MENU_ITEMS = 20;

    /**
     * Creates a tray icon and enforces the invariants.
     *
     * @param png     the icon bytes; a non-empty PNG with a valid signature
     * @param tooltip the icon tooltip; non-null, at most 128 characters, may be empty
     * @param menu    the menu entries; at most 20 entries, unmodifiable copy
     * @throws NullPointerException     if {@code png}, {@code tooltip} or {@code menu} is null
     * @throws IllegalArgumentException if the PNG signature is missing or invalid, the tooltip is
     *        too long, or the menu holds more than 20 entries
     */
    public TrayIcon(byte[] png, String tooltip, List<TrayMenuItem> menu) {
        this.png = Validation.copyAndValidatePng(png);
        this.tooltip = Validation.requireTooltip(tooltip);
        this.menu = List.copyOf(Objects.requireNonNull(menu, "menu"));
        if (this.menu.size() > MAX_MENU_ITEMS) {
            throw new IllegalArgumentException("menu must contain at most 20 items");
        }
    }

    /**
     * Returns a copy of the icon bytes. The library owns the icon once it is opened; callers must
     * not retain or mutate the returned array.
     *
     * @return a fresh copy of the validated PNG bytes
     */
    @Override
    public byte[] png() {
        return png.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TrayIcon other)) {
            return false;
        }
        return Arrays.equals(png, other.png)
                && Objects.equals(tooltip, other.tooltip)
                && Objects.equals(menu, other.menu);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(png), tooltip, menu);
    }

    @Override
    public String toString() {
        return "TrayIcon[png=" + png.length + " bytes, tooltip=" + tooltip + ", menu=" + menu.size()
                + " item(s)]";
    }
}
