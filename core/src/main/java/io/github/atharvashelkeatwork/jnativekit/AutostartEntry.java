package io.github.atharvashelkeatwork.jnativekit;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.internal.Validation;

/**
 * An autostart entry: the application to launch when the user logs into a graphical desktop
 * session, plus how to launch it.
 *
 * <p>Immutable and validated on construction. The list of arguments is copied and unmodifiable;
 * mutating the caller's list afterwards does not affect this entry.
 *
 * @param appId        stable identifier, for example {@code com.example.notes}; pattern
 *                    {@code [A-Za-z0-9][A-Za-z0-9._-]{0,63}}
 * @param displayName human application name; non-blank, at most 128 characters, no control
 *                    characters
 * @param executable  absolute path to the executable; must not contain NUL or newline characters
 * @param arguments    launch arguments; each must be non-null and must not contain NUL, CR or LF
 */
public record AutostartEntry(String appId, String displayName, Path executable, List<String> arguments) {

    /**
     * Creates an autostart entry and enforces the invariants.
     *
     * @param appId        the application identifier; pattern
     *                    {@code [A-Za-z0-9][A-Za-z0-9._-]{0,63}}
     * @param displayName the human readable application name; non-blank, at most 128 characters,
     *                    no control characters
     * @param executable  the absolute executable path; must not contain NUL or newline characters
     * @param arguments    the launch arguments; each must be non-null and must not contain NUL, CR
     *                    or LF
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if any argument violates its rule
     */
    public AutostartEntry(String appId, String displayName, Path executable, List<String> arguments) {
        this.appId = Validation.requireAppId(appId);
        this.displayName = Validation.requireDisplayName(displayName);
        this.executable = Validation.requireAbsoluteExecutable(executable);
        this.arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        for (String argument : this.arguments) {
            Validation.requireArgument(argument);
        }
    }
}
