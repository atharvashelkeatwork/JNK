# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html) after 1.0.

## [0.1.0] - 2026-11-04

First release. Pre-1.0: the public API may still change in a minor release, and the support
matrix records exactly what was verified on which OS.

### Added

- `Desktop` entry point with `notifications()`, `autostart()`, `tray()` and `platform()`.
- `Capability` and `Result<T>` with the honest status model (`CONFIRMED`,
  `DISPATCHED_UNVERIFIED`, `UNAVAILABLE`, `FAILED`) and machine-readable `Reason` values.
- Notifications: Linux `notify-send`, Windows AWT tray balloon, macOS `osascript` (argv only).
- Autostart: Linux Desktop Entry, Windows `HKCU\...\Run` via `reg.exe`, macOS LaunchAgent plist.
  Enable and disable re-read the store before returning `CONFIRMED`.
- Tray: AWT `SystemTray` provider with per-handle lifecycle and callbacks on the daemon thread
  `jnativekit-callbacks`.
- Provider discovery through `ServiceLoader`, guarded so a broken provider becomes `UNAVAILABLE`
  or `FAILED` instead of crashing the application.
- Timeout-bounded, shell-free process runner with bounded stdout/stderr capture.
- Runnable demo with a capability report, interactive menu and `--report` / `--autostart` flags.
- Unit, unit-fake and ci-integration test tiers; 3-OS CI matrix.
- Maven Central release wiring: sources and Javadoc jars, GPG signing, the Central Portal
  publishing profile and a tag-driven release workflow (see `docs/RELEASING.md`).

### Fixed

- Windows autostart `enable` wrote the `Run` value with `reg.exe add /d`. When the quoted command
  line contained spaces, `reg.exe` split it and exited with "Invalid syntax". The value is now
  imported from a temporary UTF-16LE `.reg` file, so command-line quoting cannot alter the stored
  data. The temporary file is always deleted.

### Known limitations

- Windows and macOS are tested by CI only; manual verification is limited to one Linux machine.
- Notifications are always `DISPATCHED_UNVERIFIED` in v0.1.
- Tray icons can be hidden by desktops that only expose StatusNotifier.
- Not published to Maven Central yet.

[0.1.0]: https://github.com/atharvashelkeatwork/jnativekit/releases/tag/v0.1.0
