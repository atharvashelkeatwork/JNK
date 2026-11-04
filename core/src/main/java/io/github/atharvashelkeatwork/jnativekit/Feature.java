package io.github.atharvashelkeatwork.jnativekit;

/**
 * A feature that reports a {@link Capability} and performs actions for the current environment.
 */
public interface Feature {

    /**
     * Best assessment right now. Not cached by the library; may change between calls.
     *
     * @return the current capability; never {@code null}
     */
    Capability capability();
}
