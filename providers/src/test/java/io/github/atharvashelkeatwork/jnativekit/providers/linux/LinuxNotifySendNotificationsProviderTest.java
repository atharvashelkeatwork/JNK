package io.github.atharvashelkeatwork.jnativekit.providers.linux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Notification;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.CommandResult;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.ExecutableFinder;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.FakeCommandRunner;

class LinuxNotifySendNotificationsProviderTest {

    private static final Notification NOTIFICATION =
            Notification.of("Backup done", "42 files synced");

    private static final ExecutableFinder FOUND =
            name -> Optional.of(Path.of(System.getProperty("java.io.tmpdir")).resolve(name));
    private static final ExecutableFinder MISSING = name -> Optional.empty();

    @Test
    void reportsItsIdentityAndPlatform() {
        LinuxNotifySendNotificationsProvider provider = provider(Map.of("DISPLAY", ":0"), FOUND);

        assertEquals("linux.notify-send", provider.id());
        assertTrue(provider.supports(Platform.LINUX));
        assertFalse(provider.supports(Platform.WINDOWS));
        assertFalse(provider.supports(Platform.MACOS));
        assertTrue(provider.priority() > 0);
    }

    @Test
    void capabilityIsAvailableWithADisplayAndTheTool() {
        Capability capability = provider(Map.of("DISPLAY", ":0"), FOUND).capability();

        assertEquals(Capability.Status.AVAILABLE, capability.status());
        assertEquals(Reason.NONE, capability.reason());
        assertNull(capability.remedy());
    }

    @Test
    void waylandDisplayIsEnoughForAGraphicalSession() {
        Capability capability = provider(Map.of("WAYLAND_DISPLAY", "wayland-0"), FOUND).capability();

        assertEquals(Capability.Status.AVAILABLE, capability.status());
    }

    @Test
    void capabilityWithoutAGraphicalSessionExplainsTheProblem() {
        Capability capability = provider(Map.of(), FOUND).capability();

        assertEquals(Capability.Status.UNSUPPORTED_ENVIRONMENT, capability.status());
        assertEquals(Reason.NO_GRAPHICAL_SESSION, capability.reason());
        assertTrue(capability.remedy().toLowerCase().contains("graphical"));
    }

    @Test
    void capabilityWithABlankDisplayIsTreatedAsNoSession() {
        Capability capability = provider(
                Map.of("DISPLAY", "   ", "WAYLAND_DISPLAY", ""), FOUND).capability();

        assertEquals(Reason.NO_GRAPHICAL_SESSION, capability.reason());
    }

    @Test
    void capabilityWithoutNotifySendNamesTheMissingDependency() {
        Capability capability = provider(Map.of("DISPLAY", ":0"), MISSING).capability();

        assertEquals(Capability.Status.MISSING_DEPENDENCY, capability.status());
        assertEquals(Reason.BACKEND_MISSING, capability.reason());
        assertTrue(capability.remedy().contains("libnotify-bin"));
    }

    @Test
    void showPassesTheTitleAndBodyAsSafeArguments() {
        FakeCommandRunner runner = new FakeCommandRunner();
        LinuxNotifySendNotificationsProvider provider =
                provider(Map.of("DISPLAY", ":0"), FOUND, runner);

        Result<Void> result = provider.show(NOTIFICATION);

        assertEquals(Result.Status.DISPATCHED_UNVERIFIED, result.status());
        assertEquals(Reason.NONE, result.reason());
        assertEquals("linux.notify-send", result.provider());
        assertNull(result.value());

        FakeCommandRunner.Invocation invocation = runner.lastInvocation();
        assertEquals(List.of("notify-send", "--", "Backup done", "42 files synced"),
                invocation.argv());
        assertEquals(Duration.ofSeconds(5), invocation.timeout());
    }

