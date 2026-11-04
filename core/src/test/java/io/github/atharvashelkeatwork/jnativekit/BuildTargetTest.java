package io.github.atharvashelkeatwork.jnativekit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * Guards the Milestone 0 build contract: every module must compile to Java 17 bytecode.
 *
 * <p>This test reads its own class file from the test classpath and checks the class file format
 * major version. Java 17 is major version 61. It replaces nothing and proves nothing about the
 * public API; it exists so a misconfigured {@code maven.compiler.release} fails the build on every
 * OS instead of silently producing a newer class file.
 */
class BuildTargetTest {

    /** Class file format major version used by Java 17. */
    private static final int JAVA_17_MAJOR_VERSION = 61;

    @Test
    void classesAreCompiledForJava17() throws IOException {
        byte[] classBytes;
        try (InputStream in = BuildTargetTest.class.getResourceAsStream("BuildTargetTest.class")) {
            assertNotNull(in, "own class file must be readable from the test classpath");
            classBytes = in.readAllBytes();
        }

        int majorVersion = ((classBytes[6] & 0xFF) << 8) | (classBytes[7] & 0xFF);
        assertEquals(JAVA_17_MAJOR_VERSION, majorVersion,
                "maven.compiler.release must target Java 17 (class file major version 61)");
    }
}
