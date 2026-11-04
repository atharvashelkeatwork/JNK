package io.github.atharvashelkeatwork.jnativekit.providers.windows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.internal.GuardedAutostart;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.autostart.WindowsRegFile;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.CommandResult;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.CommandRunner;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.ExecutableFinder;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.FakeCommandRunner;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.text.WindowsCommandLine;

/**
 * Tier T2: the Windows {@code reg.exe} provider against {@link FakeCommandRunner}.
 *
 * <p>These tests prove the exact argument lists, the read-back checks and every failure mapping.
 * They do not prove that a real {@code reg.exe} prints the assumed output or that Windows starts
 * the application at login; the T3 integration test and the T4 manual checklist must confirm
 * those.
 */
class WindowsRegAutostartProviderTest {

    private static final String RUN_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String RUN_KEY_IN_FILE =
            "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String NOT_FOUND =
            "ERROR: The system was unable to find the specified registry key or value.";

    private final FakeCommandRunner runner = new FakeCommandRunner();

    @Test
    void reportsItsIdentity(@TempDir Path home) {
        WindowsRegAutostartProvider provider = provider(home, true);

        assertEquals("windows.reg-exe", provider.id());
        assertTrue(provider.supports(Platform.WINDOWS));
        assertFalse(provider.supports(Platform.LINUX));
        assertFalse(provider.supports(Platform.MACOS));
        assertEquals(100, provider.priority());
    }

    @Test
    void capabilityRequiresRegExe(@TempDir Path home) {
        Capability missing = provider(home, false).capability();
        Capability present = provider(home, true).capability();

        assertEquals(Capability.Status.MISSING_DEPENDENCY, missing.status());
        assertEquals(Reason.BACKEND_MISSING, missing.reason());
        assertNotNull(missing.remedy());
        assertEquals(Capability.Status.AVAILABLE, present.status());
        assertEquals(Reason.NONE, present.reason());
    }

    @Test
    void enableImportsThenQueriesTheExactArguments(@TempDir Path home) {
        AutostartEntry entry = entry(home, "com.example.app", List.of("--flag", "two words"));
        String data = WindowsCommandLine.build(entry.executable(), entry.arguments());
        runner.enqueue(success(), success(queryOutput(entry.appId(), data)));

        Result<Void> result = provider(home, true).enable(entry);

        assertTrue(result.isConfirmed(), result::detail);
        assertEquals("windows.reg-exe", result.provider());
        List<FakeCommandRunner.Invocation> calls = runner.invocations();
        assertEquals(2, calls.size());
        List<String> importArgv = calls.get(0).argv();
        assertEquals(3, importArgv.size());
        assertEquals("reg.exe", importArgv.get(0));
        assertEquals("import", importArgv.get(1));
        assertTrue(importArgv.get(2).endsWith(".reg"), importArgv.get(2));
        assertEquals(List.of("reg.exe", "query", RUN_KEY, "/v", entry.appId()),
                calls.get(1).argv());
        assertEquals(Duration.ofSeconds(5), calls.get(0).timeout());
        assertEquals(Duration.ofSeconds(5), calls.get(1).timeout());
    }

