/**
 * One honest API for desktop notifications, autostart and system tray on Linux, Windows and macOS.
 *
 * <p>The types in this package are the public v0.1 contract: they are immutable, validated on
 * construction where an invariant exists, and they report environment or backend problems as
 * values ({@code Capability} and {@code Result}) instead of throwing when the environment is the
 * reason for a failure.
 *
 * <p>Implementation helpers live in the sibling sub-package
 * {@code io.github.atharvashelkeatwork.jnativekit.internal} and are not part of the public API.
 */
package io.github.atharvashelkeatwork.jnativekit;
