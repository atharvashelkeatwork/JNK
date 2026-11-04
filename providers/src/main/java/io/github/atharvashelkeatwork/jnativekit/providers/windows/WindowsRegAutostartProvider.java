package io.github.atharvashelkeatwork.jnativekit.providers.windows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.internal.Validation;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.autostart.AutostartResults;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.autostart.WindowsRegFile;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.autostart.WindowsRegQuery;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.CommandResult;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.CommandRunner;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.ExecutableFinder;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.PathExecutableFinder;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.exec.ProcessCommandRunner;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.text.WindowsCommandLine;
import io.github.atharvashelkeatwork.jnativekit.spi.AutostartProvider;

/**
 * Windows autostart through a per-user value in the {@code Run} registry key.
 *
 * <p>The value lives in {@code HKCU\Software\Microsoft\Windows\CurrentVersion\Run}, is named after
 * the {@code appId} and is written as {@code REG_SZ} with a {@code CommandLineToArgvW}-quoted
 * command line. The value is written by importing a temporary {@code .reg} file, because a quoted
 * command line cannot be passed safely as the {@code /d} value through Java's argument quoting;
 * the temporary file is deleted afterwards. Every call to {@code reg.exe} passes each token as a
 * separate argument-list entry, so no shell is involved. {@code enable} queries the value back and
 * compares its data before reporting {@link Result.Status#CONFIRMED}.
 *
 * <p>Windows lets the user disable a Run entry in Settings &gt; Apps &gt; Startup, so
 * {@code CONFIRMED} means the value is present and readable, not that Windows will definitely
 * start the application.
 *
 * <p>The "not found" state is recognised only from the English {@code reg.exe} message. On a
 * localized Windows a missing value is reported as {@code FAILED(IO_ERROR)} instead of a false
 * {@code CONFIRMED(false)}.
 */
public final class WindowsRegAutostartProvider implements AutostartProvider {

    private static final String ID = "windows.reg-exe";
    private static final String REG = "reg.exe";
    private static final String RUN_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    /** The same key with the full hive name, the form a {@code .reg} section header expects. */
    private static final String RUN_KEY_IN_FILE =
            "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    private static final int PRIORITY = 100;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final System.Logger LOG =
            System.getLogger(WindowsRegAutostartProvider.class.getName());

    private final CommandRunner runner;
    private final ExecutableFinder executables;

    /**
     * Creates the provider with the real command runner and the live {@code PATH}.
     */
    public WindowsRegAutostartProvider() {
        this(new ProcessCommandRunner(), new PathExecutableFinder());
    }

    WindowsRegAutostartProvider(CommandRunner runner, ExecutableFinder executables) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.executables = Objects.requireNonNull(executables, "executables");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.WINDOWS;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Capability capability() {
        if (executables.find(REG).isEmpty()) {
            return new Capability(
                    Capability.Status.MISSING_DEPENDENCY,
                    Reason.BACKEND_MISSING,
                    "reg.exe was not found on PATH",
                    "reg.exe ships with Windows; restore System32 on PATH");
        }
        return new Capability(
                Capability.Status.AVAILABLE,
                Reason.NONE,
                "reg.exe is available for per-user Run entries",
                null);
    }

