package io.github.atharvashelkeatwork.jnativekit;

import io.github.atharvashelkeatwork.jnativekit.internal.Validation;

/**
 * A single desktop notification: a title and a body. Immutable and validated on construction.
 *
 * @param title the notification title; non-null, non-blank, at most 256 characters, no NUL
 * @param body  the notification body; non-null, at most 4096 characters, no NUL, may be empty
 */
public record Notification(String title, String body) {

    /**
     * Creates a notification and enforces the invariants.
     *
     * @param title the notification title; non-null, non-blank, at most 256 characters, no NUL
     * @param body  the notification body; non-null, at most 4096 characters, no NUL, may be empty
     * @throws NullPointerException     if the title or the body is null
     * @throws IllegalArgumentException if the title is blank or too long, or the body is too long
     */
    public Notification {
        Validation.requireNotificationTitle(title);
        Validation.requireNotificationBody(body);
    }

    /**
     * Creates a notification from a title and a body, validating both.
     *
     * @param title the notification title; non-null, non-blank, at most 256 characters, no NUL
     * @param body  the notification body; non-null, at most 4096 characters, no NUL, may be empty
     * @return a validated {@code Notification}
     * @throws NullPointerException     if the title or the body is null
     * @throws IllegalArgumentException if the title is blank or too long, or the body is too long
     */
    public static Notification of(String title, String body) {
        return new Notification(title, body);
    }
}
