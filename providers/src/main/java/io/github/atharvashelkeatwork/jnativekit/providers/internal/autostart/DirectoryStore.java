package io.github.atharvashelkeatwork.jnativekit.providers.internal.autostart;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

import io.github.atharvashelkeatwork.jnativekit.Capability;
import io.github.atharvashelkeatwork.jnativekit.Reason;

/**
 * File-store helpers shared by the Linux and macOS autostart providers.
 *
 * <p>Internal to the {@code providers} module. The helpers never create the store during a
 * capability check and write through a temporary file in the same directory followed by an atomic
 * move, so a desktop session never observes a half-written entry.
 */
public final class DirectoryStore {

    private static final System.Logger LOG = System.getLogger(DirectoryStore.class.getName());

    private DirectoryStore() {
    }

    /**
     * Assesses whether a directory can hold autostart entries.
     *
     * <p>Nothing is created: the check walks up to the nearest existing ancestor and asks whether it
     * is a writable directory. A path occupied by a regular file is reported as
     * {@link Capability.Status#PERMISSION_DENIED}.
     *
     * @param directory the directory the provider would write into; never {@code null}
     * @return the capability; never {@code null}
     * @throws NullPointerException if {@code directory} is {@code null}
     */
    public static Capability capability(Path directory) {
        Objects.requireNonNull(directory, "directory");
        Path existing = directory.toAbsolutePath();
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return new Capability(
                    Capability.Status.PERMISSION_DENIED,
                    Reason.PERMISSION_DENIED,
                    "No existing parent directory for " + directory,
                    "Make sure the home directory exists and is writable");
        }
        if (!Files.isDirectory(existing)) {
            return new Capability(
                    Capability.Status.PERMISSION_DENIED,
                    Reason.PERMISSION_DENIED,
                    existing + " is not a directory",
                    "Remove the file that occupies the autostart path " + existing);
        }
        if (!Files.isWritable(existing)) {
            return new Capability(
                    Capability.Status.PERMISSION_DENIED,
                    Reason.PERMISSION_DENIED,
                    existing + " is not writable",
                    "Grant write permission to " + existing);
        }
        return new Capability(
                Capability.Status.AVAILABLE,
                Reason.NONE,
                "The autostart directory is writable: " + directory,
                null);
    }

    /**
     * Writes a file atomically, creating parent directories as needed.
     *
     * <p>The content is written to a temporary file in the target directory and then moved over the
     * target with {@link StandardCopyOption#ATOMIC_MOVE} when the file system supports it. The
     * temporary file is removed when anything fails.
     *
     * @param file    the target file; never {@code null}
     * @param content the UTF-8 content; never {@code null}
     * @throws IOException          if the parent cannot be created or the file cannot be written
     * @throws NullPointerException if an argument is {@code null}
     */
    public static void writeAtomically(Path file, String content) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(content, "content");
        Path directory = file.getParent();
        if (directory == null) {
            throw new IOException("Cannot write " + file + ": it has no parent directory");
        }
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, "." + file.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            Files.writeString(
                    temporary,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                LOG.log(System.Logger.Level.DEBUG,
                        "Atomic move is not supported for " + file + "; using a plain move", e);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
