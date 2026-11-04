package io.github.atharvashelkeatwork.jnativekit.demo;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Loads the demo's bundled resources.
 *
 * <p>This class is demo-only and is not part of the published library API.
 */
final class DemoAssets {

    private static final String TRAY_ICON = "/tray-icon.png";

    private DemoAssets() {
    }

    /**
     * Reads the tray icon PNG from the demo classpath.
     *
     * @return the PNG bytes; never an empty array
     * @throws IOException if the resource is missing or cannot be read
     */
    static byte[] loadTrayPng() throws IOException {
        try (InputStream in = DemoAssets.class.getResourceAsStream(TRAY_ICON)) {
            if (in == null) {
                throw new IOException("missing classpath resource " + TRAY_ICON);
            }
            byte[] png = in.readAllBytes();
            if (png.length == 0) {
                throw new IOException("classpath resource " + TRAY_ICON + " is empty");
            }
            return png;
        }
    }

    /**
     * Returns the demo application identifier used for autostart.
     *
     * @return the stable demo app id
     */
    static String appId() {
        return "io.github.atharvashelkeatwork.jnativekit.demo";
    }

    /**
     * Returns the human-readable demo application name.
     *
     * @return the demo display name
     */
    static String displayName() {
        return "jnativekit demo";
    }

    /**
     * Returns the tooltip used by the demo tray icon.
     *
     * @return the tray tooltip
     */
    static String trayTooltip() {
        return "jnativekit demo";
    }

    /**
     * Returns the demo's main class name, used when building a classpath launch command.
     *
     * @return the main class name
     */
    static String mainClassName() {
        return Objects.requireNonNull(Demo.class.getName(), "main class name");
    }
}
