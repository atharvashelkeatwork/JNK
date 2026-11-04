# ARCHITECTURE

## Modules (Maven multi-module, Java 17)

| Module | Artifact | Contains | Depends on |
|---|---|---|---|
| `core` | `jnativekit-core` | Public API (`docs/API.md`), `Desktop` facade, provider SPI interfaces, resolver, "unavailable" fallbacks | nothing |
| `providers` | `jnativekit-providers` | All OS implementations, process runner, file/registry helpers | `core` |
| `demo` | `jnativekit-demo` (never published) | Demo app + capability report | `core`, `providers` |

Rules:
- `core` must never depend on `providers`. Providers are found at runtime with `ServiceLoader`.
- Every jar sets `Automatic-Module-Name` in its manifest. No `module-info.java` in v0.1.
- No runtime dependencies in `core` or `providers`. Test scope: JUnit 5 only.
- Root groupId: `io.github.atharvashelkeatwork`.

Package layout:

```
core:       <root>                    public API types only (Desktop, Notification, Result, ...)
            <root>.spi                provider interfaces (NotificationsProvider, ...)
            <root>.internal           resolver, unavailable facades, validation helpers

providers:  <root>.providers.linux
            <root>.providers.windows
            <root>.providers.macos
            <root>.providers.awt      tray (shared by all OSes)
            <root>.providers.internal exec (CommandRunner), paths, text escaping
```

## Provider SPI

One interface per feature, extending the public interface:

```java
public interface NotificationsProvider extends Notifications {
    String id();                                // "linux.notify-send"
    boolean supports(Platform platform);        // cheap, no side effects
    int priority();                             // higher wins when several match
}
```
Same for `AutostartProvider` and `TrayProvider`. Registered in
`providers/src/main/resources/META-INF/services/<spi interface name>`.

### Resolution algorithm (in `core.internal`)

