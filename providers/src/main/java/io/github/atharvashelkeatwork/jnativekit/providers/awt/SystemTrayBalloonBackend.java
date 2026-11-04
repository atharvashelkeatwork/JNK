package io.github.atharvashelkeatwork.jnativekit.providers.awt;

import java.awt.AWTException;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * AWT implementation of {@link BalloonBackend}.
 *
 * <p>Each message owns a short-lived {@link TrayIcon}: the icon is added to the system tray,
 * {@link TrayIcon#displayMessage(String, String, TrayIcon.MessageType)} is called, and the icon is
 * removed again after a short delay. The delay uses a daemon delayed executor, so it never keeps
 * the JVM alive. This is the mechanism Windows uses for tray balloon notifications; it is not a
 * rich toast API and does not imply any visibility guarantee.
 *
 * <p>The whole icon lifecycle runs on the AWT event dispatch thread, like the tray backend.
 */
public final class SystemTrayBalloonBackend implements BalloonBackend {

    /** How long the temporary tray icon stays present after a message is dispatched. */
    private static final long ICON_LIFETIME_SECONDS = 10;

    private static final System.Logger LOG =
            System.getLogger(SystemTrayBalloonBackend.class.getName());

    /**
     * Creates a backend that uses the AWT system tray.
     */
    public SystemTrayBalloonBackend() {
    }

    @Override
    public boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    @Override
    public boolean isSupported() {
        return SystemTray.isSupported();
    }

    @Override
    public void display(String title, String body) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        if (isHeadless()) {
            throw new IllegalStateException("AWT is headless; a tray balloon cannot be displayed");
        }
        if (!isSupported()) {
            throw new IllegalStateException("The AWT system tray is not supported");
        }

        SystemTray tray = SystemTray.getSystemTray();
        TrayIcon[] icon = new TrayIcon[1];
        Runnable task = () -> {
            TrayIcon created = new TrayIcon(createIconImage(), "");
            icon[0] = created;
            try {
                tray.add(created);
                created.displayMessage(title, body, TrayIcon.MessageType.NONE);
            } catch (AWTException | RuntimeException e) {
                try {
                    tray.remove(created);
                } catch (RuntimeException removeFailure) {
                    e.addSuppressed(removeFailure);
                }
                throw new IllegalStateException("Could not display an AWT tray balloon", e);
            }
        };
        CompletableFuture.delayedExecutor(ICON_LIFETIME_SECONDS, TimeUnit.SECONDS)
                .execute(() -> remove(tray, icon[0]));
        AwtTrayBackend.runOnEventThread(task);
    }

    private static void remove(SystemTray tray, TrayIcon icon) {
        if (icon == null) {
            return;
        }
        try {
            tray.remove(icon);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to remove a temporary tray icon", e);
        }
    }

    private static Image createIconImage() {
        return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    }
}
