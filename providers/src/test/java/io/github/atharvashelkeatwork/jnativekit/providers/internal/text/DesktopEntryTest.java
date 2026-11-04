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

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;

/**
 * Tier T1: the Desktop Entry {@code Exec} quoting and string escaping rules from
 * {@code docs/ARCHITECTURE.md}, including hostile inputs.
 *
 * <p>These tests compare the renderer with this library's own parser, plus exact strings for the
 * cases the freedesktop specification spells out. They say nothing about how a real desktop
 * session launches the entry; only the T4 checklist can claim that.
 */
class DesktopEntryTest {

    static Stream<Arguments> hostileArguments() {
        String longArgument = "long " + "x%y$z\\q".repeat(120);
        return Stream.of(
                Arguments.of("plain"),
                Arguments.of("with space"),
                Arguments.of("two  spaces"),
                Arguments.of("tab\tinside"),
                Arguments.of("quote\"inside"),
                Arguments.of("single'quote"),
                Arguments.of("dollar$HOME"),
                Arguments.of("percent%f%"),
                Arguments.of("percent%and%%double"),
                Arguments.of("back\\slash"),
                Arguments.of("back\\slash with space"),
                Arguments.of("trailing backslash\\"),
                Arguments.of("semi;colon"),
                Arguments.of("hash#tag"),
                Arguments.of("amp&pipe|"),
                Arguments.of("less<greater>"),
                Arguments.of("tilde~star*question?"),
                Arguments.of("paren(s)"),
                Arguments.of("backtick`cmd`"),
                Arguments.of("unicode-\u00dcn\u00efc\u00f8d\u00e9-\u65e5\u672c-\ud83c\udf89"),
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of("\u0001control\u0002"),
                Arguments.of(longArgument));
    }

    @ParameterizedTest
    @MethodSource("hostileArguments")
    void execRoundTripsEveryArgument(String argument, @TempDir Path tempDir) throws Exception {
        Path executable = tempDir.resolve("My App %f $HOME `cmd` \u00dc");
        AutostartEntry entry = new AutostartEntry(
                "com.example.hostile", "Hostile App", executable, List.of(argument));

        DesktopEntry.Parsed parsed = DesktopEntry.parse(DesktopEntry.render(entry));

        assertEquals("Hostile App", parsed.name());
        assertEquals(List.of(executable.toString(), argument), parsed.exec());
    }

    @Test
    void rendersTheSpecificationShape(@TempDir Path tempDir) {
        Path executable = tempDir.resolve("app");
        AutostartEntry entry = new AutostartEntry(
                "com.example.app", "Example App", executable, List.of("--flag", "two words"));

        String rendered = DesktopEntry.render(entry);

        assertTrue(rendered.startsWith("[Desktop Entry]\n"), rendered);
        assertTrue(rendered.contains("Type=Application\n"), rendered);
        assertTrue(rendered.contains("Name=Example App\n"), rendered);
        assertTrue(rendered.contains("Exec=" + DesktopEntry.quoteExecArgument(executable.toString())
                + " --flag \"two words\"\n"), rendered);
        assertTrue(rendered.endsWith("\n"), rendered);
        assertTrue(rendered.contains("X-GNOME-Autostart-enabled=true\n"), rendered);
    }

    @Test
    void doublesPercentSignsAndEscapesReservedCharacters() {
        assertEquals("100%%", DesktopEntry.quoteExecArgument("100%"));
        assertEquals("\"two words\"", DesktopEntry.quoteExecArgument("two words"));
        assertEquals("\"a\\\"b\"", DesktopEntry.quoteExecArgument("a\"b"));
        assertEquals("\"a\\\\\\\\b\"", DesktopEntry.quoteExecArgument("a\\b"));
        assertEquals("\"a\\$b\"", DesktopEntry.quoteExecArgument("a$b"));
        assertEquals("\"a\\`b\"", DesktopEntry.quoteExecArgument("a`b"));
        assertEquals("\"\"", DesktopEntry.quoteExecArgument(""));
        assertEquals("plain", DesktopEntry.quoteExecArgument("plain"));
    }

