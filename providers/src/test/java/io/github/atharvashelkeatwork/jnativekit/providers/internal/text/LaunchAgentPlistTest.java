package io.github.atharvashelkeatwork.jnativekit.providers.internal.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;

/**
 * Tier T1: rendering and parsing the macOS launch agent property list, including hostile XML.
 *
 * <p>The parser is a dependency-free subset reader, so these tests prove the semantic round trip
 * against this library's own reader. They do not prove that {@code launchd} accepts the file; only
 * the T3 parse on macOS and the T4 login check can claim that, and neither has been run here.
 */
class LaunchAgentPlistTest {

    static Stream<Arguments> hostileValues() {
        String longValue = "long " + "&<>\"'%$\\x".repeat(100);
        return Stream.of(
                Arguments.of("plain"),
                Arguments.of("with space"),
                Arguments.of("amp&ersand"),
                Arguments.of("less<greater>"),
                Arguments.of("double\"quote"),
                Arguments.of("single'quote"),
                Arguments.of("entity&amp;looking"),
                Arguments.of("percent%PATH%"),
                Arguments.of("dollar$HOME"),
                Arguments.of("backslash\\value"),
                Arguments.of("unicode-\u00dcn\u00efc\u00f8d\u00e9-\u65e5\u672c-\ud83c\udf89"),
                Arguments.of(""),
                Arguments.of(longValue));
    }

    @ParameterizedTest
    @MethodSource("hostileValues")
    void semanticRoundTripOfArguments(String argument, @TempDir Path tempDir) throws Exception {
        Path executable = tempDir.resolve("My App & bin");
        AutostartEntry entry = new AutostartEntry(
                "com.example.plist", "Plist App", executable, List.of(argument));

        LaunchAgentPlist.Content parsed =
                LaunchAgentPlist.parse(LaunchAgentPlist.render(entry));

        assertEquals("com.example.plist", parsed.label());
        assertEquals(List.of(executable.toString(), argument), parsed.programArguments());
        assertTrue(parsed.runAtLoad());
    }

    @Test
    void rendersTheExpectedKeys(@TempDir Path tempDir) {
        AutostartEntry entry = new AutostartEntry(
                "com.example.app", "Example", tempDir.resolve("app"), List.of("--flag"));

        String xml = LaunchAgentPlist.render(entry);

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"), xml);
        assertTrue(xml.contains("<key>Label</key>\n    <string>com.example.app</string>\n"), xml);
        assertTrue(xml.contains("<key>ProgramArguments</key>\n    <array>\n"), xml);
        assertTrue(xml.contains("        <string>" + tempDir.resolve("app") + "</string>\n"), xml);
        assertTrue(xml.contains("        <string>--flag</string>\n"), xml);
        assertTrue(xml.contains("<key>RunAtLoad</key>\n    <true/>\n"), xml);
        assertTrue(xml.endsWith("</plist>\n"), xml);
    }

    @Test
    void escapesXmlMetacharactersInText(@TempDir Path tempDir) throws Exception {
        Path executable = tempDir.resolve("a&b");
        AutostartEntry entry = new AutostartEntry(
                "com.example.esc", "A & B < C", executable, List.of("<x>", "\"q\"", "'s'"));

        String xml = LaunchAgentPlist.render(entry);

        assertFalse(xml.contains("<string>A & B"), xml);
        assertTrue(xml.contains("<string>&lt;x&gt;</string>"), xml);
        assertEquals(List.of(executable.toString(), "<x>", "\"q\"", "'s'"),
                LaunchAgentPlist.parse(xml).programArguments());
    }

    @Test
    void parsesUnknownKeysAndNestedValues() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                <plist version="1.0">
                <dict>
                    <key>Label</key>
                    <string>com.example.unknown</string>
                    <key>ProgramArguments</key>
                    <array>
                        <string>/bin/app</string>
                        <string>--flag</string>
                    </array>
                    <key>RunAtLoad</key>
                    <false/>
                    <key>KeepAlive</key>
                    <dict>
                        <key>SuccessfulExit</key>
                        <false/>
                    </dict>
                    <key>ThrottleInterval</key>
                    <integer>30</integer>
                </dict>
                </plist>
                """;

        LaunchAgentPlist.Content parsed = LaunchAgentPlist.parse(xml);

        assertEquals("com.example.unknown", parsed.label());
        assertEquals(List.of("/bin/app", "--flag"), parsed.programArguments());
        assertFalse(parsed.runAtLoad());
    }

    @Test
    void ignoresXmlComments() throws Exception {
        String xml = """
                <plist version="1.0">
                <!-- a comment with <tags> and & entities -->
                <dict>
                    <key>Label</key>
                    <string>com.example.comment</string>
                    <key>ProgramArguments</key>
                    <array>
                        <string>/bin/app</string>
                    </array>
                </dict>
                </plist>
                """;

        LaunchAgentPlist.Content parsed = LaunchAgentPlist.parse(xml);

        assertEquals("com.example.comment", parsed.label());
        assertEquals(List.of("/bin/app"), parsed.programArguments());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "<plist version=\"1.0\"><dict></dict></plist>",
            "<plist version=\"1.0\"><dict><key>ProgramArguments</key><array/></dict></plist>",
            "<plist version=\"1.0\"><dict><key>Label</key><string>x</string></dict></plist>",
            "<plist version=\"1.0\"><dict><key>Label</key><string>x</string>"
                    + "<key>ProgramArguments</key><string>/bin/app</string></dict></plist>",
            "<plist version=\"1.0\"><dict><key>Label</key><string>x</string>"
                    + "<key>ProgramArguments</key><array><integer>1</integer></array></dict></plist>",
            "<plist version=\"1.0\"><dict><key>Label</key><string>&bogus;</string>"
                    + "<key>ProgramArguments</key><array><string>/bin/app</string></array>"
                    + "</dict></plist>",
            "<plist version=\"1.0\"><dict><key>Label</key></plist>",
            "<plist version=\"1.0\"><dict><key>Label</key><string>x</string></dict></plist> trailing",
            "<plist version=\"1.0\"><dict><key>Label</key><string>x</string>"
                    + "<key>ProgramArguments</key><array><string>/bin/app</string></array>"
                    + "</dict></plist><!-- unclosed"
    })
    void rejectsMalformedPropertyLists(String xml) {
        assertThrows(PlistFormatException.class, () -> LaunchAgentPlist.parse(xml));
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> LaunchAgentPlist.parse(null));
        assertThrows(NullPointerException.class, () -> LaunchAgentPlist.render(null));
    }
}
