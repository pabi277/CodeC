# Phase 9.1 — Editor Foundation
Status: planned · [client-only] · depends 8 (folder tree enables multi-file context)
Context: EditorScreen has dead Undo/Redo/Format/Find (showComingSoon 40% alpha); single BasicTextField; no bracket match / squiggles / line-col / cursor highlight.
D1: Native Compose; undo/redo via command stack; find/replace with regex option; format button calls external formatter (clang-format if installed, else indent); bracket match via text scanning; error squiggles parsed from compiler output (line/col); line/col shown in status bar; cursor line highlighted.
Sources: EditorScreen.kt (dead buttons), CSyntaxVisualTransformation (extend per-language in 12), compiler error parsing from terminal output.
Exit device: open main.c; type; undo; redo; find "main"; format; brackets highlight; compile error at line 5 → red underline; tap line 5 → jumps; PASS.
Evidence: §5.1 host (stack logic, find regex), §5.2 CI, §5.3 device (transcript with all sub-checks).
Steps: read EditorScreen; implement stack; add Find UI; integrate format; add bracket scan; parse errors; device verify.
