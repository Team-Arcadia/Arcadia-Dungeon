# Changelog

All notable changes to Arcadia Dungeon are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). One version number covers
every target; an entry that concerns only one of them says so, e.g. "(Forge 1.20.1)".

This mod is **server side only**: it has no client classes, and its screens are drawn by the shared
panel protocol in Arcadia Lib.

---

## [2.0.2] - 2026-11-07

### Fixed

- **Bosses you could not hit, waves that never came** - A run followed its mobs through an object
  the game replaces whenever a chunk unloads, which happens as soon as nobody is near, typically when
  a player dies far from the entrance and respawns there. A wave read that as its mobs being dead and
  cleared itself, then the next one did the same, so nobody ever saw a mob. A boss came back from
  disk unknown to its run: hits did not count towards its phases, killing it ended nothing, and one
  unloaded during its five seconds of spawn protection stayed invulnerable for good. A run now
  follows its mobs by id and keeps the chunks they stand in loaded for as long as it needs them.
- **Leftovers from past runs** - A mob whose run ended while its chunk was unloaded, or whose server
  crashed, stayed in the world attached to nothing, invulnerable bosses included. It is now removed
  when its chunk loads, so the ones already in your world go away by themselves.
- **Minions protected their boss whatever the phase said** - Every summon made the boss immune. Only
  a phase with `requiredAction` set to `KILL_SUMMONS` does now, as the setting always said.
- **Mobs another mod refused** - A mod that cancels a spawn left the run waiting for a mob that was
  never in the world: its wave could not end, and a refused summon kept its boss immune for good.
- **A dungeon with no waves never spawned its boss** after a recruitment, and without a recruitment
  the bosses meant for the start of the run never came. Both ways of opening a run now do the same.
- **The final boss came early** - Killing a boss that came with the start of the run brought the
  final boss in the middle of the waves, and paused them.
- **Mobs that vanished** - A piglin zombified after fifteen seconds in the overworld and a zombie
  turned into a drowned in water. The new mob belonged to no run and was refused by the area while
  the old one was removed, so the mob disappeared, and a boss took its run with it. Dungeon mobs now
  stay what they are, and they no longer leave through portals.
- **Bosses gone without a death** - A boss removed without a death event, as the ender dragon and
  some modded bosses are, left the run waiting until its timer ran out. It now counts as defeated. A
  boss another mod removes comes back at the health and phase it had, and after three tries the run
  fails with a message and a line in the log naming what to check.
- **Mobs out of reach** - The spawn search looked at every block around the point and refused
  carpets, slabs and paths as floors, so in a carpeted room the nearest place it accepted was the
  roof or the far side of a wall. The configured point now comes first, and the search walks out
  from it through open space.
- **The world lost entities during a run** - An armor stand or a villager in the area of a running
  dungeon was deleted for good when its chunk loaded. Only monsters that would have despawned anyway
  are refused now, and starting a run no longer clears pets and named mobs.
- **Pillagers never attacked** - A pillager with no main hand configured stood there unarmed, since
  the game hands out its crossbow in a step dungeons skip. It now gets one. Write `minecraft:air` in
  `mainHand` to really leave it empty. Illusioners get their bow the same way.
- **The first phase of a boss was ignored** - Its multipliers, message and summons never applied, and
  a first phase below full health never started. Phases are also put in order when a config is read,
  so a file edited by hand no longer jumps straight to the last one.
- **Rewards lost on the death screen** - Items given to a dead player were lost with its inventory,
  which hit whoever was down when a teammate finished the boss, and a dead player lost its way back.
  Both now wait for the respawn.
- **Time warnings played only once** - They were remembered for the life of the server, so only a
  dungeon's first run after a restart announced them.
- **Leaving a recruitment closed the dungeon** - Whoever opened a recruitment and left put the dungeon
  on its availability timer for everyone, and was announced as having failed it.
- **Two runs at once** - A player in a run could start another one and was then pulled back by both.
- **What a phase command summons** was refused by the area, and so were the adds a modded boss spawns
  by itself. Both are let in now and leave with the run.
- **Phase speed** multiplied the default speed, not the one set in `customAttributes`.
- **Flight taken away for good** - A player who disconnected during a run did not get it back.
- **Attribute names between game versions** - On 26.1.2, `minecraft:generic.armor` and the like were
  unknown and ignored, so a dungeon written for 1.21.1 lost its armor. Either name works everywhere.
- **Mobs invisible in single player** - The area refused the client's copy of the run's mobs.
- **One broken dungeon stopped the whole server** - An exception in a dungeon's tick now goes to the
  log, once a minute, and the server keeps running.

### Added

- **Mobs nobody can reach are brought back** - Players are held inside the area, so a mob outside it
  can never be killed, and neither can one stuck in a wall. After five seconds it goes back to where
  it spawned. If nobody hits anything for two minutes, mobs that wandered off go back too, and after
  four minutes they come to the nearest player. A long walk to the next room moves nothing, and an
  optional boss nobody has to kill stays hidden. `settings.rescueStuckMobs` turns all of it off.
