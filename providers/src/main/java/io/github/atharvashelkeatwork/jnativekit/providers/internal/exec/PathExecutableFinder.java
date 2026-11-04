package io.github.atharvashelkeatwork.jnativekit.providers.internal.exec;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Default {@link ExecutableFinder}: scans the {@code PATH} environment variable.
 *
 * <p>Entries are checked from left to right. A blank entry is ignored instead of being interpreted
 * as the current directory. A candidate counts as found when it is a regular file that the current
 * process may execute. Symlinks are followed. Invalid path entries are skipped.
 *
 * <p>This finder matches the literal name on every platform. It does not append {@code PATHEXT}
 * suffixes; the Linux and macOS providers use simple Unix tool names.
 */
public final class PathExecutableFinder implements ExecutableFinder {

    private static final System.Logger LOG = System.getLogger(PathExecutableFinder.class.getName());

    private final Map<String, String> environment;
    private final String pathSeparator;

    /**
     * Creates a finder that reads the live process environment.
     */
    public PathExecutableFinder() {
        this(System.getenv(), File.pathSeparator);
    }

    PathExecutableFinder(Map<String, String> environment, String pathSeparator) {
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
        this.pathSeparator = Objects.requireNonNull(pathSeparator, "pathSeparator");
        if (pathSeparator.isEmpty()) {
            throw new IllegalArgumentException("pathSeparator must not be empty");
        }
    }

    @Override
    public Optional<Path> find(String executableName) {
        Objects.requireNonNull(executableName, "executableName");
        if (executableName.isEmpty()) {
            return Optional.empty();
        }
        String path = environment.get("PATH");
        if (path == null || path.isEmpty()) {
            return Optional.empty();
        }
        for (String directory : path.split(Pattern.quote(pathSeparator), -1)) {
            if (directory.isEmpty()) {
                continue;
            }
            Path candidate;
            try {
                candidate = Path.of(directory).resolve(executableName);
            } catch (InvalidPathException e) {
                LOG.log(System.Logger.Level.DEBUG,
                        "Ignoring an invalid PATH entry while looking for " + executableName, e);
                continue;
            }
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return Optional.of(candidate.toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }
}