    @Test
    void enableWritesHostileArgumentsIntoTheImportedRegFile(@TempDir Path home) {
        List<String> arguments = List.of(
                "two words",
                "a\"quote",
                "100%PATH%",
                "$HOME",
                "back\\slash\\",
                "\u00dcn\u00efc\u00f8d\u00e9 \u65e5\u672c",
                "long-" + "x".repeat(500));
        AutostartEntry entry = entry(home, "com.example.hostile", arguments);
        String data = WindowsCommandLine.build(entry.executable(), arguments);
        List<String> importedFiles = new ArrayList<>();
        CommandRunner capturing = (argv, timeout) -> {
            if (argv.size() == 3 && argv.get(0).equals("reg.exe")
                    && argv.get(1).equals("import")) {
                try {
                    importedFiles.add(Files.readString(
                            Path.of(argv.get(2)), StandardCharsets.UTF_16));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return success();
            }
            return success(queryOutput(entry.appId(), data));
        };
        WindowsRegAutostartProvider provider =
                new WindowsRegAutostartProvider(capturing, finder(true));

        Result<Void> result = provider.enable(entry);

        assertTrue(result.isConfirmed(), result::detail);
        assertEquals(1, importedFiles.size());
        assertEquals(WindowsRegFile.render(RUN_KEY_IN_FILE, entry.appId(), data),
                importedFiles.get(0));
        assertTrue(data.startsWith(WindowsCommandLine.quote(entry.executable().toString())), data);
    }

    @Test
    void enableDeletesTheTemporaryFileWhenTheImportFails(@TempDir Path home) {
        runner.enqueue(failure(1, "Invalid syntax"));

        Result<Void> result = provider(home, true)
                .enable(entry(home, "com.example.app", List.of("two words")));

        assertTrue(result.isFailed(), result.toString());
        Path temporary = Path.of(runner.invocations().get(0).argv().get(2));
        assertFalse(Files.exists(temporary), temporary.toString());
    }

    @Test
    void enableFailsWhenImportTimesOut(@TempDir Path home) {
        runner.enqueue(timeout());

        Result<Void> result = provider(home, true)
                .enable(entry(home, "com.example.app", List.of()));

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.TIMEOUT, result.reason());
        assertEquals(1, runner.invocations().size());
    }

    @Test
    void enableFailsWhenImportCannotStart(@TempDir Path home) {
        runner.enqueue(launchFailure("cannot start"));
        Result<Void> result = provider(home, true).enable(entry(home, "com.example.app", List.of()));

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertTrue(result.detail().contains("reg.exe import"), result.detail());
    }

    @Test
    void enableFailsWhenImportExitsNonZero(@TempDir Path home) {
        runner.enqueue(failure(1, "Access is denied."));
        Result<Void> result = provider(home, true).enable(entry(home, "com.example.app", List.of()));

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
        assertTrue(result.detail().contains("Access is denied"), result.detail());
    }

