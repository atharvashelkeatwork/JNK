# TESTING

## Principle

Each test tier is allowed to claim only what it can prove. The README support matrix and every
agent report must name the tier behind each claim.

## Tiers

| Tier | Label | What it runs | Can prove | Cannot prove |
|---|---|---|---|---|
| T1 | `unit` | Pure logic: validation, quoting, plist/.desktop rendering, result invariants, resolver | Logic is correct for the inputs tested | Anything about a real OS |
| T2 | `unit-fake` | Providers against `FakeCommandRunner`, temp directories, fake `PathProvider`, fake tray backend | Providers issue the right commands/files, handle failures, never throw | That the real tool behaves as the fake does |
| T3 | `ci-integration` | `@Tag("integration")`, on the real CI OS, no display. e.g. real file write/read-back in a temp HOME; real `reg.exe` on `windows-latest` under a unique throwaway value name with cleanup in `finally` | Real filesystem/registry round trip works on that OS | Visible UI behaviour |
| T4 | `manual` | Owner follows a checklist on a real desktop and logs the result | Real desktop behaviour on that exact OS/desktop | Other desktops/versions |

All default-build tests (T1, T2) must pass on Linux, Windows and macOS without a display.
T3 runs only with `-Pintegration` and must clean up after itself and never touch real user entries.

## Rules for writing tests

- No test reads or writes the real home directory, real autostart entries or real registry
  entries outside a uniquely named test key that is always removed.
- Compare `Path` objects, not strings with separators.
- No `Thread.sleep` for synchronization; use latches or injected executors.
- Every provider has tests for: happy path, missing tool, non-zero exit, timeout, hostile input
  (spaces, quotes, `%`, `$`, backslashes, Unicode, very long strings), and "provider throws
  RuntimeException => facade returns FAILED and logs".
- Result invariants (see `API.md` table) have their own parameterized test.
- Hostile-string tests are mandatory for: `notify-send` args, `osascript` args, Desktop Entry
  `Exec`, Windows Run value, plist XML.
- Fixtures of real tool output (for example `reg query`) are captured from a real machine and
  stored in `src/test/resources`, with a comment saying which OS/version produced them.

## Manual checklists (T4)

Run on the owner's Linux machine after the milestone is built. Record in the log below. Windows
and macOS have no owner machine, so their coverage is CI and unit/unit-fake only, never T4.

### Notifications
1. `capability()` is `AVAILABLE` on a normal session.
2. `show` produces a visible notification; result is `DISPATCHED_UNVERIFIED`.
3. Title starting with `-`, containing quotes and Unicode displays correctly.
4. With the backend missing (Linux: temporarily rename `notify-send` via PATH change) the result is
   `UNAVAILABLE`/`BACKEND_MISSING` with a useful remedy.
5. Do Not Disturb / Focus Assist on: note what happens (the library will still say
   `DISPATCHED_UNVERIFIED`; that is correct).

### Autostart
1. `enable` returns `CONFIRMED`; the entry exists where `ARCHITECTURE.md` says.
2. Log out and back in: the app actually started.
3. `isEnabled` true; `disable` -> `CONFIRMED`; `isEnabled` false; entry gone.
4. Executable path with spaces and an argument with spaces both launch correctly.
5. Unwritable store (make the directory read-only): `enable` is `FAILED(IO_ERROR)`, not an exception.

### Tray
1. Icon appears with the tooltip; menu items work; callbacks run on `jnativekit-callbacks`.
2. A throwing callback is logged and the tray keeps working.
3. `close()` removes the icon; calling it again does nothing; `isOpen()` is false.
4. Icon visible on this desktop? Record yes/no and the desktop name/version.
5. The JVM exits when the app's own work finishes (tray does not keep it alive).

### Demo (M6)
1. `mvn -B -pl demo -am package exec:java` prints the capability table and the menu.
2. `java -jar demo/target/jnativekit-demo-0.1.0.jar --report` prints one row per
   feature and exits.
