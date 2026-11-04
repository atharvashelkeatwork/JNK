package io.github.atharvashelkeatwork.jnativekit.providers.internal.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tier T1: the {@code CommandLineToArgvW} quoting used for the Windows {@code Run} value.
 *
 * <p>The test parser below is an independent implementation of the documented
 * {@code CommandLineToArgvW} rules, so a round trip here proves the encoder against the rules this
 * library targets. It does not prove how Windows itself consumes the value; that needs the T3
 * {@code reg.exe} round trip and the T4 login check.
 */
class WindowsCommandLineTest {

    static Stream<Arguments> hostileTokens() {
        String longToken = "long " + "a\"b\\c%d$e".repeat(100);
        return Stream.of(
                Arguments.of("plain"),
                Arguments.of("with space"),
                Arguments.of("two  spaces"),
                Arguments.of("tab\tinside"),
                Arguments.of("quote\"inside"),
                Arguments.of("only\"quote"),
                Arguments.of("trailing backslash\\"),
                Arguments.of("two trailing\\\\"),
                Arguments.of("back\\slash"),
                Arguments.of("back\\\"slash quote"),
                Arguments.of("dollar$HOME"),
                Arguments.of("percent%PATH%"),
                Arguments.of("semi;colon&amp|pipe<gt>"),
                Arguments.of("unicode-\u00dcn\u00efc\u00f8d\u00e9-\u65e5\u672c-\ud83c\udf89"),
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of(longToken));
    }

    @ParameterizedTest
    @MethodSource("hostileTokens")
    void quoteRoundTripsThroughTheWindowsRules(String token) {
        String quoted = WindowsCommandLine.quote(token);

        assertTrue(quoted.startsWith("\"") && quoted.endsWith("\""), quoted);
        assertEquals(List.of(token), parse(quoted));
    }

    @Test
    void buildRoundTripsExecutableAndArguments(@TempDir Path tempDir) {
        List<String> arguments = List.of("--flag", "two words", "a\"b", "trailing\\", "");

        Path executable = tempDir.resolve("My App").resolve("app.exe");
        String commandLine = WindowsCommandLine.build(executable, arguments);

        List<String> expected = new ArrayList<>();
        expected.add(executable.toString());
        expected.addAll(arguments);
        assertEquals(expected, parse(commandLine));
    }

    @Test
    void usesTheDocumentedExactShapes() {
        assertEquals("\"plain\"", WindowsCommandLine.quote("plain"));
        assertEquals("\"\"", WindowsCommandLine.quote(""));
        assertEquals("\"a b\"", WindowsCommandLine.quote("a b"));
        assertEquals("\"a\\\"b\"", WindowsCommandLine.quote("a\"b"));
        assertEquals("\"a\\\\\"", WindowsCommandLine.quote("a\\"));
        assertEquals("\"a\\\\\\\"b\"", WindowsCommandLine.quote("a\\\"b"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"plain", "a b", "a\"b", "\\", "\\\\\"", "x y z"})
    void everyTokenIsQuoted(String token) {
        String quoted = WindowsCommandLine.quote(token);

        assertEquals('"', quoted.charAt(0));
        assertEquals('"', quoted.charAt(quoted.length() - 1));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> WindowsCommandLine.quote(null));
        assertThrows(NullPointerException.class, () -> WindowsCommandLine.build(null, List.of()));
        assertThrows(NullPointerException.class, () -> WindowsCommandLine.build(
                java.nio.file.Path.of("/opt/app"), null));
    }

    /**
     * Parses a command line with the {@code CommandLineToArgvW} rules.
     *
     * @param commandLine the command line
     * @return the decoded tokens
     */
    private static List<String> parse(String commandLine) {
        List<String> tokens = new ArrayList<>();
        int index = 0;
        while (index < commandLine.length()) {
            while (index < commandLine.length() && isSeparator(commandLine.charAt(index))) {
                index++;
            }
            if (index >= commandLine.length()) {
                break;
            }
            StringBuilder token = new StringBuilder();
            boolean inQuotes = false;
            while (index < commandLine.length()) {
                char c = commandLine.charAt(index);
                if (!inQuotes && isSeparator(c)) {
                    break;
                }
                if (c == '\\') {
                    int backslashes = 0;
                    while (index < commandLine.length() && commandLine.charAt(index) == '\\') {
                        backslashes++;
                        index++;
                    }
                    if (index < commandLine.length() && commandLine.charAt(index) == '"') {
                        token.append("\\".repeat(backslashes / 2));
                        if (backslashes % 2 == 1) {
                            token.append('"');
                            index++;
                        }
                    } else {
                        token.append("\\".repeat(backslashes));
                    }
                } else if (c == '"') {
                    if (inQuotes && index + 1 < commandLine.length()
                            && commandLine.charAt(index + 1) == '"') {
                        token.append('"');
                        index += 2;
                    } else {
                        inQuotes = !inQuotes;
                        index++;
                    }
                } else {
                    token.append(c);
                    index++;
                }
            }
            tokens.add(token.toString());
        }
        return tokens;
    }

    private static boolean isSeparator(char c) {
        return c == ' ' || c == '\t';
    }
}
