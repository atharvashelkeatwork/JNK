# SCOPE

## Problem

A Java desktop application that wants a notification, a tray icon or "start at login" has to
combine AWT, shell tools, OS-specific files and registry keys, and then guess whether it worked.
Failures are usually silent: a notification command returns exit code 0 and nothing appears;
a tray icon is accepted by AWT and never shown; an autostart entry is written to a place the
OS ignores.

## What this library offers

1. One small API for three features, selected per OS at runtime.
2. An honest result model: every action says what was confirmed, what was only dispatched,
   what is unavailable (and why, and what the user can do), and what failed.
3. A capability check before acting, with machine-readable reasons.
4. Zero runtime dependencies in v0.1; no UI framework required (works with Swing, JavaFX or none).
5. A support matrix based on what was actually tested, labeled by test tier.

## Prior art (be honest about it in the README)

| Existing | Covers | Why this project is still different |
|---|---|---|
| AWT `SystemTray` | Tray on most platforms | Reused, not replaced. We add capability checks, lifecycle and callback rules. |
| TwoSlices | Notifications | Notifications alone are not the pitch. |
| JNativeHook | Global keyboard hooks | Not in v0.1 (hotkeys are v0.3). |
| java-keyring | Credential storage | Not in v0.1 (secrets are v0.2). |
| JWM / Nucleus | Window/desktop frameworks | Different scope: frameworks, not a small library. |

Re-check each project's status before putting a comparison in the public README.
The claim to defend: *a Java developer gets reliable feature detection, clear failure reasons
and consistent semantics with less glue code than assembling these pieces by hand.*
That claim must be demonstrated by the demo and tests, not asserted.

## v0.1 IN scope

| Feature | Linux | Windows | macOS |
|---|---|---|---|
| Notifications | `notify-send` (shell-out, argv list) | AWT tray balloon via `TrayIcon.displayMessage` | `osascript` (shell-out, argv list) |
| Autostart (start at user login) | `~/.config/autostart/<id>.desktop` | `HKCU\...\Run` via `reg.exe` | `~/Library/LaunchAgents/<id>.plist` |
| Tray | AWT `SystemTray` wrapper | AWT `SystemTray` wrapper | AWT `SystemTray` wrapper |

Plus: `Capability` checks for each feature, the `Result` model, the demo app, Javadoc,
README with support matrix, 3-OS CI.

## v0.1 OUT of scope (do not build, do not stub)

- Credential storage (v0.2), global hotkeys (v0.3), file watcher, clipboard, dialogs.
- Rich notifications: actions/buttons, images, sounds, replace/update, progress.
- Dynamic tray menus (update menu after open), submenus, checkboxes. Menu is fixed at open.
- Autostart at system boot or before login. "Autostart" means: launch when this user logs into
  a graphical desktop session. Nothing else is promised.
- Wayland-specific work, other BSDs, JVMs older than 17.
- JNA / Foreign Function API backends (stretch, only after v0.1 ships).
- Maven Central publication (decision after v0.1 is verified; see Milestone 8).

## Supported environments and the verification we commit to

| Environment | Verification level for v0.1 |
|---|---|
| Linux Mint 22.3 (Cinnamon, X11) | manual-verified by the owner (2026-11-04) |
| Windows 10/11 | unit + ci-integration only; documented as **not manually verified** |
| macOS (GitHub runner only) | unit + ci-integration only; documented as **not manually verified** |
| Other Linux desktops (GNOME, KDE, Wayland) | untested; capability checks must degrade honestly |

## Success criteria for v0.1 (resume-honest)

- All three features work and are manually verified on Linux Mint 22.3 (Cinnamon, X11).
- Windows and macOS are exercised by the 3-OS CI matrix and unit/unit-fake tests, and the README
  says exactly that. Neither is claimed as manually verified.
- `mvn -B verify` is green on the 3-OS matrix.
- A demo prints a capability report for the machine it runs on, then exercises each feature.
- No claim in the README or resume that is not backed by a row in the verification log.

## Naming and namespace

Decided in Milestone 0 (2026-10-30): project name **`jnativekit`**, groupId
**`io.github.atharvashelkeatwork`**, Java package root
`io.github.atharvashelkeatwork.jnativekit`. The GitHub repository lives under the
`atharvashelkeatwork` account, which is what the Maven Central namespace check will require.

Name check performed before the first commit that contains a package name:

| Source | Query | Result |
|---|---|---|
| Maven Central search | `jnativekit`, `a:jnativekit*`, `g:io.github.atharvashelkeatwork` | 0 artifacts |
| GitHub repository search | `jnativekit` | 0 repositories |
| GitHub user/org lookup | `jnativekit`, `jnative-kit` | free (`404`) |
| GitHub code search | `jnativekit` | no matches (run by the owner while signed in) |
| General web search | `jnativekit` | no exact hits; only unrelated "nativekit" projects in other ecosystems |
| npm / PyPI | `jnativekit` | free (`404`) |

Known adjacency risk: unrelated projects named "nativekit" (web, Laravel, Dart), SourceForge's
"JNative", and the JDK's `jnativescan` tool. None of them owns the exact name `jnativekit` or the
package root above. Renaming packages later is expensive, so this decision is not revisited
without a concrete conflict.
