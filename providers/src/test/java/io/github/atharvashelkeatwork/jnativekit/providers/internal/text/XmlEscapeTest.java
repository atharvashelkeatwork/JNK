package io.github.atharvashelkeatwork.jnativekit.providers.internal.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tier T1: XML text escaping for the launch agent plist.
 */
class XmlEscapeTest {

    static Stream<Arguments> values() {
        return Stream.of(
                Arguments.of("plain"),
                Arguments.of("a & b"),
                Arguments.of("<tag>"),
                Arguments.of("\"quoted\""),
                Arguments.of("'single'"),
                Arguments.of("&amp;"),
                Arguments.of("mixed & < > \" ' text"),
                Arguments.of("\u00dcn\u00efc\u00f8d\u00e9 \u65e5\u672c \ud83c\udf89"),
                Arguments.of(""));
    }

    @ParameterizedTest
    @MethodSource("values")
    void escapeAndUnescapeRoundTrip(String value) throws Exception {
        assertEquals(value, XmlEscape.unescape(XmlEscape.escape(value)));
    }

    @Test
    void escapesTheFivePredefinedEntities() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", XmlEscape.escape("&<>\"'"));
        assertEquals("plain", XmlEscape.escape("plain"));
    }

    @Test
    void decodesNumericCharacterReferences() throws Exception {
        assertEquals("A", XmlEscape.unescape("&#65;"));
        assertEquals("A", XmlEscape.unescape("&#x41;"));
        assertEquals("A", XmlEscape.unescape("&#X41;"));
        assertEquals("\ud83c\udf89", XmlEscape.unescape("&#x1F389;"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "&",
            "&amp",
            "&bogus;",
            "&;",
            "&#;",
            "&#x;",
            "&#0;",
            "&#xD800;",
            "&#x110000;"
    })
    void rejectsMalformedEntities(String value) {
        assertThrows(PlistFormatException.class, () -> XmlEscape.unescape(value));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> XmlEscape.escape(null));
        assertThrows(NullPointerException.class, () -> XmlEscape.unescape(null));
    }
}
