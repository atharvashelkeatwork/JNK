package io.github.atharvashelkeatwork.jnativekit.providers.awt;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Desktop;

/**
 * Child-process entry point for the real headless AWT integration test.
 *
 * <p>It prints the capability that {@link Desktop#tray()} reports in a JVM started with
 * {@code -Djava.awt.headless=true}, so the test can prove the real
 * {@link java.awt.GraphicsEnvironment#isHeadless()} wiring, not only the fake.
 */
public final class HeadlessTrayProbe {

    private HeadlessTrayProbe() {
    }

    /**
     * Prints the tray capability status and reason.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        Capability capability = Desktop.tray().capability();
        System.out.println(capability.status() + " " + capability.reason());
    }
}