    @Override
    public Result<Void> enable(AutostartEntry entry) {
        Objects.requireNonNull(entry, "entry");
        String data = WindowsCommandLine.build(entry.executable(), entry.arguments());
        Result<Void> writeFailure = importValue(entry.appId(), data);
        if (writeFailure != null) {
            return writeFailure;
        }

        CommandResult query = runner.run(queryArgv(entry.appId()), TIMEOUT);
        if (query.timedOut()) {
            return AutostartResults.failed(
                    ID, Reason.TIMEOUT, "reg.exe query timed out and was destroyed");
        }
        if (query.isLaunchFailure()) {
            return AutostartResults.failed(
                    ID,
                    Reason.BACKEND_ERROR,
                    "reg.exe query could not be started"
                            + AutostartResults.stderrSuffix(query.stderr()));
        }
        if (query.exitCode() != 0) {
            if (WindowsRegQuery.isNotFound(query.stderr())) {
                return AutostartResults.failed(
                        ID,
                        Reason.VERIFICATION_FAILED,
                        "reg.exe import reported success but the value was absent on read-back");
            }
            return AutostartResults.failed(
                    ID,
                    Reason.IO_ERROR,
                    "Could not read the value back: reg.exe query exited with code "
                            + query.exitCode()
                            + AutostartResults.stderrSuffix(query.stderr()));
        }
        Optional<String> readBack = WindowsRegQuery.findValue(query.stdout(), entry.appId());
        if (readBack.isEmpty()) {
            return AutostartResults.failed(
                    ID,
                    Reason.VERIFICATION_FAILED,
                    "reg.exe query did not return the value after it was written");
        }
        if (!readBack.get().equals(data)) {
            return AutostartResults.failed(
                    ID,
                    Reason.VERIFICATION_FAILED,
                    "The value read back does not match what was written");
        }
        return AutostartResults.confirmed(
                ID,
                "Wrote and read back " + RUN_KEY + "\\" + entry.appId()
                        + "; Windows lets users disable Run entries in Settings > Apps > Startup");
    }

    @Override
    public Result<Void> disable(String appId) {
        Validation.requireAppId(appId);
        CommandResult lookup = runner.run(queryArgv(appId), TIMEOUT);
        if (lookup.timedOut()) {
            return AutostartResults.failed(
                    ID, Reason.TIMEOUT, "reg.exe query timed out and was destroyed");
        }
        if (lookup.isLaunchFailure()) {
            return AutostartResults.failed(
                    ID,
                    Reason.BACKEND_ERROR,
                    "reg.exe query could not be started"
                            + AutostartResults.stderrSuffix(lookup.stderr()));
        }
        if (lookup.exitCode() != 0) {
            if (WindowsRegQuery.isNotFound(lookup.stderr())) {
                return AutostartResults.confirmed(
                        ID, "No Run value was present for " + appId);
            }
            return AutostartResults.failed(
                    ID,
                    Reason.IO_ERROR,
                    "Could not read the Run value: reg.exe query exited with code "
                            + lookup.exitCode()
                            + AutostartResults.stderrSuffix(lookup.stderr()));
        }
        if (WindowsRegQuery.findValue(lookup.stdout(), appId).isEmpty()) {
            return AutostartResults.failed(
                    ID,
                    Reason.IO_ERROR,
                    "reg.exe query succeeded but its output did not contain the Run value");
        }

        CommandResult delete = runner.run(
                List.of(REG, "delete", RUN_KEY, "/v", appId, "/f"), TIMEOUT);
        Result<Void> failure = commandFailure("reg.exe delete", delete);
        if (failure != null) {
            return failure;
        }

        CommandResult verify = runner.run(queryArgv(appId), TIMEOUT);
        if (verify.timedOut()) {
            return AutostartResults.failed(
                    ID, Reason.TIMEOUT, "reg.exe query timed out and was destroyed");
        }
        if (verify.isLaunchFailure()) {
            return AutostartResults.failed(
                    ID,
                    Reason.BACKEND_ERROR,
                    "reg.exe query could not be started"
                            + AutostartResults.stderrSuffix(verify.stderr()));
        }
        if (verify.exitCode() != 0) {
            if (WindowsRegQuery.isNotFound(verify.stderr())) {
                return AutostartResults.confirmed(ID, "Removed the Run value for " + appId);
            }
            return AutostartResults.failed(
                    ID,
                    Reason.IO_ERROR,
                    "Could not verify the removal: reg.exe query exited with code "
                            + verify.exitCode()
                            + AutostartResults.stderrSuffix(verify.stderr()));
        }
        return AutostartResults.failed(
                ID,
                Reason.VERIFICATION_FAILED,
                "The Run value is still present after reg.exe delete");
    }

