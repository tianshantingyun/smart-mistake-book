# Architecture

The Android client is modular:

```text
app -> feature:* + core:data
feature:* -> core:domain + core:model + core:ui
core:data -> core:domain + core:model + core:database
```

Room is the single source of truth for learning facts. Composables receive
immutable `UiState` and send `Action` objects; ViewModels and domain use cases
own asynchronous work. Model providers never write database facts.

Every external model request is authorized by `ModelEgressPolicy` — one layer,
one semantic (D-K4). An agent-eligible round (capture assess/parse/classify,
tutor plan/respond) dispatches under the global model-agent consent with no
per-item manifest and must satisfy `agentConsentMatches` (consent on, configured
external provider supporting the kind, image-capable for image-bearing kinds);
every other round still needs an exact egress manifest — in production only the
tutor lobby and the confirmed-document organization routes carry one — and
passes the checks in `ModelEgressManifest` (subject / task scope / provider
identity and configuration / prompt-policy version / exact disclosure set) plus
the lobby asset scope before dispatch. Image bytes additionally leave only from
the canonical asset vault after a per-byte check
(`AndroidRestrictedModelAssetSource`), and the logical operation budget is
durable and shared across retries
(`ModelTaskRemoteDispatchPolicy.MAX_DISPATCHES = 6`).

See `docs/current/architecture-contract.md` for the full contract.
