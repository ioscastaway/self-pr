# self-pr

An Android app that carries its own source code, catches its own crashes, diagnoses them against
that source with Claude, and opens a pull request on its own repository with the fix. CI builds the
PR; a human merges; the app downloads the build CI made of the merge and installs it over itself.
The app cannot run its own tests, so it files the paperwork instead, and then it collects the
result.

> Stages 3 and 4 of the Evolving App series. Stage 1 taught an app to rewrite its rules. Stage 3
> is about rewriting code, and about the one thing an app can never do for itself: prove the
> patch works. So the app does what any engineer without a build machine does. It opens a PR and
> lets CI argue. Stage 4 is the part iOS does not have a public path for: the app installs the
> verdict.

**Series:** Evolving App (stages 3 and 4) · Android × AI
**Status:** the stage 3 loop has closed twice. On 2026-09-13 the app crashed on the Android 16 emulator,
diagnosed itself at its next launch, and opened [#1](https://github.com/ioscastaway/self-pr/pull/1)
(decimal bill amount) and [#3](https://github.com/ioscastaway/self-pr/pull/3) (empty history), each
with a fix and a new test. CI passed on both and both are merged, so the host feature is now
fixed by its own app and needs new first-draft bugs. The table at the bottom of *Experiment* is
the running log. Stage 4 (2026-09-14, [#4](https://github.com/ioscastaway/self-pr/pull/4)) runs
end to end on the Android 16 emulator up to the system's install dialog; the first successful
self-install is waiting on the CI signing secrets, see *Stage 4* under *Experiment*.

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
- `PackageInstaller`: an app can open a session, stream an APK into it and commit; the system
  asks the user, checks that the new APK is signed with the same key as the installed one, swaps
  the package and kills the process. Any app can do this to itself, given the per-app "Install
  unknown apps" grant. This is stage 4, and the reason the loop is worth closing on Android.

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

### Stage 4: installing the result

CI already uploaded the APK of every build as an artifact. Stage 4 is the app going to get it.

```
Update tab, tap 1: Ask CI
              GitHubBuilds: newest successful run of ci.yml on the channel (a branch; main by default)
                                   │
              GitHub compare: <this build's commit>...<the run's commit>
                                   │
              UpdateDecision: that is this build / ahead by N commits (these ones, from PR #M) /
                             sideways (behind, diverged) / unknown (this build's commit is not on GitHub)
                                   │  Update tab, tap 2: Install run #N over this app
              artifact zip ──► ApkExtractor (exactly one .apk, no `..`) ──► cache
                                   │
              prefs: "pending: from <sha> to <sha>, run #N"        written first; nothing after commit survives
                                   │
              PackageInstaller session ──► commit ──► the system's dialog, tap 3 ──► signature check
                                   │
              swap, process killed ──► next launch: "this launch is the build the app asked for"
```

The channel is a branch name so the loop can be tested before the merge: pointed at a pull
request's branch it installs that PR's CI build. Pointed at `main`, it installs merged work,
which after stage 3 means the app's own fixes.

What actually happened on the Android 16 emulator, 2026-09-14, from the build in #4:

| Step | Result |
|---|---|
| Ask CI on `main` | run #10, `push`, the same commit as the running build: "That is this build. Nothing to install." |
| Ask CI on `feat/self-update` | run #11, `pull_request`, ahead by 1 commit, listed by subject |
| Install run #11 | 88 MB artifact zip downloaded and unpacked, session committed, the system asked "Do you want to update this app?" |
| Tap Update | `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.ioscastaway.selfpr signatures do not match newer version` |

The refusal is correct. CI had no signing key that run and used the runner's throwaway debug key;
the build on the emulator carries the shared key. Android will not let a package replace itself
with one signed differently, whoever asks. The same install with the key in CI is the next row
of this table.

### Pull requests the app has opened about itself

| # | Crash | What the app proposed | CI | Merged |
|---|---|---|---|---|
| [#1](https://github.com/ioscastaway/self-pr/pull/1) | `NumberFormatException: For input string: "12.5"` at `BillSplitter.split` | Parse the amount as a decimal rounded to whole units and the people count as a positive integer, raise a typed `InvalidInput` with a user-facing message, catch exactly that one type in the view model, add `BillSplitterInputTest` (7 cases). Confidence 0.85. Four caveats, including "I could not compile or run the tests on device; CI on this PR is the first real check." | pass (20 tests, 4m05s) | yes |
| [#3](https://github.com/ioscastaway/self-pr/pull/3) | `NoSuchElementException: List is empty.` at `BillSplitter.lastSplit` | `lastSplit` returns `Result?` via `lastOrNull()`; `showLast()` shows "No splits yet." on null; new `BillSplitterLastSplitTest`. Two source lines changed. Confidence 0.88. Caveat: the return type changed, so any caller outside the bundle needs a null check. Filed with a fine-grained token scoped to this repository. | pass, twice: once as filed, once after the human merged `main` into it | yes, after a hand-resolved conflict with #1 |

## Architecture

`healer/` is pure Kotlin with no Android imports: trace parsing, the source index, the diagnosis
model and prompt, the validator, the pull-request plan with its request bodies. `updater/` is the
same for stage 4: the parsers for GitHub's runs, artifacts and compare responses, the update
decision, the APK extractor, the pending-update record. All of it runs in JVM tests. `platform/`
holds the crash collector, the JSON store, the knowledge base reader, the Claude diagnoser, the
GitHub publisher and the `Healer` that chains them; and for stage 4 `GitHubBuilds`,
`SelfInstaller` around the `PackageInstaller` session, and the `Updater` that chains those. The package map, the
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

- **The signing key is the whole trust story of stage 4.** Android's rule is simple: a package
  can only be replaced by one signed with the same key. So the phone's build and CI's build have
  to share a key, which means the key lives outside the repository and inside CI's secrets. Read
  the other way: whoever holds that key and can make CI run can put code on the phone, one
  dialog away. For this experiment that is the author, on a repository where the app itself
  opens pull requests and a human merges. The key is the line between "the app updates itself"
  and "anyone updates the app".
- **Android checks the signature after the user says yes, not before.** The dialog came up,
  "Update" was tapped, and only then did the session fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
  The app cannot check either: a third-party app cannot read the signing certificate out of an
  APK it has not installed without parsing it by hand. So the Update tab warns from
  `BuildConfig.SIGNED_FOR_UPDATE` on the running side, and takes the system's word on the other.
- **"Is this newer than me" is a question for GitHub, not for version codes.** The build knows
  its commit; CI's run knows its commit; `compare/a...b` says `ahead`, `behind`, `identical` or
  `diverged` and lists the commits in between with their `(#N)` squash suffixes. That is a
  changelog the app did not have to write. When the running build's commit is not on GitHub (a
  local build of uncommitted work), compare 404s and the app says it is installing blind rather
  than guessing.
- **A pull request's run does not build the commit you think.** CI checks out a merge commit
  that exists only on the runner, so `git rev-parse HEAD` there names a revision GitHub's API
  has never heard of. The workflow passes `github.event.pull_request.head.sha` into the build
  instead. Without that, every PR build would look like an update to itself forever.
- **The APK arrives inside a zip that needs a token.** Artifacts download as a zip of whatever
  was uploaded, and the endpoint is authenticated even on a public repository. The fine-grained
  token from stage 3 turned out to be enough. Releases would be simpler to download and would
  not expire after ninety days; artifacts were already there.
- **Nothing after `commit` runs.** A successful self-update kills the process, so the app writes
  what it is about to do before it does it, and the next launch decides whether it is the build
  it asked for. An install that fails runs the callback and forgets the note. This is the same
  shape as the crash handler in stage 3: write first, reason later.
- **Keys had to move out of the APK.** A CI build has no `local.properties`, so a stage 4 build
  would arrive with no Anthropic key and no GitHub token and stage 3 would stop working after
  the first update. App data survives an update, so the keys are copied into app-private storage
  by the local build and inherited by every build that replaces it.

## iOS comparison

| | Android | iOS |
|---|---|---|
| Read the app's own crash and ANR history at next launch | `ApplicationExitInfo`, on demand | `MetricKit`, delivered on the system's schedule |
| Bundle the app's own source | `assets/` | Bundle resources; equivalent |
| Ask a model for a patch, open a PR | HTTP | HTTP; equivalent |
| Know which commit you are and whether CI has a newer one | `BuildConfig` + GitHub compare | Same; portable |
| Install the resulting build (stage 4) | `PackageInstaller`: a session, a system dialog, a signature check | No third-party path. App Store and TestFlight are the only installers of third-party code; there is no API for an app to hand the system a replacement of itself |

Stage 3 is portable. Its value is that stage 4 is not. The precise statement: iOS has no public
API through which a third-party app can install or update a package, its own included; on
Android the API is public, gated by a per-app grant and a per-install dialog, and bound by the
signing key.

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
- **Stage 4 is three confirmations deep and stays there.** Ask CI, then Install, then the system's
  own dialog; plus the one-time "Install unknown apps" grant in Settings. The app never checks
  or installs on its own. Removing any of these is a policy decision, not a code change.
- **The signing key is a secret with the reach of a root shell on this one app.** It is kept
  outside the repository, in `local.properties` locally and in repository secrets in CI. Rotating
  it means uninstalling the app on every device, because that is exactly what Android forbids
  doing in place.
- **Artifacts expire** after ninety days; a `main` that has not moved for that long has nothing
  to install. The app reports the expired artifact rather than reaching for an older run.
- **Update ownership is not requested.** Android 14's `setRequestUpdateOwnership` would make
  the app the only installer allowed to update it silently; this experiment leaves adb and Play
  able to replace it, which is what testing needs.
- **Play Protect and OEM gates** were not exercised: the emulator image has no Play Protect
  scanning, and the Galaxy Z Fold 8 has not been through this loop yet.

## Setup

`local.properties`:

```
sdk.dir=...
ANTHROPIC_API_KEY=...
GITHUB_TOKEN=...      # fine-grained, this repository only, contents + pull requests: write (actions: read for stage 4)
SELF_PR_KEYSTORE=/absolute/path/outside/the/repo/self-pr-update.jks   # stage 4, see below
SELF_PR_KEYSTORE_PASSWORD=...
SELF_PR_KEY_ALIAS=self-pr
```

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then break the bill splitter, reopen the app, and go to Heal.

For stage 4 the build on the phone and the build CI makes must share a signing key. Make one
outside the repository, point `local.properties` at it, and give CI the same key as secrets:

```bash
keytool -genkeypair -keystore ~/.config/ioscastaway/self-pr-update.jks -storetype PKCS12 \
  -alias self-pr -keyalg RSA -keysize 2048 -validity 10000
base64 -i ~/.config/ioscastaway/self-pr-update.jks | gh secret set SELF_PR_KEYSTORE_B64
gh secret set SELF_PR_KEYSTORE_PASSWORD
gh secret set SELF_PR_KEY_ALIAS --body self-pr
```

Reinstall once (uninstall first: a key change is the one update Android refuses), grant
"Install unknown apps" for the app when the Update tab asks, and from then on the Update tab
installs whatever CI built on `main`.

## Verdict

Genuinely useful, in the narrow sense that the pull request it opened is one I would have merged
from a colleague, test included. Merely possible, in the wide sense that one crash on one demo
feature proves the pipeline and nothing about the hit rate. The honest claim is structural: an
Android app can capture its own death, read its own source, argue for a fix and file it, with
public APIs and two taps, and the only thing it cannot do for itself is the one thing CI does.

Stage 4, so far: every step up to the system's signature check ran on the emulator, and the
check refused an unsigned CI build for exactly the reason it should. Whether the loop closes is
now a matter of three repository secrets, and the table under *Stage 4* will say when it did.

## Next

- **Stage 4, closed**: the first self-install with a signed CI build, recorded in the table
  above; then the same on the Galaxy Z Fold 8, where Play Protect and One UI get a say.
- **Check on launch**: the Update tab could ask CI at every start and show a badge; installing
  should stay behind the taps.
- **Update ownership**: `setRequestUpdateOwnership` once the loop has run enough times that
  adb is no longer the main way the app gets onto the phone.
- **Diagnose on launch**: today both steps are behind a tap. With a token scoped to one repository
  the first step could run on its own; the second should stay behind a human until the table
  above earns it.
- **ANRs**: `ApplicationExitInfo` delivers their traces too; the host feature needs a slow path
  to produce one honestly.

---

**Reason #09 I don't regret switching to Android:**
On this planet, an app is allowed to rewrite itself.
