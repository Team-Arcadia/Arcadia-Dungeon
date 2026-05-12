# CLAUDE.md

Mod NeoForge 1.21.1 — **Arcadia Dungeon v1.0** (fresh start).

## État du projet

Le projet redémarre **from scratch** après une phase PoC (v0.0.1) archivée dans `_legacy/`.

```
arcadia-dungeon/
├── src/                  ← code v1 (squelette pour l'instant)
├── _legacy/              ← code PoC archivé (hors classpath, référence)
├── _bmad/                ← skills BMAD
├── _bmad-output/         ← artefacts planning (PRD, brainstorming)
└── ...
```

## Build

```bash
./gradlew compileJava       # compile
./gradlew runClient          # lancer client MC avec le mod
./gradlew runServer          # lancer serveur dédié
./gradlew runGameTestServer  # gametests
```

Java 21 · NeoForge 21.1.222 · Minecraft 1.21.1

## En cours

- Brainstorming features (Must/Should/Could)
- Game moments (5-7 moments définissant le feeling)
- Nouvelle PRD via `/bmad-create-prd`
- Architecture DDD + UX wireframes
- Implémentation par lots

## Règles

- Pas de Co-Authored-By dans les commits
- Pas de modif dans `_legacy/` — c'est une archive référence en lecture seule, à supprimer définitivement avant release v1.0
- Quand on ressuscite un fichier de `_legacy/`, on le **réécrit** au passage (pas de copier-coller aveugle)
