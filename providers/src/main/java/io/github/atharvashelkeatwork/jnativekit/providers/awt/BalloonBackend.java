package io.github.atharvashelkeatwork.jnativekit.providers.awt;

/**
 * Minimal AWT tray-balloon backend used by the Windows notification provider.
 *
 * <p>Internal to the {@code providers} module. The interface exists so the provider can be tested
 * without a display and so the AWT implementation stays separate from notification result mapping.
 */
public interface BalloonBackend {

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
     * Displays one balloon message with a short-lived tray icon.
     *
     * @param title the notification title
     * @param body  the notification body
     * @throws RuntimeException when AWT cannot add the icon or display the message
     */
    void display(String title, String body);
}
