# API contract (v0.1)

This file is the source of truth. Signatures here are frozen for v0.1 unless the owner
approves a change. Package root: `io.github.atharvashelkeatwork.jnativekit`.

## 1. Entry point

```java
public final class Desktop {
    public static Notifications notifications();
    public static Autostart     autostart();
    public static Tray          tray();
    public static Platform      platform();   // LINUX, WINDOWS, MACOS, OTHER
}
```

- Accessors return process-wide singletons, resolved lazily and thread-safe.
- They never return null and never throw. If no provider matches, they return a facade whose
  `capability()` is `UNSUPPORTED_OS` and whose actions return `UNAVAILABLE`.

## 2. Core types

```java
public interface Feature {
    /** Best assessment right now. NOT cached by the library; may change between calls. */
    Capability capability();
}

public record Capability(Status status, Reason reason, String detail, String remedy) {
    public enum Status { AVAILABLE, UNSUPPORTED_OS, UNSUPPORTED_ENVIRONMENT,
                         MISSING_DEPENDENCY, PERMISSION_DENIED }
    // AVAILABLE <=> reason == Reason.NONE. detail/remedy are human text, never parsed by code.
    // remedy is null when AVAILABLE or when nothing useful can be said.
}

public enum Reason {
    NONE,
    UNSUPPORTED_PLATFORM,   // no provider for this OS
    NO_GRAPHICAL_SESSION,   // headless, no DISPLAY/WAYLAND_DISPLAY, etc.
    BACKEND_MISSING,        // required tool/service not found (e.g. notify-send)
    PERMISSION_DENIED,
    INVALID_STATE,          // e.g. tray handle already closed
    TIMEOUT,                // external process exceeded its time limit
    IO_ERROR,               // file or registry read/write failed
    BACKEND_ERROR,          // tool ran and reported failure (non-zero exit, exception)
    VERIFICATION_FAILED     // action ran but read-back did not match
}

public record Result<T>(Status status, T value, String provider, Reason reason, String detail) {
    public enum Status { CONFIRMED, DISPATCHED_UNVERIFIED, UNAVAILABLE, FAILED }

    public boolean isConfirmed();
    public boolean isDispatchedUnverified();
    public boolean isUnavailable();
    public boolean isFailed();
    // Deliberately NO isSuccess(): callers must choose how to treat CONFIRMED vs DISPATCHED_UNVERIFIED.
}
```

`Result<Void>` is used for plain actions (`value == null`).

### Result invariants

| status | reason | value | provider |
|---|---|---|---|
| CONFIRMED | NONE | present when `T` is not `Void` | non-null |
| DISPATCHED_UNVERIFIED | NONE | present when `T` is not `Void` | non-null |
| UNAVAILABLE | never NONE | null | may be null if no provider was chosen |
| FAILED | never NONE | null | non-null |

`provider` names the implementation actually used, e.g. `linux.notify-send`,
`windows.reg-exe`, `awt.systemtray`. `detail` is for humans and logs; applications must
branch on `status` and `reason`, never on `detail`.

### Construction rules (enforced by the constructors)

- `status`, `reason` and `detail` must not be null in `Capability` and `Result`; `detail` may be
  empty. A null argument is rejected with `NullPointerException`.
- All length limits in this document are counted with `String.length()` (UTF-16 code units).
- `Capability.remedy` must be null when the status is `AVAILABLE`; it may be null for any other
  status.
- `Result` value rules: `UNAVAILABLE` and `FAILED` must have a `null` value. For `CONFIRMED` and
  `DISPATCHED_UNVERIFIED`, a value is only legal for types other than `Void`; the constructor cannot
  distinguish `Result<Void>` from a non-`Void` type holding `null`, so that part of the table is
  documented rather than runtime-checked.

### What each status guarantees

| Status | Guarantee |
|---|---|
| `CONFIRMED` | The provider verified the postcondition **it documents below**, as far as the OS allows. |
| `DISPATCHED_UNVERIFIED` | The OS or tool accepted the request. The final user-visible effect cannot be verified. |
| `UNAVAILABLE` | The operation cannot be performed in this environment. Nothing was attempted. |
| `FAILED` | The operation was attempted and failed. State may be partly changed; `detail` says what is known. |

### What CONFIRMED means per feature (do not widen)

| Feature | CONFIRMED means | Never means |
|---|---|---|
| Notifications | (not reachable in v0.1; always `DISPATCHED_UNVERIFIED`) | the user saw it; Do Not Disturb off |
| Autostart enable | Entry was written and **read back** equal to what was requested | the OS will definitely run it (user may have disabled it in OS startup settings) |
| Autostart disable | Entry was removed and read back as absent | - |
| Autostart isEnabled | The store was read successfully; `value` is the true/false found | - |
| Tray open | AWT accepted the icon (`SystemTray.add` returned without error) | the icon is visible (some desktops hide legacy tray icons) |

## 3. Notifications

```java
public interface Notifications extends Feature {
    Result<Void> show(Notification notification);
}

public record Notification(String title, String body) {
    public static Notification of(String title, String body);
}
```

Validation (throws `IllegalArgumentException`/`NullPointerException`):
- `title`: non-null, non-blank, <= 256 chars, no NUL.
- `body`: non-null (may be empty), <= 4096 chars, no NUL.
v0.1 has no icon, urgency, actions or sounds.

## 4. Autostart

```java
public interface Autostart extends Feature {
    Result<Void>    enable(AutostartEntry entry);   // idempotent: same entry twice -> CONFIRMED
    Result<Void>    disable(String appId);          // idempotent: absent entry -> CONFIRMED
    Result<Boolean> isEnabled(String appId);        // CONFIRMED(true|false), UNAVAILABLE, or FAILED
}

public record AutostartEntry(String appId, String displayName, Path executable, List<String> arguments) {}
```