1. Load providers with `ServiceLoader` (using the provider interface's defining class loader, not
   the calling thread's context class loader). Catch `ServiceConfigurationError` per provider:
   log at WARNING, skip that provider, keep going. Recovery from a transient `hasNext()` failure is
   bounded so a permanently broken iterator cannot loop forever.
2. Keep providers where `supports(Desktop.platform())` is true.
3. Pick the highest `priority()`. Ties: first by `id()` alphabetical, then by provider class name.
4. If none: install the "unavailable" facade (`UNSUPPORTED_OS`) with a human-readable remedy.
5. Wrap the chosen provider in a guard that converts non-fatal provider failures, including
   `RuntimeException` and non-fatal `Error` such as `AWTError` or `LinkageError`, into a
   `Capability` or `Result`; log the stack trace at WARNING. Provider selection is never repeated at
   call time; `capability()` is re-evaluated on every call.
6. For tests, the resolver takes an injected provider list and `Platform`; `Desktop` has a
   package-private reset hook used only by tests.

Platform detection: `os.name` lower-cased; contains `mac` or `darwin` -> MACOS, contains `win` ->
WINDOWS, contains `linux` -> LINUX, else OTHER. Tests inject a `Platform`; production code never calls
`System.getProperty("os.name")` outside this one class.

## Process execution (`providers.internal.exec`)

```java
interface CommandRunner {
    CommandResult run(List<String> argv, Duration timeout);   // never uses a shell
}
record CommandResult(int exitCode, String stdout, String stderr, boolean timedOut) {}
```

Policy:
- `ProcessBuilder` with an argument list only. No `sh -c`, no `cmd /c`, no string concatenation.
- Capture stdout and stderr, each bounded (64 KiB), on separate threads, so a chatty tool cannot
  deadlock or exhaust memory.
- Default timeout 5 s; on timeout call `destroyForcibly()` and report `timedOut`.
- Locale: do not parse human-readable tool output unless unavoidable; prefer exit codes and
  read-back of files/registry. Where parsing is unavoidable (reg.exe query), document the exact
  format expected and test it with captured real output.
- Tests use a `FakeCommandRunner` that records argv and returns scripted results.

## Feature design per OS

### Notifications (all `DISPATCHED_UNVERIFIED`; no CONFIRMED in v0.1)

| OS | Mechanism | Capability checks | Notes |
|---|---|---|---|
| Linux | `notify-send <title> <body>` (argv; `--` before positional args so titles starting with `-` are safe) | graphical session env var present; `notify-send` on PATH (`BACKEND_MISSING`, remedy: install `libnotify-bin`) | Exit 0 means the notification service accepted it; visibility depends on the desktop. |
| Windows | AWT `TrayIcon.displayMessage` using a short-lived tray icon owned by the provider | `SystemTray.isSupported()`, not headless | Behaviour differs between Windows versions and Focus Assist settings. No Windows machine is available for manual verification; CI and unit-fake tests cover only the provider logic. Real toast notifications need an app identity and are a later provider, not a silent upgrade. |
| macOS | `osascript` with the script passed as separate `-e` lines and the title/body passed as **arguments** (`on run argv ... end run`), never interpolated into script text | `osascript` on PATH | AppleScript injection is the main risk here; unit-test hostile strings (quotes, backslashes, newlines). Not manually verified in v0.1. |

### Autostart (read-back verification)

| OS | Store | Enable | Verify | Disable |
|---|---|---|---|---|
| Linux | `$XDG_CONFIG_HOME/autostart` or `~/.config/autostart`, file `<appId>.desktop` | write Desktop Entry (`Type=Application`, `Name`, `Exec`, `X-GNOME-Autostart-enabled=true`) atomically (temp file + move) | re-read file, parse, compare Exec and Name | delete file; verify absent |
| Windows | `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`, value name = `appId`, type `REG_SZ` | `reg.exe import` of a temporary UTF-16LE `.reg` file via argv list; the file is deleted afterwards | `reg.exe query` and compare value data | `reg.exe delete ... /f`; verify absent |
| macOS | `~/Library/LaunchAgents/<appId>.plist` | write plist (`Label`, `ProgramArguments`, `RunAtLoad=true`), XML-escaped, atomically. Do NOT run `launchctl load` (it would start the app immediately). | re-read and compare | delete file; verify absent |

Details that must be implemented exactly (they were the source of bugs in an earlier attempt):
- **Desktop Entry `Exec` quoting:** follow the Desktop Entry specification: quote arguments that
  contain spaces or reserved characters with double quotes, and escape `"`, `` ` ``, `$` and `\` inside
  them; `%` must be written as `%%`. Unit-test with spaces, quotes and `%`.
- **Windows command line:** build the Run value as `"<exe>" "<arg1>" ...` using the
  `CommandLineToArgvW` rules (backslashes before a quote are doubled, embedded quotes are escaped).
  Unit-test with `C:\Program Files\App\app.exe` and `--config=C:\My Config\settings.json`.
- **Never use `reg add` through a shell**; pass each token as a separate argv element. Do not pass
  the quoted command line as the `/d` value: on Windows, Java's `ProcessBuilder` treats an argument
  that starts and ends with a double quote as already quoted and forwards it unchanged, so
  `reg.exe` splits it at the embedded spaces. `enable` imports a temporary `.reg` file instead
  (UTF-16LE with a BOM, full hive name in the section header, value data escaped as in
  `reg.exe export`) and deletes it in a `finally` block.
- `isEnabled` reads the same store as `enable`. Missing file/key => `CONFIRMED(false)`.
  Permission or parse errors => `FAILED(IO_ERROR)`.
- Paths come from an injected `PathProvider` (home, config dir) so tests use temp directories.
- The Windows provider reports in `detail` that Windows lets users disable Run entries in
  Settings > Apps > Startup; `CONFIRMED` does not cover that.

### Tray (AWT `SystemTray`, one provider for all OSes)

- Capability: `GraphicsEnvironment.isHeadless()` false (`NO_GRAPHICAL_SESSION`),
  `SystemTray.isSupported()` true (`UNSUPPORTED_ENVIRONMENT`).
- `open`: decode PNG with `ImageIO`; create `java.awt.TrayIcon` and `PopupMenu` on the AWT
  event thread; add to `SystemTray`; return a handle.
- Menu item clicks arrive on the AWT thread; the provider immediately hands the user's `Runnable`
  to the single `jnativekit-callbacks` executor, with try/catch + logging.
- `close()`: remove the icon on the AWT thread, shut down per-handle state, idempotent.
- Known risks to verify manually: icon invisible on some Linux desktops, wrong icon size, dark
  theme contrast, macOS needing the AWT toolkit to start on the right thread. Each becomes a row
  in the support matrix, not a silent assumption.
- AWT's Linux tray uses XEmbed. A desktop that only exposes StatusNotifier (for example a Cinnamon
  session whose tray is served by `xapp-sn-watcher`) reports `SystemTray.isSupported() == false`;
  the provider then returns `UNAVAILABLE` with `BACKEND_MISSING`. That is an environment limitation,
  not an icon-visibility bug, and must be recorded as such in the manual checklist.
- The tray provider is a separate module-level piece shared by the Windows notification provider;
  share code through `providers.awt`, not by copy-paste.

## Demo app (`demo`)

The demo is a runnable jar and is never published. It prints the capability table for the current
machine, then offers an interactive menu: send a notification, toggle autostart for the demo,
open one tray icon with a Quit item, or print the report again. Non-interactive flags:
`--report` prints the table and exits; `--autostart` is the login mode and sends a notification,
opens the tray icon and waits for Quit.

The autostart entry uses the current JVM executable plus either `-jar <demo.jar>` or
`-cp <current classpath> <main class>`. This keeps the entry independent of the user's PATH and
of a shell. Packaging uses `maven-shade-plugin` in the demo module only, with the service files
merged so provider discovery still works inside the single jar. Run the demo with
`mvn -B -pl demo -am package exec:java` or `java -jar demo/target/jnativekit-demo-0.1.0.jar`.

## JNA / native calls

Not used in v0.1. Introduce JNA only when a feature cannot be done reliably with shell-outs, files
or AWT, and only through a milestone that says so. If added later, it goes in `providers`, behind
the same SPI, selected by a different provider `id()` and higher `priority()`, with the shell-out
provider retained as a fallback that is reported honestly in `Result.provider`.

## Logging

`System.Logger` named after the class. WARNING for converted failures (with stack trace), DEBUG
for command lines (never log secrets; v0.1 handles none), no INFO noise.

## Build and CI

- Maven, Java 17, `maven-compiler-plugin` with `release 17`, JUnit 5, surefire.
  Check current plugin and JUnit versions when creating the POM; do not copy versions from memory.
- Profile `integration` enables tests tagged `@Tag("integration")`.
- GitHub Actions: matrix `ubuntu-latest`, `windows-latest`, `macos-latest`; `actions/setup-java`
  (Temurin 17); run `mvn -B verify` on every push and pull request; second step runs
  `mvn -B verify -Pintegration`. Upload surefire reports on failure.
- Line endings: `.gitattributes` with `* text=auto eol=lf` for source, so Windows checkouts do not
  produce spurious diffs or broken shell scripts.
