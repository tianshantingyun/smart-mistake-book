# Known Defects Register

> **2026-10-18 绘图路线更新：** 所有生图入口统一判定，由模型按共享规则选择JS或图像模型；两者均为生图方式，正式统一接线待实施。当前约束见[教学图形契约](current/visual-generation-contract.md)。本文既有日期、提交、测试、缺陷与旧方案作为历史事实保留，不代表新路线已实现。

Entries are numbered `KD-n` and marked in their heading as open or resolved.
Resolved entries stay for provenance — they record the evidence that closed the
defect and the condition that would reopen it — so the register is also the
place to check whether a past symptom has a known disposition before reopening
it as new. Only an entry without a `(resolved …)` marker blocks a gate or a
release claim.

## 2026-09-09 audit fixes (closed, see commit history)

The full audit (`scratch/AUDIT-*-2026-09-09.md`) found and closed:

- **P0 · batch import blocked in production** — split recognition threw before
  every page could be imported (zero dimensions + fabricated egress manifest +
  no try/catch). Intake now follows spec `batch-intake` I1: the page lands in
  the library first, split recognition is best-effort afterwards. `fae1a82`.
- **P1 · knowledge-quiz write quota was a lifetime quota** — a global
  conversation id made the 50-write per-conversation cap permanent, so mastery
  writes stopped forever after 50 accepted answers. The quota is now per review
  session. `43ba61b`.
- **Three algorithm defects** — `MasterySmoothing` EMA cancelled age out
  (weakness halved), a "very effortful" self-report was lifted to Good, and
  hint/retry-assisted correct answers earned the independent-recall gain.
  `544b575`.
- **Scheduling settings were unwritable** — `setOptions` / `declareExam` /
  `removeExam` had no caller. New scheduling screen wires retention, the FSRS
  switch and the exam calendar. `141a7e1`.
- Export print ignored the requested page range; release builds logged the
  provider response body; two sources carried literal NUL bytes; `feature:tutor`
  declared an unused `core:data` dependency. `141a7e1`.
- **User-created knowledge nodes were unreusable** — they were permanently
  marked `MODEL_CANDIDATE` and excluded from candidate queries. New
  `USER_CONFIRMED` tier. `7d4adc4`.

The entries below are numbered `KD-n`; those still without a `(resolved …)`
marker are open. The batch above closed on 2026-09-09.

## KD-2 (resolved 2026-08-30) · Wall-clock p95 gate on CI runners

**Symptom.** `KnowledgeContextRetrievalInstrumentedTest#largeSubjectRecallRemainsBoundedOnRoom`
failed on CI: "Mastery snapshot read p95 was 278ms; samples=[278, 224, ...]".
Passed locally (API-34 emulator, WHPX). Absolute wall-clock budgets on shared
CI runners are environment-sensitive by construction; the deterministic parts
of the test (index-usage query-plan assertions) all passed, so the divergence
pointed at runner noise rather than a query regression.

**Resolution.** The mastery/recall p95 budgets are environment-aware — strict
locally (150/250ms), 4x on GitHub runners (`CI=true`), which covers the ~278ms
p95 measured there. The multiplier travels as the `ciSlowRunner`
instrumentation argument because runner environment variables do not propagate
into the on-device test process (`System.getenv("CI")` is always null there);
`android-check.yml` passes `-Pandroid.testInstrumentationRunnerArguments.ciSlowRunner=1`.
The deterministic index-usage query-plan assertions are unchanged.

**Reopen condition.** If a runner slowdown grows beyond the multiplied budget,
revisit with a runner-relative bound or move the wall-clock gate to the
macrobenchmark module, keeping the query-plan assertions here.

## KD-3 (resolved 2026-09-09) · Coverage rows for Android modules in status.md

`:core:domain` line/branch coverage is wired (Kover → generate_status.py).
The `:core:database` and `:core:data` rows were NOT_MEASURED because those
Android modules could not be instrumented. Resolved by the AGP + Jacoco path
described in KD-5: status.md now reports `:core:data` 53.9% / 39.1% and
`:core:database` 5.2% / 6.9%.


## KD-1 (resolved 2026-09-12) · Tutor external-authorization flow regression (instrumented)

**Symptom.** 4 of 57 tests in `CapturedTutorSessionInstrumentedTest`
(feature:tutor connected) fail deterministically on a fresh API-34 emulator:

- `expiredLeaseKeepsExactNextPlanUntilOneConfirmationResumesIt` —
  `captured_tutor_disclosure` not composed (assertIsDisplayed fails).
- `authorizedExternalAutoStartExecutesOnlyTheInitialPlanWithoutAnotherConfirmation` —
  waitUntil(5000ms) timeout.
- `localNoEgressFirstStartNeverShowsAnExternalProviderDisclosure` — waitUntil
  timeout; the panel never calls `executePlan` (fake counter stays 0).
- `restartingAfterRecreationCarriesExactStudentWordsWithStableRequestIdentity` —
  waitUntil timeout.

**Scope evidence (collected 2026-08-30).**

- Reproduces identically at commit `25fb15a` (verified in a worktree), i.e.
  the regression predates everything pushed after 2026-08-26.
- Every android-check run since 2026-08-23 (15+ runs) is failure/cancelled:
  the instrumented job aborts at the first failing connected task, so the
  tutor suite has not executed on CI in that window. No green anchor exists
  for a bounded bisect.
- Unchanged since before the break: `TutorSessionPanel.kt`,
  `CapturedTutorSessionRoute.kt`, `TutorPlanCommands.kt`,
  `ModelGateway.kt` (interface), the test file itself. Post-break diffs to
  `TutorModelTaskPolicy.kt` / `TutorTasks.kt` are additive debrief types.
- Panel gating traced: `currentProvider` ← `modelTasks.capabilities()`
  (fake returns a valid snapshot); `planLeaseApprovedAt` null for
  external-without-lease → `else` branch should compose `TutorDisclosureCard`.
  LOCAL auto-exec requires `observedTask == null` (projection returns null
  for empty task lists — verified).

**Suspicion ranking.** (1) something inside the compose effect/lease-time
semantics that the additive diffs interact with, (2) long-standing break from
the 4eee4d8-era rework that was never gated (no CI coverage), (3) emulator
timing — weakened by the expired-lease failure being a direct assertion.

**Next steps.** Dedicated session: bisect past 25fb15a (build per step
~4-6 min, no known-good anchor — try fixture-era commits first), or add
compose-state logging to `TutorModelPanel` and diff intended vs actual
branch selection for the two LOCAL/EXTERNAL scenarios above.

**Resolution evidence (2026-09-06).** The full suite was re-run twice on
emulator-5554 (Pixel 6 AVD `test_device`, API 34) against the current tree
(`2c62700` + in-flight workspace): `:feature:tutor:connectedDebugAndroidTest`
→ **57/57 passed, twice** (fresh install each run; AGP uninstalls after the
run). All four previously-deterministic failures pass. Suspected fix carriers
are the tool-loop hardening commits landed 2026-09-05/06 (`7cb373e` Lobby
disclosure boundary + T6 write-tool anchoring, `9f267f0` T6 mastery_update
chain, `5a940a1` deterministic evidence ids, `c19b330` indexed gate queries).

**Closed 2026-09-12 — the CI anchor arrived.** Run
[34693827527](https://github.com/tianshantingyun-lab/smart-mistake-book/actions/runs/34693827527)
(commit `5e2c3a9`, push to main) posted a **fully green `instrumented` job**:
`BUILD SUCCESSFUL` once, **0 `FAILED`** in the job log, and
`:feature:tutor:connectedDebugAndroidTest` executed **46 tests with 0 failures**.
That is exactly the closing condition this entry was holding for. `check` was
green in the same run.

One correction to the scope notes above: the entry says the suite "has not
executed on CI" for 15+ runs because the job aborted at the first failing
connected task. That was true before 2026-08-30, when the step gained
`--continue` — after that the suites do run even when an earlier one fails, so
the absence of a tutor anchor was about the run's overall redness, not about
the suite being skipped.

**Reopen condition.** Any recurrence of the four named waitUntil/assertion
failures on CI. They no longer reproduce on the current tree (local twice on
2026-09-06, CI green on 2026-09-12), so a recurrence means a new cause.

## KD-4 (resolved 2026-09-12) · Visual-ui device-acceptance test timed out on CI software rendering

**Symptom.** `TutorVisualComplexCircuitInstrumentedTest#complexCircuitSemanticRedrawPassesDeviceAcceptanceAndSavesStepScreenshots`
fails on CI with `ComposeTimeoutException after 2000 ms` (idle-sync wait).
Passes locally 6/6 on WHPX hardware rendering (2026-08-30). The test has no
explicit waitUntil — the timeout is Compose's internal idle synchronization,
which a continuously-redrawing surface on software GL can fail to satisfy.

**Disposition.** Same family as KD-2 (local/CI timing divergence) but NOT
fixable by a budget constant: the failing wait is implicit. Options for the
dedicated session: (a) disable animation/clock auto-advance for this test,
(b) replace implicit idle waits with explicit `waitUntil(Ns)` on the specific
condition, (c) gate the screenshot-acceptance path on real-GPU devices only.
Until fixed, the instrumented job aborts at core:visual-ui, so downstream
connected tasks (export/capture/tutor/library/app) still lack CI validation.
**Mitigation (2026-08-30)**: the CI instrumented step now runs with `--continue`,
so a visual-ui failure no longer aborts the remaining suites — every other
module keeps getting validated while this defect stays open.

**Reproduction attempt (2026-09-12) — not reproduced, and the hypothesis above
is partly wrong.** The "hardware rendering locally vs software GL on CI" framing
does not hold: the project's own `tools/start-emulator.ps1` already pins
`-gpu swiftshader_indirect`, so local runs are software GL too. What differs is
the CPU virtualisation (WHPX locally, KVM on the runner), not the GPU path.
Tried, all green, cold install each time (AGP uninstalls the test APKs, so the
Filament material cache was never warm):

| Configuration | Result |
|---|---|
| swiftshader + 2 cores, this class only, 4 runs | 4/4 pass |
| swiftshader + 2 cores, whole suite (6 tests, incl. the Filament toggle test) | pass |
| swiftshader + **1 core** + 8 host CPU busy loops, whole suite | pass |

Animations are not the difference either: `TutorAnimationPolicy` reads
`Settings.Global.getFloat(ANIMATOR_DURATION_SCALE, 1f)`, and the setting is unset
on this emulator, so it resolves to "enabled" — the same as a default runner.

**Consequence.** Option (b) remains the right fix on its merits (a surface with
its own render loop should not depend on implicit idle sync), but it cannot be
validated from here: the failure is only observable on the CI runner, so a local
pass would prove nothing and a local green would not close this entry. Apply (b)
in a session that can watch a CI run, and record the CI outcome — do not close it
on local evidence.

**Outcome 2026-09-12 — the suite passed on CI, so the failure has no reproducing
case.** Run
[34693827527](https://github.com/tianshantingyun-lab/smart-mistake-book/actions/runs/34693827527)
(commit `5e2c3a9`) ran `:core:visual-ui:connectedDebugAndroidTest` with
**6 tests, 0 failures**, inside a fully green `instrumented` job (0 `FAILED`
lines, one `BUILD SUCCESSFUL` for all nine suites). The test named in this entry
was among them. This is the same bar that closed KD-1 — one green connected run
on the runner where the failure lived.

**No code change was made for this entry, and that is stated deliberately.** The
green run does not prove an intermittent timing failure is permanently gone; it
proves there is no longer a reproducing case, and the recorded disposition
(options a/b/c) is not worth a blind change to a test that currently passes.
Treat any recurrence as new evidence.

**Reopen condition.** `ComposeTimeoutException after 2000 ms` on the
complex-circuit acceptance test on CI. If it recurs, apply option (b) — explicit
`waitUntil` on the specific condition instead of relying on implicit idle — and
validate it against the run that reproduces it, since local runs (software GL,
2 cores, and even 1 core under host load) do not.

## KD-5 (resolved 2026-09-09) · Android-library coverage was not collectible with Kover 0.9.1 + AGP 9

**Symptom.** `:core:data:koverXmlReport` / `:core:database:koverXmlReport`
produced a report with zero classes (`LINE covered=0 missed=0`), so
`docs/status.md` reported both modules as `NOT_MEASURED` while `:core:domain`
(a `kotlin.jvm` module) reported 72.5% / 51.8%. No variant-specific Kover task
existed, and an explicit `createVariant("unitTest") { add("debug") }` failed
with *"Could not find the provided variant 'debug'"*.

**Cause.** These modules apply `com.android.library` with AGP 9's built-in
Kotlin support (no `org.jetbrains.kotlin.android`), and Kover 0.9.1's variant
detection is built on the Kotlin Android plugin's variant model.

**Resolution (2026-09-09).** Switched the two Android-library modules off Kover
onto AGP's own instrumentation: `buildTypes { debug { enableUnitTestCoverage = true } }`
plus a `JacocoReport` task `unitTestCoverageXmlReport` that writes
`build/reports/coverage/unit-test.xml` (generated `*_Impl`/`*_Factory` classes
excluded, since Room output is not hand-written code).
`tools/ci/generate_status.py` now reads Kover first and falls back to that XML,
so `docs/status.md` reports real numbers: `:core:data` 53.9% line / 39.1%
branch, `:core:database` 5.2% / 6.9%.

**Known limitation.** `:core:database`'s logic lives mostly in DAO default
methods that need a real database; those are covered by instrumented tests,
which neither Kover nor this Jacoco path can measure — the low number is real
for *unit*-test coverage, not a measurement failure.

## KD-6 (resolved 2026-09-10) · `CaptureScreen.kt` exceeded the 1000-line main-file limit

**Symptom.** `feature/capture/.../CaptureScreen.kt` was 1281 lines — one composable
whose command builders (8 `Capture*Commands`, each taking a `*Sink` of
getter/setter lambdas over ~50 `remember`/`rememberSaveable` locals) were
constructed inline.

**Cause.** The builders and their sink lambdas closed over per-field
`remember`/`rememberSaveable` state, so any move risked process-death
restoration semantics.

**Resolution (2026-09-09/10).** Two-step:
1. Commit `c956c6c`: state hoisted into `CaptureScreenState` (own file; every
   field a `mutableStateOf` delegate; a `mapSaver` round-trips exactly the
   fields that were `rememberSaveable`). Screen 1281→1207.
2. Commits `09017e2` + `01cc4ff`: all 8 `Capture*Commands` now take
   `CaptureScreenState` directly. Pure state getters/setters inlined as field
   reads/writes; composite logic (`applyResumeDraft`, `applyAppendedPages`,
   `resetDraftFields`, …) moved into private command methods; cross-command
   steps (`resetDraft`, `persistAdditionalPage`, `afterWorkspaceFlush`,
   `applyReturnedImagePlan`) delegate to the owning command objects; Android/
   ViewModel effects and screen-derived values (`entryGateOpen`,
   `structuredProjection`, request builders) are injected constructor
   functions, so the commands stay free of Android/ViewModel types. The 8
   `*Sink` classes were deleted. Screen 1207→804 (< 1000); 45 stale imports
   removed.

**Verification that closed it.** `python3 tools/ci/check_file_size_gate.py`
no longer lists the path; `:feature:capture:testDebugUnitTest` 99/0;
`:feature:capture:connectedDebugAndroidTest` 23/23 (incl.
`CaptureScreenStateRestorationTest`); `:feature:capture:lintDebug` clean.

## KD-7 (resolved 2026-09-11) · Mistake catalog read per-question memory from the pre-projection table

**Symptom.** `ProblemDao.observeActiveMistakes()` and
`findMistakeBySourceKey()` joined `problem_memory_state`, so every
`next_review_at_epoch_millis` they returned was NULL in production. The
`library_catalog` view had been moved onto `learner_problem_memory_state` back
in migration 35 → 36; these two queries were missed, leaving the same fact
read through two disagreeing paths (the view: real rows but `retrievability`
NULL by design; these queries: an empty table).

**Why it stayed invisible.** The only writer of `problem_memory_state` is
`FixtureSeedDao`. Instrumented tests seed through `seedFixture`, so on device
the join produced real values and every test passed; production has no fixture
seed and returned NULL. `retrievability` is derived from stability plus "now"
and is not stored on the projection, so it is now selected as `NULL` exactly
like the view does, and readers keep computing it from the snapshot.

**Resolution.** Both queries join `learner_problem_memory_state` with
`projection_name = 'study-experience-v1'` (the same predicate and the same
learner-from-the-row resolution the view uses). `MistakeRecord` and its
consumers are unchanged — this was a read-path fix, not a field removal.

**Verification that closed it.** New
`MistakeMemoryProjectionInstrumentedTest` (3 cases) fails on the old join
(2 of 3 red: both projection-backed assertions) and passes after the fix;
`:core:database:connectedDebugAndroidTest` 151/151 and
`:core:database:testDebugUnitTest` green.

**Reopen condition.** Any catalog query that reads a per-question memory field
from a denormalized table instead of the learner projection. The general hazard
is that instrumented tests seed fixtures while production does not, so a
fixture-only writer makes a broken join look healthy on device — prefer a real
`commitProjection` in the test over fixture seeding when the assertion is about
projected state. `retrievability` is intentionally NULL at the SQL layer; a UI
consumer that needs it must go through `StudyExperienceMappers.toCatalogEntry`.

## KD-8 (resolved 2026-09-12) · Delete-all-data left the model API key in the Keystore

**Symptom.** `AndroidBackupRepository.deleteAllData()` swept the Keystore for
aliases starting with `smartmistakebook_`, but the alias the vault actually
writes is `smart_mistake_book_model_api_key_v2`. Nothing ever matched, so the
entry survived "delete all data" — the ciphertext file and DataStore metadata
were gone, so the key was unusable, but the Keystore entry was still there and
the M4 gate ("delete-all leaves an empty app") was not met in the strict sense.

**Cause.** The alias string had two independently written authorities: the vault
(which creates and deletes it correctly via `clear()`) and this sweep, which
re-derived the name and got it wrong.

**Resolution.** `MODEL_SECRET_KEY_ALIAS` is now the one authored value in the
vault file; the vault uses it and `deleteAllData` deletes exactly that alias
instead of matching a prefix.

**Verification that closed it.** New
`BackupRestoreInstrumentedTest#deleteAllDataRemovesTheModelApiKeyKeystoreAlias`
creates the alias through the production vault, asserts it exists, runs
`deleteAllData()`, and asserts it is gone — red before the fix (the fixture
assertion passed, the deletion assertion failed) and green after.
`:core:data:connectedDebugAndroidTest` 98/98; `:core:data:testDebugUnitTest` 395/395.

**Reopen condition.** Any second copy of a Keystore alias string. If the vault
ever gains another alias or a migration path, add it to the sweep by referencing
the constant, and extend the instrumented test to cover it.

## KD-9 (resolved 2026-09-12) · Startup restore-recovery outcome was discarded

**Symptom.** `SmartMistakeBookApplication` called
`BackupRestoreStartupRecovery.recoverOnStartup(this)` and dropped the result. An
interrupted restore that had to be **rolled back** (the restore silently did not
take effect) or **quarantined** (rollback failed, artifacts moved aside) looked
identical to a clean start: the student saw a normal book that was not the one
they left, with no signal that anything happened.

**Resolution.** `RestoreStartupOutcome.attentionRequired()` classifies outcomes
by whether local data moved; the app maps the two that did — `RolledBack` and
`Quarantined`/`Unreadable` — onto a `RecoverableFailure` applied after `Ready`,
so the book stays usable and the student is told the restore did not land.
`NothingToRecover` and `Cleaned` stay silent because the live generation was
never touched. Copy lives in the app layer; only the classification is in
`core:data`.

**Verification that closed it.** `RestoreRecoveryAttentionTest` (5 cases,
`:core:data:testDebugUnitTest`) pins the classification, including that exactly
the two outcomes that move data are reported; both flavors assemble. The
instrumented rollback path
(`BackupRestoreInstrumentedTest#startupRecoveryRollsBackGenerationAfterInterruptedSwap`)
still passes.

**Reopen condition.** A new `RestoreStartupOutcome` variant will fail to compile
in `attentionRequired()`, which is the intended forcing function — classify it
as reporting or silent deliberately. Note the side effect on
`OrphanAssetGcWorker`: it retries unless the state is `Ready`, so the sweep runs
on a later launch rather than during this one.

## KD-10 (resolved 2026-09-12) · "Least mastered" library sort was a no-op

**Symptom.** `LibraryQueryDao` and `RoomLibrarySearchStore` ordered by
`catalog.retrievability` for the `LEAST_MASTERED` sort, but the `library_catalog`
view defines that column as `NULL AS retrievability`. Every row compared equal,
so the sort silently fell through to `updated_at DESC`. The domain enum
`LibrarySort.LEAST_MASTERED` therefore did nothing.

**First assessment was wrong, and worth recording.** This was filed as blocked
because the only fix I could see was exposing a numeric mastery column on the
`@DatabaseView`, which needs a schema bump plus a re-exported `schemas/NN.json` —
impossible locally (`copyRoomSchemas` is `NO-SOURCE`; hand-writing the JSON is
forbidden). But the view *already* exposes the join, and the number is reachable
from the catalog alias by correlation, so no schema change is involved at all.

**Resolution.** All three sort sites order by the weakest
`lower_bound_independent_correct` among the row's bound knowledge points — the
same projection and learner the view's `mastery_id` facet already reads.
`NULL` (no evidence) sorts first, matching the view's status ordering, which
puts `unknown` first. The expression lives in two authored copies:

- `LibraryCatalogSorts.kt` → `LEAST_MASTERED_MASTERY_SQL`, used by the FTS
  store's runtime-built query;
- a `private const val` of the same name in `LibraryQueryDao.kt`, used by both
  `@Query` annotations.

The second copy is forced by Room: a query assembled from a **cross-file**
constant is rejected by KSP ("No property named value was found in annotation
Query" — reproduced for both positional and `value =` concatenation), while a
**same-file** constant compiles. Since textual sharing is unavailable, the sites
are held together by behavior instead.

**Verification that closed it.** New
`LibraryLeastMasteredSortInstrumentedTest` (3 cases) seeds three entries whose
mastery (`0.20 / 0.50 / 0.80`) is deliberately the **reverse** of their
`updated_at` order, so a no-op sort still produces a total order — just the
wrong one. Before the fix the DAO case read
`expected [entry-weak, entry-middle, entry-strong] but was [entry-strong,
entry-middle, entry-weak]`. Each path carries its own assertion, and a mutation
check confirms they bind independently: restoring `catalog.retrievability` in
the FTS store turns **only** the FTS case red, with the same reversed order.
`:core:database:connectedDebugAndroidTest` 154/154 (was 151),
`:core:database:testDebugUnitTest` 67/67, `:core:database:lintDebug` 0 errors,
`:core:data` and `:feature:library` unit tests and lint green, both flavors
assemble.

**Reopen condition.** Any new `LEAST_MASTERED` sort site must carry both a copy
of the expression and its own behavioral assertion — sharing the text is not
available. Note the copy count is the symptom, not the guarantee: if the
expression ever needs to change, change both copies and let the tests confirm
both paths. The `pagingSource` `@Query` shares the DAO's same-file constant with
`page`, so the DAO test covers both.

## KD-11 (resolved 2026-09-12) · API-26 calls in code that must run on API 23

**Symptom.** `minSdk` is 23, but three call sites used APIs that only exist from
26, and core library desugaring does not cover them — `core:data` enables
desugaring, and lint still reports these (lint *is* desugar-aware: the same
report stays silent about the module's many `java.time` usages, which the
desugared library does provide):

- `OpenAiImageGenerationChannel.parseEditResponse` decoded provider images with
  `java.util.Base64.getDecoder()`. **Reachable in production** — attached images
  and the capture clean-redraw path both read this — so on an API 23–25 device
  the decode threw `NoSuchMethodError`.
- `SmbkArchiveCodec.validate` defaulted its scratch directory to
  `Files.createTempDirectory(...)`. Production callers always pass a scratch
  dir, so this was a latent trap rather than a live crash, but the default is
  what the JVM unit tests exercise.
- `TutorVisualMaterialRepository` moved the Filament material cache with
  `Files.move(..., ATOMIC_MOVE)`. Dormant while
  `TutorVisualIsolation.STRUCTURED_SCENE_ISOLATED` is true, live the moment the
  visual path is restored.

**Resolution.** Each call was replaced with an API-23-safe equivalent that keeps
the same semantics: okio's `decodeBase64()` (already how this module encodes, and
it works in JVM tests); `File(System.getProperty("java.io.tmpdir"), "smbk-validate-<uuid>")`
with the same per-call uniqueness and `deleteOnExit`; and a sibling-directory
`renameTo` plus copy-and-delete fallback, which is what `ATOMIC_MOVE` was asking
for since the staging file always sits next to the target. A fourth call site,
`AndroidBackupRepository` reading `context.dataDir` (API 24), went through
`ContextCompat.getDataDir` — also not desugarable, and on the delete-all path.

**Verification that closed it.** `:core:data:lintDebug` and
`:core:visual-ui:lintDebug` report **0 severity=Error** (they reported 4 and 1
NewApi errors plus this severity before); `:core:data:testDebugUnitTest` 395/395
(includes the archive codec, which uses the replaced default);
`:core:visual-ui:testDebugUnitTest` 12/12; `:core:data:connectedDebugAndroidTest`
98/98; both flavors assemble.

**Reopen condition.** Any new `java.*`/`java.nio.file`/`java.time` call on a
minSdk-23 module. To settle coverage, read the spec lint itself reads rather
than guessing from the message: AGP unpacks
`desugar_jdk_libs_configuration-<version>-desugar-lint.txt` under
`.gradle/caches/<gradle-version>/transforms/<hash>/transformed/`. For
`desugar_jdk_libs 2.0.3` it contains 237 `java/time` entries and **no**
`java/nio/file/*` and no `Base64` — which is why `java.time` is safe here while
these four calls were not. Lint's "or core library desugaring" wording is a
heuristic (it appears for modules where desugaring is *off*); the spec file is
the authority. Note the project uses plain `desugar_jdk_libs`, not the `_nio`
variant that would add `java.nio.file`.
**UNVERIFIED**: none of these paths were exercised on an API 23–25 device (only
API 34 is available here); the change is justified by the desugar spec plus
lint's API-level model, not by a device run.

## KD-12 (resolved 2026-09-12) · Room's restricted `useConnection` is used cross-library-group

**Symptom.** `:core:database:lintDebug` reported `RestrictedApi` (severity Error):
`RoomDatabase.useConnection` is restricted to `androidx.room3`'s own library
group, and this app calls it from `SmartMistakeBook.core`. Not one site but
**seven, across three files** — `RoomBackupSupportStore` (4),
`RoomStudyDatabase` (2), `RoomLibrarySearchStore` (1), plus one in
`PerformanceGateTest`'s androidTest.

**Impact.** Not a runtime defect — restricted APIs are a lint-level contract and
those paths work (98/98 instrumented tests). It is a forward-compatibility risk:
the API is outside Room's public surface, so an upgrade may change or remove it
without notice.

**Resolution.** The calls are legitimate — `PRAGMA wal_checkpoint(TRUNCATE)`,
`PRAGMA user_version`, `PRAGMA foreign_keys` and `VACUUM INTO` have no DAO or
query equivalent, and the backup, delete and FTS-trigger paths cannot be built
without them. All eight sites now go through
`RoomDatabase.withRawConnection(isReadOnly) { … }` in `RawConnectionAccess.kt`,
which carries the single `@SuppressLint("RestrictedApi")` and the reason.

A wrapper rather than a suppression per site or per file: the risk deserves
exactly one reviewed decision, and a file- or class-level suppression would also
have silently legalized a *different* restricted API added to those files later.
With the exposure confined to one function, lint keeps working everywhere else;
if Room ever exposes a supported raw-connection API, that one function changes.

**Verification that closed it.** `:core:database:lintDebug` reports **0
severity=Error** (it reported this class before); `:core:database:compileDebugAndroidTestKotlin`
compiles; the module's instrumented suite still passes (:core:database 151/151).

**Reopen condition.** A new direct `useConnection` call outside
`RawConnectionAccess.kt` — lint will flag it, which is the intended forcing
function. If Room's raw-connection API moves, this one function is the change.

## KD-13 (resolved 2026-09-12) · Library-module lint is not gated, so its findings stay invisible

**Symptom.** `.github/workflows/android-check.yml` ran
`lintLocalFirstDebug lintStrictOfflineDebug`, and only `:app` declares the
`localFirst`/`strictOffline` flavors — so ten Android library modules
(`core:data`, `core:database`, `core:ui`, `core:visual-ui`, `core:export`, all
five `feature:*`) were never linted in CI at all. Their `lintDebug` findings,
including every error KD-11 had to fix, accumulated unseen.

**How this was found.** KD-11 was discovered only because a manual
`:core:data:lintDebug` run surfaced four `NewApi` errors that no gate had ever
reported.

**Resolution.** The workflow's Lint step now lists the ten library `lintDebug`
tasks explicitly alongside the two app flavors, with the same reasoning the
unit-test step already documents: a module that is not named is never checked.

**Verification that closed it.** Every module was confirmed clean *before*
wiring — `severity=Error` counts across all ten reports are 0 (warnings remain
advisory: 7/0/3/1/0/5/6/0/3/16, and lint fails the build on errors, so the green
run is itself evidence). The exact command the step will run was then executed
locally as one invocation: `BUILD SUCCESSFUL`. The workflow still parses
(19 steps in the `check` job, Lint step present).

**CI confirmation (2026-09-12).** The step has now actually run on a runner: in
run
[34693827527](https://github.com/tianshantingyun-lab/smart-mistake-book/actions/runs/34693827527)
the `check` job concluded **success** with the Lint step itself reported
`success`, so the ten library modules are linted in CI from this commit on.
Noted gap: `docs/status.md` (generated) still reports only the two app lint
variants — `tools/ci/generate_status.py` reads the app lint XML paths, so the
library results gate the build without appearing in the report. Extending the
generator to include them is optional follow-up, not required for the gate.

**Reopen condition.** A new `core`/`feature` module must be added to that task
list. The step lists modules explicitly rather than using an umbrella task,
which is the convention the Unit-tests step already documents — "the
flavor-named tasks only exist in `:app`; every other module's … are listed
explicitly or they never run in CI" — so the checked set stays reviewable. If a
future finding class is decided to be acceptable rather than fixed, suppress it
at the call site with the reason, the rule KD-12 followed.

## KD-14 (closed 2026-09-19 · host environment boundary) · Deep security scans are systematically inconclusive on this host

**Symptom.** The Mimosa pre-commit/pre-push hook has been reporting
`scanner_enobufs` for many days (scan not completing inside the host tool
window). Full on-demand deep scans do complete — they are sealed — but four
independent runs across 2026-09-14 and 2026-09-17 all report
`runStatus=inconclusive` with the verbatim-same coverage gap: the semantic
phases (`threatModel`, `findingDiscovery`) do not cover entry points /
principals / authorization surfaces, and `pathAnalysis` is N/A. The static
phases cover every file. This is a host/environment gap in the scanner, not a
repository-content problem: the same gap reproduces byte-for-byte across
days, repos states, and run attempts.

**Evidence (sealed artifacts, under `~/.mimosa/security-scans/project-c079ff08106a86cc56edfee2/`):**
2026-09-17 `scan-2026-09-17T16-22-56.206Z-b0fc1c53ff5f`
(seal `sha256:58ff6b04…`) and `scan-2026-09-17T16-28-12.386Z-e9d35c5c3609`
(seal `sha256:88601862…`); 2026-09-14
`scan-2026-09-13T16-35-18.764Z-0b14062db08c` / `scan-2026-09-13T16-38-37.763Z-7ce64768d5b5`.
Findings in all runs: 0 high / 0 medium / 3 low, the lows being CWE-330 in the
untracked parallel-session tool `tools/kb_build/audit_quality.py`
(`random.Random(fixed seed).sample` — deliberately seeded reproducible
sampling for humans, a substantive false positive). Dependency surface: 38
packages, 0 advisories.

**Known discrepancy.** The `security_scan_status` API summary carries
P1/P2/P3/P4 counts (e.g. 1/4/5/8 + 22 excluded) that do not match the sealed
report body (`0 business-logic candidate`, 3 low occurrences). Per the scan
contract, the sealed artifacts (`findings.json`, `report.md`, `seal.json`) are
authoritative; the API summary counts must not be quoted as specific findings.

**Consequence.** No completion claim may say "security audit passed". What is
defensible today: full static-mode scan with 0 high/medium and clean
dependency surface; the semantic threat-model phase is uncovered on this host.

**Resolution / close condition (updated 2026-09-18 with root cause).**
`mimosa doctor` pinpoints the missing runtimes on this host: the bundled
**Semgrep CE is not installed** (`install_metadata_missing`) and **PyCG is
unconfigured**, which is exactly why `--deep` degrades ("PyCG 未配置，--deep
将优雅降级到 Mimosa 静态可达性") and `pathAnalysis` comes back N/A. The
self-repair path is `mimosa semgrep install --accept-license` (and a PyCG
configuration), but that command is currently **blocked by the plugin's own
PreToolUse hook**, which misclassifies running the plugin CLI as a write to
the plugin cache file (two identical rejections observed 2026-09-17). So:
(a) run the install command manually from a terminal outside the agent
hooks, rerun a deep scan, and if it seals with full coverage, close this
entry with its finding summary; or (b) the Mimosa plugin side fixes the
hook false-positive / the local runtime gap. **CI cannot close this entry**:
the semantic review phase is performed by the ZCode host model (per the
plugin manifest), which does not exist on GitHub runners — a CI-hosted scan
would reproduce the same gap, not fill it. Until closed, treat this entry as
an open boundary: git-hook `scanner_enobufs` pass-throughs and any "no
scanner conclusion" note are expected, and every release claim must cite
this entry rather than assert security.

**Closed 2026-09-19 — disposition: known host environment boundary.**
Path (a) was attempted in full and cannot close this entry on this machine:
the install was run manually from a user terminal with
`MIMOSA_SEMGREP_PYTHON` pointed at the host's Python 3.13.14 (`semgrep
install --accept-license`), and `mimosa doctor` still reports
「Semgrep：内置规则检出样例，但 **Semgrep 进程不可用**」 plus 「PyCG：未配置」.
The follow-up deep scan (`scan-2026-09-19T06-17-24.939Z-fd3a2dd1012d`,
sealed `sha256:f1bffd7f…`) finished in 8 seconds and sealed **exactly the
same `runStatus=inconclusive`** as the four rounds recorded above
(`threatModel` partial with 0 entry points/principals/authorization
surfaces, `pathAnalysis` N/A, `completeness=partial`) — i.e. the scanner
process still does not see a usable Semgrep runtime, so the gap is a host
runtime/detection boundary, not repository content. Per the user's decision
(2026-09-19) the entry is **closed as a known environment boundary** rather
than left open indefinitely. Standing rules that outlive the entry:

- **No claim of "security audit passed" is permitted.** The defensible
  statement remains: static phases cover every file (1022 selected = 1022
  parsed, 0 read/parse failures), findings are 3 low with 0 high/medium,
  the dependency surface is clean (38 packages, 0 advisories), and the
  semantic phases (entry points / authorization surfaces / dataflow) are
  **not covered on this host**.
- Git-hook `scanner_enobufs` pass-throughs and any "no scanner conclusion"
  note remain expected and must be cited, not treated as a clean pass.
- The API summary counts and the sealed artifacts keep the KD-14
  discrepancy rule: sealed `findings.json`/`report.md`/`seal.json` are
  authoritative.

**Reopen condition.** Any of: (i) Mimosa ships a scanner that reaches the
full-coverage status on this host (e.g. its runtime detection is fixed, or
Semgrep/PyCG become reachable to the scan process) — a sealed
`runStatus=complete` supersedes this entry with its finding summary; or
(ii) a scan on any host surfaces a high/medium finding — that becomes its
own open KD.

**Reopen condition.** A sealed run that reports `runStatus=complete` (or an
equivalent full-coverage status) supersedes this entry with its finding
summary; any new high/medium finding from such a run becomes its own open KD.

## KD-15 (fixed 2026-09-19) · Bundled teaching-material pack is rejected on import (startup banner)

**Symptom.** A freshly installed `localFirstDebug` app shows the recoverable
startup banner 「本地知识包尚未准备好 / 错题和复习可以继续使用，自动分类会暂缓。」
(诊断编号 `startup:knowledge:<n>`) on every launch. Reproduced 2026-09-18 on a
clean rebuild (`gradlew clean :app:assembleLocalFirstDebug`) with `pm clear`ed
app data — logcat:
`com.tingyun.smartmistakebook.core.database.DatabaseContractViolationException:
Teaching-material review cannot predate its source import` from
`RoomKnowledgeBaseStore.importKnowledgeTeachingMaterials`. The main tree pack
imports fine (device DB: `knowledge_node`=2624, `knowledge_source`=13); only
the teaching-material import fails, and it is one transaction, so one violation
blocks the whole material set.

**Root cause (measured).** The six bundled sidecars
`moe-2025-teaching-support-v2-0{1..6}.json` carry **80 materials whose
`reviewedAtEpochMillis` is a few milliseconds before their source's
`importedAtEpochMillis`** (per file: 20 / 24 / 1 / 19 / 11 / 5), e.g.
`ext-che-96397bfcc6-023` reviewed 1789732985366 < imported 1789732985467. The
contract check (`KnowledgeTeachingMaterialContract.validate`, current source
line 111) requires `reviewedAt >= importedAt`. The inversion is a
stamping-order artifact of the pack builder (review stamped ~0.1 s before the
source import), not a content judgment. Verified by direct scan of the
sidecars; the runtime failure reproduces deterministically.

**Note on the stack line.** The runtime frame names
`KnowledgeTeachingMaterialContract.kt:301` while the check sits at line 112 in
the 228-line file; the number is Kotlin's inline-frame attribution for the
inlined `requireValid` body. A full clean rebuild reproduces it — do not read
it as a stale-build signal.

**Fix direction.** In the pack pipeline, stamp the source import at-or-before
the material review, or clamp `reviewedAt = max(reviewedAt, importedAt)` when
emitting the sidecars; rebuild and let a clean install confirm the banner is
gone. Owner: the knowledge-pack toolchain (parallel session's in-flight pack).

**Reopen condition.** n/a — close when a clean install launches with no banner
and the material import completes without the contract exception.

**Verified fixed (2026-09-19, pack toolchain).** 三处根因一并修复并过真机：①材料 `reviewedAt < importedAt` 494 条全量对齐（并统一同 sourceId 跨卷副本的 `importedAt` 取最早值——聚合 `distinctBy` 首现口径）；②`KnowledgeTeachingMaterialContract` 的**总量上限**才是更早的阻断（材料 2048 / 绑定 16384 / 总字符 4M 实为 11302 条材料），按用户 2026-09-19 决定全部取消，只留结构不变量；③2020 旧包 2 条材料引用主包源、与主包 id/指纹撞唯一性 → 侧车补独立源。新增 `BundledTeachingMaterialsContractTest`（内置包直接过 DB 导入契约）。真机复核：clean install 无横幅，`knowledge_teaching_material` = **10412 行**（此前 0），`knowledge_node` 2605、`knowledge_source` 54。

## KD-16 (fixed 2026-09-19) · Batch organize shows the fallback notice instead of the configure-model one

**Symptom.** `localFirstDebug`, no model configured: 错题本 → 批量导入试卷照片 →
「开始分题」 shows 「这次还没有全部分好，页面都已保留，可以稍后继续。」
(`BatchImportRoute.kt:159`). The precise 「请先在“我的”里配置模型服务，再整理相邻页面。」
(line 157) never appears. Reproduced on the 2026-09-18 clean rebuild.

**Root cause.** The precise message is reachable only through
`BatchOrganizationUnavailableException`, thrown only when
`!modelEgressAllowed()` (`RoomBatchImportRepository.kt:215-216`). The app
injects `modelEgressAllowed = { modelConfigurationStore != null }`
(`SmartMistakeBookApplication.kt:269`), which is true in `localFirst`
regardless of whether a model is configured — so the call passes the egress
gate and dies at `require(provider.canOrganizeBatchPages())`
(`RoomBatchImportRepository.kt:219`); that `IllegalArgumentException` lands in
the generic `catch (_: Exception)`.

**Fix (2026-09-19).** `organizeBatch` 里 `require(provider.canOrganizeBatchPages())`
换成 `if (!...) throw BatchOrganizationUnavailableException()`——能力不足与"未授权出网"
归为同一领域结局，UI 现有的 `catch (BatchOrganizationUnavailableException)` 分支因此显示
精确文案；egress 语义不变。新增 instrumented 用例
`organizeBatchWithAConfiguredButImageIncapableModelReportsCapabilityUnavailable`
钉住 egress-允许 + 能力-不足这一组合，并断言不派发任何模型轮次。

**Reopen condition.** 若 `organizeBatch` 的能力判定再抛 `IllegalArgumentException`
而非 `BatchOrganizationUnavailableException`，则重新落入通用 catch。

## KD-17 (closed 2026-10-18：事实核清——入口自 L4 `7f109251` 起已存在，本批补两条回归钉住；提交 `8335613f`) · A single-capture draft has no resume entry after leaving the flow

**Symptom.** 错题本 → 拍照或上传 → 拍照并整理 → (no model: the flow parks at
「整理题目」 with the capability gate) → back. The draft persists
(`problem_draft` row, status `EDITING`, source asset kept) but nothing lists or
reopens it: re-entering 拍照或上传 starts fresh; 错题本 shows its plain empty
state (it counts only committed `problem` rows via `intakeBacklogCount`) and
复习 shows 暂无需复习题.

**Evidence.** `Routes.CaptureResume` (`capture/resume/{draftId}`) has exactly
two callers — 批量导入 (`SmartMistakeBookRoot.kt:654`) and 分题复核 (:678). The
batch side works on device (「点此继续」 reopens the page's draft with its
original image, verified 2026-09-18); the single-capture side has no entry.
Device DB after the smoke: 1 batch job `COMPLETED`, 3 pages `READY`, 4 drafts
`EDITING` (3 batch + 1 single-capture).

**Fix direction (needs a product decision).** Either surface single-capture
drafts (reuse the resume route from a pending list or the 错题本 empty state),
or keep the user in-flow / explicitly discard when the flow cannot be reopened.
With a model configured the window is narrow (the flow auto-continues), so
priority is low.

**关闭记录（2026-10-18，提交 `8335613f`）.** 现状核清：录入入口的待处理列表自 `7f109251`（2026-10-03，L4）
起已包含 TUTOR 来源 EDITING 草稿并可走 resume 路由恢复（`PendingCaptureDao.kt:141` 的 `WHERE d.status = 'EDITING'`
不限 origin → `RoomPendingCaptureStore.kt:27` → `CaptureScreen.kt:913-925` → `SmartMistakeBookRoot.kt:656-657`
`Routes.captureResume`）——本条登记的「无入口」在 L4 后已不成立。本批不加重叠入口，只补两条回归钉住该事实：
`CaptureWorkflowInstrumentedTest.kt:92-127`（真库：TUTOR 来源 EDITING 草稿在列且可 resume）、
`CapturePendingCapturesInstrumentedTest.kt:95-134`（入口态列出并点开）。见批次记录 §2 T6。

**Reopen condition.** n/a — close by the chosen decision plus a device check
that the draft is reachable or resolved.

## KD-18 (fixed 2026-09-19) · `tools/teaching_sources/epub_audit.py` 在本机 Python 3.13 下恒崩，4 条测试恒红

**Symptom.** `PYTHONPATH=tools python -m pytest tools/tests -q` 里
`test_teaching_sources.py` 的 4 条用例全部 ERROR，异常都是
`TypeError: XMLParser() got an unexpected keyword argument 'resolve_entities'`
（`tools/teaching_sources/epub_audit.py:101`）。

**Evidence.** 2026-09-19 本机 Python 版本 `Python 3.13.14`；单独复现：
`python -c "import xml.etree.ElementTree as ET; ET.XMLParser(resolve_entities=False)"`
同样报错。`epub_audit.py` 最后一次改动在 `2f0b98a9`（与本轮改动无关），
`git diff HEAD -- tools/teaching_sources/epub_audit.py` 为空 —— 缺陷是既有环境兼容问题，
不是新引入的回归。该文件原本是想关掉实体解析做 XXE 加固。

**全量结果（2026-09-19 本轮实测）**：`4 failed, 212 passed`，红的 4 条全部来自本缺陷。

**Fix direction.** ~~三行 try/except 改法~~ **实际改法（更简）**：直接去掉 `resolve_entities=False`，
即 `ElementTree.XMLParser()`。stdlib `xml.etree.ElementTree` 本就从不解析外部 DTD 实体（XXE
安全不依赖这个参数），`resolve_entities` 只是内部实体替换的开关，且已在 3.13 被移除——
所以裸解析器在 3.8–3.13 全都正确。**注意有两处调用点**（`_rootfile_path` 与 `_metadata`，
登记最初只记了 101 行），只改一处仍会在 124 行崩。

**Resolution (2026-09-19).** 两处调用点均改为 `ElementTree.XMLParser()` 并加注释。
`PYTHONPATH=tools python -m pytest tools/tests/test_teaching_sources.py -q` → 4 条转绿；
全量 `python -m pytest tools/tests -q` → 全绿（当时 277 passed，0 failed）。

**Reopen condition.** 若未来引入 `defusedxml` 或重新依赖外部实体解析，需复核这两处。

## KD-19 (fixed 2026-09-19) · 同一来源在多卷的时间戳不一致，导致内置包导入被拒（KD-15 同类复发）

**Symptom.** 视觉转写批次入库后，`:core:data:testDebugUnitTest` 的
`BundledTeachingMaterialsContractTest` 报
`DatabaseContractViolationException: Teaching-material review cannot predate its source import`；
424 条用例里 7 条红（另有 6 条是 KD-18）。

**Root cause.** 同一 `sourceId` 会随不同批次落进不同卷，每卷各带一份 `source` 条目、
各自盖"写入时刻 − 60s"。Kotlin 侧 `distinctBy` 只认先出现的那份，于是后写的那份
（时间更晚）成为生效值，先前批次里 `reviewedAt` 更早的材料就被判成"材料早于来源导入"。

**Evidence.** 修前实测：命中 `desktop-src-3692ca65ed:math`（91 条）与
`desktop-src-5b2dfb6dc5:physics`（193 条）共 284 条材料违反契约；
同 sourceId 的两份副本时间戳分别来自第 1、2 次写入。修后复算违反数 0，
`:core:data` 424 条全绿（结果时间 03:04:17，晚于包改动 02:59:34）。

**Fix.**
1. `tools/kb_coverage/materialize.py` 新增 `_existing_source_entries()`：写入前先扫全卷，
   同一 `sourceId` 已有条目就**复用最早那份**（`importedAt` 取最早），不再各写各的；
2. 数据修复：把每个来源的 `importedAt` 压到
   `min(各副本原值, 该来源全部材料的 reviewedAt 最小值 − 60s)`，改写 76 条来源。

**Reopen condition.** n/a —— 由 `BundledTeachingMaterialsContractTest` 持续守门；
任何再引入该缺陷的写入都会让该用例直接变红。

## KD-20 (fixed 2026-09-19) · 门禁的控制字符集比 App 契约窄，C1/双向控制符只能靠 Kotlin 用例兜住

**Symptom.** 五三 B版批次（3,609 条材料）入库后，
`BundledTeachingMaterialsContractTest` 报
`DatabaseContractViolationException: material.contentMarkdown contains unsupported control characters`，
427 条用例 1 条红。

**Root cause.** 修复前 `gate.py` 的 `control_chars` 指标只查少量控制符（2026-09-19 实测仍报 80，与回修前一致），
而 App 契约（`KnowledgeTeachingMaterialContract.text`）拒的是
**全部 ISO 控制符（Cc，排除 \n\r\t）＋双向控制符**（U+061C、U+200E/F、U+202A–202E、U+2066–2069）。
两条判据不同源，于是"Python 门全绿、App 直接崩"。

**Evidence.** 按契约语义重扫 15,862 条材料：命中 3 个字段 / 2 条材料，均为 C1 控制符
（0x80、0x81、0x88、0x89、0x9C、0x9D —— PDF 编码事故残留）：
`ext-che-e1f44db337-030.contentMarkdown`、`ext-che-1ff1c78002-001c.boundaryMarkdown`、
`ext-che-1ff1c78002-003.contentMarkdown`。清理后重扫 0 命中；`:core:data` 427 条全绿
（结果时间 09:34，晚于清理 09:33）。

**Fix.** 清理脚本 `build/clean_contract_chars.py`（按契约语义清 Cc + 双向控制符，写回卷）。
**补修已落地（2026-10-20/21，提交 `74876108`）**：`gate.py:145-153` 的 `_CONTROL_CHARS` 已改为按契约 `text()` 的 ISO 控制判据生成（C0 除 `\n\r\t` + DEL/C1），`control_chars` 逐字段扫描 7 个文本字段；新增 `tools/tests/test_kb_control_chars_contract.py` 钉住“必须命中 / 必须放行”两侧。双向控制符仍不在该门判据内，由 Kotlin 契约用例兜住；原“未做”项关闭。

**Reopen condition.** n/a —— 由 `BundledTeachingMaterialsContractTest` 持续守门。

## KD-21 (fixed 2026-09-19) · 2,265 处 `\1` 回指残迹随包分发，门没有任何判据看得见

**Symptom.** 数学材料标题里出现 `$f(x)=f(x+a)\Rightarrow\1=a$`。全包普查：473 条材料 / 2,265 处
`\1`，另有 6 处行尾孤立反斜杠、3 处 `\，`、9 处 `f\'(x)`。这些字段是**讲题时模型直接读到的文本**：
`\1` 进去，公式语义就断了；`title` 还进别名表参与检索。

**Root cause.** 残迹形态是「一个空白 + 一串 ASCII 词元」被整体替换成 2 字符 `\1`（原文本用 LaTeX
细空 `\` 时反斜杠残留，于是出现 `\1`）。`gate.latex_damage` 只查"真命令丢了反斜杠"
（`\cos\alpha` → `\coslpha`），**反方向（反斜杠后跟了不该跟的字符）没有任何判据**，
于是整类残迹静默出厂。

**Evidence.** 30 个子代理逐字段修复时取到的独立证据：485 条字段的修复结果与
`cffda989^`（损坏前提交）/ 该卷历史版本 / 入库前 `.agent_*.csv` 产物**逐字节相等**——
即残迹确实是"曾经正确、后来被替换"。53 条无干净前身（首次入库即坏）按材料内互证重建。
`material_judgments.csv` 侧 1,762 处同源（入库上游）一并镜像修好。

**Fix.** 门新增 `invalid_escape` 指标（allowlist 由全包"反斜杠+非字母"普查定：
`\`、`\ `、`\{`、`\}`、`\%`、`\,`、`\_`、`\|`、`\;`、`\:`、`\(` 都是真写法）；
修复工具 `fix_invalid_escapes`（三类机械 + 表驱动语义）；写回器 `apply_text_fixes` 的三层独立复核。

**口径订正（2026-09-24）.** 上面的 2,265 / 2,283 含一类**判据误报**：`\\`（双反斜杠行分隔）
后面的第二个反斜杠被当成新转义起点，于是 `\begin{cases}a,\\2S_k\end{cases}` 里的 `\2` 被算成残迹。
`find_invalid_escapes` 改为把 `\\` 整体消费后，在 `ec5656d6^` 上重测的真残迹是
**1,854 处**（`\1` 1,832 / 行尾反斜杠 10 / `\'` 9 / 反斜杠+全角逗号 3）。**修复结论不变**：
那 1,854 处是真残迹，485 条字段与损坏前提交逐字节相等的证据仍然成立。详见 §W-03。

**Reopen condition.** `invalid_escape` 指标非 0（门里的哨兵 + `test_gate_reports_real_defects_not_zero`）。

## KD-22 (fixed 2026-09-19) · `$$` 被 shell 展开成 PID、`$0` 变成 /usr/bin/bash

**Symptom.** 材料正文里出现 `310243n = \frac{a-xb}{2}310243`（同一 6 位数夹住一条公式）、
`只能读到 /usr/bin/bash.1\ \mathrm{g}$`、`必须控制在 .5\sim10.5$`（`$` 计数为奇数）。

**Root cause.** 文本在某一环被丢进 **shell 上下文**：`$$`（行间公式定界符）→ 进程号、
`$0` → 脚本名 `/usr/bin/bash`、`$1`/`$c`/`$p` 这类"$ + 名字"连名字一起被展开掉。
`gate` 既没有 PID 判据也没有 `$` 奇偶判据，所以没有任何一项会红。

**Evidence.** 9 个字段的 PID 残迹（6 位数两端各一）、13 处 `/usr/bin/bash`、20 个字段 `$` 不成对；
其中化学数值类残迹可用材料自身算术反证（`0.05×99/6=82.5%` ⇒ 取样量 `$6\ \mathrm{g}$`、
莫尔法 `pH 6.5~10.5`、`$p-\pi$ 共轭`）。

**Fix.** 门新增 `shell_expansion`（`$0` 残迹 + PID 双重判据）与 `dollar_unbalanced`（`$` 奇偶）两项指标；
判据的三条限定（6–7 位、窗口 ≤120 字、中间夹公式记号）由"真残迹全是 6 位数、两例误报全是 5 位换算系数
（10000/40000）"实测倒逼，并写进单测正反两侧钉住。

**未指认环节（如实记录）.** 当时的命令记录没留下，无法指认是哪个脚本/代理把文本丢进了 shell；
因此防线放在门的判据上，而不是指认单个工具。

**Reopen condition.** `shell_expansion` 或 `dollar_unbalanced` 非 0。

## KD-23 (fixed 2026-09-19) · `\ text{` 被 `\1` 残迹掩盖：修好一类才露出另一类

**Symptom.** 修完 `\1` 后门 `latex_damage` 从 0 变 5（`$9.8\ text{m/s}^2$`、`$a=c\ text{且}\ b=d$` 等）。

**Root cause.** 两处损坏叠在同一个位置：`\ text{` 的坏点特征是 `ext{` 尾部没有反斜杠，
而同一字段里的 `\1` 恰好把 `\ text` 吃成了 `\1`，判据于是看不见它。**修得掉看得见的，才露出被掩盖的。**

**Evidence.** 逐字段比对 HEAD 版与写回后：HEAD 版 `_latex_damaged` 为 False、写回后为 True，
且命中的 tail 全是 `ext`（`text`）；全包 `\ \text{` 140 处（合法写法）vs `\ text{` 7 处（坏）。

**Fix.** `textfix` 新增形态 4（`\ 空格 命令名{` → 补回反斜杠，命令名必须紧跟 `{`，
故合法的 `\ mol` 细空不受影响）；`fix_material_text --judgments` 把同一套修复镜像回判定表
（46 个文本列；非文本列指纹比对不变、仍受损行 0）。

**Reopen condition.** 门 `latex_damage` 非 0。

## KD-24 (open) · 去截断实验（v2）净伤害词面路由已回滚——公共 gram 通胀打掉窄路召回（19 例自由落体）+ 宽路 p95 663ms 超预算；别名截断缺陷（62% 节点）改由已开启的 dense 兜底议题结构性消灭，词面侧不再以去截断方式修（口径更正见末段）

**Symptom.** 2026-09-22 第四轮按 D12 施工去截断索引（v2：删
`MAX_SEARCH_FRAGMENTS=16` / `MAX_NODE_FEATURES=192`）并把 KNOWLEDGE_READ / MASTERY_READ
聚焦解析统一成 B512→A 路由后，档 2 真机两次复跑（数据确定性插入，两次出数一致）实测
两条路都是净伤害：

- **v2 索引 × 裸 B5**：金标主集 0.5222（47/90）尚可，但 **19 例回归的自由落体例在
  v2 窄路失败**——去截断放大公共 gram 通胀（大别名量节点的二值 TF count 被公共
  2/3-gram 抬高），把少公共 gram 的精确节点（"自由落体运动"，题面公共特征只有
  "自由" → count=1）挤出 64 宽网，窄路召回被打掉；
- **v2 索引 × B512→A（统一路由）**：19 例恢复 19/19（`bundled-knowledge-recall
  regression: hit=19/19, cross-subject=0`），但金标主集 **0.3444（31/90）**（比裸 B5
  更差）、**p95=663ms / 695ms**（两次复跑，超 150ms 预算 4.4×；同设备同形路由的
  2 万合成点控制测 p95=62ms——成本来自宽召回 + A 对 512 候选的 Kotlin 计数打分，
  非设备故障）。

**Evidence.** 档 2 复测#1/#2 仪表化日志（逐字存档于 `docs/kb-vector-topic-decision.md`
§3.1）：`route=B512->A-select->top5(生产统一路由) indexVersion=2 cases=90
samplesPerQuery=5`；p95=663ms/p50=457ms/max=40901/overBudget=444/450 与
p95=695ms/p50=467ms/max=35253/overBudget=446/450；v2 索引规模
`totalFeatureRows=873465 maxFeaturesPerNode=1672`（v1 同包：`totalFeatureRows=583271
maxFeaturesPerNode=192`）。JVM 镜像与真 SQL 两次复跑漂移 0，排除镜像口径问题。

**Resolution (2026-09-22, 形状回滚 / FIX2).** 生产回零回归形状，测量设施全保留：

1. 索引回 v1 语义：`KnowledgeSearchFeatureExtractor` 恢复节点侧截断
   （`MAX_SEARCH_FRAGMENTS=16` / `MAX_NODE_FEATURES=192`），`INDEX_VERSION` 2→1；
   WP4 的版本锚点表 `knowledge_search_index_state`（v51 迁移，保留基建）按
   "锚点缺失或不等 ⇒ 整科换血"自动触发——v2 实验库（锚点=2）首次召回即回建 v1，
   Room 51 迁移本身不滚；
2. 路由回滚：KNOWLEDGE_READ（裸 B 路 limit=5）与 MASTERY_READ 聚焦解析（裸 B 路
   limit=24）回到 FIX 前生产形状；归类/拍照路径的 B512→A 不动（既有、消费者不同）；
3. 19 例回归 limit 512→64 回原形状（断言一字不改，v1 上 19/19——历史即 v1-B64→A
   19/19）；金标判分目标回 v1 裸 B5（真 SQL + JVM 镜像同步，零漂移纪律与预注册
   阈值一字不动）；
4. 提取器 v1 截断行为有独立钉（`KnowledgeSearchFeatureExtractorTest`：178 片段
   节点选 16 入预算、特征 ≤192）。

**为什么词面侧不再以去截断方式修。** 去截断的意图是消灭"62% 节点别名不全入索引"
（审计 §1.4），但实测它放大的是同一个二值 TF 排序的病（D-01 无 IDF/无长度归一化）：
公共 gram 通胀挤掉窄路召回，宽路又买不起 p95。词面栈内两条形状（v2-裸B5、
v2-B512→A）都已实测，均不及或不及 v1 生产形状（三组数并列见
`docs/kb-vector-topic-decision.md` §3.2）。

**缺陷的归宿（结构性消灭，不是掩盖）。** 别名截断缺陷改由 D12 首测已触发开启的
**同层 dense 兜底议题**解决：28,932 条向量（3,572 知识点 + 25,360 别名，审计
§2.4-1 口径）×512 维全扫 1.4–2.8ms（内存带宽估算），向量侧**无截断损失**——
"别名只有一部分进了索引"这一失败类在 dense 兜底里不存在，词面索引的截断因此
不再承载召回正确性，只承载 p95 与体积预算。议题形态预案（bge-small-zh int8 +
LiteRT ≈30MB / RRF / 无 ANN）见决策文档 §4，立项需另一次用户裁定。

**Reopen condition.** 任何一条：(i) 再次尝试词面侧去截断/宽召回统一路由而未经金标
三组数对照（`GoldenRetrievalInstrumentedTest` + `GoldenRetrievalJvmTest` 零漂移）；
(ii) `INDEX_VERSION` 被改动但锚点换血语义（不等即重建）被弱化；(iii) 19 例回归
（v1 索引 × B64→A）跌破 19/19——那是窄路召回的真实回归，不是设备噪声（KD-2 的
预算系数不覆盖召回命中断言）。

**口径更正（2026-09-23，WP-A；只更正读法，不改归宿、不改重开条件）。** 本条目（及
标题、审计 §1.4、`KnowledgeSearchFeatureExtractor.kt` 文档注释）引用的「**62%** 节点
别名被索引截断」是**含 boundary 片段**的读法。对成品包
`core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` 的 3,572 个
原子节点、按 `KnowledgeSearchFeatureExtractor.fromNode` 的实际切片规则（名称 + 排序后
别名 + boundary，片段按 `[^\p{L}\p{N}]+` 切分，判据 = 片段数 > 16）复算：

- **纯名称 + 别名超 16 片段：661 / 3,572 = 18.51%**——这才是"别名被截断"的节点面；
- **含 boundary 片段超 16 片段：2,208 / 3,572 = 61.81%**（审计记 2,209，差 1 条为口径
  边界，不影响结论）。

更正后的读法：**"62%" 不等于"62% 的节点的别名进不了索引"**——别名被截断的节点面是
18.51%，被截掉的主要是 boundary 摘录（61.81%）。**归宿不变**（该缺陷仍由已开启的同层
dense 兜底议题结构性消灭，词面侧不再以去截断方式修——v2 实测净伤害即本条登记的事实）；
**重开条件不变**（原文三条一字不动）。复算口径与命令的完整记录见
`docs/kb-vector-topic-decision.md` §7 附录 A.1。

## KD-25 (fixed 2026-09-24) · 20k 基准的两条墙钟腿把单个离群样本当 p95——档 2 三轮误红，run2 双离群"排首样本"也救不了

**Symptom.** `KnowledgeContextRetrievalInstrumentedTest#largeSubjectRecallRemainsBoundedOnRoom`
（20,000 合成点 + 19,999 关系；API-34 emulator / WHPX）在档 2 三次全红，红的都是同一
测试方法内的 **recall 墙钟门**（150ms）——mastery 门（250ms）因断言顺序在后，三次里
一次都没被执行到（失败点 `:198`，修复前行号，WP-E1 报告口径）。错误消息逐字
（WP-E1 存档 `/tmp/kcr_run{1,2,3}.log`）：

```
run1 Room recall p95 was 492ms;   samples=[492, 44, 46, 57, 57, 53, 50, 45, 50, 49]
run2 Room recall p95 was 21718ms; samples=[74, 21718, 180, 55, 51, 45, 52, 50, 45, 41]
run3 Room recall p95 was 1267ms;  samples=[1267, 54, 45, 46, 50, 52, 47, 53, 39, 43]
```

稳态样本 39–57ms（约预算 1/3）；三次的确定性守卫（EXPLAIN 索引 / 无 `SCAN feature`、
选中语义、19 例回归 19/19）全部通过——失败点在守卫之后的墙钟断言，指向**测量口径**
而非查询退化。

**Root cause（测量口径，不是查询）.**
1. `percentile95()` 口径 `((size * 95 + 99) / 100 - 1)` 在 n=10 时**恒等于 max**
   （算术：`(10*95+99)/100 - 1 = 9` → `sorted[9]`），而两条腿共用该函数 →
   任一离群样本即门红；
2. recall 腿只有 1 轮无注释预热（mastery 腿 3 轮）、无首样本剔除；
3. run2 是**双离群**，只排首样本救不了它（算术）：去首样本（74）后余 9 条
   `[21718, 180, 55, …]`，max 仍是 21718 > 150ms；即便再排最大，次大 180 > 150ms。
   run1 / run3 的离群恰好落在首位（492、1267）只是两次巧合——"剔除首样本"不是修复。

**Fix（2026-09-24, WP3；只改测量方法，预算一字不动）.**
1. `percentile95()` 口径改 `((size - 1) * 95) / 100`（最近秩），与 review 腿
   `KnowledgeResearchReviewInstrumentedTest.kt:140` 逐字一致；n=24 时取 `sorted[21]`
   （第 22 小），即容忍 2 个离群；
2. n 10 → 24（`PERFORMANCE_SAMPLE_COUNT`，两条腿同用）；
3. recall 腿预热 1 轮 → `RECALL_WARMUP_COUNT = 3` 轮（不计入统计、带注释，对齐 mastery 腿）；
4. 确定性守卫（EXPLAIN 查询计划、选中语义）与全部断言语义保留；预算仍 150/250ms ×
   CI 系数（`:522-523`），KD-2 的 `ciSlowRunner` 4 倍系数未动。

**Evidence（API-34 emulator，`ciSlowRunner` 未传 ⇒ 本地严格预算 ×1）.**
- **红→绿演示**：临时把 recall 预算改成 1ms →

  ```
  Room recall p95 was 78ms; samples=[56, 70, 59, 52, 85, 46, 68, 78, 55, 64, 57, 60, 54, 47,
                                     67, 72, 41, 58, 60, 58, 59, 78, 55, 79]
  ```

  `BUILD FAILED in 21m 38s`，XML `tests=2 failures=1 errors=0` ⇒ **门仍能红**；还原后
  文件 sha256 与改动后一致（`b4ca2450…`，见下）。
- **连跑两次全绿**（文件字面未再改动，sha256
  `b4ca2450deb8951882a1a1ed90ff1f4722316b0b548cbef4f927c156447cf080`）：

| 运行 | Gradle | XML | recall 样本（n=24） | recall p95 | mastery 样本（n=24） | mastery p95 |
|---|---|---|---|---|---|---|
| A | `BUILD SUCCESSFUL in 22m 37s` | `tests=2 failures=0 errors=0` | 89,67,65,67,56,58,80,67,60,78,66,46,71,55,52,63,53,67,67,54,59,51,63,55 | 78ms | 162,189,189,142,143,166,166,132,166,148,144,136,176,142,134,130,177,117,128,259,136,135,132,123 | 189ms |
| B | `BUILD SUCCESSFUL in 23m 28s` | `tests=2 failures=0 errors=0` | 55,66,53,67,94,82,53,78,74,62,70,53,61,50,49,55,61,68,80,92,83,65,90,64 | 90ms | 180,209,170,206,173,175,167,137,142,182,154,158,139,120,172,166,147,157,177,141,177,148,151,159 | 182ms |

  两次的 19 例回归均 `bundled-knowledge-recall regression: hit=19/19, cross-subject=0`；
  两次打印的 p95（78/189/90/182）与按新口径独立复算逐条一致（5/5，含红演示那轮）。
  逐字日志：AGP 结果目录
  `core/data/build/outputs/androidTest-results/connected/debug/`（含
  `TEST-test_device(AVD) - 14-_core_data-.xml` 与每方法 logcat），本轮另存于
  `/tmp/wp3/{red-run,green1,green2}/`（仓库外）。
- **口径算术复核**（脚本对三轮历史样本复算）：旧口径 n=10 下 p95 = 492 / 21718 / 1267
  （全红）；新口径 **n=10** 下 run2 仍红（180）——"n=10 + 换口径"不足以覆盖双离群，
  **n=24 才够**（n=24 取第 22 小 ⇒ 容忍 2 个）。

**残余风险（如实登记）.**
- 离群根因（设备侧调度 / GC / 内存压力）**未归因**：本修复只保证"≤2 个离群不再误红"，
  不保证门能把真实退化与噪声分开；真实退化的确定性判据仍是 `:122-135` 的查询计划守卫
  与 `:195-201` 的选中语义断言（后者在两次绿跑中均于统计样本上通过）。
- 本机实测 mastery 腿明显逼近预算：A 轮 p95=189ms / 250ms = 76%，且该轮有一个 259ms
  样本落在 p95 之上（旧口径 n=24 取第 23 小，同样是 189ms）；CI 的 4 倍系数（1000ms）
  覆盖得了，但 mastery 腿余量比 recall 腿小（78/150 = 52%）。若 runner 变慢或系数被改，
  mastery 腿会先红。
- 本轮**未单独演示 mastery 门的"可红性"**（红演示只打到 recall 门，受断言顺序限制）；
  mastery 门的证据是两次绿跑中该断言被实际执行且通过。

**Reopen condition.** 任何一条：(i) 该门再次出现"稳态正常 + 离群样本 ≤2"以外的红
（离群数 > 2，或稳态整体上移）——前者按 KD-2 改用 runner-relative 界，后者按真实退化查；
(ii) 若把墙钟门移入 macrobenchmark 模块，查询计划断言必须留在原地（KD-2 同款口径）；
(iii) `ciSlowRunner` 系数被改动而未经 runner 实测支撑。

## KD-26 (fixed 2026-09-25) · 20 条 `boundary` 断在公式中途，而门的 4 个文本判据只扫材料字段

**Symptom.** `core/data/src/main/resources/knowledge/moe-2025-four-subjects-v1.json` 里 20 个知识点的
`boundary` 带机械可判缺陷：20 处 `$` 不成对、4 处行尾悬空反斜杠（4 条同时两类）。长度 124–156 字符
（字节 221–391，不是等宽上限），**全部断在公式中途**，例：

- MATH t2 kp86「函数图象的识别」→ `…变化趋势要看 $x\to+\infty$ 与 $x\`
- MATH t16 kp35「同构法」→ `…（或把 $b$ 写成 $\`
- MATH t19 kp11「奔驰定理」→ `…对应哪个顶点的向量”（$S_{\`
- MATH t28 kp33「双曲线焦点三角形」→ `…（椭圆是 $b^2\tan\frac{\`

**Root cause.** 生成侧（boundary 是改写产物，不是原文）写这段时就断在公式中途；**不是**近期重建造成：
HEAD~1 / HEAD~2 / HEAD~5 / HEAD~10 / HEAD~20 / f08fce92^ / f08fce92 七个版本上这 20 条逐字同长同缺陷，
git 里没有全文可恢复。门的 `field_text_defects` 只被用在**材料**字段上，boundary / name 无判据——
所以"22/22 全绿"是真的，4 处悬空反斜杠却能在全绿下随包分发（这些字段讲题时模型直接读到）。

**Impact.** 断掉的公式让学生看到半截 LaTeX；不成对的 `$` 会让自研渲染器把后续正文当数学显示。

**Fix (2026-09-25).** ① 按该节点**已绑定材料**逐条补全被切断的公式（只补尾、不改写前文，逐字可溯源）：
工单 `tools/kb_build/make_boundary_fix_slices.py` → 20 份证据 `tools/kb_build/tables/boundary_fix_evidence/`，
裁定写入权威表 `tools/kb_build/tables/boundary_map.csv`（20 行），执行器 `tools/kb_build/apply_boundary_fixes.py`
逐条校验后写 staging（写盘前复算 `boundary_text_defect` 应为 0、点数不变，任一不过整批不写）；
② 给门加 boundary 判据 `boundary_text_defect`（`gate.field_text_defects`，与材料侧同源函数）。
两件事同一提交落地。
**落地复算（2026-09-25，本代理实测）**：staging 与成品两份包 `boundary_text_defect` 均 = 0（全 23 项指标全 0）；
`boundary_map.csv` 20 行与 staging / 成品**逐字一致**（0 处不符）；`python tools/ci/run_kb_checks.py` 五节全 OK。
**登记**：`docs/kb-problem-register-2026-09-15.md` §W-02。

**Reopen condition.** 该判据加入门后若再次出现非 0 的 boundary 缺陷——说明补全只治了存量、
生成侧仍在写截断文本，须回头改生成器（而不是再补一批）。

## KD-27 (open) · CI 的 `check` job 长期红 ⇒ status.md 的 commit-back 被 `if: success()` 永久冻结（实测冻结自 2026-09-21）

**Symptom.** `gh run view 36337107924`（main 推送，含 Stage-6 换件提交）实测：`check` job 的
`Knowledge build toolchain tests` 步骤 **红**（`FAILED (failures=7, errors=27, skipped=1)`），
`instrumented` job 也红（`feature:library` 的 `MistakeExportInstrumentedTest` 断言失败 +
测试进程计数通胀 43→49 的已知资源形态）。因为 `.github/workflows/android-check.yml` 的
commit-back 步骤条件是 `if: success()`，**任何一处红都会让 `docs/status.md` 不再被写回**——
仓库里那份停在 `74626e8` / 2026-09-21，KB 段与 gate 行数为 **0**（实测 `grep -c` = 0）。
后果是"门全绿"这句话在 09-21 之后**没有任何随仓库的凭据**。

**2026-10-24 复读（最新 CI）.** `gh run list` 最新 main push 为 run `39818426751`（headSha `9d2096ad`，2026-10-22T16:57:24Z），结论 **failure**：`check` job 的 `Knowledge build toolchain tests` 与 `Knowledge pack gate and table-pack consistency` 两步均 **success**（Python 工具链已绿）；唯一失败步骤是 `Unit tests`，红在 `:core:export:compileDebugKotlin FAILED`——`MistakePaperDraftStore.kt` 的 `kotlinx.serialization` 引用解析不到，HEAD 的 `core/export/build.gradle.kts` 缺该插件与依赖（本地工作树已加、未提交）；同一步骤另报 `:core:database:kspDebugKotlin FAILED`——HEAD 的 `port/CleanRedrawTaskPort.kt` 缺 `RevisionImageSource` 定义（本地工作树已补、未提交）。`instrumented` job 同样在 `:core:export:compileDebugKotlin` 编译失败。commit-back 条件仍是 `if: success()`（`.github/workflows/android-check.yml:145`），`docs/status.md` 仍不会被写回。

**历史归因（2026-09-21 run `36337107924`，三条，全部有 CI 日志原文；当前红因见上）**：
1. **`chapter_map.csv` 5 行坏 slug**（已提交那一版）：`ValueError: chapter_map.csv: 5 行的 slug
   在知识包中不存在（可能写成了 name）：[('CHEMISTRY','水电离出的c(H+)（或c(OH-)）的计算'),
   ('CHEMISTRY','装置气密性检查方法'), ('PHYSICS','游标卡尺的读数'), ('PHYSICS','库仑力作用下的平衡'),
   ('MATH','三角形垂心的向量特征')]` —— 单这一条就吃掉 **17 个测试**（content_audit / build_generator /
   align_chapter_locators / delete_points / verdicts 都是经 `tables.py:98 validate_chapter_map_slugs` 连坐）。
   **修法已在工作树里但未提交**（另一会话的 `chapter_map.csv` 本地改动），非本条目负责人可改。
2. **artifact 依赖的测试在干净检出上结构性不可过**（约 12 条）：`test_kb_transcription_ledger.*` /
   `test_kb_check_transcripts.RealArtifactTest` 要求 `scan_render_pages.py` 产出的 manifest
   （`SystemExit: 缺 manifest`）；`test_kb_materialize.PlanTest` 要求扫描件块清单里存在
   `('2026年新高考资料/…/1.1集合的概念（讲义）（学生版）.docx','9a51501f22-001')`。这些产物在
   `build/`（按设计不入库）⇒ CI 永远看不到。
3. **`pypdf` 未安装**：`tools/curriculum_coverage/extractor.py` 导入期即要求它，
   `tests.test_curriculum_coverage` 整个模块加载失败（`ModuleNotFoundError`）。

**Fix（本代理已做，2026-09-28）**：③ 在 CI 加 `pip install pypdf`；另修两处**平台/口径**缺陷：
`tools/tests/test_kb_build.py` 的"仓库外路径"探针从 `C:/Windows/...`（Linux 上退化成相对路径
→ FileNotFoundError 而不是预期的 ValueError）改成 `tempfile.gettempdir()`；门数分母从写死的
`/22` 改为从 `gate.evaluate()` 现算（实际 23，真跑会打印 `23/22`），并给 status.md 加"权威表
sha256"一行（同一张 `chapter_map.csv` 本地绿 / CI 红——读数必须带它对应哪版表）。
**未做（2026-09-28 时点，不属本会话可改范围）**：①②；`feature:library` 的断言失败。**2026-10-24 复核**：Python 工具链已绿（①②不再是红因），`feature:library` 断言失败不再是当前红因；当前红因是 HEAD 缺件导致的 `core:export` / `core:database` 编译失败（见上）。
**本轮交付**：本地重生成 `docs/status.md`（23/23 逐门读数 + 权威表 sha + 472/472 工具测试），
使"门全绿"重新有随仓库的凭据——但它**不是 CI 产物**，触发方式为 `local-manual`。

**Reopen condition.** 上述两处 HEAD 缺件（`core:export` 的 `kotlinx.serialization`、`core:database` 的 `RevisionImageSource`）修好后，CI `check` job 应转绿；若届时 commit-back 仍不写回，
说明还有第三条红未归因——按 `gh run view <id> --json jobs` 的步骤级结论重新归因，不要猜。

## KD-28 (open, 2026-09-28 判为**接受边界**) · `kb_build` 的 9 科白名单仍抄两份（`KnowledgeBaseImportContract` 与 `SubjectKind` 各一份）

**Symptom.** 2026-09-21 架构审计 P7/R5-3 点名：`core/database/.../KnowledgeBaseImportContract.kt:17-23`
是一份 9 科 `private val subjects = setOf(...)`，与 `SubjectKind` 是第二份清单。

**Disposition（2026-09-28，本代理裁定）**：**接受为边界，不收敛**。理由：① 实际范围是四科
（用户 2026-09 已裁"九科补完永久取消"），多出来的 5 科在当前数据里恒为空集，**没有任何可观测失败**；
② 该文件在 `core/database`——另一会话正在做 in-flight 重构，改它会在共享树上制造冲突，
收益（消除一份暂未漂移的重复）不抵代价。
**Reopen condition.** 出现第五科、或白名单需要按科放行（例如整科下架/按科开关）时，收敛到
`SubjectKind` 必须做；届时两份清单的**漂移**就是判据（先证漂移，再改）。

## KD-29 (open, 2026-09-28 登记) · AI 生成合成内容标识未落：材料与展示层没有"AI 整理"标识

**Symptom.** 随包材料的来源是"AI 从教辅/讲义/扫描件整理"，而材料字段、来源登记与 UI 展示层
**都没有**任何 AI 标识。外部依据：《人工智能生成合成内容标识办法》由网信办等四部门发布，
**2025-09-01 起施行**（`cac.gov.cn/2025-03/14/c_1743654685896173.htm` 等公开来源）。

**Disposition（2026-09-28，用户裁定）**：**暂不处理，登记为已知边界**——不声称已合规。
本条的存在就是为了让"未标识"是一个**被记录的取舍**，而不是一个没人知道的缺口。

**Reopen condition.** 对外分发/上架前必须重开（届时按"内容侧标注 + UI 一句说明"的最小形态落）；
或监管口径/应用分发渠道的要求发生变化时。

## KD-30 (fixed 2026-10-14；2026-10-03 登记) · native tool_calls 路由不解析 `extendedResult`——MASTERY_READ 的扩展预算只在 json_object 路由生效

**Symptom.** 工具 schema 对模型**两条路由都**广告 `extendedResult`（`OpenAiModelProtocol.kt`
的声明构建，`TutorToolDescriptions` 也写着"或对单次查询申请扩展预算（extendedResult=true）"），
但只有 json_object 信封路由解析它（`OpenAiModelResponseParsers.kt:477`）。Route A
（`OpenAiModelProtocol.kt:602 toTutorToolRequestsOutput`，native `assistant.tool_calls`）
构造 `TutorToolCall` 时不带该字段（`:628-646` 的实参清单里没有）→ 默认 `false` → 模型经
native 路由申请扩展预算被**静默丢弃**（`RoomTutorToolRunner.kt:565` 的 budget 分支不触发）。
哪里真的会发生：provider 走原生 tool_calls 时（`Route A` 分支 `:575`）。

**Disposition（2026-10-03 登记）**：既有缺陷（3B B5 的 diff 未触碰这两处；B5 只在该函数补了
咨询工具的 scope/kind/payload 三字段）。**不在 3B 范围，登记不修**。最小修法（供将来）：
Route A 的 `TutorToolCall` 构造补 `extendedResult = arguments.optionalBoolean("extendedResult")
?: false`（与 Route B 同口径），并补一条"两路由对 extendedResult 同义"的对拍用例。

**Fix（2026-10-14）.** 原生构造已补同口径 optionalBoolean 解析。新增 nativeMasteryReadPreservesTheExtendedResultRequest 先红后绿；本轮五模块 JVM 2,125/0，Room 工具/任务 26/0。细节与证据见 `docs/research/2026-10-14-agent-foundation-small-fixes.md`。

**补记（2026-10-14 收口 C4）.** 缺失的「两路由对拍」用例已补：
`TutorToolRequestDualParseTest.masteryReadExtendedResultParsesIdenticallyOnBothRoutes`
（`core/data/.../TutorToolRequestDualParseTest.kt:317`；Route B 信封 vs Route A 原生 `tool_calls`
解析出**同一个** `TutorToolCall`，带键/缺键各一组——本节记录者回读）。生产行
`extendedResult = arguments.optionalBoolean("extendedResult") ?: false`（`OpenAiModelProtocol.kt:704`）
为工作区未提交改动——HEAD 无此行（记录者以 `git show HEAD:… | grep` 核对）。对拍类产物 13/0/0
（C4 材料引用的构建产物 13:44:38Z，非其执行）；独立复核材料另实跑 `:core:data:testDebugUnitTest`
（7 类 116/0，未逐类点名对拍类；记录者也未找到留存产物独立复核该次运行）。

**Reopen condition.** 两路由再次出现扩展预算参数丢失或语义不一致。

## KD-31 (fixed 2026-10-11) · opencode zen `/zen/go/v1` 要求 `x-opencode-session`，应用不发该头 ⇒ 该服务商下模型全部 400

**Symptom.** 走查中把模型配到 opencode zen（`https://opencode.ai/zen/go/v1`，模型
`deepseek-v4.1-flash`）后，「测试图片与讲解能力」报「测试未通过：当前模型暂不兼容」。
logcat 两探针均 `status=400 bodyBytes=195`；curl 复现服务端错误体为
`{"type":"error","error":{"type":"MissingSessionID", ...}}` —— 该端点要求每个请求携带
`x-opencode-session`（官方文档：客户端应为每个会话发送稳定 session id，供路由与提示缓存）。
应用侧协议头只有 Authorization/Accept（`OpenAiChatCompletionsProtocol.headers`），无任何
自定义头机制；查询参数形式无效（实测仍 400）。

**Fix (2026-10-11, `64c79456`).** 传输层按解析后的 host 追加服务商兼容头：
`providerCompatibilityHeaders(host)`（`OpenAiModelTransport.kt`），仅当 host 精确等于
`opencode.ai`（大小写不敏感、仿冒域不命中）时发 `x-opencode-session`，值取进程级稳定 UUID
（服务端仅要求"每会话稳定"）。新增 `ProviderCompatibilityHeadersTest` 5 例。修复后同一配置
能力测试转「测试通过：图片读取和讲解都可用」。

**未覆盖.** 图片通道（`OpenAiImageGenerationChannel`）自建请求、不经该传输层——若将来把生图
模型也配到该服务商，需要同一头。

**Reopen condition.** 该服务商改用其它鉴权/路由要求，或第二个服务商出现同类必填头时，
把兼容头机制从"host 判定"升格为可配置项。

## KD-32 (open, 2026-10-11 登记；2026-10-13 两处峰值已修、2026-10-14 B2 补 crop/persist 采样，剩余见下) · 192MB 堆上限下的内存天花板：知识包安装与批量导入 OOM

**Symptom.** test_device（API 34，4 vCPU/1536MB，应用堆上限 192MB，manifest 无 `largeHeap`）
上走查观测到三处同源失败：
1. **批量导入崩溃**：导入 3 张 3072×4096 照片时进程 OOM 死亡
   （`FATAL EXCEPTION: DefaultDispatcher-worker-10 → java.lang.OutOfMemoryError`，
   `dumpsys meminfo` 显示 Dalvik Heap alloc 134–147MB / 上限 192MB，GC 日志
   "Clamp target GC heap from 215MB to 192MB" 后反复 `Throwing OutOfMemoryError`）；
   Activity 被强关，`batch_import_job` 留在 PROCESSING（页全部 READY，后由 WorkManager
   重试自愈为 COMPLETED，`run_attempt_count=2`）。
2. **知识包安装 OOM**：启动横幅「本地知识包尚未准备好 / 自动分类会暂缓」，
   logcat `SmartMistakeBook: Bundled knowledge install failed →
   java.lang.OutOfMemoryError: OutOfMemoryError thrown while trying to throw OutOfMemoryError`。
   重试可装成（`content_install_state` 落账、`knowledge_search_feature` 62 万行），
   但下次启动复检可再次 OOM（诊断编号 142862027 → 180194307 两次不同哈希）。
3. **SQLite 连接池饿死**：`SQLiteConnectionPool ... unable to grant a connection ... for 60s`。

**Fix（2026-10-13，提交 `42096510`）· 三处支配者各削一刀：**

1. **失败路径也释放包对象图**（诊断条目①／⑤）：`releaseResidentPacks()` 从成功路径挪进
   `finally`——启动协程 `SmartMistakeBookApplication.kt:483-491` 与横幅重试 `:658` 两处。
   安装失败不再把 ~100MB 包对象图留驻进程（诊断实测失败态 idle Dalvik 111,350KB vs
   快路径健康 8,068KB；随后一次只用 11.7MB Java 堆的批量导入被这 100MB 挤到 OOM）。
   `BundledKnowledgeBaseInstaller.kt:80-91` 与 `BundledKnowledgePackResources.kt:100-103`
   的 KDoc 同步为"成功与失败都释放、对象图是纯派生数据下次重解析"。
2. **知识库材料差分改分页**（诊断条目②③）：整包 `readByStableCodePrefix` 整读
   （`kb_peak3.hprof`：50,869 实体 + ≈30.5 万随行字符串/≈49MB char[]）改为按 stableCode
   分段、每段 500 行（`MATERIAL_READ_PAGE_SIZE`，`RoomKnowledgeContentReconciler.kt:403-415`、
   `:600`）；`groupBy{materialId}` 换成排序一次 + 二分定位（`:395-401`、`:580`），去掉
   59,543 个 ArrayList 与 ~5.9 万映射条目。DAO 换半开区间分页查询
   `readMaterialsInStableCodeRange(from, through)`（字节精确、走 stable_code 唯一索引，
   `KnowledgeTeachingMaterialDao.kt:73-97`）；旧 LIKE 方法全仓无调用方、随实现移除。
   逐行判据（`existing != material.toEntity()` 全等）与退役集合口径逐字不变。
3. **导入解码采样**（诊断条目④）：`import()` 解码前按 `MAX_DECODE_EDGE_PX = 2048` 与纯函数
   `boundedDecodeSampleSize` 设 `inSampleSize`（只降不放大；接受门 MAX_DIMENSION/MAX_PIXELS
   仍在源图 bounds 上执行）——3072×4096 单张 50.3MB → 1536×2048／12.6MB
   （`AndroidCanonicalAssetVault.kt:102-113`、`:492`、`:509`）。

**验收（设备实测，2026-10-12 18:29–19:28 UTC，装机 APK 构建于本机 10-13 02:24）.**
知识包**全量**安装（戳不一致 32682dbc9b1a7d05 vs 旧 627d8d43503e41c1）跑完：
`packs fully parsed in 29059 ms (2 packs)`、`install finished in 1133477 ms
(full path: parse + reconcile)`（≈1,133s）；无 OOM、无失败横幅、进程未死
（pid 27462 全程连续），戳已回写（`content_install_state` 18:48:40.718），
`knowledge_search_feature`=623,774 ／ `knowledge_node`=4,187 ／
`knowledge_teaching_material`=59,016。批量导入 3 张 3072×4096：3 页全部 READY、
failure_code 空、无崩溃，`canonical_source_asset` 3 条均 1536×2048（采样生效）；
`build/mem-sample.txt` 复算 Native Heap Alloc 峰 157,759 KB（Size 峰 192,772 KB）、
Dalvik Heap Alloc 峰 25,670 KB（走查报 19,116 KB 系读错列，独立核对按同一文件复算更正；
方向结论不变）；`FATAL EXCEPTION`=0、`OutOfMemoryError`=0（logcat + crash buffer +
无墓碑/ANR；覆盖 18:29–19:28）。对照崩溃窗口 Dalvik 134–147MB、native 峰 195,449 KB
（诊断口径，现记录在 `AndroidCanonicalAssetVault.kt:105-106`；docs/ 下无此数）。
新增 JVM 测试：`RoomKnowledgeContentReconcilerMaterialScanTest`（4 例）、
`CanonicalDecodeSamplingTest`（5 例）；独立通跑 `:core:database:testDebugUnitTest` 181/0、
`:core:data:cleanTestDebugUnitTest :core:data:testDebugUnitTest` 731/0，以及
`:core:database:connectedDebugAndroidTest --class=KnowledgeContentReconciliationInstrumentedTest`
7/7（含 1,200 材料跨 3 页分页全链路）、`:core:data:connectedDebugAndroidTest` 17/17。
**未达项（保持 open 的理由）：**
- ① `crop()`（`AndroidCanonicalAssetVault.kt:141-144`）与 `persistCleanImageBytes`（`:219-220`）
  仍整幅 ARGB_8888 解码（允许至 MAX_DIMENSION=8192/16MP，单帧≈64MB）——批量拆页走 crop，
  升级前已导入的大图不会被重编码，拆页时会再次整幅解码；采样只覆盖 `import()`。
- ② 采样路径无设备级用例（仪器化夹具是 40×80 小图），"采到 2048 且记录/指纹自洽"只有纯函数
  测试；>2048 源图的**分辨率语义变化**（本机 OCR/裁剪输入分辨率下降）对识别精度的影响未验证。
- ③ 知识包安装窗口的堆采样（走查报 Dalvik 86,377→120,968 KB）**无留存采样件，未能复核**。
- ④ 失败后重试需重新全量解析（~16.7s parse 口径、~100MB 对象图）——有意取舍；停在
  RecoverableFailure 时每次 onResume/横幅重试都会重付一次。
- ⑤ 行为差异（知情，非缺陷）：规范资产从"源图全分辨率 ≤16MP"变为"长边 ≤2048"（产品可见）；
  跨版本重放已落库 requestId 会因字节不同被 `validateImportReplay` 拒（>2048 长边的照片）；
  材料差分迭代顺序变为 stableCode 升序（计数与判定不变）；包内材料读取由 LIKE 前缀改为
  字节精确区间（不再折叠 ASCII 大小写/不再把 `_`/`%` 当通配符；对现有小写稳定码等价）。
- ⑥ 仪器化用例覆盖 3 页插入与幂等，但没有"跨段退役"断言。

**2026-10-14 补记（B2，工作树未提交）· 未覆盖项状态：**

- ① **已补**：`crop()`（`AndroidCanonicalAssetVault.kt:145` 先 `decodeBounds` 取源尺寸、再设
  `inSampleSize`）与 `persistCleanImageBytes()`（`:226`，用它已有的 bounds）与 `import()`
  （`:108`）现在共用同一采样口径 `canonicalDecodeSampleSize`（`:520-521`）；`MAX_DECODE_EDGE_PX`
  移到文件级 internal const（`:509`，值仍 2048）。
- ② **仍缺**：JVM 单测钉的是三条调用点传给 `inSampleSize` 的采样率（纯函数），**并未真正执行
  Android 解码**；crop/persist 的解码行为仍只在仪表化覆盖，且既有仪表化夹具 ≤2048px（采样=1，
  行为不变）；>2048 源图的分辨率语义变化对 OCR/裁剪精度的影响仍未验证。设备级用例当前**跑不
  起来**——整个 core:data androidTest 源集被 KD-34 的钉子编译错误挡死（本轮记录者实跑
  `./gradlew.bat --offline :core:data:compileDebugAndroidTestKotlin` → BUILD FAILED）。
- ③④⑤⑥ 本轮未动（维持原状）。
- 单测更新：`CanonicalDecodeSamplingTest` 新增「crop and persistCleanImageBytes decode at the
  same edge budget as import」（3072×4096→2、4096×3072→2、2048×1536→1、72×96→1、8192×8192→4；
  独立复核实跑 6/0/0）。

**2026-10-14 收口补记（C1/C2 + 采样设备收口，工作树未提交）.**

- **C1（陈旧断言修正，记录者回读）**：`BatchImportRepositoryInstrumentedTest.kt:317-331` 把
  `assertEquals(2_200, longEdge)` 改为按 `PDF_RENDER_LONG_EDGE_PIXELS / canonicalDecodeSampleSize(...)`
  （= 2200/2 = 1100）断言，并补 `<= MAX_DECODE_EDGE_PX`；PDF 渲染长边常量提为文件级 `internal const`
  （`PdfBatchImportPageRenderer.kt:27`）。**代价（独立复核 low）**：期望值改由渲染侧同一常量推出 ⇒
  常量漂移（如改成 4000：4000/2 = 2000 仍 ≤ 2048）不再让这条断言变红，`CanonicalDecodeSamplingTest`
  也未覆盖 2200 档——修正本身正确（1100 与实现一致），但一侧的保护丢了。
- **② 设备级用例已补、编译阻塞已解**：`CaptureWorkflowRecoveryInstrumentedTest.kt:248-294`
  （2400×3200 照片 → 规范记录 1200×1600、解码头长边 = 采样值、`resolve()` 完整性判据通过）与
  `:296-385`（>2048 源图 splitDraft：区域按**采样位图**映射（0.4×1200≈480px，误映射到源图会得到
  ~960px）、产物长边 ≤2048、`splitRequestFingerprint` 逐字复算、两条负向——未退到 JVM 纯函数级）；
  `BatchImportRepositoryInstrumentedTest.kt:474-531` 的 A3① 钉子已按先前建议改写（organizeBatch 后
  重读域快照，边界判定落 `pages[].boundaryAfterStatus`）。KD-34 的编译错误已消除（独立复核材料实跑
  `:core:data` connected，该类 15/0；记录者无留存产物可复核该次运行）。**"分辨率语义（OCR/裁剪精度）
  未验证"一句原样保留**——补的是用例与链路，精度影响仍未度量。
- **采样设备收口（AUD-10，设备实测）**：4096×3072（8.11MB / 6.32MB）经整卷导入 → 2048×1536
  （sample=2）；3072×4096（竖幅）→ 1536×2048（走普通录入——整卷/PDF 明确拒单张）；sha256 /
  byteSize / 尺寸全部"拉回文件 → 宿主复算"逐字节一致；开始分题 6 个 crop 长边均 ≤2048 且等于区域
  映射到 2048×1536 后的像素包围盒；混合批次峰值 Native Heap Alloc 137,710KB / Pss 123,749KB、
  无 OOM、进程未重启（证据 `.artifacts/aud10-device-2026-10-14/`；独立核对按原始 100 个采样文件
  复算同值）。度量限制：内存采样间隔 ~2s，更短瞬时峰可能漏采。

**Reopen condition.** 再次出现 OOM/知识包横幅时按本条继续；或触及裁剪/重绘路径的整幅解码
（①）与采样路径的设备用例（②）时——补齐①②后本条可结。

## KD-33 (fixed 2026-10-13，提交 `e3de72f8`；OpenAI Chat Completions 协议族；2026-10-14 三协议族补齐见补记) · 推理型模型在 CAPTURE_ASSESS 上高概率 TIMEOUT/INVALID_RESPONSE

**Symptom.** `deepseek-v4.1-flash`（opencode zen）在「分题 / 单页整理」上：`model_task`
记录 1 成功 / 3 永久失败（2×INVALID_RESPONSE「这次没有准备好题面」）/ 2 可重试失败
（TIMEOUT「模型响应超时」）。该模型是**推理型**（响应含 `reasoning_content`，实测一次
评估思考 4,522–5,759 token）。curl 复现应用口径请求（2048px 图 + 评估提示词 +
json_object、无 max_tokens）：成功时 41.6s / 51.0s、JSON 合规（键集恰为
decision/issues/suggestedActions/questionRegions，枚举合法）。但 `max_tokens=2000` 时
思考吃满预算 → `content` 为空（可复现）——与 INVALID_RESPONSE 的形态一致；应用任务预算
`MODEL_TASK_TIMEOUT_MILLIS = 120_000`，观测到的应用侧流式任务存活 >205s 后 TIMEOUT。

**Fix (2026-10-13，提交 `e3de72f8`).** 四件事同落：

1. **防呆上限 120s → 600s**：`MODEL_TASK_CEILING_MILLIS = 600_000L`
   （`RoomModelTaskRepository.kt:1077`，`withTimeout` 调用点 `:350`）。KDoc 写明它是
   防呆安全上限、不是输出/时长预算；数值与传输层 `CALL_TIMEOUT_SECONDS = 600`
   （`OpenAiModelTransport.kt:456`）同口径。
2. **300s 空闲看门狗（只改注释、值不动）**：`READ_TIMEOUT_SECONDS = 300`
   （`OpenAiModelTransport.kt:454`）＝非流式收到首字节前的静默上限／流式相邻 chunk 间隔
   上限（收到即重置）；整条调用的绝对上限是 `CALL_TIMEOUT_SECONDS`。
3. **`finish_reason` 截断归为可重试**：新增 `ModelFailureCode.INCOMPLETE_OUTPUT`
   （`ModelTasks.kt:106`）、`IncompleteModelOutputException`
   （`OpenAiModelResponseParsing.kt:144` 附近，与 `InvalidModelResponseException` 平级、
   非子类，避免被原 catch 吞掉）、共享常量
   `TRUNCATING_FINISH_REASONS = {length, content_filter}`（`:167`）。非流式
   `parseResponse`（`OpenAiModelProtocol.kt:626`，先于 content/tool_calls 提取）与流式重建
   `reconstructedChatCompletion`（`OpenAiSse.kt:135`）同一判定；网关两条出口映射为
   retryable（`OpenAiCompatibleModelGateway.kt:279`、`:421`，常量 `:850`），
   `ModelFailureRecovery.kt:29` 加入重发可恢复集合；文案「模型这次没写完」
   （`AppFailure.kt:258`）／「模型这次没写完就停了，再发一次通常会好。」
   （`TutorLobbyRoute.kt:428`）。空 content 且**无** finish_reason 声明仍是
   `INVALID_RESPONSE`（既有语义不变，有测试钉住）。
4. **`reasoning_text` 拼写**：`OpenAiSse.deltaReasoning` 认 `reasoning` /
   `reasoning_content` / `reasoning_text` 三种拼写（`OpenAiSse.kt:66-68`）。

**验收（本轮实测）.** 设备侧（test_device，装机后）：5 条 CAPTURE_ASSESS 中 3 条
136.9s／191.2s／544.6s 均 SUCCEEDED，全部超过旧 120s 预算；全库唯一
`failure_code='TIMEOUT'` 记录是装机前 2026-10-11 15:11:49；单页整理拿到真实评估
（`NEED_MORE_IMAGE` / `MISSING_OPTIONS`）。测试侧（独立复核实跑）：
`:core:model:testDebugUnitTest` 416/0、`:core:data:cleanTestDebugUnitTest :core:data:testDebugUnitTest`
731/0、`:core:database:testDebugUnitTest` 181/0、
`:app:compileLocalFirstDebugKotlin --rerun-tasks --offline` BUILD SUCCESSFUL；新增用例
落在既有文件内（OpenAiSseTest×6、MockWebServer×2、SseTransport×2、Gateway×2、
AppFailureMapping×1、ModelFailureRecovery×1；独立复核计作「A2/A3/A4 新增 11 条」，
与函数名清单 14 的计数口径不一致，以函数名清单为准）。强门禁（脚本跑）：assemble +
`:core:data` 全量回归通过（既有红 `DenseRecallRerankerTest` 点名、不计）。

**未覆盖（同源，未独立开条目）:**
- ① 截断分流只落 Chat Completions 协议族：`AnthropicMessagesProtocol.kt:108`、
  `GeminiGenerateContentProtocol.kt:121`、`OpenAiResponsesProtocol.kt:96` 仍只判"文本非空"
  （不读 stop_reason=max_tokens／finishReason=MAX_TOKENS／status=incomplete）⇒ 选这三种
  协议时依旧拿到不可重试的 INVALID_RESPONSE。
- ② 判定不看正文：`finish_reason ∈ {length, content_filter}` 即抛，完整可解析的答复也会被
  丢弃；策略性 `content_filter` 重发多半复现。
- ③ 重试出口受派遣预算限制：预算用满后 INCOMPLETE_OUTPUT 被改写成 `PERMANENT_FAILURE`
  （`RoomModelTaskRepository.kt:718-720`）。
- ④ 流式 `tool_calls` 轮 `content=null` 形态（`OpenAiSse.kt:127-141`）仍判
  `InvalidModelResponseException`——既有行为，是否真实发生**未验证**。
- ⑤ `MODEL_TASK_CEILING_MILLIS` 的 KDoc 把"每轮 600s"写成"一条任务 600s"（`withTimeout`
  只包住一轮 `gateway.execute`，MAX_DISPATCHES=6 时一条任务最多 ≈6×600s）——注释与实现
  口径不一致，无功能破坏。
- ⑥ DB 读路径 `ModelTaskTransactionDao.kt:435` 仍是裸 `valueOf(failureCode)`，回滚旧版本
  读含新枚举的行会抛（仅降级方向）。

**2026-10-14 补记（B1–B4，工作树未提交）· 未覆盖项逐条状态：**

- ① **已补三条协议族的截断分流**：Anthropic `stop_reason=max_tokens` → `IncompleteModelOutputException`
  （`AnthropicMessagesProtocol.kt:122-123`，常量 `:254`）；Gemini 任一条 `finishReason=MAX_TOKENS`
  （`GeminiGenerateContentProtocol.kt:134-136`/`:149-152`，常量 `:282`）；Responses `status=incomplete`
  或非空 `incomplete_details`（`OpenAiResponsesProtocol.kt:110-112`/`:130-135`，常量 `:276`）。
  三条都在"文本为空"判定之前分流；空文本且无声明仍抛原 `InvalidModelResponseException`。
  **残留（独立复核 medium，未修）**：只读点名的声明——Gemini SAFETY/PROHIBITED_CONTENT、
  Anthropic `model_context_window_exceeded`、Responses `failed` 未分流；三族协议的**流式重建体
  不带声明**（`reconstructedBody`），而网关终态解析吃重建体、tutor 类任务恰走流式 ⇒ 这三族下
  tutor 答复被截断仍会当完整答复落库。
- ② 维持：判定不看正文（声明命中即抛，完整可解析的答复也丢），本轮未改。
- ③ 维持：重试出口仍受派遣预算限制。
- ④ 维持：流式 `tool_calls` 轮 `content=null` 形态仍判不可重试，是否真实发生未验证。
- ⑤ **已修**：KDoc 改为"**每一轮** `gateway.execute` 的防呆安全上限（600s）"，写明一条任务
  最多派遣 `MAX_DISPATCHES=6` 轮（5 轮工具 + 1 轮终答）⇒ 本地占用上限约 6×600s=3_600s
  （值不变；本轮记录者回读 `RoomModelTaskRepository.kt:1066-1083`，与实现逐条对上）。
- ⑥ **已修 failureCode 一处**：`ModelTaskTransactionDao.buildSnapshot` 改为
  `ModelFailureCode.entries.firstOrNull { it.name == failureCode } ?: ModelFailureCode.UNKNOWN`
  （:440-441）；"三列俱全才构造 failure、缺列仍抛"的契约不变。**残留**：同函数的
  `ModelTaskStatus.valueOf` / `ModelTaskStage.valueOf` 仍是裸 `valueOf`（本轮范围只点 failureCode，未动）。
- 新增钉子（独立复核实跑）：`AnthropicMessagesProtocolTest` 13/0、`GeminiGenerateContentProtocolTest`
  11/0、`OpenAiResponsesProtocolTest` 12/0、`ModelTaskIntegrityTest` 5/0（含未知 failure code
  降级 UNKNOWN 用例）。本节记录者**未重跑**这些 Gradle 目标。

**2026-10-14 收口补记（C3，工作树未提交）· ① 的流式半边已补：**

- 三条协议族的**流式重建体**现在携带截断声明（此前重建体不带声明，而网关终态解析吃重建体、
  tutor 类任务恰走流式 ⇒ 三族下 tutor 答复被截断仍会当完整答复落库）：Anthropic 把终帧
  `message_delta.delta.stop_reason` 原样带进重建体（`AnthropicMessagesProtocol.kt:146-196`）；
  Gemini 把终帧 `candidates[].finishReason` 带进（`GeminiGenerateContentProtocol.kt:160-207`）；
  Responses 把 `status` / `incomplete_details`（`response.incomplete` 事件的 `response` 对象内）带进
  （`OpenAiResponsesProtocol.kt:145-203`）。三族与 `OpenAiSse.kt:127-164` 的 Chat Completions 重建体
  同一口径 ⇒ 空文本 + 声明命中时按 `INCOMPLETE_OUTPUT`（可重试）归类而非 `INVALID_RESPONSE`。
  每族都有"正常收尾仍能解析"的反向用例（独立复核材料实跑：Anthropic 16/16、Gemini 13/13、
  Responses 14/14 绿；记录者无留存产物可独立复核该次运行）。
- **新增两条 low 残留（独立复核，读码级、未修）**：① Gemini 流式重建取"第一个非空 `finishReason`"，
  整包解析却取"任一 candidate 的 MAX_TOKENS"——一帧内 STOP 在前、MAX_TOKENS 在后时会漏判截断
  （欠检测）；② Responses 的 `incompleteDeclarationOf` 只要 `incomplete_details` 是非 null JSON 对象
  就判"没走完"——提供方在完整答复上固定回 `{"incomplete_details":{}}` 会被误判为截断（误伤完整答复，
  概率低；JSON null 安全）。
- 只读点名声明的残留不变（SAFETY/PROHIBITED_CONTENT、`model_context_window_exceeded`、`failed`
  仍不分流）；②判定不看正文、③重试受派遣预算限制、④流式 `tool_calls` 轮 `content=null` 维持原状。

**2026-10-14 设备补记（新包，未归因）.** 页级 organize 的 CAPTURE_ASSESS 在新包上仍有
**2/7 PERMANENT_FAILURE / INVALID_RESPONSE**（page2/page6，`failure_retryable=0`、
attempt_count=1）——"按新口径失败可重试"没有出现；本轮无 curl 复现、logcat 已轮转，**未归因**，
不构成本条重开（无法证明是截断形态）。另观测到第 8 个 draft-583b094e（复用 page5 资源）的
`CAPTURE_PARSE` 以 `PROVIDER_REJECTED_INPUT`（"题图检查未通过"）失败，一并记录、不归因。

**2026-10-15/16 补记（本批九轨，提交 `038d50c0`/`09f0c85c`）· 与本条相关的现状（四处变化、三处未动）：**

- **失败上报唯一口 + 新增落点（T2）**：网关 7 处失败点改为唯一上报口 `reportModelCallFailure`——`execution` catch 5 处
  （timeout / IOException / Incomplete / Invalid / IllegalArgument，`OpenAiCompatibleModelGateway.kt:271-291`）、终态解析 2 处，
  以及**此前完全没有落点**的上游非 2xx 分支（现在带上 `httpStatus` 与错误响应体，`:388-417` / `:424-448`）；
  日志行行为逐字不变（`error` 为 null 时不记，与改前一致）。另建默认关闭的诊断缝 `ModelCallFailureDiagnostics.sink`
  （`ModelCallFailureDiagnostics.kt:37-70`）与 debug 专用写入实现（开关广播 `TEST_CAPTURE_MODEL_FAILURES`），
  目的正是取本条第①类失败的原文——本轮设备段被配额阻断、**未取样**（见下）。
- **新增一路可重试映射（KD-38③，与本条的四类映射并列）**：`OUTPUT_BUDGET_EXCEEDED` ＝
  `ModelFailureCode.INCOMPLETE_OUTPUT` + `retryable=true`（`OpenAiCompatibleModelGateway.kt:891-895`），
  catch 位置在 `IllegalArgumentException → INVALID_RESPONSE` 之前（`:281-285` / `:441-452` 对照 `:289-291`）——
  **本条建立的四类映射语义不变**（形状错误仍不可重试）。
- **未覆盖项 ③ 现在也覆盖「超预算」这一类（本轮记录者读码复核）**：派遣预算用满后 retryable 仍被改写成
  `PERMANENT_FAILURE`（`RoomModelTaskRepository.kt:730-744`），与截断同源。
- **未覆盖项 ①（三协议族）本轮未动**：`AnthropicMessagesProtocol.kt` / `GeminiGenerateContentProtocol.kt` /
  `OpenAiResponsesProtocol.kt` 本批 `git status` 无改动（本轮记录者实测），① 的三条残留维持原状。
- **未覆盖项 ⑥ 的残留未动**：`ModelTaskTransactionDao.kt` 本批无改动（`ModelTaskStatus.valueOf` / `ModelTaskStage.valueOf`
  仍是裸 `valueOf`），本轮记录者实测确认。
- **未覆盖项 ④（流式 `tool_calls` 轮 `content=null`）**：本批无相关改动，是否真实发生仍未验证。
- **文案侧面（与 KD-38 剩余项同源）**：`AppFailure.kt:240` / `:258` 把 `INCOMPLETE_OUTPUT` 一律显示
  「模型这次没写完」，本批新增的超预算失败码因此复用同一句 UI 文案（持久化 `ModelTaskFailure.message` 才区分）。

**Reopen condition.** 上述未覆盖项被真实观测命中时重开——尤其第二个协议族出现
"截断却仍判 INVALID_RESPONSE"，或流式 tool_calls 轮被结构性判死时；换用非
Chat Completions 协议跑分题/整理前，按①补齐。

## KD-34 (fixed 2026-10-14，提交 `707ca3de`；A3① 钉子与未闭合项收口见文内) · 跨页关系判定复用单页分题契约 ⇒ 批量边界评估整轮 INVALID_RESPONSE

**Symptom.** 整卷「开始分题」的**边界评估**（页间关系）落
`PERMANENT_FAILURE / INVALID_RESPONSE / stage=VALIDATING_OUTPUT`。设备实测（2026-10-12
19:13:22，emulator-5554，`com.tingyun.smartmistakebook.localfirst`）：
`model_task subject_id=batch-boundary-d15aaac1bd58d0e7e171d0ce75fe0b30`，
`request_snapshot`＝`sourceAssetId=asset-6a75ebb8…` + `followingSourceAssets[2]{237e6638…,12f62890…}`；
这三张图（1536×2048，长边 2048）正是 job `batch-6ba9eefe`（3 页）的页。旧版 logcat 抛
`IllegalArgumentException: Only a split decision may contain two to twelve question regions`
（HEAD 版 `CaptureAssessmentTasks.kt:126`；该 message 在主树只此一处——`git grep` 仅命中 :126）。

**Root cause（curl 复现实测，2026-10-13）.**

- **契约错配**：边界请求把整窗图**一次性**送入（`pageRelationRequest`，
  `RoomBatchImportRepository.kt:557-597`；一次请求喂整窗、结果只消费
  `followingPageRelations` :289 与 `decision` :295；`MAX_BOUNDARIES_PER_MODEL_REQUEST=7`
  :600 ⇒ 一次最多可送 8 张图），却复用单页分题契约：`decision=SPLIT` 时
  `questionRegions.size ∈ 2..12`、非 SPLIT 必须为空（HEAD `CaptureAssessmentTasks.kt:120-126`），
  其后还排着唯一性/最小尺寸/两两重叠三条 require。**真实失败窗为 3 张图**（3 页→2 个
  boundary→同窗一次请求），不是本轮任务书写的"两页窗口/两张图"；另补跑 2 图同形态失败 ⇒ 结论不
  依赖窗口大小。
- **提示词邀请**：多图分支（`OpenAiModelTaskAdapters.kt:89-111`）只追加
  `followingPageRelations` 规则，SPLIT 的"每道题完整外接区域"指令原样保留 ⇒ 3 页整卷被
  数出 25 道题，模型就给了 25 个区域——是提示词邀请的结果，不是模型跑偏。
- **模型实际返回**（按应用口径 curl 复现：system prompt + user[text+image_url detail=high]、
  temperature 0.1、response_format json_object、无 max_tokens；三次均 HTTP 200）：3img#1
  `questionRegions=25`（131.9s，completion 12,457 / reasoning 11,417）；3img#2 `=9`
  （109.9s，13,249/12,937）；2img#1 `=19`（85.9s，10,213/9,666）。三次 decision 全 SPLIT、
  `finish_reason=stop`（非截断）、wire keys 恰为五键（`OpenAiModelResponseParsing.kt:127-131`）、
  JSON 合 schema——**只有数组长度越界，且同一 payload 一次 25 一次 9（不稳定）**。区域是逐页
  0..1 坐标、顶边按页重启（run1 三段 0.08→0.98 / 0.05→0.95 / 0.08→0.68），报文里没有任何
  页码可供归属。
- **判定复刻**（`check_contract.py` 按 :110-139 的 require 顺序在 Python 复刻）：25 → 计数条
  FAIL（另有 distinct=24、max pairwise overlap=1.000——后续唯一/重叠两条在排队）；19 → FAIL；
  9 → 四条全过 ⇒ 落在 12 以内就静默通过，表现为**间歇性** INVALID_RESPONSE、重发可能"自己好了"。
  （"先命中计数那条"依据书写顺序与 message 唯一推断，未在设备上重放该 payload 取证。）
- 职责上本就无法消费：questionRegions 是页内归一化坐标、无页码；跨页一维数组的配对去重/
  重叠判定没有物理意义，而边界流程只读 `followingPageRelations` + decision。
  （curl 复现用的 API Key 只出现在 `-H` 参数里，未写入任何文件。）

**Fix（2026-10-14，提交 `707ca3de`）· 修提示词 + 职责下移：**

1. **A1 双变体提示词**（`OpenAiModelTaskAdapters.kt:100-139`）：`followingSourceAssets` 为空
   = 单页变体逐字保留；非空 = 多页关系变体，decision 限 PASS/RECAPTURE/NEED_MORE_IMAGE、
   不得 SPLIT、questionRegions 必须为空、不得报 MULTIPLE_QUESTIONS（契约里它与 SPLIT 绑定，
   在这里报同样被拒）；`followingPageRelations` 规则与单页版逐字相同。
2. **A2 契约减负 + 消费侧定判**：模型层（`CaptureAssessmentTasks.kt:110-127`）删掉
   2..12 计数/非 SPLIT 必须空/唯一性/最小尺寸/两两重叠四条 require 及常量，只留形状约束
   `decision == SPLIT || questionRegions.isEmpty()`（:125-127）；规则下移 core:domain
   `List<NormalizedSourceRegion>.isUsableSplitRegionSet()`（`CaptureWorkflowRepository.kt:375-379`，
   阈值常量 :381-386），两个自动拆分入口过门：`captureAutoSplitRegionsUsable`
   （`CaptureModelTaskPolicy.kt:76-78`）由 `maybeSplit` 自动提交前调用——不通过即
   `CAPTURE_SPLIT_FAILED`、不切、整页草稿、可重试、手动框选入口保留
   （`CaptureModelTaskCommands.kt:63-70`）；`BatchSplitRecognizer.kt:86-87` 不通过返回
   `BatchSplitOutcome.NotASplit`（页已进库，只放弃这次增强）。
3. 断言迁移未删语义：`CaptureSplitRegionSetTest`（7 例）与 `CaptureModelTaskPolicyTest`
   两个门用例接管被删断言，ModelTasksTest 补 `nonSplitAssessmentCannotCarryQuestionRegions`
   钉住保留的形状约束。

**回归钉子（A3）.**

- 契约/解析层：`ModelTasksTest.pageRelationAssessmentToleratesPageWideQuestionRegions`（SPLIT+21
  regions 原样接收）、`OpenAiCaptureAssessmentAdapterTest` 的「page relation prompt forbids
  splitting and keeps the relation rules」「page relation response keeps page wide question
  regions parseable」（独立复核实跑 31/0、5/0 全绿）。
- 端到端：`BatchImportRepositoryInstrumentedTest.pageWideSplitRegionsDoNotKillThePageBoundaryEvaluation`
  （UNSURE → KEPT_SEPARATE、两页各留草稿、`splitReadyJobId` 为空）。
- **钉子现状（2026-10-14 收口：已修）**：`BatchImportRepositoryInstrumentedTest.kt:474-531` 已按先前
  提醒改写——organizeBatch 之后重读 `observeBatchImports()` 域快照，边界判定落在
  `pages[].boundaryAfterStatus != FAILED`，`splitReadyJobId` 取自 organizeBatch 后的新快照
  （本节记录者回读 :507-527；`remainingBoundaryCount` 现取自域快照 :395）。独立复核材料实跑
  `:core:data` connected 该类 15/0（记录者无留存产物可独立复核该次运行；编译阻塞在源码层面已不可见）。

**验收（设备实测 2026-10-13）.**

- 新包整卷「开始分题」的边界评估不再 INVALID_RESPONSE：
  `model-task-cd3084cc69973877c20716fa3f6f0fcf`（subject `batch-boundary-79303d32…`）
  SUCCEEDED / COMPLETE / 无 failure_code / attempt_count=1 / 54,881ms；`output_snapshot`
  decision=PASS、`followingPageRelations=[NEXT_QUESTION×6]`；`batch_import_page` page 0–5
  `boundary_after_status=NEXT_QUESTION`、page6=PENDING（与 6 条关系自洽）。详见
  `docs/research/2026-10-14-4c-stage1-record.md`。
- 口径边界：logcat `IllegalArgumentException` 计数=0 只覆盖 16:23:54 之后（256KB ring；
  边界任务 15:48 发生）——"全程为 0"不可证（独立核对）。
- 同窗口观测（另行记账，不属本条）：页级 organize 的 CAPTURE_ASSESS 仍有 2/7 不可重试
  INVALID_RESPONSE，本轮未归因（见 KD-33 补记）。

**未闭合项状态（2026-10-14 收口 C5，工作树未提交）.**

- **① 契约容错只覆盖 SPLIT+区域（medium）→ 已按"限定在单页语义"收敛**：`CaptureAssessmentTasks.kt:107-141`
  把那组耦合（MULTIPLE_QUESTIONS ⟺ SPLIT、非 SPLIT ⟹ 无 regions）限定在单页轮（判别式
  `followingPageRelations.isNotEmpty()` = 多页关系轮，其消费方只读 decision + relations），关系轮里
  MULTIPLE_QUESTIONS + RECAPTURE / 非 SPLIT 带整册 regions 不再整轮被拒（本节记录者回读；单页轮行为
  逐字不变，单页轮的 relations 必须为空由任务级完成校验兜底）。**残留（C5 材料，未动）**：
  (a) 关系轮里 MULTIPLE_QUESTIONS + `decision=PASS` 仍被更早的通用规则拒（:90-93「PASS 不得带
  BLOCKING issue」，请求侧约束拦不住不合规响应）；(b) MULTIPLE_QUESTIONS `severity=REVIEW` 仍被拒
  （:123-125 要求全 BLOCKING）；(c) `schemaVersion < 3` 旧分支未动（解析路径永远是 schema 3）。
  边角行为变化（知情）：单页请求若模型回非空 `followingPageRelations`，两条耦合不再在构造函数先抛，
  改由任务级完成校验以 `InvalidProviderProtocol` 拒（`RoomModelTaskRepository.kt:553-571` →
  PERMANENT_FAILURE / INVALID_RESPONSE，失败分类不变）。
- **② 已落库旧 INVALID_RESPONSE 边界窗口不被救活（medium）→ 维持**：边界轮 requestId 确定性
  （`stableId("batch-page", jobId:windowKey)`，`RoomBatchImportRepository.kt:582`），`createModelTask`
  对已存在 requestId 直接返回旧行（`RoomModelTaskRepository.kt:214-224`），且 INVALID_RESPONSE 不可重试
  （`OpenAiCompatibleModelGateway.kt:840-843`）⇒ PERMANENT_FAILURE 属终态（`ModelTasks.kt:39-40`）⇒
  每次重新整理重放同一条已存失败、永不重派发（读码推断，未实跑）。旧 job 若被要求复活，按 requestId
  口径另修。
- **③ 低级别残留（维持）**：`OpenAiModelResponseParsers.kt:126-127` 注释仍称模型层兜 questionRegions
  契约（A2 已作废）；`CaptureWorkflowRepository.kt:427` 仍有第三份 `splitDrafts.size in 2..12` 硬编码
  （本节记录者回读确认仍在）。

**2026-10-15/16 补记（本批九轨，提交 `038d50c0`/`09f0c85c`）· 三条未闭合项现状：**

- **③ 第一条残留仍在（行号漂移）**：`OpenAiModelResponseParsers.kt:127-128` 的注释仍写「a valid response must satisfy
  the model-layer contract enforced inside CaptureAssessment」（A2 已把该契约作废）——本批 T1 改动了同一文件
  （新增短标签截断与注释），但**这处注释未动**；行号由登记的 `:126-127` 漂到 `:127-128`（本轮记录者实测原文）。
- **③ 第二条残留仍在**：`core/domain/.../CaptureWorkflowRepository.kt:427` 的第三份
  `require(splitDrafts.size in 2..12)` 仍在（本轮记录者 grep 实测，行号与登记一致）。
- **① 的 (a)/(b)/(c) 三条未动**：`CaptureAssessmentTasks.kt` 本批 `git status` 无改动（本轮记录者实测）——
  关系轮 MULTIPLE_QUESTIONS + PASS 仍被通用规则拒、severity=REVIEW 仍被拒、`schemaVersion < 3` 旧分支原样。
- **② 维持**：旧 INVALID_RESPONSE 边界窗口不被救活（requestId 确定性 + 不可重试 + 终态），本批未动。
- **本批 T4（AUD-03）改的是同族另一文件**：`core/data/.../capture/RoomCaptureWorkflowRepository.kt` 的提交回执路径
  （`confirmAndCommit` / `saveTutorSession`），与多页关系判定无关；本条引用的 `RoomBatchImportRepository.kt`、
  `core/domain/CaptureWorkflowRepository.kt`、`CaptureAssessmentTasks.kt` 本批均未被触碰（行号引用不漂）。

**Reopen condition.** 多页关系轮再次落 INVALID_RESPONSE（尤其模型回 MULTIPLE_QUESTIONS/
非空 regions 的形态——关系轮的这两条现已容错，见「未闭合项状态」①）；A3① 钉子已可运行
（2026-10-14 收口），"端到端回归已覆盖"以它为准；旧 job 的 FAILED 边界窗口若被要求复活，
按「未闭合项状态」②另修（requestId 口径）。

## KD-35 (fixed 2026-10-14，提交 `0bdd651b`) · 讲题失败卡的「重试」把 App 打崩（ImmutablePayloadConflict）

**Symptom.** 下游走查（`docs/research/2026-10-14-4c-stage1-record.md`，设备实测）：
复习 → 照片题「去讲题判定」→ 首轮讲解计划生成失败（`model_task` TUTOR_PLAN →
PERMANENT_FAILURE / INVALID_RESPONSE，文案「这次没有准备好题面，请重新处理」）；
点失败卡上的唯一出口「重试」——**直接崩溃**：
`ImmutablePayloadConflictException: model_task tutor-plan:07c06a7e… already exists with a different payload`
（`core/database/.../ModelTaskTransactionDao.kt:233`），crash buffer 唯一一条 FATAL EXCEPTION，
进程 13813 消失。机制：同 `requestId` 的任务行已存在且 payload 指纹不同（重试携带了新输入/
新时间戳），写入路径抛异常且无人兜住，异常直达主线程。

**Fix（2026-10-14，工作树未提交；分 A/B 两层）.**

- **A 身份层（重试换新身份）**：`tutorPlanAttemptCount` 由「数一个 Int」改为按**任务列表 + 本轮前情**
  计数，判据提为纯函数 `ModelTaskSnapshot.matchesTutorPlanTurn`（`TutorSessionInteractionPolicy.kt:39-91`，
  记录者回读）；`TutorPlanCommands` 改调新签名，行为逐字不变。面板 `planTasks` 回调改读
  `latestPlanTasks`（`rememberUpdatedState(tutorTasks)`，`TutorSessionPanel.kt:267`）——拿住的是 State
  而不是构造那一刻的值；重试时该轮已落 1 行 ⇒ attempt=1 ⇒ 新 requestId（尾缀 `attempt`，
  `TutorModelTaskPolicy.kt:238`），不再复用旧标识。
- **B 兜底层（冲突可见、不崩）**：`executeTurn` 的收集整段包 try/catch（`TutorPlanCommands.kt:93-143`）：
  先单独重抛 `CancellationException`；`IllegalStateException` 只在 `failure is ImmutablePayloadConflict`
  时接住 → 落日志 + `setPlanStartError(tutorPlanReplayConflictError())`（`TutorPlanCommands.kt:34-36`，
  `TUTOR_OPERATION_REPLAY_MISMATCH` / 可重试 / 数据保留）；其余同族成员（如正直性缺陷）照旧抛、不静默。
  跨层识别：新增标记接口 `ImmutablePayloadConflict`（`core/model` 新文件），
  `ImmutablePayloadConflictException` 实现它（`StudyDatabasePort.kt:63-65`）——feature 层按依赖方向看不到
  `core:database`，且 release/internal/benchmark 都开 R8，按类名字符串判定会静默失效，所以按类型精确判定。
- **可见失败 + 出口**：`TutorConversationUiState.planStartError` + `onPlanStartError`
  （`TutorConversationViewModel.kt:536-538`）；`TutorSessionInputs` 在输入区上方如实报错（tag
  `tutor_plan_start_error`，与发送路径错误各占一槽、不共用 tag）；无任务帧的状态卡由「正在准备这道题」
  改为失败卡 + 同一个「重试」出口（`TutorSessionPanel.kt:474-483`）——派发被拒时什么都没在进行中。
- **生命周期**：`executeTurn` 进门先 `setPlanStartError(null)`（`TutorPlanCommands.kt:92`），与回应轮
  `setChatStartError(null)` 同一纪律；该字段不随草稿变化下线，也不做进程死亡持久化（与既有 `error` 同纪律）。

**验收（完整退出门，2026-10-14 设备复验 + 同轮测试）.**

- **设备（`test_device`/emulator-5554；新包 `install -r` 覆盖安装、未清数据）**：首轮计划失败卡出现后
  连续点「重试」4 次，**每次 `adb logcat -b crash -d` 均为空、进程 27040 全程未重启**、MainActivity 始终
  topResumedActivity；独立核对以耐久证据复核（crash buffer 无任何 app 条目、全缓冲 `FATAL EXCEPTION` /
  `ImmutablePayloadConflict` / `OutOfMemoryError` 计数 = 0、无 `am_proc_died`、`/proc/27040/stat` 起始时刻
  与覆盖安装的 `lastUpdateTime` 同刻）。
- **重试新身份（设备 DB）**：同指纹下 request_id 尾缀 `…:0(attempt 1) → :1(2) → :2(3) → :3(4)`，
  全 PERMANENT_FAILURE/INVALID_RESPONSE；第 5 次换新指纹 `7c1c8e7d…:0` 并 SUCCEEDED（复验材料；
  独立核对自读 DB 同值；证据文件 `.artifacts/kd35-kd36-device-2026-10-14/final-evidence.txt`）。
- **兜底分支与出口（测试）**：unit `TutorPlanReplayConflictTest` 4 例（含「冲突 → 可见可重试、不崩」与
  「非冲突 IllegalStateException 照旧抛、不伪装」）；instrumented `TutorPlanReplayRetryInstrumentedTest`
  2 例（替身按"同标识不同载荷即拒"记账 ⇒ 身份被冻住必红；残余冲突 → `tutor_plan_start_error` 可见、出口保留）；
  DB 钉子 `theSameTutorPlanRequestIdWithANewTimestampIsStrictlyRejected`。记录者实读留存产物：
  `TEST-…TutorPlanReplayConflictTest.xml`（4/0/0，17:13:58Z）、`:feature:tutor` connected（33/0/0，
  含上述 2 例）、`:core:database` connected（10/0/0，含 DB 钉子）。
- **批次强门禁（脚本跑）**：true（既有红 `DenseRecallRerankerTest` 点名、不计，与 KD-33 同口径）。
- **局限（如实）**：设备复验未走完「从待处理里挑一条存入错题本」（待处理列表为空），实际可复习题为库中
  已有存储题；该轮"crash buffer 为空"的 5 个时点由复验方观测，独立核对只能复核同向的耐久证据。

**残留（如实登记）.**

- **两条机制叠加未裁决**：并发改动把 `TutorSessionPanel` 三个命令对象的 `remember` 去掉（改为随重组重建），
  本修复保留 `rememberUpdatedState`；保留哪一条留给批次收口裁决。独立复核指出：现注释把
  `rememberUpdatedState` 描述成"读 backing state"并不精确——它读的是**最近一次重组时的值**，正确性依赖
  命令对象随重组重建 + `tutorTasks` 由 `collectAsState` 派生（low，下一次重构易静默退化）。
- 非冲突异常（`LearningLedgerIntegrityException` 等）在讲题计划路径上仍会抛到主线程（按裁定"只接该类型"，
  有意保留）；`planStartError` 不跨进程保留（进程死亡后该事实由账本失败行/失败卡表达）。
- **关联观察（未验证）**：真实服务商（opencode zen / `deepseek-v4.1-flash`）对本题的计划输出不稳定——
  连续 4 次 INVALID_RESPONSE、第 5 次才成功；复验材料称 logcat 显示其中一次根因是
  `IllegalArgumentException: Tutor move label exceeds budget`（`core/model/.../TutorTasks.kt:191`，
  `TutorSuggestedMove.MAX_LABEL_CHARS = 32`）。独立核对**无法复核**该条（全缓冲 grep `budget` /
  `Tutor move label` / `MAX_LABEL_CHARS` 均 0 命中；app 主缓冲只回溯到 16:04:39，重试窗口已滚出）——
  机制在代码中真实存在，但该观测既不能证伪也不能重现。

**Reopen condition.** 任何入口再次出现同 requestId 不同 payload 的崩溃；或该异常在别的
model_task 写入路径上复现。

## KD-36 (fixed 2026-10-14，提交 `0bdd651b`；新增「重新判定」出口的死路另立 KD-37) · 讲题判定提交后 UI 锁死且零持久化

**Symptom.** 同一次走查（KD-35 崩溃后改走 composer 重试）：讲题终于产出讲解与本地检查题，
**选择提交后 UI 锁死**（选项全 `enabled=false`）且**零持久化**——
`attempt_submission` / `review_log` / `review_session` 全无新行，`review_session` 停在
第 1/1 题 IN_PROGRESS、队列项仍 PLANNED；因此判定一步的 source kind 无行可查，
下游档案必然为空。机制未定位（提交链在 UI 锁死前后哪一步断掉未取证；需设备复现 + 读码）。

**Fix（2026-10-14，工作树未提交；承接本批早前的 Compose 回归——先证选择事件被冻结、修后进入写入通道，
本批收口再落 A/B/C 三处）.**

- **A 结算读对会话**：新增 `TutorSessionPort.readRecentTutorSessionAnchors(practiceUnitId, learnerId, limit)`
  （`TutorPort.kt:147-162`，带单条退化的默认实现）+ `TutorExposureDao.findRecentAnchorsForPracticeUnit`
  （`:118-140`，`ORDER BY anchored_at_epoch_millis DESC LIMIT :limit`）+ `RoomStudyDatabase` 覆写
  （`:1264`）；`TutorJudgedReviewSettler` 改为在最近 8 个锚定会话里取「最近一次形成判定的会话」
  （`:71-92` + `TUTOR_VERDICT_ANCHOR_SCAN_LIMIT = 8` `:288-290`），新鲜度门槛（不早于
  `session.lastActiveAtEpochMillis`）原样保留。**口径**：未改成「同一复习项复用同一 tutor_conversation」
  ——「进入即新开」是已登记产品裁定且被 instrumented 测试钉住；按「让结算读对会话」实现（选择行与
  结算锚点因此落在同一会话）。
- **B 结算可观测**：`tutorSettlementNoticeFor(failed, status, visited)` 纯函数
  （`SmartMistakeBookDestinations.kt:495-511`）；异常不再被 `runCatching{}.getOrNull()` 吞掉（`:226-244`）；
  新增「重新判定」再试入口（`settlementAttempt` 计数进 LaunchedEffect 键，`:183/199/207`）；未去过
  讲题页时不显示「没有判定」（`tutorVisitedForItem`，两个入口均置位 `:293/:317`）。UI 渲染：
  `captured_review_settlement_notice/_retry`（`CapturedReviewSessionScreen.kt:45/168`）与
  `review_pretest_settlement_notice`（`ReviewSessionScreen.kt:395`）。
- **C 选项锁逃生**：`TutorInteractionCommands.submitChoice` 判据在按下之后变化时不再静默 return，而是
  `setInteractionError(TUTOR_CHOICE_SAVE_ERROR)`（`:35-46`）；写入加有界等待
  `withTimeoutOrNull(TUTOR_CHOICE_SAVE_TIMEOUT_MILLIS = 15s)`（`:55-76`），超时按失败处置（解锁 + 错误可见）。
  既有语义不动（行到达前不重复提交；成功路径不报错）。

**验收（完整退出门，2026-10-14 设备复验 + 同轮测试）.**

- ① 设备完成一次讲题判定提交：`attempt_submission` 1 行、`review_log` 1 行（source_kind = LOCAL_CHECKED、
  source_id = attempt-4a28e738…、rating = 2、evidence_weight = 0.5、scheduling_eligible = 1、
  card_id = practice-69b9cf95…）、`review_session_advance_receipt` 1 行（from_version 0 → to_version 1，
  submission_id 与 attempt_id 一致）；`review_session` 由 IN_PROGRESS(0) → COMPLETED(1, state_version 1)；
  `learner_problem_memory_state` assisted_correct_count = 1 / MODEL_JUDGED_CORRECT（复验材料；独立核对自读同值；
  档案侧 `learner_knowledge_mastery_state` = 0 行——与「讲题判定通道不写归属」的既有设计一致）。
- ② UI 不锁死：点选项后页面推进到「判断正确…」反馈与下一步出口；失败路径有可重试出口（复验材料 +
  独立核对截图复核）。
- ③ 失败路径负向用例：unit `TutorChoiceSubmissionFailureTest` 4/0（失败可见 + 解锁、15s 有界等待解挂、
  「判据变化不再静默」、成功路径语义不变）；instrumented `CapturedTutorSessionInstrumentedTest`
  `aFailedChoiceWriteUnlocksTheOptionAndShowsTheVisibleError`（记录者实读留存产物：该例在 `:feature:tutor`
  connected 33/0/0 运行中在列）。设备复验方自身未跑测试（如实记录），③ 由同轮独立复核实跑覆盖。
- 批次强门禁（脚本跑）= true（既有红 `DenseRecallRerankerTest` 点名、不计；同 KD-35）。
- **局限（如实）**：设备复验可用的复习题是库中已有存储题（「从待处理存入错题本」未走完，待处理为空）。

**残留（如实登记）.**

- 新增的「重新判定」出口存在一类确定性死路（独立复核 medium，读码级、未设备复现）——另立 **KD-37**。
- `submitChoice` 的 `catch (_: Exception)` 会把"同标识不同载荷"冲突一并折叠成 `TUTOR_CHOICE_SAVE_ERROR`
  （与 KD-35 刻意区分该类别的口径不一致；low：DB 侧幂等/冲突双保险，账本不会重复写，UI 锁仍在
  `pendingChoiceId`；`CapturedTutorSessionInstrumentedTest` 该轮 29/29 绿）。

**Reopen condition.** 提交再次无落库或 UI 无出口；或该锁死形态在机读题（选择题）通道复现。

## KD-37 (fixed 2026-10-15 于快照身份/DAO 层，提交 `038d50c0`；设备级 UI 端到端仍未达 —— 见「未达 / 剩余」) · 「重新判定」出口存在确定性死路：快照身份稳定而时间戳每次新造

**Symptom（读码推断，来源：独立复核）.** KD-36 修复新增的「重新判定」出口可能永远不可能成功：
结算载荷每跑一次都带新的时钟——`occurredAtEpochMillis = System.currentTimeMillis()`
（`SmartMistakeBookDestinations.kt:239`），而派生的快照身份是稳定的——`snapshotId =
stableId("tutor-judged-snapshot", settlement.requestId)`、`capturedAtEpochMillis =
settlement.occurredAtEpochMillis`（`TutorJudgedReviewSettler.kt:127/140`，记录者回读），且入口
`requestId = "tutor-judged-settle:$sessionId:$ordinal"` 稳定（`SmartMistakeBookDestinations.kt:229`）。settle 里 `saveAssessmentEvidenceSnapshot(...)`
（`:152`，自身一个 `@Transaction`，`AttemptTransactionDao.kt:301`）与 `recordReviewAttempt(...)`（`:153`）
是两次独立事务：只要第一次**已提交**之后才失败（例如 `advanceSession` 的 CAS 不匹配、或 attempt 写入
失败），快照行已带旧时间戳落库；下一次（含学生点「重新判定」）在 `saveEvidenceSnapshot` 因
`existing != entity`（`capturedAtEpochMillis` 不同）抛
`ImmutablePayloadConflictException("assessment_evidence_snapshot")`（`AttemptTransactionDao.kt:642-652`），
而这发生在 `recordAttempt` 的幂等回放路径（`readAttemptReplay`）之前 ⇒ **每次重试都必然失败**。

**Impact.** 该复习项永久停在「这次判定没有结算成功…可以重新判定再试一次」的可见失败上，而唯一出口
永远不可能成功——与 KD-35 同一类（稳定标识 + 新时间戳）。不崩溃、不损坏数据，但承诺与现实不符。

**Fix direction.** 快照身份纳入时间戳（或把两段写入并入同一事务 / 先走幂等回放再写快照）；修后按
设备复验「失败 → 重新判定 → 成功」路径结。

**未验证.** 源码分析结论；真实 DAO 的冲突只在 instrumented 环境可见，本次未写设备级复现用例、
未跑任何复现（独立复核亦标注"未构建设备级复现"）。

**修法（2026-10-15，本批 T1；提交 `038d50c0`）.** 时间戳容忍比较：
`AttemptTransactionDao.saveEvidenceSnapshot`（`AttemptTransactionDao.kt:642-662`）不再用整实体相等判「同一张快照」，
改调纯函数 `sameEvidenceSnapshotIdentity(existing, existingAttributions, candidate, candidateAttributions)`
（新文件 `AssessmentEvidenceSnapshotIdentity.kt:23-30`，KDoc `:6-22`）：同 `snapshotId` 且**除 `capturedAtEpochMillis`
外全部列**相等、归属行列表也相等 ⇒ 复用旧行、返回 false（不抛）；身份/内容/归属真变仍抛
`ImmutablePayloadConflictException("assessment_evidence_snapshot", …)`（`existing == null` 显式归到「不可复用」，
与旧行为等价）。先例写法是 `ModelTaskTransactionDao.kt:374` 的 `sameImmutableTutorResponse` / `:385` 的
`withoutTutorResponseTiming`（「抹掉时序后相等」）；任务书里写的 `AttemptTransactionDao.kt:374-391` 指向的是
`recordAnswerReveal`（不做时间戳容忍比较），不是先例——T1 已如实标注该指针错位。

**验收证据（本批实跑，逐条注来源）.**

- **instrumented（真 Room，设备一实跑）**：
  `MSYS_NO_PATHCONV=1 ./gradlew :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='com.tingyun.smartmistakebook.core.database.StudyDatabaseInstrumentedTest#reviewRetryReusesTheSnapshotRowWrittenBeforeARolledBackAdvance,com.tingyun.smartmistakebook.core.database.StudyDatabaseInstrumentedTest#onlyTheCaptureTimeMayDifferWhenTheSameSnapshotIdIsRewritten' --console=plain`
  → `Starting 2 tests on test_device(AVD) - 14` / `Finished 2 tests`、BUILD SUCCESSFUL in 48s；报告
  `core/database/build/outputs/androidTest-results/connected/debug/TEST-test_device(AVD) - 14-_core_database-.xml`
  （2026-10-16 00:27 生成）`tests=2 failures=0 errors=0`，单例 0.408s / 1.611s。用例复现完整死路：写快照（首落 T1）→
  令 advance 失败（poisoned `reviewQueueItemId`，事务整体回滚）→ 同一 snapshotId 换新时刻 T2 重写（旧代码在此抛冲突）
  → 重试 `recordReviewAttempt` 成功，ledger 前缀 1 行、attemptEventCount/outboxCount 各 1，回读快照仍是 T1
  （证明复用旧行、未被新时刻改写）；另一例钉「仍抛」的另一半（只差采集时刻不抛；归属权重变、内容列变仍抛）。
- **JVM 单测**：`AssessmentEvidenceSnapshotIdentityTest`（4 例：只差采集时刻＝同一张；snapshotId 不同 / 9 个内容列逐一不同 /
  归属行（含数量）不同＝不是同一张）—— 独立复核实跑
  `:core:database:testDebugUnitTest --tests "*AssessmentEvidenceSnapshotIdentityTest*"` → tests=4 failures=0 errors=0。
- **报告与源码级复核（独立核对）**：XML 的 mtime / tests / failures / timestamp / `test-result-exit-code.txt=0` 与
  `StudyDatabaseInstrumentedTest.kt:911/972` 的断言语义（`:968` `assertEquals(firstCapture, persistedAttempt.assessmentSnapshot.capturedAtEpochMillis)`、
  `:993` `changedAttribution is ImmutablePayloadConflictException`）逐项吻合；独立核对**未重跑**该仪器化用例（重跑会触发 AGP 卸包清数据）。
- **本轮记录者实测（2026-10-16）**：两个用例名确实存在于 `StudyDatabaseInstrumentedTest.kt:911/972`；
  `sameEvidenceSnapshotIdentity` 在 `AttemptTransactionDao.kt:653` 被调用、`:651` 注释写明「时间戳不是快照身份的一部分」；
  APK `app/build/outputs/apk/localFirst/debug/app-localFirst-debug.apk`（205,979,834B、2026-10-15 23:49）
  sha256=`ecd9e905db6b2540c61f0b3fca838abcfd4c5c583a8e2598cf87aa07da94ea57`，解包后 `sameEvidenceSnapshotIdentity`
  在 dex 命中 1 次 —— 即设备一安装的那只包确带本修复（设备一的抄录值与独立核对的复算一致）。

**未达 / 剩余（如实）.**

- **设备级 UI 端到端仍未达**：设备一没能走到「完成一次判定结算后连点重新判定 ≥2 次」——opencode zen 周配额耗尽
  （`GoUsageLimitError` / `limitName=weekly`；应用内能力测试 429 bodyBytes=168，host 直连复现，加 `x-opencode-session`
  头后仍 429），库内 `problem` / `review_queue_item` / `attempt_submission` / `assessment_evidence_snapshot` /
  `tutor_conversation` 均 0 行，设备上不存在可触发该按钮的状态。**因此本条原关闭条件里「设备级『重试成功』的证据」
  一项未满足**；本条的闭合依据是 DAO 层 instrumented 证据，**不等于** UI 端到端「重新判定必成功」。
- **复用旧行的语义后果（T1 如实标注）**：重试**不改写**已落库快照的 `capturedAtEpochMillis`（保持第一次那份，
  instrumented 用例已钉住）；若产品希望刷新为重判时刻，需另行裁定。
- 归属行顺序沿用既有口径（`toAttributionEntities()` 按 bindingId 排序 vs `findAttributions` ORDER BY binding_id），
  本条只改时间戳这一维。
- 独立复核结论（读码级 + 其自跑定向用例）：成立——容忍比较只排除 `capturedAtEpochMillis` 一列
  （`AssessmentEvidenceSnapshotIdentity.kt:29`），其余全部列与归属行仍逐一比较，`existing == null` 也仍抛。

**Reopen condition（2026-10-15 更新；原文保留）.** 原：n/a —— 本条即 open；关闭条件 = 修好上述死路并有设备级
「重试成功」的证据（设备级一项仍未达，见上）。现：任何入口再次出现「同 snapshotId、新时间戳」的
`ImmutablePayloadConflictException("assessment_evidence_snapshot")`；或下一批设备段补做「失败 → 重新判定 → 成功」
走查后仍失败 —— 两者任一成立即按本条重开。

## KD-38 (fixed 2026-10-15，提交 `038d50c0`；设备前后对照未达、触发频率仍未取样 —— 见「未达 / 剩余」) · 讲题计划的动作标签超 32 字符即整轮永久失败

**Symptom.** 判定链设备复验时观察到：真实服务商（opencode zen / deepseek-v4.1-flash）对同一道题的
讲题计划**连续 4 次 INVALID_RESPONSE、第 5 次才成功**；走查者当时抓到一条 logcat
`IllegalArgumentException: Tutor move label exceeds budget`（`core/model/.../TutorTasks.kt:191`，
`TutorSuggestedMove.MAX_LABEL_CHARS = 32`）——即模型生成的某个动作标签超过 32 字符时，
解析直接抛 IllegalArgumentException → 归 INVALID_RESPONSE（**不可重试**），整轮计划作废。

**证据等级（照实）.** 代码机制**真实存在**（复核者实读 `TutorTasks.kt:191` 的预算校验与抛点）；
但那条 logcat 原文**未能独立核对**（app 主日志缓冲已轮转，复验窗口滚出）——**该形态是否真实触发、
触发频率多少，均为未验证**。可确证的是：同一题连续 4 次失败、第 5 次成功，与"模型输出长度随机
越界即永久失败"的表现一致。

**Impact.** 对推理型模型（输出冗长）这是一个高概率的永久失败源：一个 33 字符的标签就把整轮计划
判死且不可重试，学生只能反复点「重试」赌下一次不越界。

**Fix direction（下一批裁定）.** 二选一或组合：① 超长标签按预算**截断**（把约束当格式要求而非
完整性判据，与既有 `take(512)` 一类做法同族）；② 把它归为**可重试**失败（`INCOMPLETE_OUTPUT`
一类的语义）而不是 INVALID_RESPONSE。修后补一条"超长标签不再整轮判死"的用例，并在设备上复验
同一题的成功率变化。

**修法（2026-10-15，本批 T1；① 与 ② 同时落地；提交 `038d50c0`）.**

1. **① 解析期按格式预算收敛短标签**（截断发生在 `distinct()` 之前；构造器**未放松**）：
   计划/回应两条路由的动作标签 `nextMoves[].label` 取 `TutorSuggestedMove.MAX_LABEL_CHARS`（32）
   （`OpenAiModelResponseParsers.kt:364`、`:408`）；`targetedEvidenceLabels` / `inferredKnowledgeLabels`
   取 `TutorKnowledgeEvidence.MAX_LABEL_CHARS`（96）（`:353`/`:357`）；两条路由的题锚词 `anchorTerms`
   取 `TutorRoundQuestionDeclaration.MAX_ANCHOR_TERM_CHARS`（64）（`:633`/`:646`）。预算常量仍在
   `TutorTasks.kt:152`（96）、`:207`（32，KDoc 写明「解析期先 take、构造器仍严格校验——它同时守 DB 回读」）、
   `:683`（64），`requireSafeModelText` 调用原样保留。
2. **② 提示词声明并 bump 版本**：计划规则 12（`OpenAiModelTaskAdapters.kt:252`）与回应规则 8（`:349`）
   各补一句预算声明，数值用常量插值（渲染为「label不超过32个字符」）；
   `ModelPromptPolicyVersions.TUTOR_PLAN` = `tutor-plan-v18-figure-conditional` →
   **`tutor-plan-v19-move-label-budget`**、`TUTOR_RESPOND` = `tutor-respond-v25-figure-conditional` →
   **`tutor-respond-v26-move-label-budget`**（`ModelEgress.kt:55`/`:67`，KDoc 追加 v19/v26 变更说明）。
3. **② 专用异常 + 网关归可重试**：新文件 `ModelTextBudget.kt` 定义
   `ModelTextBudgetExceededException(message) : IllegalArgumentException`（语义＝这一次输出超出格式预算、重发可换抽样；
   仍是 IAE 子类，既有 catch 行为不变），`requireSafeModelText` 的超预算分支改抛它
   （`ModelTasks.kt:1219-1224`），blank/控制字符两条 `require` 留在原类型。网关两处映射新增
   `catch (ModelTextBudgetExceededException)` → `OUTPUT_BUDGET_EXCEEDED`（`ModelFailureCode.INCOMPLETE_OUTPUT`、
   `retryable=true`、文案「模型这次输出超出长度预算，可以重试」；`OpenAiCompatibleModelGateway.kt:281-285` 预发送防御性出口、
   `:441-452` 响应解析出口、常量 `:891-895`），并排在 `catch (IllegalArgumentException) → INVALID_RESPONSE` 之前
   （`:289-291`）——形状错误仍不可重试。

**验收证据（逐条注来源）.**

- **JVM 定向用例（独立复核实跑）**：`:core:data:testDebugUnitTest --tests "*TutorLabelBudgetTruncationTest*"
  --tests "*TutorMoveLabelBudgetPromptTest*" --tests "*SmbkArchiveCodecTest*" --tests "*DenseTokenizerParityTest*"`
  → 3 / 4 / 13 / 7，0 失败；另 `:core:model:test :core:data:testDebugUnitTest :feature:review:testDebugUnitTest
  --tests "*TutorTasksTest*" --tests "*OpenAiCompatibleModelGatewayTest*" ...` → 63 / 17 / 5 等，0 失败。
  新增用例名：`TutorLabelBudgetTruncationTest`（97 字知识标签截到 96、66 字锚词截到 64 且仍通过逐字锚绑定本轮题、
  直接构造 33 字标签仍抛 `ModelTextBudgetExceededException`）；`TutorMoveLabelBudgetPromptTest`（计划/回应两条提示词
  含「label不超过32个字符」、预算字面量 32、两个版本字面量——提示词一改，钉版本的用例先红）；
  `OpenAiCompatibleModelGatewayTest` 三条（计划/回应 33 字符标签截断后整轮成功、正文级超预算 messageMarkdown > 12000
  → 可重试 INCOMPLETE_OUTPUT）。
- **包内确带修复（设备一 dex 扫描 + 独立核对复算 + 本轮记录者复算）**：APK sha256 同上（与 KD-37 同一只包）；
  dex 命中 `ModelTextBudgetExceededException` ×2、`tutor-plan-v19-move-label-budget` ×2、
  `tutor-respond-v26-move-label-budget` ×2、`exceeds budget` ×8、`OUTPUT_BUDGET_EXCEEDED` ×2
  （前四串为设备一/独立核对的读数，`OUTPUT_BUDGET_EXCEEDED` 与 8 次 `exceeds budget` 为本轮记录者复算；
  逐串命中数与两段材料互洽）。
- **本轮记录者实测（2026-10-16，读码级）**：上述全部截断点、常量、版本字面量、`AppFailure` 映射与网关 catch 顺序
  在树内逐一核对通过；旧字面量 `figure-conditional` 在**活代码/活文档**里零命中（仅剩
  `docs/research/2026-10-03-stage4b-completion-record.md`、`2026-10-11-thin-vertical-slice-walkthrough.md:470`、
  `algorithm-version-ledger.md:236` 的历史记录，以及 `.artifacts/`、`build/agent-batch11/clean-head/` 两处快照/暂存副本）。
- **T1 侧自述**：本轨**未运行任何 Gradle**（编译与断言在其自述中为 UNVERIFIED），且他线当时在飞同一对文件
  （`OpenAiCompatibleModelGateway.kt` / `OpenAiCompatibleModelGatewayTest.kt`，日志上报口改写）；合并时按区域核，
  复核时 grep `OUTPUT_BUDGET_EXCEEDED` 与 `aTutorBodyBeyondItsTextBudgetIsRetryableInsteadOfPermanentlyFailed`。

**未达 / 剩余（如实）.**

- **设备前后对照未达、触发频率仍未取样**：设备一进「去讲题判定」在链条第一环断掉（录入→整理需模型 → opencode zen
  周配额 429 → 造不出可复习题），**计划失败次数、最终成功、失败原文分类（标签越界/其他形状/截断/其他）一项未测**；
  独立核对另证实「失败原文分类」的原始证据**不存在**（设备无 `files/debug-model-failures`、logcat 窗口无
  `ModelFailureCapture|TUTOR_PLAN` 命中、仓库无相关记录）——即本条的触发频率自登记起至今仍为未验证。
  T2 的捕获通道已武装过（设备一实测）但未触发，且随之后的卸载重装丢失。
- **截断按 UTF-16 码元可能切断代理对（独立复核 low，未修）**：`String.take(n)` 在 emoji 处可留下孤立代理，
  而 `requireSafeModelText` 只拒 ISO 控制字符与双向控制符（`ModelTasks.kt:1225-1237`）不拒孤立代理；
  同批 `DebugModelFailureCapture` 为同一风险专门写了不切代理对的 `truncateToUtf8Bytes`，本条这侧没有等价保护。
  只确认到显示层可渲染为「�」，未验证下游（JSON 序列化/导出）是否抛异常。
- **UI 文案未分叉（T1 标注，读码级）**：`AppFailure.kt:240` 把 `INCOMPLETE_OUTPUT` 一律映到
  `MODEL_OUTPUT_INVALID`、`:258` 显示「模型这次没写完」，所以超预算场景学生侧看到的仍是截断措辞；
  持久化的 `ModelTaskFailure.message` 才是更准确的那句。要分开需要新失败码（DB 词表/迁移面），超出本批范围。
- **同形但未点名的字段**：`TutorDebriefOutput.teachingFocusLabels`（预算 `TutorDebriefInput.MAX_DEBRIEF_LABEL_CHARS`）
  与其它正文级模型文本**不做**解析期截断，只在③上受益（超预算 → 可重试）；是否补截断需另开一条。
- **重试出口受派遣预算限制（与 KD-33 未覆盖项 ③ 同源，本轮记录者读码复核）**：预算用满后 retryable 会被改写成
  `PERMANENT_FAILURE`（`RoomModelTaskRepository.kt:730-744`），`OUTPUT_BUDGET_EXCEEDED` 同样受此限制。
- **台账未登记本批 bump（独立复核 low）**：`docs/research/algorithm-version-ledger.md:236` 的最新一条仍是
  「批 4：`tutor-lobby-v17-export-layout-proposal`（plan/respond 不动）」，没有 v19/v26 这一行（历史行未被改错）。
- T1 侧零编译零测试（见上「T1 侧自述」）。

**Reopen condition（2026-10-15 更新；原文保留）.** 原：n/a —— 本条即 open；关闭条件 = 修好并有「超长标签不判死」的
测试证据（测试证据已到，设备对照仍未达）。现：真实服务商上再次出现「超长标签 → 整轮永久失败」的形态；或下一批设备段
做出前后对照后发现失败构成里仍有标签越界类（那说明截断之外的字段仍在判死）。

## KD-39 (fixed 2026-10-17，提交 `463a5dea`（读侧对账补偿）) · 切分回执写在提交事务之外：「题已入库、台账仍 PENDING」且无补偿路径

**Symptom（读码推断，来源：本批九轨独立复核 finding 1）.** 本批 T4 新增的 SAVED 回执
（`RoomCaptureWorkflowRepository.confirmAndCommit` 拿到真实提交后调 `recordSplitImportCommit`，
`:451-483`；讲题来源路径 `saveTutorSession` `:649-656`）写在提交事务**之外**：提交与回执是两次相邻的
数据库调用，两步之间进程被杀/协程被取消，题已在库（`confirmState=COMMITTED`），而分题任务台账仍是 PENDING。

**Impact.** 该分题任务永久停在「还有一道待提交」：`readNextUnsubmittedDraft` 排除刚提交的草稿、复核页无
「放弃」出口、草稿 `COMMITTED` 读不回——三条路都不通，且无任何补偿/对账路径把它救回。窗口窄
（两次相邻数据库调用之间），当前未设备复现。

**Fix direction（待裁定）.** 二选一或组合：① 回执并入同一事务（提交与置 SAVED 原子）；② 保留两步但加对账
补偿——启动/进入复核时按 `splitDraftId` 反查已 COMMITTED 的题把台账追平（T4 已有
`readQuestionBySplitDraftId` 反查口，复用即可）。

**Reopen condition.** n/a —— 本条即 open；关闭条件 = 上述窗口被消除或补偿路径落地，并有一条
「提交后被杀 → 重启后台账追平」的用例（instrumented 或设备）。

**关闭记录（2026-10-17，提交 `463a5dea`）.** 按 Fix direction ② 落地：读侧对账补偿
（`RoomSplitImportStore.reconcileCommittedQuestionDrafts` 在同一写事务里把「草稿已 COMMITTED 且台账
PENDING」的题补写 SAVED 回执，复核页装配/逐题续做/启动各跑一次）；instrumented
`SplitImportReceiptReconciliationInstrumentedTest` 5/0（设备实跑，含「提交后被杀 → 重启追平」）。实现与
证据详见批次记录 §2 T1/§3.3。

## KD-40 (fixed 2026-10-17，提交 `463a5dea`（生成器补「绑定时刻」说明）) · status.md 的 run_context 绑定陈旧：本地生成必显示 BOUND 且可指向旧提交

**Symptom（来源：本批独立复核 finding 3；本轮记录者读码）.** 本批 T5 给状态页加了 `run_context()` 绑定
（`tools/ci/generate_status.py:432-470`）：COMMIT_SHA/BRANCH 与 `git rev-parse HEAD` 不一致即 UNBOUND。
本地手动生成时页面顶部固定显示 `BOUND · sha <生成时的 HEAD>`——本批记录里它指向 `a15aebb7`（本批之前的
提交，生成时本批代码尚未提交）。页面不会自述「这是生成时刻的绑定」，读者容易把 sha 当成「页面描述的那个状态」。

**Impact.** 低：不改变门判定（三值 Overall 与证据新鲜度已诚实化），只影响状态页可解读性——一份随本批提交的
页面会指向本批之前的提交。

**Fix direction（待裁定）.** 生成物头部补一句「绑定时刻 = 生成时刻，非所描述代码的提交」；或 CI 在
commit-back 后重新生成（自指不可解，至少注明）。

**Reopen condition.** n/a —— 登记性低危；读者被该 sha 误导时升级。

**关闭记录（2026-10-17，提交 `463a5dea`）.** `generate_status.py` 新增 `RUN_CONTEXT_NOTE`，头部 BOUND/UNBOUND
两种情况都印「绑定时刻 = 生成时刻，非所描述代码的提交」；模板同步；生成器单测更新（`test_ci_generate_status.py`，
T2 自跑 16 例）。见批次记录 §2 T2。

## KD-41 (fixed 2026-10-17，提交 `463a5dea`（损坏行覆盖重写）) · 小测题面损坏行 ⇒「重试出题」每次重生成、必再撞同一身份、再丢弃

**Symptom（读码，来源：本批独立复核 finding 5）.** `KnowledgeQuizItemStore.save()` 在「既有行存在但解不开」
（损坏载荷）时返回 null（`KnowledgeQuizItemStore.kt:66-79`），调用方把 null 当「未持久化」→ 显示 Unavailable；
学生点「重试出题」→ 重新生成 → 再按同一 `(sessionId, knowledgeNodeId)` 身份保存 → 再撞同一损坏行 → 再丢弃。

**Impact.** 低（需先有损坏行才会进这条死循环）：白烧一次模型调用、题面永远显示不出来。

**Fix direction（待裁定）.** 损坏行按「不可复用」处理：读到解不开的行即覆盖重写（或先删后写），
补一条「损坏行 → 重试可恢复」的用例。

**Reopen condition.** n/a —— 登记性低危；损坏行在真实设备被观测到时升级。

**关闭记录（2026-10-17，提交 `463a5dea`）.** 损坏行按「不可复用」处理：`save()` 撞
`ImmutablePayloadConflictException` 后先分辨既有行还能不能解，解不开才**整行覆盖重写**（新增端口
`overwriteAssessmentItemSnapshot`；正常行仍首见即真）；用例「损坏行 → 重试可恢复」；`KnowledgeQuizItemStoreTest`
7/0（设备实跑）。见批次记录 §2 T3。

## KD-42 (fixed 2026-10-17，提交 `463a5dea`；设备核由独立核对纠正——提示实际已显示) · 批量导入在 staging 阶段被杀：该批静默丢弃、无用户可见提示

**Symptom（设备观察，来源：本批 T3 设备段 + 独立核对补正）.** 批量导入（20 页）在 job 建立**之前**的
staging 阶段杀进程：重进 App 后该批不存在（列表里没有、也没有失败条目），学生看到的是「什么都没发生」。
独立核对补正：孤儿 staging 目录**非永久残留**——`ORPHAN_GRACE_MILLIS=60min` + `reconcileOrphans` 会在
下次启动且超宽限后回收（`AndroidBatchImportSourceStaging` 常量、`RoomBatchImportRepository.kt:89`），
磁盘面自愈。对照组：job 建立**之后**被杀可完整续跑（5/5 页 READY、无重复行、无新模型调用）。

**Impact.** 低-中：数据面无损（源图还在相册、可重导），但用户面无任何「这批被中断了、请重导」的提示。

**Fix direction（待裁定）.** 最小面：staging 开始时先登记一条「导入中」记录，被回收/超时后显示为
「已中断，可重导」。

**Reopen condition.** n/a —— 登记性低危；用户因此丢失过工作且无人察觉时升级。

**关闭记录（2026-10-17，提交 `463a5dea`）.** 读口 + 提示落地（staging 非空 ⇒ 批量导入入口显示「上次导入被中断，
可重导」，带 testTag）；**设备核由独立核对纠正**：提示实际已显示——截图 `10_kd42_no_notice.png` 与
`ver2.xml` 均命中 `batch_import_interrupted` 节点与同一文案，设备走查员误把空态截图当证据（自述「未观察到
提示」不成立）。instrumented `BatchImportInterruptedStagingInstrumentedTest` 1/0、
`BatchImportInterruptedNoticeInstrumentedTest` 2/0。见批次记录 §2 T4/§3.3/§3.5。


## KD-43 (fixed 2026-10-21（批次 16 收尾：单元 3,029/0 + 设备 24/0 含身份粘性自动补偿/UNKNOWN 键 REPLACE/跨会话隔离）；2026-10-16 登记（AUD-11 报告 §3；medium）) · 答案键：错键判错之后没有纠正/撤销/重放的落点；身份粘性还会静默丢写

**Symptom（实测，来源：`docs/research/2026-10-15-aud-11-verdict-granularity-cooldown-answer-key.md` §3；
真库 + 真写入口，5/5 通过）.** 题面 A 客观正确、B 错误，模型把键指到 B，学生选 A（客观正确）：

- 判答 `isCorrect = false` → 落 **NEGATIVE 0.35**（confidence 1.0，理由「知识点复习作答（答错）」）；
  账本里只有一条 `ChatEvidenceSubmitted`、**没有 attempt/快照事件**——attempt 侧的纠正设施
  （`AttemptCorrection` 一族）没有可纠正的对象。
- 证据行里**没有题面/键/选项列**：`evidence_id = knowledge-quiz:<requestId>:<节点>`（只由 通道 + 命名空间 +
  节点 派生），其余各列全是语义位（方向/权重/理由/置信/通道/时间/拒因/锚定等级）。
- 纠正三路全落空：①同 requestId + 纠正后的键（冷却内）被 `SAME_KC_IN_COOLDOWN` 拒，且与既有行同 id
  **连观察行都落不下来**；②换新身份仍拒（只多一条 rejected 观察行）；③**13h 后**纠正只**追加**一笔
  POSITIVE——错判不被替换、不被补偿、不被抵消（append-only 并存）。
- 撤销无落点：端口上的 chat-evidence 面只有写 + 读 + 计数（`ModelTaskDatabasePort`），全仓对
  `learner_chat_evidence` 没有 UPDATE/DELETE 的写路径。
- 重放：`LearningProjector.replay` **确定性复现**错判（`failureWeight = 0.35`、`masteryScore` 更低、
  两次重放逐位相同）——重放洗不掉它，只会再积一次分。
- **身份粘性（静默丢写）**：一条先被拒的写占住 `evidence_id`；条件变化后**同一身份**的合法写通过门、
  写入口回报 `Accepted`，而 Room 的 IGNORE 把它吞掉——账本事件数不变、库里那一行仍是被拒状态。

**Impact.** 中：错键一旦落库，学生的正确作答按「答错」长期计入掌握度（`0.35` lapse + 重放叠加），
而系统里**没有任何**纠正、撤销、补偿或重放的落点；审计面在 T9 之前也无法定位「这笔证据读的是哪一道题、
哪一版键」。两个面都会静默：错判静默留在账本里，身份粘性的丢写在写入口侧曾经完全无感。

**Fix direction（待裁定，纠正语义留 5B）.** 二选一或组合：①错键被纠正后由一笔**补偿性账本事件**抵消
（需要证据载荷能定位受影响的行——T9 已补审计口与键版本引用）；②写路径按「键已改判」单列一类事件；
③明确裁定「恢复只读回原题面、改判走新会话/新身份」，把 ① 的缺口变成有意的产品边界。身份粘性那一半：
T9 已把「accepted 被 IGNORE」变成可观察结果（`ChatEvidenceWriteReceipt` 三档 + WARN 日志，
`KnowledgeEvidenceLedgerStatus.BLOCKED_BY_REJECTED_ROW`），**谁该吃这个冲突仍未裁定**（是回写端
报冲突、还是允许改写既有被拒行）。

**2026-10-20 进展（批次 15 T2，schema 68；代码级 + JVM，未关账）.** 已落地：
① `learner_chat_evidence.corrects_evidence_id` + 索引（迁移 `AnswerKeyCorrectionMigration_67_68.kt:20-38`，schema 68）；
② 账本 `EVIDENCE_CORRECTION`（`LearningState.kt:551/556`）——`VOID` 从有效证据集合排除、`REPLACE` 按账本最终有效替代重算并挂回原作答位置，
目标必须更早、悬空/自指在重放入口拒绝（`LearningProjector.kt:543/720/732/799-831`）；
③ 投影/证据/账本三串同 bump（`projector-v14`/`evidence-v6`/`ledger-v4`，`learning-core-v14`），登记见 `algorithm-version-ledger.md` §3.25；
④ 键信任两轴：模型题面 `verificationStatus=UNKNOWN`、`scoringMode=AUTO_VERIFIED` 只表示本地可机械判分（`KnowledgeQuizItemStore.kt:136-140/157`）；
未核验/争议/迟到旧键只落 rejected 观察行；⑤ 小测“答案有问题”入口与 I1 接线（`KnowledgeReviewSessionScreen.kt:64/87/348-412`、
`SmartMistakeBookRoot.kt:748-815`）。复核后又在当前树修了两处：
已作答但只落 rejected 观察行时回落到争议观察（`KnowledgeQuizFeedbackWriter.kt:443-452`），以及 `resolveTarget` 只认原始 `KNOWLEDGE_QUIZ`、
排除更正行自身（`:626-647`，避免第二次报告撤销第一条 VOID）；掌握度聚合排除 `KEY_CORRECTION`/`KEY_VOID`（`MasteryOverviewDao.kt:149`）。

测试（门禁 XML，记录员未重跑）：`ChatEvidenceCorrectionReplayTest` 9/0、`KnowledgeQuizFeedbackWriteTest` 5/0（core:data 02:17）、
`AnswerKeyTrustPolicyTest` 5/0（core:domain 02:14）、`KernelWave0SchemaContractTest` 8/0（core:database 02:14）。
`AnswerKeyCorrectionInstrumentedTest` 现有 11 例（含“已作答未核验键争议”“第二次报告不命中第一条 VOID”）与 `FullMigrationMatrixInstrumentedTest` 的 67→68 用例
**均未执行**，因此不能按关闭条件关账。

**2026-10-21 收尾实跑（关闭）**：单元门 11 模块 **3,029/0**；设备 `SelfReportedReviewInstrumentedTest` 1/1、
`AnswerKeyCorrectionInstrumentedTest` 12/12、`KnowledgeQuizCooldownChannelScopeInstrumentedTest` 7/7、
`ReviewUndoTransactionInstrumentedTest` 4/4 全部通过。读侧 `KnowledgeQuizItemStore` 已传 `sessionId` 与
`correctionLedgerOrder`（只接受本会话键替代）；UNKNOWN 键 REPLACE 对话框与 Root 的 `loadAnswerKeyVersion`
兜底已接线；自动补偿与“补偿身份也被占住”的反例有 JVM 与设备证据。UI 对话框无单独设备点击，但写路径/重放有设备证据。

**2026-10-21 进展（批次 16 T1a/T1b/T7；提交前记录）.** 三处落树：
① **(b) 自动补偿**——`KnowledgeEvidenceWriter.kt:117/159-192` 先按原身份写，撞住被拒观察行时换确定性新身份
（`knowledgeEvidenceIdAfterRejectedIdentity`，`:308-337`）再写；回执新增 `LEDGERED_AFTER_REJECTED_IDENTITY`/
`REPLAYED_AFTER_REJECTED_IDENTITY`（`:371-380`），上层 `KnowledgeQuizFeedbackWriter.kt:250-272` 按“已入账”回报；
补偿身份也被占住时仍报 `BLOCKED_BY_REJECTED_ROW`（反例，不无限换身份）。被拒观察行由 DAO IGNORE 保留、不改写。
② **UNKNOWN 键 UI 走 REPLACE**——`KnowledgeReviewSessionScreen.kt:455-560` 新增“改正答案键”对话框；
`KnowledgeQuizFeedbackWriter.kt:595-665` 保留观察行并按用户断言的新键把这次作答重判成新身份证据（`recordUserAssertedKeyCorrection`
`:804-853`），`expectedKeyVersion` 为题面级 CAS（`:856-874`）；I1 接线见 `SmartMistakeBookRoot.kt:872-950`
（`loadAnswerKeyVersion` 按 item.id 现读，提交前兜底取版本，不再传 null 关掉 CAS）。
③ **读侧同源/跨会话隔离**——`latestCorrectedKey` 支持账本序（`:379-445`）；`KnowledgeQuizItemStore.kt:41-70`
同时传 `sessionId` 与 `correctionLedgerOrder`，只接受本会话的键替代，避免常量 itemId 把旧会话的键套到新题面。
测试已写：`KnowledgeEvidenceLedgerStatusTest`（含补偿身份也占住的反例）、`KnowledgeQuizFeedbackWriteTest`
（UNKNOWN 改正重判恰一次）、`LatestCorrectedKeyOrderTest`（账本序/撤销回退/显式降级）、设备
`AnswerKeyCorrectionInstrumentedTest` 增量。该段为提交前记录；收尾实跑已补单元/设备证据并关闭（见下）。

**Reopen condition.** 再次出现错键无纠正落点或身份粘性静默丢写时重开；关闭条件 = 上面两半各自有明确处置落地：(a) 错键纠正/撤销/
补偿至少一条路径 + 一条「错键 → 纠正 → 账本被抵消/被单列」的用例；(b)「同身份撞被拒行」的行为被裁定并
有用例钉住（报冲突 / 覆盖重写 / 保持静默但记账）。**当前判定**：收尾实跑单元 3,029/0、设备 24/0 通过，已关闭。

## KD-44 (fixed 2026-10-18，提交 `8335613f`；裁定=后台自动结算、零用户参与) · 判词锚定让「KC 集为空」的题永远拿不到判定：NO_VERDICT 永久阻塞 + 文案误导

**Symptom（读码，来源：批次 12 对抗性复核）.** T8 的锚定判据（整题判决要求 ≥1 条 accepted 证据锚在
`MistakeRecord.knowledgeNodeIds` 上）对「KC 集为空或只含伪节点」的题是一个永久阻塞：锚定集来自
`knowledge_question_lattice`（有回执时还要求 accepted==MAX 且存在 KNOWLEDGE 分类行，`ProblemDao.kt:122-160`），
而伪绑定/无绑定题读到空集 → 模型判词被整条丢弃 → NO_VERDICT 永久阻塞该复习项；提示文案
「本次讲题还没有形成判定…」（`SmartMistakeBookDestinations.kt:513-518`）对「模型给了判词、但证据不锚定」的
情形是误报（NO_VERDICT 的定义是「模型也没给出判词」，`TutorJudgedReview.kt:70-83`）。

**Impact.** medium：这类题走讲题判定通道时永久卡住（与 KD-37 同类「唯一出口必然失败」的形态）；同时文案把
「无锚定证据」说成「没有判定」。注意边界：绑定读口自身刻意「不要求匹配 KNOWLEDGE 分类」
（`ProblemOrganizationDao.kt:522-533` KDoc），因此「空 KC 集」是合法状态而非数据损坏——缺口在出口与文案，
不在锚定本身。

**Fix direction（待裁定）.** ① 锚定集为空时给「本题无绑定知识点」的明确出口（文案按「无绑定」分流，
不伪装成「没有判定」）；或 ② 允许伪节点（`pseudo:*`）参与锚定集。

**Reopen condition.** n/a —— 本条即 open；关闭条件 = 上述任一出口落地 + 一条「KC 集为空 → 明确可见出口
（不永久静默）」的用例。

**关闭记录（2026-10-18，提交 `8335613f`；裁定原话：「要的是解决办法…需要自动化在后台做好，不要有用户的
参与决定这类事，用户不需要知道」）.** 按裁定实施 **pseudo 锚定自动结算**：`TutorJudgedReviewSettler` 在本题
KC 集为空（诚实未分类）时，把锚定集换成该题 revision 的伪绑定节点 `pseudo:<科目>`——已物化则直接取
（表内全部行读口 + rev/node 三重限定），未物化则复用既有物化先例就地物化 → 判词正常结算、归属指向伪绑定行、
队列推进；**零用户可见文案/指引**（「还没有形成判定」只留给真·无判词）。自纠路径：投影器
`attributionsForAttempt` 的 upcast 从**当前绑定**派生——题后来被绑定/改绑后重放自然改挂真实节点（KF-32 机制，
本批留证）。测试：`TutorJudgedReviewSettlerTest`（空 KC 集题正常结算到 pseudo、归属指向伪绑定、不再 NO_VERDICT；
真·无判词仍 NO_VERDICT）+ 设备 `TutorJudgedReviewSettleInstrumentedTest` 2/0。零 bump 依据见 ledger §3.23。
见批次记录 §2 T5。

## KD-45 (fixed 2026-10-17，提交 `463a5dea`) · 净化领取的比较交换会误报成功：并发下重复付费 + 双写终态

**Symptom（读码，来源：批次 12 对抗性复核 finding 3）.** `claimTaskAttempt` 丢弃 UPDATE 的命中行数，
改用「回读到的 `attemptCount == expected+1`」判领取：两个并发调用者读到同一版本 N 时，输家 UPDATE 命中 0 行，
但赢家刚把行加到 N+1 → 输家回读也命中 → 误报领取成功，继续跑同一行作业（重复付费；两个执行者用相同
expected 写终态，后写覆盖先写，SUCCEEDED 可被覆写成 FAILED/SUPERSEDED）。

**关闭记录（2026-10-17，提交 `463a5dea`）.** 比较交换以 UPDATE 的**命中行数**为准：`claimed == 0` 即返回
null（领不到）；`CleanRedrawTaskRunnerTest` 17/0 + 设备 `CleanRedrawTaskInstrumentedTest` 2/2 绿。
见批次记录 §2 T6/§8。

## KD-46 (fixed 2026-10-18，提交 `8335613f`（口径按计划默认=每次排计划 ≤10）) · 3E「每日 10」实为「每次排计划 ≤10」

**Symptom.** 知识点队列不落库（会话内现排，ledger §3.12 同口径），cap 只在 `selectKnowledgeReviewQueue`
对第二档候选 `.take(FULL_LIBRARY_SCAN_DAILY_CAP)`；同日多次进入（或答题后重算）可拿到不同的 10 个节点，
当日累计可超过 10。裁定（2026-10-16）的口径是「每日最多补捞 10 个」。

**Impact.** low：单次会话的预算与体验不受影响；「每日」总量是近似值。若裁定本意是严格每日总量，需要落库
当日已扫描集合（新增持久面）。

**Fix direction（待裁定）.** ① 接受近似（KDoc 写明「每次排计划 ≤10，非严格每日」）；或 ② 落库当日已捞回集合。

**关闭记录（2026-10-18，提交 `8335613f`）.** 按 ① 落地：KDoc 如实写明「每次排计划 ≤10，非严格每日总量」，
同口径引用点六处同批改齐（`KnowledgeReviewFullLibraryScan.kt:5-15`、`KnowledgeReviewQueue.kt:131-133/:162-165`、
`StudyExperienceRepository.kt:337-338`、`StudyReviewPlannerService.kt:546-547/:652-653`、`KnowledgePort.kt:66-69`，
含测试注释）。口径按批准的计划默认，不再要求落库当日集合。见批次记录 §2 T6。

**Reopen condition.** n/a —— 登记性低危。

## KD-47 (fixed 2026-10-21（批次 16 T7；单元测试通过：StorageRestoreFeedbackTest 4/0）；2026-10-17 登记（批次 12 对抗性复核 finding 8；low）) · 设置恢复是多键顺序写、非原子：WRITE_FAILED 文案「本机设置保持原样」可能与事实不符

**Symptom.** `applyRestoredSettings` 依次调六个写口（setOptions→逐条删/加考试→setOptimizedParameters→
setEnabled→setReminderTime），中途失败返回 `WRITE_FAILED`；枚举 KDoc 与 UI 文案都称「设置仍是恢复前那份 /
保持原样」，但失败可能发生在中途——留下「改了一半」的设置。

**Impact.** low：只在恢复中途失败时发生；DB 已一致，设置半新半旧且文案误导。

**Fix direction（待裁定）.** 文案改「设置未能完整写入，可能有部分设置已变」；或写前先快照旧值、失败时回滚。

**进展（2026-10-18，提交 `8335613f`）.** 枚举与 `RestoreReceipt` KDoc 已如实化（`BackupRepository.kt:56-60/:76-84`）；
**面向用户文案仍未改**（`SecondaryScreens.kt:732`「设置未能写入，本机设置保持原样。」）——该字符串位于他线在飞
新增 hunk（不可分离），按计划留给后续；同缺陷的日志（`AndroidBackupRepository.kt:599`）与测试 KDoc 亦未改。
见批次记录 §2 T6。

**2026-10-19 再核（批次 14 T5）**：日志/注释侧已按批 14 落（`AndroidBackupRepository.kt:608-633` 明写「只能说
『未能完整写入、可能有部分设置已变』，不能说『设置保持原样』」）；**面向用户文案仍未改**——`SecondaryScreens.kt:734-735`
该串为他线在飞新增行（HEAD 无此串），本批未动。KD-47 保持 open。

**2026-10-20 再核（批次 15 记录）**：剩余项只有面向用户文案。`SecondaryScreens.kt:735` 在 `WRITE_FAILED` 时仍拼
「设置未能写入，本机设置保持原样。」；仓库自己的 KDoc 明确多键顺序写非原子、只能说「可能有部分设置已变」。该文件属他线在飞、
本批未改；`docs/research/2026-10-20-batch-15-prd-a-b-backup-wg05-record.md` §6 第 14 行照录。KD-47 保持 open（关闭见下条）。

**关闭记录（2026-10-21，批次 16 T7；代码级）.** 面向用户文案已在唯一调用点改为如实说法：
`app/src/main/kotlin/com/tingyun/smartmistakebook/SecondaryScreens.kt:954-962` 新增纯函数
`storageRestoreCompletionMessage`，`WRITE_FAILED` 返回「设置未能完整写入，可能有部分设置已变。」；
恢复完成消息由该函数生成（`:727-732`）。测试 `app/src/test/kotlin/com/tingyun/smartmistakebook/StorageRestoreFeedbackTest.kt`
新增两条（`WRITE_FAILED` 文案与另外两态），共 4 例。**证据边界**：记录员未重跑 Gradle；记录时点只读看到
`app/build/test-results/testLocalFirstDebugUnitTest/TEST-...StorageRestoreFeedbackTest.xml`（2026-10-21 05:05，tests=4 failures=0 errors=0）。
本条是文案改动，源码位置与文案已逐字核对，故判 fixed；不据此声称设置恢复的原子性已解决（缺陷原意只是文案误导）。

**Reopen condition.** n/a —— 登记性低危。

## KD-48 (fixed 2026-10-18，提交 `8335613f`（调试面消费点已接）) · 「可观察性先行」在应用里没有消费面

**Symptom.** `KnowledgeQuizEvidenceAudit`（internal）全仓只有测试调用、无生产调用方；
`KnowledgeEvidenceLedgerStatus` 无人读；「accepted 被 IGNORE 吞掉」暴露给学生的只是枚举值 + 一条 logcat WARN。
作为「先行」可接受，但它不是「学生/开发能看见」的现状。

**Impact.** low：写入口不再无感（回报 + WARN），但没有任何 UI/诊断面消费它。

**Fix condition.** 在调试/审计面（如设置页诊断或复核屏）接一个只读消费点，或等 5B 的纠正语义一并接。

**关闭记录（2026-10-18，提交 `8335613f`）.** 调试面消费点已接：`KnowledgeQuizEvidenceAudit` 公开化 +
新增公开 `scanRows()`/`KnowledgeQuizEvidenceAuditRow`（:67-97）；新增 debug-only
`app/src/debug/kotlin/.../KnowledgeEvidenceAuditActivity.kt`（只读、无写路径），`app/src/debug/AndroidManifest.xml:18-25`
注册（exported=true，与 `BindingAuditActivity` 同一边界）；三态用例（对上/坏行/未对上）落盘。见批次记录 §2 T6。

**Reopen condition.** n/a —— 登记性低危。

## KD-49 (fixed 2026-10-18，提交 `8335613f`（按 KDoc 方案如实化，不加索引）) · 切分对账查询没有可用索引前导列

**Symptom.** 对账查询谓词只有 `confirm_state`（`SplitImportDao.kt:113-116`），现有索引
`(job_id, confirm_state)` 无法用它做前导过滤 → 每次读触发全表扫；触发点在复核页 Flow 每次采集 / 每次取
下一题 / 启动。个人库数百行规模下不是缺陷，但与实现 KDoc「走索引扫描」的说法不完全一致。

**Impact.** low（规模小）；实现注释与事实有出入。

**Fix direction.** 加 `(confirm_state)` 单列索引（需 schema 迁移）或在 KDoc 如实写明走全表扫。

**关闭记录（2026-10-18，提交 `8335613f`）.** 按后者落地：`SplitImportDao.kt:98-116` 如实写明「谓词只有
`confirm_state`、现有索引（`SplitImportEntities.kt:52-56` 三条）不适用、当前走全表扫；个人库规模可接受，
不加索引」。见批次记录 §2 T6。

**Reopen condition.** n/a —— 登记性低危。

## KD-50 (fixed 2026-10-18，提交 `8335613f`（KDoc 与判据对齐）) · `isCurrentProblemRevision` 判据比 KDoc 宽

**Symptom.** `isCurrentProblemRevision`（`ProblemDraftTransactionDao.kt:757-761`）只查「有没有
`error_book_entry.current_revision_id = 该修订`」，不校验条目状态（ACTIVE/ARCHIVED）也不校验条目属于同一
practice unit——「挂到已归档条目的修订上」是它允许的形状，与 KDoc 措辞不完全对应。未观察到因此丢或错挂的
实际路径，仅作边界记录。

**Impact.** low（边界记录）。

**关闭记录（2026-10-18，提交 `8335613f`）.** KDoc 与判据对齐：写明「只查条目当前修订指向
（`error_book_entry.current_revision_id`），不校验条目状态（ACTIVE/ARCHIVED）也不校验同一 practice unit」
（`ProblemDraftTransactionDao.kt` KD-50 处）。见批次记录 §2 T6。

**Reopen condition.** n/a —— 登记性低危。

## KD-51 (fixed 2026-10-18，提交 `8335613f`；设备实测 0 条连接饥饿) · 冷启动知识库搜索特性重建占死 SQLite 连接池：单条 SQL 等 270s、冷启 ANR、采集路径卡「正在安全保存」

**Symptom（设备实测，emulator-5554；三次 reboot 后仍复现，宿主资源充足 64G/8.3G 空闲、CPU 39%）.**
冷启动反复 ANR（`/data/anr/...SystemJobService` 同型记录，主线程栈落
`BundledKnowledgePackResources.kt:132→:259` / `BundledKnowledgeBaseInstaller.kt:52` /
`KnowledgeNodeRelationContract.kt:74`）；logcat「SQLiteConnectionPool ... unable to grant a connection ...
for 270.20203 seconds」，阻塞语句 `DELETE FROM knowledge_search_feature WHERE subject = ?` /
`INSERT OR IGNORE INTO knowledge_search_feature`。后果：采集路径卡在「正在安全保存…」、新草稿不落库，
设备走查的 AUD-05/AUD-09 项因此无法完成。

**Impact.** medium（依赖包规模与设备速度）：冷启动阶段 App 整体不可用（ANR）+ 采集写入被饿死。

**Fix direction（待设计）.** 搜索特性重建改分块短事务 + 让读连接不被长写事务饿死；或重建降级为可中断后台
（采集路径优先）。复现命令与日志见批次记录 §3.3/§3.5（独立核对替换记录 `anr_2026-10-16-19-59-37-873`）。

**Reopen condition.** n/a —— 本条即 open；关闭条件 = 冷启动不再出现 270s 级连接等待 + 一条设备级复现对照。

**关闭记录（2026-10-18，提交 `8335613f`）.** T4 把整科重建从「一条长写事务（COUNT+整科 DELETE+一次性 INSERT）」
改为「读相（计数+候选集走 `withReadTransaction`，不占写连接）+ 分块短写事务（删/插各按 64 节点一块，抽取在
事务外）+ 块间可取消」，**锚点最后写**语义保持（崩溃/取消=整科重跑、无部分可见）。**设备实测**（人为构造的
强制全量安装，非用户路径；改前基线取自批 12 记录、本次未重测）：13 条分块重建（MATH 285.6s/449.3s 等，
独立核对更正 12→13），`install finished in 2781112 ms`，**全程 0 条 `unable to grant`**（改前为单条
270.20203s 等待）；快路径冷启 install 1075ms、0 条饥饿；采集保存不再被饿死（约 15s 内进入「整理题目」，
批 12 为卡死）。见批次记录 §2 T4/§3.2。

**补记（2026-10-18 对抗性复核，medium；无已知生产可达路径）.** 分块重建的完整性门是 `indexed >= reviewed`，
但 indexed 计数**不筛** `verification_status`（reviewed 才筛，`ProblemOrganizationDao.kt:228-235` vs `:215-221`）
——若某科存在「有特征行但未审校」的节点，取消于删块之后仍可能通过门、读路径把残缺索引判完整（静默少召回）。
已核当前无生产可达 I>R 路径（`KnowledgeBaseImportContract.kt:164` 禁 MODEL_CANDIDATE；退役删特征行；
伪节点不写特征行），仅作边界登记；加固方向 = indexed 计数同筛 verification_status（一行 SQL）。

## KD-52 (fixed 2026-10-19，批次 14 T2；设备全链 PASS) · A8 手动建题只落了半边：schema/校验器已放行，但没有生产者，空源草稿读取路径硬崩

**Symptom（读码 + 设备实测）.** 批次 13 的 A8 只落了：schema 67（`problem_draft.source_asset_id` 可空）+
校验器按 `USER_TRANSCRIPTION` 放行。**没有创建入口**——`MANUAL_TRANSCRIPTION` 全仓只有 model 定义、
生产代码无处设置（设备走查确认：录入屏只有 拍照/相册/整卷-PDF，无「手动建题」入口）；
而读取路径对空源草稿是硬 `checkNotNull(sourceAssets.firstOrNull())` 抛 `IllegalStateException`（非类型化失败）：
任何空源行出现时 resume/批处理/讲题会直接崩（`ProblemDraftTransactionDao.kt:1134-1146` 注释自认「空源草稿的
读取分支要连同创建入口一起落地（下一批）；现在没有任何生产者，这里对空源行 fail-closed」）。

**Impact.** medium：当前无生产者 ⇒ 无空源行 ⇒ 不会崩（fail-closed 是安全态）；但「手动建题」这个已批准的
产品功能**整体不可达**，计划里 T2 的「手动题全链提交」验收在本树无法验证。

**Fix direction（下一批首选）.** 创建入口（录入屏「手动建题」）+ 草稿链空源分支
（`importDraft`/`initialDraftCommand`）+ 读取路径空源分支（`ProblemDraftTransactionDao:1136`、`RoomStudyDatabase:842`、
`RoomBatchImportRepository:503`、`RoomTutorToolRunner:1071`、`RoomCaptureWorkflowRepository:470-480/649/680`）
+ 提交路径空源分支，一并落地并补全链用例。见批次记录 §2 T2/§3.5/§7。

**Reopen condition.** n/a —— 本条即 open；关闭条件 = 创建入口 + 全链提交用例落地（手动题 `created=true` 且
证据 provenance=USER_TRANSCRIPTION）。

**关闭记录（2026-10-19，批次 14 T2；设备全链 PASS）.** 生产者落地：录入屏「手动建题」→ `startManualDraft`
→ `importDraft(localUri=null)` 空源草稿（初始块 USER_TRANSCRIPTION、空 `source_asset_id`、无区域、作者 USER）+
读取/提交路径空源分支 + 全链用例。**设备实测全链**：录入方式含「手动建题」→ 填标题/题面 → 「保存修改并存入
错题本」→ 回执「这道题是新建入库的」→ 错题本 1 道题 → 详情页题面一致 → 导出 PDF 内容一致不崩。
`ManualTranscriptionCaptureInstrumentedTest` 3/0、`MistakePdfMissingSourceInstrumentedTest` 1/0（设备）。
**同批连带修复**：既有回归 `CaptureWorkspacePolicyTest` 的空白照片段落误放行（high，见批次记录 §8 收尾修复）。

## KD-53 (fixed 2026-10-20（批次 15 收尾：365MiB 校验+恢复 1/0；AUD-05 27/0 设备含缺资产拒恢复）；2026-10-18 登记（批次 13 设备实测；medium）) · 大库的自建备份不可校验/不可恢复：单条目解压上限 100MB < 库体积

**Symptom（设备实测）.** app 内创建完整备份 → UI 报「备份完成」（63,467,871 B）→ 同一文件走「校验一个备份文件」
报 `备份无效：database.sqlite 超过单条目解压大小上限`；`SmbkArchiveCodec.kt:113` 单条目解压上限 100 MB，
而该设备库 365,133,824 B（`VACUUM INTO` 快照）。⇒ **本设备上该备份不可校验、不可恢复**。

**Impact.** medium：大库（当前内容包 + 使用后）上，备份产出的是一个"自己都验不过"的文件——备份功能在大库
规模下实质失效；同时 AUD-05 的「缺引用资产」专用门在设备核里被这个更早的门遮挡（结果仍是交换前拒绝，
但验的不是目标门）。

**Fix direction（待设计）.** 上限与大库现实对齐（抬高上限并配资源预算），或改流式/分块解压校验
（压缩比与总量门保留）。见批次记录 §3.4。

**关闭记录（2026-10-20，批次 15 收尾实跑）.** 设备（emulator-5554，API 34）：
`BackupLargeDatabaseScaleInstrumentedTest`（365MiB 自建包）**1/0，39.045s**——备份→校验→恢复→新 Room 读回；
`BackupRestoreInstrumentedTest` **27/0**，其中 `restoreIsRejectedWhenTheArchivedDatabaseReferencesAMissingAsset`
真实触发 AUD-05 缺引用资产拒恢复门（交换前拒绝、原库/包外原图不动）。KD-56 已关闭，校验+恢复两侧均过。
批次 14 记录的解包前 free-space 预检缺口仍是已知低危，不影响本关闭条件。

**Reopen condition.** 大库备份再次出现校验/恢复失败时重开；关闭条件 = 大库（≥365MB 快照）的自建备份可在 app 内校验与恢复，
且 AUD-05 专用门在设备上被真实触发。

**2026-10-19 进展（批次 14 T4）**：上限已按条目类型分层（`database.sqlite` 1 GiB / 题图仍 100 MiB；总量 2 GiB、
压缩比 100 保留）——**校验侧设备实测关闭**（365,133,824 B 单条目过校验，「备份有效：4 个文件全部通过校验」）；
**恢复侧未达**：app 内两次恢复均以 main 线程 `SQLException: Connection pool is closed` 崩溃并启动回滚（独立缺陷
见 **KD-56**）。**新增低危**：解包先写盘、磁盘预检在后（staging 上限=总量门 2 GiB，恶意/损坏归档可先占掉约
2 GiB 剩余空间；压缩比门挡不住写盘量——`SmbkArchiveCodec.kt:124-145` + `AndroidBackupRepository.kt:337/361-395`），
待把 free-space 预检前移到解包前或给 staging 单独预算。

**2026-10-20 进展（批次 15 T1，未关账）**：本轨审计后**未改 codec**（DB 单条目 1 GiB、总量 2 GiB、压缩比 100、压缩归档 1 GiB 已覆盖大库；`SmbkArchiveCodec.kt:129/136/140/142`）。
恢复侧代码把“下一代的 365MB 拷贝 + 全量 integrity_check”前移到关库前（`AndroidBackupRepository.kt:529`），关库段只剩通知/有界宽限/终态/close/rename
（`:547-556`、`:565-621`），坏包在交换前拒绝。**但没有设备证据**：未在设备上用 ≥365MB 自建包完成“校验→恢复→重启→数据可读”；
KD-56 仍 open。故 KD-53 保持 open，不能据代码顺序改写关闭。

## KD-54 (fixed 2026-10-20（批次 15 收尾实跑：365MiB 设备对照无分钟级写饥饿，独立快照 2/0）；2026-10-18 登记（批次 13 设备实测；medium）) · 备份快照 VACUUM INTO 单语句占写连接 601s：备份期间其它写被饿死

**Symptom（设备实测）.** 备份快照 `VACUUM INTO` 单语句占写连接最长 **601.274s**、期间 40 条
`unable to grant a connection`（与 KD-51 同型、不同语句；KD-51 修的索引重建本身 0 条）——备份期间其它写
（采集提交等）被饿死。

**Impact.** medium：备份是用户主动动作但可能持续 10 分钟，期间 App 的写入路径全部排队（冷启动安装叠加时更糟）。

**Fix direction（待设计）.** 与 KD-51 同思路但对象是快照：`VACUUM INTO` 的原子性要求单语句，只能
① 让路（备份前把写队列排空 + 备份期间 UI 显式"备份中"并抑制其它写入口），或 ② 改分块 checkpoint 拷贝
（需自证一致性）。见批次记录 §3.4。

**关闭记录（2026-10-20，批次 15 收尾实跑）.** 设备（emulator-5554，API 34）：
`BackupLargeDatabaseScaleInstrumentedTest`（365MiB）在成包期间用写门持续做真实写事务——**0 次失败、最坏 <5s**，
大库备份→校验→恢复 39.045s 通过；`BackupCreateWriteGateInstrumentedTest` **2/0**：独立快照期间写入口直接放行、
`activity=Idle`、Room `checkpointForBackup`/`snapshotForBackup` 0 调用；回退路径仍可见 Snapshotting 并排队后放行。
独立快照引擎在 WAL 库上走 `OPEN_READONLY`/独立 RW 的 `VACUUM INTO`，不占 Room 单写连接。残余风险（非 WAL/回退时
checkpoint 结果行被忽略、写门仍只覆盖两仓库）另登记为 KD-62（low），不抵消关闭条件。

**Reopen condition.** 备份期间再次出现分钟级写饥饿时重开；关闭条件 = 备份期间无分钟级写饥饿（设备对照）。

**2026-10-19 进展（批次 14 T4）**：「让路」形态已落地（`BackupWriteGate` + 采集仓库 admittedWrite + 备份仓库自身），
但**覆盖只有两条仓库**：其余写路径（复习调度/知识写入/掌握度/导出记录/提醒协调等）仍直连 DAO——设备实测快照
期间仍有 **9 条 `unable to grant`、最坏 90.014s**（批 13 记录为 601s/40 条，量级下降但**分钟级饥饿仍在，不能关账**）。
附带两处文案/行为：孤立图清理 worker 在备份期间连吃 3 次 deferred 变 FAILED（界面「这次没清理完」）；
恢复撞上备份时文案「恢复失败…备份正在进行…」。待把写门覆盖到全部 DAO 直写入口（或把快照改成可让路的形态）。

**2026-10-20 进展（批次 15 T1，未关账）**：快照默认改走**独立于 Room 的 SQLite 连接** `VACUUM INTO`（`AndroidBackupRepository.kt:198`；
`FrameworkVacuumIntoSnapshotEngine` 从 `:1355` 起：只读优先、独立 RW 回退、要求 WAL、目标 `quick_check=ok` 且 `user_version` 与源一致）。
独立快照失败才回退旧路径，旧路径仍包在 `writeGate.runBackup` 全写者屏障里（`:205-212`）。JVM `BackupWriteGateRestoreLifecycleTest` 11/0（门禁 XML 02:17）。
**仍未达**：① 无设备对照，分钟级 `unable to grant` 是否消失未验证；② Android framework `SQLiteDatabase(OPEN_READONLY)` 上 `VACUUM INTO` 是否放行未实测
（只读失败会落独立 RW，仍非 Room 连接）；③ 写门仍只覆盖备份/采集两仓库，复习调度/知识写入/掌握度/导出/提醒等 DAO 直写未接入；
④ 旧回退的 `checkpointForBackup` 仍忽略 `wal_checkpoint(TRUNCATE)` 的结果行（`RoomBackupSupportStore.kt:15-19`），一致性依赖“备份期无写者”
（独立快照成功时不会走此路径，但回退时风险仍在）。KD-54 保持 open。

## KD-55 (fixed 2026-10-19（批次 14 T5 读后复核）/ 2026-10-23（批次 18 T8 有界读入）；2026-10-18 登记（批次 13 对抗性复核 finding 5；low）) · docx 嵌入图片直读 file:// 不再复核 sha/长度（与 PDF 侧同形的 TOCTOU）

**Symptom.** docx 渲染时图片字节直接从 `file://` URI 读取（`DocxImageSource.kt:26-45`），不再复核记录里的
`contentSha256`/`byteSize`——核对只在读详情时由 vault.resolve 做过（`AndroidCanonicalAssetVault.kt:273-281`），
存在「读详情 → 渲染」之间的 TOCTOU 窗口。与 PDF 侧同形（非本批新引入），docx 侧同样没有第二次核对。

**Impact.** low（窗口窄、需并发改文件）。

**Fix direction.** 渲染前按记录里的 sha/长度复查一次（或直接读 vault 的规范句柄）。

**关闭记录（2026-10-19，批次 14 T5）.** 已按 Fix direction 落地：`DocxImageSource` 新增记录 sha/长度复核
（`File.length()` 预检 + 读后同一份字节的 size+sha256 复核，不符按既有「读不出」口径省略图）；导出输入/批量
Image 块/卷面编辑/草稿 DTO 全链携带来源资产记录；用例覆盖匹配、长度不符、等长换字节（TOCTOU）。见批次记录
§2 T5。

**2026-10-23 批次 18 T8 加固（有界读入）.** 在批次 14 的读后复核之上，`DocxImageSource.read` 仍先按
`expectedByteSize` 预检 `File.length()`，但读取改为 `readBounded`：只读记录长度、再多探 1 字节；文件在
`stat` 与 `read` 之间变长时超出部分不会进入内存，变短或变大均返回 null（`DocxImageSource.kt:45-94`）；
随后用同一份即将嵌入的字节复核长度与 sha256。渲染器在写文件前先读取并校验全部图片，任一不符即
`GENERATED_PDF_INVALID`，不写半份文件（`DocxMistakeRenderer.kt:73-84`、`:116-120`；渲染器生产代码本批未改）。
用例：`DocxImageSourceTest` 8/0（含增长/缩短/长度不符）、`DocxMistakeRendererTest` 17/0（含仅长度不符不落盘）；
只读构建工件 XML 为 failures=0/errors=0（2026-10-24 02:41）。**本记录员未运行 Gradle**；Gradle 门禁结果仍以
批次材料为准。旧卷面草稿反序列化后 `expectedContentSha256/expectedByteSize` 缺省为 null 时仍回退
`file.readBytes()`（无上界、无 sha 复核），属已登记降级路径，不抵消主路径修复。

**Reopen condition.** n/a —— 登记性低危。

## KD-56 (fixed 2026-10-20（批次 15 收尾实跑：365MiB 设备恢复不崩、数据可读）；2026-10-19 登记（批次 14 设备实测；high）) · 大库恢复在 app 内崩溃：main 线程 `SQLException: Connection pool is closed` + 启动回滚

**Symptom（设备实测，两次复现）.** 批次 13 的自建备份（63,467,871 B；内库 365,133,824 B）在 app 内恢复：
第一次 04:26:33 FATAL EXCEPTION main `android.database.SQLException: Error code: 21, message: Connection pool is
closed`（`androidx.room3 PassthroughConnectionPool.useConnection`）→ 进程死亡；重启后启动对账回滚，横幅
「备份恢复没有完成／已回到恢复之前的数据」（诊断号 `startup:restore-reverted:815bed99-…`）；第二次重试同样崩溃
（`d73c2a84-…`）。校验侧已通过（见 KD-53 进展）。

**Impact.** high：大库恢复（本机真实规模）在设备上**不可用**——恢复是「换机/重装」的唯一通道；且崩溃发生在
main 线程（进程死亡），回滚语义正确但用户拿不到数据。

**归因边界（照实）**：批次 13 时代恢复在「单条目上限」门就被拒、从未走到交换路径——本形态是**首次在真实大库
上执行 swap** 时暴露；是否由批次 14 T4 的写门/让路改动引入，还是既有潜伏缺陷，尚未定因（两份诊断号里只有
`d73c2a84` 有三张截图佐证，`815bed99` 无落盘佐证——独立核对实测）。

**Fix direction（下一批首选）**：定位「恢复交换路径里谁在池关闭后仍持有连接」——沿 `AndroidBackupRepository`
的 swap/关库时序与 T4 新接入的写门（`BackupWriteGate`）交叉核对；补一条「大库恢复 → 不崩、数据可读」的设备级
用例。见批次记录 §3.3。

**2026-10-20 进展（批次 15 T1，未关账，P0 仍 open）**：恢复顺序已重排：关库前对**同一份将晋升的字节**跑全量 `integrity_check`
（`AndroidBackupRepository.kt:529`）；`admission.beginCommit()` 排空/挡写（`:542`）；先 `restartNotifier` → 有界宽限（默认 750ms，`:108`）
→ `markDatabaseClosedForRestart` → `database.close()`（`:547-556`）；关库后的 rename/fsync/journal 段整体 `NonCancellable`（`:565-621`），
关库后只做 `verifySwappedDatabaseShape`（`:600`），不再调用注入端口。写门新增终态 `markDatabaseClosedForRestart`，终态后 `withWriteAdmission`
立即抛 `BackupWriteDeferredException`（`BackupWriteGate.kt:358/251`）；生产已在 `SmartMistakeBookApplication.kt:467` 接 `onRestartRequired=::requireDatabaseRestart`。
JVM `BackupWriteGateRestoreLifecycleTest` 11/0（门禁 XML 02:17）；仪器化 `BackupRestoreLifecycleInstrumentedTest`（关库后主线程查库必须报
“Connection pool is closed”、关库前已通知、关库后端口零调用、坏包交换前失败）与 `BackupLargeDatabaseScaleInstrumentedTest`（默认 365MiB）**均已写未跑**。
**仍未达**：无设备/强门证据；750ms 只是启发式宽限，未接入写门的 DAO 直写与应用级 collector 仍可能命中已关闭的池；`WorkManager.cancelAllWork()`
是 best-effort。关闭条件“大库自建备份在设备上完成恢复且不崩、数据可读”未取得，故保持 open。见批次记录 §2.1/§4。

**关闭记录（2026-10-20，批次 15 收尾实跑）.** 设备（emulator-5554，API 34）跑
`BackupLargeDatabaseScaleInstrumentedTest`（365MiB 造数）：**1/0，39.045s**——备份→校验→恢复→新 Room 读回，
恢复后数据可读、进程不崩；同日 `BackupRestoreLifecycleInstrumentedTest` 3/0（关库前已通知重启、关库后端口零调用、
写门终态、坏包交换前失败不关库）。生产 `SmartMistakeBookApplication.kt:467` 已接
`onRestartRequired = ::requireDatabaseRestart`。残余风险（750ms 启发式宽限、未接入写门的 DAO 直写/应用级 collector）
另登记为 KD-61（low），不抵消关闭条件。

**Reopen condition.** 再次出现大库恢复崩溃/回滚时重开；关闭条件 = 大库自建备份在设备上完成恢复且不崩（与 KD-53 关闭条件同批）。

## KD-57 (fixed 2026-10-20（批次 15 收尾实跑：设备 8/0，重试成功 + Expired 可达）；2026-10-19 登记（批次 14 对抗性复核；medium）) · 分享接收「再试一次」必然失败：授权在 finally 里被无条件撤销，Expired 分支不可达

**Symptom（读码）.** `CaptureShareIntake` 的 `finally` 无条件 `revokeReadGrant` 掉本次分享的全部 URI 授权
（`CaptureShareIntake.kt:57-59`），而「再试一次」只对 `UNREADABLE`/`IMPORT_FAILED` 开放（`CaptureSharePolicy.kt:179-180`）
——第二次尝试必然再失败且理由变成「文件可能已损坏或没有读取权限」；另外进程/配置重建时 `pendingSharedCapture`
由 `rememberSaveable` 恢复成非空（`SmartMistakeBookRoot.kt:228-230`），为「授权已随进程消失」准备的
`CaptureShareRouteState.Expired` 分支（要求 request==null，`CaptureShareIntakeRoute.kt:146-153`）几乎不可达，
用户拿到的是 UNREADABLE 这句错话。

**Impact.** medium：失败态出口不可用（重试死路 + 文案误因）。

**Fix direction.** 失败路径不撤授权（成功才撤），或把 Expired 判据改为「恢复态即过期」；补失败重试用例。

**2026-10-20 进展（批次 15 T7，未关账）**：`CaptureShareIntake.kt:49-77` 不再在 `finally` 无条件撤销，成功才 `releaseReadGrants`；
`:84-86` 提供明确放弃入口；可重试失败保留授权，`SecurityException` 归类 `AUTHORIZATION_EXPIRED`。`CaptureSharePolicy.kt:69/186/194`
新增独立 `AUTHORIZATION_EXPIRED` 理由与文案；`:204/213-219` 定义 10 分钟接收窗口与纯函数（deadline 即过期、时钟回拨不误判、非正窗口立即过期）。
`CaptureShareIntakeRoute.kt:91-100` 由 `request==null || expiredByResult || 超时` 派生 Expired，`:105-112` 到 deadline 主动翻过期，
`:116-154` 每次尝试前校验窗口，`:159-167` 仅手动录入/关闭/返回释放授权；系统返回键已接 `BackHandler`（`:180`）。
JVM `CaptureShareAuthorizationPolicyTest` 6/0（门禁 XML 02:14）。`CaptureShareAuthorizationInstrumentedTest`（4 例）、
`CaptureShareIntakeRouteInstrumentedTest`（4 例）与 `SharedCaptureEntryInstrumentedTest.kt:148` 全链用例**均已写未跑**。
**仍未达**：无设备证据证明“授权有效时再试一次真的成功”“授权失效走 Expired”；10 分钟窗口是 T7 自选值（计划未给数字，待产品确认）；
`importPdf` 对 `createPdfImport` 的 `SecurityException` 仍统一映射 `IMPORT_FAILED`（低危）。故保持 open。见批次记录 §2.7/§6。

**关闭记录（2026-10-20，批次 15 收尾实跑）.** 设备（emulator-5554，API 34）：
`CaptureShareAuthorizationInstrumentedTest` + `CaptureShareIntakeRouteInstrumentedTest` **8/0**——授权有效时同 URI
“再试一次”真的成功并随后撤销；越过窗口/读取被拒→Expired；明确放弃才撤权；PDF 授权过期不再误报 IMPORT_FAILED。
`SharedCaptureEntryInstrumentedTest` 全链（分享→可重试失败→写回源文件→再试一次→进入录入并新增草稿）18/0 批次内通过。
10 分钟窗口是 T7 自选产品值，未由用户逐条确认；若要调整只改
`CAPTURE_SHARE_AUTHORIZATION_TIMEOUT_MILLIS`，不影响关闭条件。

**Reopen condition.** 分享重试再次变成死路或 Expired 不可达时重开；关闭条件 = 「再试一次」在授权仍有效时真的能重试成功 + Expired 可达。

## KD-58 (fixed 2026-10-20（批次 15 收尾：core:ui 41/0 含卡片用例 + 生产端口接线）；2026-10-19 登记（批次 14 对抗性复核；medium）) · 旧 attachedImages 兼容链静默失败：无预留重载默认抛、异常被吞成 null

**Symptom（读码）.** 模型输出 `attachedImages` 的历史 resolver 走无预留旧重载
（`AttachedImageGeneratorFactory.kt:48` 的 `channel.generate(request)`），而生产通道不覆盖该重载 → 默认实现抛
`ImageDispatchNotReservedException`（`ImageGenerationChannel.kt:62-73`），被 `resolveOne` 的
`catch(Exception){ null }` 吞掉（`AttachedImageGenerator.kt:88-90`）——用户只看到少一张图、没有原因。
方向（WG-05 历史 resolver cache-only）是有意的，但「缓存未命中=静默不可用」这一档没有可读理由，也没有用例点名
（单测替身仍覆盖旧重载所以测试是绿的）。

**Impact.** medium：历史消息缺图不可解释；一旦新实现者沿用旧重载，预留防线也被绕过（见 KD-59）。

**Fix direction.** 缓存未命中给显式「可重新生成」动作与文案（WG-05 落地时一并做）；补一条点名用例。

**2026-10-20 进展（批次 15 T6，部分达成，保持 open）**：历史配图读取改为 cache-only（`AttachedImageGeneratorFactory.kt:32-49`），
缓存未命中/损坏/缺源/读失败返回可读 `AttachedImageResolution`（`AttachedImageGenerator.kt:194-227`）；显式重生成绑定原消息
`parentRequestId` 并走共享 coordinator/账本（`:96-192`）。生产 resolver `AttachedImageRuntimeResolver`（`AttachedImageGeneratorFactory.kt:119-139`）
同时实现旧 `String?` 函数与 `AttachedImageResolutionPort`；`SmartMistakeBookApplication.kt:895-907` 返回该对象，Root `:1009`/Destinations `:403`
传入路由，`TutorChatConversation.kt:328-365` 做端口检测后给卡片真实原因与“重新生成”。`AttachedImageCard.kt:59-145` 有未就绪/原因/动作文案。
JVM `AttachedImageGeneratorTest` 12/0、`AttachedImageRegenerationWiringTest` 3/0（core:data 02:17）。
**关闭记录（2026-10-20，批次 15 收尾实跑）.** 单测门含 `:core:ui:testDebugUnitTest` 后 **41/0**（其中
`AttachedImageCardTest` 8 例覆盖未就绪折叠提示、原因文案、“重新生成”动作、损坏/缺源不提供盲目重生成）；
core:data `AttachedImageGeneratorTest` 12/0、`AttachedImageRegenerationWiringTest` 3/0；生产 resolver 对象
`AttachedImageRuntimeResolver` 同时实现旧函数与 `AttachedImageResolutionPort`，Application/Root/Destinations 传入，
`TutorChatConversation` 端口检测后给真实原因与显式重生成。缓存未命中不再静默、WG-05 接线落地，关闭条件满足。
未做设备点击/真实 Provider 质量验证，记录为未覆盖项。

**Reopen condition.** 历史配图再次静默缺图或“重新生成”入口在真机不可达时重开。

## KD-59 (fixed 2026-10-20，批次 15 T6（代码级 + JVM）；2026-10-19 登记（批次 14 对抗性复核；low）) · 「无预留→0 HTTP」在接口层偏弱：旧重载可被覆盖、binding 公开可自造

**Symptom（读码）.** `ImageGenerationChannel` 带 dispatch 的默认实现只核 sha/非空 id/可选配置指纹，然后委托给
**可被实现者覆盖的旧重载**，且委托时 `configurationFingerprint=null`（跳过配置绑定复核，
`ImageGenerationChannel.kt:29-54`）；`FigureImageDispatchBinding` 是公开类、构造器公开，任何调用方都能自造一个
`method=IMAGE_MODEL`、sourceSha 匹配的 binding。今天没有生产实现者覆盖旧重载（不构成可复现旁路），但测试替身
正是以旧方式实现——这条路一旦被新实现者沿用就会重新打开。

**Impact.** low（当前无可复现旁路；防线强度依赖实现者自律）。

**Fix direction.** 旧重载改为 `@Deprecated(level=ERROR)` 或收窄可见性；binding 构造器 internal 化 + 测试工厂。

**关闭记录（2026-10-20，批次 15 T6；代码级 + JVM）.** 按 Fix direction 落地：`ImageGenerationChannel.kt:72/83` 两个旧无预留重载
`@Deprecated(level = DeprecationLevel.ERROR)`；`FigureGenerationCoordinator.kt:41` `FigureImageDispatchBinding` 构造器 `internal`，
`:77` 生产唯一入口 `fromReservation`，`:94` 测试工厂 `forTest` 加 `@RequiresOptIn(FigureDispatchBindingTestFactory, ERROR)`。
`ImageDispatchReservationGuardTest` 6/0（core:data 02:17）覆盖：旧重载为 ERROR、binding internal/`fromReservation`/`forTest`、只实现旧重载的替身在 dispatch 入口
fail-closed 且旧方法 0 调用、生产源码无未带 dispatch 的 channel 调用。设备无需（接口硬化）。见批次记录 §2.6。

**Reopen condition.** n/a —— 登记性低危。

## KD-60 (fixed 2026-10-20，批次 15 T6（采用“分列 + 诊断说清”分支；代码级 + JVM）；2026-10-19 登记（批次 14 对抗性复核；low）) · 图像预留与讲题工具环共享同一派发预算：先烧工具轮会让配图被判 BUDGET_EXHAUSTED

**Symptom（读码）.** T1 的图像预留与讲题工具环共用同一 `model_task_operation.dispatch_count`：
`reservedLimit=MAX_DISPATCHES-1=5`（留终答位，`RoomFigureDispatchLedger.kt:41-97` + `ModelTaskTransactionDao.kt:342-345`），
而工具环自己用 6 预留。一个逻辑操作里先烧掉几轮工具后，最后一次配图会被判 `BUDGET_EXHAUSTED`，工具结果只说
「配图没有生成成功」，真实原因是共享预算被聊天轮吃掉；入口声明的 `FigureGenerationBudget(remainingRemoteDispatches=6)`
是常量、不反映 durable 余量（policy 的预算门只是装饰，真门是账本）。

**Impact.** low-中：配图在长工具环里可能被预算判死且理由不可读；预算语义与文档不一致。

**Fix direction.** 预算声明从账本读实余量（或把配图预算与聊天轮预算分列并在诊断里说清）。

**关闭记录（2026-10-20，批次 15 T6；采用计划允许的“分列并在诊断说清”分支）.** `FigureGenerationBudgets.kt:18-24` 的三入口共用声明只写
“上限 = `MAX_DISPATCHES`、与工具轮共享、余量=未预读（null）”，不再用常量 6 冒充余额；真门仍是 durable `model_task_operation.dispatch_count`。
`RoomFigureDispatchLedger.kt:65-91` 的拒绝消息带真实已派发次数、可用上限、是否为终答保留 1 次、共享预算与“未出网”；
`FigurePromptAssembler.kt:164-180` 在提示词里分列“上限/共享/未预读/终答”。`FigureGenerationBudgetDeclarationTest` 4/0（core:data 02:17）
覆盖声明余量 null、6 只是上限、prompt 分列、账本拒绝含“已派发 5/共享/终答/未出网”。未新增从账本预读余量的读口（需改 RoomStudyDatabase/新端口，属他线禁改面）。
见批次记录 §2.6。

**Reopen condition.** n/a —— 登记性低危。

## KD-61 (open, 2026-10-20 登记（批次 15 收尾复核；low）) · 恢复关库前的 750ms 宽限是启发式，未接入写门的 DAO 直写/应用级 collector 仍可能命中已关闭连接池

**Symptom（读码 + 设备证据边界）.** KD-56 已关闭（365MiB 设备恢复不崩），但关库前只给
`observerQuiesceMillis=750ms` 的 best-effort 宽限（`AndroidBackupRepository.kt:108/547-556`）；`WorkManager.cancelAllWork()`
也是 best-effort。写门只覆盖备份与采集两条仓库（`grep withWriteAdmission` 仅 `AndroidBackupRepository.kt:153`、
`RoomCaptureWorkflowRepository.kt:154`），复习调度/知识写入/掌握度/导出/提醒等 DAO 直写与应用级 collector 未接入。
设备大库用例的并发写走写门，因此没有覆盖这条旁路。

**Impact.** low（窗口窄、需恰好在关库瞬间有非门控写；设备大库用例未复现崩溃）。

**Fix direction.** 把写门覆盖到全部写入口，或在关库前做硬性 quiesce（停观察者/worker + 等所有连接归还）并补设备用例。

**Reopen condition.** 再次出现“关库后仍查询”的崩溃/错误时按本条继续；关闭条件 = 非门控写入口在关库前被硬性排空或证明不可达。

**2026-10-21 批次 16 再核**：T7 只做了 KD-62 的 ①+②（见下条），**写门覆盖面未扩大**；KD-62③ 仍归本条。
本条保持 open，无新增证据；设备恢复/并发写未跑。

**2026-10-22 批次 17 再核（只收窄/反证，不关闭）**：写门覆盖面**仍未扩大**——`grep -rn withWriteAdmission --include=*.kt core feature app` 的
生产代码命中仍只有 `AndroidBackupRepository.kt:153`（清理孤立题图）与 `RoomCaptureWorkflowRepository.kt:166`（采集写入口；行号随他线编辑从 154 移到 166）；
复习调度/知识写入/掌握度/导出/提醒仍是 DAO 直写。本批反而**新增了不经写门的直写面**：图生成 operation/attempt 审计
（`FigureGenerationRecorderPort` → `RoomStudyDatabase` → `FigureGenerationDao`）与启动僵尸对账
（`FigureGenerationReconcileDao`，入口 `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/FigureGenerationReconciler.kt:51`）都在写门之外；
显式重绘的任务行/终态写同样直写。设备侧 D1 自报 `BackupRestoreInstrumentedTest` 32/32、`BackupLargeDatabaseScaleInstrumentedTest` 1/1（365MiB）通过，
但大库用例的并发写显式走 `gate.withWriteAdmission("设备大库写入口")`
（`core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/BackupLargeDatabaseScaleInstrumentedTest.kt:104`），仍不覆盖上述旁路。
故本条保持 **open**，且旁路集合比批次 16 更大；关闭条件不变（非门控写入口在关库前被硬性排空或证明不可达）。

**2026-10-24 批次 19 T3 处置（部分接入 + 硬性 quiesce + 旁路清单登记；保持 open）**：

现状核实（`grep withWriteAdmission` 生产代码命中，批次 19 T3 实测）：仍是两仓库——
`AndroidBackupRepository.kt`（清理孤立题图，`admittedWrite` 语义）与 `RoomCaptureWorkflowRepository.kt`
（采集写入口 `admittedWrite`，全部保存/切分/提交/挂接走它；`resumePendingCleanRedraws` 除外，见下）。
恢复关库窗口 = `beginCommit()` 排空接门写者 → restartNotifier → 750ms 观察者宽限 →
`markDatabaseClosedForRestart()` → `database.close()`（`AndroidBackupRepository.kt` 交换段）。

本批三项改动（先读码核实、测试先行；本轨不跑 Gradle，测试由门禁轨执行）：
1. **图生成僵尸对账接入写门**：`FigureGenerationReconciler` 构造注入 `BackupWriteGate.shared`，
   对账写包进 `withWriteAdmission("图生成僵尸对账")`——批次 17 点名的旁路之一关闭（恢复交换段它被拒、
   关库终态立即失败，不再撞 code 21 / 写进将被换掉的旧库）。JVM 用例：
   `FigureGenerationReconcilerTest`（恢复交换段被拒不写 / 终态立即失败 / 空闲门直通）。
2. **关库前硬性 quiesce 钩子**：`AndroidBackupRepository` 新增 `backgroundQuiesce`（默认 null=行为不变），
   在 `beginCommit` 之后、750ms 宽限与关库之前调用；`deleteAllData` 同一位置对称接入。
   生产装配（`SmartMistakeBookApplication`）传「取消整个 applicationScope + 限时 2s join 全部子协程」
   （`BackgroundWriterQuiesce.kt`）：协程取消是协作式的，在途 DAO 写把当前 SQL 跑完再退出，join 等
   「最后一笔归还连接」——把"关库瞬间仍有应用级 collector 带着在途 SQL"与"关库后 collector 再发起写"
   两条都关掉。钩子抛异常中止恢复且**不关库**（交换前失败分支，写门回 Idle、活库可读）。
   配套：四个 DataStore 工厂（调度设置/提醒偏好/睡眠日志/模型配置）换到独立 `dataStoreScope`——
   DataStore 不进 room3 连接池、不在写入口面里，且恢复 Phase 8 要往它写回设置；留在 applicationScope
   会让 actor 随 quiesce 取消（`edit` 撞关闭通道 → 设置恢复降级 WRITE_FAILED，超时路径甚至挂死）。
   用例：`BackgroundWriterQuiesceTest`（app JVM，在途写先于返回完成/挂死者超时放弃/quiesce 后新 launch
   no-op/空 scope 立即返回）、`RestoreBackgroundQuiesceInstrumentedTest`（core:data androidTest，
   quiesce 位于 notify 与 close 之间且门未终态；钩子抛异常→不关库、门回 Idle、活库可读）。
3. **旁路清单（未接门、未排空，登记为残余）**——按入口家族：
   - 热区文件（本批禁改，接门须改调用方）：BatchImport/SplitImport 全族（`BatchImportDriverWorker`
     → `drivePendingImports`；启动两笔对账 `reconcileStuckJobs`/`reconcileCommittedQuestionDrafts`）、
     `MistakeExportJobRunner`（`ExportPdfWorker` 的 recordSucceeded/Failed + 启动僵尸对账）、
     `RoomMistakeDetailRepository`、`RoomTutorToolRunner`（含 `TutorConversationDao` 全部写方）、
     `CleanRedrawTaskPort` 实现
     （`resumePendingCleanRedraws` 的任务行领取/终态写、显式重绘续跑）、`ReviewReminderCoordinator`、
     knowledge 证据/测验写（`KnowledgeEvidenceWriter`/`KnowledgeQuizFeedbackWriter`）、
     `RoomModelTaskToolRounds`、`RoomAgentPendingRequestRepository`（create/resolve 两写口）。
     其中**应用级 collector 已被 ② 的 quiesce 覆盖**（它们全部 `applicationScope.launch`：两笔切分对账、
     净化/显式重绘续跑、导出僵尸对账、知识包安装、学习库初始化/FSRS——取消+join 后不再发起新写）；
     残余是**用户动作路径**（恢复窗口内学生仍可操作直至重启横幅接管）与下一条。
   - WorkManager worker 在途 SQL：`cancelAllWork()` 只取消不 join——worker 已开始的语句仍可能与
     close() 竞态（原生层）。750ms 宽限部分吸收。
   - UI（viewModelScope/Compose）写：未接门也不在 applicationScope——继续靠重启横幅 + 750ms 启发宽限。
   - quiesce 超时路径：挂死（不吃取消）的 collector 会让 2s 超时后照常关库（如实记日志），该在途写仍属残余。
   状态：**open（收窄）**。关闭条件不变：非门控写入口在关库前被硬性排空或证明不可达。
   未跑项：本轨未执行任何 Gradle 测试（门禁轨 P5/P6/P7 跑单测/androidTest 编译/设备）；设备端
   D1 换机演练将首次以真实 collector 在飞的状态走恢复全链。

## KD-62 (fixed ①+② 2026-10-21（批次 16 T7；RoomBackupCheckpointValidationTest 5/0、BackupTempArtifactCleanupTest 2/0；③ 保留 KD-61）；2026-10-20 登记（批次 15 收尾复核；low）) · 备份回退与清理残余：checkpoint 结果行被忽略、成包临时库无启动清理、写门只覆盖两仓库

**Symptom（读码）.** ① 旧回退的 `checkpointForBackup` 消费 `wal_checkpoint(TRUNCATE)` 结果行后不校验是否真的完成
（`RoomBackupSupportStore.kt:15-19`），非 WAL/长写者场景一致性依赖“备份期无写者”；② 进程在成包/校验中途被杀时，
`databases/backup-snapshot-*.db` 与 `cache/backup-snapshots/*` 没有启动清理（`RestoreRecoveryCoordinator.cleanupOrphans`
只删 `restore-*`/`restore-rollback-*` 与隔离区），会长期占用空间；③ 写门仍只覆盖备份/采集两仓库（同 KD-61）。

**Impact.** low（独立快照成功时通常不走回退；清理缺口影响磁盘空间）。

**Fix direction.** 校验 checkpoint 的实际完成状态；启动清理覆盖成包临时库/目录；写门覆盖或明确边界。

**2026-10-21 收尾（批次 16 T7；单元门通过）.** ① 已关闭：`RoomBackupSupportStore.kt:15-37` 读取
`PRAGMA wal_checkpoint(TRUNCATE)` 的 busy/log/checkpointed 三列，`checkpointCompletionProblem`（`:155-184`）判定未完成即
`error(...)` 中止备份；非 WAL 的 `-1/-1` 视为合法 no-op。② 已关闭：`RestoreRecoveryCoordinator.kt:370-373` 在启动
`cleanupOrphans` 调用 `cleanupOrphanBackupArtifacts`（`:400-416`），删除 `cache/backup-snapshots/` 与
`databases/backup-snapshot-*.db`（含 `-wal/-shm`），不碰正式库/`restore-*`。③ **未动**：写门仍只覆盖备份/采集两仓库，
归 KD-61。单元门 11 模块 3,029/0 中 `RoomBackupCheckpointValidationTest` 5/0、`BackupTempArtifactCleanupTest` 2/0 通过。
①+② 关闭，③ 保留 KD-61。

**Reopen condition.** n/a —— 登记性低危；出现回退一致性故障或磁盘被临时库占满时升级处置。

## KD-63 (open, 2026-10-24 登记（批次 19 P8 复核发现；low）) · deleteAllData 清「待重建」标记的时机在第 10 步，留窄崩溃窗口；S3 重查 finally 无条件删 staged 边车未注明防御性质

**Symptom（读码 + 复核对拍）.** ① `AndroidBackupRepository.deleteAllData` 的 try/finally（`finally { admission.endRestore() }`）
在 `deleteAllNonDatabaseFiles` 调用之前结束；标记过滤在 `deleteAllNonDatabaseFiles` 第 10 步（~:1028-1040，
`it.name == RestoreIndexRebuildMarker.FILE_NAME`，批次 19 T2-3 新增）。此时库文件已删、写门已 endRestore 重放行——
若进程在「库已删、标记未删」窗口崩溃，残留 `restore-index-rebuild-pending.json` 会让下次启动把空库当恢复后待重建
重跑（`RestoreIndexRebuild.kt:202-210` 仅 failed.isEmpty() 时 clear；`:213-220` 失败重写标记），失败科目每启动重试
（正是该改动注释自己描述的失败形态）。配套断言 `BackupRestoreInstrumentedTest.deleteAllDataClearsThePendingRestoreIndexRebuildMarker`
（:125-161，设备 56/56 中已过，归门禁）。② `RestoreSoftReferenceRecheck` 的 try/finally
（`finally { 删 "$path-wal"/"$path-shm" }`，:192）无条件删除 staged 库边车——数据结局与旧代码相同
（恢复 Phase 3 本来只 `copyTo` 主文件，WAL 帧进不了 next 代），**不是新数据丢失模式**，但属防御性清理而未注明。

**Impact.** low（窗口窄：需恰在库已删、标记未删之间崩溃；后果是空库上的一次无意义重建重试，不丢数据）。

**Fix direction.** ① 把标记删除提前到不可逆段（关库删库）之前，或在协调器消费标记时校验「库非空/版本匹配」再重建；
② 在 `RestoreSoftReferenceRecheck` finally 注明防御性清理语义（或仅在确认边车为本阶段自建时删除）。

**Reopen condition.** 出现「全清数据后每次启动重跑派生索引重建」类现象时按本条处置；修复后以崩溃注入或顺序断言关闭。

## KD-64 (fixed 2026-10-26 批次 21 T1 域层修复 + 设备复验 3/3) · NewIntroductionPolicy 全超尺寸死锁分支 granted slice 可为负 → IllegalArgumentException，3E 排计划在「全部候选时长 > 当日时间预算」配置下崩溃

**Symptom（设备实跑 + 源码推导）.** 批次 20 T10 设备用例 `StudyReviewPlanner3EScanInstrumentedTest`（真 Room 种子库直跑 `StudyReviewPlannerService.createReviewPlan`）2/3 红：`java.lang.IllegalArgumentException: Granted slice must not be negative`，栈 `NewIntroductionPolicy$IntakeDecision.<init>(NewIntroductionPolicy.kt:68)` ← `decide(:151)` ← `StudyReviewPlannerService.createReviewPlan(:435)`。机制（基线 §8.3 读码证明）：`NewIntroductionPolicy.kt:68` `require(grantedSliceSeconds >= 0)`；`:154` 附近 `grantedSliceSeconds = effectiveSlice - remaining`；全超尺寸死锁分支（`:122-129`）中 sliceBudget = topDuration.coerceAtMost(timeBudgetSeconds) 而 capBudget = topDuration 无上界（`:52-53` 仅 require>0）——全部候选 `estimatedDurationSeconds > timeBudgetSeconds` 时全数 deferred、remaining 停在 timeBudget > effectiveSlice → granted < 0。具体触发例：timeBudget=300、单候选 duration=600 → granted = effectiveSlice−300 < 0。JVM 对照 `StudyReviewPlannerExamWindowTest` 6/0/0 绿 = 该形状 JVM 未覆盖。两文件均 git 干净（HEAD 态），非本批引入。

**Impact.** 中（合法配置「单候选时长超过当日时间预算」下计划创建直接崩溃；设备已复现 2 例；JVM 门未覆盖该分支，回归面前裸）。

**Fix direction.** 域层修复（不在批次 20 设备轨授权内，待实施轨）：全超尺寸死锁分支对 granted 做 coerceAtLeast(0) 或在死锁分支给 top 候选保底切片并如实记 deferred；时长上界（`:52-53`）按产品口径另裁。补 JVM 用例覆盖「全候选超尺寸」形状后再清。

**Reopen condition.** 设备/JVM 任一「全候选 estimatedDurationSeconds > timeBudgetSeconds」用例红即按本条处置；修复后 T10 两红例转绿 + 新 JVM 形状用例全绿关闭。

**Verified fixed（2026-10-26，批次 21 T1 域层修复）.** 修复 = granted 记账公式改为引入候选真实时长之和 `introduced.sumOf { it.estimatedDurationSeconds }`（`NewIntroductionPolicy.kt:181`），不再用只在常规路径成立的恒等式 `effectiveSlice − remaining`——死锁分支 `remaining` 从扩大的 sliceBudget 起算，旧公式在该分支既可报负（本条崩溃本体）也可低估（top 可入死锁形状实费 250s 只报 100s）。语义取向（两案对拍后裁定）：采纳推荐案「granted 钳到零」的忠实实现——全超预算候选如实 deferred、该轮 granted = 0（对齐 `previewBacklog` 的「超全预算 = 专项会话、非日引入项」语义）；否决「timeBudget 全给 top、当日不引入新卡」——top > timeBudget 时照样装不下，对崩溃形状无效，且会冻结本可引入的伴候选、重造死锁保护要消灭的永久滞留，还改动了引入集合（排程语义）超出缺陷本体。introduced/deferred 划分逻辑零改动；伴候选仍可经扩片引入（T10 用例「全超尺寸死锁保护会把它当日引入」注释所依赖的行为保持），如按真实时长记账。修复后锚点：IntakeDecision require `:80`、死锁分支 `:143-152`（KD-64 注释 `:135` 起）、granted 公式 `:181`。证据：JVM 直测红→绿（kotlin-compiler-embeddable 2.3.21 直编 + JUnitCore 直跑，非 Gradle）——修复前 19 例 3 红（两例 `IllegalArgumentException: Granted slice must not be negative` = T10 同构形状 timeBudget=300/单候选 600 及伴候选形状 600/150，一例记账断言 250≠100）；修复后 19/19 绿，含既有 granted 精确断言（180 / ≥300 / ≤600 / 90）全部不变 = 常规路径取值逐位不变。**设备复验（2026-10-26 批次 21 设备轨）**：`StudyReviewPlanner3EScanInstrumentedTest` → `OK (3 tests)`、`AM_EXIT=0`（`build/device-track-batch21/step1-kd64-run1.log`）——T10 两红例转绿。JVM `NewIntroductionPolicyTest` 13/13（XML `tests="13" failures="0" errors="0"`，2026-10-26 05:20）。门禁 `:core:domain:test`/强门由主会话脚本执行并报 `exit=0`（工单转录；本记录员未跑 Gradle）；批次 19 `StudyReviewPlannerExamWindowTest` 六例不回退由门禁覆盖。**本条关闭。**

## KD-65 (open, 2026-10-25 登记（批次 20 复核发现 #10；低）) · OpenAiModelResponseParsers KDoc 与白名单不符：「purpose」实际在 FIGURE_GENERATION_WIRE_KEYS 内且另有值校验

**Symptom（读码对拍）.** `OpenAiModelResponseParsers.kt` 的 `toCapturedFigureProposal` KDoc 称 policyVersion 与 purpose「故意不在白名单里」，但 `FIGURE_GENERATION_WIRE_KEYS` 实际包含 `"purpose"`（decision/purpose/method/reason/fidelity/constraints/program/imageOperation/imagePrompt），且代码另做 `wirePurpose != SOURCE_TRANSCRIPTION` 即抛的值校验——行为仍是 fail-closed（错误 purpose 整轮拒），只是注释表述与集合成员不符。

**Impact.** low（纯文档性；行为正确且 fail-closed，但误导后续维护者对白名单边界的判断）。

**Fix direction.** 改 KDoc 使其与集合成员一致（说明 purpose 在白名单内、另有取值校验拒绝模型回填）；不改行为。

**Reopen condition.** 若后续改动把 purpose 值校验放松或白名单语义变化时按本条重审。

## KD-66 (closed 2026-10-27（批次 26 关闭：hard+500 ms 设备门三轮独立绿 E-01/E-02 + 记录回链，批次 26 记录 §3.5/§4）；2026-10-26 登记（批次 21 T3/复核发现 #1/#13/#21；高）) · QuickJS issue #240 使引擎硬期限不能返回：`execute()` 永不返回、`close()` 卡死、`inFlight` 不复位

**Symptom（设备实跑 + 源码/上游源码核对）.** quickjs-kt-android 1.0.15（钉 commit `a081755`）的 issue #240：脚本用一个 job 先 `Promise.resolve().then(f)` 排队下一个再自旋时，`evaluate()` 在 `awaitEvaluateResult` 的排水循环内持 `jsMutex` 永不返回，另一线程的 `close()` 也抢同一把锁卡死；`evaluationTimeoutMillis`/`interruptEvaluation()` 都不能让队列排空。批次 21 设备轨真跑：单独 `issue240...` 用例 `timeout 60` → `Terminated`、`AM_EXIT=124`（`build/device-track-batch21/step2-quickjs-issue240-real.log`）；类级运行 7 个点后无 summary，线程栈显示 `quickjs-visual-js` 卡在 `executePendingJob←awaitEvaluateResult←evalAndAwait`、`quickjs-visual-force-close` 卡在 `QuickJs.close→yieldThread`（`step2-thread-dump.txt:458-560`）。`QuickJsVisualExecutionEngine` 的 `withTimeout(hard+500ms)` 无法抢占 `withContext(jsDispatcher)` 内的同步 JNI；`finally` 不执行 → `inFlight=true` 永久，后续 `execute()` 只回 BUSY（T6 已把 `inFlight` 改为在飞事务指针；#240 下 `finally` 仍不执行，指针同样不复位，行为不变）。

**Impact.** high（调用方拿不到 1500 ms 硬上界；任何不可信图程序可拖死一个引擎实例。批次 22 T1 已落 L2 进程级硬终止代码；批次 23 设备轨已验证 4/5 路径（隔离往返、watchdog 硬杀、死亡后重建均绿）。批次 24 已修 `abandon()` 死亡确认与冷启动首连预算的代码缺陷，设备轨 E-01/E-01b 在 emulator-5554 上 **6/6**（两代 debug APK，见下方批次 24 设备轨 Progress）；关闭门（hard+500 ms）仍未达成，故本条维持 open）。

**Progress（2026-10-26 批次 22 T1，app 模块 + Application guard；非 Gradle、非设备）.** L2（唯一进程级硬终止层）代码已落，**KD-66 保持 open：关闭门未达成，JS 仍不可用/不广告**（设备执行见下方批次 23 Progress）。

- **落点**：`app/src/main/AndroidManifest.xml` 注册非导出 `VisualJsService`（`android:isolatedProcess="true"`、`android:process=":visual_js"`，无 intent-filter）；`app/src/main/aidl/.../visualjs/IVisualJsService.aidl`（`initialize`/`execute`/`terminate`/`protocolVersion`/`workerUid`/`currentGeneration`，源码与 JSON 走 `ParcelFileDescriptor` 有界管道）与 `IVisualJsCallback.aidl`（`oneway` 结果/致命回调）；`app/src/main/kotlin/.../visualjs/VisualJsService.kt` 的独立 watchdog（`ScheduledThreadPoolExecutor` + 令牌 CAS + `Process.killProcess(Process.myPid())`）、`terminate(generation)` 的 generation 核对与过期 no-op、正常完成先失效令牌、宿主 Binder 死亡观察；`VisualJsClientEngine.kt`（L3）实现 `VisualExecutionEngine` 端口。
- **启动隔离**：`SmartMistakeBookApplication.onCreate` 在 `super.onCreate()` 后、任何初始化副作用之前用 `Process.myUid() != applicationInfo.uid`（API 28+ 叠加 `Process.isIsolated()`）短路，位置先于 Room/备份/维护/Provider；构造期字段审计结论（`by lazy` 不触达、协程作用域只分配）写在代码注释；**ContentProvider/AndroidX Startup 实际装配面未 `dumpsys` 审计**。
- **调用方上界（如实，批次 22 时点；已被批次 24 Progress 取代）**：`VisualJsService` 的 watchdog 在 ~1500 ms SIGKILL；宿主 `VisualJsClientEngine` 的 2500 ms 只包住一次会话，`ensureConnected()` 的 5000 ms 连接预算在期限之外，期限到点后的死亡确认再等 ≤1000 ms → 当时口径为**最坏约 8.5 s，不是 1500/2500 ms 硬上界**。批次 24 已把首连改为 15_000 ms、重建 10_000 ms、死亡确认 2_000 ms，最坏约 19.5 s（见下方批次 24 T1/设备轨 Progress）；**8.5 s 不是当前值**。并发调用还会在 client 的 `executeMutex` 上无超时排队，排队方等待不设上界（与引擎端口的 BUSY 背压语义不同）。死亡未确认时不重建、后续请求回 ENGINE_CLOSED（防并发放大进程数）。
- **验证状态（2026-10-26 复核更新）**：设备轨已执行（修复前）→ **4/4 红**。首例断言原文：`first execution: request pipe ended before a complete envelope arrived expected:<0> but was:<1>`（emulator-5554，`am instrument` 直跑 `workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`，pre-fix APK）。根因经设备探针定位：`ParcelFileDescriptor.createReliablePipe()` 的 read end 带一条状态 comm 通道；宿主在 `execute()` 返回后立刻关闭自己那份 read end，服务端稍后读到 EOF 时 `AutoCloseInputStream.checkError()` 把这次正常关闭读成 `IOException: Remote side is dead`，**64 KiB 数据已完整缓冲仍抛**；服务端把该 IOException 折成 `RequestRead.Failed` → `ENGINE_CLOSED`。修复（本批复核 #2）：服务端在 Binder 线程先 `request.dup()`（dup 出的数据 fd 不带 comm 通道）再投递执行线程；宿主 client 与测试 harness 读结果 payload 前同样先 `dup()`。**修复后的设备复跑已执行（批次 23，见下方 Progress 与 E-01b/E-01e）**：`libquickjs.so` 装载、`killProcess` 权限、watchdog 杀进程与重建后无 BUSY 已有绿证据（4/5），仅 `abandon` 路径红。AIDL 源文件在复核中发现 `IVisualJsCallback.aidl` 被误覆盖为 `IVisualJsService` 的生成 Java stub（`aidl.exe` 双输入调用的输出覆盖了第二份输入）；已恢复为正确 `oneway` 回调接口，并用 `aidl.exe` 对两份 AIDL 分别编译（exit=0）验证。**2026-10-26 记录时点复核**：当时测试集为 5 例（第 5 例 `theClientRebuildsAtMostOnceAfterConfirmedAbandons` 已在批次 23 设备执行，结果为红，见下方 Progress）；批次 24 起测试集为 6 例（新增冷启动用例），设备结果见下方批次 24 设备轨。记录员复跑 `aidl.exe` 时误用双位置参数（`INPUT [OUTPUT]`）再次覆盖该文件，已按 R1 §4.2 与生成 Java 签名恢复（1340 B，UTF-8），单文件 `--lang=java` 编译两份 AIDL 均 exit=0；后续禁止双输入调用。其 watchdog 用例以 ≤6 s 判死，**弱于本条 Reopen condition 的 hard+500 ms**，不能作为关闭证据。
- **本轮新增用例（设备已执行，红）**：`theClientRebuildsAtMostOnceAfterConfirmedAbandons`（首次 `abandon` 确认死亡后允许一次重建；第二次确认死亡后不得再 bind），用新增 `VisualJsClientEngine.abandonForTest()` 走生产 `abandon` 路径。批次 23 设备轨真跑结果：`AssertionError: the first abandon did not confirm death`（`VisualJsIsolationInstrumentedTest.kt:210`）——服务进程在 attach 后 16 ms 内被 `terminate(generation)` 的 SIGKILL 杀死，但客户端 1 s 内没有收到 binder death；根因是 `VisualJsClientEngine.abandon()` 先 `unlinkDeath` 再等 `link.death`（`VisualJsClientEngine.kt:570-571`）。E-01c 单方法稳定复现，E-01b/E-01d/E-01e 四次独立运行同形；修复方向是死亡确认顺序/判定，不是放宽断言。主源码与测试源码此前已用缓存 Kotlin 2.3.21 直接编译通过（`tmp/verify_batch22_visualjs_compile.py` / `..._test_compile.py` → COMPILE_OK）。
- **未做**：生产装配点（JS 仍 UNAVAILABLE、工具面/提示词/能力快照零改动）、服务内内容度量（`UnwiredVisualContentMeasurer`，只有纯几何程序可通）、4E.2 交互图/4E.3、上游 issue #240 修复（仍 OPEN）。

**Progress（2026-10-26 批次 23 设备轨；supersedes 上方"复跑 UNVERIFIED / 第 5 例未执行"的记录时点表述）.** 设备证据已按 R4 规范落盘（`docs/research/evidence/2026-10-26-batch-23/`）。dup 修复后的 `VisualJsIsolationInstrumentedTest` 在 emulator-5554（API 34，x86_64，APK sha256 `e5ec3a89…bb441`）真跑两轮：**5 例全部执行，4 绿 1 红**。

- **绿**：`workerIsIsolatedAndAProgramRoundTripsWithoutBeingKilledLater`（隔离 UID 成立 + AIDL 往返成功）、`aStaleGenerationIsRejectedAndAStaleTerminateIsANoOp`（过期 generation/terminate no-op）、`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState`（1500 ms 硬期限 `killProcess` + 新 generation 无 BUSY）、`theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy`（死亡确认后重建一次，新 worker 无 BUSY）。
- **红**：`theClientRebuildsAtMostOnceAfterConfirmedAbandons`（`the first abandon did not confirm death`）——见上方新增用例段与 E-01c；这是 client `abandon()` 的结构性缺陷，不是 dup 修复回退。
- **冷启动对照**：重启后立即跑 5/5 红（隔离进程 attach 4.6–7.5 s > 客户端 5 s 连接预算，E-01d）；沉降约 2 min 后回到 1/5 红（E-01e），证明加载态超时与结构性失败已分离。
- **结论**：`libquickjs.so` 装载、`killProcess` 权限、watchdog 杀进程、死亡后重建均已有设备绿证据；**KD-66 仍 open**，原因只剩关闭门——现有 watchdog 用例以 ≤6 s 判死，弱于 Reopen condition 的 hard+500 ms，且 issue #240 形状本身仍泄漏 daemon 线程（批次 23 E-02 的观察用例绿=已知形态，不是可终止）。abandon 缺陷建议另立 KD（本条只跟踪 #240 关闭门）。
- **证据**：`research/evidence/2026-10-26-batch-23/step01b-visualjs-isolation-rerun.md:94`、`step01e-visualjs-isolation-postboot-settled.md:85`、`step01c-abandon-confirmed.md:107`、`00-index.md` §3/§3.1；原始日志 `build/device-track-batch23/step01b-visualjs-isolation-rerun.log`（`Tests run: 5, Failures: 1`）、`step01e-visualjs-isolation-postboot-settled.log`（同）。

**（2026-10-26 批次 24 T3 核对）** 批次 23 的设备执行状态以**批次 23 E-01b/E-01e** 为准（dup 修复后 5 例全部执行、4 绿 1 红）；"复跑 UNVERIFIED / 第 5 例未执行"旧表述作废。**批次 24 设备轨 E-01/E-01b 已 6/6（两代 debug APK，见下方 Progress）**。**保持 open**：剩余关闭门只有 hard+500 ms 设备用例（现有 watchdog 以 ≤6 s 判死，不算），abandon 路径红已在批次 24 设备轨消失。

**Progress（2026-10-26 批次 24 T1，app `visualjs` client + 仪器化用例；非 Gradle、非设备）.** `abandon()` 的结构性缺陷与冷启动误判已在代码层修复；**设备复跑已由下方批次 24 设备轨 E-01/E-01b 完成（6/6）；本条仍 open：hard+500 ms 关闭门未动**。

- **abandon 死亡确认（修复 E-01c 的结构性红）**：`VisualJsClientEngine.abandon()` 不再先 `unlinkDeath` 再等 `link.death`。现在保留 death 注册，用"死亡事实（`markDead` 完成 `CompletableDeferred`）+ 轮询 `IBinder.isBinderAlive` + `DEATH_CONFIRM_TIMEOUT_MILLIS`（2 s）兜底"组合确认旧实例终结；超时分支若最后一刻发现 `isBinderAlive=false` 也先 `markDead`，保证 `death`/在飞请求有界完成（R1 §4.4 待收口点 1）；确认后才清空 link、由下一次 `execute` 在 `connectLocked` 扣**唯一一次**重建预算，只有未确认才置 `unavailable=true`（fail-closed）。`handleDeath` 在 `ABANDONED` 状态短路，不会为 abandon 自动 bind。**它消灭的失败**：批次 23 E-01b/E-01c 的 `the first abandon did not confirm death`（服务 16 ms 内已 SIGKILL，客户端 1 s 内收不到本应到达的 binder death）。
- **冷启动首连预算**：首连 5_000 → 15_000 ms，重建 5_000 → 10_000 ms（R1 §6.2：E-01b 的重建 0.6–0.9 s 只覆盖沉降态，重负载重建无实测）。设备实测冷启动 attach 4.6–7.5 s（E-01d），旧 5 s 会把加载态误判成结构性失败并永久 `unavailable`；15 s 覆盖实测上界 + 握手。**它消灭的失败**：设备重启后立即跑 5/5 红的首连误判。调用方最坏等待相应从约 8.5 s 变为约 19.5 s（首连 15 s + 宿主阶段 2.5 s + 死亡确认 2 s，另加 Binder 开销），仍不是 1500/2500 ms 硬上界。
- **仍未覆盖（如实）**：连接超时仍是一次失败即永久 `unavailable`（未实现 R1 §5.3 的 LOADING + 500 ms 退避重试），也未实现服务端 warm-up/ready；E-01d 的"首个 execute 时 QuickJS 原生装载超过 1500 ms 被 watchdog 误杀"（R1 §5.1/§5.4）本批未修。冷启动用例只证明首连 15 s 预算（E-01 已设备绿），不能证明 1500 ms 误杀已消失。
- **用例（5 例形状 + 1 例冷启动）**：`theClientRebuildsAtMostOnceAfterConfirmedAbandons` 在首次 abandon 重建后新增一次真实 `execute`，断言返回 `Rendered`（BUSY 会使其红），覆盖"重建后无 BUSY"；新增 `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`（attach 回调推迟 7.6 s = 实测上界 + 100 ms，旧 5 s 预算下必红），断言首次 `execute` 直接成功且只连接一次。仪器化类共 6 例。**本批禁 Gradle，未执行任何 JVM/仪器化/设备用例**；"红→绿"是按旧代码路径的确定性推演，不是执行证据。主源码与测试源码已用缓存 Kotlin 2.3.21 非 Gradle 直编：`python tmp/verify_batch22_visualjs_compile.py` / `..._test_compile.py` → `COMPILE_OK`（exit=0）。
- **issue #240 真挂死仍按 watchdog 覆盖（如实）**：本批没有改引擎终止语义。`theWatchdogKillsAHungWorkerAndAFreshGenerationHasNoBusyState` 仍以 ≤6 s 观察 L2 SIGKILL，`theClientRebuildsOnceAfterADeathAndTheNewWorkerIsNotBusy` 仍以死亡后重建 + 无 BUSY 覆盖；两者都弱于 Reopen condition 的 hard+500 ms，不能作为关闭证据。#240 的自续 job 仍会泄漏 daemon 线程（批次 23 E-02 观察型用例绿是已知形态，不是可终止）。
- **证据/未落盘**：代码 `app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsClientEngine.kt:575-642`、用例 `app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt:205-260`；历史红证据 `research/evidence/2026-10-26-batch-23/step01b-visualjs-isolation-rerun.md:94`、`step01c-abandon-confirmed.md:107`、`step01b-visualjs-isolation-rerun.md` 同批冷启动 `step01d-visualjs-isolation-coldboot.md:61-72`。**本批（T1）未执行设备命令；设备日志见下一节批次 24 设备轨**。

**Progress（2026-10-26 批次 24 设备轨，E-01/E-01b；supersedes 上方"设备复跑 UNVERIFIED / 5 例 4 绿 1 红"的记录时点表述）.** `VisualJsIsolationInstrumentedTest` 在两代 debug APK 上各跑一遍，**均 `OK (6 tests)`**：E-01 用 21:31 构建件（app sha256 `53c3a453…6eba`，`Time: 23.945`），E-01b 用 21:37 他线重建后的当前安装件（app sha256 `782cebd6…73c6`，`Time: 22.208`）；测试 APK 两轮同为 `343b637a…08c0e`。两轮均为 6 个用例逐个 `INSTRUMENTATION_STATUS_CODE` 1→0，崩溃签名 8 类全 0，`death was not confirmed` 告警 0。批次 23 红的 `theClientRebuildsAtMostOnceAfterConfirmedAbandons` 转绿（重建后真实 `execute` 返回 `Rendered`，第二次确认后不再 bind），冷启动 7.6 s 延迟 attach 用例也绿（首连 15 s 预算）。**这不关闭 KD-66**：类内 watchdog 用例仍以 ≤6 s 判死，弱于 hard+500 ms 关闭门；#240 的 daemon 线程泄漏不变；JS 仍不广告。证据：`docs/research/evidence/2026-10-26-batch-24/step01-visualjs-isolation.md:124`、`step01b-visualjs-isolation-rerun.md:102`、`00-index.md:43-44`/`:53-57`；原始日志 `build/device-track-batch24/step01-isolation.log`、`step01b-isolation-rerun.log`（均 `OK (6 tests)`）。

**（2026-10-26 复核修复 #3/#4；代码层，未跑 Gradle/设备）.** ① **death-before-registration 竞态**：`Link.onServiceConnected` 原先在 `dead`/`ABANDONED` 检查通过后无条件写 `state = CONNECTED`；若 `markDead` 在两步之间赢，`handleDeath` 已清 link 并写 DEAD，随后仍会被覆盖。现在 `markConnected` 与 `markDead` 共用 `stateLock`（`markDead` 先 CAS `dead` 再取锁），`connectLocked` 用 `isConnected()` 在锁内复查 `dead`；已死 link 不会被当连接返回。② **首连中 `close()` 的 15 s 等待**：`close()` 现在对当前 link 调 `markDead("the client was closed", unexpected=false)`，完成 `connected`/`death`、终结 activeCall 并解注册/解绑；首连或重建中的 `connectLocked` 立即返回 null，不再等满预算。**未修/仍如实登记**：连接超时仍一次失败即永久 `unavailable`（无 LOADING/500 ms 退避，P-04）、`ensureConnected()` 仍在宿主 2500 ms 期限之外、最坏等待约 19.5 s、`executeMutex` 排队无上界；这些是契约 §4:72 已写明的已知限制，不是本轮回归。**验证边界**：本轮未运行 Gradle/设备，竞态交错无确定性测试；只做了非 Gradle 源码编译检查（`tmp/verify_batch22_visualjs_compile.py` / `..._test_compile.py`，见结果）。

**（2026-10-27 批次 25 T1 补充；代码层，本轨未跑 Gradle/设备）.** 上段"竞态交错无确定性测试"的缺口已由新用例补上：`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsHandshakeRaceInstrumentedTest.kt`（新增，未跟踪）钉住 `markConnected`/`markDead`/`isConnected` 共用 `stateLock`、`markDead` 先 CAS `dead` 再取锁的交错不变量（markDead 赢得交错时连接发布让位、已死 link 不得被发布成 CONNECTED）。共 2 例（fake-binder 夹具，不启动真实 `:visual_js`，不作隔离门证据）：①首连握手停在发布点前最后一个握手调用（workerUid 停车窗），markDead 在窗口内完整落地后放行 → `connectForTest=false`、`connectionCount=0`、bind 恰好 1 次；②已连接实例死亡后恰好一次重建，重建握手再输给死亡 → `connectionCount` 仍 1、bind 恰好 2 次、之后 fail-closed 不再 bind（重建预算只扣一次）。同批按批次 24 §5 #11 改正"`abandon()` 未确认分支"的"迟到的 death 会被丢弃"措辞（注释与 logcat 文本；行为不变：迟到死亡仍走 markDead/handleDeath 补完等待者，重建被 fail-closed 门挡下）。非 Gradle 直编 `tmp/verify_batch25_t1_race_compile.py`：主源（Protocol+**真实** FrameQueue+`aidl.exe` 直出的 isReady AIDL 垫片+Engine；不含 `VisualJsService.kt`——T2 在飞引 core/domain 未落地符号，用批次 24 构建中间件旧类解析）→ `MAIN_COMPILE_OK`；`--race-only` 只编本批新用例 → `TEST_COMPILE_OK`（均 exit=0；`aidl.exe` 垫片不是 AIDL 门，AIDL 门仍归 Gradle 任务）。**整测类编译当前红**：`VisualJsIsolationInstrumentedTest.kt:883/:889` 的 `READY_WAIT_MILLIS`/`READY_POLL_MILLIS` 未解析——T2 在飞新增断言引用尚未落地的常量，非 T1 改动；终态编译由门禁脚本统一执行。用例的执行证据待本批设备/门禁轨；**这不改变本条关闭门**——关闭仍以 hard+500 ms 设备用例（#240 形状）为准，该交错测试不覆盖 #240。

**Progress（2026-10-27 批次 25 设备轨；记录员回填）.** 设备证据 `docs/research/evidence/2026-10-27-batch-25/`：① hard+500 ms 专属用例**仍未写**（E-02 `step02-kd66-hard-500ms.md:48` NOT-WRITTEN；全 androidTest 源核查无该断言，现有 watchdog 以 `withTimeoutOrNull(6_000L)` 判死（`VisualJsIsolationInstrumentedTest.kt:198`），弱于关闭门）；② 本条 T1 交错用例 2/2 设备绿（E-01 `step01-visualjs-isolation.md`，fake-binder，不覆盖 #240 形状）。**关闭门不变，本条保持 open**（判据对照见该索引 §9.2）。

**Progress（2026-10-27 批次 26 T1，hard+500 ms 关闭门用例落码；本轨未跑 Gradle/设备）.** 批次 25 E-02 的 NOT-WRITTEN 消除的是**落码缺口**（执行缺口仍在，见末句）：`VisualJsIsolationInstrumentedTest.aHungWorkerReleasesTheCallerWithinHardPlus500ms`（类内第 13 例，client 引擎生产调用面，`VisualJsIsolationInstrumentedTest.kt:373`）。红基线以源核查重证：改前全 visualjs androidTest 源 grep 无任何 hard+500 ms/elapsed 计时断言（唯一 `hard+500` 命中是类 KDoc 自述"不能作为关闭门"），既有 watchdog 只以 `withTimeoutOrNull(6_000L)` 判死（现 `:202`/`:552`）——"无 500 ms 断言即无法关闭本门"由该核查钉住。断言按 R1 设计 §5.2 清单落齐、不放宽：A1 有界拒绝；A2 `HARD_DEADLINE_EXCEEDED`；A4 诊断含 "died"（归因 worker 死亡路径，先于计时——宿主 2500 ms 超时兜底诊断不含 "died"，两种失败形状在计时前分开）；A5 `elapsed ≤ SERVICE_HARD_DEADLINE_MILLIS + 500`（门常量相对形式 `HARD_PLUS_500_GATE_MILLIS`，`:776`；500 ms 是门自带余量、不追加容差；hard+500=2000 ms 紧于宿主 2500 ms 阶段期限，兜底路径不可能蒙混）；A6 死亡后重建；A7 连接数=2 且新程序 `Rendered`（门第二句"后续请求可用"；`Rendered` 传递性证明旧进程真死——否则 #240 线程仍占服务单在飞槽必 BUSY）。计时端到端（`SystemClock.uptimeMillis()` 同钟相减、不扣除任何段），计时前预热 `Rendered` 负控；与既有用例关系按 R1 §6 裁定**共存**（两处 6 s 死亡观察与状态机用例原样保留，非本门证据）。验证边界：非 Gradle 直编 `python tmp/verify_batch25_t1_race_compile.py` → `MAIN_COMPILE_OK` + `TEST_COMPILE_OK`（exit 0；告警仅既有两处，非新增代码）；**Gradle 门编译与设备执行均未发生**——本门关闭仍缺设备轨执行（R1 §4.5：两轮独立绿）与记录回链，**本条保持 open**。

**Progress（2026-10-27 批次 26 T5 登记；设备轨未出数）.** T1 落码状态见上方批次 26 T1 块（他线 T1 轨登记，本轨未改其内容）；本块只登记台账面与设备轨状态：① 批次 25 §9 主会话收口与本条相关的两项修复已在树——R15 loading 旗标死亡竞态裁定修复（`VisualJsClientEngine.kt` handleDeath 清零 `lastBindFailedOnLoading` 移到 ABANDONED 短路之后，见 KD-76 合并块）、A-14 夹具新开 `callbackPipe`（断言未放宽，本轮复读确认）；② 隔离套件复跑（A-14+R15 后 12+2 全绿判据）与 hard+500 ms 设备执行均归批次 26 计划 T2（`.zcode/plans/plan-2026-10-27-batch-26-kd66-gate-lockfiles.md:14`）；③ 截至本块登记时点 `docs/research/evidence/` 下无批次 26 证据目录（本轮实测 `ls`），设备结果未出——不预填判定，出数后以实际为准。**关闭门不变，本条维持 open**。

**Progress（2026-10-27 批次 26 设备轨 + 记录员收口；关闭。上方批次 26 T1/T5 两块的「Gradle 门编译与设备执行均未发生／本条维持 open」为设备出数前时点表述，被本块取代）.** 关闭判据两条均已达成（工单 §2：hard+500 ms 设备用例绿 + 记录回链）：① **设备门**——T1 落码的 `aHungWorkerReleasesTheCallerWithinHardPlus500ms`（`VisualJsIsolationInstrumentedTest.kt:373`，断言 A1–A7 按 R1 设计 §5.2 落齐、门常量相对形式 `HARD_PLUS_500_GATE_MILLIS = SERVICE_HARD_DEADLINE_MILLIS + 500L`（`:776-777`）、500 ms 为门自带余量不追加容差）在 emulator-5554 上**三次独立运行全绿**：E-01 套件轮（`OK (13 tests)`，Time 59.33）+ E-02 聚焦轮 ×2（`OK (1 test)`，3.142 s / 3.081 s）——超过 R1 §4.5（`docs/research/2026-10-27-kd66-hard-500ms-design.md:128`）的"两轮独立运行均绿"判据；每轮 logcat 恰 1 条 `hard deadline (1500 ms)` kill 行且请求 ID 各异（`747412e7…`/`71ec7659…`/`18a15171…`，记录员对原始 logcat 逐条 grep 复核）、无隐藏重试；时间轴锚点：warm-up finished → kill（挂死提交后 ~1500 ms 按时杀）→ 死亡后 32 ms 重建 → 新进程 ready → 用例绿（A6/A7）。② **记录回链**——`docs/research/2026-10-27-batch-26-record.md` §3.5（设备轨 E-02 行）与 §4（门禁表）。设备证据按 R4 落盘于 `docs/research/evidence/2026-10-27-batch-26/`（`step02-kd66-hard-500ms.md` §3/§8；原始日志 `build/device-track-batch26/` 只作 raw-log、不作关闭证据）。**关闭语义边界（缺口如实保留，不随本条消失）**：本门关闭的是"调用方拿不到 1500 ms 硬上界"的进程层防线缺口；issue #240 **上游仍 OPEN**（AAR 钉版 1.0.15/commit `a081755` 不变），#240 自续 job 的 daemon 线程泄漏仍是登记形态（Fix direction ③ accept-the-leak 不变），上游修复版落地后按 Fix direction ② 把观察型用例反转为协作中断断言；**JS 仍不广告**（KD-76 open、MVA 不成立，本关闭不改变广告门，见批次 26 记录 §6）。设备数字为设备轨/复核轨实跑、记录员转录（记录员未复跑设备）。

**Fix direction.** ①（批次 22 T1 已落代码，未关闭）`:visual_js` `isolatedProcess` + AIDL 控制通道 + `killProcess(myPid())` 进程级硬终止——唯一能终止任意循环的层；设备门见 Progress。② 上游发 >1.0.15 修复后升级并把观察型用例反转为"必须协作中断"断言。③ 引擎层维持 accept-the-leak：每事务独立 daemon dispatcher、强弃 close 放专用守护线程、失败即 fuse/BUSY，如实现 KDoc 所述。

**Reopen condition.** 4E.2 进程隔离落地或上游修复版升级后，以"issue #240 形状在 hard+500ms 内返回拒绝、且后续请求可用"的设备用例关闭；在此之前不得把 T3 标成运行时完成。（T1 的仪器化用例现以 ≤6 s 判死 worker，弱于本条件；关闭时仍以 hard+500 ms 的用例为准。）

## KD-67 (open, 2026-10-26 登记（批次 21 T2/复核发现 #15；中）) · G5-A 生产候选全部 `contentVerified=false`：即使接线也到不了 ASK，CLARIFY_OR_CONFIRM 无消费者

**Symptom（读码 + 用例）.** 唯一生产题面写入方 `KnowledgeQuizItemStore.record` 固定 `verificationStatus = UNKNOWN`（`KnowledgeQuizItemStore.kt:173-176`）；`AdaptiveSelectionAssembler` 只把 `VERIFIED`/`USER_ASSERTED` 置真（批次 21 实现时 `:162-163`，当前 `:177-178`），错题候选恒 false（当时 `:194`，当前 `:209`）；`AdaptiveQuestionSelector.safeBaseCandidates` 要求 `contentVerified=true`（`:241`），未核验目标返回 `CLARIFY_OR_CONFIRM`（批次 21 实现时 `:288-292`，当前 `:293-295`）。feature/app 全仓没有 `CLARIFY_OR_CONFIRM` 的消费者，也没有把行升级为 VERIFIED/USER_ASSERTED 的写方；测试 `assembled production candidates stay fail-closed to clarify until verified` 钉住该出口。

**Impact.** medium（G5-A 代码落地但产品面不可用；不是回归，是 fail-closed 死端）。

**Progress（2026-10-26 批次 22 T4，core/domain + core/data；非设备、非 Gradle）.** 核心侧三块已落，死端所需"写方 API / 消费契约 / 可注入 seam"具备；**产品面仍未接通，本条保持 open**。

- **核验写方 API**：`core/domain/.../AnswerKeyVerificationUpgrade.kt`——来源类型 `AnswerKeyVerificationSource.IndependentVerification` / `UserAssertion`（`confirmationId` + `confirmedByLearnerId` + 时间；PRD-02 来源语义）与纯函数 `decideAnswerKeyVerificationUpgrade`：目标只能是 VERIFIED/USER_ASSERTED；当前必须是 UNKNOWN（已核验行不改写/不二次升级）；来源必须与目标同型；USER_ASSERTED 的确认者必须等于本题面学习者。`core/data/.../KnowledgeQuizItemVerificationUpgrader.kt` 读行 → 领域判定 → 单列 CAS 端口 `AssessmentItemVerificationStatusWriter`，缺行/坏题面/并发状态变更均有可判别出口。**复核 #13 后补回执绑定**：新增 `AnswerKeyVerificationSourceVerifier` 端口与 `SOURCE_NOT_VERIFIED` 拒绝原因，`upgrade()` 在写库前（含 `AlreadyUpgraded` 幂等出口）要求 id 指向真实回执/核验记录；只校验"非空 id"的漏洞关闭。生产实现（查更正回执/独立核验记录）仍未落——见"仍未闭合"⑤。**生产 Room adapter 未落**：单列 UPDATE 是 core/database 的 `SeedAssessmentPort` 扩展，不在本轨可改范围（本轨只动 core/domain 与 core/data）；adapter 落地前确认流无法在生产路径完成写入（批次 23 T2 已落地，见下）。
- **CLARIFY 消费契约**：domain 层 `AdaptiveClarificationConsumer` + `AdaptiveClarificationRequest`（未核验候选投影、原因码、选择器版本、决策时刻）；"未核验目标"过滤与选择器 `noCandidateDecision` 同源（`unverifiedTargetCandidates` 单处定义，避免两处判据漂移）。core:data 装配器新增 `select` / `selectAndConsume` 输出接线位：CLARIFY 决策携带可消费载荷；`selectAndConsume` 只在 CLARIFY 时调用消费者，其余决策不产生空回调。**feature 侧实现（确认卡/补题入口）登记遗留**——本批不建 UI；实现前产品上仍无确认入口。
- **装配器 seam**：`AdaptiveSelectionAssembler` 由 `internal` 改为公开可注入形态（构造参数仍是既有端口/对象，不引入 DI 框架）；KDoc 写明实例作用域、并发安全、一次选择内的跨表读不是同一事务快照、消费者不写库等调用方约束。
- **测试（JVM 直编 + JUnitCore，非 Gradle）**：`AnswerKeyVerificationUpgradePolicyTest` 4/4、`KnowledgeQuizItemVerificationUpgraderTest` **9/9**（原 7 例 + 复核 #13 新增"无回执 id 拒绝且不写库""已升级行的伪造回执不得回报 AlreadyUpgraded"）、`AdaptiveSelectionAssemblerTest` 12/12（新增"UNKNOWN 候选→CLARIFY 可消费出口"与"升级后进 ASK"两例；后者用既有 Elo 预测器重放 12 笔作答前缀，区间 [0.56496, 0.84231] 落在 FOUNDATION 走廊 [0.55, 0.85]）；既有 `AdaptiveQuestionSelectorTest` 21/21、`EloCorrectnessPredictorTest` 14/14 不回退。本轮复跑命令 `python tmp/verify_batch22_t4.py` → 五类全绿（4/9/12/21/14）。
- **仍未闭合**：① 确认流的生产调用点（谁在什么 UX 条件下构造 `UserAssertion`）；② core/database 的单列 UPDATE adapter（批次 23 T2 已落地，见下）；③ feature 侧 `AdaptiveClarificationConsumer` 实现与 UI；④ 设备/集成用例"生产候选能进 ASK、未核验候选仍 CLARIFY"；⑤ `AnswerKeyVerificationSourceVerifier` 的生产实现（更正回执/独立核验记录查询）。五者全部落地前不得宣告"G5-A 生产可用"。

**Progress（2026-10-26 批次 23 T2，core/database + core/data；设备执行见下）.** 生产落库半边已落并在真 Room/SQLite 上 5/5 通过，**本条仍 open**：写方现在有真实 Room 单列 CAS，但 feature 消费者与生产调用点仍未接。

- **core/database 单列 CAS（原"仍未闭合"②，本批落地）**：`SeedAssessmentPort.compareAndSetAssessmentItemVerificationStatus`（`core/database/.../port/LearningProjectionPort.kt:50-72`，返回真实受影响行数）；DAO 参数化 `UPDATE assessment_item_snapshot SET verification_status = :new WHERE assessment_item_snapshot_id = :id AND verification_status = :expected`（`dao/LearningDao.kt:183-204`，行数 0/1 如实返回，无行不新增）；`RoomStudyDatabase` adapter（`:1578-1599`）在写库前经 `DatabaseContractValidator.validateAssessmentItemVerificationStatusCas`（`DatabaseContract.kt:612-633`）收紧为仅 `UNKNOWN → VERIFIED/USER_ASSERTED`，已核验状态互换/降级直接抛 `DatabaseContractViolationException`。`verification_status` 列 v69 已存在，**无 schema 版本变更/迁移**。
- **core/data 生产 adapter 接线**：新增 `StudyDatabaseAssessmentItemVerificationStatusWriter`（`core/data/.../KnowledgeQuizItemVerificationUpgrader.kt:47-67`）把端口 Boolean 契约落到上面的 0/1 行数；`KnowledgeQuizItemVerificationUpgrader` 增加生产构造形态 `(database, learnerId, sourceVerifier)`（`:141-154`，写口自动走单列 CAS）。`upgrade()` 顺序未变：读行 → 题面可读 → 领域判据 → **sourceVerifier 回执校验（`:213`）** → 单列 CAS（`:219`）；四参构造器保留给测试注入。
- **测试（JVM 直编 + JUnitCore，非 Gradle，本轨实跑）**：`KnowledgeQuizItemVerificationUpgraderTest` **11/11**（新增"生产写端口如实回报 0/1 行数且不补行"与"并发同 id 两次恰一次生效（屏障强制双方先读 UNKNOWN 再 CAS）"；既有成功升级/已升级幂等/来源不符不写库 9 例全部改走生产 adapter 后不回退）；`AdaptiveSelectionAssemblerTest` 12/12 不回退。命令 `python tmp/verify_batch23_t2.py red`（改动前编译红：unresolved reference / overrides nothing）→ `python tmp/verify_batch23_t2.py green`（exit=0）。**未跑 Gradle；真实 Room SQL 未执行**。
- **core:database 仪器化用例（已设备执行，5/5 PASS）**：`AssessmentItemVerificationStatusInstrumentedTest`（`core/database/src/androidTest/.../AssessmentItemVerificationStatusInstrumentedTest.kt`，5 例：单列不变/过期 expected 0 行/无行不补行/非法状态对拒绝/并发恰一赢家）。设备轨 2026-10-26T16:25 在 emulator-5554（API 34，x86_64）以 `am instrument` 直跑测试 APK（sha256 `feb1b3601939487297b37af2c95a480b8b00995ba74d7c0da859f073aaa6df4e`）→ **`OK (5 tests)`，failures=0，skipped=0**；5 个用例逐个 `STATUS_CODE 0`。命令：`D:/smart mistake book/gradlew.bat -p "D:/smart mistake book" :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.tingyun.smartmistakebook.core.database.AssessmentItemVerificationStatusInstrumentedTest --console=plain`（本次实际执行用 `am instrument` 直跑，见 E-07 §2）。**结果早已不是 UNVERIFIED**：仓内证据 `docs/research/evidence/2026-10-26-batch-23/step07-kd67-cas.md:82`（判定 PASS 5/5）、原始日志 `build/device-track-batch23/step07-kd67-cas.log`（`OK (5 tests)`），索引 `00-index.md` §3/§3.1。KD-67 仍 open 的原因只剩产品消费面（①③④⑤），设备落库判据已达成。
- **仍未闭合（批次 23 后）**：① 确认流的生产调用点；③ feature 侧 `AdaptiveClarificationConsumer` 实现与 UI；④ 设备/集成用例"生产候选能进 ASK、未核验候选仍 CLARIFY"；⑤ `AnswerKeyVerificationSourceVerifier` 的生产实现。② 已落地。四者全部落地前不得宣告"G5-A 生产可用"。
- **残余边界（批次 23 复核 #10/#17，低）**：① `AssessmentItemVerificationStatusWriter` 的 JVM 并发用例只覆盖"两个写者改成同一目标"（输家 `AlreadyUpgraded`）；输家重读发现被改成**别的**状态（`ConcurrentVerificationChange`）在 core/data/core/domain 测试中零覆盖。② `@Update overwriteAssessmentItem`（KD-41 损坏行修复口，`LearningDao.kt:171-181`）仍可整行改写含 `verification_status`，存储层没有强制"只对解不开的行"；这是既有契约边界，不是本批回归，未观察到并发双写。

**Fix direction.** 产品侧补"题面核验写方"（谁在什么条件下把行升为 VERIFIED/USER_ASSERTED）与 `CLARIFY_OR_CONFIRM` 消费者（确认卡/补题入口）；不得为接通而放宽选择器 `contentVerified` 判据。接线时同时把 `AdaptiveSelectionAssembler` 从 `internal` 提升为跨模块可用端口（或在 core/domain 定义接口 + 实现），并改热区 VM。**（批次 22 T4 已落 domain 判据 + core:data 写方 API/端口契约 + 公开 seam + CLARIFY 消费契约；批次 23 T2 已落 core/database 单列 CAS + core/data 生产 adapter；feature 消费者与生产调用点仍待接线，见 Progress。）**

**Reopen condition.** 出现真实生产候选可被标为 VERIFIED/USER_ASSERTED 的写路径且 feature/review 消费 CLARIFY 决策后，以"生产候选能进 ASK、未核验候选仍 CLARIFY"的设备/集成用例关闭。（批次 22 的 JVM 进展不改变本条件。）

## KD-68 (open, 2026-10-26 登记（批次 21 T1/复核发现 #16；中）) · 新题积压 UI 承诺不可调度项：用 `preview` 算术而非 `previewBacklog`，`unschedulableCount` 无生产消费者

**Symptom（读码）.** `NewIntroductionPolicy.preview` 是粗略算术（固定参考预算 900s、把超全预算项也计入"每天约 X 题"），`previewBacklog` 才标 `unschedulableCount`；`ReviewRoute.kt:529` 调 `preview`、`:558` 固定 `DEFAULT_INTAKE_DAILY_BUDGET_SECONDS=900`；`StudyReviewPlannerService.kt:468` 只保留 `introducedIds`，deferred 新题从当日计划消失；`unschedulableCount`/`previewBacklog` 在生产 main/feature/app 无消费者。学生仍会看到"还有 N 道新题待学：每天约 X 题，约 M 天安排完"，而超全预算题在真实预算下永远不会被引入。

**Impact.** medium（KD-64 崩溃已修，但产品承诺与实际调度不一致；用户被误导，不是数据损坏）。

**Progress（2026-10-26 批次 22 核对；本批无代码变更）.** `feature/review` 仍是他线热区，本批未动；现状未变：`ReviewRoute.kt:529` 仍调 `NewIntroductionPolicy.preview`、`:558` 仍是固定 `900`，生产 main/feature/app 仍无 `previewBacklog`/`unschedulableCount` 消费者（grep 只命中 domain 定义与测试）。**本条保持 open，待 feature/review 热区空出。**

**Progress（2026-10-26 批次 23 T3，core/domain + core/data；非 Gradle、非设备）.** 数据侧半边已落，**UI 消费仍在 feature/review 热区，本条保持 open**。

- **决策事实（只加只读派生）**：`IntakeDecision` 新增 `unschedulableCount`（默认 0，require ≥0 且 ≤ `deferred.size`，`NewIntroductionPolicy.kt:90-96`）；`decide` 以 `ordered.count { it.estimatedDurationSeconds > timeBudgetSeconds }` 派生（`:205`）。它不参与发放算术，`introduced`/`deferred`/`grantedSliceSeconds` 逐位不变（KD-64 既有 13 例金样全绿，见下）。语义钉住：因当日切片用完而 `deferred` 的题**不是** unschedulable（测试 `deferred because today's slice ran out is not unschedulable`）。
- **如实预览 API**：新增 `previewSchedulability` + `SchedulabilityPreview`（`:359-424`；`schedulableCount`/`unschedulableCount`/`beyondHorizonCount`，恒有 `schedulable + unschedulable == backlogCount`，`beyondHorizonCount ≤ schedulableCount`）。`previewBacklog`（`:319`）改为委托同一逐日模拟，并按旧口径合并回 `unschedulableCount + beyondHorizonCount`，既有取值逐位不变；`preview`（`:269`）KDoc 已写明"旧预览会隐藏超全预算项"并指向 `previewBacklog`/`previewSchedulability`，仍保留兼容、未加 `@Deprecated`（避免热区调用点产生编译警告）。
- **快照读面（与 `intakeBacklogCount` 同族）**：`StudyReviewOverview` 新增 `intakeUnschedulableCount`（默认 0，require ≥0 且 ≤ `intakeBacklogCount`，`StudyExperienceRepository.kt:98-109`）；`StudyExperienceMappers.toOverview` 透传（`:223`/`:257`）；`StudySnapshotBuilder` 在计算当前积压后经 `StudyReviewPlannerService.intakeUnschedulableCount` 派生（`StudySnapshotBuilder.kt:91`、`:132`；方法 `StudyReviewPlannerService.kt:299`）。该派生与 `createReviewPlan` 的引入判定**同一口径**：共用 `expectedNewSeconds`（L1/L2/L3 时长，`StudyReviewPlannerService.kt:264`）与 `examCapacityBudgetSeconds`（考试扩容预算；考试天数不影响"整日预算都装不下"的判定）；O(n) 计数，不把 365 天逐日模拟放进每次快照构建。只读，不写计划、不改当日引入集合。积压按 practice unit 去重，返回计数不超过快照上的 `intakeBacklogCount`。
- **测试（JVM 直编 + JUnitCore，非 Gradle）**：`tmp/verify_batch23_t3.py`。**红**：新测试对 HEAD 源编译失败（`unschedulableCount`/`previewSchedulability`/`intakeUnschedulableCount` unresolved，exit=1）；**绿**：工作树源编译 exit=0，`NewIntroductionPolicyTest` 19/19（新增 6 例覆盖全超预算形状、顺延≠不可调度、混合子集、正常形状两 API 一致、旧 API 金样）、`StudyReviewOverviewIntakeTest` 3/3、`StudyExperienceMappersIntakeTest` 2/2；**旧 API 金样**：HEAD 版 `NewIntroductionPolicyTest` 13/13、`PretestRoutingTest` 6/6、`PretestScoringModeAssemblyTest` 4/4、`IntakePreviewAssemblyTest` 4/4（后者含 `previewBacklog` 空表/超预算/考试三形状）对工作树源全绿。
- **复核发现 #9 补测（2026-10-26）**：新增 `StudySnapshotIntakeUnschedulableTest`（`core/data/src/test/.../study/`）——不再只测 policy/mappers/overview，而是用假库 + 真 `RoomBackedStudyExperienceRepository` 走 `StudySnapshotBuilder.build` → `StudyReviewPlannerService.intakeUnschedulableCount`：两道 EASY（模型档 90 s）+ 一道 HARD（300 s），日预算 120 s；FIFO 只引入第一道 EASY，第二道 EASY 因切片用完顺延（**可调度**），只有 HARD 计入 `intakeUnschedulableCount`。实测 `review.scheduledPracticeUnitIds == ["unit-easy-1"]`、`intakeBacklogCount == 2`、`intakeUnschedulableCount == 1`。同一 `tmp/verify_batch23_t3.py`：红（HEAD 源 unresolved `intakeUnschedulableCount`）→ 绿（`StudySnapshotIntakeUnschedulableTest` `OK (1 test)`，既有 19/3/2 与旧金样全绿）。**"与 planner 同一口径"现在有执行证据，不再只是读码结论。**
- **仍未闭合（本条保持 open 的剩余条件）**：`feature/review` 仍是他线热区——`ReviewRoute.kt:529` 仍调 `preview`、`:558` 仍是固定 `900`，`intakeUnschedulableCount`/`previewSchedulability` 在生产 main/feature/app 仍零消费者；`ReviewIntakeBacklogPreviewInstrumentedTest` 尚未覆盖"全超预算项显示为不可调度/专项会话"。Reopen condition 不变。
- **残余（批次 23 复核 #11/#19/#20，低）**：① `IntakeDecision.unschedulableCount` 的 KDoc 写"count over [deferred]"，实现是 `ordered.count { ... }`（全部候选）；今日枚举可证明两者等价（非 allOversized 分支 cap ≤ effectiveSlice/2 < budget；allOversized 分支 sliceBudget ≤ budget 且 cap=topDuration，任一超预算候选都过不了 `duration <= remaining`），但契约文字与实现口径不一致，改发放守卫时会触发 `init` 的 `<= deferred.size` require。② `previewSchedulability.beyondHorizonCount` 只在三个用例中被断言为 0（`NewIntroductionPolicyTest.kt:245/:285/:323`），没有把积压推到 `MAX_SIMULATED_DAYS` 之外产生非零值的用例。③ 快照每次构建会多一次 `readExams()` + 全积压难度档批量读（`StudySnapshotBuilder.kt:91` → `StudyReviewPlannerService.kt:308/:314`），与同一轮 `createReviewPlan` 的读取重复；空积压短路，非空时是额外的 DB 读（未做基准，静态风险）。

**Fix direction.** feature/review 热区空出后，把 `ReviewRoute` 的积压预览改为 `previewSchedulability(...)`（或至少 `previewBacklog(...)`）并消费 `intakeUnschedulableCount`/`unschedulableCount`（文案区分"可日引入"与"需专项会话"）；`StudySnapshotBuilder.intakeBacklogCount` 与计划器已同口径（批次 23 T3）；保留 `preview` 仅作兼容/测试。

**Reopen condition.** `ReviewIntakeBacklogPreviewInstrumentedTest` 覆盖"全超预算项显示为不可调度/专项会话"且真实计划不再承诺其日引入后关闭。

## KD-69 (closed 2026-10-27（批次 24 主会话收口裁定，记录 §9）；2026-10-26 登记（批次 21 设备轨步五/复核发现 #12）；低) · `internal`/`benchmark` buildType minify=true 但缺项目 proguardFiles：R8 strict full mode 剥掉生成类的无参构造器（WorkDatabase_Impl / ML Kit registrar），启动 FATAL

**Symptom（设备实跑 + R8 产物实证；批次 21，修复前）.** `:app:assembleLocalFirstInternal`（当时 minify=true、无 proguardFiles）装机启动崩溃：`java.lang.RuntimeException: Unable to get provider androidx.startup.InitializationProvider` ← `androidx.startup.StartupException: java.lang.NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init>` ← `Room.getGeneratedImplementation`（`build/device-track-batch21/step5-r8-logcat.txt:372-410`）。同批 release 冒烟：BUILD SUCCESSFUL 3m24s、装机启动 PID=13393、523 行 logcat 中 FATAL/ANR/ClassNotFound 等全 0（`step5-r8-release-logcat.txt`）。

**根因（批次 22 R4 `docs/research/2026-10-26-r8-workmanager-room.md` §3/§4 修正原归因）.** AGP 9.3.0 起 `android.r8.strictFullModeForKeepRules` 默认 true（`BooleanOption.class` 字节码：ordinal 30 / `iconst_1`），R8 不再为被 keep 的类隐式保留无参构造器。WorkManager 2.10.5 传递的 `androidx.room:room-runtime:2.6.1` consumer rule 是无成员限定的 `-keep class * extends androidx.room.RoomDatabase`（`localFirstInternal/configuration.txt:1025-1026`，**规则已生效**），只保住类、保不住成员；`WorkDatabase_Impl.<init>()` 无直接调用点 → 被裁（`localFirstInternal/usage.txt:43128-43131` 把 `public void <init>()` 列为被移除）。原记录“没有 proguardFiles ⇒ 规则全没生效”不准确：真正缺的是本项目 `app/proguard-rules.pro` 的两组显式 keep。`benchmark`（修前 `app/build.gradle.kts:58-64`）与 `internal`（修前 `:65-70`）只设 `isMinifyEnabled = true`、无 `proguardFiles`，且 `matchingFallbacks += listOf("release")` **不继承** proguardFiles；release 因应用 `-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }`（`localFirstRelease/configuration.txt:101`；规则源 `app/proguard-rules.pro:8-10`）而构造器保住（`localFirstRelease/usage.txt:45476-45479` 无 `<init>`）。同一缺陷还覆盖三个 ML Kit registrar：internal 被裁（`localFirstInternal/usage.txt:50717-50718 / 50955-50956 / 51010-51011` 各含 `public void <init>()`），release 因 `app/proguard-rules.pro:17-19` 保住；它们由 ComponentDiscovery 惰性反射实例化，只补 Room 一条则下一次中文 OCR 会以同样形状崩。

**Impact.** low（仅内部/基准构建；不进 release 包，但会让 minified debug 冒烟假红/无法用于验证）。

**Progress（2026-10-26 批次 22 T5，`app/build.gradle.kts`；非 Gradle、非设备）.** 已按 R4 §5 方案 A 落最小补丁：`benchmark`（现 `:67`）与 `internal`（现 `:75`）各补一行 `proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")`，与 release（现 `:84`）同一规则集；未关 minify、未改 strict full mode、未动依赖版本。修前诊断按 R4 §8.1 只读复跑并复现：D-1 consumer rule 在 `localFirstInternal/configuration.txt:1025-1026`；D-2 internal 的 `WorkDatabase_Impl` 段含 `public void <init>()`；D-3 release 同段不含 `<init>()`；D-4 项目 Room keep 只命中 `localFirstRelease/configuration.txt:101`；D-5 `gradle.properties` 无 strict full mode 逃逸口（无输出）；D-6 internal 被裁无参构造器 351 个（与批次 21 基线一致）；三个 ML Kit registrar 的 internal/release 对照同样复现。**修复后的构建与设备判据见下方批次 23 Progress（E-04，PASS）**（批次 22 声称的 internal/benchmark/release 冒烟零崩溃当时无日志落盘，见 KD-74）：静态断言 S1-S4 与设备冒烟 D1-D6 的命令见 R4 §8.2；`internal` 是唯一“可调试 + minify”变体，debug minified 冒烟应由设备轨执行（`./gradlew :app:assembleLocalFirstInternal` → 安装冷启动 → logcat 断言 → 一次中文 OCR → 一次导出 Worker）。

**Progress（2026-10-26 批次 23 设备轨，E-04；supersedes 上方"未验证"的记录时点状态）.** `strictOffline internal`（minify=true，真 R8；设备侧 APK sha256 `da8df2f8…0e5b` 与产物一致）装机执行：两次冷启动均 `Status: ok` / `LaunchState: COLD`（4935/5135 ms），PID 存活，`FATAL EXCEPTION`/`StartupException`/`NoSuchMethodException`/`ClassNotFoundException`/`NoClassDefFoundError`/`UnsatisfiedLinkError`/`MlKitException`/`SIGSEGV` 全 0；`BatchImportDriverWorker` 与 `OrphanAssetGcWorker` 走 WorkManager 反射真实实例化并 `Worker result SUCCESS`；一次中文 OCR 真实完成（Dynamite 本地中文模块 + Hani 模型 + `OCR process succeeded`），`LOCAL_OCR` revision 已写入 Room；`usage.txt` 中 `WorkDatabase_Impl` 段无 `public void <init>()`，三个 ML Kit registrar 的 `-keep ... { <init>(); }` 规则均命中。**Reopen condition 的设备判据满足；是否关闭待主会话按批次记录回链与 R4 §6.3 裁定（KD-74 收口不等于设备门全绿，KD-69 的关闭仍以本条判据为准）。** 证据：`docs/research/evidence/2026-10-26-batch-23/step04-r8-smoke.md:142`（判定 PASS）、`00-index.md` §3/§3.1；原始日志 `build/device-track-batch23/step04-coldstart{1,2}-*.txt`、`step04-ocr-*`。覆盖说明（不改变判据）：`release` 变体本批未重跑（沿用批次 21 绿，`assembleLocalFirstRelease`/`assembleStrictOfflineRelease` 冒烟无回退证据）、`usage.txt` 只核了 Room + 三个 registrar 而非全量穷举、`ExportPdfWorker` 未单独触发（触发的是两个启动 Worker）。

**（2026-10-26 批次 24 T3 核对 + 设备轨 E-03 回填）** internal 判据已由批次 23 E-04 满足，旧"设备冒烟无日志/未验证"表述作废。**release 冒烟已在批次 24 E-03 重跑**：`strictOffline release`（R8 + shrinkResources，一次性本地 key）两次冷启动均 `Status: ok` / `LaunchState: COLD`（2405/1245 ms），PID 存活，9 类崩溃签名 0（`docs/research/evidence/2026-10-26-batch-24/step03-r8-release-smoke.md:174`、`00-index.md:46`）；因此"本批未重跑 release"不再是当前状态。**仍不能宣告关闭**：该包用一次性本地 key 签名（生产 `RELEASE_*` 缺失，`step03:48`），未跑中文 OCR，`usage.txt` 未全量穷举、`ExportPdfWorker` 未单独触发是覆盖边界；是否关闭由主会话按 Reopen condition（含 release 不回退）裁定。（以上为批次 24 T3 核对时点状态；2026-10-27 主会话已裁定关闭，见下方收口 Progress。）

**Progress（2026-10-27 主会话收口裁定，批次 24 记录 §9；本条关闭）.** 关闭依据：① internal 判据——批次 23 E-04 `strictOffline internal`（minify=true，真 R8）冷启动 ×2 全绿 + 一次中文 OCR 完成 + `WorkDatabase_Impl`/三个 ML Kit registrar 的 keep 命中（`docs/research/evidence/2026-10-26-batch-23/step04-r8-smoke.md:142`）；② release 不回退——批次 24 E-03 `strictOffline release`（R8 + shrinkResources）冷启动 ×2 全绿、9 类崩溃签名 0（`docs/research/evidence/2026-10-26-batch-24/step03-r8-release-smoke.md:174`，签名限制 `:40-50`）。裁定原文：批次 24 记录 §9 主会话收口（`docs/research/2026-10-26-batch-24-record.md:396` 起，裁定条 `:404`）——"E-03 strictOffline release 冷启动 ×2 全绿、9 类崩溃签名 0，满足'release 冒烟不得回退'；生产签名另属发布链验证（一次性本地 key 已如实记录）"。**关闭不等于覆盖缺口消失（继续如实登记）**：生产 release 签名 UNVERIFIED（`validateReleaseSigning` exit=1 缺四个 `RELEASE_*`，`step03-r8-release-smoke.md:48`）；中文 OCR 未在 release 步执行（internal E-04 已覆盖一次）；`usage.txt` 只核了 Room + 三个 registrar 而非全量穷举；`ExportPdfWorker` 未单独触发。前三项归发布链验证/发布 runbook 与准入清单跟踪，不属本条重开范围；Reopen condition 的设备判据文本不变。

**Fix direction.** 已落：给 `internal`/`benchmark` 补与 release 相同的 `proguardFiles`；批次 23 设备轨已按上述命令完成 internal 冷启动 ×2 + 中文 OCR 冒烟（E-04 PASS），批次 24 E-03 又完成 release 冷启动 ×2（一次性本地 key）。残余（生产 release 签名 UNVERIFIED、release 中文 OCR 未跑、`usage.txt` 未全量穷举、`ExportPdfWorker` 未单独触发）见 Progress。未采纳的备选：关闭其 minify（掩盖 R8 缺陷）、`android.r8.strictFullModeForKeepRules=false`（AGP 10 弃用/11 移除）、升级 room 2.7+（归 R2）。

**Reopen condition.** `internal` minified 构建装机冷启动（含二次冷启动）无 `FATAL EXCEPTION`/`StartupException`/`NoSuchMethodException`/`ClassNotFoundException`/`NoClassDefFoundError`，且**一次中文 OCR** 无 `MlKitException`/`NoSuchMethodException` 后关闭；release 冒烟不得回退。

## KD-70 (closed 2026-10-27（批次 24 主会话收口裁定，记录 §9）；2026-10-26 登记（批次 21 T3/复核发现 #6/#21）；中) · QuickJS 内存上限路径在 x86_64 未成立：设备实际 `INVALID_PROGRAM`，契约要求的 `MEMORY_LIMIT` 未达

**Symptom（设备实跑）.** `memoryOverTheHeapBudgetIsRejectedAsAMemoryLimit` 断言 `MEMORY_LIMIT`，设备实际 `INVALID_PROGRAM: program failed in phase prepare: InternalError: string too long`（旧夹具；`step2-quickjs-oom-detail.log:1-6`，独立复跑 3/3 同）；改用分配型探针（数组/对象持续增长）后仍 `INVALID_PROGRAM`，诊断 `program failed in phase prepare: null`（`step2-oom-attribution.log`）。`classifyJsException` 只按 `lowered.contains("out of memory")` 归类（`QuickJsVisualExecutionEngine.kt` 批次 21 版本 `:416`；T2 已重写，见 Progress），AAR 异常消息丢失/文案不同即落 INVALID_PROGRAM。断言未放宽（符合纪律），但契约 §4"内存超限不截断成正确图、返回有界诊断"的具体拒因未被设备证明。

**Impact.** medium（内存上限的**可观测拒因**未验证；引擎仍返回有界拒绝、不崩，故非安全洞；但测试门红、契约判据未落地）。

**Progress（2026-10-26 批次 22 T2 + 批次 23 设备轨；core/data + 仪器化用例）.** 根因与探针已按 R3 源码级研究落定，代码与用例已改。**批次 23 设备轨已执行（2026-10-26，批次 23 E-02）：全量 20 例 19/20 绿，唯一红 P2**——P2 `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage` 实际输出 `INVALID_PROGRAM: [UNATTRIBUTED] phase prepare failed with a message-less engine error (raw=null, mallocSize=177216, mallocLimit=16777216)`（`docs/research/evidence/2026-10-26-batch-23/step02-quickjs-oom.md:112`）："消息丢失"分支已命中（`raw=null`），但读到的堆只有约 1.06% of 16 MiB，低于 90% 阈值，分类器按设计落 `UNATTRIBUTED`；**原因已明确为夹具没有稳定推满堆，不是分类器阈值问题**。P1 的 `MEMORY_LIMIT` 主路径已在设备成立（诊断含 `16777216`/`out of memory`），P3/P4/P5 绿。**这是批次 23 时点：P2 判据未达成；批次 24 设备轨 E-02 已 20/20、P2 转绿，见下方批次 24 Progress**；不得下调 90% 阈值。

- **根因（R3 `docs/research/2026-10-26-quickjs-oom-semantics.md` §0/§2/§3）.** ① 旧夹具 `s = s + s` 先撞 `JS_STRING_LEN_MAX = (1 << 30) - 1`（约 1 GiB，`quickjs.c:212`）的 rope 长度检查——堆几乎没涨，所以必然落 `INVALID_PROGRAM: InternalError: string too long`，与 16 MiB 堆上限无关。② 另一形态 `program failed in phase prepare: null` 是确定性链：堆耗尽到连 Error 对象都分配不出来时 `JS_ThrowError2` 抛 `JS_NULL`（`quickjs.c:7645-7655`）→ quickjs-kt 把 `JS_NULL` 映射为 Java `null` → `reason.toString()` 合成字符串 `"null"`（`QuickJs.jni.kt:837-843`）；旧分类器 `contains("out of memory")` 接不住。③ `throw null` / `Promise.reject(null)` / `throw 'null'` 产生同一 `"null"`，因此**不能**只凭消息判 OOM。
- **分类修复.** 新增纯函数 `core/data/.../visual/VisualJsFailureClassification.kt`：类型优先（`QuickJsInterruptedException` → 协作超时，先于任何消息匹配）→ 消息严格前缀 `InternalError: out of memory` → `"not defined"` → 消息丢失哨兵（`null`/`"null"`/`Invalid evaluation handle: -1`）且 `memoryUsage.mallocSize >= 90% mallocLimit` 才归堆上限 → 否则内部 `UNATTRIBUTED`。90% 阈值与推导写入代码注释（R3 §6.3），不得为设备变绿下调。`QuickJsVisualExecutionEngine.runPhase` 只在丢失哨兵路径、`close()` 之前读取 `memoryUsage`（`JS_ComputeMemoryUsage` 不分配，R3 §2.1.4），普通 JS 错误不额外拿 `jsMutex`。**对外枚举未改**（T2 裁定）：`UNATTRIBUTED` 折成 `INVALID_PROGRAM`，诊断以 `[UNATTRIBUTED]` 开头供宿主/AIDL 区分（R3 §6.6 退化口径）——跨进程映射（T1 AIDL）需能保留该前缀或后续裁定加 `UNKNOWN`。
- **探针替换（R3 §5，仪器化用例）.** `memoryOverTheHeapBudgetIsRejectedAsAMemoryLimit` 改用 **P1**：`prepare` 内单次 64 MiB `ArrayBuffer`（分配型、必然先撞 16 MiB 堆；64 MiB < INT32_MAX 且不产生长字符串）。新增 **P2** `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage`（50 万小对象链表 → `"null"` + 堆证据归属）、**P3** `stringLengthCapIsNotAHeapOutOfMemory`（rope 翻倍负控）、**P4** `nullThrownAtLowMemoryIsUnattributedInvalidProgram`（低堆 `throw null` → `INVALID_PROGRAM` + `[UNATTRIBUTED]` 前缀；T2 裁定不新增 UNKNOWN）、**P5** `forgedOutOfMemoryMessageIsInvalidProgram`（`RangeError('out of memory')` 不得误报）。旧 1M-array 夹具已删（`fast-array` 扩容余量不可控，非确定性）。
- **JVM 覆盖.** 新增 `core/data/src/test/.../visual/VisualJsFailureClassificationTest.kt`：类型分支、严格前缀、丢失哨兵+堆证据、UNATTRIBUTED 兜底、90% 边界、未设上限不归属。**本轨（批次 22 T2 的 JVM 覆盖）未运行任何 Gradle/设备命令**（门禁由脚本串行执行），该 JVM 用例本轨结果记为待验证——**这不是 KD-70 的设备执行状态**（设备执行见下条批次 23 E-02，批次 24 结果见更下方）。
- **设备轨结果（2026-10-26，批次 23 E-02）.** 全量 20 例仅 **P2 红**；P1/P3/P4/P5 未报红（逐例 `STATUS_CODE` 见 `docs/research/evidence/2026-10-26-batch-23/step02-quickjs-oom.md:73-92`，P2 实际诊断见同文件 `:112`）。② P2 的判据仍未达成：`MEMORY_LIMIT` + `attributed by memoryUsage` 未在设备出现；该分支是"消息丢失 + 堆证据"的中央新逻辑，不能以其它四例绿替代。① P1 的 `MEMORY_LIMIT` 判据在设备成立。命令见 R3 §7.2（`:core:data:assembleDebugAndroidTest` + 逐类 `am instrument`，不要加 `-e log true`）。若 P2 仍拿不到可读 message 或堆证据，按"诊断落盘 + 归因 AAR 构件"处理，不得放宽断言。

**（2026-10-26 批次 24 T3 核对，已由批次 24 设备轨 E-02 取代）** 批次 23 的设备状态是 20 例 19/20、P2 红（`mallocSize=177216`）；"设备未复跑/待验证"旧表述在当时作废。**批次 24 设备轨 E-02 已 20/20、P2 转绿（见下方批次 24 Progress）**，因此"P2 判据未达成"不再是当前状态。KD-70 是否关闭由主会话按 Reopen condition 裁定；精确 `mallocSize` 仍未打印（断言只约束 ≥15,099,495），且只测了 x86_64/4 KB（arm64/16 KB 未测）。（以上为批次 24 T3 核对时点状态；2026-10-27 主会话已裁定关闭，见下方收口 Progress。）

**Progress（2026-10-26 批次 24 T2；core/data/visual + 仪器化用例；未跑 Gradle/设备）.** R2 缺口已补齐并落盘为 `docs/research/2026-10-26-quickjs-memory-accounting.md`（开工时该文件不存在；按批次 24 基线 §6 + R3 推导，源码行号用 GitHub API 对钉版 commit 实读）。**新结论：`mallocSize=177216` 不是失败点，而是 OOM 之后异常展开时的释放结果**——① 主机制（引用计数）：`JS_CallInternal` 异常出口先把 `ret_val = JS_EXCEPTION`，再走 `done:` 释放该帧**所有局部变量与栈值**（`quickjs.c:20690`、`:20699-20709`）；`__JS_FreeValueRT` 对引用计数归零的对象立即 `free_zero_refcount`（`:6474-6485`），`free_object` 逐个 `free_property`（`:6341-6353`），属性里的 `next` 引用被释放后**级联**清空整条链表（`free_property` 调 `JS_FreeValueRT`，`:6097-6117`）。旧夹具的链只由局部 `head` 与节点 `next` 属性持有 → 局部释放即整链同步释放，发生在 `JS_Eval` 返回前；引擎随后才读 `memoryUsage`（`QuickJsVisualExecutionEngine.kt:447-467`）。② 次要同向机制：每次 `JS_NewObjectFromShape` 分配前会调 `js_trigger_gc`（`:5613-5620`，`:5619`），异常展开后的 job 排水阶段（`QuickJs.jni.kt:590`）也会再触发 GC；即使引用计数路径不存在，读数同样会回落。③ 批次 22 与批次 23 两次设备执行读到同一个 177216（`docs/research/2026-10-26-batch-22-record.md:117`、`:180`；`docs/research/evidence/2026-10-26-batch-23/step02-quickjs-oom.md:92`），与"同步释放后的固定水位"一致；批次 23 E-02 `:101` 已并列"未推满堆 / 错误处理前已回收"两种解释，本条按源码链把后者落成主解释。**因此旧 P2 形状（未锚定链表 + 比值证据）不可能成立，换夹具是唯一出路，不是调阈值。**

- **夹具修复（仪器化 P2）.** `HEAP_LIST_SOURCE` 保持 50 万轮、单轮一个小对象、无大 realloc、无长字符串，但每轮把最新头节点写到 `globalThis.__visualHeapProbeHead`：给链增加一个**根引用**，异常展开释放局部 `head` 时引用计数不归零，链不会被级联回收；全局对象同时是 GC 根（`JS_MarkContext` 标记 `ctx->global_obj`，`quickjs.c:2717-2728`）。预期设备形态写进用例 KDoc：消息丢失（`raw` 为 `"null"` 或 Java null）+ `mallocSize >= 90% × 16777216` + `mallocLimit=16777216` → `MEMORY_LIMIT`，诊断含 `attributed by memoryUsage` 与 `PENDING_CALIBRATION`，且 `mallocSize` 不得为 `-1`（证明快照真的取到）。若设备仍读到约 177216，说明锚定未生效或 AAR 与 tag 源码行为不一致，实际数字落盘后继续查，**不得改成"有界拒绝即可"**。
- **分类器口径（R2 §3）.** 90% 阈值与 `MEMORY_LIMIT` 映射**不变**；新增内部证据状态 `VisualJsCalibration`（`VisualJsFailureClassification.kt:87-100`）：以比值为**主证据**的归因带 `PENDING_CALIBRATION`（诊断追加同名字面量），引擎原文 `InternalError: out of memory` 的直接证据不带标记。这是把"比值尚未校准"如实暴露给宿主/设备日志，不放宽也不收紧任何门值；未新增对外枚举。
- **JVM 矩阵（`VisualJsFailureClassificationTest`）.** 新增/更新：批次 23 E-02 数值形状（`177216 / 16777216` → `UNATTRIBUTED`、不带标记）、比值边界 90% 含边界 → `PENDING_CALIBRATION`、直接前缀 → `NONE` 且诊断不含标记、`Invalid evaluation handle: -1`/Java null 的近上限归因带标记、低堆/无快照兜底不带标记。
- **设备状态（T2 时点；已被下方批次 24 设备轨 E-02 取代）.** 批次 24 T2 **未运行 Gradle、未编译、未执行 JVM/仪器化/设备用例**；T2 交付时 P2 新形状为 **UNVERIFIED**（此前设备状态是批次 23 E-02 的红）。**批次 24 设备轨 E-02 已执行：20/20、P2 绿（见下）**；设备复跑按 R4 落盘。**KD-70 保持 open，等待主会话按 Reopen condition 裁定**。（T2 时点状态；2026-10-27 主会话已裁定关闭，见下方收口 Progress。）

**Progress（2026-10-26 批次 24 设备轨，E-02；supersedes 上方"P2 红 / 新形状 UNVERIFIED"的记录时点表述）.** 批次 24 T2 的锚定夹具在设备上复跑 `QuickJsVisualExecutionEngineInstrumentedTest` 全量 **20/20**：`OK (20 tests)`、`Time: 8.275`、failures/skipped 均 0、7 类崩溃签名 0，逐例 `INSTRUMENTATION_STATUS_CODE` 1→0（emulator-5554，API 34，x86_64，PAGE_SIZE 4096，测试 APK sha256 `8ff654e95686ea5ef89a73cc797f6ae7688c720877b01a50700dff6d5d605281`）。P2 `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage` **转绿**：由通过断言约束，reason=`MEMORY_LIMIT`、诊断含 `attributed by memoryUsage`/`mallocLimit=16777216`/`PENDING_CALIBRATION`、`mallocSize != -1`，即 `mallocSize >= 15,099,495`（≥90%）；**精确 `mallocSize` 未打印，是事实缺口（EXACT VALUE UNVERIFIED）**，不能写成已读到 99% 或已升校准。**该结果不等于 KD-70 关闭**：① 只测 x86_64/API 34/4 KB，R2 §5 要求的 arm64/16 KB 各一次未做（step02 §7 自认）；② P2 用例总耗时 98 ms（logcat `started 13:33:46.398` → `finished .496`），用例内 `cooperativeTimeoutMillis=10_000`，不是默认 100 ms，故本次不是"贴着生产 100 ms 协作期限"通过；若把该夹具放回默认 100 ms 或跑在慢速/arm64 设备，存在先落 `PHASE_TIMEOUT` 而非 OOM 的风险；③ Reopen condition（`MEMORY_LIMIT` 或裁定等价判据转绿 + `memoryUsage`/诊断落盘）是否满足由主会话裁定。（设备轨时点状态；2026-10-27 主会话已裁定关闭，见下方收口 Progress。）证据：`docs/research/evidence/2026-10-26-batch-24/step02-quickjs-oom.md:10`/`:57-61`/`:141-149`、`00-index.md:45`/`:58-61`；原始日志 `build/device-track-batch24/step02-kd70.log`（`OK (20 tests)`）、`step02-kd70-logcat.txt:116-118`。

**残余边界（批次 24 复核 #6；不改变本次 kind/reason，但不得当成已消除）.** ① 真实程序把唯一引用放在局部/栈上时，OOM 后仍落 `UNATTRIBUTED` → `INVALID_PROGRAM`（R2 §3.3；锚定夹具只是测试仪器）；② 丢失哨兵 + 堆 ≥90% 时，程序故意的 `throw null` 也会被归 `MEMORY_LIMIT`（`VisualJsFailureClassificationTest` 的 at-threshold 用例明示；P4 只覆盖低堆）；③ 可读 OOM 文案若不是严格的 `InternalError: out of memory` 前缀会落 `INVALID_PROGRAM`。宿主侧必须保留/消费 `[UNATTRIBUTED]` 前缀（AIDL/协调器），当前协调器无 `VisualExecutionEngine` 注入、`UNATTRIBUTED` 命中 0——该门已登记到准入清单 P-11/A-01，落地前不得把 `[UNATTRIBUTED]` 诊断当“已分类”。

**Progress（2026-10-27 主会话收口裁定，批次 24 记录 §9；本条关闭）.** 关闭依据：批次 24 设备轨 E-02 全量 20/20、P2 `heapExhaustionWithLostErrorMessageIsAttributedByMemoryUsage` 转绿——reason=`MEMORY_LIMIT`、诊断含 `attributed by memoryUsage`/`mallocLimit=16777216`/`PENDING_CALIBRATION`、`mallocSize != -1`（`docs/research/evidence/2026-10-26-batch-24/step02-quickjs-oom.md:141` 判定、`:57-61` 断言口径）——满足 Reopen condition（"`MEMORY_LIMIT`（或经裁定的等价判据）转绿，且 `memoryUsage`/诊断证据落盘"）。裁定原文：批次 24 记录 §9 主会话收口（`docs/research/2026-10-26-batch-24-record.md:396` 起，裁定条 `:403`）。**关闭时的缺口注明（不随关闭作废，按裁定"另立"跟踪）**：① **精确 `mallocSize` 未打印（EXACT VALUE UNVERIFIED）**——由通过断言只能约束 `mallocSize >= 15,099,495`（≥90%×16 MiB），不能写成已读到 99% 或已升校准，超参仍 `PENDING_CALIBRATION`；② 只测 x86_64/API 34/4 KB，arm64/16 KB 各一次未做——按裁定"剩余 ABI 覆盖（arm64/16KB）另立，不阻断本条关闭"，由 4E.2 准入清单 P-02/R-03 与 R4 `docs/research/2026-10-27-arm64-16kb-coverage.md` 继续跟踪（ps16k 为 x86_64，不能替代 arm64）；③ P2 用例总耗时 98 ms、夹具内 `cooperativeTimeoutMillis=10_000`（非默认 100 ms），慢速/arm64 设备存在先落 `PHASE_TIMEOUT` 而非 OOM 的风险（R4 §0-3 已把"判据与批次 24 完全一致、不放宽"带入 ps16k 重跑判据）。**本条关闭不改变 JS 不广告与 4E.2/契约 §9 启用门**；`[UNATTRIBUTED]` 前缀消费门（P-11/A-01）继续有效。

**Fix direction.** ① 用能真实触达 QuickJS 堆上限的夹具（分阶段分配并在 OOM 前读 `memoryUsage` 记录）重跑设备用例；② 若 AAR 在 x86_64 的 OOM 必然先报 `string too long`/`prepare null`，则把契约/用例改为"有界拒绝 + 诊断保留原因"的可证判据，需先裁定（不得直接放宽断言）；③ `classifyJsException` 增加结构化诊断或明确已知边界。**（批次 22 T2 已按 ①/③ 落分类器与 P1–P5 探针；批次 23 E-02 的设备执行已完成：P1 绿、P2 红且诊断落盘；批次 24 T2 已按 ① 把 P2 换成根引用锚定夹具（R2 §4）并给比值归因加 `PENDING_CALIBRATION` 标记；批次 24 设备轨 E-02 已 20/20、P2 绿，但只覆盖 x86_64/4 KB 且精确 `mallocSize` 未打印；②的等价判据由 E-02 的 `MEMORY_LIMIT` 设备证据承担，是否关闭已于 2026-10-27 由主会话裁定关闭，见上方收口 Progress。）**

**Reopen condition.** 设备用例以 `MEMORY_LIMIT`（或经裁定的等价判据）转绿，且 `memoryUsage`/诊断证据落盘后关闭。

## KD-71 (open, 2026-10-26 登记（批次 21 T3 复核发现 #22；低）) · `QuickJsVisualExecutionEngine` close/execute 竞态：closed 检查与 `currentTransaction.set` 之间的窗口

**Symptom（读码，登记时）.** `execute()` 先 `closed.get()` 检查，再 `inFlight.compareAndSet`，之后才 `currentTransaction.set(transaction)`（`QuickJsVisualExecutionEngine.kt:152-158`）；`close()` 只 `currentTransaction.get()?.forceAbandon()`（`:203-207`）。并发 close 恰好落在窗口内会读到 null，不强弃；随后 execute 仍会创建 QuickJs 并执行/返回成功，与"关闭后 execute 立即回 ENGINE_CLOSED"的终端语义不完全一致。

**Impact.** low（概率低；需要 close 与 execute 精确并发，且 close 后仍可能有一次执行）。

**Progress（2026-10-26 批次 22 T6，core/data 引擎 + 仪器化用例；非 Gradle、非设备）.** 修复已落；设备执行见下方批次 23 Progress。

- **单锁状态机（窗口本体）.** `closed` 与 `inFlight` 改为同一把 `stateLock` 保护：`execute` 在同一临界区内"判 closed → 判 BUSY → 登记 inFlight → schedule watchdog"（`QuickJsVisualExecutionEngine.kt:157-163`、`:201-217`）；`close()` 在同一临界区内"置 closed → 取走在飞事务 → `watchdog.shutdownNow()`"（`:267-284`）。两种顺序被压成二选一：close 先到 → 登记直接回 ENGINE_CLOSED；登记先到 → close 必能取到该事务。closed 判定与事务登记不再可分，原 `:152-158` 的窗口消失。对外错误枚举零改动。
- **关闭语义补全（不只登记）.** 只修登记窗口仍留下第二种"关闭后成功"：close 取到事务时它可能尚未创建活引擎（例如卡在 `measurer.measure`），旧 `forceAbandon` 无引擎可强弃，事务随后照常 render。现在事务带 `abandoned` 标志，阶段在创建上下文前（`:402-410`）与登记活引擎后（`:417-424`）各查一次；`forceAbandon` 的 `abandoned.set → liveEngine.getAndSet` 与阶段的 `liveEngine.set → abandoned.get` 构成 Dekker 握手，至少一侧看见另一侧。`close()` 强弃后**有界等待**事务收尾（`:283`，上界 `hardDeadlineMillis + 500ms`；#240 的同步 JNI 卡死等不到，超时按 accept-the-leak 返回，不阻塞 @After）；并发 close 的后来者同样等在飞事务。事务最终结果与 `closed=true` 在同一临界区判定（`:247-256`）：close 先到 → 本可成功的渲染也折成 ENGINE_CLOSED；事务判定先到 → 结果在关闭前已成立。
- **并发用例（`QuickJsVisualExecutionEngineInstrumentedTest`）.** ① 确定性红例 `closeDuringAnInFlightTransactionWaitsAndRejectsIt`（`:545`，批次 22 记录时点行号）：用 `GatedMeasurer`（`:115`）把事务卡在 measure（此时登记必已完成、prepare 上下文已关闭、`liveEngine=null`——正是旧实现强弃失效的形状）；close 在另一线程调用，断言 200ms 内不得返回（旧实现立即返回 = 红），放行后断言 execute 回 ENGINE_CLOSED（旧实现回 Rendered = 红）、close 返回后再 execute 仍 ENGINE_CLOSED。② 32 轮无协调压测 `concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome`（`:617`，批次 22 记录时点行号）：只允许 Rendered（close 前已完成）或 ENGINE_CLOSED，任何 BUSY/库异常穿透/挂死均为红；每轮 close 返回后再 execute 必须 ENGINE_CLOSED。既有 issue #240 观察用例与 `closingIsIdempotentAndExecuteAfterCloseIsRejected` 保留不改。
- **测试纪律.** 批次 22 未运行任何 Gradle/设备命令（门禁由主会话脚本串行执行）；上述"旧实现红"是按旧代码路径（`finished` 不参与阶段判定、close 无等待）的确定性推演，**不是执行证据**。批次 23 设备轨已执行，见下方 Progress。

**Progress（2026-10-26 批次 23 设备轨，E-02）.** `QuickJsVisualExecutionEngineInstrumentedTest` 全量 20 例真跑（emulator-5554，API 34，x86_64，APK sha256 `5ef6ff9b46165517c0196f6b2926e8a07b64cf1fcfa8e62c5c82eb6901e72e75`），KD-71 的两条用例均 `STATUS_CODE 0`：`closeDuringAnInFlightTransactionWaitsAndRejectsIt 0`、`concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome 0`（逐例见 `docs/research/evidence/2026-10-26-batch-23/step02-quickjs-oom.md:73-92`；原始日志 `build/device-track-batch23/step02-quickjs-oom.log`）。Reopen condition 的两条设备判据（close 后不再有成功执行、in-flight 事务被强弃后关闭；32 轮压测全绿）已满足；是否关闭待主会话按批次记录回链裁定。同批唯一红是 KD-70 的 P2，与本条无关。

**Fix direction.** 已落：`stateLock` 单临界区承载 closed/inFlight；close 强弃 + 有界等待；阶段 abandoned 双查 + 最终结果线性化判定。设备执行已完成（见上）；若后续在更慢设备上 `closeDuringAnInFlightTransactionWaitsAndRejectsIt` 的 200ms 等待窗口与调度冲突，应放宽的是"等待观察窗口"（时间常量），**不得**放宽"execute 必须 ENGINE_CLOSED"的断言。

**Reopen condition.** 新增并发用例在设备上证明 close 后不再有成功执行、且 in-flight 事务被强弃后关闭（`closeDuringAnInFlightTransactionWaitsAndRejectsIt` + 压测 32 轮全绿，日志落盘）后关闭。

**Progress（2026-10-29 批次 30 T12 状态核对；只读，零 Gradle/零设备/零 git 写；状态保持 open）.** 本批计划 T7（"close/execute 竞态（CAS 或锁），配可复现红测"）在 T12 核对时点**未见本批新增证据**：`git status --porcelain -- core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngine.kt core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/visual/QuickJsVisualExecutionEngineInstrumentedTest.kt` 输出为空（两文件无本批改动），本批也没有 `docs/research/evidence/2026-10-29-batch-30/` 设备证据目录。既有设备证据沿用前批：批次 23 E-02 跑 20 例、两条 KD-71 用例 `STATUS_CODE 0`；批次 28 T1 设备复跑引擎整包 `OK (26 tests)`（`docs/research/evidence/2026-10-28-batch-28/step02-scenev1-coredata-suites.md:51`，含本条的 `closeDuringAnInFlightTransactionWaitsAndRejectsIt` 与 `concurrentCloseAndExecuteRoundsNeverReportAnUnexpectedOutcome`）。因此本轨**维持 open**："判据已满足、待主会话裁定"的既有结论本批**未重跑、未复核**，也不因本批出现新证据而改写；**不得据本批读成已关闭**。若 T7 后续落码，应以该轨的新红测/设备证据另起 Progress 块回填，本块不代替它。**（2026-10-29 复核修复轮更正）** 本块"两文件无本批改动 / 本批没有证据目录"只对 T12 时点（约 01:11）成立，**不是现树事实**：`git diff --stat` 实测 `QuickJsVisualExecutionEngine.kt` **+20**、`QuickJsVisualExecutionEngineJvmTest.kt` **+58**（T7 新增 `beforeRegistrationHookForTest` 与确定性竞态用例）；`docs/research/evidence/2026-10-29-batch-30/` 已落盘 **11 份**设备证据（02:25–02:41）。T7 的 JVM/设备**执行**证据仍未出现（本轮零 Gradle/零设备），故本条仍 open；但"已落码"不得再写成"无改动"。

**Progress（2026-10-29 批次 30 T7 落码 + 记录员复核；只读，零 Gradle/零设备/零 git 写；状态保持 open）.** T7（计划任务 7"close/execute 竞态，配可复现红测"）已落码：`QuickJsVisualExecutionEngine.kt:196` 新增测试专用缝 `beforeRegistrationHookForTest`（生产恒 null），调用点 `:234`；`QuickJsVisualExecutionEngineJvmTest.kt` **+58 行 / 3 例**，其中 `close landing between the closed precheck and registration still rejects the execution` 用该缝把 close 精确落在 closed 预检与登记之间。记录员实读确认代码在树。**未执行**：该 JVM 用例未跑（仓内无结果 XML/日志），设备轨十步也未包含 `QuickJsVisualExecutionEngineInstrumentedTest`；"修复前应红 / 修复后必回 ENGINE_CLOSED"是代码路径推演，**不是执行证据**。既有设备证据仍为批次 23 E-02（20 例、两条 KD-71 用例 `STATUS_CODE 0`）与批次 28 T1（引擎整包 `OK (26 tests)`），本批未重跑。**本条维持 open**：要关闭需至少一次新用例的实际执行（JVM 或设备）落盘，且 Reopen condition 的两条语义判据仍成立。

**Progress（2026-10-29 批次 31 T5 台账同步；只读复读 + 本机 JUnit XML；零 Gradle/零设备/零 git 写；状态保持 open）.** 本批**未新增设备证据**（`docs/research/evidence/2026-10-29-batch-31/` 实测不存在：`ls` = `No such file or directory`），也未上 `QuickJsVisualExecutionEngineInstrumentedTest`；但**"新用例未执行"的旧表述已被本机 JUnit 产物否证**：`core/data/build/test-results/testDebugUnitTest/TEST-com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineJvmTest.xml`（mtime **2026-10-29 04:06:29 +0800**，晚于用例源 01:17 与引擎源 01:11）实测 `tests="3" skipped="0" failures="0" errors="0"`，三例含本条的 `close landing between the closed precheck and registration still rejects the execution`。**证据等级如实**：该 XML 在 `build/`（`.gitignore` 忽略），不是仓内证据，仓内也无可指向这次执行的命令行/门禁日志，故只能记"本机 JVM 执行绿、命令与门禁归属 UNVERIFIED"。生产缝仍在树（`QuickJsVisualExecutionEngine.kt:196` `beforeRegistrationHookForTest`、`:234` 调用点）。**本条保持 open**：Reopen condition 要求设备侧两条并发用例（close 后不再成功执行 + 32 轮压测）在新源上成立；本批未复跑，既有设备证据仍为批次 23 E-02（20 例、两条 `STATUS_CODE 0`）与批次 28 T1（引擎整包 `OK (26 tests)`）。上方批次 30 T12 块"两文件无本批改动 / 本批没有证据目录"按补记惯例保留为**批次 30 T12 时点**表述，已由同块更正与批次 30 设备轨取代，不得当现树事实。

**（2026-10-29 批次 31 设备轨回填更正）** 本块"`docs/research/evidence/2026-10-29-batch-31/` 实测不存在"只对 T5 快照成立：该目录现已有 **15 份**设备证据（06:20–06:54）。但设备轨仍未包含 `QuickJsVisualExecutionEngineInstrumentedTest`，本条的关键执行缺口（新源上的设备侧两条并发用例 + 32 轮压测）**仍未验证**，故"本条保持 open"不变；仅"证据目录不存在"一句作废。

**Progress（2026-10-29 批次 33 T6 状态核对；只读，零 Gradle/零设备/零 git 写；状态保持 open）.** 本批 T6 时点**无新增 KD-71 证据**：`docs/research/evidence/2026-10-29-batch-33/` 不存在（`ls` = `No such file or directory`），设备轨在 T6 之后串行执行，本块不能代替它。既有本机 JUnit 旁证仍在：`core/data/build/test-results/testDebugUnitTest/TEST-com.tingyun.smartmistakebook.core.data.visual.QuickJsVisualExecutionEngineJvmTest.xml` mtime **2026-10-29 12:38:58**、`tests="3" skipped="0" failures="0" errors="0"`（含 `close landing between the closed precheck and registration still rejects the execution`），属 build/ 旁证、非仓内命令日志、命令归属 UNVERIFIED。**本条保持 open**：Reopen condition 要求新源上设备侧两条并发用例 + 32 轮压测；本批设备轨若包含该套件以其结果为准，T6 不预判。

**记录回链（2026-10-29 批次 33 记录员；只读；状态保持 open）.** 本批设备轨 14 套件（`docs/research/evidence/2026-10-29-batch-33/`，10 份）**不含** `QuickJsVisualExecutionEngineInstrumentedTest`；现树 `QuickJsVisualExecutionEngine.kt` 与 `QuickJsVisualExecutionEngineJvmTest.kt` 对 HEAD 零 diff（`git status --porcelain` 为空）。Reopen condition（新源上设备侧两条并发用例 + 32 轮压测）仍未满足；本条保持 open。记录见 `docs/research/2026-10-29-batch-33-record.md` §7 #14。

## KD-72 (open, 2026-10-26 复核返修 #5/#10 登记；中) · dependency locking 已配置但仓库无已跟踪锁文件：DEFAULT 模式下锁门未生效，且已生成的锁与 HEAD/提交面不一致

**Symptom（读码 + 只读命令，2026-10-26 批次 23 复核更新）.** 根 `build.gradle.kts` 的 `subprojects { dependencyLocking { lockAllConfigurations() } }` 已声明；工作树已生成 13 个**未跟踪**的 `gradle.lockfile`（app、core/model、core/domain、core/database、core/data、core/export、core/ui、core/markdown、feature×5；另有 `settings-gradle.lockfile`），`git ls-files '*gradle.lockfile'` 仍为 0，`:benchmark` 无锁。锁生成于他线未提交的 core/ui/core/export/feature/tutor/settings 构建改动之上，且按 `dependencies` 报告任务生成的锁只有 `debugCompileClasspath` + 工具链配置，没有 test/androidTest/runtime。`DEFAULT`（不是 `LENIENT`；默认模式由 Gradle 9.6.1 字节码确认）对"完全没有锁状态"放行，但对"有锁状态却不匹配"在解析期判红——因此这批锁既不能原样提交（core/ui/app 锁缺 HEAD 仍用的 `richtext-commonmark`；core/export 锁多出 HEAD 没有的 `kotlinx-serialization-json`），也不能算作锁门已生效。`buildscript`/`pluginManagement` 未锁；当前真正生效的仍只有 `tools/ci/check_dependency_pins.py` 的 quickjs 三构件 sha256 与两条版本事实。

**Evidence（本轮回跑）.** `python tools/ci/check_dependency_pins.py` → exit 0，`resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']`；`python -m unittest discover -s tools/tests -p "test_check_dependency_pins.py" -t tools -v` → 14 tests OK。两者都只证明"钉版门"，不证明锁门。**注意解析图来源**：脚本读的是 `app/build/intermediates/incremental/lintVitalAnalyzeStrictOfflineRelease/strictOfflineRelease-artifact-dependencies.xml`（AIDL 修复前的旧构建产物）；本轮禁跑 Gradle，解析图未刷新，因此"resolved 2.3.21"是旧图事实，不是修复后重解析的证据。批次 22 T3 的"dependency locking 已启用"只能记为**部分属实**。

**Evidence（2026-10-26 批次 23 只读复核）.** `git status --porcelain -- '*.lockfile'` → 13 个 `?? <module>/gradle.lockfile` + `?? settings-gradle.lockfile`；`git ls-files '*gradle.lockfile'` → 0；`ls benchmark/gradle.lockfile` → 不存在。逐模块配置集合（从锁文件右侧提取）：core/data/database/export/ui/markdown 与 feature×5 只有 `debugCompileClasspath`（及 annotationProcessor/KSP/kotlinCompilerPluginClasspath 等工具配置）；core/domain/model 只有 `compileClasspath`；app 只有 `localFirstDebugCompileClasspath`/`localFirstDebugRuntimeClasspath`/`localFirstDebugUnitTestCompileClasspath`，无 androidTest/unitTestRuntime。`git show HEAD:core/ui/build.gradle.kts` 仍为 `implementation(libs.richtext.commonmark)`，而 core/ui 与 app 锁均无 `com.halilibo.compose-richtext:richtext-commonmark`；`git show HEAD:core/export/build.gradle.kts` 无 serialization 依赖，而 core/export 锁含 `kotlinx-serialization-*:1.9.0=debugCompileClasspath`。**这些是静态事实；本轮未运行 Gradle/CI，解析期失败行为依据 Gradle 9.6.1 官方文档（`DEFAULT`："Existing entries in the lock state must be matched in the build"、"A version mismatch or missing resolved module causes a build failure"、"Resolution result must not contain extra dependencies compared to the lock state"）。**

**Progress（2026-10-26 批次 23 T1 复核修复；只读 + 构建/CI 文本，未运行 Gradle）.** 已落三件：

- `build.gradle.kts` 的注释改为实际状态（13 个未跟踪锁、DEFAULT、不能原样提交），并新增聚合任务 `lockAllDependencies`（每个子项目的 `dependencies` + `resolveLockableClasspaths`）；后者解析所有 `*CompileClasspath`/`*RuntimeClasspath`，补上 test/androidTest/runtime 覆盖。**该任务未运行 Gradle 验证；首次门禁轨执行时以实际解析结果为准。** 这同时收窄复核发现 #8 的不对称：此后新增/升级 androidTest/testRuntimeOnly 依赖也会进入锁状态（前提是锁按新任务重生成并入库）；`buildscript`/`pluginManagement`、file AAR 与 detached 配置仍不锁（R3 §2.7 的已知残余，不在本条修复范围）。
- 新增 `tools/ci/check_lockfiles.py` + `tools/tests/test_check_lockfiles.py`（9/9 通过：`python -m unittest discover -s tools/tests -p "test_check_lockfiles.py" -t tools -v`）。语义：锁尚未落地时只提示；一旦有第一个跟踪锁，强制每个 included project 都有锁、Android application/library 有 unitTest/androidTest 配置、JVM 有 test 配置，并拒绝未 include 模块的锁。实跑当前仓库 → exit 0，提示 13 个未跟踪锁不能原样提交，并点名 `:benchmark` 缺锁（DEFAULT 下不会报错）。
- CI 增加两个 step：`Dependency lockfile manifest and coverage gate`（始终跑静态脚本）与 `Dependency lockfiles are up to date`（仅当有跟踪锁时跑 `lockAllDependencies --write-locks` + `git status --porcelain -- '*.lockfile'` 判红）。**CI 未运行；Gradle step 的实际行为 UNVERIFIED。**

**Progress（2026-10-26 批次 24 T3，只读核对；无 Gradle/git 写）.** 复核确认"仓库无任何锁文件 / 默认 `LENIENT`"的旧表述作废：`git status --porcelain | grep -i lockfile` → 13 条 git 可见的未跟踪锁条目（含 `settings-gradle.lockfile`）；盘上实为 **14 个** `gradle.lockfile`（`core/markdown/gradle.lockfile` 因整个目录未跟踪被折叠成 `?? core/markdown/`）；`git ls-files '*gradle.lockfile'` → 0；`benchmark/gradle.lockfile` 不存在；根 `build.gradle.kts:19-33` 写明默认 `LockMode.DEFAULT`（不是 `LENIENT`）且这批锁不能原样提交；`python tools/ci/check_lockfiles.py` → exit 0，输出"13 个未跟踪锁文件…不能原样提交；缺锁 project：:benchmark（DEFAULT 模式下不会报错）"。这批锁生成于他线未提交的构建改动、与 HEAD 不一致，**不得原样 `git add`**；等干净树用 `lockAllDependencies --write-locks` 重生成后再单独提交。**KD-72 保持 open**，剩余条件见下。

**仍未闭合（KD-72 保持 open）**：① 锁必须在**干净、待提交的树**上重新生成（当前锁与 HEAD 不一致，且 hot-zone build 改动不能随本批提交）；② `:benchmark` 缺锁、`:core:markdown` 模块与锁都不能单独入库（R3 §6 P5）；③ 一次负控（人为抬版/加依赖在解析期红）未执行；④ 现有 13 条 git 可见锁条目（盘上 14 个文件）不得原样 `git add`。**T1 不能写成已完成。**

**Impact.** medium（供应链：POM 静默抬版/新增传递依赖在解析期不会被拦；当前唯一的漂移被 sha256/版本门覆盖，但覆盖范围是定向的，不是全图；且现有锁与提交面不一致，误提交会红 CI）。

**Fix direction.** 主会话/门禁轨在他线 build 改动落定后，于干净树执行 `./gradlew --no-configuration-cache --no-parallel --console=plain lockAllDependencies --write-locks`，审 diff 后把 14 个 included project 的锁与对应源码一起提交；`:core:markdown` 未入库时先按 R3 P5 排除，等模块落地再补。随后跑 `python3 tools/ci/check_lockfiles.py` 与 CI 的写锁-diff step，并按 R3 §5.5 做负控。在锁文件入库前，不得把 T3 写成"依赖锁定已完成"。

**Reopen condition.** 仓库内每个 subproject 都有 `gradle.lockfile`，且一次负控（人为抬高某个传递依赖版本）在解析期被锁状态判红后关闭。

**Progress（2026-10-29 批次 30 T7 复核 + 记录员实测；只读，零 Gradle/零 git 写；状态保持 open）.** 按批次 29 R3 分支 B 的 W1–W7 逐项复核，结论**仍不可行、零动作**：**W1 六文件 6/6 不干净**（记录员实测 `git status --porcelain`：`settings.gradle.kts`、`core/ui/build.gradle.kts`、`feature/tutor/build.gradle.kts`、`core/export/build.gradle.kts`、`.gitignore` 均 M，`core/markdown/build.gradle.kts` 为 `??`）；**W2 树未收口**（记录时点 737 行 = 236 M + 501 ??，他线在飞）；**W3 暂存区无锁**（staged = 0）；**W4 跟踪锁 0**，盘上 `*.lockfile` **14**（13 个 `gradle.lockfile` + `settings-gradle.lockfile`），git 可见未跟踪条目 **13**（`core/markdown/gradle.lockfile` 折叠在 `?? core/markdown/` 内），全部不得 `git add`；**W5 `:benchmark` 无锁**；**W6 负控从未执行**；**W7 Gradle 未跑**。本批禁改 hot-zone build 文件，故无任何锁文件动作。**本条保持 open。**（口径澄清：T7 报告的"盘上 14"成立，指 `*.lockfile` 总数；只数 `gradle.lockfile` 为 13。两者不是矛盾。）

**Progress（2026-10-29 批次 31 T5 锁窗口复核；只读，零 Gradle/零 git 写；状态保持 open）.** 本批计划任务 11 只要求复核窗口、不做动作。本轮开工快照实测：`git status --porcelain | wc -l` = **696**（` M` **221** + `??` **475**）、`git diff --cached --name-only | wc -l` = **0**；`git ls-files '*lockfile' | wc -l` = **0**；盘上 `find . -maxdepth 3 -name "*.lockfile" -not -path "./.worktrees/*" | wc -l` = **14**；`ls benchmark/gradle.lockfile` = `No such file or directory`。**W1 六文件 6/6 不干净**（逐文件 `git status --porcelain`：`settings.gradle.kts` M、`core/ui/build.gradle.kts` M、`feature/tutor/build.gradle.kts` M、`core/export/build.gradle.kts` M、`.gitignore` M、`core/markdown/build.gradle.kts` `??`）；**W6 负控仍未执行、W7 Gradle 未跑**。**干净树重生成锁的窗口仍不满足 → 零动作，本条保持 open**；13 条 git 可见未跟踪锁（`core/markdown/gradle.lockfile` 折叠在 `?? core/markdown/`）不得 `git add`。

**Progress（2026-10-29 批次 33 T6 锁窗口复核；只读，零 Gradle/零 git 写；状态保持 open）.** 本批硬约束"锁文件不提交"，故零动作。T6 实测：`git ls-files '*lockfile' | wc -l` = **0**；`git status --porcelain | grep -i lockfile | wc -l` = **13**（`app`、`core/{data,database,domain,export,model,ui}`、`feature/{capture,library,profile,review,tutor}` 各一 + `settings-gradle.lockfile`，全部 `??`）；盘上 `find . -maxdepth 3 -name "*.lockfile" -not -path "./.worktrees/*" | wc -l` = **14**（多出 `core/markdown/gradle.lockfile`，折叠在 `?? core/markdown/` 内）；`ls benchmark/gradle.lockfile` = `No such file or directory`。**W6 负控未执行、W7 Gradle 未跑**（本轨纪律）。干净树重生成锁的窗口仍不满足 → 本条保持 open，13 条 git 可见未跟踪锁不得 `git add`。

**记录回链（2026-10-29 批次 33 记录员；只读；状态保持 open）.** 记录员实跑：`git ls-files '*lockfile'` = **0**；`git status --porcelain | grep -c 'gradle.lockfile'` = **13**；`find . -maxdepth 3 -name '*.lockfile' -not -path './.git/*' -not -path './.worktrees/*'` = **14**；`ls benchmark/gradle.lockfile` = `No such file or directory`。W6 负控未执行、W7 Gradle 未跑；本批零锁文件动作。本条保持 open。记录见 `docs/research/2026-10-29-batch-33-record.md` §1.3/§7 #14。

**Progress（2026-10-29 批次 34 T6 复核 + 记录员复核；只读，零 Gradle/零 git 写；状态保持 open）.** 本批硬约束"锁文件不提交"，故零动作。T6 开工快照：`git status --porcelain | wc -l` = **692**（` M` **217** + `??` **475**）、暂存无锁；W1 六文件 6/6 不干净、W2 树未收口、W3 暂存无锁、W4 跟踪 0/盘上 14/git 可见 13、W5 `:benchmark` 缺锁、W6 负控从未执行、W7 Gradle 未跑 ⇒ 判定仍为**分支 B（不可行）**。记录员在写盘前复核：工作树已增到 **727**（**246 M + 481 ??**，含本批与其他在飞改动），`git ls-files '*lockfile'` = 0、git 可见 13、盘上 14、`benchmark/gradle.lockfile` 不存在。干净树重生成锁的窗口仍不满足 → 本条保持 open；13 条 git 可见未跟踪锁与盘上多出的 `core/markdown/gradle.lockfile` 一律不得 `git add`。记录见 `docs/research/2026-10-29-batch-34-record.md` §3.6。

**Progress（2026-10-30 批次 35 记录员复核；只读，零 Gradle/零 git 写；状态保持 open）.** 本批硬约束"锁文件不提交"，零动作。记录时点实跑：`git ls-files '*lockfile' | wc -l` = **0**；`git status --porcelain | grep -ci lockfile` = **13**（全部未跟踪）；盘上 `find . -maxdepth 3 -name '*.lockfile' -not -path './.git/*' -not -path './.worktrees/*'` = **14**（多出 `core/markdown/gradle.lockfile`，折叠在 `?? core/markdown/` 内）；`ls benchmark/gradle.lockfile` = 不存在。**W1 六文件 6/6 不干净**（`settings.gradle.kts`、`.gitignore`、`core/ui/build.gradle.kts`、`feature/tutor/build.gradle.kts`、`core/export/build.gradle.kts` 均 M，`core/markdown/build.gradle.kts` 为 `??`）。干净树重生成锁的窗口仍不满足 → 本条保持 open；13 条 git 可见未跟踪锁不得 `git add`。记录见 `docs/research/2026-10-30-batch-35-record.md` §10。

## KD-73 (open, 2026-10-26 复核返修 #11 登记；中) · 运行时 kotlin-stdlib 强制降回 2.3.21：字节码/API 兼容证据只覆盖 quickjs AAR，不是全解析图

**Symptom（读码 + 研究文档）.** `build.gradle.kts:47-57` 用 `eachDependency + useVersion` 把全项目运行时 `kotlin-stdlib` 从 quickjs-kt-android 1.0.15 POM 要求的 2.4.10 压回编译期 2.3.21；`gradle/libs.versions.toml:52-60` 记录该选择。R2 `docs/research/2026-10-26-dependency-governance.md` §3.1 的 88 条成员引用扫描只覆盖该 AAR（加上缓存内 78 POM/54 module 的样本），**不是完整解析图的 API 兼容证明**；文档 §3.4 自己列明运行期降级可能以 `NoSuchMethodError`/`NoClassDefFoundError` 暴露，且元数据 `mv=[2,4,0]` 的向前兼容官方口径是 preferably/mostly。本批没有新的构建/设备/R8 证据；`check_dependency_pins.py` 只校验解析版本，不校验 API 兼容性；`useVersion` 是软覆盖（strict 约束仍可赢，届时门会红）。

**Impact.** medium（若图中其它库引用了 2.4 才有的 stdlib 成员，编译期不报错、只在运行期崩；当前无证据表明存在，但也无证据排除）。

**Progress（2026-10-26 批次 23；只读 + 门禁脚本）.** 本批**没有**新增全解析图 API 兼容证据：锁文件轨只产出 13 个未跟踪锁（未入库，见 KD-72），锁状态本身不证明"图中每个 jar 的 stdlib 成员引用在 2.3.21 全部可解析"；`python tools/ci/check_dependency_pins.py` 本批复跑 exit=0（`resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']`）只校版本/三构件 sha256，不校 API。基线 §5.3 也已注明解析图来源是 AIDL 修复前的旧 `artifact-dependencies.xml`。**本条保持 open**，Reopen condition 不变。

**Fix direction.** 二选一并在门里固化：① 扩展现有扫描到完整解析图的每个 jar（对 2.3.21 stdlib 做成员引用解析，unresolved 必须为 0）；② 或按 R2 路径 B 全项目升到 2.4.10（当前被 KSP 无 2.4 兼容版本阻断）。无论哪条，都要有设备/R8 冒烟与 `NoSuchMethodError`/`NoClassDefFoundError` 的 logcat 断言，不得只靠版本门。

**Reopen condition.** 完整解析图扫描 unresolved=0 且设备/R8 冒烟无 stdlib 归因崩溃，或升级到 2.4.10 的构建/设备证据落盘后关闭。

**Progress（2026-10-29 批次 30 T7 静态全解析图扫描；只读，零 Gradle/零 git 写；状态保持 open）.** T7 轨自述已完成静态全图扫描：以 14 个 `gradle.lockfile` 为解析图（340 个唯一坐标，290 runtime/compile + 50 toolchain），落盘构件 329 个（11 个无构件；22 个非 stdlib 坐标 + stdlib 本体作索引跳过），其中 299 个含 class（**103,041 个 class**）；自写 class 常量池扫描器对 `kotlin/*` 成员引用做 2.3.21/2.4.10 双 jar 解析：**138,225 条引用在 2.3.21 全部可解析，仅 2.4.10 可解析 = 0**；53,000 条既不在 2.3.21 也不在 2.4.10（42,511 `kotlin/reflect`、8,233 `kotlin/metadata`、2,177 `kotlin/script` 等，属图内其它 jar 或 JDK）。元数据 mv 扫描：299 个构件仅 quickjs-kt-android 1.0.15 为 `mv=[2,4,0]`，其 106 class 的 328 条引用（去重 88 条）全部在 2.3.21 可解析；缓存 175 POM + 130 `.module` 中只有 quickjs 声明 stdlib 2.4.10。唯一触达链 = `core:data`（`core/data/build.gradle.kts:199`）及传递消费者 `app`，字节码层无 2.4-only 引用；**无需改构建文件**。

**裁决：本条保持 open。** 记录员只复核到前提"盘上 14 个 `*.lockfile`"；**10.3 万 class 扫描器、逐构件清单与 138,225 条引用结果均无脚本/日志/结果文件落仓**，不可独立复跑 → **UNVERIFIED**。即使静态半边成立，本 Reopen condition 还要求"设备/R8 冒烟无 stdlib 归因崩溃"，本批未做。**不得据轨自述读成已关闭。**

**Progress（2026-10-29 批次 31 T5 状态核对；只读；状态保持 open）.** 本批未新增全解析图扫描脚本/日志（`find` 全仓无 KD-73 扫描产物，`tmp/` 无 stdlib/class-scan 脚本），批次 30 T7 自述的 **10.3 万 class / 138,225 条引用仍无可复跑脚本与结果落仓 → UNVERIFIED**；设备/R8 冒烟本批未跑。**本条保持 open**，Reopen condition 不变（完整解析图 unresolved=0 且设备/R8 无 stdlib 归因崩溃，或升 2.4.10 的构建/设备证据落盘）。

**Progress（2026-10-29 批次 33 T6 状态核对；只读；状态保持 open）.** 本批无新增全解析图扫描脚本/日志：`find` 仓内（排除 `.git`/`build`/`out`/`output`）无 `*stdlib*scan*` / `*kd73*` 产物；批次 30 T7 自述的 10.3 万 class / 138,225 条引用仍无可复跑脚本与结果落仓 → **UNVERIFIED**。`python tools/ci/check_dependency_pins.py` 本轨实跑 **exit=0**，尾部输出 `resolved: quickjs=['1.0.15'] kotlin-stdlib=['2.3.21']`、`OK: quickjs checksums, upstream POM requirement and resolved runtime pins match`；该脚本只校版本与三构件 sha256，不校 API 兼容，且解析图来源仍是 `app/build/intermediates/incremental/lintVitalAnalyzeStrictOfflineRelease/strictOfflineRelease-artifact-dependencies.xml`（旧构建产物，非本批重解析）。设备/R8 冒烟本批未跑。**本条保持 open**。

**记录回链（2026-10-29 批次 33 记录员；只读；状态保持 open）.** 本批无新增全解析图扫描脚本/日志；`gradle/libs.versions.toml`、根 `build.gradle.kts`、`core/data/build.gradle.kts`、`core/database/build.gradle.kts` 对 HEAD 零 diff（`git status --porcelain` 为空），stdlib 仍 2.3.21。设备/R8 冒烟未跑；本条保持 open。记录见 `docs/research/2026-10-29-batch-33-record.md` §7 #14。

**Progress（2026-10-29 批次 34 T6 状态核对 + 记录员复核；只读；状态保持 open）.** 本批无新增全解析图扫描脚本/日志或结果落仓；批次 30 T7 自述的 10.3 万 class / 138,225 条引用仍无可复跑脚本与结果 → **UNVERIFIED**。记录员复核：`gradle/libs.versions.toml:3` 仍 `kotlin = "2.3.21"`；`tools/ci/check_dependency_pins.py` 本批未复跑（其只校版本/三构件 sha256，不校 API）。设备/R8 冒烟本批未跑（15 套件均为 debug；R-04 release 轨未执行）。→ 本条保持 open，Reopen condition 不变。记录见 `docs/research/2026-10-29-batch-34-record.md` §3.6。

**Progress（2026-10-30 批次 35 记录员复核；只读，零 Gradle/零 git 写；状态保持 open）.** 本批无新增全解析图扫描脚本/日志或结果落仓。记录员复核：`gradle/libs.versions.toml:3` 仍 `kotlin = "2.3.21"`。口径澄清：批次 35 R-04 K6 设备红的 `NoSuchMethodError: No direct method <init>(Ljava/util/Map;)V in class Lj$/util/g;`（`build/device-track-batch35/r04-k6-logcat.txt`，4 处）是 test 侧 desugared-library 与主包错位，**不构成** stdlib 2.3.21 API 兼容性的正或反证据。设备/R8 冒烟本批未跑（18 套件均为 debug；R-04 release 轨 K6 崩溃为 desugar/keep 面非 stdlib 面）。→ 本条保持 open，Reopen condition 不变。记录见 `docs/research/2026-10-30-batch-35-record.md` §10。

## KD-74 (closed 2026-10-26（批次 24 T3）；2026-10-26 复核返修 #6 登记；中) · 批次 22 设备结论（16KB 3/3、R8 三变体冒烟、备份/迁移）无日志落盘

**Symptom（只读文件系统）.** 任务书设备轨声称 16KB 3/3 全绿、R8 internal/benchmark/release 冒烟零崩溃、备份/迁移抽验全绿，但仓库内没有对应设备日志/结果：`find` 2026-10-26 08:00 之后无 device 日志（`build/device-track-batch21` 是上一批）；只能确认 `app/build/outputs/apk/strictOffline/{internal,benchmark,release}/*.apk`（09:34/09:35/09:43）与 mapping 产物存在。`NativeLibraryPageSizeInstrumentedTest.kt` 确有 3 个 `@Test`，但"在盘"不等于"设备已跑"。

**Impact.** medium（release/16KB/备份这些高风险面的事实不可复核；把产物存在当成设备通过会重演 T1"未记录全红"的问题）。

**Fix direction.** 设备轨重跑并把证据落到 `build/device-track-batch22/`：每条命令的 `am instrument` summary/`INSTRUMENTATION_CODE`、logcat 的 FATAL/ANR/NoSuchMethod/NoClassDef 全量 grep、16KB 的 `getprop ro.product.cpu.page.size` 与构建产物 `readelf -l` 页对齐、备份/迁移用例输出；同时记录 APK/mapping 的 sha256。

**Progress（2026-10-26 批次 23，设备证据落盘轨；只读 + 仓内 `.md`）.** 设备结论已按 R4 v1 落到 `docs/research/evidence/2026-10-26-batch-23/`（`00-index.md` + E-00…E-09）。按 R4 §1.2 的裁定，Fix direction 里的 `build/` 路径被 `.gitignore:15` 忽略、无法形成仓内证据，本批以仓内 `evidence/` 为落盘位置，`build/device-track-batch23/` 只留原始全量。逐项：C1 ① `am instrument` summary/`INSTRUMENTATION_CODE` 见 E-01…E-07；② 12 类签名对全部 17 个 logcat 重扫（E-08，`research/evidence/2026-10-26-batch-23/step08-signature-rescan.md:130`），旧矩阵漏掉的 01d/01e/06d/06e 已补齐，2 条命中均为测试前的 GMS/Primes 系统组件（同文件 `:113`），被测应用归因 0；③ 16KB `getconf`=16384 + `zipalign` exit 0（E-03）+ 官方 LLVM 22.1.8 `llvm-readobj --program-headers` 对 debug/internal 各 14 个 64 位 `.so` 全 PASS（E-09，`research/evidence/2026-10-26-batch-23/step09-elf-llvm-readobj.md:119`）；④ 备份/迁移见 E-05/E-06。C2 T1 复跑见 E-01/E-01b/E-01c/E-01d/E-01e。C3 产物身份 E-00，本轮 6 个 sha256/字节数逐项重算一致。C4 已由批次记录补齐：`docs/research/2026-10-26-batch-23-record.md` §4 为每个设备数字给出 E-ID + `path:line`，未把 `build/` 当关闭证据。C5 脱敏 0 命中、15 个 `.md` 的 `git check-ignore` 均无输出。**C6 未满足且本条不能关闭**：记录虽已落盘，但另有未闭合子项——本机缺 `llvm-readelf` 别名二进制（E-09 用同发行版 `llvm-readobj`，`research/evidence/2026-10-26-batch-23/step09-elf-llvm-readobj.md:107`），等价性仍需主会话裁定；arm64 16KB 运行时未执行（ps16k AVD 为 x86_64）；T1 仍 1/5 红（abandon 结构性缺陷，E-01b/E-01c）、KD-70 P2 仍红（E-02）。按 R4 §6.3，设备红绿不影响本条，但"四类结论齐备 + 等价判据裁定"未齐，故 **KD-74 保持 open，不写 closed**。若主会话裁定 `llvm-readobj` 等价于 `llvm-readelf`，C1③ 即成立，届时只需补记录 "KD-74 closed" 即可关闭本条。（以上为批次 23 记录时点状态；本条已于下方批次 24 T3 Progress 关闭。）

**Progress（2026-10-26 批次 24 T3，台账收口；只读核对 + 文档回链）.** 三条件齐备，**本条关闭**：① 记录回链——`docs/research/2026-10-26-batch-23-record.md` §4（`:176-202`）为每个设备数字给出 E-ID + `path:line`，§4 末（`:202`）已补记 "KD-74 closed"，§8 第二块（`:408-415`）给出主会话裁定；② 证据文件——`docs/research/evidence/2026-10-26-batch-23/00-index.md` 与 15 个 `.md`（E-00…E-09，含 E-01b…E-01e）在盘，`git ls-files docs/research/evidence/ | wc -l` = 15；③ 主会话等价裁定——记录 §8 `:415` 裁定 `llvm-readobj --program-headers` 与 `llvm-readelf -l` 字段级等价，接受为 E-09 证据。C1/C2/C3 覆盖见 `00-index.md` §4；C4/C5 见同文件 §7 回链检查；C6 由记录 §4 末补记满足。**关闭不等于设备门全绿**：T1 仍 1/5 红（abandon 结构性缺陷，E-01b/E-01c）、KD-70 P2 仍红（E-02）、arm64 16KB 运行时未执行（**三者均为批次 23 时点状态；批次 24 复跑已更新前两项**：T1 abandon 红已由批次 24 E-01/E-01b 转绿——两代 debug APK 各 6/6，`docs/research/evidence/2026-10-26-batch-24/step01-visualjs-isolation.md:124`、`step01b-visualjs-isolation-rerun.md:102`；KD-70 P2 红已由批次 24 E-02 转绿——20/20，`step02-quickjs-oom.md:141`，且 KD-70 已于 2026-10-27 由主会话裁定关闭（记录 §9 `:403`）；arm64 16KB 运行时至批次 25 台账同步时仍未执行）；分别由 KD-66/KD-70 与 16KB 启用门继续跟踪（KD-70 关闭后，arm64/16KB 覆盖缺口按裁定由准入清单 P-02/R-03 与 R4 `docs/research/2026-10-27-arm64-16kb-coverage.md` 跟踪）。证据路径：`research/evidence/2026-10-26-batch-23/00-index.md`（E-01…E-09）。

**Reopen condition.** 上述四类结论各有可复跑命令与落盘结果，且 T1 修复后的设备复跑结果一并落盘后关闭。

## KD-75 (open, 2026-10-26 批次 23 T4 登记；高) · PDF 导出无文档级隐式标识：官方 PDF 载体是 Info 字典 `/AIGC`，当前只剩逐图显式图注；且"仅 AI 图"写入判据漏掉模型解析文本

**Symptom（读码 + 官方指南）.** 单份/批量导出的 PDF 由 `android.graphics.pdf.PdfDocument`（Skia）生成，文件内只有逐张 AI 图的显式图注"本图由 AI 生成"（`core/export/.../AiGeneratedImageCaption.kt:27`），**没有**文档级隐式标识。TC260-PG-20258A §6.3 明确规定 PDF 文本文件的隐式标识载体是 Document Information Dictionary，写法为 `/AIGC({"Label":..., ...7 字段...})`（R1 `docs/research/2026-10-26-pdf-implicit-labeling.md:65-69`；官方 PDF sha256 `6bf6c118…3b0e`）。图片指南 20259A 只覆盖图片文件、不含 PDF（R1 :71-86），因此"AI 图装进 PDF"是两份指南之间的空档，不是豁免；《标识办法》第五条要求文件元数据含隐式标识（R1 :29）。**当前状态：既无隐式标识，也不能声称"法规未要求"。**

**第二处缺口（复核新发现，比 R1 原文更严重）.** R1 §4.3（:295-305）把写入判据限定为"PDF 实际会渲染出 AI 生成图"，并在 §1.4（:92）断言"当前 PDF 无 AI 生成文本块"。代码不支持这个断言：
- `CaptureParseInput` 经 `payload.toCapturedDocument(...)` 进入文档（`core/data/.../OpenAiModelTaskAdapters.kt:64`），模型返回的 `"paragraph"` 直接变成 `ContentBlock.Paragraph`（`core/data/.../OpenAiModelResponseParsers.kt:190`），`"figure_request"`/`"diagram_note"` 也变成 `ContentBlock.Paragraph`（`:218`、`:235`，正文为"图示：…"占位），且每个块都带 `provenance = QuestionBlockProvenance.MODEL_DOCUMENT_PARSE`（`:261`）。
- 该 provenance 存在 `CapturedQuestionDocument.blockEvidence`（`core/model/.../CapturedQuestionDocument.kt:17`，枚举 `:210`）；提交校验只要求 `reviewStatus ∈ {LOCAL_POLICY_ACCEPTED, USER_CONFIRMED}`（`:359-362`），不要求 provenance 改变，因此**已提交**文档仍可带 `MODEL_DOCUMENT_PARSE` 证据。
- 导出资格层只取 `state.questionDocument.document.blocks`（`core/export/.../MistakePdfEligibility.kt:118-119`）并只把 `blocks` 放进 `MistakePdfExportInput`（`:170`）；`MistakePdfExportInput` 没有 provenance 字段（`:318-370`）。`core/export/src/main` 对 `blockEvidence`/`QuestionBlockProvenance` 零命中（本日 grep），`core/export` 内也没有任何 `MODEL_DOCUMENT_PARSE` 用法；测试夹具只构造 `USER_CORRECTION`/`USER_TRANSCRIPTION`，因此"模型解析文本进入导出"这条路径完全未被现有测试覆盖。

→ 结果：**若题面来自 CAPTURE_PARSE，PDF 可能包含模型产出的文本块，但导出层无法区分**；按"仅 AI 图"判据写 `/AIGC` 会漏标这类 PDF。保守口径下不能把"文本块都是用户/OCR 内容"当成已验证事实。模型结构化转写是否属于"生成合成文本"是适用范围解释（同 R1 U2），但写入判据不能建立在"PDF 一定没有模型文本"这个未经代码支持的前提上。

**Evidence（本日只读）.** 上述行号均为本轮直接读取；`git status --porcelain core/export` → 5 个 M（`build.gradle.kts`、`MistakeExportJobRunner.kt` 与 3 个测试）；R1 §8.2 的 append-only Info 增量 PoC 已在真实 PDF 上读回 `/AIGC`（Python pypdf/PyMuPDF），但**Android PDFium 接受度未验证**（R1 U3）。**本批未写任何代码、未写读回测试、未运行 Gradle/设备。**

**Impact.** 高（合规 + 可追溯）：PDF 是 AI 图离开 App 后的实际载体，重绘时图片自身的 EXIF/XMP 已丢失（`GeneratedFigureMetadata.kt` KDoc）；当前导出物只有人可读图注，没有机器可读来源信息。若未来按"仅 AI 图"落地，还会漏掉含模型解析文本的 PDF，重演"看起来有标识、实际漏标"的失败。

**Fix direction（热区空出后，须整批落地）.** 不引第三方库；在 `core/export` 新增 `PdfAigcInfoInjector`（append-only 增量更新，重定义/新增 Info 对象，写 `/AIGC` 7 字段 JSON；值保持 ASCII；已存在则幂等替换；遇到 `/Encrypt`、`/Linearized`、坏 trailer 一律 fail-closed）。写入判据必须**同时**覆盖：(a) `isAiGeneratedExportImage` 为真的 AI 图（现行口径）；(b) 文档含 `MODEL_DOCUMENT_PARSE` 文本。实现 (b) 二选一：① 在 `MistakePdfExportInput` 携带块级 provenance（同步 `inputSha256`/golden）；② 资格层先派生出"文档含模型解析文本"的布尔值并随输入传递。**不写 XMP**：20258A §6.3 的 PDF 载体是 Info 字典，XMP 是 20259A 的图片载体；写 XMP 要重定义 Catalog + 新增流对象，风险与工作量约 2 倍且不消灭任何 Info 方案未消灭的失败（R1 §3.4 :261-266）。缓存必须失效：`MistakePdfEligibility.kt:186` 的 `"mistake-export-v3"` → `v4`，否则旧暖缓存会继续返回无 `/AIGC` 的 PDF（R1 §2.4）。打印部分页 `MistakePdfDelivery.writeSubset` 重绘丢元数据，需缓冲后注入或明确登记。DOCX 的 `docProps/custom.xml` 对称缺口另立条目（R1 U9），不在本条。

**验收判据（未运行）.** JVM：合成经典 xref 夹具（有/无 `/Info`、有 `/ID`、已存在 `/AIGC`、`/Encrypt`、`/Linearized`、坏 trailer）+ 注入后 `readAigc` 读回 + 原 Info 键保留 + 幂等 + fail-closed。仪器化：真实 `DeterministicMistakePdfRenderer` 产物 → `PreparedMistakePdf.verifyIntegrity()` → `PdfRenderer` 页数一致 → 读回 `Label/ContentProducer/ProduceID` → 负向（纯 `QUESTION_DIAGRAM`/用户文本且无模型解析文本 → 读不到 `/AIGC`）。独立读者至少一次（测试 APK 内 pdfbox-android 或外部 pypdf/PyMuPDF），不得只靠自写读回器自证。设备 PDFium 接受增量段（R1 U3）必须闭环。另见 R1 §5。

**Reopen condition.** 含 AI 图或含 `MODEL_DOCUMENT_PARSE` 文本的导出 PDF 能在设备上被独立读者读回正确 `/AIGC`，纯用户内容 PDF 读不到；旧缓存指纹已升版且无旧无标识产物；显式图注逐位不变（或按 R1 判据登记差异）；设备日志落盘（按 R4 规范）后关闭。**不得以"法规未要求 PDF 隐式标识"关单**——20258A §6.3 明文有 PDF 方法（R1 §1.3.1）。

---

## KD-76 (open, 2026-10-26 批次 24 T5 登记（研究复核高危项）；高) · 隔离门 6/6 不含 warm-up/ready：冷启动首个 execute 仍可能被 1500 ms watchdog 误杀，按现判据可在首个用户请求必败时放行 JS 广告

**Symptom（读码 + 批次 23 设备证据）.** `docs/research/2026-10-26-4e2-entry-checklist.md:256` 把必须设备绿的隔离门定义为 `VisualJsIsolationInstrumentedTest` **6/6**，`:269` 进一步写"广告判定：**6/6 必须设备绿**"。但 6 例中没有任何一例模拟"冷启动首个 `execute` 在 CPU 竞争下被 1500 ms 服务硬期限杀掉"：

- 批次 24 新增的冷启动用例 `aColdStartAttachBeyondTheOldConnectBudgetStillRendersTheFirstProgram`（`app/src/androidTest/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsIsolationInstrumentedTest.kt:249-260`）只把 `onServiceConnected` 推迟 7.6 s（`COLD_START_ATTACH_DELAY_MILLIS=7_600L`，同文件 `:383`），断言首连预算与"首次 execute 成功"；其夹具 KDoc 明说"只推迟 attach 回调、不推迟 `bindService`"（同文件 `:379-381`），**没有制造首个 execute 内的 QuickJS 原生装载竞争**。（行号时点注——批次 27 T5 勘误、按符号定位：上引测试文件 `:249-260`/`:379-381`/`:383` 与下方 `VisualJsService.kt:413-427` 为批次 23/24 时点；批次 25 warm-up 落层与批次 26 T1 用例入类后实测漂移——冷启动用例现于测试文件 `:274`、夹具 KDoc「只推迟 attach 回调、不推迟 `bindService`」现于 `:760-761`、`COLD_START_ATTACH_DELAY_MILLIS` 现 `:763`、服务 watchdog arm 现于 `VisualJsService.kt:484-496`。）
- 服务端 watchdog 在请求开始即 arm 1500 ms（`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualJsService.kt:413-427`；常量 `SERVICE_HARD_DEADLINE_MILLIS=1500L`，`core/domain/src/main/kotlin/com/tingyun/smartmistakebook/core/domain/VisualExecutionEngine.kt:126`）。批次 23 冷启动设备日志在同一时刻打出 `hard deadline (1500 ms) for request live-after-stale-terminate; killing the worker`（`docs/research/evidence/2026-10-26-batch-23/step01d-visualjs-isolation-coldboot.md:65`），同文件 `:72` 把它归因为冷启动负载（重启后负载 5.25、system_server/GMS 正在启动）。这与"首个用户请求在冷启动被误杀"是同一失败形状，不是 #240 自续 job。
- 契约已如实登记该缺口：`docs/current/visual-generation-contract.md:72` 写"冷启动首个 execute 的 1500 ms watchdog 误杀（E-01d）仍需 warm-up/ready，本批未修"；批次 24 T1 Progress 也写"冷启动用例只证明首连 15 s 预算（若设备跑绿），不能证明 1500 ms 误杀已消失"（`docs/known-defects.md:2602`）。

**Impact.** 高（广告门完整性 + 用户首个请求可能必败）：隔离门可以在**沉降态**跑绿（批次 23 沉降后 4/5 绿，E-01b/E-01e），而真实用户冷启动后的第一个请求仍可能因原生装载超过 1500 ms 被判 `HARD_DEADLINE_EXCEEDED` / 进程被杀。按 R3 的 6/6 判据，此时可以放行 JS 广告，广告承诺与实际失败直接冲突；该失败形状已被设备日志在冷启动负载条件下实际捕获（批次 23 E-01d：重启后立即执行 5/5 红，隔离进程 attach 4.6–7.5 s 超旧 5 s 预算，重启负载 5.25、system_server/GMS 正在启动，`docs/research/evidence/2026-10-26-batch-23/step01d-visualjs-isolation-coldboot.md:61-72`）——证据只是**一台**冷启动模拟器上的一次实跑，"仍可能被误杀"有证据，"发生在每个新装/重启用户的首个请求"是外推（批次 24 复核 §5 #8）：是否触发取决于设备负载与原生装载耗时，不得当作每例必现，也不得因沉降态复绿而排除。

**Fix direction（实现形态需裁定；不得放宽 1500 ms 程序期限）.** 在服务端引入明确的 warm-up/ready 语义（二选一，需主会话裁定）：① 服务在 `onCreate`/首个请求前完成 `libquickjs.so` 装载与一次空程序 warm-up，ready 之前不 arm 程序 watchdog；② 首个请求的一次性原生装载单独计时并排除在 1500 ms 程序期限之外，装载完成后再 arm 同一 watchdog。无论哪种，**已装载后的真实程序执行仍必须 1500 ms 硬杀**，不得用"首请求放行"掩盖 #240 挂死。客户端只允许在 ready 后发首个用户请求；ready 超时/失败必须 fail-closed 并如实显示加载态，不得把加载失败写成程序错误。广告门前必须把 R3 的 6/6 升级为包含以下设备用例的判据（数量以主会话裁定为准）：③ 冷启动/CPU 竞争下首个 `execute` 返回 `Rendered`，且日志无该请求的 watchdog hard deadline；④ 负控——同一 warm 服务上挂死程序仍在 1500 ms 内 SIGKILL（不得因 warm-up 分支放宽）；⑤ 首连失败/ready 超时走明确加载态，不永久 `unavailable`（与 T1 已登记的 LOADING 缺口一致）。

**验收判据（未运行）.** 设备证据按 R4 规范落盘：设备冷启动（最好含低内存/高负载条件）→ 首个真实请求成功；同一设备 warm 后挂死程序仍 1500 ms 硬杀；`onFatal`/logcat 证明没有"首个请求被 hard deadline 杀掉"的签名。R3 广告门的 6/6 文本必须同步改为包含 warm-up/ready 用例，否则不得以 6/6 绿作为 JS 广告依据。JVM 可覆盖 ready 状态机与超时映射；设备用例不可由 JVM 代替。

**Reopen condition.** 设备证据证明冷启动（含 CPU 竞争）首个 `execute` 成功且不是靠放宽 1500 ms 程序期限获得；warm 服务的挂死负控仍在 1500 ms 内被杀；R3/契约的广告门文本已包含该用例并回链证据。在此之前 JS 继续不广告（本批纪律，计划 `.zcode/plans/plan-2026-10-26-batch-24-isolation-final-kd70.md:15`、`:17`）。

**Progress（2026-10-27 批次 25 T2/T3；代码层，本轨未跑 Gradle/设备。原分「T2/T3」与「T2 续」两块登记，批次 26 T5 合并措辞——原第一块的「⑤ 仍未落」就代码层而言以下方 loading 语义为准，设备判据不变）.**

- **服务端 warm-up/ready（T2，Fix direction ①）**：`VisualJsService.onCreate` 在任何请求前经单线程执行器完成一次性装载 warm-up（真实 `QuickJs.create` + 一次空程序 evaluate，`QuickJsVisualExecutionEngine.warmUpNativeRuntime`）后置单调 `ready`；ready 前 `execute` 拒绝（"ready 之前不 arm 程序 watchdog"由该门结构性保证），warm-up 失败 killSelf 走既有死亡→重建→fail-closed 路径（不新增第三条恢复路径）；AIDL `IVisualJsService.isReady()` 新增（`PROTOCOL_VERSION` 1→2，`app/src/main/aidl/com/tingyun/smartmistakebook/visualjs/IVisualJsService.aidl`），宿主 ready 等待独立预算。不新增 `STATUS_NOT_READY`（保守分支，待主会话追认）。
- **客户端 loading 语义（T2，⑤ 的代码层）**：`VisualJsClientEngine.connectLocked` 在握手成功后轮询 `isReady()`（间隔 100 ms、预算 `READY_TIMEOUT_MILLIS=10_000`——R1' §2.3-4 的待设备校准起点；独立于 attach 预算与 2500 ms 阶段期限；ready 单调，已连接 link 复用不复查）。attach/ready 预算超时 = **loading 形状**：如实折 `ENGINE_CLOSED`（新增 `lastConnectFailure` 诊断通道，文本区分 loading/not-ready 与结构失败），**不写 `unavailable`、不扣重建预算**（`lastBindFailedOnLoading` 旗标豁免 `rebuildConsumed` 检查；死亡/结构失败/握手成功均清零旗标），下一次 execute 重新 bind + 等 ready——批次 24 登记的「连接超时一次失败即永久 unavailable」在 loading 形状上解除；死亡与结构性失败（bind 失败/协议不匹配/同 UID/死亡未确认）仍按既有语义锁存；**死亡路径上的重建照旧记账**（`rebuildAfterDeath` 失败即锁存，契约「最多重建一次」不变；批次 25 §9 主会话收口的 R15 修复把 handleDeath 顶部无条件清零 `lastBindFailedOnLoading` 移到 ABANDONED 短路**之后**——loading 放弃的死亡（主动 terminate）不再清豁免、真实非预期死亡仍清，竞态确定性消除）。loading 超时收尾 = terminate + unbind（服务 onDestroy → killSelf，不留孤儿进程）+ 复用 `abandon` 的死亡确认 ≤2 s；loading 放弃后的晚到 attach 经 `Link.abandonedForLoading` 按 markDead(unexpected=true) 收尾——handleDeath 在 ABANDONED 上短路，不锁存、不重建（否则首连超时的重试会被 1 s 后的晚到 attach 锁存）。close() 在 attach/ready 等待中立即终结（批次 24 修复 + ready 轮询的 closed/death 检查），不无限等。
- **warm-up 落点修正（T2，研究复核高危项的保守分支）**：R1' §2.3-1 草图「构造 `QuickJsVisualExecutionEngine` 即触发 `QuickJs.create`」读码复核**不成立**——构造函数（`core/data/.../visual/QuickJsVisualExecutionEngine.kt` 类头）只初始化 measurer/期限、stateLock/fused 集合与 watchdog 线程池，`QuickJs.create` 全文件唯一调用点在事务路径内（`runPhase` 内）。按草图实现会得到 ready=true 但 libquickjs.so 根本未装载——恰是本条 ③ 要抓的失败形状。修正 = **直接触达装载点**：`warmUpNativeRuntime()` 内 `QuickJs.create(Dispatchers.Default)` → 一次 `void 0;` evaluate（①「装载 + 一次空程序」原文口径；上游 `evaluate` 是 suspend，函数内 `runBlocking` 在调用线程完成，无用户 job 不卡排水循环）→ close。修正登记见 R1' §8。
- **用例（T2，类共 12 例：原 6 + T3 新增 3 + 本轨新增 3；批次 26 T1 落码后再 +1 = 13 例，见 KD-66 批次 26 T1 块）**：harness 新增 `awaitReady`（预算 30 s），既有用例适配新协议（真实 execute 前等 ready；断言零放宽——ready 门在 generation/BUSY/有界读检查之前，不等 ready 会让 STALE/BUSY/超预算断言误见 SHUTTING_DOWN）。本轨新增：`anExecuteAcquiredDuringWarmUpIsRefusedBoundedlyAndNeverWatchdogKilled`（连接后不等 ready 直接 acquire：SHUTTING_DOWN|OK|REJECTED 有界返回、无 fatal/死亡、ready 后同一 worker 可用）、`aFirstConnectTimeoutFailsHonestlyAndTheNextExecuteRetriesWithoutLatching`（attach 推迟 bind+16 s > 15 s 预算：两次 execute 均如实 FAILED、诊断含 loading、`unavailableForTest()==false`、connectionCount==0）、`aReadyTimeoutFailsHonestlyWithoutLatchingAndTheNextExecuteRebinds`（注入恒 not-ready 的本地 fake binder + 测试 seam `readyTimeoutOverrideForTest=250`：ENGINE_CLOSED + not-ready 诊断、不锁存、connectionCount 1→2 证明重新握手）。**seam 消灭的失败**：ready 超时分支在真服务上不可确定性触达（真实 warm-up 总会完成）。fake-binder 平台前提已独立取证：AOSP `android-14.0.0_r1` `Binder.java`（gh api 实读）本地 `linkToDeath`/`unlinkToDeath` 是 no-op 不抛、`isBinderAlive()` 非 final 可覆写。T1 轨 `VisualJsHandshakeRaceInstrumentedTest.FakeWorkerService` 补 `isReady()=true`（协议变更强制：否则其握手在 ready 轮询处按 loading 失败转红）。
- **队列/背压（T3，与本条互补、不构成本条关闭条件）**：契约 §64 队列/背压已落为 `VisualJsFrameQueue`（1 在飞 + ≤2 待绘 + latest-wins + BUSY 保帧）；其自身判据与设备证据独立登记（批次 25 记录 §3.3），不并入本条验收。
- **验证边界（T2，如实）**：非 Gradle 检查已执行：`python tmp/verify_batch25_t2_compile.py` 五阶段全 COMPILE_OK（exit=0：aidl.exe 单输入 ×2 + javac；core:data 引擎；app main 4 文件（含 T3 队列联合编译）；androidTest 2 文件）；既有 `QuickJsVisualExecutionEngineJvmTest` **2/2 绿**（`python tmp/verify_batch25_t2_jvm_regression.py`，JUnitCore，非 Gradle）。「红→绿」为按代码路径的确定性推演（批次 24 同型先例），不是执行证据。③④⑤ 设备判据、READY_TIMEOUT=10 s 的设备校准、warm-up 实测收益全部 UNVERIFIED（设备侧首验结果见下方设备轨块）。
- **设备判据口径（批次 26 T5 合并后统一；批次 27 T5 按批次 26 设备证据 E-01/E-02/E-03 刷新——出数前时点的「③ 未达／④ 弱口径／⑤ 首份（部分）」表述以下句为准，原文见 git 历史）**：③（冷启动首请求绿且无该请求 hard-deadline 签名）**尝试成功、不判定达成（PARTIAL）**——`adb reboot` 冷启动（boot_completed 后不沉降，loadavg 0.75）即跑 13/13 全绿（Time 64.072），5 条 kill 行逐条归因负向夹具设计内注入、零意外杀/零首请求杀，冷态 warm-up(~1.9 s)→首个 execute `Rendered` 全链成立；但负载轻于批次 23 E-01d（boot 0.75/跑后 4.31 vs 5.25）、调用方是测试 APK 内生产 client 引擎非产品进程/UI 首请求、契约 §4 worst-case 29.5 s 链与低内存条件 UNVERIFIED（`step03-kd76-criteria.md` §5/§7）；④（warm 服务挂死负控仍 1500 ms 硬杀）**强证据（v3 补记解除弱口径）**——warm 服务（ready 门后）#240 挂死被 1500 ms 硬期限 SIGKILL（kill 行实测提交后 ~1.52–1.55 s，`step03` §4.1）+ 调用方 hard+500 ms 有界返回（E-02 三轮绿）+ 新 generation 无 BUSY；旧 6 s 计时用例（`withTimeoutOrNull(6_000L)`，`VisualJsIsolationInstrumentedTest.kt:202`/`:552`，按符号定位）共存、降级为服务级回归观察，不再是本判据的计时门；⑤ 代码层已落 + **行为层证据 ×2 轮绿**（加载态三用例：首连超时/ready 超时/warm-up acquire——沉降轮 E-01 与冷启动轮 E-03 全绿，批次 25 注入形状首验为其中的首份）；⑤ 收尾未做项 = 加载态 UI/文案面与 R3 研究原文（`2026-10-26-4e2-entry-checklist.md`）6/6 门文本（归主会话裁定），准入清单面已由批次 26 T5 按 R2 复判刷新（P-07 行）、并由批次 27 T5 按 E-03 补记。**剩余缺口归属**：`READY_TIMEOUT=10 s` 设备校准、29.5 s worst-case 链与低内存条件仍 UNVERIFIED；**"warm-up 实测装载成本/收益"缺口归批次 27 设备轨**（计划 `.zcode/plans/plan-2026-10-27-batch-27-4e2-mva-static.md:12` 设备轨列"warm-up 装载成本实测"）。**批次 27 T4 同步（工单三：③④⑤ 状态待设备轨口径）**：④ 强证据与 ⑤ 行为层两轮绿按批次 26 E-01/E-02/E-03 维持、不回退为"待设备轨"；仍待批次 27 设备轨出数的判据面 = ③ 残余 UNVERIFIED（产品进程/UI 首请求、29.5 s worst-case 链、低内存条件）+ warm-up 实测装载成本 + 隔离回归（后两项计划 `:12` 设备轨列明），出数前本条不判定达成；③ 残余项是否随本批设备轨复验由主会话/设备轨编排定。**本条保持 open，设备验证前 JS 不广告**（沿用批次 24 计划纪律）。

**Progress（2026-10-27 批次 25 设备轨；记录员回填）.** 设备证据 `docs/research/evidence/2026-10-27-batch-25/`：本条 T2 三例设备首验绿（E-01 `step01-visualjs-isolation.md:170-181`）是 **⑤（loading 不锁存、有界重试）的首份（部分）设备证据**——但为沉降态 + 注入形状（attach 推迟 / 恒 not-ready fake binder），非真实冷启动 attach 分布；③（冷启动首请求绿且无该请求 hard deadline）未达，④ 部分（warm 服务挂死负控绿但计时 ≤6 s 弱口径），⑤ 的 UI/文案面与 R3/准入清单广告门文本同步未做（判据对照见该索引 §9.3）；契约 §4 worst-case 29.5 s 启动面未设备实测。**本条保持 open，设备验证前 JS 不广告**。

**Progress（2026-10-27 批次 26 T5 登记；设备轨未出数）.** ③④⑤ 设备判据的执行已列入批次 26 计划 T2（`.zcode/plans/plan-2026-10-27-batch-26-kd66-gate-lockfiles.md:14`，与 A-14/R15 后 12+2 复跑同轮；④ 与 KD-66 hard+500 ms 同夹具，两时限断言可一次落齐）；批次 25 §9 主会话收口的 R15 修复与 A-14 夹具确认已在树（见上方合并块）。截至本块登记时点，`docs/research/evidence/` 下无批次 26 证据目录（本轮实测 `ls`），T1/T2 设备结果未出——不预填判定，出数后以实际为准；**本条维持 open，设备验证前 JS 不广告**。

**Progress（2026-10-27 批次 26 设备轨；记录员回填。上方批次 26 T5 块「设备轨未出数」为出数前时点表述，被本块取代）.** 设备证据 `docs/research/evidence/2026-10-27-batch-26/`（E-01/E-02/E-03；E-02 兼作 KD-66 关闭门证据，该条已于同日关闭）：**④ 强证据**——warm 服务（ready 门后）#240 挂死被 1500 ms 硬期限 SIGKILL（kill 行实测提交后 ~1.52–1.55 s，`step03-kd76-criteria.md` §4.1）+ 调用方 hard+500 ms 有界返回（E-02 三轮绿）+ 新 generation 无 BUSY——warm-up 分支未放宽硬杀；**⑤ 行为层证据 ×2 轮**——加载态三用例（首连超时/ready 超时/warm-up acquire）沉降轮（E-01）与冷启动轮（E-03）全绿；⑤ 的 UI/文案面与 R3 广告门文本升级仍未做；**③ 尝试成功但边界如实**——`adb reboot` 冷启动（boot_completed 后不沉降，loadavg 0.75）即跑 13/13 全绿（Time 64.072），5 条 kill 行逐条归因负向夹具设计内注入（`step03` §4 归因表；其 §9 更正两处源码锚点 off-by-one，判定不变），零意外杀、零首请求杀，冷态 warm-up(~1.9 s)→首个 execute `Rendered` 全链成立；但**负载轻于批次 23 E-01d**（boot 0.75/跑后 4.31 vs 5.25）、调用方是测试 APK 内生产 client 引擎而非产品进程/UI 首请求、契约 §4 worst-case 29.5 s 链与低内存条件 UNVERIFIED、一次实跑一台 x86_64（API 34）——**③ 不判定达成**；R3/契约广告门文本升级未完成（本条关闭的另一半，归主会话）。**本条保持 open，设备验证前 JS 不广告**（判据对照见该索引 §3.1/§5）。

**Progress（2026-10-27 批次 27 设备轨；记录员回填）.** 设备证据 `docs/research/evidence/2026-10-27-batch-27/`：**「warm-up 实测装载成本/收益」缺口出数（E-04；上方判据口径块与准入清单 P-07 行的「归批次 27 设备轨」标注，就该项被本块取代）**——控制组（沉降态、单用例×10、每次恰好 1 个新 worker）样本 652–738 ms，p50=686/p95=725/max 738/mean 688 ms；套件 churn 组 n=16 中位 675.5 ms（churn 尾 1259–1746 ms 落在挂死注入负控用例同窗，设计内负载非环境故障）；口径=AMS `Start proc` → 同 pid `warm-up finished`（logcat threadtime 毫秒、按 pid 配对；`step04` §1/§3）；26/26 worker 全部到达 ready。边界如实（`step04` §6）：x86_64/API 34 模拟器口径，**arm64/真机/低内存/高负载 UNVERIFIED**；收益半边无新对照数据（warm-up 已是结构性前置，只能由批次 23 E-01d 历史红例反证）；宿主 bind→ready 客户端可感延迟未测（无计时 seam）；`READY_TIMEOUT=10 s` 对实测 p95 余量 ≈14×（churn 尾 ≈5.7×）——设备校准当前无数据压力，但仅 x86_64 模拟器口径。**同轮隔离回归已执行但红（E-05；归因不在本条判据面，属批次 27 T1 缺陷）**：Isolation 13 例 10 红，统一签名 `SCENE_FIELD_TYPE: __visualCompile expects three JSON text arguments` = T1 compile 阶段接线缺陷（引擎把 scene/metrics/expect 三个 JSON 文本未加引号直插 JS 源，编译器实参成了 JS 对象值；设备首曝；Race 2/2 与备份 34/34 不回退、16/16 worker ready、9 类崩溃签名 0——缺陷只在 compile 接线，不在装载/服务/隔离面）；修复已在批次 27 修复环落码（三参改经 `jsStringLiteral` 转义为 JS 字符串字面量传递 + 编译器查表 null-proto/自有键判定三处原型链逃逸修复）且单测门复跑 exit=0，**修复后设备复验 UNVERIFIED**（隔离套件未再上设备）。**本条保持 open，设备验证前 JS 不广告**（③ 产品进程/UI 首请求、29.5 s worst-case 链、低内存、R3 广告门文本升级等既有缺口不随本块消失）。

**Progress（2026-10-28 批次 28 T3 台账同步；状态刷新，零 Gradle/零设备/零 git 写）.** 按批次 28 计划 T3（`.zcode/plans/plan-2026-10-28-batch-28-scenev1-device-painter.md:14`）与 R2 剩余项排序（`docs/research/2026-10-28-mva-remaining-order.md` §3）把本条状态归一为"**装载成本已出数 + 剩余解锁条件清单**"，不新增设备判定、不出数：① **warm-up 实测装载成本已出数（批次 27 E-04；数字转录上方批次 27 设备轨块与 E-file，非本轮实跑）**——控制组 n=10 p50=686/p95=725/max 738 ms、套件 churn 组 n=16 中位 675.5 ms、16+10=26/26 worker 全部 ready（E-file `step04-warmup-cost.md:10`/`:65-68`/`:123-124`）；`READY_TIMEOUT=10 s` 对实测 p95 余量 ≈14×（churn 尾 ≈5.7×），设备校准当前无数据压力，但仅 x86_64/API 34 模拟器口径（E-file §6）。② **剩余解锁条件（全部出数后才可判达成；R2 §3 口径，按任务命题三面归置）**：(a) **产品 UI 面**——③ 判定达成需产品进程/UI 首请求（非测试 APK 内生产 client 引擎调用方）在冷启动（含 CPU 竞争、低内存）下首个 `execute` 返回 `Rendered` 且日志无该请求 hard-deadline 签名；其前置 = **批次 28 T1 修复后设备全绿复跑**（批次 27 隔离 13 例 10 红系 `$` 直插接线缺陷，修复已在树，修复后设备复验 UNVERIFIED——计划 `.zcode/plans/plan-2026-10-28-batch-28-scenev1-device-painter.md:7`）；(b) **29.5 s worst-case 启动链**（首连 15 s + ready 10 s + 宿主 2.5 s + 死亡确认 2 s，契约 `:72`）设备实测仍缺；(c) **arm64/真机口径**——装载成本与 ready 校准现有数字仅 x86_64/API 34 模拟器口径，arm64 运行时本机原理性不可采证（批次 25 R4 定论，R2 排序 §1.5 E3），归发布矩阵批挂起；(d) 关闭另一半归主会话不变——R3 广告门文本 6/6 → 含 ③④⑤ 升级 + 加载态 UI/文案面收尾。**本条保持 open，设备验证前 JS 不广告**。

**Progress（2026-10-28 批次 28 设备轨；记录步回填）.** 设备证据 `docs/research/evidence/2026-10-28-batch-28/`（emulator-5554 / test_device / API 34 / x86_64 / 4096）：**T3 块把 ③ 的前置列为"批次 28 T1 修复后设备全绿复跑"，该前置已达成**——隔离套件 `OK (13 tests)`（E-01，批次 27 的 10 红全转绿）、Race `OK (2 tests)`、编译器套件设备首跑先红后绿 `OK (6 tests)`（E-02，红轮 :144 `expected:<1.0> but was:<0.5>` 归因测试期望缺陷、四源证据：契约 :98 / `visual-compiler.js:860/:884/:937` / `run_tests.mjs:219` / V8 探针三值 0.5）、引擎 `OK (26 tests)`（9 红全转绿）、painter 17/17（E-03）、备份 `OK (34 tests)` 不回退（E-04）；17/17 worker `Start proc`→`warm-up finished` 按 pid 1:1、17 个 kill 全归因零意外杀（5 负控 watchdog / 4 terminate / 7 unbind / 1 start-instr 清旧进程）、9 类崩溃签名 ×6 logcat 全 0。**KD-66 的 hard+500 ms 用例在新源上重验绿**（closed 状态无 reopen 条件）。**本条仍保持 open**：⑤ 行为层随 E-01 刷新为 13/13 绿，但 ③ 判定所需的产品进程/UI 首请求（非测试 APK 调用方、含 CPU 竞争/低内存）、29.5 s worst-case 链、`READY_TIMEOUT=10 s` 设备校准（仍仅 x86_64 口径）、arm64/真机口径、R3 广告门文本升级（归主会话）均未出数。**设备验证前 JS 不广告。**

**Progress（2026-10-28 批次 29 T6 台账同步；零代码/零 Gradle/零设备/零 git 写）.** 本批不改变本条判据面，也不新增设备判定：③④⑤ 仍按批次 26 设备轨（④ 强证据、⑤ 行为层 ×2 轮绿、③ PARTIAL）与批次 27 E-04 装载成本维持（见上方各块）；**批次 29 设备轨未出数**——`docs/research/evidence/2026-10-28-batch-29/` 实测不存在（本轮 `ls` = `No such file or directory`），故计划 T1 的隔离 13 例复跑、painter 21 例、PDF 13 例与屏/PDF 一致门 10 例的设备执行均无判定可登记；本条 ③ 剩余缺口（产品进程/UI 首请求、29.5 s worst-case 链、低内存、arm64/真机口径）与 R3 广告门文本升级不随本块消失。**本条保持 open，设备验证前 JS 不广告。**

**Progress（2026-10-28 批次 29 设备轨；证据落盘员回填。上方批次 29 T6 块「设备轨未出数」为出数前时点表述，被本块取代）.** 设备证据 `docs/research/evidence/2026-10-28-batch-29/`（emulator-5562 / API 34 / x86_64 / PAGE_SIZE 4096；三包设备侧 sha256 一致）：**隔离套件 `Tests run: 13, Failures: 12`（E-02）**，12 红同因 `VisualMeasurementEnvironmentException: measure paint flags 1283 do not match the environment fingerprint paintFlags=0`（`VisualContentMeasuring.kt:460`；worker warm-up 失败后 service 自杀），12 个红例名与栈见 `research/evidence/2026-10-28-batch-29/step02-isolation-race.md:55-100`；**Race `OK (2 tests)`、备份 `OK (34 tests)` 不回退**（该文件 `:122`、`research/evidence/2026-10-28-batch-29/step05-backup.md:40`）。**worker 生命周期**：`Start proc .*:visual_js` = 14、`warm-up finished` = **0**、`warm-up failed` = 12、四类杀因之和 1 ≠ 14、无存活 worker（`research/evidence/2026-10-28-batch-29/step02-isolation-race.md:104-116`、`research/evidence/2026-10-28-batch-29/step06-signatures.md:68-79`）——即在批次 29 树上**真实 worker 无法到达 ready**。归因：本批 KD-78 ④ 落码假设（`Paint()` 默认 flags=0）导致的产品缺陷（R1，`research/evidence/2026-10-28-batch-29/step01-painter.md:63-87`），**不是**装载成本 / ready 校准面——批次 27 E-04 的 652–738 ms 与 26/26 ready 数字仍有效；⑤ 行为层因此在本批树呈红（非 KD-76 判据本身回归，修 R1 后须复跑）。③ 判定所需的产品进程/UI 首请求、29.5 s worst-case 链、低内存、arm64/真机口径仍 UNVERIFIED，R3 广告门文本升级未做。**本条保持 open，设备验证前 JS 不广告。**

**Progress（2026-10-28 复核修复轮；零 Gradle/零设备）.** 上块归因的 R1（`Paint` flags 假设）已按 KD-78 ④ 修复落码：度量侧 `forEnvironment` 与回放侧 `textPaint` 都**显式** `flags = 0`，`VisualMeasurementEnvironmentException: measure paint flags 1283 …` 的装配级恒抛在源码层消失（详见 KD-78 复核修复块）。**隔离 13 例 / warm-up ready > 0 的设备复跑仍 UNVERIFIED**（本轮未运行 Gradle、未上设备）；本条判据、关闭条件与"JS 不广告"不变。

**交叉引用.** KD-66 跟踪 issue #240 的 hard+500 ms 关闭门；本条只跟踪"冷启动首个 execute 不被一次性装载误杀"的 warm-up/ready 缺口，不代替 KD-66，也不改变其关闭门。批次 24 T1 的 `abandon()` 修复与首连预算属另一失败形状，设备复跑见批次 24 E-01/E-01b（6/6），与本条的 warm-up/ready 门无关。

## KD-77 (open, 2026-10-27 登记（API 23 冒烟评估；中）；2026-10-29 批次 32 T6 标题回改：android-23 镜像已装盘，旧包冒烟 logcat 未落仓、修复后 APK 未跑) · API 23 运行时未验证：修复后 APK 未在 API 23 完成装机/冷启动/最小冒烟

**Symptom（本机实测，2026-10-27）.** `C:/Android/Sdk/cmdline-tools/latest/bin/sdkmanager.bat --list_installed` 的 Installed packages 为：build-tools 34.0.0/36.0.0/37.0.0、emulator 37.1.11、platform-tools 37.0.1、`platforms;android-34`、`platforms;android-37.0`、`system-images;android-34;google_apis;x86_64`、`system-images;android-36;google_apis_ps16k;arm64-v8a`、`system-images;android-36;google_apis_ps16k;x86_64`——**无 `platforms;android-23`，也无任何 `system-images;android-23*`**。磁盘交叉核对：`/c/Android/Sdk/platforms` 仅 `android-34`、`android-37.0`；`/c/Android/Sdk/system-images` 仅 `android-34`、`android-36`；`ls -d /c/Android/Sdk/platforms/android-23 /c/Android/Sdk/system-images/android-23` 两者均 `No such file or directory`。`emulator -list-avds` 无 `api23_smoke`。按本轮纪律**不下载镜像**，因此未创建临时 AVD、未安装 v21 APK、未起服务/最小冒烟。

**标题/Symptom 前提回改（2026-10-29 批次 32 T6）.** 上一段的"本机无 android-23 平台/系统镜像 / 未执行 v21 APK 冒烟"只对**登记时点（2026-10-27）**成立，**不是现树事实**，不得再被读成"API 23 运行时没有任何证据"。批次 30 T7 自述并已由记录员实盘核对：`/c/Android/Sdk/platforms/android-23`、`/c/Android/Sdk/system-images/android-23`、`~/.android/avd/api23_smoke{32,64}.avd` 均在盘；T7 在该镜像上装过**旧 APK**（`strictOffline` debug，sha256 `05a5b8bd…`，versionCode 2 / 0.2.0-offline）并跑了一次冷启动/隔离套件（12 红，`measure paint flags 1280`），但**冒烟 logcat 未落仓 → 该运行时结论未复核**；现盘 APK 已被批次 30 设备轨覆盖（`1429ee0e…`）。因此本条标题改为"**修复后 APK 未完成 API 23 冒烟**"，反向表述（"无镜像 / 未执行冒烟"）作废；仍不能标绿，关闭条件见下（证据回填见本条 2026-10-29 批次 30 T7 / 批次 31 T5 两段 Progress）。

**Impact.** `app/build.gradle.kts:21` 声明 `minSdk = 23`；`docs/current/release-gates.md:38` 与 `docs/research/2026-10-26-batch-24-4e2-admission-checklist.md:216`（R-01）把 API 23 运行时列为发布矩阵门。现有设备结论（API 34/4 KB、x86_64 16 KB）均不能覆盖 API 23：任何 API 24+ 符号、SQLite/Resources 行为差异或 native 装载差异只会在 API 23 设备暴露；补证据前，API 23 只能标记为未验证，不能计入发布门。

**Fix direction.** 在有网络/可安装 SDK 的机器上安装 API 23 平台与系统镜像（例如 `system-images;android-23;google_apis;x86_64`，tag 以当时可用清单为准），建临时 AVD `api23_smoke`，安装 v21 APK（记录 APK sha256 与版本），执行最小冒烟：冷启动、服务/核心路径启动、适用的隔离/引擎装载，并按 R4 设备证据规范落盘 logcat 与 `am instrument`/`dumpsys` 输出。不得用 API 34 结果替代，也不得把"镜像未安装"写成"已验证"。

**Reopen condition（关闭条件）.** API 23 AVD 或真机上 v21 APK 完成装机 + 冷启动 + 最小冒烟，证据落盘且 logcat 无 `FATAL EXCEPTION`、`ANR`、`NoSuchMethodError`、`NoClassDefFoundError`（若有命中须有明确的非本应用归因说明）；同时记录设备/镜像/APK 指纹与命令。镜像仍不可得时本条保持 open，发布矩阵的 API 23 项不得标绿。

**Progress（2026-10-29 批次 30 T7 API 23 冒烟；只读复核 + 轨自述；状态保持 open）.** T7 轨自述：`sdkmanager --list` 有 `android-23` 条目，已装 `platforms;android-23` 与三套镜像（`google_apis/x86_64`、`default/x86`、`default/x86_64`）；`google_apis x86_64` guest 不启动（adb 控制台应答 avd name，guest shell 超时、logcat 空）；`default x86` 可启动但 APK native-code 仅 arm64-v8a+x86_64 装不上；`default x86_64` 启动成功（sdk=23 / Android 6.0）。在该镜像上装 **旧 APK**（strictOffline debug，sha256 `05a5b8bd…`，versionCode 2 / 0.2.0-offline，构建 2026-10-28 22:31）：冷启动 `am start -W` 2.857 s、`mResumedActivity=MainActivity`，全量 logcat 的 FATAL/ANR/NoSuchMethod/NoClassDef/UnsatisfiedLink 均为 0。随后跑隔离套件 13 例 **12 红**，根因 `VisualMeasurementEnvironmentException: measure paint flags 1280 do not match the environment fingerprint paintFlags=0`（warm-up 被杀）——该 APK 早于批次 29 修复（commit `48a473e0` 落于 2026-10-29 00:09:41），是"修复前源"失败在 API 23 的重现；同时给出新事实：平台 `Paint()` 默认 flags 在 API 23 = **1280**、API 34 = 1283，反证"显式装配 0"的必要性。

**记录员实测**：`/c/Android/Sdk/platforms/android-23`、`/c/Android/Sdk/system-images/android-23`、`~/.android/avd/api23_smoke{32,64}.avd` 均在盘；现盘 `app-strictOffline-debug.apk` sha256 已是 `1429ee0e…`（旧包被批次 30 设备轨覆盖）。**冒烟 logcat 未落仓 → 上述运行时结论未复核。** **本条保持 open**：关闭条件要求"API 23 AVD 或真机上 v21 APK 完成装机 + 冷启动 + 最小冒烟，证据落盘且 logcat 无四类崩溃"，需用**修复后 APK** 重跑并把证据落盘；API 23 项不得标绿。

**Progress（2026-10-29 批次 31 T5 状态核对；只读；状态保持 open）.** 本批**未创建/启动 API 23 AVD，也未重跑冒烟**：`docs/research/evidence/2026-10-29-batch-31/` 不存在（`ls` = `No such file or directory`）；批次 31 基线 §7.2 的三台在线模拟器全部是 **API 34 / x86_64 / PAGE_SIZE 4096**，无 API 23；批次 30 T7 的旧 APK 冒烟 logcat 至今未落仓。**API 23 运行时证据本批未出数**，本条保持 open；关闭仍需修复后 APK 在 API 23 完成装机 + 冷启动 + 最小冒烟，并把四类崩溃签名 logcat 与设备/镜像/APK 指纹落盘。**API 23 项不得标绿。**

**（2026-10-29 批次 31 设备轨回填更正）** 本块"evidence 目录不存在"只对 T5 快照成立：该目录现已有 **15 份**批次 31 设备证据，但**无一份是 API 23**（八套件均在 `emulator-5562` / API 34），API 23 冒烟仍未出数。本条**保持 open**，"API 23 项不得标绿"不变。

**Progress（2026-10-29 批次 33 T6 状态核对；只读；状态保持 open）.** 本批 T6 时点**未创建/启动 API 23 AVD、未重跑冒烟**：`docs/research/evidence/2026-10-29-batch-33/` 不存在（`ls` = `No such file or directory`）。`C:/Android/Sdk/platform-tools/adb.exe devices -l` 三台在线均为 `emulator-5562/5566/5584`，逐台 `getprop ro.build.version.sdk` 实测 **34 / 34 / 34**，无 API 23。盘上 `~/.android/avd/api23_smoke.avd`、`api23_smoke32.avd`、`api23_smoke64.avd`、`/c/Android/Sdk/platforms/android-23`、`/c/Android/Sdk/system-images/android-23` 仍在，但修复后 APK 的 API 23 装机/冷启动/冒烟证据仍未落仓。**本条保持 open；API 23 项不得标绿。**

**记录回链（2026-10-29 批次 33 记录员；只读；状态保持 open）.** 本批设备轨全部在 `emulator-5562` / API 34 / x86_64 / PAGE_SIZE 4096（`docs/research/evidence/2026-10-29-batch-33/step00-environment.md`），**无 API 23**；未创建/启动 API 23 AVD，修复后 APK 的 API 23 装机/冷启动/冒烟证据仍未落仓。本条保持 open；API 23 项不得标绿。记录见 `docs/research/2026-10-29-batch-33-record.md` §7 #14。

**Progress（2026-10-29 批次 34 T6 状态核对 + 记录员复核；只读；状态保持 open）.** 本批设备轨 15 套件全部在 `emulator-5562` / API 34 / x86_64 / PAGE_SIZE 4096（`00-index.md` §1、§3），**无 API 23**。记录员记录时点实跑 `adb devices -l` 列出 `emulator-5562/5566/5584`；`getprop ro.build.version.sdk`：`-5562`=**34**、`-5584`=**34**、`-5566` 返回 `device 'emulator-5566' not found`（stale transport）。盘上 `platforms/android-23`、`system-images/android-23` 与 `api23_smoke*.avd` 仍在，但**修复后 APK（批次 34 四包）未在 API 23 装机/冷启动/冒烟**，四类崩溃签名 logcat 未落仓 → 本条保持 open；API 23 项不得标绿。记录见 `docs/research/2026-10-29-batch-34-record.md` §3.6。

**Progress（2026-10-30 批次 35 记录员复核 + 跨账本不一致登记；只读；状态保持 open）.** 本批设备轨两轮 18 套件全部在 `emulator-5562` / API 34 / x86_64 / PAGE_SIZE 4096（`docs/research/evidence/2026-10-30-batch-35/step-device-track.md` §0），**无 API 23 动作**；批次 35 四包未在 API 23 装机/冷启动/冒烟。**跨账本不一致登记（本批不代裁）**：KB 线 docs-only 提交 `4341f5ad` 在其自账本 `docs/kb-batch25-2026-10-29.md:401` 自述"KD-77 已关闭（API 23 兼容证据齐）"（`:245` 列 API23 装机+冷启+四类崩溃 0、DAO 4/4 证据；`:200` 同文件自留"融合 golden 红（dense sha 重钉缺失）、建议保持 open 或拆分"），而全局台账本条标题仍 open——两账本口径分歧由主会话/KB 线裁定；裁定前**本条保持 open，API 23 项不得标绿**。记录见 `docs/research/2026-10-30-batch-35-record.md` §10。

## KD-78 (open, 2026-10-28 登记（批次 28 T2 移交收口 + 复核第 1 轮；中）；批次 29 T5 四缺口生成门/语义/指纹已落码，③ 设备像素证据已出；④ 显式装配修复已落码，**2026-10-29 批次 30 设备复跑通过、④ 关闭**；整条仍 open（①② 另核销）) · DrawOps V1 生成门/回放语义四缺口：stroke-only 无颜色源、dash 形状门未钉、#RRGGBB(AA) 与 op.alpha 优先级未定义、measure/replay flags 不在环境指纹——生成侧已于批次 29 T5 收口，painter 侧兜底保留（③ 静默错画已改为乘法语义）

**Symptom（读码 + 官方源实读，2026-10-28）.** 四个缺口同属"DrawOps V1 词表/回放语义在两道生成门（`visual-compiler.js`、`VisualDrawOpsValidator.kt`）与回放 painter（`VisualDrawOpsPainter.kt`）之间的空档"。①②是批次 28 T2 的明示移交（`docs/research/evidence/2026-10-28-batch-28/step03-painter-smoke.md` §7"4 项移交"、`00-index.md` §5-4"本步未落 known-defects/契约（T3 归属）"，R1 `docs/research/2026-10-28-painter-design.md` §5），③由复核第 1 轮指名、④为 R1 §5-③——本条登记前四者在 known-defects/契约零登记（本轮 grep `PAINTER_STROKE_WITHOUT_FILL|PAINTER_DASH_ODD|RRGGBB` 于两文件均 0 命中）。

- **① stroke-only 无颜色源**：validator `:340-342` 只要求 fill/stroke 二选一即放行，`VisualStrokeSpec`（`VisualDrawOps.kt:54-60`）只有 width/cap/join/miterLimit/dash、无 color 字段，compiler 白名单拒绝 stroke 带色（R1 §5-①）——stroke-only 文档合法出两道生成门，回放侧无颜色来源，painter 以 [PAINTER_STROKE_WITHOUT_FILL] fail-closed（`VisualDrawOpsPainter.kt:137-143`），设备面 `aStrokeOnlyPathFailsClosed`（E-03 17 例之一）。
- **② dash 形状门未钉**：validator `:424-442` 与 compiler `requireStroke`（`visual-compiler.js:472-485`）只钉逐项值域与 ≤32 条数，不钉偶数、不钉最小条数——奇数 dash 与空数组 `dash=[]` 都合法出两道门。painter 前置形状门（偶数且 ≥2 同拒，[PAINTER_DASH_ODD]，`VisualDrawOpsPainter.kt:144-154`）挡住两个 AOSP 层面的失败：奇数进 native 行为无规约；`length<2`（含空数组）被 `DashPathEffect` 构造器直接抛**无码** `ArrayIndexOutOfBoundsException`（aosp-mirror `DashPathEffect.java:34-39`：`if (intervals.length < 2) { throw new ArrayIndexOutOfBoundsException(); } native_instance = nativeCreate(intervals, phase);`，2026-10-28 gh api 实读）。**painter 侧缓冲串状态已同步修复（2026-10-28 复核修复，代码已在树）**：`bindPathPaint` 原用"只扩不缩"常驻缓冲并整缓冲交构造器——同实例先长 dash 后短 dash 会把残尾混进 pattern（构造器按 `intervals.length` 全量消费）、空 dash 在未扩容实例上抛无码异常/在已扩容实例上整条复用旧 pattern；现按 `dash.size` 精确装配（`VisualDrawOpsPainter.kt:240-250`），回归用例落 `VisualDrawOpsPainterInstrumentedTest`（空 dash 有码拒 ×2 + 残尾 strip 逐位一致；**设备复跑 UNVERIFIED，归批次 28 设备收口轨**）。**生成侧两门仍不钉形状，缺口保留**。
- **③ 8 位色 alpha 被 op.alpha 静默覆盖（复核第 1 轮新指名；唯一静默错画项）**：三侧都接受 `#RRGGBBAA`（validator `COLOR_PATTERN` `:45`、compiler `COLOR_PATTERN`/`requireColor`、painter `parseDrawOpsColor` `VisualDrawOpsPainterPure.kt:112-132`，末两位=alpha），但 `bindPathPaint`/`bindTextPaint` 先 `setColor`（含 AA 字节）后 `setAlpha`（`VisualDrawOpsPainter.kt:214-215/268-269`），而 AOSP `Paint.setAlpha` 只替换 alpha 通道、保留 r/g/b（aosp-mirror `Paint.java:1250-1259` `setAlpha` 实现体 `Color.pack(r, g, b, a, cs)`，2026-10-28 gh api 实读）——8 位色的 alpha 字节在回放侧**永远失效**（如 fill `"#80ff0000"` 无组透明按不透明画，无任何诊断）；纯测 `VisualDrawOpsPainterPureTest` `six and eight digit hex colors parse to argb` 只钉解析语义（AA→ARGB 末位），不构成反证。契约对该形态与 `op.alpha` 的优先级零定义（`visual-generation-contract.md` 本轮 grep RRGGBB 0 命中）。
- **④ measure/replay flags 不在环境指纹**：度量环境指纹只含 `typefaceId|sizeSp|density|fontScale|locale`（`VisualContentMeasuring.kt:150-152`），无 Paint flags 项；replay `textPaint` 按 R1 §2.3/§3.2 严格复制默认 flags（AA 关，`VisualDrawOpsPainter.kt` `bindTextPaint` KDoc）——度量 Paint 的 flags 一旦被改动，digest 不变、度量与回放静默失配（R1 `2026-10-28-painter-design.md` §5-③）。

**Impact.** ①②：后续批次/调用方会重复踩"合法生成、绘制时被拒"的坑，逐次以 fail-closed 诊断收场（预算已花、图未出）；② 的 painter 侧无码崩溃路径已随复核修复关闭，生成侧缺口仍在。③：**静默错画**（非 fail-closed）——生产方写 `#RRGGBBAA` 即得到错误透明度且无诊断，唯一防线是"没人这么写"，与 painter 自身"宁可拒不可默认"的 [PAINTER_COLOR] 词表门哲学相悖。④：跨环境恢复/换字体场景的度量-回放一致性缺一道钉。

**Fix direction（V1.1 收口建议，归契约批/主会话裁定）.** ①：给 stroke spec 加 color 字段（scene→compiler→validator→`VisualStrokeSpec` 四处同改，词表两侧同改纪律）或钉"fill 必填、stroke 色取 fill 色"（R1 §5-① 两案）；②：validator 与 compiler 各加一行"长度为偶且 ≥2"（与 painter 门同词）；③：二选一并写进契约——拒绝 8 位色（词表收紧为 #RRGGBB）或定义乘法/覆盖语义（AA×op.alpha 归一）；④：flags 进环境指纹，或以共享常量/跨侧测试钉死 `forEnvironment` 与 replay textPaint 同构。

**Reopen condition（关闭条件）.** ①②③④逐项：契约条文落地 + 两道生成门同改 + 词表/负向矩阵（Node 台 + JVM）各钉一例 + painter 对应 fail-closed 面拆除或对齐；③ 另需设备轨像素级正/负例（AA 色被 op.alpha 按裁定语义正确调制）。在此之前生成侧维持放行、painter 维持 fail-closed，不得以"设备 17/17 绿"读成缺口已关。

**Progress（2026-10-28 批次 29 T5 收口落码；零 Gradle/零设备/零 git 写；状态保持 open，余 ③ 设备像素证据）.** ⚠️ **标题回改（批次 30 T12，2026-10-29）**：③ 的设备像素正/负例已在批次 29 设备轨出数并绿（`docs/research/evidence/2026-10-28-batch-29/step01-painter.md:59-60`），当时的"余 ③"已过期；现余项是 **④ 显式装配修复后的设备复跑**（painter 24 / flags 2 / 隔离 13），批次 30 修复后设备证据 **未出数**。按批次 29 计划 §1 裁定 2–5 逐项落地（裁定原文：`.zcode/plans/plan-2026-10-28-batch-29-pdf-replay-kd78.md:14-17`），并有 JVM/Node 实跑红→绿：

- **① stroke-only 生成门**：`visual-compiler.js:892-897` 在 path 节点 `fill==null && stroke!=null` 时 `fail('PAINTER_STROKE_WITHOUT_FILL')`；原生侧 `VisualDrawOpsParser.kt:420-432` 同码拒绝（批次 29 T2 已把结构解析下沉到 Parser，`VisualDrawOpsValidator.kt:36-40` 只是入口委托）。回放侧 `VisualDrawOpsPainter.kt:137-143` 兜底保留（裁定 4：「回放侧兜底保留」）。负向钉：Node `run_tests.mjs:436-439`；JVM `VisualDrawOpsValidatorTest.kt:265`。**受影响的既有夹具同批对齐**：`QuickJsSceneCompilerInstrumentedTest.kt:277` 的全节点场景原用 stroke-only dot（新生成门会拒），改为 `fill:'#ff0000'` + stroke（节点/命令计数不变）；Node 台 `run_tests.mjs` 的全节点场景同步补 fill（`:193`）。
- **② dash 形状门**：生成侧两门各加"偶数且 ≥2"，码 `PAINTER_DASH_ODD`——`visual-compiler.js:485-492`、`VisualDrawOpsParser.kt:534-546`；回放侧 `VisualDrawOpsPainter.kt:148-154` 兜底保留。负向钉：Node `run_tests.mjs:440-451`（奇数/单项/空数组）+ 负控 `:455-461`（`[2,2,4,4]` 仍合法）；JVM `VisualDrawOpsValidatorTest.kt:284`（含 even-dash 负控）。
- **③ 8 位色 alpha 语义（唯一静默错画项）**：词表是 `#RRGGBBAA`（末两位=alpha，与三侧既有词表一致，`VisualDrawOpsPainterPure.kt:112-132`）。纯层新增 `effectiveDrawOpsAlpha255(colorArgb, alpha) = floor(colorA × op.alpha)`（`VisualDrawOpsPainterPure.kt:160`；`drawOpsAlpha255` 收敛为其 colorA=255 特例 `:177`）；painter 在 `setColor` 后以该值 `setAlpha`：path fill/stroke `VisualDrawOpsPainter.kt:162/169`、label `:195`。契约公式见 `docs/current/visual-generation-contract.md` §5「DrawOps V1 生成门与回放语义」。**夹具口径更正（如实登记）**：任务书示例 `#80ff0000` 在 `#RRGGBBAA` 词表下 alpha 字节是 **00**（R=0x80 G=0xff B=0x00 A=0x00），不是 0x80；按本仓四侧一致的词表，"无组透明应得 0x80"的夹具是 **`#ff000080`**（alpha 字节 0x80）。JVM `VisualDrawOpsPainterPureTest.kt:136` 同时钉正例（`#ff000080`→128、×0.5→64）与负控（`#80ff0000`→0）；设备像素正/负例 `VisualDrawOpsPainterInstrumentedTest.kt:252`（白底 `#ff000080` 中心期望 `0xFFFF7F7F`，旧实现会画成 `0xFFFF0000`）。
- **④ flags 指纹**：度量环境新增 `paintFlags`（默认 0，`VisualContentMeasuring.kt:141/157`），进指纹 `:167-171`；`forEnvironment` 校验实际度量 Paint 的 flags 与指纹一致、不一致 fail-closed（`:456-464`），观测面 `paintFlagsForTest`（`:426`）。回放侧保持 `Paint()` 默认并导出 `REPLAY_TEXT_PAINT_FLAGS=0`（`VisualDrawOpsPainterPure.kt:78`）与观测面 `textPaintFlagsForTest`（`VisualDrawOpsPainter.kt:265`）。跨侧钉：app JVM 指纹字面与 digest 变化（`VisualContentMeasuringTest.kt:268/283`）、core JVM 常量（`VisualDrawOpsPainterPureTest.kt:165`）、core 设备测真实 textPaint（`VisualDrawOpsPainterInstrumentedTest.kt:263`）、app 设备测度量 Paint ↔ 回放 `Paint()` 默认 ↔ 指纹三面比对 + 失配 fail-closed（`app/src/androidTest/.../visualjs/VisualMeasureReplayPaintFlagsInstrumentedTest.kt`，2 例）。
- **契约条文**:`docs/current/visual-generation-contract.md` §5 新增四条（8 位色乘法公式、dash 形状、stroke-only、flags 指纹）；§5.1 注记同步为"已收口、余设备像素证据"。§5.1 表行与起用门处方未改。
- **验证（本批实跑，非 Gradle）**：Node 台先红后绿——新增四例前基线 `node tools/visual_compiler/run_tests.mjs` = `89 passed, 0 failed`，仅加负向用例后 `90 passed, 4 failed`（`expected rejection but compile succeeded` ×4），落码后 `94 passed, 0 failed`。JVM 套件——`python tmp/verify_batch29_kd78_jvm.py`：当前源 `OK (48 tests)`（ValidatorTest 35 + PureTest 13）；**下一步红证**用 `python tmp/verify_batch29_kd78_red.py` 以 HEAD 源跑当前测试 = `Tests run: 35, Failures: 3`（其中 2 条为本条生成门：stroke-only/奇数 dash 在 HEAD 校验器被放行；第 3 条 `DRAWOPS_SCHEMA_VERSION` 属批次 29 T2 的码面拆分，非本条）。app 度量 JVM——`python tmp/verify_batch29_kd78_app_jvm.py` = `OK (16 tests)`。
- **未落/UNVERIFIED（本条不能关闭的原因）**：③ 关闭条件要求"设备轨像素级正/负例"，`VisualDrawOpsPainterInstrumentedTest` 的 alpha8 用例已写入但**未上设备**；painter 仪器化套件因本次改动由 19 例变 21 例（3 例改 hand-built 兜底 + 2 例新增），整包设备复跑 UNVERIFIED；`VisualMeasureReplayPaintFlagsInstrumentedTest` 2 例未上设备；`VisualDrawOpsPainter.textPaintFlagsForTest` 为设备测观测面，未在真机执行。Gradle 门禁由脚本串行执行（本任务纪律：不运行 Gradle），故 AAR/编译级门亦未在本轮运行。**JS 不广告不变（KD-76 open）**；"设备 17/17 绿"旧证据不覆盖本批新增用例。

**Progress（2026-10-28 批次 29 T6 台账同步；零代码/零 Gradle/零设备/零 git 写；状态保持 open）.** 台账员按上方 T5 Progress 逐项复读源码锚（本轮 `grep`/`sed` 实读，**非重跑测试**）：① `visual-compiler.js:896` 与 `VisualDrawOpsParser.kt:428` 同码拒 stroke-only；② `visual-compiler.js:491` 与 `VisualDrawOpsParser.kt:542` 拒奇数/`<2` dash；③ 纯层公式 `VisualDrawOpsPainterPure.kt:160`（`effectiveDrawOpsAlpha255`）、painter 使用点 `VisualDrawOpsPainter.kt:162/169/195`；④ 指纹 `VisualContentMeasuring.kt:141`/`:171`/`:459` 与回放常量 `VisualDrawOpsPainterPure.kt:78` 同值。契约 §5 已按 T6 写入五条 V1.1 语义（含 hitRegions 口径，`docs/current/visual-generation-contract.md` §5）。**设备证据未出数**：`docs/research/evidence/2026-10-28-batch-29/` 实测不存在（`ls` = `No such file or directory`），故 ③ 的设备像素正/负例（`VisualDrawOpsPainterInstrumentedTest.kt:252`，整包现 **21 例**）、PDF painter **13 例**、屏/PDF 一致门 **10 例**、`VisualMeasureReplayPaintFlagsInstrumentedTest` **2 例**全部未上设备；Gradle 门禁由脚本串行执行，本轮未运行。**本条保持 open，JS 不广告**；"设备 17/17 绿"旧证据不覆盖本批新增用例。

**Progress（2026-10-28 批次 29 设备轨；证据落盘员回填。上方 T5/T6 块「未上设备 / 设备证据未出数」为出数前时点表述，被本块取代）.** 设备证据 `docs/research/evidence/2026-10-28-batch-29/`（emulator-5562 / test_device / API 34 / x86_64 / PAGE_SIZE 4096；三包设备侧 sha256 与宿主逐字节一致，见该索引 §2）：**painter 整包 21 例上设备 `Tests run: 21, Failures: 1`（E-01）**——20 绿含 **③ 的设备像素正/负例**（`VisualDrawOpsPainterInstrumentedTest.kt:252` `anEightDigitColorAlphaMultipliesWithTheOpAlphaInsteadOfBeingOverwritten`：`#ff000080` 中心 `0xFFFF7F7F`、背景白；旧实现会画 `0xFFFF0000`）与 ①② 的 hand-built 回放兜底三例（`research/evidence/2026-10-28-batch-29/step01-painter.md:59-60`）。**唯一红例是 ④ 的回放 flags 观测面**：`theReplayTextPaintKeepsTheDefaultFlagsMirroredByTheMeasureFingerprint`，`expected:<0> but was:<1283>`（`VisualDrawOpsPainterInstrumentedTest.kt:267`，`research/evidence/2026-10-28-batch-29/step01-painter.md:48-56`）——**④ 的落码假设被设备否证**：AOSP `Paint()` 默认 flags = `0x1|0x2|0x100|0x400` = **1283**（android-14.0.0_r1 `Paint.java`），而 `REPLAY_TEXT_PAINT_FLAGS = 0`（`VisualDrawOpsPainterPure.kt:78`）与 `DEFAULT_PAINT_FLAGS = 0`（`VisualContentMeasuring.kt:184`）按 0 校验；跨侧 flags 用例 2 例 1 红（`forEnvironment` 第 460 行即抛，`research/evidence/2026-10-28-batch-29/step03-parser-t5-device.md:72-88`），并使真实 `visual_js` worker warm-up 12/12 失败、隔离 12/13 红（`research/evidence/2026-10-28-batch-29/step02-isolation-race.md:83-100`）。**结论与状态**：③ 设备像素证据已出（正例绿）、①② 生成门由 JVM/Node 台钉住（上方 T5/T6 块），但 **④ 设备面红且缺陷已打到 worker 启动路径**，本条**维持 open**；修复方向 = 两侧 Paint 在构造/`forEnvironment` 后显式置为指纹值（首选，修法见 `research/evidence/2026-10-28-batch-29/step01-painter.md:89-103`）或把指纹常量改为平台默认（不推荐，随 API 漂移）；修后须复跑 painter 21 例 + flags 2 例 + 隔离 13 例取新证据。**JS 不广告不变（KD-76 open）**。

**Progress（2026-10-28 复核修复轮；零 Gradle/零设备；状态保持 open，待设备复跑）.** ④ 按上方设备轨推荐的首选修法落码：**两侧 Paint 构造后显式装配指纹值**（常量仍为 0，不改为随 API 漂移的 1283）——

- 度量侧：`app/src/main/kotlin/com/tingyun/smartmistakebook/visualjs/VisualContentMeasuring.kt` `PaintVisualTextMeasureBackend.forEnvironment` 在 `Paint()` 之后先 `paint.flags = DEFAULT_PAINT_FLAGS`（显式 0）再设 `textSize`，随后与 `environment.paintFlags` 比对。**校验比的是实际 Paint 而非 environment 自述**，故 `paintFlags=1` 的坏指纹仍 fail-closed（原 2 例设备判据语义不变）。
- 回放侧：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPainter.kt` 的 `textPaint` 改为 `Paint().apply { flags = REPLAY_TEXT_PAINT_FLAGS }`（显式 0）。`REPLAY_TEXT_PAINT_FLAGS`/`DEFAULT_PAINT_FLAGS` 仍为 0；KDoc 改为"显式钉死、非平台默认（API 34 = 1283）"。
- 设备判据同步：app `VisualMeasureReplayPaintFlagsInstrumentedTest` 删去"`Paint()` 默认即回放值"的错误对照，改为钉 `DEFAULT_PAINT_FLAGS == 0`、指纹 `paintFlags=0` 且 `Paint().flags != 0`（把"必须显式置值"变成常驻判据）；core `VisualDrawOpsPainterInstrumentedTest` 的 flags 用例改名 `theReplayTextPaintKeepsTheExplicitlyPinnedFlagsMirroredByTheMeasureFingerprint`（断言不变）。
- 同轮复核 R3：回放 `playDrawPath` 门序改为 **dash 形状门 → stroke-only 门**，与 `visual-compiler.js`（`requireStroke` 内 dash 门先于 path 节点 stroke-only）和 `VisualDrawOpsParser`（`parseStroke` 先于 stroke-only）同码同序；新增设备负例 `aStrokeOnlyPathWithAnOddDashReportsTheSameFirstGateAsTheGenerators`（期望 `PAINTER_DASH_ODD`）。
- 同轮 R10：`VisualDrawOpsPainterRendered.kt` 的 `drawRendered` 补两例设备判据——有效序列逐像素 == `validatedDocument` 路径；null/截断命令 → `PAINTER_DRAW_COMMANDS` 且消息内嵌 `DRAWOPS_MISSING_COMMANDS`/`DRAWOPS_MISSING_FIELD`。
- **验证状态**：本会话**未运行 Gradle、未上设备**。修复后 painter 套件计数已变（现树 `grep -c "@Test"` = 24），app flags 2 例、隔离 13 例的绿轮全部 **UNVERIFIED**；④ 的关闭仍以设备证据为准（painter 全量 + flags 2 + 隔离 13 全绿、warm-up ready > 0）。契约 §5 flags 条文同轮已更正。

**Progress（2026-10-29 批次 30 T12 状态核对 + 计数回改；只读，零 Gradle/零设备/零 git 写；状态保持 open）.** **（2026-10-29 复核修复轮更正）** 本块"`docs/research/evidence/2026-10-29-batch-30/` 不存在 / 修复后设备复跑未出数"只对 T12 时点（约 01:11）成立，**不是现树事实**：设备轨 02:25–02:41 已落盘该目录 **11 份**证据；修复后实测 painter **24/24 绿**、跨侧 flags **2/2 绿**（④ 九项判据全满足 → 设备轨判定 ④ 可关闭）、隔离 13/13 绿、PDF **16/16 绿**；parity 11 例 **1 红**（P07 `F4_GEOMETRY` centroid 0.5181，A 类：原型门初始阈值未标定，未放宽）。现树计数（`grep -c "@Test"`）：painter **24**、parity **11**、PDF **16**、PureTest **13**、跨侧 flags **2**、隔离 **13**。本条在记录员裁定前仍保持 open，但"余项 = ④ 修复后设备复跑"应改为"④ 设备判据已满足、待裁定"，**不得再把"未出数 / 目录不存在"当现树事实**。旁证：R2 设计文档的"设备 19 例 + JVM 10 例"与本条计数漂移已同批回改（`docs/research/2026-10-28-screen-pdf-parity-gate.md:14/80`）。**锚点行号回改（附录 A 基线 §3 的漂移项，本轨现树实测）**：上方 T5/T6 Progress 引用的旧行号随修复下移，原文按补记惯例保留，定位以本表为准——`VisualDrawOpsPainterPure.kt` 的 `REPLAY_TEXT_PAINT_FLAGS` `:78`→**`:87`**、`effectiveDrawOpsAlpha255` `:160`→**`:169`**、`drawOpsAlpha255` `:177`→**`:186`**；`VisualContentMeasuring.kt` 的 `paintFlags` 字段 `:141`→**`:144`**、`paintFlags>=0` 校验 `:157`→**`:160`**、指纹串 `:171`/`:167-171`→**`:174`/`:172-175`**、`DEFAULT_PAINT_FLAGS` `:184`→**`:192`**、`paintFlagsForTest` `:426`→**`:435`**、`forEnvironment` `:456-464`→**`:463-481`**（throw 在 `:474-478`）；`VisualDrawOpsPainter.kt` 的 `textPaintFlagsForTest` `:265`→**`:273`**、alpha 计算点 `:162/169/195`→**`:170/177/203`**；`VisualDrawOpsPainterInstrumentedTest.kt` 的 8 位色 alpha 用例函数 `:252`→**`:253`**、flags 用例函数 →**`:264`**（其断言在 `:274-275`）。

**Progress（2026-10-29 批次 30 设备复跑裁决；只读，零 Gradle/零设备/零 git 写）.** 设备轨（`emulator-5562` / API 34 / x86_64 / 4096）在修复后源上跑 `docs/research/evidence/2026-10-29-batch-30/step02-flags.md`、`step01-isolation.md`、`step03-painter.md`、`step08-signatures.md`：

- **④-P1**：painter `theReplayTextPaintKeepsTheExplicitlyPinnedFlagsMirroredByTheMeasureFingerprint` 在 **`OK (24 tests)`** 内（预期 0 == 实际 0；批次 29 此处为 1283 红）。
- **④-P2**：跨侧 flags **`OK (2 tests)`**；`PARITY_ENV` 同设备指纹含 `replayTextPaintFlags=0`。
- **④-P3/P4**：隔离 **`OK (13 tests)`**；`Start proc .*:visual_js` 17 与 `warm-up finished` 17 **逐 pid 相同**，`warm-up failed` = 0。
- **④-N1/N2**：坏指纹 `paintFlags=1` 仍 fail-closed；`Paint().flags != 0` 常驻钉子绿。
- **④-L1/L2/L3**：isolation/flags/painter 三份 logcat 的装配级恒抛 **0**；kill 四类 5+4+7+1 = 17 = spawn、无未分类杀因；11 份 logcat 9 类签名全 0。

**裁决：④ 关闭（2026-10-29 批次 30）。** 依据 = 上述九项全满足，且批次 29 同设备同断言的 `expected 0 but was 1283` 未复现。**整条 KD-78 仍 open**：①② 的生成侧缺口不在本批设备面，按本条 Reopen condition 四项另行核销。**未验证**：④ 的逻辑负例（把常量改回 1283 后应红）未执行；真机/arm64 未跑。证据回链 `docs/research/2026-10-29-batch-30-record.md` §4.2。

## KD-79 (closed 2026-10-29 批次 30 设备复跑满足关闭条件；2026-10-28 批次 29 复核修复轮登记；高) · PDF 写盘失败 fail-open：`PdfDocument.writeTo` 在 API 34 吞掉 OutputStream 异常，`PAINTER_PDF_IO` 不可达，调用方把截断 PDF 当成功

**Symptom（设备实测 + AOSP 源实读，2026-10-28 批次 29 设备轨 R2）.** `VisualDrawOpsPdfPainter.write()` 直接 `pdf.writeTo(out)` 并 `catch (IOException)`（原 `:88-95`）。API 34 的 native `SkJavaOutputStream::write`（android-14.0.0_r1 `CreateJavaOutputStreamAdaptor.cpp`）在 OutputStream 抛出 Java 异常时执行 `ExceptionDescribe()` → `ExceptionClear()` → `return false`，异常**不会**逃回 Java，`writeTo` 正常返回（void，无失败标志）。因此该 catch **不可达**；`FailingOutputStream` 场景下 `pdfWriteFailureIsCoded` 拿不到 `PAINTER_PDF_IO`（`Tests run: 13, Failures: 1`，`docs/research/evidence/2026-10-28-batch-29/step04-pdf-parity.md`；logcat 230 次 `java.io.IOException: simulated write failure` 全部被 native 打印后清除）。

**Impact.** 写盘失败（磁盘满、流已关闭、远端断开）时 PDF 被静默截断，而调用方认为导出成功——与本类 KDoc "I/O 归类消灭 IOException 逃出"及设计 §7.3 码面表相反。当前 `VisualDrawOpsPdfPainter` 只被测试引用、未进生产装配，故实际暴露面受限；但生产装配前必须关闭（fail-open 属最危险类别：数据缺失无诊断）。

**Fix direction / Progress（2026-10-28 复核修复轮落码；零 Gradle/零设备）.** `core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainter.kt` 新增 `PdfWriteFailureTrackingStream`：包装调用方 OutputStream，在每个 `write/flush/close` 入口捕获 `Exception`、记录首个失败并**重抛**（重抛保证 native 仍按"本次写失败"处理，记录保证 `writeTo` 返回后可见）。`writeTo` 返回后若 `firstFailure != null` → `paintingFailure(PAINTER_PDF_IO, …)`；保留原 `catch (IOException)` 作未来平台行为变化时的双重兜底。设备判据 `pdfWriteFailureIsCoded`（`VisualDrawOpsPdfPainterInstrumentedTest.kt:443-450`）语义不变，修复后应绿。

**Reopen/关闭条件.** 设备轨在 API 34 上复跑 PDF 13 例（**批次 31 T5 现树计数更正**：`VisualDrawOpsPdfPainterInstrumentedTest` 现为 **16 例**（`grep -c "@Test"` 实测），13 是批次 29 登记时点；批次 30 T5 追加 `writeFile`/`PdfWriteIntegrity` 3 例，批次 30 设备轨已按 16 例执行 `OK (16 tests)`；`pdfWriteFailureIsCoded` 绿 = `PAINTER_PDF_IO` 可达）且 logcat 仍出现 native 打印的模拟 IOException（证明确实走了 fail-open 路径而修复把它转成有码异常）；若未来该 painter 进入生产装配，另需"部分写后进程被杀/流关闭"的恢复语义裁定。**验证状态**：本会话未运行 Gradle/设备，修复的绿轮 **UNVERIFIED**；不得以"代码已改"读成已关闭。

**Progress（2026-10-29 批次 30 T12 状态核对 + 设计文档更正注记；只读，零 Gradle/零设备/零 git 写；状态保持 open）.** **（2026-10-29 复核修复轮更正）** 本块"证据目录不存在 / 修复后设备复跑未出数 / PDF 13 例"只对 T12 时点（约 01:11）成立，**不是现树事实**：设备轨 02:25–02:41 已落盘 `docs/research/evidence/2026-10-29-batch-30/`（11 份）；`VisualDrawOpsPdfPainterInstrumentedTest` 现树 **16 例**，设备复跑 **`OK (16 tests)`**（`step04-pdf.md`），`pdfWriteFailureIsCoded` 绿、logcat 230 条 native `java.io.IOException: simulated write failure`——关闭判据 79-P1/79-P2 满足，设备轨判定**可按 KD 自身条件关闭**；D3 的 ENOSPC/静默短写/页数不符/`renameTo` 失败四缺口仍 **UNVERIFIED**。本轨只读复读现树锚点确认修复仍在树：`core/data/src/main/kotlin/com/tingyun/smartmistakebook/core/data/visual/VisualDrawOpsPdfPainter.kt` 的 `PdfWriteFailureTrackingStream`（`:98-109`）与 `PAINTER_PDF_IO` 兜底（`:117-163`）未变；文件 **154 行**（`wc -l` 实测），契约 §5.1 的"104 行"与"仪器化 13 例"已同批回改。R1 设计文档 `docs/research/2026-10-28-pdf-replay-differences.md` 顶部已加"writeTo 结论被设备否证"更正注记（指向本条）。**本条在记录员裁定前仍保持 open，但不得再把"未出数 / PDF 13 例"当现树事实**。

**Progress（2026-10-29 批次 30 设备复跑关闭裁决；只读，零 Gradle/零设备/零 git 写）.** 设备轨（`emulator-5562` / API 34 / x86_64 / 4096）在修复后源上执行 `VisualDrawOpsPdfPainterInstrumentedTest`：**`OK (16 tests)`**（`step04-pdf.md`），`pdfWriteFailureIsCoded` 绿 = `PAINTER_PDF_IO` 可达；logcat **230** 条 native `java.io.IOException: simulated write failure`（`step08-signatures.md` §4），证明失败路径确实被走到、修复把它转成有码异常。设备轨另判 79-P3（`pdfFileWriteRoundTripsThroughReadback`：目标存在/非空/含 `%%EOF`/`PdfRenderer` 回读 48×32/无 `.pending-` 残留）与 79-N2（`pdfWriteFailureMidStreamKeepsTheFirstFailureText`，首块后抛且 `acceptedBytes>0`）亦绿。

**裁决：KD-79 关闭（2026-10-29 批次 30）。** 依据 = 本条 Reopen/关闭条件 79-P1 + 79-P2 + 79-L1 + 79-L2 全满足；计划 C.6 口径另需 79-P3 + 79-N2 亦满足。**残留（不得读成已覆盖）**：79-N3 磁盘满（ENOSPC）模拟、79-N4 静默短写（设备面；仅 JVM `PdfWriteIntegrityTest` 16 例覆盖，本批未跑）、79-N5 页数回读不符注入、79-N6 的 `renameTo` 失败注入**均无设备用例 → UNVERIFIED**；计划任务 6 的"磁盘满模拟"未做。若该 painter 未来进入生产装配，另需"部分写后进程被杀/流关闭"的恢复语义裁定。现树 `VisualDrawOpsPdfPainter.kt` 已 **258 行**（T5 追加 `writeFile` + `PdfWriteIntegrity` 接入；上文 T12 块记的 154 行是 T5 落码前时点）。证据回链 `docs/research/2026-10-29-batch-30-record.md` §3.5/§4.2。

## KD-80 (closed 2026-10-29 批次 34；2026-10-29 批次 30 登记；高) · schema 70 落地后备份机器清单一度仍停在 69：v70 库备份/恢复 fail-closed（设备实测 34 例 8 红；批次 31 S=70 复跑 34/34 绿；批次 32 S=71 复跑 34/34 绿；**批次 33 在最终 data APK `36f79b19…` 上 14 套件全绿：备份 34、裁剪器 9、提交 12、仓库 11、80-C4 5，C1/C4 设备面核销；C2 静态 PASS、设备侧未逐名 → PARTIAL；80-C3 设备 9/9 + 主会话 JVM 12/17 已满足；批次 34 在同源 data APK `53e69f48…` 上跑 rowCounts 逐名设备用例 `OK (2 tests)`（90 键逐名 PASS）→ 四条全满足，关闭**）

**Symptom（设备实测 + 读码，2026-10-29 批次 30）. `StudyDatabase` 升到 70（`StudyDatabase.kt`）后，`SmbkSelectionPlan` 当时仍写 `SCHEMA_VERSION = 69`、`allTables.size == 86`、`SPLIT = 5`、软引用 20、旧 `IDENTITY_HASH = 6633b7c2fe223041c4a2927203853f4f`；`BackupSnapshotTrimmer` 在裁剪前要求 `database.version == SmbkSelectionPlan.SCHEMA_VERSION`，不等即 fail。设备 `BackupRestoreInstrumentedTest` → `Tests run: 34, Failures: 8`：7 条 `BackupSnapshotTrimException: 快照 schema=70，裁剪器只支持 schema=69`，1 条 `schema68ArchiveRestoresAndMigratesTo69 expected:<69> but was:<70>`（`docs/research/evidence/2026-10-29-batch-30/step06-backup.md`）。

**Impact.** 该窗口内 v70 库的备份/恢复全路径不可用（fail-closed：不会产生损坏数据，但功能整体不可用）。属批次 30 D-01「备份资产枚举同步」交付项。

**Fix direction / Progress（已落树，零设备证据）.** 现树 `SmbkSelectionPlan.kt:40` 已是 `SCHEMA_VERSION = 70`、`:538` 已是 `require(allTables.size == 90)`（注释 90 张表：类别 74 + ALWAYS 7 + DERIVED 2 + SPLIT 7）；`BackupSnapshotTrimmer` 的版本门改为跟随该常量（`:268-271`）；`BackupRestoreInstrumentedTest` 的用例已由 `…MigratesTo69` 改名为 `schema68ArchiveRestoresAndMigratesToCurrentVersion`（`git diff` 5 增 5 删）。**但这些改动发生在设备轨之后**，34/34 绿轮**没有跑过**。

**Reopen/关闭条件.** `BackupRestoreInstrumentedTest` 34/34 在 v70 库上绿；`SmbkSelectionPlan` 表清单与 `70.json` 的 90 张实体逐名一致（附可复跑比对命令）；`BackupSnapshotTrimmer` 与选择计划单测绿；备份/恢复后旧行与视觉四表引用关系保持。**验证状态**：设备绿轮 UNVERIFIED，不得以"代码已改"读成已关闭。

**Progress（2026-10-29 批次 31 T5 状态核对 + 关闭条件复核；只读；状态保持 open）.** 修复仍在树（本轮复读实测）：`SmbkSelectionPlan.kt:40` `SCHEMA_VERSION = 70`、`:43` `IDENTITY_HASH = "11a8a701bae767a8a3c5bc810e4743a4"`、`:46` `CLOSURE_VERSION = 3`、`:538` `require(allTables.size == 90)`、`:554` `softReferences.size == 21`；`BackupSnapshotTrimmer.kt:268-283` 的 schema 与 `room_master_table` identityHash 两门均跟随常量；`BackupRestoreInstrumentedTest.kt:488` 已改名 `schema68ArchiveRestoresAndMigratesToCurrentVersion`、`:590` 期望 `SmbkSelectionPlan.SCHEMA_VERSION`（该文件仍 ` M`，是批次 30 修复未提交的改动），整文件 **34 例**（`grep -c "@Test"`）。表清单一致性本轮复算：`70.json` 的 `database.entities` = **90**、`identityHash` 逐字相同；源码引用的表名与 90 个实体名**双向差集为空**（python 实跑，附录 A）。**设备绿轮本批未出数**：`docs/research/evidence/2026-10-29-batch-31/` 不存在（`ls` = `No such file or directory`），`BackupRestoreInstrumentedTest` 34/34 本批未跑；`grep -c` 只是源计数，不是执行结果。故 Reopen/关闭条件（34/34 设备绿 + 清单逐名一致 + 裁剪器/选择计划单测绿 + 旧行与四表引用保持）**仍未满足**。**另加一项覆盖缺口**：本轮 `grep -rln "tutor_visual" core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/ | wc -l` = **0**，即 34 例对第四项"备份/恢复后视觉四表引用关系保持"**零覆盖**；即使 34/34 全绿也不足以按台账原文关闭，需补断言或如实记 UNVERIFIED（与批次 31 T1 设备判据 `docs/research/2026-10-29-batch-31-device-criteria.md` §0-3 一致）。**本条保持 open，不得以"代码已改"读成已关闭。**

**Progress（2026-10-29 批次 31 设备轨回填 + 复核修复轮；只读，零 Gradle/零设备/零 git 写；状态保持 open）.** 上一块"`docs/research/evidence/2026-10-29-batch-31/` 不存在 / 34/34 本批未跑"只对 T5 快照（05:39）成立，**不是现树事实**：设备轨 06:20–06:54 已落盘该目录 **15 份**证据（`ls` 实测：`00-index.md`、`step00`–`step12`、`step08b`；索引 `docs/research/evidence/2026-10-29-batch-31/00-index.md` §2/§8）。逐项回填：

- **80-C1 = PASS**：`BackupRestoreInstrumentedTest` 设备实跑 `OK (34 tests)`（E-01，06:31；批次 30 的 8 条红例逐条 `STATUS_CODE=0`，`BackupSnapshotTrimException`/`expected:<69>` 均为 0）。
- **80-C2 = PASS**：`S=70 python build/device-track-batch31/kd80-c2-compare.py` → `RESULT=PASS`、`EXIT=0`（90 实体 / 2 视图 / 双向差集空 / identityHash 逐字同）。
- **80-C3 = PARTIAL**：设备 `BackupSnapshotTrimmerInstrumentedTest` `OK (9 tests)`（E-11）；JVM 半边在 E-12 记"本轨未执行"。**本机 JUnit XML 补充旁证（非仓内证据、命令归属 UNVERIFIED）**：`core/data/build/test-results/testDebugUnitTest/TEST-…SmbkSelectionPlanTest.xml` = `tests="12" failures="0" errors="0"`、`TEST-…VisualCheckpointRetentionTest.xml` = `tests="17" failures="0" errors="0"`，mtime 均为 2026-10-29 06:21。按"未跑 = 未验证"的仓内口径，仍只记 PARTIAL。
- **80-C4 = UNVERIFIED**：`grep -rln "tutor_visual" core/data/src/androidTest/.../backup/` = 0 命中，34 例对视觉四表引用关系零断言。
- **结论**：`80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4` 未满足 → **KD-80 保持 open**；可写"80-C1/80-C2 已核销"，不得写 closed，也不得再写"34/34 未跑 / 证据目录不存在"。
- **记录回链（2026-10-29 记录员）**：设备轨与现树存在 **7 个源文件差**（提交/仓库/结构门/parity 的新内容未上设备；设备 34/34 绿仍有效，但它对应的是 06:20 的源），详见 `docs/research/2026-10-29-batch-31-record.md` §4.4/§5.2 #1。

**Progress（2026-10-29 批次 32 T6 状态核对；只读，零 Gradle/零设备/零 git 写；状态保持 open）.** 本轨逐句清点"反向虚报"（把已有证据写成"目录不存在/未跑"）：上列批次 30 T12 与批次 31 T5 的"证据目录不存在 / 34/34 未跑"均已被其后设备轨块显式取代（E-01 34/34 绿、E-12 80-C2 PASS）；本块不改写历史块，只把**现树**与**KD-80 状态**钉住：

- **schema 常量的现树事实（本轨 10:05 快照实读，未运行 Gradle/KSP）**：`core/database/.../StudyDatabase.kt:139` 已是 `STUDY_DATABASE_VERSION = 71`（工作树未提交，属批次 32 schema 轨）；`VisualSnapshotIdentityMigration_70_71.kt:52-53` 已有 `object : Migration(70, 71)` 并注册于 `StudyDatabase.kt:411`；但 **`71.json` 仍不存在**（`ls core/database/schemas/.../ | sort -n | tail` 最高 70），`SmbkSelectionPlan.kt:40` 仍是 `SCHEMA_VERSION = 70`、`:43` 仍是 `IDENTITY_HASH = "11a8a701bae767a8a3c5bc810e4743a4"`、`:538` 90 表、`:554` 21 条软引用；`VisualSnapshotIdentityMigration_70_71.kt` 为 `??`（未跟踪）。
- **对 KD-80 的直接后果**：`BackupSnapshotTrimmer` 的版本门跟随 `SmbkSelectionPlan.SCHEMA_VERSION`（`:268-271`）。一旦本轨之后 v71 库被创建而常量/清单未回填，备份/恢复会**原形复现** `快照 schema=71，裁剪器只支持 schema=70` 的 fail-closed（即 KD-80 本体的失败形态，不是新缺陷）。因此 **KD-80 继续 open**：现有 80-C1/80-C2 的绿是 **S=70** 口径，**不得**拿来关闭 S=71；S=71 必须在 `71.json` 生成、`SmbkSelectionPlan` 常量回填后，按 T1 判据 §3.2 的 `S=71` 脚本重跑（80-C2）+ 备份 34/34 重跑（80-C1）。
- **不变项**：80-C3 仍 PARTIAL（JVM 仓内执行缺命令归属）、80-C4 仍 UNVERIFIED（34 例对视觉四表引用零断言）；关闭规则 `80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4` 不变。**本块不得读成 schema 71 已交付或 KD-80 已关闭。**

**Progress（2026-10-29 批次 32 集成接线；只读，零 Gradle/零设备/零 git 写；状态保持 open）.** 上块"`71.json` 仍不存在 / `SCHEMA_VERSION = 70`"是 **10:05 时点**，已被 T3 生成 + T4 回填取代；本块只钉现树事实，不改关闭判定：`core/database/schemas/com.tingyun.smartmistakebook.core.database.StudyDatabase/71.json` **已存在**（`database.version=71`、`identityHash=714c0ecb85ee5edf74f0013740148138`、90 entities / 2 views、378,961 B，mtime 2026-10-29 10:13；仍 `??` 未跟踪，提交必须纳入），`SmbkSelectionPlan.kt` 已回填 `SCHEMA_VERSION = 71` 且 `IDENTITY_HASH = "714c0ecb85ee5edf74f0013740148138"`；三处同值（`71.json` / 生成的 `StudyDatabase_Impl.kt` `RoomOpenDelegate(71, "714c…", …)` / 常量），`BackupSnapshotTrimmer.kt:268-283` 的版本与身份两门随常量自动对齐 71。**但这不等于 KD-80 可关闭**：80-C1 的 34/34 与 80-C2 的清单比对都是 **S=70 口径**；S=71 的备份 34/34、1→71 迁移矩阵、`SmbkSelectionPlanTest`/`BackupSnapshotTrimmerInstrumentedTest` 与设备结构门均**未在本批执行 → UNVERIFIED**（**这是 10:13 接线时点的表述，已被下方同日 11:19–11:35 设备轨块取代**：S=71 备份 34/34、1→71 矩阵 15、70→71 专用 3、裁剪器 9、设备结构门 5 例均已实跑；不得再把本句读成现树事实）。故 80-C1/80-C2 的 S=71 复跑仍是关闭前置，KD-80 保持 open。

**Progress（2026-10-29 批次 32 设备轨回填 + 复核修复轮；只读，零 Gradle/零设备；git 仅 `add` 三条交付物；状态保持 open）.** 上块末句"…均**未在本批执行 → UNVERIFIED**"是 **10:13 接线时点**，已被同日 **11:19–11:35** 的设备轨取代；本块只回填执行事实，不改关闭判定：

- **S=71 设备证据（实跑）**：70→71 专用 `OK (3 tests)`（E-04，`build/device-track-batch32/step04-migration-70-71.log`，11:19）；1→71 矩阵 `OK (15 tests)`（E-05，`step03-migration-matrix.log`，11:19–11:26）；备份/恢复 `OK (34 tests)`（E-06，`step01-backup.log`，11:26），其判据明写"schema68 快照恢复后读到的库版本必须是 `SmbkSelectionPlan.SCHEMA_VERSION`（现 71）"，且 `BackupSnapshotTrimException`/`expected:<70>`/`expected:<71>` 均 0；裁剪器 `OK (9 tests)`（E-09，`step09-trimmer.log`）。**不得再把这几项写成"未在本批执行"。**
- **结构门仍非全绿**：E-07 `Tests run: 5, Failures: 1`（`step05-structure-gates.log`）；红因是用例自身在 `:221` 读取已在 `:205` 断言删除的行（E-13，A 类）。复核修复轮已把读侧改读未列入淘汰候选的 STAGING 行（`snap-staging-keeper`），**修复后的绿轮尚未重跑**；重跑前不得写"结构门全绿"。
- **仍 UNVERIFIED**：JVM `SmbkSelectionPlanTest`（12 例）、`VisualCheckpointRetentionTest`（17 例）等纯函数门在本轮**未执行**（设备轨只跑 `assemble*`，无任何 `testDebugUnitTest` 记录）；触发器 backstop 未落，本批不声称 DB 级触发器生效。
- **交付链**：`71.json`、`VisualSnapshotIdentityMigration_70_71.kt`、`TutorVisualSnapshotIdentityMigration_70_71InstrumentedTest.kt` 已由复核修复轮 `git add` 纳入索引（此前 `??`；文件内容未改）。
- **KD-80 判定不变**：80-C1（S=71 备份 34/34）与 80-C2（S=71 清单比对 `RESULT=PASS`/`EXIT=0`）的设备面已有绿轮；80-C3 的 JVM 半边与 80-C4（视觉四表引用零断言）仍不满足 → `80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4` 未全满足，**保持 open**，不得写 closed。

**记录回链（2026-10-29 批次 32 记录员；只读核对，零 Gradle/零设备/零 git 写；状态保持 open）.** 上块"JVM `SmbkSelectionPlanTest`/`VisualCheckpointRetentionTest` 未执行"是 **11:35 设备轨时点**；复核修复轮之后本机 `build/` 下有这两套件的 JUnit XML（mtime **12:36–12:38**，`SmbkSelectionPlanTest` 12/0、`VisualCheckpointRetentionTest` 17/0、`TutorVisualIdentityTest` 6/0、`TutorVisualSchemaContractTest` 9/0、`KernelWave0SchemaContractTest` 8/0），但属 **build/ 旁证、非仓内命令日志**，80-C3 仍记 **PARTIAL**。**设备绿轮的源边界**：设备 APK 构建于 **11:14–11:17**，现树在 **12:26–12:28** 另有 **11 个源文件**改动（`src-sha-before.txt` 对现树：850/861 相同 / 11 不同，清单见 `docs/research/2026-10-29-batch-32-record.md` §4.4），其中包含 `RoomStudyDatabase`/`TutorVisualCommitRepository` 的 stage 淘汰集修复与仓库第 9 例；因此 80-C1 的 34/34 与 80-C2 的清单 PASS 仍只对 11:14 的源成立，**当前树复跑 UNVERIFIED**。80-C4 仍零断言。**KD-80 保持 open，关闭判定不变。**

**Progress（2026-10-29 批次 33 T6 状态核对；只读，零 Gradle/零设备/零 git 写；状态保持 open，不写关闭建议）.** 本批 T6 时点**无新的 KD-80 执行证据**：`docs/research/evidence/2026-10-29-batch-33/` 不存在（`ls` = `No such file or directory`），设备轨在 T6 之后串行执行。逐条：

- **80-C1**：批次 32 设备轨 S=71 备份 `OK (34 tests)`（`build/device-track-batch32/step01-backup.log`，11:26）仍是对**11:14–11:17 的源**；批次 32 记录 §4.4 实读 12:26–12:28 另有 11 个源文件改动（含 `RoomStudyDatabase`/`TutorVisualCommitRepository`）。本批未重跑 → 现树复跑 **UNVERIFIED**。
- **80-C2**：批次 32 S=71 清单比对 `RESULT=PASS`/`EXIT=0` 同属旧源；本批未重跑 → 现树复跑 **UNVERIFIED**。
- **80-C3**：仍 **PARTIAL**——设备裁剪器 `OK (9 tests)`（批次 32 E-09）；JVM 半边只有本机 build/ XML 旁证（`SmbkSelectionPlanTest` `tests="12" failures="0"`、`VisualCheckpointRetentionTest` `tests="17" failures="0"`，mtime 2026-10-29 12:38:58），非仓内命令日志，命令归属 UNVERIFIED。
- **80-C4**：T6 实测 `grep -rln "tutor_visual" core/data/src/androidTest/kotlin/com/tingyun/smartmistakebook/core/data/backup/ | wc -l` = **0**；`BackupRestoreInstrumentedTest` 仍 **34** 例、零视觉四表引用断言。本批研究 `docs/research/2026-10-29-backup-visual-reference-checks.md` 已给出 5 例设计与判据（§5.3），但该 doc §7.1 自认**未编译、未上设备**，故 80-C4 仍 **UNVERIFIED**。
- **结论**：`80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4` 未全满足 → **KD-80 保持 open；本块不写关闭建议**。若本批设备轨把 80-C4 用例跑绿、80-C1/C2 在新源上复跑、80-C3 命令归属落盘，再由证据轨/记录员按关闭条件裁决；在此之前不得把本批研究设计读成执行证据。

**Progress（2026-10-29 批次 33 设备轨回填 + 复核修复轮；只读回填 + 测试源码补强；零 Gradle/零设备；状态保持 open）.** 上块"证据目录不存在 / 80-C4 零断言 / 未重跑"只对 **T6 时点（约 14:15）**成立，**不是现树事实**：设备轨 14:44–15:13 已落盘 `docs/research/evidence/2026-10-29-batch-33/` **10 个 `.md`**（设备轨落盘时 988 行；复核修复轮回写后 1002 行）；本会话对最终构建源快照实跑 `sha256sum -c build/device-track-batch33/src-sha-before-c4c.txt` → **862 个文件全 OK、EXIT=0**，证明下列绿轮对应的正是这一份源，不是旧包。逐条：

- **80-C1 = PASS**：`BackupRestoreInstrumentedTest` 设备实跑 **`OK (34 tests)`**（E-03，`build/device-track-batch33/step01-backup-final.log`；最终 data APK `36f79b19…`）。**不得再写"34/34 未跑"**。
- **80-C2 = PARTIAL（不是 PASS）**：`S=71` 静态比对 `RESULT=PASS` / `EXIT=0`；但判据 `docs/research/2026-10-29-batch-33-device-criteria.md:255-257` 明确要求设备侧"90 键逐名写进证据结论，不得只写 34/34"，而 E-03 §4 自认 raw log 不逐表打印（对 `step01-backup-final.log` 实跑 `grep -c "rowCounts"` = 0、`grep -c "tutor_visual_snapshot"` = 0）。设备半边只能记 `UNVERIFIED`；`00-index.md` §4 已同轮由 PASS 改为 PARTIAL。
- **80-C3 = PARTIAL（不变）**：设备 `BackupSnapshotTrimmerInstrumentedTest` **`OK (9 tests)`**（E-03，`step03-trimmer-final.log`）；JVM `SmbkSelectionPlanTest` 12 / `VisualCheckpointRetentionTest` 17 本轨**未执行**（设备轨无 `testDebugUnitTest`），仍无仓内命令日志。
- **80-C4 = PASS**：新增 `core/data/src/androidTest/.../backup/BackupVisualReferenceInstrumentedTest.kt`（**1181 行 / 5 例**，`grep -c "@Test"` 实测 = 5），设备实跑 **`OK (5 tests)`**（E-04，`build/device-track-batch33/step04-c4-visual.log`）。**不得再写"80-C4 零断言 / 用例类不存在"**。
- **结论**：`80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4` 仍未全满足（C2 设备侧逐名、C3 JVM 半边）→ **KD-80 保持 open**。可写"80-C1 / 80-C4 在 `36f79b19…` 上已核销；80-C2 静态半边 PASS、设备侧未逐名；80-C3 设备半边 PASS、JVM 半边 PARTIAL"。
- **同轮台账/证据回写（复核修复员）**：本标题与 `00-index.md` §4 已按现树更正；结构门/提交/仓库/retention 四处 KDoc 的"本批未执行"已改写为实际读数（结构门 `OK (5 tests)`、提交 `OK (12 tests)`、仓库 `OK (11 tests)`；retention 仍 **UNVERIFIED**，JVM 未被设备轨执行）；判据文档的 T1 快照句已加"已被设备轨取代"标注。
- **测试补强（本轨落码，UNVERIFIED）**：仓库第 10 例补断言"plan 淘汰集必须含在飞行行"（区分事务外预检拒绝 vs 事务内重判），新增第 12 例"淘汰后净值仍超 → 写库前拒绝"（预算 40 < 净值 58），使 stage 侧正负例成对。本会话**不运行 Gradle/设备**，这两处改动后的 12 例复跑 **UNVERIFIED**；`OK (11 tests)` 只对补强前的源成立。

**记录回链（2026-10-29 批次 33 记录员；只读核对；状态保持 open）.** 记录员对 `build/device-track-batch33/src-sha-before-c4c.txt` 实跑 `sha256sum -c`：862 文件 **858 OK / 4 FAILED**（`TutorVisualCommitRepositoryInstrumentedTest`、`VisualCheckpointRetentionTest`、`TutorVisualCommitInstrumentedTest`、`TutorVisualStructureGatesInstrumentedTest`），即设备绿轮对应 14:44–15:13 的构建源，现树已有 4 个测试文件不同；仓库设备 **11 例**、现树 **12 例**，第 12 例与"plan 淘汰集含在飞行行"断言**从未上设备**。设备原始日志 grep：14 套件 `OK (5/34/9/5/15/4/3/12/11/16/24/16/13/2)`；`step01-backup-final.log` 对 `rowCounts`/`tutor_visual_snapshot` 均 **0 命中**（80-C2 设备侧仍 PARTIAL）。故标题"14 套件全绿"只对该构建源成立；**KD-80 保持 open**，关闭判定不变。记录见 `docs/research/2026-10-29-batch-33-record.md` §4.4/§4.5。

**Progress（2026-10-29 批次 33 主会话补证：80-C3 的 JVM 半边；实跑；状态仍 open）.** 主会话对当前树实跑 `./gradlew.bat :core:data:testDebugUnitTest --tests "*SmbkSelectionPlanTest" --tests "*VisualCheckpointRetentionTest" --rerun-tasks` → **EXIT=0**、`BUILD SUCCESSFUL in 2m 7s`（`--rerun-tasks` 强制重跑，非 UP-TO-DATE）；结果 XML 实测 `SmbkSelectionPlanTest tests="12" failures="0"`、`VisualCheckpointRetentionTest tests="17" failures="0"`。命令与阈值写入仓内证据 `docs/research/evidence/2026-10-29-batch-33/step-jvm-80c3.md`。**80-C3 由此满足**（设备裁剪器 9/9 见 E-03）。
仍未满足的只剩 **80-C2 的设备侧逐名**：`step01-backup-final.log` 对 `rowCounts`/`tutor_visual_snapshot` 均 0 命中，判据要求 90 键逐名进证据。**KD-80 保持 open，剩余项收敛为一项**：设备侧 rowCounts 90 键枚举（静态 `RESULT=PASS` 不计入）。

**Progress（2026-10-29 批次 34 T7 文档｜KD-80 按设备证据逐条裁决；只读，零 Gradle/零设备/零 git 写；状态保持 open）.** 本块只按**已落盘的设备证据**逐条裁决，不把用例落码读成执行证据：

- **设备证据边界（本轮 18:14 快照实读）**：`docs/research/evidence/2026-10-29-batch-34/` 在本轨写入自己的记录**之前不存在**（`ls` → `No such file or directory`；现目录内**仅**有本轨 `step-t7-doc-ledger.md`，无任何设备证据），`build/device-track-batch34` **不存在**；最近一轮设备证据仍是批次 33，且其绿轮对应 14:44–15:13 的构建源。**本批尚无任何设备执行结果可引用**。
- **80-C1 = 旧源 PASS / 本批 UNVERIFIED**：批次 33 最终 data APK `36f79b19…` 上 `BackupRestoreInstrumentedTest` `OK (34 tests)`（`build/device-track-batch33/step01-backup-final.log`）。该绿轮的源与现树不同（记录员 `sha256sum -c` 862 文件 858 OK / 4 FAILED：`TutorVisualCommitRepositoryInstrumentedTest`、`VisualCheckpointRetentionTest`、`TutorVisualCommitInstrumentedTest`、`TutorVisualStructureGatesInstrumentedTest`），且本批 `BackupRestoreInstrumentedTest` 开工即 ` M`；批次 34 判据 §2.1 明确禁止引用批次 33 绿轮 → **本批重建 APK 上复跑 UNVERIFIED，不得写 PASS**。
- **80-C2 = PARTIAL（静态 PASS / 设备 UNVERIFIED）**：静态半边本轮按判据 §1.4 原命令实跑复证 → `RESULT=PASS` / `EXIT=0`（90/90/90、`ONLY_PLAN=[]`、`ONLY_JSON=[]`、`DUP=[]`、`VIEWS_MATCH=True`、`PLAN_VERSION=JSON_VERSION=71`、`PLAN_HASH=JSON_HASH=714c0ecb85ee5edf74f0013740148138`；命令与逐行输出见 `docs/research/evidence/2026-10-29-batch-34/step-t7-doc-ledger.md` §2）。**设备侧 rowCounts 90 键仍未逐名**：批次 33 原始日志 `grep -c "rowCounts"` = **0**、`grep -c "tutor_visual_snapshot"` = **0**（本轮对 `step01-backup-final.log` 实读）。本批新增用例 `core/data/src/androidTest/.../backup/BackupRowCountsCoverageInstrumentedTest.kt`（2 例：`manifestRowCountsEnumerateTheNinetySchemaTablesByName`、`everyRowCountMatchesThePackagedAndRestoredDatabaseByName`；逐名 `assertTrue("rowCounts 缺少 71.json 表 $table")` + 每表 `COUNT(*)` 对包内库/恢复库）**已落码但未上设备** → 设备半边仍 `UNVERIFIED`。
- **80-C3 = 满足（旧源；本批未重跑）**：设备 `BackupSnapshotTrimmerInstrumentedTest` `OK (9 tests)`（`build/device-track-batch33/step03-trimmer-final.log`）；JVM 半边由主会话实跑 `:core:data:testDebugUnitTest --tests "*SmbkSelectionPlanTest" --tests "*VisualCheckpointRetentionTest" --rerun-tasks` → `EXIT=0`、`SmbkSelectionPlanTest 12/0`、`VisualCheckpointRetentionTest 17/0`（仓内证据 `docs/research/evidence/2026-10-29-batch-33/step-jvm-80c3.md`）。**判据本身满足**；但本批"同源/同 APK"硬门要求重取，故本批口径下仍是旧源绿轮。
- **80-C4 = 旧源 PASS / 本批 UNVERIFIED**：批次 33 `BackupVisualReferenceInstrumentedTest` `OK (5 tests)`（`build/device-track-batch33/step04-c4-visual.log`）；该测试文件现树未改（`git status --porcelain` 无该路径），但设备结果仍属批次 33 源，本批未重跑。
- **裁决**：四条**未在同一源/同一本批 APK 上全部取到**；其中唯一有明确缺口的是 **80-C2 设备侧 rowCounts 逐名**（用例已落码、设备未执行）。→ **KD-80 保持 open**。设备轨必须按 `docs/research/2026-10-29-batch-34-device-criteria.md` §1.5/§4，用本批**重建重装**的 APK 重取四条（含 `OK (2 tests)` 与 §1.3 的 90 行清单进证据结论）后才可关闭；在此之前不得写 closed，也不得把本块的静态 PASS 读成设备 PASS。

**Progress（2026-10-29 批次 34 设备轨回填｜80-C2 设备侧逐名已取到；KD-80 关闭）.** 上块 T7 的“本批尚无任何设备执行结果可引用 / 唯一缺口是 80-C2 设备侧”只对 18:14 快照成立，**不是现树事实**：设备轨 18:42–19:02 已落盘 `docs/research/evidence/2026-10-29-batch-34/`（11 份）与 `build/device-track-batch34/`（15 套件）。逐条回填：

- **80-C1 = PASS（本批）**：`BackupRestoreInstrumentedTest` 设备实跑 `OK (34 tests)`（E-02，`step02-backup-rowcounts-trimmer-c4.md` §3）。
- **80-C2 = PASS（本批，静态 + 设备侧逐名）**：静态 `RESULT=PASS` / `EXIT=0`（T7 §2 实跑）；设备 `BackupRowCountsCoverageInstrumentedTest` `OK (2 tests)`（raw `build/device-track-batch34/step02-rowcounts.log:7/:14/:21/:28/:34`，两条方法名均在未过滤的 `-r` 流中），90 键清单取自同源 asset `4a13d9d0…`（E-02 §4.1 逐名列出 90 表；`missing=[] extra=[]` 且 90 唯一）。
- **80-C3 = PASS（设备本批 + JVM 沿用批次 33 仓内命令日志）**：设备 `BackupSnapshotTrimmerInstrumentedTest` `OK (9 tests)`（E-02）；JVM 12/0 + 17/0 见 `docs/research/evidence/2026-10-29-batch-33/step-jvm-80c3.md`。**口径保留**：本批未重跑 JVM；若要求同批重跑，则 80-C3 的 JVM 半边记 `UNVERIFIED（本轮）`，其余三条仍成立。
- **80-C4 = PASS（本批）**：`BackupVisualReferenceInstrumentedTest` `OK (5 tests)`（E-02）。
- **同一源/同一 APK 家族**：`src-sha-before` 与 `after` 均 864 文件、`diff` 空；四包设备侧 sha 4/4 一致（`ead9d4a4…` / `2dd22651…` / `53e69f48…` / `f75d6fc8…`）。**已知边界**：该 864 文件清单的 find 集合未含 `core/model/src`（见 E-01 §8 的复核更正），本批 A-02/A-03 的 model 改动不在快照内；事后 `find core/model/src -newermt "2026-10-29 18:43:40"` 为空，但这是事后核对、不是构建时门。
- **裁决**：`80-C1 ∧ 80-C2 ∧ 80-C3 ∧ 80-C4` 在同一批设备证据上全部满足 → **KD-80 关闭（2026-10-29 批次 34）**。不得再写“80-C2 设备侧未逐名 / 本批无设备结果”。

**记录回链（2026-10-29 批次 34 记录员；只读；确认关闭）.** 记录员复核原始日志与静态门：`build/device-track-batch34/step*.log` 15 套件计数逐项与源码 `@Test` 一致（34/2/5/9/5/15/4/3/12/12/16/24/16/13/2），`step02-rowcounts.log` 含两条方法名与 `OK (2 tests)`；S=71 清单门本轮实跑 `RESULT=PASS`/`EXIT=0`；15 份 logcat 的 9/12 类崩溃签名全 0。**KD-80 关闭成立**。口径保留：80-C3 的 JVM 沿用批次 33 命令日志，本批未重跑；设备源快照 find 集合不含 `core/model/src`（见 E-01 §8），不影响 KD-80 的备份/视觉源（均在 data/database）。记录见 `docs/research/2026-10-29-batch-34-record.md` §4.4/§4.7。

**裁决复核与回链（2026-10-30 批次 35 台账维护员；只读核对 + 静态门复跑；零 Gradle/零设备/零 git 写；关闭维持）.** 批次 35 计划任务 6 的"本批裁决关闭"按基线核对（`docs/research/2026-10-30-batch-35-baseline.md` §四）收敛为：关闭裁决已由上两块写就，本块按批次 33/34 证据对**四条逐条回链复核**，全部成立：

- **80-C1**：`BackupRestoreInstrumentedTest` 设备 `OK (34 tests)`（E-02 §3 实读；raw `build/device-track-batch34/step01-backup.log` 本会话实读 `OK (34 tests)`，`BackupSnapshotTrimException`/`expected:<70>`/`expected:<69>` grep = 0 命中）。
- **80-C2**：设备侧逐名 `BackupRowCountsCoverageInstrumentedTest` `OK (2 tests)`（E-02 §4/§4.1；raw `step02-rowcounts.log` 本会话实读：`:7/:14` `everyRowCountMatchesThePackagedAndRestoredDatabaseByName`、`:21/:28` `manifestRowCountsEnumerateTheNinetySchemaTablesByName`、`:34` `OK (2 tests)`；90 键逐名清单 + `missing=[] extra=[]` 见 E-02 §4.1）；静态半边本轮按 T7 §2 原命令复跑 → `SCHEMA=71 PLAN_TABLES=90 UNIQUE=90 JSON_TABLES=90`、`ONLY_PLAN=[] ONLY_JSON=[] DUP=[]`、`VIEWS_MATCH=True`、`PLAN_VERSION=JSON_VERSION=71`、`PLAN_HASH=JSON_HASH=714c0ecb85ee5edf74f0013740148138`、`RESULT=PASS`/`EXIT=0`；且现树 `71.json` sha256 = `4a13d9d0b5ee561f088142267afe0a97890745ff66d57ae2b1191bef47881d94`（本会话实算，378,961 B）与设备绿轮所用 asset sha `4a13d9d0…`（E-02 §4.1）逐字节相同——静态半边与设备半边对的是同一份 schema 字节。
- **80-C3**：设备裁剪器 `BackupSnapshotTrimmerInstrumentedTest` `OK (9 tests)`（E-02 §3；raw `step04-trimmer.log` 本会话实读）；JVM `SmbkSelectionPlanTest` 12/0 + `VisualCheckpointRetentionTest` 17/0（`docs/research/evidence/2026-10-29-batch-33/step-jvm-80c3.md`，批次 33 主会话补证）。**口径保留不变**：批次 34 未重跑 JVM 半边，本批亦未重跑（设备绿绑定源快照；本轨零 Gradle）。
- **80-C4**：`BackupVisualReferenceInstrumentedTest` `OK (5 tests)`（E-02 §3；raw `step05-c4-visual.log` 本会话实读）。
- **结论**：四条回链与证据文件逐条一致 → **KD-80 关闭维持（closed 2026-10-29 批次 34；2026-10-30 批次 35 复核确认）**。本块只读证据回链，不构成新执行证据；已知边界沿用批次 34（设备源快照 find 集合当时不含 `core/model/src`，判据文档已由复核修复轮更正为含 `core/model/src` 的 973 文件口径，见 E-01 §8 与批次 34 判据 `:284/:302/:310`）。

## KD-81 (closed 2026-10-30 批次 35（按主会话裁定：内容身份 = db+wal，`-shm` 显式排除——它是只读导出会重建/改写的共享内存索引，纳入即制造假漂移；T5b 在树、37 项回归绿）；2026-10-29 批次 32 T6 登记（AUD-08 溯源缺口；中）；批次 34 工具侧 `source.snapshot` 覆盖 db+wal) · 切分评估快照的 `source.db.sha256` 只覆盖主库字节、不含 `-wal`/`-shm`：同一主库 + 不同 WAL 会得到不同导出却报同一快照身份

**Symptom（读码 + 受控实验复读，2026-10-29）.** `tools/split_golden/export_split_eval.py:72-73` 的 `_sha256(path) = hashlib.sha256(path.read_bytes()).hexdigest()` 与 `:221-222` 的 `source.db = {file, sha256, bytes}` 只读主库文件；全文 `grep -in "wal"` = **0 命中**。设备库默认 WAL 模式，未 checkpoint 的已提交行只在 `-wal` 内。受控实验（`tools/split_golden/README.md:86-89`）：两个快照主库 sha 同为 `946b896c…`，带 WAL 导出 1 页 2 框、无 WAL 导出 0 页。**本轨对批次 31 设备快照复跑（sha256/stat 实跑）**：`tmp/aud08-db-5562/smart-mistake-book.db` = `eb4d327147afa075ce28d143be5bc5aaf791eb59d202ca3ce8697b6351d8f4dc`（12,775,424 B，mtime 2026-10-29 05:26:18），`-wal` = `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`（**0 B**，mtime 05:38:47），`-shm` = `fd4c9fda9cd3f9ae7c962b0ddf37232294d55580e1aa165aa06129b8549389eb`（32,768 B，mtime 08:26:14）——三件 mtime 不同（不是同一时刻取），WAL 为空。

**Impact.** 报告里的 `source.db.sha256` 不能单独充当快照身份：同一主库 + 不同 WAL 会得到不同的 `pages`/门值，却报同一个 db sha。AUD-08 的门值复现与"设备库无分题数据"的判断都依赖该身份；批次 31 记录的"导出 0 页、门 0.2857 FAIL"建立在一个 WAL 为 0 B、且三件非同一时刻取得的快照上（设备数据度量 UNVERIFIED，`docs/research/2026-10-29-batch-31-record.md` §5.2 #6）。

**Fix direction（二选一；本轨只登记，未改工具）.** ① 把 `-wal`/`-shm` 纳入快照身份：manifest 为每个存在的边车记 `{file, sha256, bytes}`，身份 = 主库 + 边车排序后的摘要；或 ② 取库前显式 `PRAGMA wal_checkpoint(TRUNCATE)` 并校验结果（busy/log/checkpointed），把"无 WAL 帧"变成可验证前提。两条都必须保留"三件同一时刻取自同一设备"的取库纪律（`tools/split_golden/README.md` 步骤 1）。

**Detection（本轨已执行；可直接作为复跑门）.**

```bash
SNAP=tmp/aud08-db-5562/smart-mistake-book.db
for f in "$SNAP" "$SNAP-wal" "$SNAP-shm"; do
  [ -e "$f" ] && sha256sum "$f" && stat -c '%n %s %y' "$f"
done
# FAIL 判据：-wal 存在且 size>0，而报告 source.db 只有主库 sha（或三件 mtime 不同）
```

本轨输出（2026-10-29，逐行）：

```text
eb4d327147afa075ce28d143be5bc5aaf791eb59d202ca3ce8697b6351d8f4dc  tmp/aud08-db-5562/smart-mistake-book.db
e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  tmp/aud08-db-5562/smart-mistake-book.db-wal
fd4c9fda9cd3f9ae7c962b0ddf37232294d55580e1aa165aa06129b8549389eb  tmp/aud08-db-5562/smart-mistake-book.db-shm
smart-mistake-book.db 12775424 2026-10-29 05:26:18.701635400 +0800
smart-mistake-book.db-wal 0 2026-10-29 05:38:47.396713600 +0800
smart-mistake-book.db-shm 32768 2026-10-29 08:26:14.163243400 +0800
```

→ 该快照 WAL 为空但三件 mtime 不同，**不能**据此声明"设备库确无分题数据"；检测门在 WAL 非空时必须判"快照身份不完整"。

**关闭条件.** 工具侧 manifest 覆盖 `-wal`/`-shm`（或 checkpoint 完成证据），并用受控实验证明"同主库 + 不同 WAL 必得不同身份/可区分导出"；AUD-08 设备库复跑使用同一时刻三件快照并落盘。未满足前，报告 `source.db.sha256` 只能读作"主库字节摘要"，不得读作完整快照身份。**工具未改、设备未复跑，本条保持 open。**

**Progress（2026-10-29 批次 34 T7 文档｜工具侧修复已落码 + 回归测试实跑；设备未复跑；状态保持 open）.** 上句"工具未改"只对批次 32 T6 时点成立，**不是现树事实**：

- **工具侧修复（现树实读，17:50 落码）**：`tools/split_golden/export_split_eval.py` 新增 `_sidecar_paths`（db + `-wal`，`:101-104`）与 `_input_manifest`（`:107-135`）。产物身份 `source.snapshot` 的 `identityKind = "inputs-v1"`，`identitySha256 = sha256(canonical({"db": sha256(主库)|null, "wal": sha256(-wal)|null}))`（`:127-133`）→ **同一主库 + 不同 WAL 必得不同身份**。`-shm` 被**显式排除**（`_sidecar_paths` docstring：共享内存索引，只读导出会重建/改写它），mtime 也不进身份。空 `-wal`（0 B）按"无 WAL"归一（只读打开本身会创建 0 B `-wal`，否则第一次导出就会被自己的读副作用判成不一致）。导出前后各取一次清单，`identitySha256` 变化即 `DataError("快照一致性：导出期间 db/-wal 字节发生变化…")`（`:286-294`），且该校验先于写盘。读事务用显式 `BEGIN` 钉住一个 WAL 快照（`:222`）。旧字段 `source.db.sha256` 保留为主库字节摘要，**不塞新口径**（`:296-300`）。
- **回归测试（本轮实跑）**：`python -m pytest tools/tests/test_split_gate.py -q` → **36 passed in 1.74s**（EXIT=0）。其中与 KD-81 直接相关的用例：`test_same_main_db_different_wal_yields_different_identity`（`:669`）、`test_same_wal_snapshot_exported_twice_is_byte_identical`（`:701`）、`test_absent_wal_is_recorded_as_absent`（`:721`）、`test_empty_wal_sidecar_from_own_read_is_not_drift`（`:735`）、`test_export_rejects_input_change_during_export`（`:765`）、`test_reads_are_pinned_to_one_snapshot`（`:789`）。命令与输出见 `docs/research/evidence/2026-10-29-batch-34/step-t7-doc-ledger.md` §3。
- **关闭条件逐条**：① "同主库 + 不同 WAL 必得不同身份/可区分导出" → **工具侧已由受控实验用例证明**；② "manifest 覆盖 `-wal`/`-shm`" → **`-wal` 已覆盖，`-shm` 按设计排除**；这是与关闭条件字面表述的一处差异，需主会话裁定（内容身份上 `-shm` 是派生索引、不承载已提交帧；若要求字面覆盖，应改为 checkpoint 路线 ②）。③ "AUD-08 设备库复跑使用同一时刻三件快照并落盘" → **未做**（本批无设备轨产物）。
- **裁决**：KD-81 的原始缺陷（同主库 + 不同 WAL 同身份）在工具侧**已消灭**且有回归用例；但关闭条件 ②的字面 `-shm` 项与 ③的设备复跑仍未满足 → **KD-81 保持 open**，可写"工具侧修复 + 36 项回归绿；设备侧复跑与 `-shm` 口径待裁"。不得写 closed。

**Progress（2026-10-29 批次 34 复核修复轮 + 记录员复核；只读 + pytest 实跑；状态保持 open）.** 上块"36 项回归"是修复轮前时点，**现树为 37**：T5b `test_export_rejects_wal_change_inside_transaction` 已补在 `tools/tests/test_split_gate.py:829`（事务内写入 WAL 后，后比对仍必须 `exit 2`），记录员实跑 `python -m pytest tools/tests/test_split_gate.py -q` → **37 passed in 2.87s**。实现锚点复读：`export_split_eval.py:78` `IDENTITY_KIND="inputs-v1"`、`:101-135` `_input_manifest`（db+wal 进摘要，`-shm` 只观察、mtime 不进摘要）、`:213` 导出前清单、`:222` 显式 `BEGIN`、`:290-294` 导出后比对（变化即 `DataError("快照一致性…")`，先于写盘）、`:300` `payload.sha256`、`:358-359` stdout 如实打印三种身份。**与关闭条件的差异不变**：② 字面要求 `-shm` 覆盖，实现按 R2 C3 显式排除（只读导出会重建/改写 `-shm`；纳入即自指），需主会话裁定是否接受"内容身份不含派生 shm"；③ AUD-08 设备库复跑本批**未做**（设备轨 15 套件不含 AUD-08）。**新增登记的口径上限**：身份=导出前后两次字节清单，无锁、不绑定 SQLite 读事务；ABA 或撕裂 WAL 时"身份≠事务实际读到的快照"未写进 README/KD（R2 U5 只覆盖取副本撕裂）。→ **KD-81 保持 open**；可写"工具侧修复 + 37 项回归绿（含 T5b）；设备复跑与 `-shm` 口径待裁"。记录见 `docs/research/2026-10-29-batch-34-record.md` §3.5/§5.2 #I。

**裁决：KD-81 关闭（2026-10-30 批次 35；主会话裁定落地；本块零 Gradle/零设备/零 git 写）.** 上块"保持 open、`-shm` 口径待裁、设备复跑未做"是批次 34 复核修复轮时点；`-shm` 口径已由主会话裁定定谳（计划 `.zcode/plans/plan-2026-10-30-batch-35-dual-backend-closeout.md` §0：按"db+wal 进身份、`-shm` 排除"裁定为可接受——`-shm` 会被只读导出改写，进身份反而制造假漂移），本块落裁定与回链：

- **裁定内容**：快照**内容身份 = db + `-wal`**（`identityKind="inputs-v1"`，`export_split_eval.py:78`；身份 = `sha256(canon({"db": …, "wal": …}))`，`:107-135`）；**`-shm` 显式排除且按裁定接受为最终口径**——`-shm` 是派生的共享内存索引、不承载已提交帧（批次 34 块已登记的口径；工具 docstring `:101-102`：只读导出会重建/改写它），纳入身份会把导出自身的读副作用当成漂移（假漂移/自指），同一快照两次导出反而不再逐字节一致（与 `:701` 用例钉住的性质直接冲突）。关闭条件 ② 的"`-shm` 字面覆盖"自本裁定起**改判为已满足（按排除口径）**，不再记"字面未满足"。
- **T5b 回链（已落地）**：`test_export_rejects_wal_change_inside_transaction`（`tools/tests/test_split_gate.py:829`，docstring 自述：读事务**内**发生的已提交写入，后比对仍必须 `exit 2` 且校验先于写盘）。本会话实跑 `python -m pytest tools/tests/test_split_gate.py -q` → **37 passed in 2.44s**（EXIT=0），含 KD-81 直接用例六件：`:669` 同主库不同 WAL 必得不同身份、`:701` 同快照两次导出逐字节一致、`:721` 无 WAL 如实记录、`:735` 自造 0 B WAL 不判漂移、`:765` 导出期间输入变化拒绝、`:789` 读事务钉住一个快照。
- **关闭条件逐条**：① "同主库 + 不同 WAL 必得不同身份/可区分导出" → **满足**（`:669` 用例 + `:107-135` 身份公式）；② "manifest 覆盖 `-wal`/`-shm`（或 checkpoint 完成证据）" → **按裁定满足**（`-wal` 进身份、`-shm` 按排除口径接受；checkpoint 路线 ② 不采用，无须）；③ "AUD-08 设备库复跑同一时刻三件快照并落盘" → **按裁定不再作为关闭前置**：本缺陷是工具身份口径缺陷，工具侧已消灭并有回归兜底；未来任何 AUD-08 复跑导出都自动携带 db+wal 内容身份与导出前后比对（`:213` 导出前清单、`:290-298` 导出后变化即 `DataError` 且先于写盘、`:234` 显式 `BEGIN`、`:300/:309` `payload.sha256`），"三件同一时刻"的取库纪律保留在 `tools/split_golden/README.md`。批次 31 快照（WAL 0 B、三件非同时刻）的度量局限已在 Symptom/Impact 登记，不因关闭改写。
- **残留登记（不阻塞关闭，不得读成已覆盖）**：身份 = 导出前后两次字节清单，无锁、不绑定 SQLite 读事务；ABA/撕裂 WAL 时"身份 ≠ 事务实际读到的快照"的口径上限已在批次 34 块登记，是否写进 `tools/split_golden/README.md` 属文档改进。`docs/current/visual-generation-contract.md:208` 的快照身份边界句停在批次 32 T6 时点（"`-wal`/`-shm` 不含"），与现树口径不符——该文件属 docs/current 热区，本轨不改，留契约轨回改。
- **结论**：**KD-81 关闭（closed 2026-10-30 批次 35）**。可写"工具侧修复（db+wal 进身份、`-shm` 排除按裁定）+ 37 项回归绿（含 T5b）"；不得再写"`-shm` 口径未决 / KD-81 open"；AUD-08 度量复跑可作为独立工作项另行排期，不是本缺陷的关闭前置。记录见 `docs/research/2026-10-29-batch-34-record.md` §3.5/§5.2 #I 与本块。

## KD-82 (open（known limitation，不排期；2026-10-30 批次 35 计划任务 15 半登记；低）) · GENERATE_FIGURE 工具 schema 的 `maxLength` 按 Unicode 码点计（JSON Schema 语义），而 `imagePrompt`/`program` 的本地门按 UTF-8 字节计：非 ASCII 下广告预算可达执行门的 3-4×

**Symptom（读码 + 数值演示，2026-10-30）.** 工具 schema 广告面有两处把**字节上限常量**塞进按**码点**计数的 `maxLength` 关键字：`OpenAiModelProtocol.kt:552` 给 `imagePrompt` 广告 `maxLength = FigureGenerationPolicy.MAX_IMAGE_PROMPT_UTF8_BYTES`（`FigureGenerationPolicy.kt:32`，4096）；`OpenAiModelProtocol.kt:583` 给 `program` 广告 `maxLength = MAX_PROGRAM_UTF8_BYTES`（`FigureGenerationPolicy.kt:33`，65536）。JSON Schema 的 `maxLength` 以 Unicode 码点计数；本地执行门以 UTF-8 字节计数（`figureUsageUtf8Bytes` = `toByteArray(UTF_8).size`，`FigureGeneration.kt:609`；门在 `FigureGenerationPolicy.kt:274` 与 `:222`）。数值演示（本批 python 实跑）：1500 个汉字的 imagePrompt → 码点 1500 ≤ 4096（schema 合法）而 UTF-8 4500 字节 > 4096（本地门拒绝，"imagePrompt 超过 4096 字节预算" `:277`）；码点语义下 schema 合法串的字节上限最坏 = 4×4096 = 16384（CJK 常态 3×）。同族小边界：`description`（`OpenAiModelProtocol.kt:619`，200）与 `payloadMarkdown`（`:652`，600）的本地门按 Kotlin `.length`（UTF-16 单元，`TutorToolLoop.kt:343` 与 `:281`）计，与码点语义只在星面字符（1 码点 = 2 UTF-16 单元）上有 2× 差，中文正文不受影响。方向性（UTF-8 字节数 ≥ UTF-16 单元数 ≥ 码点数，恒成立）："本地门通过 ⇒ schema 合法"恒真；不成立的只有反方向"schema 合法 ⇒ 本地通过"。

**Impact.** 模型按 schema 写满非 ASCII 预算时，调用被本地门 fail-closed 拒绝——浪费一轮工具调用、错误信息是"超过 N 字节预算"。**没有安全或正确性漏洞**：本地字节门是唯一执行点，schema 只是给模型的广告；被本地接受的内容不会与广告矛盾（方向性见上）。批次 35 计划 §0 把它列为【低】，与本读码一致。

**为何登记为已知限制、本批不改值（T4 二选一的裁定与理由）.** ① 广告冻结：`maxLength` 值进工具 schema 广告面，共享纪律"能力快照 / prompt / 广告文本零改动直到全门绿"冻结它；② 不存在与字节门**一致**的码点表达值：要严格保证"schema 合法 ⇒ 本地通过"只能取 `maxLength = ⌊字节上限/4⌋`（4096→1024、65536→16384），把广告预算砍 4×，是产品取舍不是机械修正；按 CJK/3 取值仍不严格（4 字节星面字符存在）；③ 本地门零改动：本条不放宽 `FigureGenerationPolicy.kt:274`/`:222` 任何断言；④ 同一广告面正在排队 A-04（prompt 按真实能力注入 schema），届时按同一批裁定落值，避免两次广告面改动。

**Fix direction（广告解冻批二选一）.** ① schema `maxLength` 改为 `⌊对应字节上限/4⌋`（严格保证 schema 合法串必过本地门）；或 ② 保留现值、在字段 `description` 文本补"该上限按 UTF-8 字节执行，非 ASCII 实际预算约为其 1/3"（仍是广告面改动，须与 ① 同批裁定）。两者都必须带 JVM 用例：构造"码点合法但字节超限"的输入，断言本地门仍拒绝且错误信息带字节计数。

**关闭条件.** 广告解冻批（A-04 或同等）裁定 ①/②、落码并过 JVM 用例；在此之前本条按 known limitation 保留，**任何文档/证据不得把现 schema 写成"与本地门同一口径"**。本批零代码改动、零广告面改动。
