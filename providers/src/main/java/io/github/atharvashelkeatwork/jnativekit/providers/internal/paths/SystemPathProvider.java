package io.github.atharvashelkeatwork.jnativekit.providers.internal.paths;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * The production {@link PathProvider}: {@code user.home} plus the XDG configuration directory when
 * it is set to an absolute path.
 *
 * <p>The constructor accepts an explicit home and environment so tests can exercise the XDG rules
 * without changing the real environment.
 */
public final class SystemPathProvider implements PathProvider {

    private static final System.Logger LOG = System.getLogger(SystemPathProvider.class.getName());

    private static final String XDG_CONFIG_HOME = "XDG_CONFIG_HOME";
    private static final String DEFAULT_CONFIG_DIRECTORY = ".config";

    private final Path home;
    private final Map<String, String> environment;

    /**
     * Creates a provider that reads the home directory and the environment of the running JVM.
     *
     * @throws IllegalStateException if {@code user.home} is not set
     */
    public SystemPathProvider() {
        this(defaultHome(), System.getenv());
    }

    /**
     * Creates a provider with an explicit home directory and environment.
     *
     * @param home        the absolute home directory; never {@code null}
     * @param environment the environment variables to read; never {@code null}
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if {@code home} is not an absolute path
     */
    public SystemPathProvider(Path home, Map<String, String> environment) {
        Objects.requireNonNull(home, "home");
        if (!home.isAbsolute()) {
            throw new IllegalArgumentException("home must be an absolute path");
        }
        this.home = home;
        this.environment = Map.copyOf(Objects.requireNonNull(environment, "environment"));
    }

    @Override
    public Path home() {
        return home;
    }

    @Override
    public Path configDirectory() {
        String xdg = environment.get(XDG_CONFIG_HOME);
        if (xdg != null && !xdg.isBlank()) {
            try {
                Path candidate = Path.of(xdg);
                if (candidate.isAbsolute()) {
                    return candidate;
                }
            } catch (InvalidPathException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Ignoring invalid XDG_CONFIG_HOME value; falling back to the home directory", e);
            }
        }
        return home.resolve(DEFAULT_CONFIG_DIRECTORY);
    }

    private static Path defaultHome() {
        String value = System.getProperty("user.home");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("user.home is not set");
        }
        return Path.of(value);
    }
}
