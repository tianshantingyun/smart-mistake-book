# Pinned CommonMark presentation adapter

This module replaces `com.halilibo.compose-richtext:richtext-commonmark:0.17.0` with its
published Android source, pinned by `upstream-manifest.json`. The parser, AST, inline,
list, quote and table rendering remain the same. Existing dependencies retain their
published versions. There is one CommonMark implementation in the application.

Changes:

1. Android expect/actual declarations are joined for this Android-only module.
2. `Markdown` gains an optional `codeBlockContent` composable slot. It receives the
   original literal and language and also applies inside lists and blockquotes.
3. The default path remains upstream's original code-block rendering; the application
   opts into its Claude-derived native code block after its existing safety gate.

No internal visibility suppression, reflection, second parser, alternate UI framework,
HTML permissions or remote-content capability is introduced. The existing upstream
HTML/image implementations are unchanged; the application continues to reject such
input before rendering.

Upstream: https://github.com/halilozercan/compose-richtext/tree/v0.17.0
License: Apache-2.0, see LICENSE. Upstream archive snapshots and the exact original
files are in the task's `.jez/artifacts/open-claude-ui-coverage-2026-10-24/upstream/`.
