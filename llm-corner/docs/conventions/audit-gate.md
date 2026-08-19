# Audit Gate

The pre-commit / pre-push checklist. CI enforces this gate; running
it locally first catches breaks before the GitHub red.

## Before every commit

```sh
./gradlew build      # THE gate: compiles + runs the full test suite
```

That single command IS the gate — there is no separate lint, vet,
typecheck, or format tool in this repo (no Checkstyle/Spotless/PMD/
ErrorProne plugin in `build.gradle.kts`; typechecking is `javac`
inside the same build). Tests run on the JUnit platform with
Testcontainers (`build.gradle.kts:63,110`), so **Docker must be
running locally**. "Compiles" is not the gate — tests PASS is the
gate. `./gradlew test --tests "ClassName"` is for iteration only,
never a substitute.

If the build fails, the commit is not safe — fix first.

Beyond the build, grep source (test files excluded) for zero-count
categories:

- New `FIXME` / `HACK` / `WORKAROUND` / `TODO` markers → 0
  (five legacy Russian-language TODOs predate this gate — see
  Permitted exceptions)
- Hardcoded secret values (Stripe `sk_live_`/`whsec_` prefixes, JWT
  secrets, passwords) → 0; env-var names only
- Stray debug prints (`System.out.println`, `printStackTrace`) in
  non-test code → 0

## Before every push

```sh
./gradlew build      # same command, rerun on the final state
```

There is no lint job to gate on — CI runs exactly `./gradlew build`
(`.github/workflows/docker-build.yml:34`) before building the deploy
image. Because no linter exists, the manual greps above stand in
permanently after any refactor:

- Unused/orphaned symbols: `grep -rn '<removed-symbol>' src/` → 0.
- Orphan imports: the IDE usually catches; double-check touched files.
- Dead config: a renamed/removed `application.yml` or `configs/*.yml`
  key must not leave `@Value` references behind (they fail at boot,
  i.e. in prod).

## Permitted exceptions

- Five legacy Russian-language `TODO`s predate the no-TODO rule
  (`scheduler/OrderScheduler.java:10`,
  `scheduler/SubscriptionScheduler.java:17-18`,
  `service/payment/impl/BalancePaymentService.java:31`,
  `service/SubscriptionDeactivationService.java:42`). Tracked in
  `../../active/backlog.md`; do not add new ones, and delete each
  alongside the change that resolves it.
- Test files are excluded from the source-only grep patterns.

## On a real positive

1. Stop. Don't commit.
2. Fix the root cause.
3. Re-run the gate. Confirm clean.
4. Commit.

## CI gate

<https://github.com/CoursesTools/backend-v2/actions/workflows/docker-build.yml>
— the workflow file at `.github/workflows/docker-build.yml`.

If CI is red on `master`, that's a P0 — nothing else merges until
it's green. Remember the flip side: a GREEN push to `master` is a
production deploy (`../conventions/mindset.md`, hard invariant 1), so
the local gate is also the last human-controlled checkpoint before
prod.
