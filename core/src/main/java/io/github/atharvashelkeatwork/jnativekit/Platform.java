package io.github.atharvashelkeatwork.jnativekit;

/**
 * The desktop platform the library is running on, as reported by the operating system.
 */
public enum Platform {

    /** A Linux desktop session, for example the owner's Cinnamon session on Linux Mint. */
    LINUX,

    /** Microsoft Windows. */
    WINDOWS,

    /** Apple macOS. */
    MACOS,

    /** Any operating system the library does not explicitly know about. */
    OTHER
}
