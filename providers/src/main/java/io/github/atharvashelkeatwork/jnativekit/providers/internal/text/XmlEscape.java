package io.github.atharvashelkeatwork.jnativekit.providers.internal.text;

import java.util.Objects;

/**
 * Escapes and decodes the XML text used by the macOS launch agent property list.
 *
 * <p>Internal to the {@code providers} module. Only the five predefined XML entities and numeric
 * character references are supported; anything else is a format error instead of a silent guess.
 */
public final class XmlEscape {

    private XmlEscape() {
    }

    /**
     * Escapes a value for use as XML text.
     *
     * @param value the raw value; never {@code null}
     * @return the escaped value
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static String escape(String value) {
        Objects.requireNonNull(value, "value");
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&apos;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /**
     * Decodes XML text produced by {@link #escape(String)}.
     *
     * @param value the escaped text; never {@code null}
     * @return the decoded text
     * @throws PlistFormatException if an entity is unterminated or unsupported
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static String unescape(String value) throws PlistFormatException {
        Objects.requireNonNull(value, "value");
        StringBuilder decoded = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c != '&') {
                decoded.append(c);
                i++;
                continue;
            }
            int semicolon = value.indexOf(';', i + 1);
            if (semicolon < 0) {
                throw new PlistFormatException("Unterminated XML entity");
            }
            decoded.append(decodeEntity(value.substring(i + 1, semicolon)));
            i = semicolon + 1;
        }
        return decoded.toString();
    }

    private static String decodeEntity(String entity) throws PlistFormatException {
        switch (entity) {
            case "amp":
                return "&";
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "quot":
                return "\"";
            case "apos":
                return "'";
            default:
                return decodeCharacterReference(entity);
        }
    }

    private static String decodeCharacterReference(String entity) throws PlistFormatException {
        boolean hexadecimal = entity.startsWith("#x") || entity.startsWith("#X");
        boolean decimal = entity.startsWith("#") && !hexadecimal;
        if (!hexadecimal && !decimal) {
            throw new PlistFormatException("Unsupported XML entity &" + entity + ";");
        }
        String digits = entity.substring(hexadecimal ? 2 : 1);
        int codePoint;
        try {
            codePoint = Integer.parseInt(digits, hexadecimal ? 16 : 10);
        } catch (NumberFormatException e) {
            throw new PlistFormatException("Invalid numeric XML entity &" + entity + ";");
        }
        if (!Character.isValidCodePoint(codePoint)
                || (codePoint >= 0xD800 && codePoint <= 0xDFFF)
                || codePoint == 0) {
            throw new PlistFormatException("Invalid XML code point in &" + entity + ";");
        }
        return new String(Character.toChars(codePoint));
    }
}
