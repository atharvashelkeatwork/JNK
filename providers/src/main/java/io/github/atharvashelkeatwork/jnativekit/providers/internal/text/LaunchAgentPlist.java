package io.github.atharvashelkeatwork.jnativekit.providers.internal.text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;

/**
 * Renders and parses the macOS launch agent property list written for autostart.
 *
 * <p>Internal to the {@code providers} module. The rendered file is an XML property list with
 * {@code Label}, {@code ProgramArguments} and {@code RunAtLoad} keys. {@code launchctl} is never
 * invoked: writing the file is enough for the next login, and loading it would start the
 * application immediately.
 *
 * <p>The parser understands the plist subset this library writes, tolerates unknown keys and only
 * decodes the predefined XML entities. It exists so {@code enable} can verify a read-back
 * semantically and {@code isEnabled} can reject a malformed store instead of guessing.
 */
public final class LaunchAgentPlist {

    private static final String DOCTYPE =
            "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" "
                    + "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">";

    private LaunchAgentPlist() {
    }

    /**
     * Renders a complete launch agent property list.
     *
     * @param entry the entry to render; never {@code null}
     * @return the XML content, with {@code \n} line endings and a trailing newline
     * @throws NullPointerException if {@code entry} is {@code null}
     */
    public static String render(AutostartEntry entry) {
        Objects.requireNonNull(entry, "entry");
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append(DOCTYPE).append('\n');
        xml.append("<plist version=\"1.0\">\n");
        xml.append("<dict>\n");
        appendStringValue(xml, "    ", "Label", entry.appId());
        xml.append("    <key>ProgramArguments</key>\n");
        xml.append("    <array>\n");
        appendString(xml, "        ", entry.executable().toString());
        for (String argument : entry.arguments()) {
            appendString(xml, "        ", argument);
        }
        xml.append("    </array>\n");
        xml.append("    <key>RunAtLoad</key>\n");
        xml.append("    <true/>\n");
        xml.append("</dict>\n");
        xml.append("</plist>\n");
        return xml.toString();
    }

    /**
     * Parses a launch agent property list.
     *
     * @param xml the file content; never {@code null}
     * @return the parsed values; never {@code null}
     * @throws PlistFormatException if the content is not an XML plist with the required keys
     * @throws NullPointerException if {@code xml} is {@code null}
     */
    public static Content parse(String xml) throws PlistFormatException {
        Objects.requireNonNull(xml, "xml");
        Cursor cursor = new Cursor(tokenize(xml));
        cursor.expectStart("plist");
        cursor.expectStart("dict");
        Map<String, Object> values = parseDict(cursor);
        cursor.expectEnd("dict");
        cursor.expectEnd("plist");
        cursor.expectEndOfInput();

        Object labelValue = values.get("Label");
        if (!(labelValue instanceof String label)) {
            throw new PlistFormatException("The plist has no string Label value");
        }
        Object argumentsValue = values.get("ProgramArguments");
        if (!(argumentsValue instanceof List<?> rawArguments)) {
            throw new PlistFormatException("The plist has no ProgramArguments array");
        }
        List<String> arguments = new ArrayList<>(rawArguments.size());
        for (Object element : rawArguments) {
            if (!(element instanceof String argument)) {
                throw new PlistFormatException("ProgramArguments must contain only strings");
            }
            arguments.add(argument);
        }
        boolean runAtLoad = Boolean.TRUE.equals(values.get("RunAtLoad"));
        return new Content(label, arguments, runAtLoad);
    }

    private static void appendStringValue(StringBuilder xml, String indent, String key, String value) {
        xml.append(indent).append("<key>").append(XmlEscape.escape(key)).append("</key>\n");
        appendString(xml, indent, value);
    }

    private static void appendString(StringBuilder xml, String indent, String value) {
        xml.append(indent).append("<string>").append(XmlEscape.escape(value)).append("</string>\n");
    }

    private static Map<String, Object> parseDict(Cursor cursor) throws PlistFormatException {
        Map<String, Object> values = new LinkedHashMap<>();
        while (!cursor.isEnd("dict")) {
            cursor.expectStart("key");
            String key = cursor.readText();
            cursor.expectEnd("key");
            values.put(key, parseValue(cursor));
        }
        return values;
    }

    private static Object parseValue(Cursor cursor) throws PlistFormatException {
        cursor.skipWhitespaceText();
        Token token = cursor.next();
        if (token.kind() == Kind.SELF) {
            if (token.value().equals("true")) {
                return Boolean.TRUE;
            }
            if (token.value().equals("false")) {
                return Boolean.FALSE;
            }
            throw new PlistFormatException(
                    "Unsupported self-closing plist element <" + token.value() + "/>");
        }
        if (token.kind() != Kind.START) {
            throw new PlistFormatException("Expected a plist value but found " + describe(token));
        }
        switch (token.value()) {
            case "string":
                return readTextValue(cursor, "string");
            case "true":
                cursor.expectEnd("true");
                return Boolean.TRUE;
            case "false":
                cursor.expectEnd("false");
                return Boolean.FALSE;
            case "integer":
            case "real":
            case "date":
            case "data":
                return new NonStringValue(token.value(), readTextValue(cursor, token.value()));
            case "array":
                return parseArray(cursor);
            case "dict":
                Map<String, Object> nested = parseDict(cursor);
                cursor.expectEnd("dict");
                return nested;
            default:
                throw new PlistFormatException(
                        "Unsupported plist element <" + token.value() + ">");
        }
    }

