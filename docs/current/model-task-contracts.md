# Model Task Contracts

Every `ModelTaskKind` has one machine-checkable contract:

- exact input fields
- exact disclosure set and prohibited disclosures
- asset policy (required / forbidden / exact grants)
- prompt policy version
- output schema and parser
- capability requirements
- max provider dispatches

Contract parity tests must lock:

- input JSON contains only allowed fields
- provider request body contains or omits images exactly as authorized
- wire keys, prompt policy, schema, and parser agree
- invalid output fails closed and never writes a business success
- optional model fields never trigger deletion
- model output cannot carry arbitrary routing, SQL, file paths, or URLs

`TUTOR_LOBBY` forbids images. Image tutoring routes through Capture before a
tutor plan/respond task may reference authorized canonical assets.

## Streaming (SSE) contract

Tutor text tasks (`TUTOR_PLAN`, `TUTOR_LOBBY`, `TUTOR_RESPOND`) may stream
their reply over Server-Sent Events. Non-streaming tasks (capture
assess/parse, problem organization) always return a
single JSON completion and never request `stream=true`.

- A streaming request carries `"stream":true` and the transport reads
  `text/event-stream`, concatenating the delta `content` fields back into a
  chat-completion envelope.
- The gateway replays the deltas as `ModelGatewayEvent.Progress` frames
  (throttled every 8 SSE frames) so the persisted task enters `STREAMING` and
  the tutor UI renders the reply incrementally, then emits the terminal
  `Completed` with the fully parsed output.
- Streaming is only advertised (`supportsStreaming`) for a provider that
  passed the structured-output capability probe; an unverified provider gets a
  conservative non-streaming request.
- The terminal `Completed` always carries the complete output even when the
  reply streamed, so every consumer that ignores `Progress` still works.

## Planned GENERATE_FIGURE extension (4E)

All figure-producing tasks use the shared FigureGenerationProposal with explicit JS or IMAGE_MODEL method. Existing TUTOR_PLAN/TUTOR_RESPOND tool arguments, CAPTURE_PARSE and IMAGE_PIPELINE_CLASSIFY carry the same policy; purpose never selects a backend. JS source is a typed program field; IMAGE_MODEL carries imageOperation and imagePrompt. No TUTOR_VISUAL_GENERATE/REVIEW tasks are restored. Native function schema and compatibility parsing must agree, and a new-policy GENERATE missing method must fail instead of silently choosing images. TutorVisualContext is bounded to current program/state/diagnostics with old-schema fingerprint compatibility. No partial streaming source executes. At the last reserved tutor dispatch, both tool routes are suppressed for a final answer. Details/status: [visual contract](visual-generation-contract.md).

Prerequisites: reconstruct native SSE tool-call arguments and test the other protocols through Route B. Every source-dependent entry freezes VisualSourceBasis and prepares only missing evidence; authorized images actually sent are reflected in grants/fingerprints. Present tutor requests lack the original question image. Existing sufficient structured evidence does not require redundant image transmission, and image-edit input can go directly to the selected execution model. Text planning/repairs and image generation/edit execution share the durable six-dispatch ledger; tutor operations reserve one final answer, standalone cleanup/transcription with a local terminal result does not. Switching methods keeps operation identity and remaining budget. Capability advertising, parsing and execution must agree.

## Unified figure policy (latest user decision, 2026-10-18)

Use [one prompt policy](figure-generation-prompt-policy.md) for tutor tools, CAPTURE_PARSE and IMAGE_PIPELINE_CLASSIFY proposals. Purpose and method are orthogonal; allow JS/IMAGE_MODEL where capabilities satisfy the goal. IMAGE_MODEL calls join the same durable remote dispatch ledger as planning/repair (max6; tutor reserves a final response, standalone local completion does not). A method switch keeps operation identity and budgets. Do not add visual model task kinds, duplicate classifiers when a proposal exists, or advertise an unimplemented backend. Existing native/RouteB/SSE, source grants and fingerprint requirements remain.
