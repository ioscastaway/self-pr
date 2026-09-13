# self-pr

An Android app that carries its own source code, catches its own crashes, diagnoses them against
that source with Claude, and opens a pull request on its own repository with the fix. CI builds the
PR; a human merges. The app cannot run its own tests, so it files the paperwork instead.

> Stage 3 of the Evolving App series. Stage 1 taught an app to rewrite its rules. This one is
> about rewriting code, and about the one thing an app can never do for itself: prove the patch
> works. So the app does what any engineer without a build machine does. It opens a PR and lets
> CI argue.

**Series:** Evolving App (stage 3) · Android × AI
**Status:** see the bottom of *Experiment* for the log of pull requests the app has filed about
itself, each with what CI said.

## Why I built this

The Evolving App ladder has a rung where the app changes its own code. Read literally, that is
science fiction. Read carefully, it is four ordinary things chained together, and every one of
them is a public API:

1. know that you crashed, and where (`Thread.setDefaultUncaughtExceptionHandler`,
   `ApplicationExitInfo`);
2. know your own source (a zip in `assets/`, made by the build);
3. ask a model for a fix, in a shape you can validate (structured output);
4. push a branch and open a pull request (the GitHub REST API).

The interesting part is not any of the four. It is the seam between 3 and 4: what an app is
allowed to publish about itself without a human having read it, and what it must never touch.

## The iOS brain

An iOS developer is used to `MetricKit`: crash and hang diagnostics arrive on the system's
schedule, not yours, and there is no way to read the process's exit history on demand. That
shapes the whole mental model: crashes are something a dashboard tells you about tomorrow, not
something the app knows about at its next launch.

The rest of the loop is portable. Bundling source, calling a model, calling GitHub: none of it
needs a permission on either platform. Stage 3 is not an Android-only trick and the README does
not pretend otherwise. What it sets up is stage 4, where the app installs the result, and that
one has no third-party path on iOS.

## What Android exposes

- `Thread.setDefaultUncaughtExceptionHandler`: same as anywhere on the JVM, but on Android the
  process is about to die, so the handler writes a file and gets out of the way.
- `ActivityManager.getHistoricalProcessExitReasons` (API 30+): the system's own record of why
  this process last died, including ANRs and native crashes the handler never sees, with a trace
  when the system has one, readable at the next launch.
- `assets/` and `BuildConfig`: the build puts a zip of the app's sources and its git revision into
  the APK, so the running app is its own source of truth.
- `PackageInstaller` (stage 4, not yet): the reason this loop is worth closing on Android.

## Experiment

The host feature is a bill splitter, written the way first drafts get written. It works for the
inputs the author tried and crashes on the ones they did not:

| Input | Crash | Line |
|---|---|---|
| `12.5` or an empty amount | `NumberFormatException` | `amountText.trim().toInt()` |
| `0` people | `ArithmeticException: divide by zero` | `(amount + tip) / people` |
| *Show last* before any split | `NoSuchElementException` | `history.last()` |

None of these is a planted `throw`. They are the three bugs a reviewer would find in the first
five minutes, left in so the app has something real to fix.

```
crash ──► CrashCollector ──► CrashReport (trace, build, device)
                                   │  Heal tab, tap 1: Diagnose
              TraceParser: frames in com.ioscastaway.selfpr.*  ──► SourceIndex: those files + their tests
                                   │
              ClaudeDiagnoser: trace + ARCHITECTURE.md + files ──► Diagnosis (root cause, confidence,
                                   │                                 whole-file patches, PR title/body)
              PatchValidator: bundled files only, tests may be new, non-empty, changed
                                   │  Heal tab, tap 2: Open pull request (confidence ≥ 0.6)
              GitHubPublisher: ref ──► branch ──► contents PUT per file ──► pull request
                                   │
              GitHub Actions: testDebugUnitTest + assembleDebug ──► a human merges, or not
```

Two taps on purpose. The diagnosis is shown in full, every patched file included, before the
button that publishes exists.

### Pull requests the app has opened about itself

| # | Crash | What the app proposed | CI | Merged |
|---|---|---|---|---|
| _(none yet)_ | | | | |

## Architecture

`healer/` is pure Kotlin with no Android imports: trace parsing, the source index, the diagnosis
model and prompt, the validator, the pull-request plan with its request bodies. All of it runs in
JVM tests. `platform/` holds the crash collector, the JSON store, the knowledge base reader, the
Claude diagnoser, the GitHub publisher, and the `Healer` that chains them. The package map, the
loop and the invariants are in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), which the build
bundles as an asset and the app hands to the model together with its source.

The knowledge base is made by one Gradle task, `bundleSource`, on every build: `app/src/main/java`,
`app/src/test/java`, the manifest, `app/build.gradle.kts`, the version catalog and the notes. If
a file is not in that list the app cannot see it and will not patch it.

## What I learned

_(filled from real runs; see the table above)_

## iOS comparison

| | Android | iOS |
|---|---|---|
| Read the app's own crash and ANR history at next launch | `ApplicationExitInfo`, on demand | `MetricKit`, delivered on the system's schedule |
| Bundle the app's own source | `assets/` | Bundle resources; equivalent |
| Ask a model for a patch, open a PR | HTTP | HTTP; equivalent |
| Install the resulting build (stage 4) | `PackageInstaller`, user-confirmed | No third-party path outside the App Store and TestFlight |

Stage 3 is portable. Its value is that stage 4 is not.

## Limitations

- **Security.** The app publishes code about itself to a repository it holds a token for. The
  token in this experiment is baked into the debug build from `local.properties`; it should be a
  fine-grained token scoped to this one repository with contents and pull-request write only,
  and it should never be in a build that leaves the owner's devices. What the model returns is
  validated (bundled files only, new files only under `app/src/test/`, no path traversal) but not
  compiled; CI compiles it, a human reads it, and nothing merges by itself.
- **What leaves the device.** To the model: the trace, the architecture notes, and the app's own
  source files for the frames in the trace. To GitHub: the patched files and the PR text. The
  trace can include values the user typed (`For input string: "12.5"`), and that is the one place
  user data can travel. Nothing else about the user is collected.
- **The app cannot verify itself.** "Confidence" is the model's own estimate. CI on the pull
  request is the measurement, and the table above is the result.
- **Whole-file patches.** The model must return every patched file in full. Large files hit
  `max_tokens` and the diagnoser says so rather than applying a truncated file.
- **Crashes outside the bundle** (framework, SDKs, Compose internals) produce no own frames and
  are reported as undiagnosable.

## Setup

`local.properties`:

```
sdk.dir=...
ANTHROPIC_API_KEY=...
GITHUB_TOKEN=...      # fine-grained, this repository only, contents + pull requests: write
```

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then break the bill splitter, reopen the app, and go to Heal.

## Verdict

_(after the first pull requests)_

## Next

- **Stage 4**: CI uploads the APK; the app fetches the build for a merged PR and updates itself
  through `PackageInstaller`. The loop closes.
- **Diagnose on launch**: today both steps are behind a tap. With a token scoped to one repository
  the first step could run on its own; the second should stay behind a human until the table
  above earns it.
- **ANRs**: `ApplicationExitInfo` delivers their traces too; the host feature needs a slow path
  to produce one honestly.

---

**Reason I don't regret switching to Android** (reserved for this series, to be written once the
loop has closed): On this planet, an app is allowed to rewrite itself.