    @Test
    void escapesControlCharactersInStringValues() {
        assertEquals("a\\nb\\tc\\rd\\\\e", DesktopEntry.escapeStringValue("a\nb\tc\rd\\e"));
        assertEquals("100%", DesktopEntry.escapeStringValue("100%"));
    }

    @Test
    void nameRoundTripsWithHostileButAllowedCharacters(@TempDir Path tempDir) throws Exception {
        String displayName = "Name % with \\ backslash & <xml> \"quotes\" 'single' \u00e9";
        AutostartEntry entry = new AutostartEntry(
                "com.example.name", displayName, tempDir.resolve("app"), List.of());

        DesktopEntry.Parsed parsed = DesktopEntry.parse(DesktopEntry.render(entry));

        assertEquals(displayName, parsed.name());
        assertEquals(List.of(tempDir.resolve("app").toString()), parsed.exec());
    }

    @Test
    void ignoresUnknownKeysCommentsAndOtherGroups() throws Exception {
        String content = """
                # a comment
                [Other Group]
                Name=Wrong
                Exec=/wrong

                [Desktop Entry]
                Type=Application
                Name=Right
                Comment=ignored
                Exec=/bin/right --flag
                """;

        DesktopEntry.Parsed parsed = DesktopEntry.parse(content);

        assertEquals("Right", parsed.name());
        assertEquals(List.of("/bin/right", "--flag"), parsed.exec());
    }

    @Test
    void acceptsCrLfLineEndings() throws Exception {
        String content = "[Desktop Entry]\r\nName=Windows File\r\nExec=/bin/app arg\r\n";

        DesktopEntry.Parsed parsed = DesktopEntry.parse(content);

        assertEquals("Windows File", parsed.name());
        assertEquals(List.of("/bin/app", "arg"), parsed.exec());
    }

    @Test
    void appliesStringEscapesToUnquotedExecArguments() throws Exception {
        DesktopEntry.Parsed parsed = DesktopEntry.parse(
                "[Desktop Entry]\nName=A\nExec=/bin/app a\\sb\n");

        assertEquals(List.of("/bin/app", "a b"), parsed.exec());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "Name=A\nExec=/bin/app\n",
            "[Desktop Entry]\nExec=/bin/app\n",
            "[Desktop Entry]\nName=A\n",
            "[Desktop Entry]\nName=A\nExec=\n",
            "[Desktop Entry]\nName=A\nExec=\"unterminated\n",
            "[Desktop Entry]\nName=A\nExec=\"trailing\\",
            "[Desktop Entry]\nName=A\nExec=/bin/app \"a\\\""
    })
    void rejectsMalformedFiles(String content) {
        assertThrows(DesktopEntryFormatException.class, () -> DesktopEntry.parse(content));
    }

    @Test
    void parserRejectsNull() {
        assertThrows(NullPointerException.class, () -> DesktopEntry.parse(null));
        assertThrows(NullPointerException.class, () -> DesktopEntry.render(null));
        assertThrows(NullPointerException.class, () -> DesktopEntry.quoteExecArgument(null));
        assertThrows(NullPointerException.class, () -> DesktopEntry.escapeStringValue(null));
    }

    @Test
    void parsesEveryArgumentOfAFullCommandLine(@TempDir Path tempDir) throws Exception {
        List<String> arguments = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            arguments.add("arg " + i + " % v");
        }
        Path executable = tempDir.resolve("My App").resolve("app");
        AutostartEntry entry = new AutostartEntry(
                "com.example.many", "Many", executable, arguments);

        List<String> expected = new ArrayList<>();
        expected.add(executable.toString());
        expected.addAll(arguments);

        assertEquals(expected, DesktopEntry.parse(DesktopEntry.render(entry)).exec());
    }
}
