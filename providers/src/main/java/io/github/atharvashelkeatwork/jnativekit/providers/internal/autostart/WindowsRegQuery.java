package io.github.atharvashelkeatwork.jnativekit.providers.internal.autostart;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Parses the output of {@code reg.exe query} and recognises its "not found" error.
 *
 * <p>Internal to the {@code providers} module. The expected output shape is the one Windows uses
 * for a single value:
 *
 * <pre>
 * HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\Run
 *     appId    REG_SZ    "C:\Path\App.exe" "--flag"
 * </pre>
 *
 * <p>Only the English "unable to find"/"cannot find" error text is recognised. On a localized
 * Windows a missing value is therefore reported as a failure instead of a false
 * {@code CONFIRMED(false)}; that is the honest direction and is documented in the provider.
 */
public final class WindowsRegQuery {

    private WindowsRegQuery() {
    }

    /**
     * Finds the data of one {@code REG_SZ} or {@code REG_EXPAND_SZ} value in query output.
     *
     * @param stdout    the captured standard output; never {@code null}
     * @param valueName the value name to look for; never {@code null}
     * @return the value data, or an empty optional when the output does not contain it
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Optional<String> findValue(String stdout, String valueName) {
        Objects.requireNonNull(stdout, "stdout");
        Objects.requireNonNull(valueName, "valueName");
        for (String rawLine : stdout.split("\n", -1)) {
            String line = rawLine.endsWith("\r")
                    ? rawLine.substring(0, rawLine.length() - 1)
                    : rawLine;
            String trimmed = line.stripLeading();
            if (!trimmed.startsWith(valueName)) {
                continue;
            }
            int afterName = valueName.length();
            if (trimmed.length() <= afterName || !Character.isWhitespace(trimmed.charAt(afterName))) {
                continue;
            }
            int index = skipWhitespace(trimmed, afterName);
            int typeStart = index;
            while (index < trimmed.length() && !Character.isWhitespace(trimmed.charAt(index))) {
                index++;
            }
            String type = trimmed.substring(typeStart, index);
            if (!type.equals("REG_SZ") && !type.equals("REG_EXPAND_SZ")) {
                continue;
            }
            index = skipWhitespace(trimmed, index);
            return Optional.of(trimmed.substring(index));
        }
        return Optional.empty();
    }

    /**
     * Returns whether {@code reg.exe} reported that the key or value does not exist.
     *
     * @param stderr the captured standard error; never {@code null}
     * @return {@code true} for the English "unable to find"/"cannot find" message
     * @throws NullPointerException if {@code stderr} is {@code null}
     */
    public static boolean isNotFound(String stderr) {
        Objects.requireNonNull(stderr, "stderr");
        String normalized = stderr.toLowerCase(Locale.ROOT);
        return normalized.contains("unable to find") || normalized.contains("cannot find");
    }

    private static int skipWhitespace(String value, int index) {
        int current = index;
        while (current < value.length() && Character.isWhitespace(value.charAt(current))) {
            current++;
        }
        return current;
    }
}
