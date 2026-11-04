package io.github.atharvashelkeatwork.jnativekit.demo;

import java.nio.file.Path;
import java.util.List;

import io.github.atharvashelkeatwork.jnativekit.Autostart;
import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Notifications;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.Tray;
import io.github.atharvashelkeatwork.jnativekit.TrayHandle;
import io.github.atharvashelkeatwork.jnativekit.TrayIcon;

/**
 * Shared test doubles for the demo tests. Demo-only; not published.
 */
final class TestDoubles {

    private TestDoubles() {
    }

    static AutostartEntry entry() {
        Path executable = Path.of(System.getProperty("java.io.tmpdir"))
                .resolve("jnativekit-demo-test.jar").toAbsolutePath();
        return new AutostartEntry(DemoAssets.appId(), DemoAssets.displayName(), executable,
                List.of("--autostart"));
    }

    static Capability available() {
        return new Capability(Capability.Status.AVAILABLE, Reason.NONE, "available", null);
    }

    static <T> Result<T> confirmed(T value, String provider, String detail) {
        return new Result<>(Result.Status.CONFIRMED, value, provider, Reason.NONE, detail);
    }

    static Result<Void> confirmedVoid(String provider, String detail) {
        return new Result<>(Result.Status.CONFIRMED, null, provider, Reason.NONE, detail);
    }

    static <T> Result<T> unavailable(Reason reason, String detail) {
        return new Result<>(Result.Status.UNAVAILABLE, null, null, reason, detail);
    }

    static final class FakeNotifications implements Notifications {
        private final Capability capability = available();
        Notification shown;
        Result<Void> result = confirmedVoid("test.notifications", "accepted");
        RuntimeException throwOnShow;

        @Override
        public Capability capability() {
            return capability;
        }

        @Override
        public Result<Void> show(Notification notification) {
            if (throwOnShow != null) {
                throw throwOnShow;
            }
            shown = notification;
            return result;
        }
    }

    static final class FakeAutostart implements Autostart {
        private final Capability capability = available();
        Result<Boolean> enabled = confirmed(false, "test.autostart", "absent");
        Result<Void> enableResult = confirmedVoid("test.autostart", "written");
        Result<Void> disableResult = confirmedVoid("test.autostart", "removed");
        AutostartEntry enabledEntry;
        String disabledAppId;

        @Override
        public Capability capability() {
            return capability;
        }

        @Override
        public Result<Void> enable(AutostartEntry entry) {
            enabledEntry = entry;
            return enableResult;
        }

        @Override
        public Result<Void> disable(String appId) {
            disabledAppId = appId;
            return disableResult;
        }

        @Override
        public Result<Boolean> isEnabled(String appId) {
            return enabled;
        }
    }

    static final class FakeTray implements Tray {
        private final Capability capability = available();
        Result<TrayHandle> openResult = unavailable(Reason.BACKEND_MISSING, "not configured");
        TrayIcon openedIcon;
        int openCount;

        @Override
        public Capability capability() {
            return capability;
        }

        @Override
        public Result<TrayHandle> open(TrayIcon icon) {
            openCount++;
            openedIcon = icon;
            return openResult;
        }
    }

    static final class FakeTrayHandle implements TrayHandle {
        boolean open = true;
        boolean closed;
        int closeCount;

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
            closed = true;
            closeCount++;
        }
    }
}