    @Override
    public Result<Boolean> isEnabled(String appId) {
        Validation.requireAppId(appId);
        CommandResult query = runner.run(queryArgv(appId), TIMEOUT);
        if (query.timedOut()) {
            return AutostartResults.failed(
                    ID, Reason.TIMEOUT, "reg.exe query timed out and was destroyed");
        }
        if (query.isLaunchFailure()) {
            return AutostartResults.failed(
                    ID,
                    Reason.BACKEND_ERROR,
                    "reg.exe query could not be started"
                            + AutostartResults.stderrSuffix(query.stderr()));
        }
        if (query.exitCode() != 0) {
            if (WindowsRegQuery.isNotFound(query.stderr())) {
                return AutostartResults.found(
                        ID, false, "No Run value was found for " + appId);
            }
            LOG.log(System.Logger.Level.WARNING,
                    "Could not read the Run value for " + appId
                            + ": reg.exe query exited with code " + query.exitCode());
            return AutostartResults.failed(
                    ID,
                    Reason.IO_ERROR,
                    "Could not read the Run value: reg.exe query exited with code "
                            + query.exitCode()
                            + AutostartResults.stderrSuffix(query.stderr()));
        }
        if (WindowsRegQuery.findValue(query.stdout(), appId).isEmpty()) {
            return AutostartResults.failed(
                    ID,
                    Reason.IO_ERROR,
                    "reg.exe query succeeded but its output did not contain the Run value");
        }
        return AutostartResults.found(ID, true, "Found the Run value for " + appId);
    }

    /**
     * Writes the value through a temporary {@code .reg} file and deletes the file afterwards.
     *
     * <p>{@code reg.exe add /d} cannot carry a quoted command line that contains spaces: Java's
     * {@link ProcessBuilder} sees an argument that starts and ends with a double quote as already
     * quoted and forwards it unchanged, after which {@code reg.exe} splits it. Importing a file
     * keeps the data out of the command line entirely, so the only argument Java quotes is a plain
     * temporary path.
     *
     * @param appId the value name
     * @param data  the command line to store
     * @return {@code null} when the import succeeded, otherwise the failure result
     */
    private Result<Void> importValue(String appId, String data) {
        Path file;
        try {
            file = Files.createTempFile("jnativekit-run-", ".reg");
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not create a temporary .reg file", e);
            return AutostartResults.failed(
                    ID, Reason.IO_ERROR, "Could not create a temporary .reg file: " + e);
        }
        try {
            Files.write(
                    file,
                    WindowsRegFile.encode(WindowsRegFile.render(RUN_KEY_IN_FILE, appId, data)),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            CommandResult imported = runner.run(
                    List.of(REG, "import", file.toString()), TIMEOUT);
            return commandFailure("reg.exe import", imported);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Could not write the temporary .reg file " + file, e);
            return AutostartResults.failed(
                    ID, Reason.IO_ERROR, "Could not write the temporary .reg file: " + e);
        } finally {
            deleteTemporary(file);
        }
    }

    private static void deleteTemporary(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Could not delete the temporary .reg file " + file, e);
        }
    }

    private Result<Void> commandFailure(String action, CommandResult result) {
        if (result.timedOut()) {
            return AutostartResults.failed(
                    ID, Reason.TIMEOUT, action + " timed out and was destroyed");
        }
        if (result.isLaunchFailure()) {
            return AutostartResults.failed(
                    ID,
                    Reason.BACKEND_ERROR,
                    action + " could not be started"
                            + AutostartResults.stderrSuffix(result.stderr()));
        }
        if (result.exitCode() != 0) {
            return AutostartResults.failed(
                    ID,
                    Reason.BACKEND_ERROR,
                    action + " exited with code " + result.exitCode()
                            + AutostartResults.stderrSuffix(result.stderr()));
        }
        return null;
    }

    private static List<String> queryArgv(String appId) {
        return List.of(REG, "query", RUN_KEY, "/v", appId);
    }
}
