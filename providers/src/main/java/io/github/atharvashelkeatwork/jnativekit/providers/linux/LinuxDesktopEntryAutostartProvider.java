package io.github.atharvashelkeatwork.jnativekit.providers.linux;

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
import io.github.atharvashelkeatwork.jnativekit.providers.internal.text.DesktopEntry;
import io.github.atharvashelkeatwork.jnativekit.spi.AutostartProvider;

/**
 * Linux autostart through a Desktop Entry in the XDG autostart directory.
 *
 * <p>The entry is written to {@code $XDG_CONFIG_HOME/autostart/<appId>.desktop}, or
 * {@code ~/.config/autostart/<appId>.desktop} when {@code XDG_CONFIG_HOME} is not set. Writes go
 * through a temporary file and an atomic move, and {@code enable} re-reads and parses the file
 * before reporting {@link Result.Status#CONFIRMED}.
 *
 * <p>The provider never runs a command and never touches another application's entry. The file
 * name is derived only from the validated {@code appId}, so no caller-supplied path fragment can
 * escape the autostart directory.
 */
public final class LinuxDesktopEntryAutostartProvider implements AutostartProvider {

    private static final String ID = "linux.desktop-entry";
    private static final int PRIORITY = 100;
    private static final String AUTOSTART_DIRECTORY = "autostart";
    private static final String ENTRY_SUFFIX = ".desktop";

    private static final System.Logger LOG =
            System.getLogger(LinuxDesktopEntryAutostartProvider.class.getName());

    private final PathProvider paths;

    /**
     * Creates the provider with the real home and configuration directories.
     */
    public LinuxDesktopEntryAutostartProvider() {
        this(new SystemPathProvider());
    }

    LinuxDesktopEntryAutostartProvider(PathProvider paths) {
        this.paths = Objects.requireNonNull(paths, "paths");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean supports(Platform platform) {
        return platform == Platform.LINUX;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public Capability capability() {
        return DirectoryStore.capability(autostartDirectory());
    }

    @Override
    public Result<Void> enable(AutostartEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Path file = entryFile(entry.appId());
        try {
            DirectoryStore.writeAtomically(file, DesktopEntry.render(entry));
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not write the autostart entry " + file, e);
            return AutostartResults.failed(
                    ID, Reason.IO_ERROR, "Could not write " + file + ": " + e);
        }

        try {
            DesktopEntry.Parsed parsed =
                    DesktopEntry.parse(Files.readString(file, StandardCharsets.UTF_8));
            if (!parsed.name().equals(entry.displayName())
                    || !parsed.exec().equals(expectedArgv(entry))) {
                return AutostartResults.failed(
                        ID,
                        Reason.VERIFICATION_FAILED,
                        "The written entry did not read back as requested: " + file);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not read back the autostart entry " + file, e);
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
                        ID, "No autostart entry was present for " + appId);
            }
            if (Files.exists(file)) {
                return AutostartResults.failed(
                        ID,
                        Reason.VERIFICATION_FAILED,
                        "The autostart entry is still present after deletion: " + file);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not delete the autostart entry " + file, e);
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
            DesktopEntry.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (NoSuchFileException e) {
            return AutostartResults.found(ID, false, "No autostart entry at " + file);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not read the autostart entry " + file, e);
            return AutostartResults.failed(
                    ID, Reason.IO_ERROR, "Could not read " + file + ": " + e);
        }
        return AutostartResults.found(ID, true, "Found " + file);
    }

    private Path autostartDirectory() {
        return paths.configDirectory().resolve(AUTOSTART_DIRECTORY);
    }

    private Path entryFile(String appId) {
        return autostartDirectory().resolve(appId + ENTRY_SUFFIX);
    }

    private static List<String> expectedArgv(AutostartEntry entry) {
        List<String> argv = new ArrayList<>(entry.arguments().size() + 1);
        argv.add(entry.executable().toString());
        argv.addAll(entry.arguments());
        return argv;
    }
}
