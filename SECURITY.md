# Security policy

## Supported versions

| Version | Supported |
|---|---|
| 0.1.x | yes |
| older | no |

v0.1 is pre-1.0. Fixes are made on `main` and released as a new patch or minor version.

## Reporting a vulnerability

Please **do not** open a public issue for a security problem.

Use GitHub's private vulnerability reporting on this repository:
<https://github.com/atharvashelkeatwork/jnativekit/security/advisories/new>

Include:

- the affected version and OS,
- a minimal reproduction or the exact input that triggers it,
- the impact you believe it has,
- whether the issue involves command execution, file writes, the registry, or untrusted input.

You can expect an acknowledgement within a few days. This is a small project without a paid
security team; timelines are best effort, and coordinated disclosure is appreciated.

## Scope notes

jnativekit runs external programs (`notify-send`, `osascript`, `reg.exe`) only through
`ProcessBuilder` with separate arguments — never a shell. It validates caller-supplied `appId`,
executable paths and strings before use, and it does not store secrets in v0.1. Reports about
argument injection, path traversal outside the documented store, or a fallback that silently
weakens a result are especially relevant.