    @Test
    void hostileTitleAndBodyAreNeverParsedAsOptionsOrScript() {
        String title = "-Title \"quoted\" \\ backslash $HOME %s 日本語";
        String body = "Body\nwith\r\nnewlines, \"quotes\", \\backslash and $DISPLAY";
        FakeCommandRunner runner = new FakeCommandRunner();
        LinuxNotifySendNotificationsProvider provider =
                provider(Map.of("DISPLAY", ":0"), FOUND, runner);

        Result<Void> result = provider.show(Notification.of(title, body));

        assertEquals(Result.Status.DISPATCHED_UNVERIFIED, result.status());
        assertEquals(List.of("notify-send", "--", title, body),
                runner.lastInvocation().argv());
    }

    @Test
    void aNonZeroExitBecomesFailedBackendError() {
        FakeCommandRunner runner = new FakeCommandRunner()
                .enqueue(new CommandResult(3, "", "notification service denied\n", false));
        LinuxNotifySendNotificationsProvider provider =
                provider(Map.of("DISPLAY", ":0"), FOUND, runner);

        Result<Void> result = provider.show(NOTIFICATION);

        assertEquals(Result.Status.FAILED, result.status());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertNull(result.value());
        assertTrue(result.detail().contains("3"));
        assertTrue(result.detail().contains("notification service denied"));
    }

    @Test
    void aTimeoutBecomesFailedTimeout() {
        FakeCommandRunner runner = new FakeCommandRunner()
                .enqueue(new CommandResult(
                        CommandResult.LAUNCH_FAILED_EXIT_CODE, "", "", true));
        LinuxNotifySendNotificationsProvider provider =
                provider(Map.of("DISPLAY", ":0"), FOUND, runner);

        Result<Void> result = provider.show(NOTIFICATION);

        assertEquals(Result.Status.FAILED, result.status());
        assertEquals(Reason.TIMEOUT, result.reason());
    }

    @Test
    void aLaunchFailureBecomesFailedBackendError() {
        FakeCommandRunner runner = new FakeCommandRunner()
                .enqueue(new CommandResult(
                        CommandResult.LAUNCH_FAILED_EXIT_CODE, "", "No such file", false));
        LinuxNotifySendNotificationsProvider provider =
                provider(Map.of("DISPLAY", ":0"), FOUND, runner);

        Result<Void> result = provider.show(NOTIFICATION);

        assertEquals(Result.Status.FAILED, result.status());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertTrue(result.detail().contains("could not be started"));
        assertTrue(result.detail().contains("No such file"));
    }

    @Test
    void showDoesNotStartTheToolWhenTheCapabilityIsUnavailable() {
        FakeCommandRunner runner = new FakeCommandRunner();
        LinuxNotifySendNotificationsProvider noSession = provider(Map.of(), FOUND, runner);
        LinuxNotifySendNotificationsProvider noTool = provider(Map.of("DISPLAY", ":0"), MISSING, runner);

        Result<Void> first = noSession.show(NOTIFICATION);
        Result<Void> second = noTool.show(NOTIFICATION);

        assertEquals(Result.Status.UNAVAILABLE, first.status());
        assertEquals(Reason.NO_GRAPHICAL_SESSION, first.reason());
        assertEquals(Result.Status.UNAVAILABLE, second.status());
        assertEquals(Reason.BACKEND_MISSING, second.reason());
        assertTrue(second.detail().contains("libnotify-bin"));
        assertTrue(runner.invocations().isEmpty());
    }

    @Test
    void rejectsANullNotificationWithoutStartingTheTool() {
        FakeCommandRunner runner = new FakeCommandRunner();
        LinuxNotifySendNotificationsProvider provider =
                provider(Map.of("DISPLAY", ":0"), FOUND, runner);

        assertThrows(NullPointerException.class, () -> provider.show(null));
        assertTrue(runner.invocations().isEmpty());
    }

    private static LinuxNotifySendNotificationsProvider provider(
            Map<String, String> environment, ExecutableFinder executables) {
        return provider(environment, executables, new FakeCommandRunner());
    }

    private static LinuxNotifySendNotificationsProvider provider(
            Map<String, String> environment,
            ExecutableFinder executables,
            FakeCommandRunner runner) {
        return new LinuxNotifySendNotificationsProvider(runner, executables, environment);
    }
}
