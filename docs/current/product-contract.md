# Product Contract

The product turns a mistake into a local learning asset that can be revised,
searched, explained, re-practiced, scheduled, and migrated.

## Non-negotiable facts

- Room is the sole source of truth for learning facts.
- All question images go through the canonical asset pipeline and are recorded
  in Room before use.
- Tutor lobby is text-only. A photo/selected image starts the Capture semantic
  pipeline, not a fake image-capable lobby.
- Models propose typed tools/actions; the host enforces TutorPermissionPolicy.
  NOTEBOOK_WRITE and local actions use ASK; MASTERY_UPDATE and ADVISORY_WRITE
  use AUTO_VISIBLE with host validation and visible results. MasteryWriteGate
  controls evidence admission; models cannot directly write the database or
  choose arbitrary mastery values. This does not authorize silent saves or navigation.
- A logical external operation has one stable identity and at most
  `ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES` provider dispatches across
  retries and process recreation (currently 6: five tool rounds plus one final
  answer). Local execution never consumes this budget.
- Process recreation requires an explicit user "continue" before any external
  send resumes.
- No timestamp may be used to bypass idempotency conflicts.
- No release build exposes debug activities or broad FileProvider roots.
- No silent in-memory truncation of knowledge points or facet options.
- Backup is explicit, validated, restorable, and never contains provider keys
  or leases.

## Empty and error language

Users see business impact, not internal pipeline terms. Error copy explains
what happened, whether data is safe, and the next action.

## Planned teaching figures (4E)

JS and IMAGE_MODEL are two methods of one figure-generation capability, governed by the [shared prompt policy](figure-generation-prompt-policy.md). All transcription, cleanup, redraw and explanation entry points use the same decision/coordinator; purpose never hardcodes method. Models may revise or switch methods within existing authority and budget, without an extra confirmation; clients never silently substitute. Both methods count as generation, with remote image calls/cost recorded separately. Forms remain native; no mastery from browsing or generation on history-open. Unified integration is not shipped yet.

## Planned product-completion corrections (2026-10-19)

The [readiness design](../superpowers/specs/2026-10-19-product-readiness-design.md) adds accessible saved-question revisions, answer-key provenance/correction/retraction/replay, native export format selection and model-independent self-rating. These are planned changes, not shipped claims. Local answer comparison does not verify a model-generated answer key; disputed or unverified keys initially retain session feedback without new numeric mastery writes. Preserve raw answers and immutable history.

Stage 5 development and isolated testing may proceed after its contracts are ready; new real-learning writes remain gated by all 3D requirements, with 3E additionally required for 5b. Empty attribution is not a safe isolation mechanism. Native editing/export/backup fixes do not acquire unrelated model or 3D dependencies. Existing three-rating and exposure decisions remain in force.
