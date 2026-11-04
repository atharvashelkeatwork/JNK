package io.github.atharvashelkeatwork.jnativekit.providers.internal.exec;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Finds an executable on the process search path without running it.
 *
 * <p>Internal to the {@code providers} module. The lookup is cheap, has no side effects and never
 * starts a process, so it is safe to call from every capability check.
 */
@FunctionalInterface
public interface ExecutableFinder {

    /**
     * Finds the first executable with the given name on the search path.
     *
     * @param executableName the simple executable name, for example {@code notify-send}; never
     *                       {@code null}
     * @return the absolute path of the executable, or an empty optional when it is not present
     * @throws NullPointerException if {@code executableName} is {@code null}
     */
    Optional<Path> find(String executableName);
}
