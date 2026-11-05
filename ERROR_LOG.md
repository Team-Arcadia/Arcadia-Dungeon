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

## [2026-10-24 20:34] — Forge warned about the jar at every client launch on 1.20.1

**Context:** First launch of the forge-1.20.1 client with the whole stack deployed.

**Error:** A warning screen before the main menu:

```
File Mod File: ...\arcadia-dungeon-forge-1.20.1-2.0.0.jar failed to load a valid ResourcePackInfo
```

**Root cause:** The jar has no `pack.mcmeta`. Every other module ships one on this target;
this one never had it, because the mod is server-side and carries no assets at all. Forge 1.20.1
still registers each mod jar as a resource pack and complains when it cannot read the metadata,
so a mod with nothing to serve still needs to say so.

**Fix:** A minimal `pack.mcmeta` with `pack_format` 15, the same as the other 1.20.1 modules. The
1.21.1 and 26.1.2 targets do not need one and do not get one.

**Prevention:** A warning screen is not a log line a player will skip. Launch each target once
after a packaging change, and read what the game puts in front of the player before the menu.

## [2026-11-05 16:10] — A translated dungeon config still announced in French

**Context:** A server owner translated a dungeon's JSON to Spanish and started a test run.

**Error:** The dungeon's name came out in Spanish inside French sentences: `[Donjon] <player> lance
Mazmorra de ejemplo! [REJOINDRE (90s)]`, then `Recrutement: 60s restantes!` and the rest.

**Root cause:** Two separate faults. The chat lines were `Component.literal` strings in the code,
out of reach of any config. And `recruitmentMessage`, which looks like the setting for exactly that
line, was saved and editable but never read: `broadcastClickableJoin` built its own text. The
Dungeons panel had a third form of the same mistake: `Component.translatable` on a mod no client
holds, so nothing could resolve the key.

**Fix:** `Messages`, which resolves every player-facing line on the server from
`config/arcadia/dungeon/lang/<language>.json`. The five per-dungeon announcements became optional
overrides, and the recruitment announcement reads its field.

**Prevention:** In a server-side mod the client has no language file of it, so a translation key
is never an option and text must leave the server already resolved. A config field needs a reader:
search for its uses when adding one, not only for its assignment.
