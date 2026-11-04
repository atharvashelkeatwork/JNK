package io.github.atharvashelkeatwork.jnativekit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Covers the {@code TrayMenuItem} validation rules.
 */
class TrayMenuItemTest {

    @Test
    void itemAcceptsNonBlankLabelAndAction() {
        TrayMenuItem item = TrayMenuItem.item("Quit", () -> {
        });
        assertEquals("Quit", item.label());
        assertNotNull(item.action());
    }

    @Test
    void itemRejectsNullLabelOrAction() {
        assertThrows(NullPointerException.class, () -> TrayMenuItem.item(null, () -> {
        }));
        assertThrows(NullPointerException.class, () -> TrayMenuItem.item("Quit", null));
    }

    @Test
    void itemRejectsBlankLabel() {
        assertThrows(IllegalArgumentException.class, () -> TrayMenuItem.item("   ", () -> {
        }));
    }

    @Test
    void separatorAcceptsEmptyLabelAndNullAction() {
        TrayMenuItem separator = TrayMenuItem.separator();
        assertEquals("", separator.label());
        assertNull(separator.action());
    }

    @Test
    void separatorRejectsNonEmptyLabel() {
        assertThrows(IllegalArgumentException.class, () -> new TrayMenuItem("Quit", null));
        assertThrows(IllegalArgumentException.class, () -> new TrayMenuItem("   ", null));
    }

    @Test
    void itemRequiresActionForNonEmptyLabel() {
        assertThrows(IllegalArgumentException.class, () -> new TrayMenuItem("", () -> {
        }));
    }
}
