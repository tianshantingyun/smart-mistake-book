# Current Contract Entry

These documents are the authoritative implementation contracts for the
smart-mistake-book Android client. Historical planning and status documents in
`docs/` remain for provenance but do not override these contracts.

- `product-contract.md` - product facts, experience rules, and prohibited shortcuts
- `architecture-contract.md` - module boundaries, data ownership, and state machine rules
- `model-task-contracts.md` - model task input/disclosure/output parity
- `data-schema.md` - Room schema, migration, and backup rules
- `release-gates.md` - closed beta and public release gates

- `visual-generation-contract.md` - unified figure-generation contract, JS runtime details, implementation status, isolation, model budget, persistence and export gates (4E; not shipped yet)

- `figure-generation-prompt-policy.md` - current unified generation choice rules and reusable prompt; JS and IMAGE_MODEL are two methods across every figure-producing entry point (integration pending)

Planning handoff (not a shipped-feature claim): [downstream figure wiring and remaining work](../../.zcode/plans/plan-2026-10-18-followup-figure-wiring.md) assigns the shared WG tasks to later stages; [MASTER LINE](../REFACTOR-MASTER-LINE.md) remains the status and scheduling authority.

Latest product-completion planning: [research](../research/2026-10-19-product-readiness-research.md), [design](../superpowers/specs/2026-10-19-product-readiness-design.md), and [PRD execution tasks](../superpowers/plans/2026-10-19-product-readiness-followup-plan.md). These revise future work and development/enablement boundaries; they are not completion evidence.

UI planning authority for the 2026-10-19 redesign: [observed usability and visual issues](../research/2026-10-19-ui-ux-audit.md), [page, state and interaction design](../superpowers/specs/2026-10-19-ui-ux-redesign-design.md), and [UI-00–11 execution tasks](../superpowers/plans/2026-10-19-ui-ux-redesign-plan.md). Five functional areas map to four bottom destinations; this does not authorize a fifth navigation button. These documents extend PRD/WG work without claiming the redesign shipped.