3. Menu 1 sends a visible notification; the result line names the status, provider and reason.
4. Menu 2 toggles autostart: enable when disabled, disable when enabled; an unknown state changes
   nothing and says so.
5. Menu 3 opens the icon; its Quit item closes it and ends the demo; opening it twice is refused.
6. `--autostart` sends a notification, opens the tray and stays until Quit. Record what happens
   when the tray cannot open.
7. Capture the README screenshot/gif on Linux.
8. Record the OS, desktop and JDK. Windows and macOS are not manually tested; they are covered by
   T3 (`ci-integration`) and T2 (`unit-fake`) in CI.

## Verification log (the only source for README claims)

Add one row per manual or CI verification. Never delete rows; add a new row when re-tested.

| Date | Feature | OS / desktop / JDK | Tier | Result | Notes |
|---|---|---|---|---|---|
| 2026-11-04 | Notifications | Linux Mint 22.3 Zena, Cinnamon 6.6.9, X11, kernel 7.0.0-42-generic, OpenJDK 21.0.12.1 (release 17) | manual | PASS | Owner ran the Notifications checklist: capability, visible notification, hostile title, missing backend, and Do Not Disturb note. `notify-send` backend; result remains `DISPATCHED_UNVERIFIED` by design. |
| 2026-11-04 | Autostart | Linux Mint 22.3 Zena, Cinnamon 6.6.9, X11, kernel 7.0.0-42-generic, OpenJDK 21.0.12.1 (release 17) | manual | PASS | Owner ran the Autostart checklist: enable with read-back, real logout/login, disable, spaces in executable/arguments, and unwritable store. Entry under `$XDG_CONFIG_HOME/autostart`. |
| 2026-11-04 | Tray | Linux Mint 22.3 Zena, Cinnamon 6.6.9, X11, kernel 7.0.0-42-generic, OpenJDK 21.0.12.1 (release 17) | manual | PASS (environment limitation) | Owner ran the Tray checklist. Cinnamon exposes StatusNotifier (`xapp-sn-watcher`), not XEmbed, so `SystemTray.isSupported()` is false and capability reports `UNSUPPORTED_ENVIRONMENT`/`BACKEND_MISSING`. No icon is expected or shown; recorded as an environment limitation. |
| 2026-11-04 | Demo (M6) | Linux Mint 22.3 Zena, Cinnamon 6.6.9, X11, kernel 7.0.0-42-generic, OpenJDK 21.0.12.1 (release 17) | manual | PASS | Owner ran the Demo checklist: capability table, interactive menu, `--report`, notification, autostart toggle, tray open/Quit, and `--autostart`. Tray reports unavailable on this desktop. |
| 2026-11-04 | Autostart | windows-latest (GitHub Actions), Temurin 17 | ci-integration | PASS | Owner-reported green CI. Evidence: [CI run 18, `verify (windows-latest)`](https://github.com/atharvashelkeatwork/jnativekit/actions/runs/41801088566/job/126476724358); the `mvn -B verify -Pintegration` step succeeded. T3 wrote, read back, and deleted a unique throwaway `HKCU\...\Run` value through `reg.exe import`. |
| 2026-11-04 | Autostart | macos-latest (GitHub Actions), Temurin 17 | ci-integration | PASS | Owner-reported green CI. Evidence: [CI run 18, `verify (macos-latest)`](https://github.com/atharvashelkeatwork/jnativekit/actions/runs/41801088566/job/126476724072); the `mvn -B verify -Pintegration` step succeeded. T3 wrote, read back, and deleted a LaunchAgent in a temporary HOME. |

## Support matrix template (README is generated by hand from the log)

| Feature | Linux Mint (Cinnamon) | Windows 10/11 | macOS | Other |
|---|---|---|---|---|
| Notifications | untested | untested | untested | untested |
| Autostart | untested | untested | untested | untested |
| Tray | untested | untested | untested | untested |

Allowed cell values: `manual-verified (<date>)`, `ci-integration`, `unit-fake only`, `untested`,
`unsupported`. A cell never says "works" without one of those labels.
