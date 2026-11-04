package io.github.atharvashelkeatwork.jnativekit.providers.macos;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;
import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Platform;
import io.github.atharvashelkeatwork.jnativekit.Reason;
import io.github.atharvashelkeatwork.jnativekit.Result;
import io.github.atharvashelkeatwork.jnativekit.internal.Validation;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.autostart.AutostartResults;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.autostart.DirectoryStore;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.paths.PathProvider;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.paths.SystemPathProvider;
import io.github.atharvashelkeatwork.jnativekit.providers.internal.text.LaunchAgentPlist;
import io.github.atharvashelkeatwork.jnativekit.spi.AutostartProvider;

/**
 * macOS autostart through a per-user launch agent property list.
 *
 * <p>The plist is written to {@code ~/Library/LaunchAgents/<appId>.plist} with a {@code Label}, a
 * {@code ProgramArguments} array and {@code RunAtLoad=true}. {@code launchctl} is deliberately not
 * called: loading the job would start the application immediately instead of at the next login.
 * {@code enable} re-reads and parses the plist before reporting {@link Result.Status#CONFIRMED}.
 */
public final class MacLaunchAgentAutostartProvider implements AutostartProvider {

    private static final String ID = "macos.launch-agent";
    private static final int PRIORITY = 100;
    private static final String LIBRARY = "Library";
    private static final String LAUNCH_AGENTS = "LaunchAgents";
    private static final String ENTRY_SUFFIX = ".plist";

    private static final System.Logger LOG =
            System.getLogger(MacLaunchAgentAutostartProvider.class.getName());

    private final PathProvider paths;

    /**
     * Creates the provider with the real home directory.
     */
    public MacLaunchAgentAutostartProvider() {
        this(new SystemPathProvider());
    }

    MacLaunchAgentAutostartProvider(PathProvider paths) {
        this.paths = Objects.requireNonNull(paths, "paths");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.MACOS;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Capability capability() {
        return DirectoryStore.capability(launchAgentsDirectory());
    }

    @Override
    public Result<Void> enable(AutostartEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Path file = entryFile(entry.appId());
        try {
            DirectoryStore.writeAtomically(file, LaunchAgentPlist.render(entry));
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not write the launch agent " + file, e);
            return AutostartResults.failed(
                    ID, Reason.IO_ERROR, "Could not write " + file + ": " + e);
        }

        try {
            LaunchAgentPlist.Content parsed =
                    LaunchAgentPlist.parse(Files.readString(file, StandardCharsets.UTF_8));
            LaunchAgentPlist.Content expected =
                    new LaunchAgentPlist.Content(entry.appId(), expectedArgv(entry), true);
            if (!parsed.equals(expected)) {
                return AutostartResults.failed(
                        ID,
                        Reason.VERIFICATION_FAILED,
                        "The written launch agent did not read back as requested: " + file);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not read back the launch agent " + file, e);
            return AutostartResults.failed(
                    ID, Reason.IO_ERROR, "Could not read back " + file + ": " + e);
        }
        return AutostartResults.confirmed(ID, "Wrote and read back " + file);
    }

    @Override
    public Result<Void> disable(String appId) {
        Validation.requireAppId(appId);
        Path file = entryFile(appId);
        try {
            if (!Files.deleteIfExists(file)) {
                return AutostartResults.confirmed(
                        ID, "No launch agent was present for " + appId);
            }
            if (Files.exists(file)) {
                return AutostartResults.failed(
                        ID,
                        Reason.VERIFICATION_FAILED,
                        "The launch agent is still present after deletion: " + file);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not delete the launch agent " + file, e);
            return AutostartResults.failed(
                    ID, Reason.IO_ERROR, "Could not delete " + file + ": " + e);
        }
        return AutostartResults.confirmed(ID, "Removed " + file);
    }

    @Override
    public Result<Boolean> isEnabled(String appId) {
        Validation.requireAppId(appId);
        Path file = entryFile(appId);
        try {
            LaunchAgentPlist.Content parsed =
                    LaunchAgentPlist.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.label().equals(appId)) {
                return AutostartResults.failed(
                        ID,
                        Reason.IO_ERROR,
                        "The launch agent at " + file + " declares Label " + parsed.label());
            }
        } catch (NoSuchFileException e) {
            return AutostartResults.found(ID, false, "No launch agent at " + file);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not read the launch agent " + file, e);
            return AutostartResults.failed(
                    ID, Reason.IO_ERROR, "Could not read " + file + ": " + e);
        }
        return AutostartResults.found(ID, true, "Found " + file);
    }

    private Path launchAgentsDirectory() {
        return paths.home().resolve(LIBRARY).resolve(LAUNCH_AGENTS);
    }

    private Path entryFile(String appId) {
        return launchAgentsDirectory().resolve(appId + ENTRY_SUFFIX);
    }

    private static List<String> expectedArgv(AutostartEntry entry) {
        List<String> argv = new ArrayList<>(entry.arguments().size() + 1);
        argv.add(entry.executable().toString());
        argv.addAll(entry.arguments());
        return argv;
    }
}
