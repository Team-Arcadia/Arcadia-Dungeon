# Changelog

All notable changes to Arcadia Dungeon are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). One version number covers
every target; an entry that concerns only one of them says so, e.g. "(Forge 1.20.1)".

This mod is **server side only**: it has no client classes, and its screens are drawn by the shared
panel protocol in Arcadia Lib.

---

## [2.0.1] - 2026-11-05

### Added

- **Language files you can edit** - The text players read now lives in
  `config/arcadia/dungeon/lang/en_us.json` and `fr_fr.json`, written on first start. Colour codes
  (`&6`) and placeholders (`%dungeon%`) work in every line, and a change is picked up by a restart
  or `/arcadia_dungeon admin debug reload`. An update adds the lines it brings and never touches
  the ones already there.
- **Your own language** - Copy `en_us.json` to, say, `es_es.json` and translate it. Players whose
  game is in that language get it, and a line the file does not have comes from `en_us`. To give
  everyone one language whatever their game is set to, write `{"language": "es_es"}` in
  `config/arcadia/dungeon/language.json`. The default, `"auto"`, follows each player's game.

### Fixed

- **Dungeon messages stayed in French whatever the config said** - Recruitment, countdowns, gates
  closing, waves, bosses, deaths, end of run, the leaderboards and the `status`, `progression`,
  `top` and `stats` commands were written into the code. They are all in the language files now.
- **`recruitmentMessage` was never read** - The field was saved in every dungeon and offered by
  `setmessage recruitment`, and the announcement ignored it. A dungeon's own text now replaces the
  announcement, with the join button after it.
- **The Dungeons panel could show raw keys** - A player without the mod on their client, which is
  the point of a server-side mod, read `arcadia_dungeon.panel.title` instead of a title. The server
  now sends the text itself.
- **`&c&l` lost its colour** - Each code replaced the one before it, so that line came out bold and
  white. Codes now combine the way the game reads them.

### Changed

- **Dungeon announcements are optional overrides** - `startMessage`, `completionMessage`,
  `failMessage`, `availabilityMessage` and `recruitmentMessage` are empty by default and the
  language files answer instead. A config still holding one of the old French defaults is treated
  as empty, and a message you wrote yourself is kept.

Admin commands (`/arcadia_dungeon admin ...`) and the example dungeon's content are still in French.

### Ajouts

- **Des fichiers de langue modifiables** - Le texte lu par les joueurs se trouve désormais dans
  `config/arcadia/dungeon/lang/en_us.json` et `fr_fr.json`, écrits au premier démarrage. Les codes
  couleur (`&6`) et les variables (`%dungeon%`) fonctionnent sur chaque ligne, et une modification
  est prise en compte au redémarrage ou avec `/arcadia_dungeon admin debug reload`. Une mise à jour
  ajoute ses nouvelles lignes sans jamais toucher à celles qui existent.
- **Votre propre langue** - Copiez `en_us.json` en `es_es.json`, par exemple, et traduisez-le. Les
  joueurs dont le jeu est dans cette langue la reçoivent, et une ligne absente du fichier vient de
  `en_us`. Pour imposer une langue à tout le monde quel que soit leur jeu, écrivez
  `{"language": "es_es"}` dans `config/arcadia/dungeon/language.json`. La valeur par défaut,
  `"auto"`, suit le jeu de chaque joueur.

### Correctifs

- **Les messages de donjon restaient en français quoi que dise la config** - Le recrutement, les
  comptes à rebours, la fermeture des portes, les vagues, les boss, les morts, la fin de partie, les
  classements et les commandes `status`, `progression`, `top` et `stats` étaient écrits dans le
  code. Tout se trouve maintenant dans les fichiers de langue.
- **`recruitmentMessage` n'était jamais lu** - Le champ était enregistré dans chaque donjon et
  proposé par `setmessage recruitment`, mais l'annonce l'ignorait. Le texte propre à un donjon
  remplace maintenant l'annonce, suivi du bouton pour rejoindre.
- **Le panneau Donjons pouvait afficher des clés brutes** - Un joueur sans le mod sur son client, ce
  qui est tout l'intérêt d'un mod server-side, lisait `arcadia_dungeon.panel.title` au lieu d'un
  titre. Le serveur envoie désormais le texte lui-même.
- **`&c&l` perdait sa couleur** - Chaque code remplaçait le précédent, la ligne sortait donc en gras
  et en blanc. Les codes se combinent maintenant comme le jeu les lit.

### Modifications

- **Les annonces de donjon deviennent des remplacements optionnels** - `startMessage`,
  `completionMessage`, `failMessage`, `availabilityMessage` et `recruitmentMessage` sont vides par
  défaut et les fichiers de langue prennent le relais. Une config qui contient encore l'un des
  anciens textes français par défaut est considérée comme vide, et un message écrit par vos soins
  est conservé.

Les commandes d'administration (`/arcadia_dungeon admin ...`) et le contenu du donjon d'exemple
restent en français.

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
