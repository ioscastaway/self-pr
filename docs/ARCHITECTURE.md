# self-pr — architecture notes

These notes are bundled into the APK as an asset, next to a zip of the app's own source tree. The
running app hands both to the model when it diagnoses one of its own crashes. Keep them accurate;
the app shows them on the About tab. `BuildConfig.GIT_SHA` names the exact revision they describe.

## Package map

```
com.ioscastaway.selfpr
├── App.kt                  Application: builds Graph, installs CrashCollector, imports pending crashes
├── Graph.kt                Manual DI
├── demo/                   The host feature
│   └── BillSplitter        Pure Kotlin bill splitting. First-draft quality on purpose; see README
├── healer/                 Pure Kotlin. No Android imports. Runs on the JVM in tests
│   ├── CrashReport         CrashReport, HealRecord (NEW / DIAGNOSED / FILED / FAILED)
│   ├── TraceParser         own frames, headline, culprit (innermost cause first)
│   ├── SourceIndex         path → content of the bundled tree; class name → path; test next to main
│   ├── Diagnosis           Diagnosis, FilePatch, Diagnoser seam, PatchValidator, HealPrompt (+ schema)
│   └── PullRequestPlan     branch name, commit message, PR title/body, GitHub request bodies
├── platform/               Everything that touches Android or the network
│   ├── CrashCollector      uncaught handler → file; ApplicationExitInfo → CrashReport
│   ├── HealStore           files/heal/crashes.json + records.json, StateFlows
│   ├── KnowledgeBase       assets/source.zip + assets/ARCHITECTURE.md, read once
│   ├── Secrets             BuildConfig keys → Anthropic client / GitHub token
│   ├── ClaudeDiagnoser     Diagnoser over the Anthropic SDK, structured output, one call
│   ├── GitHubPublisher     REST: base ref → branch → contents PUT per file → pull request
│   └── Healer              the loop; validation and the confidence gate live here
└── ui/                     Compose. MainActivity, HealViewModel, SelfPrApp (Split / Heal / About)
```

## The loop

```
crash (main thread)
  → CrashCollector handler writes files/crashes/<ts>.txt, process dies
next launch
  → CrashCollector.importPending: file → CrashReport; ApplicationExitInfo → CrashReport (ANR, native)
Heal tab, Diagnose
  → TraceParser.ownFrames(trace, applicationId)
  → SourceIndex.filesFor(frames) + the test next to the culprit
  → ClaudeDiagnoser.diagnose(crash, files, notes)      output_config.format = HealPrompt.schema
  → HealRecord(DIAGNOSED, diagnosis)
Heal tab, Open pull request
  → PatchValidator.validate: only bundled files or new files under app/src/test/, non-empty, changed
  → confidence ≥ Diagnosis.MIN_CONFIDENCE_TO_FILE
  → PullRequestPlan.from(crash, diagnosis, changed)
  → GitHubPublisher.open: GET git/ref/heads/main → POST git/refs → PUT contents/<path> ×N → POST pulls
  → HealRecord(FILED, prUrl)
GitHub Actions (.github/workflows/ci.yml)
  → testDebugUnitTest + assembleDebug on the pull request; the APK is an artifact
a human merges
```

## Invariants

- `healer/` never imports `android.*`.
- The app never publishes without two taps: Diagnose, then Open pull request. The diagnosis is
  shown in full (root cause, confidence, every patched file) before the second tap is possible.
- The model may only replace files that are bundled, and may add files only under
  `app/src/test/`. Paths with `..` are refused. This is enforced in `PatchValidator`, not in
  the prompt.
- A diagnosis below `MIN_CONFIDENCE_TO_FILE` is displayed but cannot be filed.
- What leaves the device: the trace, these notes, the bundled source files for the frames in the
  trace (to the model); the patched files and the PR text (to GitHub). Nothing about the user.
- The knowledge base is rebuilt on every build by the `bundleSource` Gradle task. If a file is
  not in `app/src/main/java`, `app/src/test/java`, the manifest, `app/build.gradle.kts`,
  `gradle/libs.versions.toml` or `docs/ARCHITECTURE.md`, the app cannot see it and will not
  patch it.

## Known limits

- The app cannot compile or test its own patch. CI does that on the pull request; the app's
  "confidence" is the model's, not a measurement.
- Whole-file replacement means the model must return every patched file in full; large files
  hit `max_tokens`, and the diagnoser reports that instead of applying a truncated file.
- Crashes in code the app did not bundle (Compose internals, the SDKs) produce no own frames and
  are reported as undiagnosable.
- One commit per file: the contents API's shape. A multi-file fix is several commits on the
  branch; the PR squashes them on merge.
