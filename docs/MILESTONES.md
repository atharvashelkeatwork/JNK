# MILESTONES

One milestone = one or two opencode sessions. Do not start a milestone until the previous
one's exit condition is met. Each milestone ends with: green `mvn -B verify` on the 3-OS CI,
the BUILT/VERIFIED/OPEN report, and (where listed) a manual checklist logged in `docs/TESTING.md`.

Session routine: Plan mode first ("Read AGENTS.md and docs/. Plan Milestone N. Do not edit files."),
review the plan, then Build mode.

Time estimates assume part-time work. They are guesses, not promises.

> **Correction 2026-11-04:** the owner has one Linux machine (Linux Mint 22.3, Cinnamon, X11) and
> no Windows or macOS machine. The owner completed every manual check on Linux only. Statements
> below that require manual runs on Windows are re-scoped: Windows and macOS are covered by the
> 3-OS CI matrix and unit/unit-fake tests, and are documented as not manually verified. Milestone
> statuses are unaffected.

---

## M0 - Repository bootstrap (about 1 weekend)

**Goal:** an empty but real project that builds on 3 OSes.

Tasks
1. Name check: search GitHub, Maven Central, and the web for `jnativekit`. Owner decides final
   name and groupId. Record the decision in `docs/SCOPE.md`.
2. New repository, license (Apache-2.0 recommended: patent grant; MIT acceptable), `.gitignore`,
   `.gitattributes`, copy in `AGENTS.md` and `docs/`.
3. Maven reactor with `core`, `providers`, `demo`; Java 17 `release`; JUnit 5; one trivial test per module.
4. GitHub Actions matrix (ubuntu, windows, macos) running `mvn -B verify`.
5. README stub: one paragraph, status "pre-alpha", link to docs.

Acceptance
- `mvn -B verify` passes locally on Mint and on the Windows laptop.
- CI is green on all three OSes on the first push.

Exit condition: owner has cloned the repo fresh on both machines and built it.

---

## M1 - Core types (about 1 weekend)

**Goal:** the public data types exactly as in `docs/API.md`, with no providers.

Tasks: `Platform`, `Reason`, `Capability`, `Result<T>` (with invariants enforced in the
constructor), `Notification`, `AutostartEntry` (appId pattern, absolute path, arg checks),
`TrayIcon`, `TrayMenuItem`; `Feature`, `Notifications`, `Autostart`, `Tray`, `TrayHandle`; Javadoc.

Acceptance
- Invariant table in `API.md` is covered by a parameterized test.
- Validation tests: every rule in `API.md` has a passing and a failing case.
- Records defensively copy lists/arrays and are immutable.

Exit condition: owner reads the Javadoc and confirms it matches `API.md`.

---

## M2 - SPI, resolver, `Desktop` facade, process runner (about 1 weekend)

**Goal:** the plumbing every provider will use.

Tasks: SPI interfaces; resolver per `ARCHITECTURE.md`; unavailable facades; guard that converts
`RuntimeException` to `FAILED(BACKEND_ERROR)`; `CommandRunner` + real implementation
(timeout, bounded output, no shell) + `FakeCommandRunner`; `PathProvider`.

Acceptance
- Tests: no providers -> `UNSUPPORTED_OS`; two providers -> highest priority wins; deterministic tie-break;
  a provider throwing in `ServiceLoader` or at call time does not crash `Desktop`.
- Real `CommandRunner` tests (T3) prove timeout kills the process and output is bounded, on all 3 OS
  (use a portable child: the JVM itself via `java -version` or a small test main class).

Exit condition: green CI; owner reviews the resolver code.

---

## M3 - Notifications (about 1 to 2 weekends)

Order: M3a Linux, M3b Windows, M3c macOS.

M3a Linux (`notify-send`): capability checks, argv safety (`--`), result mapping, tests T2.
M3b Windows (AWT balloon): provider using shared `providers.awt` helper, tests with a fake tray backend.
M3c macOS (`osascript` with `on run argv`): hostile-string tests, T2 only.

Acceptance
- Hostile strings tested for all three.
- Manual checklist "Notifications" done on Mint and Windows and logged.
- macOS marked `unit-fake only`.

Exit condition: verification log has Mint and Windows rows for notifications.

---

## M4 - Autostart (about 2 weekends)

Order: Linux, then macOS (pure file work), then Windows (`reg.exe`).

Acceptance
- Desktop Entry quoting, plist escaping, Windows command-line quoting each have table-driven tests
  with hostile inputs (see `ARCHITECTURE.md`).
- T3 integration tests: Linux and macOS write into a temp HOME; Windows writes a uniquely named
  Run value under HKCU and always deletes it.
- Manual checklist "Autostart" on Mint and Windows including a real logout/login.
- `isEnabled` never returns `CONFIRMED(false)` for an unreadable store (explicit test).

Exit condition: logged manual results for Mint and Windows.

---

## M5 - Tray (about 1 to 2 weekends)

Tasks: AWT tray provider, PNG decoding, callback executor, `TrayHandle` lifecycle, shared helper
reused by the Windows notification provider.

Acceptance
- Lifecycle tests with a fake tray backend: double close, open after close, throwing callback,
  callback thread name, no callbacks after close.
- Headless capability test (`java.awt.headless=true`) returns `NO_GRAPHICAL_SESSION`.
- Manual checklist "Tray" on Mint and Windows, with the desktop/visibility notes recorded.

Exit condition: logged manual results; any invisible-icon behaviour documented, not hidden.

---

## M6 - Demo app and capability report (about 1 weekend)

Tasks: `demo` main prints a capability table for this machine, then offers a menu (or flags) to
fire a notification, toggle autostart for the demo, and open a tray icon with Quit.
Package runnable jar (shade plugin is allowed in `demo` only).

Acceptance: run on Mint and Windows; screenshots/gifs for the README captured by the owner.

---

## M7 - Documentation and v0.1.0 (about 1 to 2 weekends)

Tasks: README (problem, quick start, honest prior art, support matrix from the verification log,
limitations), Javadoc build clean, `CHANGELOG.md`, `CONTRIBUTING.md` (short), issue templates,
`SECURITY.md` (how to report), tag `v0.1.0`, GitHub release with the demo jar.

Acceptance
- Every README claim maps to a verification-log row.
- A fresh clone builds with one command on both machines.
- The README example compiles (it is a test or a checked snippet in `demo`).

---

## M8 - Optional: Maven Central (separate decision)

Only after v0.1.0 is verified and the name is settled: namespace verification for
`io.github.<user>`, GPG signing, sources/javadoc jars, Central Portal publishing, a release
workflow. Allow extra time for the first publication; it is mostly process, not code.

---

## After v0.1 (not planned in detail)

v0.2 credential storage; v0.3 global hotkeys (Wayland limits documented honestly);
optional JNA/FFM providers; real Windows toasts; richer notifications. Re-plan before starting any.

## Risk list to revisit at each milestone

- Linux tray invisibility on some desktops. Mitigation: capability honesty + docs, not hacks.
- Windows balloon notifications behave differently across versions. Mitigation: manual log; later toast provider.
- macOS cannot be manually verified. Mitigation: label it; ask a Mac owner to run the demo and add a log row.
- Scope creep during agent sessions. Mitigation: `AGENTS.md` scope rules; reject unrequested files in review.
- Drift between machines. Mitigation: always `git pull` before work; CI is the shared truth.
