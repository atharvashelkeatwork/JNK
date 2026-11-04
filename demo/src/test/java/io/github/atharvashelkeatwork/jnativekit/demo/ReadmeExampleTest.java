package io.github.atharvashelkeatwork.jnativekit.demo;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Desktop;
import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.TrayHandle;
import io.github.atharvashelkeatwork.jnativekit.TrayIcon;
import io.github.atharvashelkeatwork.jnativekit.TrayMenuItem;

/**
 * Compile-checks the quick-start example that the README shows to users.
 *
 * <p>The example method is never invoked: compiling it against the real public API is the
 * acceptance criterion. Running it would write an autostart entry and show a tray icon on the
 * machine that executes the tests, which is exactly what the test rules forbid.
 */
class ReadmeExampleTest {

    @Test
    void readmeExampleCompilesAgainstThePublicApi() throws NoSuchMethodException {
        assertNotNull(ReadmeExampleTest.class.getDeclaredMethod("readmeQuickStart"));
    }

    /**
     * The exact code the README quick-start section shows. Keep the two in sync.
     *
     * @throws Exception if the example icon cannot be read
     */
    @SuppressWarnings("unused")
    private static void readmeQuickStart() throws Exception {
        Capability caps = Desktop.notifications().capability();
        if (caps.status() != Capability.Status.AVAILABLE) {
            System.out.println(caps.reason() + ": " + caps.remedy());
        } else {
            Result<Void> result = Desktop.notifications()
                    .show(Notification.of("Backup done", "42 files synced"));
            System.out.println(result.status() + " via " + result.provider());
        }

        Result<Void> autostart = Desktop.autostart().enable(new AutostartEntry(
                "com.example.notes", "Example Notes",
                Path.of(System.getProperty("java.io.tmpdir"), "notes"), List.of("--minimized")));
        System.out.println("autostart: " + autostart.status());

        byte[] iconPng = Files.readAllBytes(Path.of("icon.png"));
        Result<TrayHandle> tray = Desktop.tray().open(new TrayIcon(
                iconPng, "Example Notes",
                List.of(TrayMenuItem.item("Quit", () -> System.exit(0)))));
        System.out.println("tray: " + tray.status());
    }
}
