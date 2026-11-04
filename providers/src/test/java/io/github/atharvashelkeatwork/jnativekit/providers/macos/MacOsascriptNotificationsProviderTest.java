package io.github.atharvashelkeatwork.jnativekit.providers.macos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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

class MacOsascriptNotificationsProviderTest {

    private static final Notification NOTIFICATION =
            Notification.of("Backup done", "42 files synced");

    private static final ExecutableFinder FOUND =
            name -> Optional.of(Path.of(System.getProperty("java.io.tmpdir")).resolve(name));
    private static final ExecutableFinder MISSING = name -> Optional.empty();

    private static final String SCRIPT_START = "on run argv";
    private static final String SCRIPT_DISPLAY =
            "display notification ((item 2 of argv) as text) with title ((item 1 of argv) as text)";
    private static final String SCRIPT_END = "end run";

    @Test
    void reportsItsIdentityAndPlatform() {
        MacOsascriptNotificationsProvider provider = provider(FOUND);

        assertEquals("macos.osascript", provider.id());
        assertTrue(provider.supports(Platform.MACOS));
        assertFalse(provider.supports(Platform.LINUX));
        assertFalse(provider.supports(Platform.WINDOWS));
        assertTrue(provider.priority() > 0);
    }

    @Test
    void capabilityIsAvailableWhenOsascriptIsPresent() {
        Capability capability = provider(FOUND).capability();

        assertEquals(Capability.Status.AVAILABLE, capability.status());
        assertEquals(Reason.NONE, capability.reason());
        assertNull(capability.remedy());
    }

    @Test
    void capabilityWithoutOsascriptNamesTheMissingDependency() {
        Capability capability = provider(MISSING).capability();

        assertEquals(Capability.Status.MISSING_DEPENDENCY, capability.status());
        assertEquals(Reason.BACKEND_MISSING, capability.reason());
        assertTrue(capability.detail().contains("osascript"));
    }

    @Test
    void showPassesTheTitleAndBodyAsArgumentsAfterTheScript() {
        FakeCommandRunner runner = new FakeCommandRunner();
        MacOsascriptNotificationsProvider provider = provider(FOUND, runner);

        Result<Void> result = provider.show(NOTIFICATION);

        assertEquals(Result.Status.DISPATCHED_UNVERIFIED, result.status());
        assertEquals(Reason.NONE, result.reason());
        assertEquals("macos.osascript", result.provider());
        assertNull(result.value());

        FakeCommandRunner.Invocation invocation = runner.lastInvocation();
        assertEquals(List.of(
                "osascript",
                "-e", SCRIPT_START,
                "-e", SCRIPT_DISPLAY,
                "-e", SCRIPT_END,
                "--",
                "Backup done",
                "42 files synced"), invocation.argv());
        assertEquals(Duration.ofSeconds(5), invocation.timeout());
    }

    @Test
    void hostileTitleAndBodyNeverEnterTheAppleScriptText() {
        String title = "-Title \"quoted\" \\ backslash $HOME %s 日本語";
        String body = "Body\nwith\r\nnewlines, \"quotes\", \\backslash and $(whoami)";
        FakeCommandRunner runner = new FakeCommandRunner();
        MacOsascriptNotificationsProvider provider = provider(FOUND, runner);

        Result<Void> result = provider.show(Notification.of(title, body));

        assertEquals(Result.Status.DISPATCHED_UNVERIFIED, result.status());
        List<String> argv = runner.lastInvocation().argv();
        assertEquals(title, argv.get(argv.size() - 2));
        assertEquals(body, argv.get(argv.size() - 1));
        for (int i = 0; i < argv.size() - 2; i++) {
            assertFalse(argv.get(i).contains(title));
            assertFalse(argv.get(i).contains(body));
        }
    }

    @Test
    void aNonZeroExitBecomesFailedBackendError() {
        FakeCommandRunner runner = new FakeCommandRunner()
                .enqueue(new CommandResult(1, "", "execution error: Not authorized\n", false));
        MacOsascriptNotificationsProvider provider = provider(FOUND, runner);

        Result<Void> result = provider.show(NOTIFICATION);

        assertEquals(Result.Status.FAILED, result.status());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertTrue(result.detail().contains("1"));
        assertTrue(result.detail().contains("Not authorized"));
    }

    @Test
    void aTimeoutBecomesFailedTimeout() {
        FakeCommandRunner runner = new FakeCommandRunner()
                .enqueue(new CommandResult(
                        CommandResult.LAUNCH_FAILED_EXIT_CODE, "", "", true));
        MacOsascriptNotificationsProvider provider = provider(FOUND, runner);

        Result<Void> result = provider.show(NOTIFICATION);

        assertEquals(Result.Status.FAILED, result.status());
        assertEquals(Reason.TIMEOUT, result.reason());
    }

    @Test
    void aLaunchFailureBecomesFailedBackendError() {
        FakeCommandRunner runner = new FakeCommandRunner()
                .enqueue(new CommandResult(
                        CommandResult.LAUNCH_FAILED_EXIT_CODE, "", "No such file", false));
        MacOsascriptNotificationsProvider provider = provider(FOUND, runner);

        Result<Void> result = provider.show(NOTIFICATION);

        assertEquals(Result.Status.FAILED, result.status());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertTrue(result.detail().contains("could not be started"));
    }

    @Test
    void showDoesNotStartTheToolWhenOsascriptIsMissing() {
        FakeCommandRunner runner = new FakeCommandRunner();
        MacOsascriptNotificationsProvider provider = provider(MISSING, runner);

        Result<Void> result = provider.show(NOTIFICATION);

        assertEquals(Result.Status.UNAVAILABLE, result.status());
        assertEquals(Reason.BACKEND_MISSING, result.reason());
        assertTrue(result.detail().contains("osascript"));
        assertTrue(result.detail().contains("Restore"));
        assertTrue(runner.invocations().isEmpty());
    }

    @Test
    void rejectsANullNotificationWithoutStartingTheTool() {
        FakeCommandRunner runner = new FakeCommandRunner();
        MacOsascriptNotificationsProvider provider = provider(FOUND, runner);

        assertThrows(NullPointerException.class, () -> provider.show(null));
        assertTrue(runner.invocations().isEmpty());
    }

    private static MacOsascriptNotificationsProvider provider(ExecutableFinder executables) {
        return provider(executables, new FakeCommandRunner());
    }

    private static MacOsascriptNotificationsProvider provider(
            ExecutableFinder executables, FakeCommandRunner runner) {
        return new MacOsascriptNotificationsProvider(runner, executables);
    }
}
