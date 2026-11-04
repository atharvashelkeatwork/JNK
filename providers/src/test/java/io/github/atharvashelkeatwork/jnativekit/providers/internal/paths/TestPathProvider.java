package io.github.atharvashelkeatwork.jnativekit.providers.internal.paths;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Test double for {@link PathProvider}: a temporary home directory.
 *
 * <p>Test helper only; it is not shipped. Every provider test passes one of these so the real home
 * directory, the real autostart directory and the real launch agents directory are never touched.
 */
public final class TestPathProvider implements PathProvider {

    private final Path home;
    private final Path configDirectory;

    /**
     * Creates a provider whose configuration directory is {@code <home>/.config}.
     *
     * @param home the temporary home directory; never {@code null}
     */
    public TestPathProvider(Path home) {
        this(home, home.resolve(".config"));
    }

    /**
     * Creates a provider with an explicit configuration directory.
     *
     * @param home            the temporary home directory; never {@code null}
     * @param configDirectory the configuration directory; never {@code null}
     */
    public TestPathProvider(Path home, Path configDirectory) {
        this.home = Objects.requireNonNull(home, "home").toAbsolutePath();
        this.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory").toAbsolutePath();
    }

    @Override
    public Path home() {
        return home;
    }

    @Override
    public Path configDirectory() {
        return configDirectory;
    }
}
