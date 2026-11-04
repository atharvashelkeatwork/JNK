package io.github.atharvashelkeatwork.jnativekit.demo;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

import io.github.atharvashelkeatwork.jnativekit.Autostart;
import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Desktop;
import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Notifications;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.Tray;
import io.github.atharvashelkeatwork.jnativekit.TrayHandle;
import io.github.atharvashelkeatwork.jnativekit.TrayIcon;
import io.github.atharvashelkeatwork.jnativekit.TrayMenuItem;

/**
 * The actions behind the demo menu and the autostart launch mode.
 *
 * <p>All feature access goes through the public API. This class is demo-only and is not part of
 * the published library API.
 */
final class DemoActions {

    private final Notifications notifications;
    private final Autostart autostart;
    private final Tray tray;
    private final byte[] trayPng;
    private final AutostartEntry autostartEntry;
    private final PrintStream out;
    private final PrintStream err;
    private final CountDownLatch quitLatch = new CountDownLatch(1);

    private TrayHandle trayHandle;

    DemoActions(Notifications notifications, Autostart autostart, Tray tray, byte[] trayPng,
            AutostartEntry autostartEntry, PrintStream out, PrintStream err) {
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.autostart = Objects.requireNonNull(autostart, "autostart");
        this.tray = Objects.requireNonNull(tray, "tray");
        this.trayPng = trayPng == null ? null : trayPng.clone();
        this.autostartEntry = Objects.requireNonNull(autostartEntry, "autostartEntry");
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
    }

    /**
     * Prints the capability table for this machine.
     */
    void printCapabilities() {
        CapabilityReport.print(out, Desktop.platform(), notifications, autostart, tray);
    }

    /**
     * Sends one demo notification and prints the honest result.
     */
    void sendNotification() {
        try {
            Result<Void> result = notifications.show(
                    Notification.of("jnativekit demo", "Hello from the jnativekit demo."));
            printResult("Notification", result);
        } catch (RuntimeException e) {
            err.println("Notification threw unexpectedly: " + e);
        }
    }

    /**
     * Enables autostart when the current state is confirmed disabled, and disables it when the
     * current state is confirmed enabled. An unknown state is reported and left unchanged.
     */
    void toggleAutostart() {
        try {
            Result<Boolean> current = autostart.isEnabled(DemoAssets.appId());
            printResult("Autostart isEnabled", current);
            if (!current.isConfirmed()) {
                out.println("Autostart state is unknown; nothing was changed.");
                return;
            }
            if (Boolean.TRUE.equals(current.value())) {
                printResult("Autostart disable", autostart.disable(DemoAssets.appId()));
            } else {
                printResult("Autostart enable", autostart.enable(autostartEntry));
            }
        } catch (RuntimeException e) {
            err.println("Autostart check threw unexpectedly: " + e);
        }
    }

    /**
     * Opens one tray icon with a Quit item. Opening twice is refused by the demo, so the menu
     * cannot create duplicate icons.
     */
    synchronized void openTray() {
        if (trayHandle != null && trayHandle.isOpen()) {
            out.println("Tray icon is already open.");
            return;
        }
        if (trayPng == null) {
            out.println("Tray icon unavailable: the demo resource is missing.");
            return;
        }
        try {
            TrayIcon icon = new TrayIcon(trayPng, DemoAssets.trayTooltip(),
                    List.of(TrayMenuItem.item("Quit", this::quit)));
            Result<TrayHandle> result = tray.open(icon);
            printResult("Tray open", result);
            if (result.isConfirmed()) {
                trayHandle = result.value();
                out.println("Choose Quit in the tray menu to exit the demo.");
            }
        } catch (RuntimeException e) {
            err.println("Tray open threw unexpectedly: " + e);
        }
    }

    /**
     * Opens the tray and blocks until Quit is selected. Used by the autostart launch mode.
     */
    void runAutostartMode() {
        printCapabilities();
        sendNotification();
        openTray();
        if (!isTrayOpen()) {
            out.println("Tray did not open; the autostart demo is exiting.");
            return;
        }
        awaitQuit();
        closeTray();
    }

    /**
     * Blocks until {@link #quit()} is called. Used by both demo modes so the tray Quit item ends
     * the process.
     */
    void awaitQuit() {
        try {
            quitLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Closes the demo tray icon, if one is open.
     */
    synchronized void closeTray() {
        if (trayHandle == null) {
            return;
        }
        trayHandle.close();
        trayHandle = null;
        out.println("Tray closed.");
    }

    /**
     * Closes the tray and releases any autostart-mode wait.
     */
    void quit() {
        closeTray();
        quitLatch.countDown();
    }

    /**
     * Returns whether the demo currently owns an open tray handle.
     *
     * @return {@code true} while the demo tray icon is open
     */
    synchronized boolean isTrayOpen() {
        return trayHandle != null && trayHandle.isOpen();
    }

    /**
     * Returns the autostart entry used by the demo.
     *
     * @return the demo autostart entry
     */
    AutostartEntry autostartEntry() {
        return autostartEntry;
    }

    private void printResult(String action, Result<?> result) {
        StringBuilder line = new StringBuilder(action)
                .append(": ").append(result.status());
        if (result.reason() != Reason.NONE) {
            line.append(" reason=").append(result.reason());
        }
        if (result.provider() != null) {
            line.append(" provider=").append(result.provider());
        }
        if (result.value() != null) {
            line.append(" value=").append(describeValue(result.value()));
        }
        if (!result.detail().isEmpty()) {
            line.append(" detail=").append(result.detail());
        }
        out.println(line);
    }

    private static String describeValue(Object value) {
        if (value instanceof TrayHandle) {
            return "open tray handle";
        }
        return String.valueOf(value);
    }
}
