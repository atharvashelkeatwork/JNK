package io.github.atharvashelkeatwork.jnativekit.demo;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.github.atharvashelkeatwork.jnativekit.Autostart;
import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Notifications;
import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.Tray;
import io.github.atharvashelkeatwork.jnativekit.TrayHandle;
import io.github.atharvashelkeatwork.jnativekit.TrayIcon;

class CapabilityReportTest {

    @Test
    void printsOneRowPerFeatureAndTheRemedy() {
        Capability available = new Capability(Capability.Status.AVAILABLE, Reason.NONE,
                "notify-send is on PATH", null);
        Capability unavailable = new Capability(Capability.Status.MISSING_DEPENDENCY,
                Reason.BACKEND_MISSING, "notify-send was not found", "Install libnotify-bin");
        Capability limited = new Capability(Capability.Status.UNSUPPORTED_ENVIRONMENT,
                Reason.NO_GRAPHICAL_SESSION, "the JVM is headless", "Run inside a desktop session");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CapabilityReport.print(new PrintStream(bytes, true, StandardCharsets.UTF_8), Platform.LINUX,
                new FakeNotifications(available), new FakeAutostart(unavailable),
                new FakeTray(limited));

        String text = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Platform: LINUX"), text);
        assertTrue(text.contains("Notifications"), text);
        assertTrue(text.contains("AVAILABLE"), text);
        assertTrue(text.contains("notify-send is on PATH"), text);
        assertTrue(text.contains("Autostart"), text);
        assertTrue(text.contains("MISSING_DEPENDENCY"), text);
        assertTrue(text.contains("Install libnotify-bin"), text);
        assertTrue(text.contains("Tray"), text);
        assertTrue(text.contains("NO_GRAPHICAL_SESSION"), text);
        assertTrue(text.contains("the JVM is headless"), text);
    }

    private static final class FakeNotifications implements Notifications {
        private final Capability capability;

        FakeNotifications(Capability capability) {
            this.capability = capability;
        }

        @Override
        public Capability capability() {
            return capability;
        }

        @Override
        public Result<Void> show(Notification notification) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeAutostart implements Autostart {
        private final Capability capability;

        FakeAutostart(Capability capability) {
            this.capability = capability;
        }

        @Override
        public Capability capability() {
            return capability;
        }

        @Override
        public Result<Void> enable(AutostartEntry entry) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Void> disable(String appId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Boolean> isEnabled(String appId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeTray implements Tray {
        private final Capability capability;

        FakeTray(Capability capability) {
            this.capability = capability;
        }

        @Override
        public Capability capability() {
            return capability;
        }

        @Override
        public Result<TrayHandle> open(TrayIcon icon) {
            throw new UnsupportedOperationException();
        }
    }
}
