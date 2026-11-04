package io.github.atharvashelkeatwork.jnativekit.providers.internal.autostart;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Builds the temporary {@code .reg} file that writes one Windows {@code Run} value.
 *
 * <p>Internal to the {@code providers} module. The provider cannot pass a quoted command line as
 * the {@code /d} value of {@code reg.exe add}: when such an argument starts and ends with a double
 * quote, Java's {@link ProcessBuilder} treats it as already quoted and forwards it unchanged, so
 * {@code reg.exe} splits it at the embedded spaces. The provider therefore imports a small
 * generated file instead.
 *
 * <p>The format is the one {@code reg.exe export} produces: a version header, one key line and one
 * {@code REG_SZ} value line. The key line uses the full hive name, such as
 * {@code HKEY_CURRENT_USER}, rather than the {@code HKCU} abbreviation. Inside a quoted string,
 * {@code \} is escaped as {@code \\} and {@code "} as {@code \"}. The file is encoded as UTF-16LE
 * with a byte-order mark, which is the encoding {@code reg.exe import} expects.
 *
 * @see <a href="https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/reg-import">
 *      reg import</a>
 */
public final class WindowsRegFile {

    private static final String HEADER = "Windows Registry Editor Version 5.00";

    private WindowsRegFile() {
    }

    /**
     * Renders the text of a {@code .reg} file that sets one {@code REG_SZ} value.
     *
     * @param key       the registry key path, with single backslashes; never {@code null}
     * @param valueName the value name; never {@code null}
     * @param data      the value data; never {@code null}
     * @return the file text with CRLF line endings
     * @throws NullPointerException if an argument is {@code null}
     */
    public static String render(String key, String valueName, String data) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(valueName, "valueName");
        Objects.requireNonNull(data, "data");
        return HEADER + "\r\n"
                + "\r\n"
                + "[" + key + "]\r\n"
                + "\"" + escape(valueName) + "\"=\"" + escape(data) + "\"\r\n";
    }

    /**
     * Encodes file text as UTF-16LE with a byte-order mark.
     *
     * @param content the file text; never {@code null}
     * @return the encoded bytes, starting with {@code FF FE}
     * @throws NullPointerException if {@code content} is {@code null}
     */
    public static byte[] encode(String content) {
        Objects.requireNonNull(content, "content");
        byte[] text = content.getBytes(StandardCharsets.UTF_16LE);
        byte[] encoded = new byte[text.length + 2];
        encoded[0] = (byte) 0xFF;
        encoded[1] = (byte) 0xFE;
        System.arraycopy(text, 0, encoded, 2, text.length);
        return encoded;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
