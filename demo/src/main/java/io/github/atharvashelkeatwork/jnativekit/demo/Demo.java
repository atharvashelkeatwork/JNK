package io.github.atharvashelkeatwork.jnativekit.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Desktop;
import io.github.atharvashelkeatwork.jnativekit.Platform;

/**
 * Runnable demo for jnativekit.
 *
 * <p>With no arguments it prints a capability report and starts an interactive menu. The
 * {@code --report} flag prints only the report, and {@code --autostart} is the mode used by the
 * demo's own autostart entry: it sends a notification and opens the tray icon, then waits for
 * Quit.
 *
 * <p>This class is demo-only and is not part of the published library API.
 */
public final class Demo {

    private Demo() {
    }

    /**
     * Runs the demo.
     *
     * @param args {@code --help}, {@code --report}, {@code --autostart}, or no argument
     */
    public static void main(String[] args) {
        PrintStream out = System.out;
        PrintStream err = System.err;
        String mode = args.length == 0 ? "" : args[0];
        if (args.length > 1 || !isKnownMode(mode)) {
            usage(err);
            return;
        }
        if ("--help".equals(mode)) {
            usage(out);
            return;
        }

        byte[] trayPng = loadTrayPng(err);
        AutostartEntry entry = LaunchCommand.demoEntry(
                Path.of(System.getProperty("java.home")),
                Desktop.platform() == Platform.WINDOWS,
                LaunchCommand.currentCodeSource(),
                System.getProperty("java.class.path"));
        DemoActions actions = new DemoActions(
                Desktop.notifications(),
                Desktop.autostart(),
                Desktop.tray(),
                trayPng,
                entry,
                out,
                err);

        switch (mode) {
            case "--report" -> actions.printCapabilities();
            case "--autostart" -> actions.runAutostartMode();
            default -> {
                actions.printCapabilities();
                Thread menuThread = new Thread(
                        () -> new DemoMenu(actions,
                                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                                out).run(),
                        "jnativekit-demo-menu");
                menuThread.setDaemon(true);
                menuThread.start();
                actions.awaitQuit();
            }
        }
    }

    private static boolean isKnownMode(String mode) {
        return mode.isEmpty() || "--help".equals(mode) || "--report".equals(mode)
                || "--autostart".equals(mode);
    }

    private static byte[] loadTrayPng(PrintStream err) {
        try {
            return DemoAssets.loadTrayPng();
        } catch (IOException e) {
            err.println("Warning: could not load the tray icon: " + e.getMessage());
            return null;
        }
    }

    private static void usage(PrintStream out) {
        out.println("jnativekit demo");
        out.println("Usage: java -jar jnativekit-demo.jar [--help|--report|--autostart]");
        out.println("  --help       show this help");
        out.println("  --report     print the capability report and exit");
        out.println("  --autostart  run the login-time demo: notification + tray icon");
        out.println("  (no flag)    print the capability report and open the interactive menu");
    }
}
