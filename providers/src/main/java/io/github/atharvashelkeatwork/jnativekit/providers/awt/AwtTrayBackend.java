package io.github.atharvashelkeatwork.jnativekit.providers.awt;

import java.awt.AWTException;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;

/**
 * AWT implementation of {@link TrayBackend}.
 *
 * <p>Icon and menu creation, {@link SystemTray#add(TrayIcon)} and {@link SystemTray#remove(TrayIcon)}
 * all run on the AWT event dispatch thread. Callback actions handed to the backend are invoked
 * unchanged on the AWT thread; it is the provider's job to move user code off that thread.
 *
 * <p>This class is internal to the {@code providers} module.
 */
public final class AwtTrayBackend implements TrayBackend {

    private static final System.Logger LOG = System.getLogger(AwtTrayBackend.class.getName());

    /**
     * Creates a backend that uses the real AWT system tray.
     */
    public AwtTrayBackend() {
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
    public IconHandle add(Image image, String tooltip, List<MenuEntry> menu) {
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(tooltip, "tooltip");
        Objects.requireNonNull(menu, "menu");

        TrayIcon[] icon = new TrayIcon[1];
        SystemTray systemTray = SystemTray.getSystemTray();
        Runnable task = () -> {
            TrayIcon created = new TrayIcon(image, tooltip);
            created.setImageAutoSize(true);
            PopupMenu popup = new PopupMenu();
            for (MenuEntry entry : menu) {
                if (entry.isSeparator()) {
                    popup.addSeparator();
                } else {
                    MenuItem item = new MenuItem(entry.label());
                    Runnable action = entry.action();
                    item.addActionListener(event -> action.run());
                    popup.add(item);
                }
            }
            created.setPopupMenu(popup);
            icon[0] = created;
            try {
                systemTray.add(created);
            } catch (AWTException e) {
                throw new AwtTrayException("The AWT system tray rejected the icon", e);
            }
        };
        try {
            runOnEventThread(task);
        } catch (RuntimeException e) {
            removeQuietly(systemTray, icon[0]);
            throw e;
        }
        return () -> runOnEventThread(() -> systemTray.remove(icon[0]));
    }

    /**
     * Runs a task on the AWT event dispatch thread.
     *
     * <p>Package-private so the balloon backend runs its icon lifecycle on the same thread without
     * duplicating the event-thread handling.
     */
    static void runOnEventThread(Runnable task) {
        if (EventQueue.isDispatchThread()) {
            task.run();
            return;
        }
        try {
            EventQueue.invokeAndWait(task);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AwtTrayException(
                    "Interrupted while waiting for the AWT event dispatch thread", e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AwtTrayException("The AWT event dispatch thread failed", cause);
        }
    }

    private static void removeQuietly(SystemTray systemTray, TrayIcon icon) {
        if (icon == null) {
            return;
        }
        try {
            runOnEventThread(() -> systemTray.remove(icon));
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not remove an AWT tray icon", e);
        }
    }

    /**
     * Unchecked carrier for checked AWT failures crossing the event-thread boundary.
     */
    private static final class AwtTrayException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private AwtTrayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