- **A boss's minions can always be found** - They glow after 15 seconds and are brought back to the
  boss after 30 if they strayed. After 60 seconds without a hit they come to the nearest player, and
  after `settings.summonLockMaxSeconds` (180 by default, 0 for never) the boss can be hit anyway. The
  warning now says how many are left.
- **`/arcadia_dungeon admin debug validate`** - Lists what is wrong with each dungeon's config:
  unknown mobs, items, effects and attributes, spawn points outside the area or in a dimension that
  does not exist, a boss after a wave that does not exist. The same list goes to the log at start
  and on reload. Nothing is refused because of it.
- **A dungeon says why it cannot start** - One with nothing in it, or monsters on Peaceful difficulty,
  now refuses with a message. Mobs and bosses that fail to spawn are named in the log.
- **Game tests on every target** - Forge 1.20.1 and NeoForge 26.1.2 now run them too, and a new one
  plays whole dungeons from the first wave to their completion.

### Changed

- **Your dungeons do not need any change** - Config files load as they are and the new settings take
  their default. Two things you may notice in a fight, because they are what the config always said:
  pillagers now shoot, and a phase whose `requiredAction` is `NONE` no longer makes the boss immune.

### Correctifs

- **Boss qu'on ne pouvait pas taper, vagues qui n'arrivaient jamais** - Une partie suivait ses mobs
  à travers un objet que le jeu remplace dès qu'un chunk se décharge, ce qui arrive dès que personne
  n'est à proximité, typiquement quand un joueur meurt loin de l'entrée et réapparaît là-bas. Une
  vague prenait ça pour la mort de ses mobs et se terminait toute seule, puis la suivante faisait de
  même, et personne ne voyait jamais un mob. Un boss revenait du disque inconnu de sa partie : les
  coups ne comptaient plus pour ses phases, le tuer ne terminait rien, et un boss déchargé pendant
  ses cinq secondes de protection restait invulnérable pour de bon. Une partie suit désormais ses
  mobs par identifiant et garde chargés les chunks où ils se trouvent tant qu'elle en a besoin.
- **Restes des anciennes parties** - Un mob dont la partie s'est terminée pendant que son chunk était
  déchargé, ou dont le serveur a planté, restait dans le monde sans rien pour le gérer, boss
  invulnérables compris. Il est maintenant retiré au chargement de son chunk : ceux déjà présents
  dans votre monde disparaissent d'eux-mêmes.
- **Les sbires protégeaient leur boss quoi que dise la phase** - Chaque sbire rendait le boss immunisé.
  Seule une phase avec `requiredAction` à `KILL_SUMMONS` le fait désormais, comme l'a toujours dit
  ce réglage.
- **Mobs refusés par un autre mod** - Un mod qui annule une apparition laissait la partie attendre un
  mob qui n'a jamais été dans le monde : sa vague ne pouvait pas finir, et un sbire refusé gardait
  son boss immunisé pour de bon.
- **Un donjon sans vagues ne faisait jamais apparaître son boss** après un recrutement, et sans
  recrutement les boss prévus pour le début de la partie ne venaient jamais. Les deux façons de
  lancer une partie font maintenant la même chose.
- **Le boss final arrivait trop tôt** - Tuer un boss venu avec le début de la partie faisait venir le
  boss final au milieu des vagues, et les mettait en pause.
- **Mobs qui disparaissaient** - Un piglin se zombifiait après quinze secondes dans l'overworld et un
  zombie se changeait en noyé dans l'eau. Le nouveau mob n'appartenait à aucune partie et la zone le
  refusait pendant que l'ancien était retiré : le mob disparaissait, et un boss emportait sa partie
  avec lui. Les mobs de donjon restent désormais ce qu'ils sont, et ne partent plus par les portails.
- **Boss partis sans mourir** - Un boss retiré sans événement de mort, comme l'ender dragon et certains
  boss moddés, laissait la partie attendre jusqu'à la fin du temps. Il compte maintenant comme vaincu.
  Un boss retiré par un autre mod revient avec la vie et la phase qu'il avait, et après trois essais
  la partie échoue avec un message et une ligne de log qui dit quoi vérifier.
- **Mobs hors de portée** - La recherche d'apparition regardait chaque bloc autour du point et refusait
  les tapis, les dalles et les chemins comme sols : dans une salle avec des tapis, le premier endroit
  accepté était le toit ou l'autre côté d'un mur. Le point configuré passe maintenant en premier, et
  la recherche part de lui à travers l'espace libre.
- **Le monde perdait des entités pendant une partie** - Un porte-armure ou un villageois dans la zone
  d'un donjon en cours était supprimé pour de bon au chargement de son chunk. Seuls les monstres qui
  auraient de toute façon disparu sont maintenant refusés, et lancer une partie n'efface plus les
  familiers ni les mobs nommés.