    @Test
    void enableFailsWhenQueryTimesOut(@TempDir Path home) {
        runner.enqueue(success(), timeout());
        Result<Void> result = provider(home, true).enable(entry(home, "com.example.app", List.of()));

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.TIMEOUT, result.reason());
    }

    @Test
    void enableFailsWhenQueryCannotStart(@TempDir Path home) {
        runner.enqueue(success(), launchFailure("nope"));
        Result<Void> result = provider(home, true).enable(entry(home, "com.example.app", List.of()));

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
    }

    @Test
    void enableReportsAVanishedValueAsVerificationFailure(@TempDir Path home) {
        runner.enqueue(success(), failure(1, NOT_FOUND));
        Result<Void> result = provider(home, true).enable(entry(home, "com.example.app", List.of()));

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.VERIFICATION_FAILED, result.reason());
    }

    @Test
    void enableMapsAnUnknownQueryErrorToIoError(@TempDir Path home) {
        runner.enqueue(success(), failure(1, "Access is denied."));
        Result<Void> result = provider(home, true).enable(entry(home, "com.example.app", List.of()));

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.IO_ERROR, result.reason());
    }

    @Test
    void enableReportsMissingReadBackAsVerificationFailure(@TempDir Path home) {
        runner.enqueue(success(), success(RUN_KEY + "\r\n"));
        Result<Void> result = provider(home, true).enable(entry(home, "com.example.app", List.of()));

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.VERIFICATION_FAILED, result.reason());
    }

    @Test
    void enableReportsMismatchedReadBackAsVerificationFailure(@TempDir Path home) {
        AutostartEntry entry = entry(home, "com.example.app", List.of("--flag"));
        runner.enqueue(success(), success(queryOutput(entry.appId(), "\"C:\\Other\\app.exe\"")));

        Result<Void> result = provider(home, true).enable(entry);

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.VERIFICATION_FAILED, result.reason());
    }

    @Test
    void disableIsConfirmedWhenTheValueIsAbsent(@TempDir Path home) {
        runner.enqueue(failure(1, NOT_FOUND));

        Result<Void> result = provider(home, true).disable("com.example.app");

        assertTrue(result.isConfirmed(), result::detail);
        assertEquals(1, runner.invocations().size());
    }

    @Test
    void disableDeletesThenVerifiesTheValueIsGone(@TempDir Path home) {
        String appId = "com.example.app";
        runner.enqueue(
                success(queryOutput(appId, "\"C:\\App\\app.exe\"")),
                success(),
                failure(1, NOT_FOUND));

        Result<Void> result = provider(home, true).disable(appId);

        assertTrue(result.isConfirmed(), result::detail);
        List<FakeCommandRunner.Invocation> calls = runner.invocations();
        assertEquals(3, calls.size());
        assertEquals(List.of("reg.exe", "query", RUN_KEY, "/v", appId), calls.get(0).argv());
        assertEquals(List.of("reg.exe", "delete", RUN_KEY, "/v", appId, "/f"), calls.get(1).argv());
        assertEquals(List.of("reg.exe", "query", RUN_KEY, "/v", appId), calls.get(2).argv());
    }

    @Test
    void disableFailsWhenQueryTimesOut(@TempDir Path home) {
        runner.enqueue(timeout());

        Result<Void> result = provider(home, true).disable("com.example.app");

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.TIMEOUT, result.reason());
    }

    @Test
    void disableFailsWhenQueryCannotStart(@TempDir Path home) {
        runner.enqueue(launchFailure("nope"));

        Result<Void> result = provider(home, true).disable("com.example.app");

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
    }

    @Test
    void disableMapsAnUnknownQueryErrorToIoError(@TempDir Path home) {
        runner.enqueue(failure(1, "Access is denied."));

        Result<Void> result = provider(home, true).disable("com.example.app");

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.IO_ERROR, result.reason());
    }

    @Test
    void disableFailsWhenQuerySucceedsWithoutTheValue(@TempDir Path home) {
        runner.enqueue(success(RUN_KEY + "\r\n"));

        Result<Void> result = provider(home, true).disable("com.example.app");

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.IO_ERROR, result.reason());
        assertEquals(1, runner.invocations().size());
    }

    @Test
    void disableFailsWhenDeleteTimesOut(@TempDir Path home) {
        runner.enqueue(
                success(queryOutput("com.example.app", "\"C:\\App\\app.exe\"")),
                timeout());

        Result<Void> result = provider(home, true).disable("com.example.app");

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.TIMEOUT, result.reason());
    }

    @Test
    void disableFailsWhenDeleteExitsNonZero(@TempDir Path home) {
        runner.enqueue(
                success(queryOutput("com.example.app", "\"C:\\App\\app.exe\"")),
                failure(1, "Access is denied."));

        Result<Void> result = provider(home, true).disable("com.example.app");

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
    }

    @Test
    void disableFailsWhenTheValueSurvivesTheDelete(@TempDir Path home) {
        runner.enqueue(
                success(queryOutput("com.example.app", "\"C:\\App\\app.exe\"")),
                success(),
                success(queryOutput("com.example.app", "\"C:\\App\\app.exe\"")));

        Result<Void> result = provider(home, true).disable("com.example.app");

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.VERIFICATION_FAILED, result.reason());
    }

    @Test
    void isEnabledIsFalseForTheEnglishNotFoundMessage(@TempDir Path home) {
        runner.enqueue(failure(1, NOT_FOUND));

        Result<Boolean> result = provider(home, true).isEnabled("com.example.app");

        assertTrue(result.isConfirmed(), result::detail);
        assertEquals(Boolean.FALSE, result.value());
    }

    @Test
    void isEnabledIsTrueWhenTheValueIsPresent(@TempDir Path home) {
        runner.enqueue(success(queryOutput("com.example.app", "\"C:\\App\\app.exe\"")));

        Result<Boolean> result = provider(home, true).isEnabled("com.example.app");

        assertTrue(result.isConfirmed(), result::detail);
        assertEquals(Boolean.TRUE, result.value());
    }

    @Test
    void isEnabledNeverReportsAnUnreadableStoreAsConfirmedFalse(@TempDir Path home) {
        runner.enqueue(failure(1, "ERROR: Access is denied."));

        Result<Boolean> result = provider(home, true).isEnabled("com.example.app");

        assertTrue(result.isFailed(), "unreadable store must not be CONFIRMED(false): " + result);
        assertEquals(Reason.IO_ERROR, result.reason());
        assertFalse(result.isConfirmed());
    }

    @Test
    void isEnabledReportsQuerySuccessWithoutTheValueAsFailed(@TempDir Path home) {
        runner.enqueue(success(RUN_KEY + "\r\n"));

        Result<Boolean> result = provider(home, true).isEnabled("com.example.app");

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.IO_ERROR, result.reason());
    }

    @Test
    void isEnabledFailsOnTimeout(@TempDir Path home) {
        runner.enqueue(timeout());

        Result<Boolean> result = provider(home, true).isEnabled("com.example.app");

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.TIMEOUT, result.reason());
    }

    @Test
    void isEnabledFailsWhenRegCannotStart(@TempDir Path home) {
        runner.enqueue(launchFailure("nope"));

        Result<Boolean> result = provider(home, true).isEnabled("com.example.app");

        assertTrue(result.isFailed(), result.toString());
        assertEquals(Reason.BACKEND_ERROR, result.reason());
    }

    @Test
    void validatesCallerInput(@TempDir Path home) {
        WindowsRegAutostartProvider provider = provider(home, true);

        assertThrows(NullPointerException.class, () -> provider.enable(null));
        assertThrows(IllegalArgumentException.class, () -> provider.disable("../escape"));
        assertThrows(IllegalArgumentException.class, () -> provider.isEnabled("bad id"));
        assertThrows(NullPointerException.class, () -> provider.isEnabled(null));
        assertThrows(NullPointerException.class, () -> provider.disable(null));
    }

    @Test
    void guardedFacadeConvertsAnUnexpectedRuntimeException(@TempDir Path home) {
        CommandRunner throwing = (argv, timeout) -> {
            throw new IllegalStateException("boom");
        };
        GuardedAutostart guarded = new GuardedAutostart(
                new WindowsRegAutostartProvider(throwing, finder(true)));

        Result<Boolean> isEnabled = guarded.isEnabled("com.example.app");

        assertTrue(isEnabled.isFailed(), isEnabled.toString());
        assertEquals(Reason.BACKEND_ERROR, isEnabled.reason());
    }

    private WindowsRegAutostartProvider provider(Path home, boolean regPresent) {
        return new WindowsRegAutostartProvider(runner, finder(regPresent));
    }

    private static ExecutableFinder finder(boolean present) {
        return name -> present
                ? Optional.of(Path.of("reg.exe"))
                : Optional.empty();
    }

    private AutostartEntry entry(Path home, String appId, List<String> arguments) {
        return new AutostartEntry(appId, "Example App",
                home.resolve("Program Files").resolve("App").resolve("app.exe"), arguments);
    }

    private static CommandResult success() {
        return new CommandResult(0, "", "", false);
    }

    private static CommandResult success(String stdout) {
        return new CommandResult(0, stdout, "", false);
    }

    private static CommandResult failure(int exitCode, String stderr) {
        return new CommandResult(exitCode, "", stderr, false);
    }

    private static CommandResult timeout() {
        return new CommandResult(CommandResult.LAUNCH_FAILED_EXIT_CODE, "", "", true);
    }

    private static CommandResult launchFailure(String stderr) {
        return new CommandResult(CommandResult.LAUNCH_FAILED_EXIT_CODE, "", stderr, false);
    }

    private static String queryOutput(String appId, String data) {
        return "\r\n" + RUN_KEY + "\r\n    " + appId + "    REG_SZ    " + data + "\r\n";
    }
}
