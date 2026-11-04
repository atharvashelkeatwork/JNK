# Contributing

Thanks for taking the time to improve jnativekit.

## Before you start

Read these, in order:

1. [`AGENTS.md`](AGENTS.md) — the working rules for humans and coding agents.
2. [`docs/SCOPE.md`](docs/SCOPE.md) — what belongs in the current version.
3. [`docs/API.md`](docs/API.md) — the public contract. If code and this file disagree, the file wins.
4. [`docs/TESTING.md`](docs/TESTING.md) — test tiers and what each tier is allowed to claim.

## Build and test

```bash
mvn -B verify                 # unit + unit-fake tests, all three OSes
mvn -B verify -Pintegration   # adds ci-integration tests
```

All default tests must pass on Linux, Windows and macOS without a display. Do not use
`Thread.sleep` for synchronization, hard-coded absolute paths or real user home directories in
tests.

## Releases

Publication to Maven Central is described in [`docs/RELEASING.md`](docs/RELEASING.md). Only the
owner runs a release; the default build needs no credentials and no signing key.

## Rules for a change

- Keep the change inside the current milestone. Do not add features, modules, public types,
  dependencies or configuration that the docs do not describe.
- Runtime dependencies in `core` and `providers` are not allowed. Test scope is JUnit 5.
- Never run external programs through a shell. Use `ProcessBuilder` with an argument list, a
  timeout, bounded output and `destroyForcibly()`.
- Invalid caller input throws `NullPointerException` / `IllegalArgumentException`. Environment
  problems are returned as `Capability` / `Result`, never thrown.
- Never report a feature as working on an OS it was not run on. Update the support matrix only
  from the verification log in `docs/TESTING.md`.
- Public types and methods need Javadoc. `mvn verify` builds the Javadoc jars and fails on
  doclint errors.

## Pull requests

- Keep the change focused and explain the user-visible effect.
- Add or update tests at the correct tier.
- Run `mvn -B verify` and `mvn -B verify -Pintegration` locally.
- Do not commit generated files under `target/`.
- The owner records manual verification; a PR cannot mark a milestone `DONE`.

## Reporting bugs

Use the bug report issue template and include the OS, desktop environment, JDK version, the
`--report` output from the demo, and the `status` / `reason` / `provider` values you saw.
Security issues go through [`SECURITY.md`](SECURITY.md), not the public issue tracker.
