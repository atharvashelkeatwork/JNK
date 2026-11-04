package io.github.atharvashelkeatwork.jnativekit.providers.internal.paths;

import java.nio.file.Path;

/**
 * Supplies the user directories the file-based providers need.
 *
 * <p>Providers take a {@code PathProvider} in their constructor so tests can use temporary
 * directories and never touch the real user home or autostart directory.
 */
public interface PathProvider {

    /**
     * Returns the current user's home directory.
     *
     * @return the absolute home directory; never {@code null}
     */
    Path home();

    /**
     * Returns the directory that holds per-user configuration, for example
     * {@code ~/.config} on Linux.
     *
     * @return the absolute configuration directory; never {@code null}
     */
    Path configDirectory();
}
