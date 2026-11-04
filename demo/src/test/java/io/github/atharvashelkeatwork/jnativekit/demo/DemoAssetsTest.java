package io.github.atharvashelkeatwork.jnativekit.demo;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

class DemoAssetsTest {

    @Test
    void trayIconIsAPng() throws IOException {
        byte[] png = DemoAssets.loadTrayPng();

        assertTrue(png.length > 8);
        assertArrayEquals(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'},
                java.util.Arrays.copyOf(png, 8));
    }

    @Test
    void identifiersAreUsableByThePublicApi() {
        assertTrue(DemoAssets.appId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"));
        assertFalse(DemoAssets.displayName().isBlank());
        assertEquals(Demo.class.getName(), DemoAssets.mainClassName());
    }
}