- "Autostart" means: launch when this user logs into a graphical desktop session.
  Not at boot. Not before login. Not for other users.
- `appId` format: `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`; no other characters. It is a stable
  identifier, never a path. Providers derive file names / registry value names from it.
  Reverse-DNS style (`com.example.myapp`) is recommended and required to look sensible on macOS.
- `displayName`: non-blank, <= 128 chars, no control characters.
- `executable`: must be an absolute path. Existence is NOT checked (installers may run first).
- `arguments`: immutable copy; each element non-null, no NUL, no CR/LF.
- `isEnabled` must never return `false` just because the store could not be read.
  Unreadable store => `FAILED` (or `UNAVAILABLE`), never `CONFIRMED(false)`.
- Re-enabling an entry whose appId already exists with different content overwrites it
  (documented behaviour, result `CONFIRMED` after read-back).
- Only entries created by this library are managed. `disable` removes the entry for that appId;
  it must not touch other apps' entries.
- `AutostartEntry.arguments()` returns an unmodifiable copy; mutating the caller's list after
  construction does not change the entry.

## 5. Tray

```java
public interface Tray extends Feature {
    /** Opens one tray icon. Each successful call returns a new, independent handle. */
    Result<TrayHandle> open(TrayIcon icon);
}

public interface TrayHandle extends AutoCloseable {
    boolean isOpen();
    @Override void close();           // idempotent, never throws, safe from any thread
}

public record TrayIcon(byte[] png, String tooltip, List<TrayMenuItem> menu) {}
public record TrayMenuItem(String label, Runnable action) {
    public static TrayMenuItem item(String label, Runnable action);
    public static TrayMenuItem separator();
}
```

Rules:
- `png`: non-empty, starts with the 8-byte PNG signature, defensively copied; full decoding happens
  when the tray provider opens the icon. `TrayIcon.png()` returns a defensive copy.
- `tooltip`: <= 128 chars, may be empty. Menu: at most 20 items, labels non-blank (except separators).
- Menu is fixed at open time in v0.1.

Construction details:
- `TrayMenuItem.separator()` is a `TrayMenuItem` with an empty label and a null action.
- `TrayIcon.menu()` returns an unmodifiable copy; mutating the caller's menu list afterwards does
  not affect the icon. `TrayIcon` equality compares PNG bytes by content, and `toString` does not
  expose array identity.
- `Tray` is a factory/feature; the live icon is a `TrayHandle`. A closed handle is permanently
  closed; open a new one to show the icon again. This is why `Desktop.tray()` can be a singleton.
- `open` when `capability()` is not `AVAILABLE` returns `UNAVAILABLE` with the capability's reason.
- Calling `open` twice yields two icons; applications decide whether that is wanted. The library
  never creates a duplicate icon on its own.
- `close()` removes the icon, releases native resources, and stops accepting callbacks.
  Repeated calls are no-ops. After `close()`, `isOpen()` is false.
- Menu callbacks run on a library-owned single daemon thread named `jnativekit-callbacks`,
  **never** on the AWT event thread and never while the library holds internal locks.
- If a callback throws, the exception is caught, logged at `WARNING`, and the tray keeps working.
- The JVM is not kept alive by the tray (daemon threads). Applications must keep their own
  non-daemon thread or main loop.

## 6. Platform

```java
public enum Platform { LINUX, WINDOWS, MACOS, OTHER }
```

## 7. Threading and blocking

- All public methods are thread-safe.
- Methods that run external processes block up to a documented limit (default 5 seconds per
  process). Exceeding it returns `FAILED` with `Reason.TIMEOUT`.
- Applications should not call blocking feature methods on a UI thread.

## 8. Error rules (summary)

| Situation | Behaviour |
|---|---|
| null/invalid argument, bad appId, relative executable path | throws `NullPointerException` / `IllegalArgumentException` |
| missing tool, headless session, unsupported OS | returned as `Capability` / `UNAVAILABLE` |
| tool ran and failed, IO error, timeout, read-back mismatch | `FAILED` with a specific `Reason` |
| provider threw an unexpected failure (`RuntimeException` or a non-fatal `Error`) | facade converts to `FAILED(BACKEND_ERROR)` or a non-available capability, logs at WARNING with stack trace |
| use of a closed tray handle | `isOpen()` false; no exception |

## 9. Example (must compile against the final API; it is part of the README)

```java
var caps = Desktop.notifications().capability();
if (caps.status() != Capability.Status.AVAILABLE) {
    System.out.println(caps.reason() + ": " + caps.remedy());
} else {
    Result<Void> r = Desktop.notifications().show(Notification.of("Backup done", "42 files synced"));
    if (r.isDispatchedUnverified()) { /* accepted by the OS; visibility not guaranteed */ }
}

Desktop.autostart().enable(new AutostartEntry(
        "com.example.notes", "Example Notes", Path.of("/opt/notes/bin/notes"), List.of("--minimized")));

Result<TrayHandle> tray = Desktop.tray().open(new TrayIcon(
        iconPng, "Example Notes",
        List.of(TrayMenuItem.item("Quit", () -> System.exit(0)))));
```

## 10. Stability promises

- Everything in this file is public API from 0.1.0 and follows semantic versioning after 1.0.
- Everything under packages named `internal` is not API and may change at any time.
- Adding enum constants to `Reason` / `Status` is allowed in minor releases; callers should
  handle unknown values with a default branch.
