package io.github.atharvashelkeatwork.jnativekit.internal;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Internal validation helpers shared by the public core records.
 *
 * <p>This class is an implementation detail. It is public only because it is used across packages,
 * and it is not part of the public API; the signatures, rules and messages of the public types are
 * the contract.
 *
 * @see io.github.atharvashelkeatwork.jnativekit.AutostartEntry
 * @see io.github.atharvashelkeatwork.jnativekit.Notification
 * @see io.github.atharvashelkeatwork.jnativekit.TrayIcon
 */
public final class Validation {

    /** Maximum length of a display name, in characters. */
    private static final int MAX_DISPLAY_NAME_LENGTH = 128;

    /** Maximum length of a tooltip, in characters. */
    private static final int MAX_TOOLTIP_LENGTH = 128;

    /** Maximum length of a notification title, in characters. */
    private static final int MAX_TITLE_LENGTH = 256;

    /** Maximum length of a notification body, in characters. */
    private static final int MAX_BODY_LENGTH = 4096;

    private static final Pattern APP_ID_PATTERN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private Validation() {
    }

    /**
     * Validates an application id and returns it unchanged.
     *
     * @param appId the application identifier to validate
     * @return the validated application id
     * @throws NullPointerException     if the app id is null
     * @throws IllegalArgumentException if the app id does not match
     *                                  {@code [A-Za-z0-9][A-Za-z0-9._-]{0,63}}
     */
    public static String requireAppId(String appId) {
        Objects.requireNonNull(appId, "appId");
        if (!APP_ID_PATTERN.matcher(appId).matches()) {
            throw new IllegalArgumentException(
                    "appId must match [A-Za-z0-9][A-Za-z0-9._-]{0,63}");
        }
        return appId;
    }

    /**
     * Validates a display name and returns it unchanged.
     *
     * @param displayName the human readable application name to validate
     * @return the validated display name
     * @throws NullPointerException     if the display name is null
     * @throws IllegalArgumentException if it is blank, too long, or contains control characters
     */
    public static String requireDisplayName(String displayName) {
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        if (displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("displayName must be at most 128 characters");
        }
        for (int i = 0; i < displayName.length(); i++) {
            if (Character.isISOControl(displayName.charAt(i))) {
                throw new IllegalArgumentException("displayName must not contain control characters");
            }
        }
        return displayName;
    }

    /**
     * Validates an executable path and returns it unchanged.
     *
     * @param executable the executable path to validate; existence is not checked
     * @return the validated executable path
     * @throws NullPointerException     if the executable is null
     * @throws IllegalArgumentException if the path is not absolute or contains NUL/CR/LF
     */
    public static Path requireAbsoluteExecutable(Path executable) {
        Objects.requireNonNull(executable, "executable");
        if (!executable.isAbsolute()) {
            throw new IllegalArgumentException("executable must be an absolute path");
        }
        String value = executable.toString();
        if (value.indexOf('\u0000') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("executable must not contain NUL or newline characters");
        }
        return executable;
    }

    /**
     * Validates a launch argument and returns it unchanged.
     *
     * @param argument the argument to validate
     * @return the validated argument
     * @throws NullPointerException     if the argument is null
     * @throws IllegalArgumentException if the argument contains NUL, CR or LF
     */
    public static String requireArgument(String argument) {
        Objects.requireNonNull(argument, "argument");
        if (argument.indexOf('\u0000') >= 0 || argument.indexOf('\r') >= 0 || argument.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("argument must not contain NUL, CR or LF");
        }
        return argument;
    }

    /**
     * Validates a notification title and returns it unchanged.
     *
     * @param title the notification title to validate
     * @return the validated title
     * @throws NullPointerException     if the title is null
     * @throws IllegalArgumentException if the title is blank or longer than 256 characters, or
     *                                  contains a NUL character
     */
    public static String requireNotificationTitle(String title) {
        Objects.requireNonNull(title, "title");
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("title must be at most 256 characters");
        }
        if (containsNul(title)) {
            throw new IllegalArgumentException("title must not contain NUL");
        }
        return title;
    }

    /**
     * Validates a notification body and returns it unchanged.
     *
     * @param body the notification body to validate
     * @return the validated body
     * @throws NullPointerException     if the body is null
     * @throws IllegalArgumentException if the body is longer than 4096 characters or contains a
     *                                  NUL character
     */
    public static String requireNotificationBody(String body) {
        Objects.requireNonNull(body, "body");
        if (body.length() > MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("body must be at most 4096 characters");
        }
        if (containsNul(body)) {
            throw new IllegalArgumentException("body must not contain NUL");
        }
        return body;
    }

    /**
     * Validates a tray tooltip and returns it unchanged.
     *
     * @param tooltip the tooltip to validate; may be empty
     * @return the validated tooltip
     * @throws NullPointerException     if the tooltip is null
     * @throws IllegalArgumentException if the tooltip is longer than 128 characters
     */
    public static String requireTooltip(String tooltip) {
        Objects.requireNonNull(tooltip, "tooltip");
        if (tooltip.length() > MAX_TOOLTIP_LENGTH) {
            throw new IllegalArgumentException("tooltip must be at most 128 characters");
        }
        return tooltip;
    }

    /**
     * Validates icon bytes and returns a defensive copy of them.
     *
     * @param png the icon bytes to validate
     * @return a defensive copy of the valid PNG bytes
     * @throws NullPointerException     if the PNG bytes are null
     * @throws IllegalArgumentException if the bytes are shorter than the PNG signature or do not
     *                                  start with it
     */
    public static byte[] copyAndValidatePng(byte[] png) {
        Objects.requireNonNull(png, "png");
        if (png.length < PNG_SIGNATURE.length) {
            throw new IllegalArgumentException("png must be a non-empty PNG");
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if ((png[i] & 0xFF) != (PNG_SIGNATURE[i] & 0xFF)) {
                throw new IllegalArgumentException("png must be a PNG file");
            }
        }
        return png.clone();
    }

    private static boolean containsNul(String value) {
        return value.indexOf('\u0000') >= 0;
    }
}
