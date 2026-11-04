package io.github.atharvashelkeatwork.jnativekit.providers.internal.autostart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tier T1: parsing the output of {@code reg.exe query}.
 *
 * <p>The sample output uses the documented single-value layout. It is <strong>not</strong> a
 * captured fixture from a real Windows machine, so this test proves the parser against the
 * documented shape only. The T3 registry round trip and the T4 manual check must confirm the real
 * format; see the {@code OPEN} section of the milestone report.
 */
class WindowsRegQueryTest {

    private static final String RUN_KEY =
            "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";

    @Test
    void findsAnSzValue() {
        String stdout = "\r\n" + RUN_KEY + "\r\n"
                + "    com.example.app    REG_SZ    \"C:\\Program Files\\App\\app.exe\" \"--flag\"\r\n"
                + "\r\n";

        Optional<String> value = WindowsRegQuery.findValue(stdout, "com.example.app");

        assertEquals(Optional.of("\"C:\\Program Files\\App\\app.exe\" \"--flag\""), value);
    }

    @Test
    void findsAnExpandSzValueWhenItIsNotFirst() {
        String stdout = RUN_KEY + "\n"
                + "    other    REG_SZ    other-data\n"
                + "    com.example.app    REG_EXPAND_SZ    %LOCALAPPDATA%\\App\\app.exe\n";

        Optional<String> value = WindowsRegQuery.findValue(stdout, "com.example.app");

        assertEquals(Optional.of("%LOCALAPPDATA%\\App\\app.exe"), value);
    }

    @Test
    void doesNotMatchAPrefixOfAnotherValueName() {
        String stdout = "    com.example.app.extra    REG_SZ    data\n";

        assertEquals(Optional.empty(), WindowsRegQuery.findValue(stdout, "com.example.app"));
    }

    @Test
    void ignoresUnsupportedValueTypes() {
        String stdout = "    com.example.app    REG_DWORD    0x1\n";

        assertEquals(Optional.empty(), WindowsRegQuery.findValue(stdout, "com.example.app"));
    }

    @Test
    void returnsEmptyForMissingOrEmptyOutput() {
        assertEquals(Optional.empty(), WindowsRegQuery.findValue("", "com.example.app"));
        assertEquals(Optional.empty(), WindowsRegQuery.findValue(RUN_KEY + "\r\n", "com.example.app"));
    }

    @Test
    void keepsAnEmptyValueDataString() {
        String stdout = "    com.example.app    REG_SZ    \r\n";

        assertEquals(Optional.of(""), WindowsRegQuery.findValue(stdout, "com.example.app"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ERROR: The system was unable to find the specified registry key or value.",
            "error: the system was UNABLE TO FIND the specified registry key or value.",
            "ERROR: Cannot find the specified registry key or value.",
            "cannot find"
    })
    void recognisesTheEnglishNotFoundMessage(String stderr) {
        assertTrue(WindowsRegQuery.isNotFound(stderr));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "ERROR: Access is denied.",
            "ERROR: El sistema no puede encontrar la clave o el valor del registro especificado.",
            "ERROR: Das System kann den angegebenen Registrierungsschlüssel nicht finden.",
            "WARNING: something else"
    })
    void doesNotTreatOtherErrorsAsNotFound(String stderr) {
        assertFalse(WindowsRegQuery.isNotFound(stderr));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> WindowsRegQuery.findValue(null, "x"));
        assertThrows(NullPointerException.class, () -> WindowsRegQuery.findValue("", null));
        assertThrows(NullPointerException.class, () -> WindowsRegQuery.isNotFound(null));
    }
}
