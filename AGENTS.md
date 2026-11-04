# AGENTS.md

Instructions for any AI coding agent (opencode or otherwise) working in this repository.
The human owner is Atharva Shelke. Read this file fully before every session.

## What this project is

A Java 17 library giving ordinary JVM applications one consistent API for three desktop
features on Linux, Windows and macOS: **notifications, autostart, system tray**.
Its value is NOT "wrapping native calls". Its value is an honest API: every call reports
what was actually verified, and every unavailable feature explains why and what to do.

Name: `jnativekit`, groupId `io.github.atharvashelkeatwork` (decided in Milestone 0, see `docs/SCOPE.md`).

## Read first, in this order

1. `docs/SCOPE.md`        what is in and out of v0.1
2. `docs/API.md`          the public contract (source of truth for all signatures)
3. `docs/ARCHITECTURE.md` modules, providers, process execution rules
4. `docs/TESTING.md`      test tiers and what each tier is allowed to claim
5. `docs/MILESTONES.md`   the current milestone and its acceptance criteria

If code and `docs/API.md` disagree, the docs win. If the docs seem wrong, STOP and tell
the owner; do not silently change the contract.

## Hard rules

### Scope
- Work only on the current milestone in `docs/MILESTONES.md`. Do not start the next one.
- Do not add features, modules, public types or dependencies that are not in the docs.
  Ask first. Runtime dependencies for v0.1 are: none. Test dependency: JUnit 5 only.
  JNA is NOT approved for v0.1 unless a milestone explicitly says so.
- Do not add Lombok, DI frameworks, logging frameworks or reflection tricks. Use `System.Logger`.
- Java 17 language level, no preview features, no `--enable-preview`.

### Truthfulness (the most important rule)
- Never report a feature as working on an OS you did not run it on.
- Every report ends with a block in exactly this form:

```
BUILT:     <what changed>
VERIFIED:  <for each claim: unit-fake | ci-integration | manual on <OS/desktop> | NOT VERIFIED>
OPEN:      <known gaps, doubts, things the owner must test by hand>
```

- A passing Linux unit test is evidence for Linux logic only. It says nothing about Windows
  or macOS desktop behaviour.
- `CONFIRMED` / `DISPATCHED_UNVERIFIED` must follow the definitions in `docs/API.md`.
  Do not upgrade a status because it looks nicer.

### Safety and security
- Never run external programs through a shell. No `sh -c`, no `cmd /c`, no string-concatenated
  commands. Use `ProcessBuilder` with a `List<String>` of arguments.
- Every external process needs a timeout, bounded output capture, and `destroyForcibly()` on timeout.
- Validate every caller-supplied identifier and path as specified in `docs/API.md`
  (appId pattern, absolute executable path, no NUL or newline characters).
- Never write outside the files the feature owns. Tests must never touch the real user
  home, real autostart entries or the real registry; use injected temp directories or fake runners.
- Do not store secrets. Do not fall back silently to a weaker behaviour. A fallback must be
  a separate, named provider reported in `Result.provider`.

### Errors
- Invalid caller input throws `NullPointerException` / `IllegalArgumentException`.
- Environment problems (missing tool, no display, permission, unsupported OS, backend failure)
  are returned as values (`Capability`, `Result`). They are never thrown to the caller.
- Never swallow an exception silently. Convert it to a `FAILED` result AND log it at
  `WARNING` with the stack trace.

### Cross-platform hygiene
- No hard-coded path separators or absolute OS paths in tests. Compare `Path` objects.
- No `Thread.sleep` based tests. Use latches, fakes or injected clocks.
- All tests in the default build must pass on Linux, Windows and macOS without a display.

## Commands

```
mvn -B verify                      # full build + all default tests (must pass on all 3 OS)
mvn -B -pl core test               # one module
mvn -B verify -Pintegration        # also runs @Tag("integration") tests (CI-integration tier)
mvn -B -pl demo -am package exec:java  # run the demo (manual checks)
```

## Definition of done for any task

1. Code compiles on Java 17.
2. New behaviour has tests at the correct tier (see `docs/TESTING.md`).
3. `mvn -B verify` passes.
4. Public Javadoc written for every public type and method touched.
5. `docs/` updated if behaviour or contract details changed (ask first if the contract changed).
6. The BUILT / VERIFIED / OPEN report is given.

## Git

- Do not run `git commit`, `git push` or rewrite history unless the owner asks in that session.
- Suggest a commit message at the end of each task (imperative mood, one line + short body).

## Autonomy and gates

The owner wants steady progress without constant supervision. You may run a whole milestone
without asking questions, but milestones are gated. These rules override any instruction to
"keep going" or "complete all milestones".

1. `docs/PROGRESS.md` is the status board. Only the owner may mark a milestone `DONE`.
   You may set a milestone to `BUILT - awaiting CI and owner checks`.
2. Never start milestone N+1 until `docs/PROGRESS.md` shows milestone N as `DONE`.
   You may *plan* N+1 (read-only) while waiting.
3. When you finish a milestone: run `mvn -B verify` and `mvn -B verify -Pintegration`, update
   `docs/PROGRESS.md`, give the BUILT / VERIFIED / OPEN report, list the exact manual checklist
   items from `docs/TESTING.md` still to be done by the owner, then STOP.
4. You cannot see CI and cannot run other operating systems. Never write "CI green" or any
   statement about another OS unless the owner has told you so in this session.
5. Never edit the verification log in `docs/TESTING.md`. Only the owner records verification.
6. Never make a failing test pass by deleting it, weakening an assertion, adding `@Disabled`, or
   loosening the contract. If a test still fails after 3 honest attempts, STOP and report.
7. If the code, the docs and the milestone disagree, STOP and ask. Do not pick silently.
8. Milestones with a manual checklist (M3 to M6) can never be fully finished by you alone.

## Session workflow (owner and agent)

1. Start in Plan mode: "Read AGENTS.md and docs/. Plan Milestone N. Do not edit files."
2. Owner reviews the plan, corrects it, then switches to Build mode.
3. Agent implements only that plan, runs `mvn -B verify`, gives the report.
4. Owner runs the manual checklist for that milestone on the real machines and records
   the result in `docs/TESTING.md` (verification log). Only then is the milestone done.

## Third-party skills
Review skills are advisory and read-only. AGENTS.md and docs/ always win. Never delete or weaken
tests, remove validation, or change the API contract because a review skill suggested it.
Report review findings to the owner instead of applying them.
