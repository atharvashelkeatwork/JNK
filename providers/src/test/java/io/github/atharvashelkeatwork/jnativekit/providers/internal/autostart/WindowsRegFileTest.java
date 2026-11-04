package io.github.atharvashelkeatwork.jnativekit.providers.internal.autostart;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tier T1: the temporary {@code .reg} file used for the Windows {@code Run} value.
 *
 * <p>These tests check the documented {@code reg.exe export} shape, the string escaping and the
 * UTF-16LE byte-order mark. They do not prove that a real {@code reg.exe import} accepts the file;
 * the T3 registry round trip must confirm that.
 */
class WindowsRegFileTest {

    private static final String RUN_KEY =
            "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    @Test
    void rendersOneSzValueWithTheExportHeader() {
        String content = WindowsRegFile.render(
                RUN_KEY,
                "com.example.app",
                "\"C:\\App\\app.exe\" \"two words\"");

        assertEquals(
                "Windows Registry Editor Version 5.00\r\n"
                        + "\r\n"
                        + "[" + RUN_KEY + "]\r\n"
                        + "\"com.example.app\"="
                        + "\"\\\"C:\\\\App\\\\app.exe\\\" \\\"two words\\\"\"\r\n",
                content);
    }

    @Test
    void escapesEveryBackslashAndQuoteInTheData() {
        String content = WindowsRegFile.render(RUN_KEY, "com.example.app", "a\\\"b\\\\");

        assertEquals(
                "\"com.example.app\"=\"a\\\\\\\"b\\\\\\\\\"",
                valueLine(content));
    }

    @Test
    void escapesQuotesInTheValueName() {
        String content = WindowsRegFile.render(RUN_KEY, "app\"name", "data");

        assertEquals("\"app\\\"name\"=\"data\"", valueLine(content));
    }

    @Test
    void encodesUtf16LittleEndianWithABom() {
        byte[] encoded = WindowsRegFile.encode("A\u00e9");

        assertArrayEquals(
                new byte[] {(byte) 0xFF, (byte) 0xFE, 'A', 0, (byte) 0xE9, 0},
                encoded);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "plain",
            "two words",
            "\"C:\\Program Files\\App\\app.exe\" \"--flag\"",
            "a\\\"b\\\\",
            "100%PATH%",
            "$HOME",
            "\u00dcn\u00efc\u00f8d\u00e9 \u65e5\u672c"
    })
    void valueDataRoundTripsThroughTheFileEscaping(String data) {
        String line = valueLine(WindowsRegFile.render(RUN_KEY, "com.example.app", data));
        String prefix = "\"com.example.app\"=\"";

        assertEquals(data, unescape(line.substring(prefix.length(), line.length() - 1)));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class,
                () -> WindowsRegFile.render(null, "name", "data"));
        assertThrows(NullPointerException.class,
                () -> WindowsRegFile.render(RUN_KEY, null, "data"));
        assertThrows(NullPointerException.class,
                () -> WindowsRegFile.render(RUN_KEY, "name", null));
        assertThrows(NullPointerException.class, () -> WindowsRegFile.encode(null));
    }

    private static String valueLine(String content) {
        String[] lines = content.split("\r\n", -1);
        return lines[3];
    }

    private static String unescape(String escaped) {
        StringBuilder value = new StringBuilder(escaped.length());
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == '\\' && i + 1 < escaped.length()) {
                char next = escaped.charAt(i + 1);
                if (next == '\\' || next == '"') {
                    value.append(next);
                    i++;
                    continue;
                }
            }
            value.append(c);
        }
        return value.toString();
    }
}
