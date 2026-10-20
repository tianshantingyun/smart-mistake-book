# Release Gates

## Closed beta

- one production tutor route and one session fact model
- text tutor, photo tutor, and saved-problem tutor entry close the loop
- authorized images really reach the provider
- no vision support means fail before network
- ASK actions never execute without their required user action; AUTO_VISIBLE evidence
  writes pass host policy/MasteryWriteGate and remain observable, with no arbitrary model mastery writes
- process recreation never auto-sends
- each logical operation has at most `ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES`
  provider dispatches (currently 6: five tool rounds plus one final answer),
  and local execution consumes none of them
- capture draft, retake, failure, and kill recovery work
- library uses database queries/paging and no silent facet truncation
- full Room migration matrix passes
- backup create/validate/restore round trip passes
- release manifest has no debug entry or broad provider path
- API keys never appear in logs, backup, prompt, or Room
- no dead buttons, fake numbers, or internal jargon in any shipped root page
- core TalkBack, large font, and 48dp checks pass
- both flavors assemble/lint/unit, and core device suite passes
- privacy policy, in-app disclosure, and Data safety draft exist

## Public beta additions

- real-provider cross-subject protocol and teaching quality thresholds
- 1k/10k/50k library performance gates
- Macrobenchmark and Baseline Profile
- closed-test upgrade/backup restore without blocking incidents
- crash/ANR/jank monitoring
- release signing, version, rollback, and staged rollout rehearsal
- website download/usage/privacy pages match the shipped version

## Planned 4E enablement gate

Enable capabilities separately under the [visual contract](visual-generation-contract.md): 4E.1 unifies every figure entry point, source-evidence handling, shared coordinator/ledger and static backends/PDF; reuse the existing image backend first, then advertise the JS backend after its gates. 4E.2 adds local interactions/forms and 4E.3 extends complex-source/fidelity coverage. Purpose does not determine method or phase. Each advertised capability must pass applicable gates: fixed-source runtime build, API 23/arm64/16 KB/R8, isolated startup/hard termination, bounded IPC/scene/text/pixels, both tool routes and six-dispatch convergence, migration/backup/GC, native controls/TalkBack/large fonts, screen/PDF snapshot parity and real-provider quality. API 34 research probes do not satisfy the release matrix. Existing beta gates continue to apply.

Unified-generation enablement additionally requires one shared prompt/proposal across all entry points, both methods available by actual capabilities, same-purpose use of either method, budgeted bidirectional method changes, image-call ledger integration and method-neutral generation counts. Purpose-specific fixed routing is prohibited; this new gate is a target, not current behavior.

Wiring coverage is tracked by [WG-01–10 in the 4E plan](../superpowers/plans/2026-10-18-flexible-js-drawing-implementation.md#10-全入口接线任务与后续阶段承接). 4E.1 requires the applicable WG-01–08/10 paths: transcription, durable cleanup, tutor tools, legacy read-only image resolution with explicit regeneration, save/detail references, PDF/DOCX/print/share consumption and restore/ledger state. Verify real entry-to-artifact calls and migration of the three existing direct image-channel callers; prompt/interface presence alone is insufficient. WG-09 belongs to stages 5/5B under their existing prerequisites, and gates only their figure-producing branches. DOCX must embed an actual snapshot/image; history opening and export must dispatch no model calls.

## Planned product-completion gates (2026-10-19)

See [PRD-01–11](../superpowers/plans/2026-10-19-product-readiness-followup-plan.md). Before enabling expanded real learning, verify saved-question revision/CAS, answer-key provenance and correction/retraction, effective-evidence replay equality, three-rating undo with exposure preserved, and the existing 3D gates. Development/isolated tests do not satisfy enablement; 5b additionally consumes the 3E queue gate.

Native PDF/DOCX selection and delivery, manual entry, saved-content editing, and backup recovery must work without a model. Test first-use/empty/no-due states, unsupported-action declaration parity, >=365 MB backup round trip and concurrent writes, and draft recovery after reproducible delayed/failed writes. Opening a share chooser is not proof of delivered content.

Figure enablement includes [design section 6](../superpowers/specs/2026-10-19-product-readiness-design.md#6-统一生图的质量与启用): a frozen 120-task evaluation, first-pass >=90%, within-budget >=95%, each group >=90%, zero accepted critical semantic errors, and separate latency/cost evidence. These are proposed engineering targets, not achieved measurements. Do not count text fallback as requested-figure success or model self-review as independent truth. Continue all existing isolation, API23/arm64/16KB/R8 and WG wiring gates.

## Planned UI usability and visual gates (2026-10-19)

Follow [UI-00–11](../superpowers/plans/2026-10-19-ui-ux-redesign-plan.md) and the [UI design](../superpowers/specs/2026-10-19-ui-ux-redesign-design.md). Address UX-01–14 with matched-content before/after evidence. Verify discoverable first-use actions, list-first library layout, concise assistant empty state, native export selection, correct back destinations, preserved drafts, and truthful task/correction/recovery receipts.

Check normal/empty/loading/error/unavailable/recovery/conflict states, 320/360/412/600/840dp windows, fontScale 1.0/1.3/2.0, keyboard/system insets and TalkBack. Interactive touch regions must be at least 48dp and not overlap; normal text must meet 4.5:1 contrast on its actual background. No shrinking text to hide overflow, false progress, unavailable-action advertising, or regeneration during read/export. Existing 3D/PRD/WG business gates continue to apply; visual previews alone do not satisfy them.
