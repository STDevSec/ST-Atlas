# 0004 — Tiptap JSON documents with attachment references

**Decision.** Report content is stored as Tiptap/ProseMirror JSON (`jsonb`),
never as HTML. Images are `image` nodes whose `attachmentId` points to an
`attachment` row; the browser loads them through the authorized attachment
endpoint. Pasted/dropped images are uploaded first, then inserted. The PDF
renderer converts the JSON to HTML with an allow-list of node and mark types.

**Why.** JSON is structured (findings and variables can be real nodes),
cannot carry script by construction, and keeps documents small (no Base64).

**Consequences.** The backend must keep its renderer in sync with the editor
extensions enabled in the frontend; unknown node types are dropped and logged.
