package io.github.atharvashelkeatwork.jnativekit.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.atharvashelkeatwork.jnativekit.AutostartEntry;

class LaunchCommandTest {

    @Test
    void usesJavaExeOnWindowsAndJavaElsewhere() {
        Path home = Path.of("java-home");

        assertEquals(home.resolve("bin").resolve("java"), LaunchCommand.javaExecutable(home, false));
        assertEquals(home.resolve("bin").resolve("java.exe"), LaunchCommand.javaExecutable(home, true));
    }

    @Test
    void buildsAJarLaunchWhenRunningFromAJar() {
        Path jar = Path.of("build", "jnativekit-demo.jar").toAbsolutePath();

        List<String> arguments = LaunchCommand.arguments(jar, "ignored");

        assertEquals("-jar", arguments.get(0));
        assertEquals(jar, Path.of(arguments.get(1)));
        assertEquals("--autostart", arguments.get(2));
    }

    @Test
    void buildsAClasspathLaunchWhenRunningFromClasses() {
        Path classes = Path.of("target", "classes").toAbsolutePath();

        List<String> arguments = LaunchCommand.arguments(classes, classes.toString());

        assertEquals("-cp", arguments.get(0));
        assertEquals(classes.toString(), arguments.get(1));
        assertEquals(Demo.class.getName(), arguments.get(2));
        assertEquals("--autostart", arguments.get(3));
    }

    @Test
    void nullCodeSourceFallsBackToTheClasspathLaunch() {
        List<String> arguments = LaunchCommand.arguments(null, "core.jar");

        assertEquals("-cp", arguments.get(0));
        assertEquals("core.jar", arguments.get(1));
        assertEquals(Demo.class.getName(), arguments.get(2));
        assertEquals("--autostart", arguments.get(3));
    }

    @Test
    void demoEntryIsAValidAbsoluteAutostartEntry() {
        Path javaHome = Path.of(System.getProperty("java.io.tmpdir")).resolve("java-home").toAbsolutePath();
        Path codeSource = Path.of(System.getProperty("java.io.tmpdir"))
                .resolve("jnativekit-demo.jar").toAbsolutePath();

        AutostartEntry entry = LaunchCommand.demoEntry(javaHome, false, codeSource, "ignored");

        assertEquals(DemoAssets.appId(), entry.appId());
        assertEquals(DemoAssets.displayName(), entry.displayName());
        assertEquals(javaHome.resolve("bin").resolve("java"), entry.executable());
        assertTrue(entry.executable().isAbsolute());
        assertEquals(List.of("-jar", codeSource.toString(), "--autostart"), entry.arguments());
    }
}
