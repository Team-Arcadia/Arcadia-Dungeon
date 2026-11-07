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

## [2026-11-07 01:34] — Waves cleared themselves and bosses could not be hit after a player died far from the entrance

**Context:** Players reported bosses that take no damage and waves that never appear, on a dungeon whose rooms run far from its entrance. Reproduced with a game test holding no chunk near the fight.

**Error:** No exception. Chat said wave N cleared with no mob in sight, then the next wave did the same. The boss reappeared with no bar update, hits did nothing, or killing it ended nothing.

**Root cause:** A run kept a Java reference to each mob and read isAlive() on it. When a chunk unloads the game calls setRemoved(UNLOADED_TO_CHUNK) on the object, so isAlive() answers false for a mob that is only on disk, and a reload gives a new object with the same UUID. A wave counted that as a kill. The spawn protection countdown ran only while isAlive() held, so the Invulnerable flag was saved to disk and came back for good.

**Fix:** TrackedMob follows a mob by UUID and tells unloaded from killed and removed, by removal reason. A run holds its mobs' chunks with a short ticket renewed every second, so a crash leaves nothing force loaded. Countdowns no longer depend on the entity, and the flag is set again on a mob read back from disk. Dungeon mobs read from disk with no run to answer to are removed.

**Prevention:** Never treat isAlive() or isRemoved() as death on a mob you keep a reference to: check getRemovalReason(), and follow long-lived entities by UUID. Anything that must survive an unload cannot live in a reference or in a countdown tied to one.

## [2026-11-07 01:34] — A mob the dungeon was waiting on never entered the world because the join event was cancelled

**Context:** Spawning wave mobs and boss summons with level.addFreshEntity, with another mod or a script allowed to cancel EntityJoinLevelEvent.

**Error:** No error. The wave never ended and the boss kept saying to kill its minions, with nothing to kill.

**Root cause:** addFreshEntity returns false when the join is cancelled and its result was ignored. The object was added to the alive list, and an entity never added is not removed and has health, so isAlive() stays true forever.

**Fix:** Only a mob whose addFreshEntity returned true is tracked. A refused one is logged, and a refused boss is tried again three times before the run fails with a message.

**Prevention:** Check the result of addFreshEntity whenever the entity is going to be waited on, and never count an entity as present because the object exists.

## [2026-11-07 01:34] — Cancelling EntityJoinLevelEvent for an entity loaded from disk deletes it for good

**Context:** The dungeon area refuses unmanaged mobs while a run is on. It did not look at where the entity came from.

**Error:** Armor stands and villagers inside a dungeon's area disappeared from the world, with nothing in the log above debug level.

**Root cause:** An entity read from a chunk goes through the same event. Cancelling it means it is not added to the section, so the next save of the chunk no longer holds it: a refusal becomes a deletion. The same handler also ran for the client's copies in single player, where tags are not synced, and hid the run's own mobs from the host.

**Fix:** Check event.loadedFromDisk(): from disk, only a monster that would have despawned anyway is refused. Return at once when event.getLevel().isClientSide().

**Prevention:** A join filter must branch on loadedFromDisk() and on the logical side before it cancels anything.

## [2026-11-07 01:34] — Game test mock player fails with Payload may not be sent to the client

**Context:** Writing run tests that need a ServerPlayer, with h.makeMockServerPlayerInLevel(), on a mod depending on Arcadia Lib.

**Error:** java.lang.UnsupportedOperationException: Payload arcadia_lib:hub_permissions may not be sent to the client! at ArcadiaLibNet.sendHubPermissions

**Root cause:** The mock player logs in through placeNewPlayer on a connection with no negotiated channels, and the library sends its payload on login.

**Fix:** Use FakePlayer, which is in no player list and holds no chunk. That is also the stricter case for tests about unloading. What looks players up by id gets it through the private PlayerList.playersByUUID map by reflection.

**Prevention:** On any mod depending on Arcadia Lib, build test players with FakePlayer, not the mock player of the framework.

## [2026-11-07 01:34] — Run game tests failed at random: mobs in bedrock, on roofs, or killed with nobody near

**Context:** Game tests running thirty scenarios at once on far stretches of the flat test world, with time budgets in milliseconds.

**Error:** the mob was put outside its room; the wave counts as cleared although no mob was killed; the boss was removed and the run is still waiting for it

**Root cause:** Three traps of the test world, none in the mod. getHeight answers the bottom of the world for a chunk that is not loaded, which put test sites inside bedrock. The flat world has villages, so a site can land on a roof and golems kill test monsters. Every test starts in one tick and generates chunks synchronously, a freeze of up to a minute on Forge 1.20.1 that used up time budgets computed before it.

**Fix:** Load the chunk before reading its height, use mobs golems ignore when one has to stay alive, start time budgets in the scenario's first step, and wait with isPositionEntityTicking before looking entities up.

**Prevention:** In a game test, never read getHeight on a chunk you did not load, never let a monster wait in the open, and start any wall clock budget inside the sequence.
