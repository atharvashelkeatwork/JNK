package io.github.atharvashelkeatwork.jnativekit;

/**
 * Autostart support: enable, disable and query whether an application starts at user login.
 *
 * <p>Autostart means: launch when this user logs into a graphical desktop session. It is not
 * system boot, pre-login or per-user-other-than-current.
 */
public interface Autostart extends Feature {

    /**
     * Enables autostart for the given entry. Enabling an entry that already exists with the same
     * content is idempotent and returns {@code CONFIRMED}; an existing entry with different content
     * is overwritten.
     *
     * @param entry the entry to enable; validated by its constructor
     * @return {@code CONFIRMED} when the entry was written and read back correctly, or
     *         {@code FAILED} when the store could not be written or verified
     * @throws NullPointerException     if {@code entry} is null
     * @throws IllegalArgumentException if the entry violates a validation rule
     */
    Result<Void> enable(AutostartEntry entry);

    /**
     * Disables autostart by removing the entry for the given appId. Disabling an absent entry is
     * idempotent and returns {@code CONFIRMED}.
     *
     * @param appId the application identifier; pattern
     *              {@code [A-Za-z0-9][A-Za-z0-9._-]{0,63}}
     * @return {@code CONFIRMED} when the entry was removed (or absent), or {@code FAILED} when the
     *         store could not be read or written
     * @throws NullPointerException     if {@code appId} is null
     * @throws IllegalArgumentException if {@code appId} is not a valid identifier
     */
    Result<Void> disable(String appId);

    /**
     * Returns whether autostart is enabled for the given appId.
     *
     * @param appId the application identifier; pattern
     *              {@code [A-Za-z0-9][A-Za-z0-9._-]{0,63}}
     * @return the found value; {@code CONFIRMED(true)} or {@code CONFIRMED(false)}, or
     *         {@code UNAVAILABLE} / {@code FAILED} when the store cannot be read
     * @throws NullPointerException     if {@code appId} is null
     * @throws IllegalArgumentException if {@code appId} is not a valid identifier
     */
    Result<Boolean> isEnabled(String appId);
}
