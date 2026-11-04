package io.github.atharvashelkeatwork.jnativekit.providers.internal.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;

/**
 * Renders and parses the Desktop Entry subset used for Linux autostart.
 *
 * <p>Internal to the {@code providers} module. The renderer follows the freedesktop Desktop Entry
 * specification for the {@code Exec} key: a literal {@code %} is written as {@code %%} because
 * field codes are expanded after unquoting, and arguments that contain reserved characters are
 * enclosed in double quotes with {@code "}, {@code `}, {@code $} and {@code \} escaped. String
 * values also use the spec's {@code \\}, {@code \n}, {@code \t} and {@code \r} escapes.
 *
 * <p>Quoting is applied after the string-level escapes, so a literal backslash in an argument is
 * written as four backslashes, exactly as the specification requires.
 */
public final class DesktopEntry {

    private DesktopEntry() {
    }

    /**
     * Renders a complete autostart Desktop Entry file.
     *
     * @param entry the entry to render; never {@code null}
     * @return the file content, with {@code \n} line endings and a trailing newline
     * @throws NullPointerException if {@code entry} is {@code null}
     */
    public static String render(AutostartEntry entry) {
        Objects.requireNonNull(entry, "entry");
        StringBuilder content = new StringBuilder();
        content.append("[Desktop Entry]\n");
        content.append("Type=Application\n");
        content.append("Name=").append(escapeStringValue(entry.displayName())).append('\n');
        content.append("Exec=").append(execCommandLine(entry)).append('\n');
        content.append("X-GNOME-Autostart-enabled=true\n");
        return content.toString();
    }

    /**
     * Renders the {@code Exec} value for an entry: the executable followed by its arguments.
     *
     * @param entry the entry whose command line to render; never {@code null}
     * @return the quoted command line
     * @throws NullPointerException if {@code entry} is {@code null}
     */
    public static String execCommandLine(AutostartEntry entry) {
        Objects.requireNonNull(entry, "entry");
        StringBuilder commandLine = new StringBuilder(quoteExecArgument(entry.executable().toString()));
        for (String argument : entry.arguments()) {
            commandLine.append(' ').append(quoteExecArgument(argument));
        }
        return commandLine.toString();
    }

    /**
     * Escapes one argument value and quotes it when the Desktop Entry specification requires it.
     *
     * @param argument the raw argument; never {@code null}
     * @return the value as it appears in the {@code Exec} key
     * @throws NullPointerException if {@code argument} is {@code null}
     */
    public static String quoteExecArgument(String argument) {
        Objects.requireNonNull(argument, "argument");
        String escaped = escapeStringValue(argument).replace("%", "%%");
        if (!needsQuoting(escaped)) {
            return escaped;
        }
        StringBuilder quoted = new StringBuilder(escaped.length() + 2).append('"');
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == '"' || c == '`' || c == '$' || c == '\\') {
                quoted.append('\\');
            }
            quoted.append(c);
        }
        return quoted.append('"').toString();
    }

    /**
     * Applies the Desktop Entry string-value escapes to a value.
     *
     * @param value the raw value; never {@code null}
     * @return the escaped value, safe to place after {@code Name=}
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static String escapeStringValue(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\t' -> escaped.append("\\t");
                case '\r' -> escaped.append("\\r");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /**
     * Parses a Desktop Entry file and returns the {@code Name} and the decoded {@code Exec} argv.
     *
     * <p>The parser is deliberately strict about the parts this library manages: a
     * {@code [Desktop Entry]} group, a {@code Name} key and a non-empty {@code Exec} key must be
     * present. Other keys and groups are ignored.
     *
     * @param content the file content; never {@code null}
     * @return the parsed values; never {@code null}
     * @throws DesktopEntryFormatException if the file is not a valid entry
     * @throws NullPointerException        if {@code content} is {@code null}
     */
    public static Parsed parse(String content) throws DesktopEntryFormatException {
        Objects.requireNonNull(content, "content");
        boolean groupSeen = false;
        boolean inDesktopEntry = false;
        String name = null;
        String exec = null;
        for (String rawLine : content.split("\n", -1)) {
            String line = rawLine.endsWith("\r")
                    ? rawLine.substring(0, rawLine.length() - 1)
                    : rawLine;
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[")) {
                inDesktopEntry = line.equals("[Desktop Entry]");
                if (inDesktopEntry) {
                    groupSeen = true;
                }
                continue;
            }
            if (!inDesktopEntry) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1);
            if (key.equals("Name") && name == null) {
                name = unescapeStringValue(value);
            } else if (key.equals("Exec") && exec == null) {
                exec = value;
            }
        }
        if (!groupSeen) {
            throw new DesktopEntryFormatException("The file has no [Desktop Entry] group");
        }
        if (name == null) {
            throw new DesktopEntryFormatException("The [Desktop Entry] group has no Name key");
        }
        if (exec == null) {
            throw new DesktopEntryFormatException("The [Desktop Entry] group has no Exec key");
        }
        List<String> arguments = parseExec(exec);
        if (arguments.isEmpty()) {
            throw new DesktopEntryFormatException("The Exec key is empty");
        }
        return new Parsed(name, arguments);
    }

    private static boolean needsQuoting(String value) {
        if (value.isEmpty()) {
            return true;
        }
        for (int i = 0; i < value.length(); i++) {
            switch (value.charAt(i)) {
                case ' ', '\t', '\n', '"', '\'', '\\', '>', '<', '~', '|', '&',
                        ';', '$', '*', '?', '#', '(', ')', '`':
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    private static String unescapeStringValue(String value) {
        StringBuilder unescaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i + 1 >= value.length()) {
                unescaped.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 's' -> unescaped.append(' ');
                case 'n' -> unescaped.append('\n');
                case 't' -> unescaped.append('\t');
                case 'r' -> unescaped.append('\r');
                case '\\' -> unescaped.append('\\');
                default -> unescaped.append('\\').append(next);
            }
        }
        return unescaped.toString();
    }

    private static List<String> parseExec(String value) throws DesktopEntryFormatException {
        List<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        boolean started = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (inQuotes) {
                if (c == '\\') {
                    if (i + 1 >= value.length()) {
                        throw new DesktopEntryFormatException(
                                "The Exec key ends with a lone backslash inside quotes");
                    }
                    char next = value.charAt(++i);
                    if (next == '"' || next == '`' || next == '$' || next == '\\') {
                        current.append(next);
                    } else {
                        current.append('\\').append(next);
                    }
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                started = true;
            } else if (c == ' ' || c == '\t') {
                if (started) {
                    arguments.add(expandFieldCodes(unescapeStringValue(current.toString())));
                    current.setLength(0);
                    started = false;
                }
            } else {
                current.append(c);
                started = true;
            }
        }
        if (inQuotes) {
            throw new DesktopEntryFormatException("The Exec key has an unterminated quoted argument");
        }
        if (started) {
            arguments.add(expandFieldCodes(unescapeStringValue(current.toString())));
        }
        return arguments;
    }

    private static String expandFieldCodes(String value) {
        return value.replace("%%", "%");
    }

    /**
     * The values this library needs from a Desktop Entry file.
     *
     * @param name the decoded {@code Name} value
     * @param exec the decoded {@code Exec} argv, with the executable first
     */
    public record Parsed(String name, List<String> exec) {

        /**
         * Creates the record and copies the argv.
         *
         * @param name the decoded name
         * @param exec the decoded argv
         * @throws NullPointerException if an argument is {@code null}
         */
        public Parsed {
            Objects.requireNonNull(name, "name");
            exec = List.copyOf(Objects.requireNonNull(exec, "exec"));
        }
    }
}
