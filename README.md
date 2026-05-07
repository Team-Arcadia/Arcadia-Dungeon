# Arcadia Dungeon

Mod NeoForge 1.21.1 — Donjons instanciés multijoueur avec archétypes, chrono et récompenses.

## Prérequis

- Minecraft 1.21.1
- NeoForge ≥ 21.1.222
- Java 21

## Installation

1. Télécharger `arcadia_dungeon-1.0.jar` et le placer dans le dossier `mods/` de ton instance.
2. Lancer le serveur (ou le client solo) — le fichier `example_dungeon.json` est généré automatiquement dans `config/arcadia_dungeon/dungeons/`.

---

## Pour les joueurs

### Rejoindre un donjon

1. Ouvrir le hub : tape `/arcadia hub` en jeu.
2. Choisir un donjon dans la liste.
3. Choisir ton **archétype** (Guerrier · Mage · Archer) — ton inventaire est mis de côté automatiquement.
4. Cliquer **Lancer** dans le lobby pour démarrer la run.

### Pendant la run

- Le HUD (haut-gauche) affiche ton **chrono**, les **vies restantes** et la **salle courante**.
- Le pool de vies est **partagé** : chaque mort consomme 1 vie. À 0 → DÉFAITE, récompenses partielles.
- Tue le boss pour décrocher la **VICTOIRE** et les récompenses complètes.
- Tape `/arcadia abandon` à tout moment pour quitter (aucune récompense).

### Récompenses

| Résultat | Currency | Loot | PB |
|----------|----------|------|----|
| VICTOIRE | 100 %    | Oui  | Oui (si plus rapide) |
| DÉFAITE  | 25 %     | Non  | Non |
| ABANDON  | —        | —    | — |

L'inventaire d'avant la run est **toujours restauré** à la fin, quel que soit le résultat.

---

## Pour les admins (op ≥ 2)

### Commandes

```
/arcadia reload              — Recharge tous les JSONs de donjons sans redémarrer le serveur
/arcadia debug showscreen    — Ouvre l'écran debug
/arcadia debug forcecomplete <runId>  — Termine la run en VICTOIRE (+ distribue les récompenses)
/arcadia debug forcedefeat   <runId>  — Termine la run en DÉFAITE (+ distribue les récompenses)
/arcadia debug killboss      <runId>  — Tue le boss en jeu (déclenche la VICTOIRE via event)
/arcadia debug showvictory           — Ouvre le ResultScreen VICTOIRE sans run active (test UI)
```

Le `runId` est un UUID affiché dans les logs serveur lors du démarrage de la run.

### Admin Hub

Ouvre le hub admin in-game (écran dédié) via `/arcadia debug showscreen` — liste des donjons chargés avec leurs statuts, bouton Reload intégré.

### Configurer un donjon

1. Copier `config/arcadia_dungeon/dungeons/example_dungeon.json` et le renommer (ex : `mon_donjon.json`).
2. Modifier les champs — chaque champ est auto-documenté par son champ `_doc_*` voisin.
3. Recharger en jeu : `/arcadia reload`.

**Champs principaux :**

```jsonc
{
  "schemaVersion": 1,
  "id": "mon_namespace:mon_donjon",   // doit correspondre au nom de fichier
  "nameKey": "Mon Donjon",            // affiché dans le hub joueur
  "lives": 3,                         // pool de vies partagé
  "rooms": [ /* 2-3 salles avec waves */ ],
  "boss": {
    "type": "minecraft:wither_skeleton",
    "hp": 200,
    "phases": [ /* seuils HP → multiplicateurs */ ]
  },
  "rewards": {
    "currency": 50,
    "loot": [{ "item": "minecraft:diamond", "min": 1, "max": 3 }]
  },
  "archetypes": [
    { "id": "warrior", "nameKey": "Guerrier", "items": ["minecraft:iron_sword", "..."] }
  ]
}
```

Le fichier `example_dungeon.json` est la référence complète avec commentaires.

---

## Structure des fichiers

```
config/arcadia_dungeon/
└── dungeons/
    ├── example_dungeon.json   ← exemple auto-généré
    └── mon_donjon.json        ← tes donjons personnalisés
```

Les données joueur (currency, PBs) sont sauvegardées dans `saves/<world>/arcadia_dungeon/`.

---

## Problèmes connus

- Les structures Minecraft (templateRef) doivent exister dans le pack de données — le mod ne génère pas les structures lui-même en v1.0.
- Un run zombie (bloquée > 30 min) est automatiquement nettoyée sans récompense.

## Licence

Voir `LICENSE` à la racine du dépôt.