    private static Object parseArray(Cursor cursor) throws PlistFormatException {
        List<Object> elements = new ArrayList<>();
        while (!cursor.isEnd("array")) {
            elements.add(parseValue(cursor));
        }
        cursor.expectEnd("array");
        return elements;
    }

    private static String readTextValue(Cursor cursor, String tag) throws PlistFormatException {
        String value = cursor.readText();
        cursor.expectEnd(tag);
        return value;
    }

    private static List<Token> tokenize(String xml) throws PlistFormatException {
        List<Token> tokens = new ArrayList<>();
        int index = 0;
        while (index < xml.length()) {
            if (xml.startsWith("<!--", index)) {
                int end = xml.indexOf("-->", index + 4);
                if (end < 0) {
                    throw new PlistFormatException("Unterminated XML comment");
                }
                index = end + 3;
                continue;
            }
            char c = xml.charAt(index);
            if (c != '<') {
                int nextTag = xml.indexOf('<', index);
                int end = nextTag < 0 ? xml.length() : nextTag;
                tokens.add(new Token(Kind.TEXT, XmlEscape.unescape(xml.substring(index, end))));
                index = end;
                continue;
            }
            int end = xml.indexOf('>', index + 1);
            if (end < 0) {
                throw new PlistFormatException("Unterminated XML tag");
            }
            String inner = xml.substring(index + 1, end).trim();
            index = end + 1;
            if (inner.isEmpty()) {
                throw new PlistFormatException("Empty XML tag");
            }
            if (inner.startsWith("?") || inner.startsWith("!")) {
                continue;
            }
            boolean selfClosing = inner.endsWith("/");
            if (selfClosing) {
                inner = inner.substring(0, inner.length() - 1).trim();
            }
            boolean endTag = inner.startsWith("/");
            if (endTag) {
                inner = inner.substring(1).trim();
            }
            int whitespace = indexOfWhitespace(inner);
            String name = whitespace < 0 ? inner : inner.substring(0, whitespace);
            if (name.isEmpty()) {
                throw new PlistFormatException("Malformed XML tag");
            }
            Kind kind = endTag ? Kind.END : selfClosing ? Kind.SELF : Kind.START;
            tokens.add(new Token(kind, name));
        }
        return tokens;
    }

    private static int indexOfWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String describe(Token token) {
        return token.kind() == Kind.TEXT
                ? "text \"" + token.value() + "\""
                : "<" + token.value() + ">";
    }

    private enum Kind {
        START,
        END,
        SELF,
        TEXT
    }

    private record Token(Kind kind, String value) {
    }

    /**
     * A parsed scalar that is not a plist {@code <string>}; kept so an array of strings can reject
     * it while unknown dictionary keys may still contain it.
     */
    private record NonStringValue(String type, String value) {
    }

    private static final class Cursor {

        private final List<Token> tokens;
        private int index;

        private Cursor(List<Token> tokens) {
            this.tokens = tokens;
        }

        private void skipWhitespaceText() {
            while (index < tokens.size()) {
                Token token = tokens.get(index);
                if (token.kind() != Kind.TEXT || !token.value().isBlank()) {
                    return;
                }
                index++;
            }
        }

        private Token next() throws PlistFormatException {
            if (index >= tokens.size()) {
                throw new PlistFormatException("Unexpected end of the property list");
            }
            return tokens.get(index++);
        }

        private String readText() {
            StringBuilder text = new StringBuilder();
            while (index < tokens.size() && tokens.get(index).kind() == Kind.TEXT) {
                text.append(tokens.get(index++).value());
            }
            return text.toString();
        }

        private boolean isEnd(String name) {
            skipWhitespaceText();
            return index < tokens.size()
                    && tokens.get(index).kind() == Kind.END
                    && tokens.get(index).value().equals(name);
        }

        private void expectStart(String name) throws PlistFormatException {
            skipWhitespaceText();
            Token token = next();
            if (token.kind() != Kind.START || !token.value().equals(name)) {
                throw new PlistFormatException(
                        "Expected <" + name + "> but found " + describe(token));
            }
        }

        private void expectEnd(String name) throws PlistFormatException {
            skipWhitespaceText();
            Token token = next();
            if (token.kind() != Kind.END || !token.value().equals(name)) {
                throw new PlistFormatException(
                        "Expected </" + name + "> but found " + describe(token));
            }
        }

        private void expectEndOfInput() throws PlistFormatException {
            skipWhitespaceText();
            if (index != tokens.size()) {
                throw new PlistFormatException(
                        "Unexpected content after </plist>: " + describe(tokens.get(index)));
            }
        }
    }

    /**
     * The values this library needs from a launch agent property list.
     *
     * @param label            the {@code Label} value
     * @param programArguments the {@code ProgramArguments} array, with the executable first
     * @param runAtLoad        the {@code RunAtLoad} value; {@code false} when absent
     */
    public record Content(String label, List<String> programArguments, boolean runAtLoad) {

        /**
         * Creates the record and copies the argument list.
         *
         * @param label            the label
         * @param programArguments the program arguments
         * @param runAtLoad        whether the job runs at load
         * @throws NullPointerException if an argument is {@code null}
         */
        public Content {
            Objects.requireNonNull(label, "label");
            programArguments = List.copyOf(Objects.requireNonNull(programArguments, "programArguments"));
        }
    }
}
