package io.github.atharvashelkeatwork.jnativekit.demo;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.github.atharvashelkeatwork.jnativekit.Reason;

class DemoMenuTest {

    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 1, 2, 3};

    @Test
    void notificationCommandCallsTheFeature() {
        TestDoubles.FakeNotifications notifications = new TestDoubles.FakeNotifications();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoMenu menu = menu("1\n5\n", notifications, new TestDoubles.FakeAutostart(),
                new TestDoubles.FakeTray(), output);

        menu.run();

        assertNotNull(notifications.shown);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Notification: CONFIRMED"),
                output.toString());
    }

    @Test
    void trayCommandOpensAnIconAndQuitClosesIt() {
        TestDoubles.FakeTray tray = new TestDoubles.FakeTray();
        TestDoubles.FakeTrayHandle handle = new TestDoubles.FakeTrayHandle();
        tray.openResult = TestDoubles.confirmed(handle, "test.tray", "added");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoMenu menu = menu("3\n5\n", new TestDoubles.FakeNotifications(),
                new TestDoubles.FakeAutostart(), tray, output);

        menu.run();

        assertTrue(handle.closed);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Tray closed."),
                output.toString());
    }

    @Test
    void unknownCommandIsReported() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoMenu menu = menu("9\n5\n", new TestDoubles.FakeNotifications(),
                new TestDoubles.FakeAutostart(), new TestDoubles.FakeTray(), output);

        menu.run();

        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Unknown choice: 9"),
                output.toString());
    }

    @Test
    void blankLineIsIgnored() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoMenu menu = menu("\n5\n", new TestDoubles.FakeNotifications(),
                new TestDoubles.FakeAutostart(), new TestDoubles.FakeTray(), output);

        menu.run();

        assertTrue(output.toString(StandardCharsets.UTF_8).contains("Choose an action:"),
                output.toString());
    }

    @Test
    void endOfInputQuitsAndClosesTheTray() {
        TestDoubles.FakeTray tray = new TestDoubles.FakeTray();
        TestDoubles.FakeTrayHandle handle = new TestDoubles.FakeTrayHandle();
        tray.openResult = TestDoubles.confirmed(handle, "test.tray", "added");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoMenu menu = menu("3\n", new TestDoubles.FakeNotifications(),
                new TestDoubles.FakeAutostart(), tray, output);

        menu.run();

        assertTrue(handle.closed);
        assertFalse(handle.open);
    }

    @Test
    void unavailableAutostartDoesNotChangeTheEntry() {
        TestDoubles.FakeAutostart autostart = new TestDoubles.FakeAutostart();
        autostart.enabled = TestDoubles.unavailable(Reason.PERMISSION_DENIED, "no access");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DemoMenu menu = menu("2\n5\n", new TestDoubles.FakeNotifications(), autostart,
                new TestDoubles.FakeTray(), output);

        menu.run();

        assertNull(autostart.enabledEntry);
        assertNull(autostart.disabledAppId);
        assertTrue(output.toString(StandardCharsets.UTF_8).contains("state is unknown"),
                output.toString());
    }

    private static DemoMenu menu(String input, TestDoubles.FakeNotifications notifications,
            TestDoubles.FakeAutostart autostart, TestDoubles.FakeTray tray,
            ByteArrayOutputStream output) {
        PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8);
        DemoActions actions = new DemoActions(notifications, autostart, tray, PNG,
                TestDoubles.entry(), stream, stream);
        return new DemoMenu(actions, new BufferedReader(new StringReader(input)), stream);
    }
}
