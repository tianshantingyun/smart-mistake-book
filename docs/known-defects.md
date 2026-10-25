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

## KD-64 (open, 2026-10-25 登记（批次 20 设备轨 T10；中）) · NewIntroductionPolicy 全超尺寸死锁分支 granted slice 可为负 → IllegalArgumentException，3E 排计划在「全部候选时长 > 当日时间预算」配置下崩溃

**Symptom（设备实跑 + 源码推导）.** 批次 20 T10 设备用例 `StudyReviewPlanner3EScanInstrumentedTest`（真 Room 种子库直跑 `StudyReviewPlannerService.createReviewPlan`）2/3 红：`java.lang.IllegalArgumentException: Granted slice must not be negative`，栈 `NewIntroductionPolicy$IntakeDecision.<init>(NewIntroductionPolicy.kt:68)` ← `decide(:151)` ← `StudyReviewPlannerService.createReviewPlan(:435)`。机制（基线 §8.3 读码证明）：`NewIntroductionPolicy.kt:68` `require(grantedSliceSeconds >= 0)`；`:154` 附近 `grantedSliceSeconds = effectiveSlice - remaining`；全超尺寸死锁分支（`:122-129`）中 sliceBudget = topDuration.coerceAtMost(timeBudgetSeconds) 而 capBudget = topDuration 无上界（`:52-53` 仅 require>0）——全部候选 `estimatedDurationSeconds > timeBudgetSeconds` 时全数 deferred、remaining 停在 timeBudget > effectiveSlice → granted < 0。具体触发例：timeBudget=300、单候选 duration=600 → granted = effectiveSlice−300 < 0。JVM 对照 `StudyReviewPlannerExamWindowTest` 6/0/0 绿 = 该形状 JVM 未覆盖。两文件均 git 干净（HEAD 态），非本批引入。

**Impact.** 中（合法配置「单候选时长超过当日时间预算」下计划创建直接崩溃；设备已复现 2 例；JVM 门未覆盖该分支，回归面前裸）。

**Fix direction.** 域层修复（不在批次 20 设备轨授权内，待实施轨）：全超尺寸死锁分支对 granted 做 coerceAtLeast(0) 或在死锁分支给 top 候选保底切片并如实记 deferred；时长上界（`:52-53`）按产品口径另裁。补 JVM 用例覆盖「全候选超尺寸」形状后再清。

**Reopen condition.** 设备/JVM 任一「全候选 estimatedDurationSeconds > timeBudgetSeconds」用例红即按本条处置；修复后 T10 两红例转绿 + 新 JVM 形状用例全绿关闭。

## KD-65 (open, 2026-10-25 登记（批次 20 复核发现 #10；低）) · OpenAiModelResponseParsers KDoc 与白名单不符：「purpose」实际在 FIGURE_GENERATION_WIRE_KEYS 内且另有值校验

**Symptom（读码对拍）.** `OpenAiModelResponseParsers.kt` 的 `toCapturedFigureProposal` KDoc 称 policyVersion 与 purpose「故意不在白名单里」，但 `FIGURE_GENERATION_WIRE_KEYS` 实际包含 `"purpose"`（decision/purpose/method/reason/fidelity/constraints/program/imageOperation/imagePrompt），且代码另做 `wirePurpose != SOURCE_TRANSCRIPTION` 即抛的值校验——行为仍是 fail-closed（错误 purpose 整轮拒），只是注释表述与集合成员不符。

**Impact.** low（纯文档性；行为正确且 fail-closed，但误导后续维护者对白名单边界的判断）。

**Fix direction.** 改 KDoc 使其与集合成员一致（说明 purpose 在白名单内、另有取值校验拒绝模型回填）；不改行为。

**Reopen condition.** 若后续改动把 purpose 值校验放松或白名单语义变化时按本条重审。
