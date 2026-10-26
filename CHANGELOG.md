# Changelog

All notable changes to Arcadia Dungeon are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). One version number covers
every target; an entry that concerns only one of them says so, e.g. "(Forge 1.20.1)".

This mod is **server side only**: it has no client classes, and its screens are drawn by the shared
panel protocol in Arcadia Lib.

---

## [2.0.0] - 2026-10-26
### Correctifs

- **Les pommes dorées ne faisaient rien dans un donjon** - Un donjon refuse tout effet bénéfique sauf autorisation préalable, et les pommes dorées sont censées être l’exception. L’autorisation était écrite à la fin de la consommation, que NeoForge signale après que les effets ont déjà été proposés et refusés : la pomme était donc mangée pour rien, sans Régénération ni Absorption, et sans Résistance ni Résistance au feu pour une pomme enchantée. Elle est maintenant écrite au début de la consommation, trente-deux ticks plus tôt, et la fenêtre ouverte est courte pour qu’une bouchée annulée ne laisse aucune brèche.

### Fixed

- **Golden apples did nothing inside a dungeon** - A dungeon refuses every beneficial effect unless it has been allowed in advance, and golden apples are meant to be the exception. The allowance was written when the apple finished being eaten, which NeoForge posts after the effects have already been offered and refused, so the apple was consumed for nothing: no Regeneration, no Absorption, and for an enchanted one no Resistance or Fire Resistance either. It is now written when the player starts eating, about thirty two ticks earlier, and the window it opens is short so a cancelled bite leaves no gap.


### Added

- **Translation files** — The panel wrote its own text, in French on two targets and English on the
  third, so whichever language a player chose, two targets of three answered in the other one. Ten
  keys now, with `en_us` and `fr_fr` on every target, and the French has its accents back.
- **A Dungeons panel in the hub** — Reached from the hub rather than by command, listing every
  dungeon with its state: recruiting, running, on cooldown, or already cleared.

### Changed

- **Three targets** — Forge 1.20.1 and NeoForge 26.1.2 join NeoForge 1.21.1, with the shared code
  under `common/<minecraft version>`.
- **The close button is gone** — The panel already prints "Press ESC to close" under its toolbar,
  so the button spent a column repeating the keyboard.

### Ajouts

- **Fichiers de traduction** — Le panneau écrivait son propre texte, en français sur deux cibles et
  en anglais sur la troisième : quelle que soit la langue choisie, deux cibles sur trois
  répondaient dans l'autre. Dix clés désormais, avec `en_us` et `fr_fr` sur chaque cible, et le
  français a retrouvé ses accents.
- **Un panneau Donjons dans le hub** — Accessible depuis le hub plutôt que par commande, listant
  chaque donjon avec son état : recrutement, en cours, en temps de recharge, ou déjà terminé.

### Modifications

- **Trois cibles** — Forge 1.20.1 et NeoForge 26.1.2 rejoignent NeoForge 1.21.1, le code partagé
  vivant sous `common/<version de minecraft>`.
- **Le bouton Fermer a disparu** — Le panneau écrit déjà « Press ESC to close » sous sa barre, donc
  le bouton dépensait une colonne à répéter le clavier.