- **Les pillards n'attaquaient jamais** - Un pillard sans arme configurée restait là sans rien, car le
  jeu donne son arbalète à une étape que les donjons sautent. Il en reçoit une maintenant. Écrivez
  `minecraft:air` dans `mainHand` pour vraiment le laisser sans arme. Les illusionnistes reçoivent
  leur arc de la même façon.
- **La première phase d'un boss était ignorée** - Ses multiplicateurs, son message et ses sbires ne
  s'appliquaient jamais, et une première phase sous la vie maximale ne démarrait pas. Les phases sont
  aussi remises en ordre à la lecture d'une config, donc un fichier édité à la main ne saute plus
  directement à la dernière.
- **Récompenses perdues sur l'écran de mort** - Les objets donnés à un joueur mort disparaissaient avec
  son inventaire, ce qui touchait celui qui était à terre quand un coéquipier finissait le boss, et un
  joueur mort perdait son chemin de retour. Les deux attendent désormais la réapparition.
- **Les alertes de temps ne jouaient qu'une fois** - Elles étaient retenues pendant toute la vie du
  serveur, donc seule la première partie d'un donjon après un redémarrage les annonçait.
- **Quitter un recrutement fermait le donjon** - Celui qui ouvrait un recrutement puis partait mettait
  le donjon sous son délai de disponibilité pour tout le monde, et était annoncé comme ayant échoué.
- **Deux parties à la fois** - Un joueur en partie pouvait en lancer une autre et se faisait ensuite
  ramener par les deux.
- **Ce qu'une commande de phase invoque** était refusé par la zone, tout comme les renforts qu'un boss
  moddé fait apparaître lui-même. Ils entrent maintenant et repartent avec la partie.
- **La vitesse de phase** multipliait la vitesse par défaut, pas celle de `customAttributes`.
- **Le vol retiré pour de bon** - Un joueur déconnecté pendant une partie ne le récupérait pas.
- **Noms d'attributs entre versions du jeu** - En 26.1.2, `minecraft:generic.armor` et les autres
  étaient inconnus et ignorés : un donjon écrit pour la 1.21.1 perdait son armure. Les deux noms
  fonctionnent désormais partout.
- **Mobs invisibles en solo** - La zone refusait la copie côté client des mobs de la partie.
- **Un donjon cassé arrêtait tout le serveur** - Une exception pendant le tick d'un donjon va désormais
  dans le log, une fois par minute, et le serveur continue de tourner.

### Ajouts

- **Les mobs que personne ne peut atteindre sont ramenés** - Les joueurs sont retenus dans la zone,
  donc un mob en dehors ne peut jamais être tué, pas plus qu'un mob coincé dans un mur. Après cinq
  secondes, il retourne là où il est apparu. Si personne ne frappe rien pendant deux minutes, les
  mobs éloignés y retournent aussi, et après quatre minutes ils viennent au joueur le plus proche. Une
  longue marche jusqu'à la salle suivante ne déplace rien, et un boss optionnel que personne n'est
  obligé de tuer reste caché. `settings.rescueStuckMobs` désactive tout cela.
- **Les sbires d'un boss peuvent toujours être trouvés** - Ils brillent après 15 secondes et sont
  ramenés au boss après 30 s'ils se sont éloignés. Après 60 secondes sans un coup, ils viennent au
  joueur le plus proche, et après `settings.summonLockMaxSeconds` (180 par défaut, 0 pour jamais) le
  boss peut être frappé quand même. L'avertissement indique maintenant combien il en reste.
- **`/arcadia_dungeon admin debug validate`** - Liste ce qui ne va pas dans la config de chaque donjon :
  mobs, objets, effets et attributs inconnus, points d'apparition hors de la zone ou dans une dimension
  qui n'existe pas, boss après une vague qui n'existe pas. La même liste va dans le log au démarrage
  et au rechargement. Rien n'est refusé à cause d'elle.
- **Un donjon dit pourquoi il ne peut pas démarrer** - S'il ne contient rien, ou des monstres en
  difficulté Paisible, il refuse maintenant avec un message. Les mobs et boss qui n'apparaissent pas
  sont nommés dans le log.
- **Des game tests sur chaque cible** - Forge 1.20.1 et NeoForge 26.1.2 les exécutent aussi, et un
  nouveau joue des donjons entiers de la première vague jusqu'à la fin.

### Modifications

- **Vos donjons n'ont besoin d'aucun changement** - Les fichiers de config se chargent tels quels et
  les nouveaux réglages prennent leur valeur par défaut. Deux choses peuvent se remarquer en combat,
  parce que c'est ce que la config a toujours dit : les pillards tirent maintenant, et une phase dont
  le `requiredAction` est `NONE` ne rend plus le boss immunisé.

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
