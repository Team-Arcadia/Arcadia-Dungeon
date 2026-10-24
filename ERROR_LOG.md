# Error log

Every non-trivial error hit while working on this mod, with its root cause and what
resolved it. Read it before touching an area a past error was recorded in.

## [2026-10-24 18:15] — A fresh clone could not build any target

**Context:** Rebuilding a published version in a separate working copy, to compare it against
2.0.0.

**Error:** `Error: Unable to access jarfile <target>/gradle/wrapper/gradle-wrapper.jar`, then,
once that was worked around, `package com.arcadia.lib.ui does not exist` and
`cannot find symbol: class TableDefinition`.

**Root cause:** The blanket `*.jar` rule in `.gitignore` swallowed two files that have to be
tracked. Both exceptions were written when the repository was a single flat project, so they only
matched at the root: `!gradle/wrapper/gradle-wrapper.jar` and `!libs/arcadia-lib-*.jar`. Moving to
the per-target layout put both files under `<target>/`, where neither pattern reaches, so the
Gradle wrapper and the library jar every target compiles against were silently left out of the
repository. Nothing showed it, because a working copy has the files on disk.

**Fix:** Match at any depth, `!**/gradle/wrapper/gradle-wrapper.jar` and
`!**/libs/arcadia-lib-*.jar`, and commit the files that were sitting there untracked. Checked by
cloning the repository from GitHub into an empty folder and compiling a target in it.

**Prevention:** When a repository changes layout, re-check every `.gitignore` exception: a pattern
without `**/` only ever matched the root. `git ls-files <path>` on each file a clone needs to build
answers the question in one command.
