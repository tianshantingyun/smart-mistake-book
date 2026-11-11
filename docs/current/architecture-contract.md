# Architecture Contract

## Module dependency direction

```text
feature:* -> core:domain + core:model + core:ui
core:data -> core:domain + core:model + core:database
app       -> feature:* + core:data
```

Prohibited:

- `feature:*` depending on concrete `core:data` classes
- Composable calling DAOs or managing database/files/provider workflows
- model parser writing database facts
- persistent egress manifest acting as cross-process send permission

## State rules

- Complex pages use one immutable `UiState` plus one `Action` set.
- `SavedStateHandle` stores small IDs and transient UI state only; business
  state is rebuilt from Room.
- Model results are applied with CAS: expected workflow/session/basis must
  match the current persisted state or the result is stale.
- All sends, saves, attempts, and review advances use explicit command
  idempotency and single transactions where the plan requires them.

## Asset pipeline

Camera, photo picker, PDF page, and tutor attachments all use:

```text
URI -> safe read -> format/size/pixel budget -> orientation fix ->
metadata strip -> content hash -> private vault -> Room SourceAsset
```

There is exactly one tutor/session conversation fact model in production.

## Planned visual runtime (4E, not implemented)

See [visual-generation-contract](visual-generation-contract.md). `core:model` owns pure visual DTOs/validation/compiler; planned `core:visual` owns shared Android content layout and Canvas replay; planned `core:visual-js` owns the isolated execution process and bounded IPC. UI/export consume immutable RenderScene. Feature modules use domain ports; no DAO/provider workflow enters Compose or the worker. Application and actual auto-start paths must not initialize host services in the isolated UID. This is a target boundary, not a claim that the new modules exist.

Planned dependency wiring keeps the existing data boundary:

```text
core:domain    -> core:model (VisualExecutionEngine/VisualProgramRunner/VisualRepository ports)
core:visual    -> core:model (native content layout and replay, no JS client)
core:visual-js -> core:model (worker/client and bounded IPC, no Room/data/UI)
core:data      -> core:domain + core:model + core:database (persistence, identity, CAS)
core:ui/export -> core:visual + core:model (replay only; no QuickJS execution dependency)
app            -> feature:* + core:data + core:visual + core:visual-js
```

`app` composes a planned AndroidVisualExecutionEngine implementing the domain port and owning prepare/measure/render orchestration. `core:data` receives that port; it does not import native layout, service, or AIDL classes. This adds a concrete platform adapter rather than a dependency cycle or an export-to-runtime dependency.

## Unified figure orchestration

All figure-producing use cases depend on core:domain.FigureGenerationCoordinator. A core:data implementation applies one policy/proposal/state/quality/ledger/persistence flow, delegating execution to the injected JS engine or existing image channel. Entry adapters (tutor, transcription, cleanup, redraw) bind context; they must not select a backend by purpose. Image execution joins the existing remote-dispatch ledger. UI/export consume typed artifacts without triggering new generation. The JS-only dependency wiring above remains the JS implementation detail.
