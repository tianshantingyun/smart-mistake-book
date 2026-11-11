# Data Schema and Migration

The current Room schema is versioned in `core/database/schemas/`. Every schema
version from 1 to current must exist as exported JSON.

Rules:

- Never use destructive migration for user data.
- Every historical version has a migration chain test to current.
- Critical commands use single transactions and idempotent receipts.
- Conversation/message ordinals and logical operation identities are unique.
- Asset cross references are indexed in both directions.
- Deleting a tutor conversation never deletes a saved problem or its assets.
- Backup format version is independent of Room schema version.

When adding a schema change:

1. bump `STUDY_DATABASE_VERSION`
2. add a non-destructive `Migration`
3. let Room export the new schema JSON
4. add migration/instrumented tests
5. run `git diff --exit-code -- core/database/schemas` after a clean build

## Planned visual records (4E; not migrated)

Research initially observed Room 66; final source check is Room 67, request schema 17. Add program/snapshot/message-visual/problem-revision-visual tables non-destructively; target 68/18 only if those versions remain free at implementation time. Do not write JS into canonical pixel records. LOCAL_VISUAL_PREVIEW assets need real snapshot references in GC and backup enumeration. Preserve old raster assets and request fingerprints; unknown visual versions fall back to stored snapshots/text. Full fields and migration tests: [visual contract](visual-generation-contract.md).

Programs are immutable; host-owned role and source-verification state separate question diagrams from tutor explanations. Animation frames stay in memory; checkpoints have CAS, retention quotas, staging cleanup and transactional message/reference commits. Pinned user facts are never evicted as cache. Unknown execution and render versions have separate fallback rules; restored DTOs are revalidated.

## Unified generation operation records

Add shared figure_generation_operation/attempt records alongside the four JS tables; six planned new tables total. Legacy clean_redraw_task gains a non-destructive operation link. Stable operation identity excludes method; attempt/artifact fingerprints include it. All methods share state, quality, source basis, references and generation counts; local preview sourceType does not define business billing. Typed FigureArtifactRef keeps raster IDs distinct from JS IDs. The same target next-free migration must cover ledger/references/backup/GC; no migration has been implemented by this document change.
