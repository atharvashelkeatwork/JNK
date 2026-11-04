package io.github.atharvashelkeatwork.jnativekit.demo;

import java.io.PrintStream;
import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.Autostart;
import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Feature;
import io.github.atharvashelkeatwork.jnativekit.Notifications;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Tray;

/**
 * Prints the capability report for the running machine.
 *
 * <p>The report shows the raw {@link Capability} values: status, reason, human detail and remedy.
 * It deliberately does not promise that an available feature will produce a visible result; the
 * library's result model is the source of truth for that. This class is demo-only and is not part
 * of the published library API.
 */
final class CapabilityReport {

    private CapabilityReport() {
    }

    /**
     * Prints a capability table for the three demo features.
     *
     * @param out           the destination stream
     * @param platform      the detected platform
     * @param notifications the notifications feature
     * @param autostart     the autostart feature
     * @param tray          the tray feature
     */
    static void print(PrintStream out, Platform platform, Notifications notifications,
            Autostart autostart, Tray tray) {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(notifications, "notifications");
        Objects.requireNonNull(autostart, "autostart");
        Objects.requireNonNull(tray, "tray");

        out.println("jnativekit capability report");
        out.println("Platform: " + platform);
        out.printf("%-14s %-24s %-26s %s%n", "Feature", "Status", "Reason", "Detail");
        printRow(out, "Notifications", notifications);
        printRow(out, "Autostart", autostart);
        printRow(out, "Tray", tray);
        out.println();
    }

    private static void printRow(PrintStream out, String name, Feature feature) {
        Capability capability = feature.capability();
        String detail = capability.detail().isEmpty() ? "-" : capability.detail();
        out.printf("%-14s %-24s %-26s %s%n",
                name, capability.status(), capability.reason(), detail);
        if (capability.remedy() != null) {
            out.println("               remedy: " + capability.remedy());
        }
    }
}
