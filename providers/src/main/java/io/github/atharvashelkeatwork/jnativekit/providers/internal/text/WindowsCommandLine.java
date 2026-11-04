package io.github.atharvashelkeatwork.jnativekit.providers.internal.text;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Builds the command line stored in the Windows {@code Run} registry value.
 *
 * <p>Internal to the {@code providers} module. The value is consumed by
 * {@code CreateProcess} and parsed by {@code CommandLineToArgvW}, so every token is enclosed in
 * double quotes and backslashes that precede a quote are doubled. This keeps paths with spaces,
 * embedded quotes and trailing backslashes intact.
 */
public final class WindowsCommandLine {

    private WindowsCommandLine() {
    }

    /**
     * Builds the registry value data for an executable and its arguments.
     *
     * @param executable the absolute executable path; never {@code null}
     * @param arguments  the launch arguments; never {@code null}
     * @return the quoted command line
     * @throws NullPointerException if an argument is {@code null}
     */
    public static String build(Path executable, List<String> arguments) {
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(arguments, "arguments");
        StringBuilder commandLine = new StringBuilder(quote(executable.toString()));
        for (String argument : arguments) {
            commandLine.append(' ').append(quote(argument));
        }
        return commandLine.toString();
    }

    /**
     * Quotes one command-line token using the {@code CommandLineToArgvW} rules.
     *
     * <p>The result is always quoted, including a non-empty token without spaces, so the stored
     * value has one predictable shape. An empty token becomes {@code ""}.
     *
     * @param argument the raw token; never {@code null}
     * @return the quoted token
     * @throws NullPointerException if {@code argument} is {@code null}
     */
    public static String quote(String argument) {
        Objects.requireNonNull(argument, "argument");
        StringBuilder quoted = new StringBuilder(argument.length() + 2).append('"');
        int backslashes = 0;
        for (int i = 0; i < argument.length(); i++) {
            char c = argument.charAt(i);
            if (c == '\\') {
                backslashes++;
            } else if (c == '"') {
                quoted.append("\\".repeat(backslashes * 2 + 1)).append('"');
                backslashes = 0;
            } else {
                quoted.append("\\".repeat(backslashes)).append(c);
                backslashes = 0;
            }
        }
        quoted.append("\\".repeat(backslashes * 2)).append('"');
        return quoted.toString();
    }
}
