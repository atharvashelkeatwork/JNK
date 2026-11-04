package io.github.atharvashelkeatwork.jnativekit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * Covers the {@code TrayIcon} validation rules, defensive copying and equality.
 */
class TrayIconTest {

    private static byte[] pngBytes() {
        try {
            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("PNG writer failed");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<TrayMenuItem> menuOf(int size) {
        return IntStream.range(0, size)
                .mapToObj(i -> TrayMenuItem.item("Item " + i, () -> {
                }))
                .toList();
    }

    @Test
    void acceptsValidIconAndCopiesBytes() {
        byte[] source = pngBytes();
        byte[] original = source.clone();
        TrayIcon icon = new TrayIcon(source, "Example", List.of());
        assertArrayEquals(original, icon.png());
        byte[] viaGetter = icon.png();
        viaGetter[0] = (byte) 0;
        assertArrayEquals(original, icon.png());
        source[0] = (byte) 0;
        assertArrayEquals(original, icon.png());
    }

    @Test
    void rejectsEmptyPng() {
        assertThrows(IllegalArgumentException.class, () -> new TrayIcon(new byte[0], "tip", List.of()));
    }

    @Test
    void rejectsNonPngBytes() {
        assertThrows(IllegalArgumentException.class, () -> new TrayIcon(new byte[8], "tip", List.of()));
    }

    @Test
    void rejectsNullPng() {
        assertThrows(NullPointerException.class, () -> new TrayIcon(null, "tip", List.of()));
    }

    @Test
    void acceptsValidTooltipLengths() {
        new TrayIcon(pngBytes(), "", List.of());
        new TrayIcon(pngBytes(), "t".repeat(128), List.of());
    }

    @Test
    void rejectsNullOrTooLongTooltip() {
        assertThrows(NullPointerException.class, () -> new TrayIcon(pngBytes(), null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TrayIcon(pngBytes(), "t".repeat(129), List.of()));
    }

    @Test
    void rejectsMenuWithTooManyEntries() {
        assertEquals(20, menuOf(20).size());
        new TrayIcon(pngBytes(), "tip", menuOf(20));
        assertThrows(IllegalArgumentException.class, () -> new TrayIcon(pngBytes(), "tip", menuOf(21)));
    }

    @Test
    void rejectsNullMenu() {
        assertThrows(NullPointerException.class, () -> new TrayIcon(pngBytes(), "tip", null));
    }

    @Test
    void iconsWithEqualContentAreEqual() {
        TrayIcon a = new TrayIcon(pngBytes(), "tip", List.of());
        TrayIcon b = new TrayIcon(pngBytes(), "tip", List.of());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        TrayIcon c = new TrayIcon(pngBytes(), "tip", List.of(TrayMenuItem.item("Quit", () -> {
        })));
        assertNotEquals(c, a);
    }

    @Test
    void toStringDoesNotLeakArrayIdentity() {
        TrayIcon icon = new TrayIcon(pngBytes(), "tip", List.of());
        String text = icon.toString();
        assertFalse(text.contains("[B@"));
        assertTrue(text.contains("png="));
    }
}
