package io.github.atharvashelkeatwork.jnativekit.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

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

class DemoActionsTest {

    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3};

    @Test
    void sendsOneNotificationAndPrintsTheResult() {
        FakeNotifications notifications = new FakeNotifications();
        notifications.result = confirmedVoid("test.notifications", "accepted");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoActions actions = actions(notifications, new FakeAutostart(), new FakeTray(), PNG, output);

        actions.sendNotification();

        assertNotNull(notifications.shown);
        assertEquals("jnativekit demo", notifications.shown.title());
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Notification: CONFIRMED"), text);
        assertTrue(text.contains("provider=test.notifications"), text);
        assertTrue(text.contains("detail=accepted"), text);
    }

    @Test
    void notificationExceptionIsReportedInsteadOfEscaping() {
        FakeNotifications notifications = new FakeNotifications();
        notifications.throwOnShow = new IllegalStateException("boom");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoActions actions = actions(notifications, new FakeAutostart(), new FakeTray(), PNG, output);

        actions.sendNotification();

        assertTrue(output.toString(StandardCharsets.UTF_8).contains("threw unexpectedly: java.lang.IllegalStateException: boom"));
    }

    @Test
    void togglesAutostartOnWhenCurrentlyDisabled() {
        FakeAutostart autostart = new FakeAutostart();
        autostart.enabled = confirmed(false, "test.autostart", "absent");
        autostart.enableResult = confirmedVoid("test.autostart", "written and read back");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoActions actions = actions(new FakeNotifications(), autostart, new FakeTray(), PNG, output);

        actions.toggleAutostart();

        assertEquals(actions.autostartEntry(), autostart.enabledEntry);
        assertNull(autostart.disabledAppId);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Autostart enable: CONFIRMED"), output.toString());
    }

    @Test
    void togglesAutostartOffWhenCurrentlyEnabled() {
        FakeAutostart autostart = new FakeAutostart();
        autostart.enabled = confirmed(true, "test.autostart", "present");
        autostart.disableResult = confirmedVoid("test.autostart", "removed and read back");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoActions actions = actions(new FakeNotifications(), autostart, new FakeTray(), PNG, output);

        actions.toggleAutostart();

        assertEquals(DemoAssets.appId(), autostart.disabledAppId);
        assertNull(autostart.enabledEntry);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Autostart disable: CONFIRMED"), output.toString());
    }

    @Test
    void doesNotToggleWhenTheAutostartStateIsUnknown() {
        FakeAutostart autostart = new FakeAutostart();
        autostart.enabled = unavailable(Reason.BACKEND_MISSING, "store could not be read");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoActions actions = actions(new FakeNotifications(), autostart, new FakeTray(), PNG, output);

        actions.toggleAutostart();

        assertNull(autostart.enabledEntry);
        assertNull(autostart.disabledAppId);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("state is unknown"), output.toString());
    }

    @Test
    void opensOneTrayIconAndQuitClosesIt() {
        FakeTray tray = new FakeTray();
        FakeTrayHandle handle = new FakeTrayHandle();
        tray.openResult = confirmed(handle, "test.tray", "added");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoActions actions = actions(new FakeNotifications(), new FakeAutostart(), tray, PNG, output);

        actions.openTray();

        assertTrue(actions.isTrayOpen());
        assertNotNull(tray.openedIcon);
        assertEquals("Quit", tray.openedIcon.menu().get(0).label());

        tray.openedIcon.menu().get(0).action().run();

        assertFalse(actions.isTrayOpen());
        assertTrue(handle.closed);
        assertEquals(1, handle.closeCount);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Tray closed."), output.toString());
    }

    @Test
    void openingTheTrayTwiceReusesTheExistingIcon() {
        FakeTray tray = new FakeTray();
        tray.openResult = confirmed(new FakeTrayHandle(), "test.tray", "added");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoActions actions = actions(new FakeNotifications(), new FakeAutostart(), tray, PNG, output);

        actions.openTray();
        actions.openTray();

        assertEquals(1, tray.openCount);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("already open"), output.toString());
    }

    @Test
    void trayUnavailableIsPrintedAndDoesNotCreateAHandle() {
        FakeTray tray = new FakeTray();
        tray.openResult = unavailable(Reason.BACKEND_MISSING, "SystemTray is not supported");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoActions actions = actions(new FakeNotifications(), new FakeAutostart(), tray, PNG, output);

        actions.openTray();

        assertFalse(actions.isTrayOpen());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Tray open: UNAVAILABLE"), output.toString());
    }

    @Test
    void trayWithoutAnIconResourceIsReported() {
        FakeTray tray = new FakeTray();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoActions actions = actions(new FakeNotifications(), new FakeAutostart(), tray, null, output);

        actions.openTray();

        assertEquals(0, tray.openCount);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("resource is missing"), output.toString());
    }

    @Test
    void autostartModeReturnsWhenTheTrayCannotOpen() {
        FakeNotifications notifications = new FakeNotifications();
        notifications.result = confirmedVoid("test.notifications", "accepted");
        FakeTray tray = new FakeTray();
        tray.openResult = unavailable(Reason.NO_GRAPHICAL_SESSION, "headless");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoActions actions = actions(notifications, new FakeAutostart(), tray, PNG, output);

        actions.runAutostartMode();

        assertNotNull(notifications.shown);
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Tray did not open"), text);
        assertFalse(actions.isTrayOpen());
    }

    @Test
    void autostartModeWaitsForQuitAndClosesTheTray() throws InterruptedException {
        FakeNotifications notifications = new FakeNotifications();
        notifications.result = confirmedVoid("test.notifications", "accepted");
        FakeTray tray = new FakeTray();
        FakeTrayHandle handle = new FakeTrayHandle();
        tray.openResult = confirmed(handle, "test.tray", "added");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoActions actions = actions(notifications, new FakeAutostart(), tray, PNG, output);

        Thread runner = new Thread(actions::runAutostartMode, "demo-autostart-test");
        runner.setDaemon(true);
        runner.start();

        assertTrue(tray.opened.await(5, TimeUnit.SECONDS), "the tray should open");
        tray.openedIcon.menu().get(0).action().run();
        runner.join(5000);

        assertFalse(runner.isAlive(), "the autostart mode should return after Quit");
        assertTrue(handle.closed);
        assertFalse(actions.isTrayOpen());
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Tray closed."));
    }

    private static DemoActions actions(FakeNotifications notifications, FakeAutostart autostart,
            FakeTray tray, byte[] png, ByteArrayOutputStream output) {
        PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8);
        return new DemoActions(notifications, autostart, tray, png, entry(), stream, stream);
    }

    private static AutostartEntry entry() {
        Path executable = Path.of(System.getProperty("java.io.tmpdir"))
                .resolve("jnativekit-demo-test.jar").toAbsolutePath();
        return new AutostartEntry(DemoAssets.appId(), DemoAssets.displayName(), executable,
                List.of("--autostart"));
    }

    private static <T> Result<T> confirmed(T value, String provider, String detail) {
        return new Result<>(Result.Status.CONFIRMED, value, provider, Reason.NONE, detail);
    }

    private static Result<Void> confirmedVoid(String provider, String detail) {
        return new Result<>(Result.Status.CONFIRMED, null, provider, Reason.NONE, detail);
    }

    private static <T> Result<T> unavailable(Reason reason, String detail) {
        return new Result<>(Result.Status.UNAVAILABLE, null, null, reason, detail);
    }

    private static Capability available() {
        return new Capability(Capability.Status.AVAILABLE, Reason.NONE, "available", null);
    }

    private static final class FakeNotifications implements Notifications {
        private final Capability capability = available();
        private Notification shown;
        private Result<Void> result = confirmedVoid("test.notifications", "accepted");
        private RuntimeException throwOnShow;

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

    private static final class FakeAutostart implements Autostart {
        private final Capability capability = available();
        private Result<Boolean> enabled = confirmed(false, "test.autostart", "absent");
        private Result<Void> enableResult = confirmedVoid("test.autostart", "written");
        private Result<Void> disableResult = confirmedVoid("test.autostart", "removed");
        private AutostartEntry enabledEntry;
        private String disabledAppId;

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

    private static final class FakeTray implements Tray {
        private final Capability capability = new Capability(Capability.Status.AVAILABLE,
                Reason.NONE, "available", null);
        private Result<TrayHandle> openResult = unavailable(Reason.BACKEND_MISSING, "not configured");
        private TrayIcon openedIcon;
        private int openCount;
        private final CountDownLatch opened = new CountDownLatch(1);

        @Override
        public Capability capability() {
            return capability;
        }

        @Override
        public Result<TrayHandle> open(TrayIcon icon) {
            openCount++;
            openedIcon = icon;
            opened.countDown();
            return openResult;
        }
    }

    private static final class FakeTrayHandle implements TrayHandle {
        private boolean open = true;
        private boolean closed;
        private int closeCount;

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
