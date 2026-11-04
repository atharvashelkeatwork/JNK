package io.github.atharvashelkeatwork.jnativekit.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;

/**
 * The interactive demo menu.
 *
 * <p>Reads commands from the injected reader so the menu can be tested without a console. This
 * class is demo-only and is not part of the published library API.
 */
final class DemoMenu {

    private final DemoActions actions;
    private final BufferedReader in;
    private final PrintStream out;

    DemoMenu(DemoActions actions, BufferedReader in, PrintStream out) {
        this.actions = Objects.requireNonNull(actions, "actions");
        this.in = Objects.requireNonNull(in, "in");
        this.out = Objects.requireNonNull(out, "out");
    }

    /**
     * Prints the menu and handles commands until Quit or end of input.
     */
    void run() {
        while (true) {
            printMenu();
            String line;
            try {
                line = in.readLine();
            } catch (IOException e) {
                out.println("Could not read input: " + e.getMessage());
                actions.quit();
                return;
            }
            if (line == null) {
                actions.quit();
                return;
            }
            if (!handle(line.trim())) {
                return;
            }
        }
    }

    private void printMenu() {
        out.println();
        out.println("Choose an action:");
        out.println("  1) Send a notification");
        out.println("  2) Toggle autostart for this demo");
        out.println("  3) Open a tray icon with Quit");
        out.println("  4) Print the capability report");
        out.println("  5) Quit");
        out.print("> ");
        out.flush();
    }

    /**
     * Handles one command line.
     *
     * @param command the trimmed command
     * @return {@code true} to keep the menu running, {@code false} to exit
     */
    private boolean handle(String command) {
        switch (command) {
            case "1" -> actions.sendNotification();
            case "2" -> actions.toggleAutostart();
            case "3" -> actions.openTray();
            case "4" -> actions.printCapabilities();
            case "5" -> {
                actions.quit();
                return false;
            }
            case "" -> {
                // Ignore empty lines; print the menu again.
            }
            default -> out.println("Unknown choice: " + command);
        }
        return true;
    }
}
