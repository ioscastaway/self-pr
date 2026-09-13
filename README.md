# self-pr

An Android app that carries its own source code, catches its own crashes, diagnoses them against
that source with Claude, and opens a pull request on its own repository with the fix. CI builds the
PR; a human merges. The app cannot run its own tests, so it files the paperwork instead.

> Stage 3 of the Evolving App series. Stage 1 taught an app to rewrite its rules. This one is
> about rewriting code, and about the one thing an app can never do for itself: prove the patch
> works. So the app does what any engineer without a build machine does. It opens a PR and lets
> CI argue.

**Series:** Evolving App (stage 3) · Android × AI
**Status:** the loop has closed twice. On 2026-09-13 the app crashed on the Android 16 emulator,
diagnosed itself at its next launch, and opened [#1](https://github.com/ioscastaway/self-pr/pull/1)
(decimal bill amount) and [#3](https://github.com/ioscastaway/self-pr/pull/3) (empty history), each
with a fix and a new test. CI passed on both and both are merged, so the host feature is now
fixed by its own app and needs new first-draft bugs. The table at the bottom of *Experiment* is
the running log.

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

| Input | Crash | Line | Fixed by |
|---|---|---|---|
| `12.5` or an empty amount | `NumberFormatException` | `amountText.trim().toInt()` | the app, #1 |
| `0` people | `ArithmeticException: divide by zero` | `(amount + tip) / people` | the app, #1 (it noticed the next line while fixing the first) |
| *Show last* before any split | `NoSuchElementException` | `history.last()` | the app, #3 |

None of these was a planted `throw`. They were the three bugs a reviewer would find in the first
five minutes, left in so the app had something real to fix. All three are gone now, by the app's
own pull requests; the next round needs a fresh first draft.

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
| [#1](https://github.com/ioscastaway/self-pr/pull/1) | `NumberFormatException: For input string: "12.5"` at `BillSplitter.split` | Parse the amount as a decimal rounded to whole units and the people count as a positive integer, raise a typed `InvalidInput` with a user-facing message, catch exactly that one type in the view model, add `BillSplitterInputTest` (7 cases). Confidence 0.85. Four caveats, including "I could not compile or run the tests on device; CI on this PR is the first real check." | pass (20 tests, 4m05s) | yes |
| [#3](https://github.com/ioscastaway/self-pr/pull/3) | `NoSuchElementException: List is empty.` at `BillSplitter.lastSplit` | `lastSplit` returns `Result?` via `lastOrNull()`; `showLast()` shows "No splits yet." on null; new `BillSplitterLastSplitTest`. Two source lines changed. Confidence 0.88. Caveat: the return type changed, so any caller outside the bundle needs a null check. Filed with a fine-grained token scoped to this repository. | pass, twice: once as filed, once after the human merged `main` into it | yes, after a hand-resolved conflict with #1 |

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

- **The diagnosis was better than the bug deserved.** Given the trace, the notes and two source
  files, the model fixed the crash at the input boundary rather than at the throw site, noticed
  the divide-by-zero one line later and fixed it in the same change, declined to fix the third bug
  (`history.last()`) because it was a different crash, and said so in a caveat. It also wrote a
  test that reproduces the exact string from the trace. This is what a careful reviewer would have
  asked for.
- **The validator earned its place before the model misbehaved.** Nothing was refused in the first
  run, but the rules (bundled files only, tests may be new, no `..`) are the difference between
  "an app that edits its own repository" and "an app that edits a repository". They are code, not
  prompt, on purpose.
- **Whole-file patches are the right size here and the wrong size later.** Three files, under
  three kilobytes each, came back complete at `effort: high` in about eighty seconds. A file ten
  times that size would hit `max_tokens` and the diagnoser would refuse it rather than apply a
  truncated file. Stage 3 on a real app needs diffs, and diffs need a validator that can apply
  them.
- **The contents API means one commit per file.** The PR had three commits with the same message.
  Harmless with squash-merge, ugly otherwise; the Git Data API (blob → tree → commit) would make
  it one commit and is the next change to `GitHubPublisher`.
- **The trace carries user input.** `For input string: "12.5"` went to the model and into the PR
  body. For a bill amount that is nothing; for a real app it is the one place this design leaks
  user data, and the redaction has to happen before the trace leaves the process.
- **"Confidence" is a claim, CI is a measurement.** The app reported 0.85 and 0.88; CI reported
  pass twice. The table above is the only number that matters, and it needs many more rows before
  the second tap can go away.
- **Two whole-file PRs from the same base cannot both merge.** #1 and #3 each replaced
  `BillSplitter.kt` in full from the same revision, so the second one conflicted once the first
  was in. A human merged `main` into #3 and kept both changes; CI passed again. The app cannot do
  that step today: it would need to re-diagnose against the new base, which means the bundled
  source must be the source of `main`, not of the build that crashed. Diff-shaped patches would
  make most of these conflicts disappear; the rest are stage 3.5.
- **The smallest correct fix is a sign of a good diagnosis.** For the empty-history crash the
  model changed two source lines (`last()` → `lastOrNull()`, a null message in the view model)
  and wrote a test, then flagged the one real consequence: a changed return type that any caller
  outside the bundle would need to know about. It did not touch the two bugs it had already
  fixed in #1, because #1 was not merged and the bundled source it saw was still the original.

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

Genuinely useful, in the narrow sense that the pull request it opened is one I would have merged
from a colleague, test included. Merely possible, in the wide sense that one crash on one demo
feature proves the pipeline and nothing about the hit rate. The honest claim is structural: an
Android app can capture its own death, read its own source, argue for a fix and file it, with
public APIs and two taps, and the only thing it cannot do for itself is the one thing CI does.

## Next

- **Stage 4**: CI uploads the APK; the app fetches the build for a merged PR and updates itself
  through `PackageInstaller`. The loop closes.
- **Diagnose on launch**: today both steps are behind a tap. With a token scoped to one repository
  the first step could run on its own; the second should stay behind a human until the table
  above earns it.
- **ANRs**: `ApplicationExitInfo` delivers their traces too; the host feature needs a slow path
  to produce one honestly.

---

**Reason #09 I don't regret switching to Android:**
On this planet, an app is allowed to rewrite itself.
