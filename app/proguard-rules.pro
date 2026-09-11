# Phase 42.2 — R8 rules for the release variant. THE LAW of this file:
#   1. One SECTION per library that needs rules.
#   2. Each -keep gets a comment naming the failure it prevents
#      ("crashes with X" / "loses Y" / "network layer returns error Z").
#   3. A keep is added ONLY after a device round or CI run reproduces the
#      failure without it — no cargo-cult keeps.
#   4. -keepattributes SourceFile,LineNumberTable is non-negotiable: a
#      crash record the owner cannot read is the worst third-party flake.
#   5. -renamesourcefileattribute SourceFile is permitted (one source of
#      truth for every frame); absented line numbers are not.
# ProguardKeepsDocumentedTest enforces 2 and 4 mechanically.
#
# Current state (2026-09-11): first R8 attempt ships with NO library
# keeps. Compose is R8-aware through its own artifacts; org.json models
# are built by hand from literal keys (no reflection); org.eclipse.jgit
# ships its own consumer rules. Anything this file needs beyond lines
# appears here only after being proven, with the failure named.

## Section: stack traces (non-negotiable)
# Prevents "class a.b cannot be cast" dead ends: without SourceFile and
# LineNumberTable every pasted crash log from a release build is
# unreadable, which is exactly the support cost this file exists to beat.
-keepattributes SourceFile,LineNumberTable

## Section: sora-editor language-textmate (tm4e)
# Prevents "ERROR: R8: Missing class org.eclipse.jdt.annotation.NonNullByDefault"
# (measured in CI run 34577896124 — the first R8 run): Phase 29.1 EXCLUDES
# the org.eclipse.jdt.annotation artifact from the textmate dependency
# because those types are compile-time-only nullness metadata; tm4e's
# plist parser still REFERENCES NonNullByDefault, and R8's missing-class
# analysis promotes that reference to a hard error. The class is never
# loaded at runtime (annotations are invisible to Android's runtime), so
# warning suppression is the correct fix — NOT shipping the artifact back.
-dontwarn org.eclipse.jdt.annotation.**

