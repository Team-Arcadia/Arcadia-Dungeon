# Arcadia Dungeon

Un système de donjons **entièrement configurable** pour serveurs Minecraft avec des bosses adaptatives, des phases de combat, des récompenses et bien plus !

**Mod ID:** `arcadia_dungeon`  
**Version:** 1.0.0  
**Auteur:** vyrriox  
**Plateforme:** NeoForge 1.21.1

---

## 🎮 Fonctionnalités

### 🏰 Système de Donjons Avancé

- **Configuration JSON complète** : Définissez des donjons avec tous les détails souhaités
- **Systèmes de phases** : Bosses avec plusieurs phases de combat
- **Bosses adaptatives** : Augmentation de santé/dégâts en fonction du nombre de joueurs
- **Invocation de sbires** : Les bosses peuvent invoquer des créatures alliées
- **Points de contrôle** : Téléportation au spawn/retour à la fin

### ⚔️ Combat Avancé

- Multiplicateurs de dégâts et vitesse personnalisables par phase
- Actions requises (tuer les sbires, attendre, etc.)
- Invulnérabilité pendant les transitions de phase
- Immunité aux dégâts configurable
- Messages de phase personnalisés

### 🎯 Gestion des Récompenses

- Récompenses personnalisables pour chaque dungeon
- Cooldowns après complétion
- Support des commandes post-complétion

### 📢 Notifications

- Annonces globales de démarrage/complétion
- Annonces d'échec
- Messages colorés configurables

### 🔐 Permissions

- Système de permissions compatibles **LuckPerms**
- `arcadia_dungeon.bypass.antiparasite` : Contourner le système anti-parasite
- `arcadia_dungeon.bypass.antifly` : Contourner le système anti-vol

### 📊 Intégrations Optionnelles

- **LuckPerms** : Gestion avancée des permissions
- **Spark** : Profilage de performance

---

## 📋 Pré-requis

- **Java 21** ou supérieur
- **Minecraft 1.21.1**
- **NeoForge 21.1.0+**

---

## 🚀 Installation

### Pour les serveurs

1. Téléchargez le fichier JAR du mod depuis les [releases](https://github.com/vyrriox/Arcadia/releases)
2. Placez-le dans le dossier `mods` de votre serveur NeoForge
3. Redémarrez le serveur

### Pour le développement

```bash
# Clone le repository
git clone https://github.com/vyrriox/Arcadia.git
cd Arcadia-Dungeon

# Construire le projet
./gradlew build

# Lancer le serveur en développement
./gradlew runServer

# Lancer le client en développement
./gradlew runClient
```

---

## ⚙️ Configuration

Les donjons se configurent via des fichiers **JSON** dans le répertoire `dungeon-configs/`.

### Structure d'un Dungeon

```json
{
  "id": "mon_donjon",
  "name": "Mon Donjon",
  "cooldownSeconds": 3600,
  "announceStart": true,
  "announceCompletion": true,
  "startMessage": "&6[Donjon] &e%player% lance &e%dungeon%!",
  "completionMessage": "&6[Donjon] &a%player% a vaincu &e%dungeon%!",
  "failMessage": "&6[Donjon] &c%player% a échoué dans &e%dungeon%!",
  "teleportBackOnComplete": true,
  "spawnPoint": {
    "dimension": "minecraft:overworld",
    "x": 0.0,
    "y": 64.0,
    "z": 0.0,
    "yaw": 0.0,
    "pitch": 0.0
  },
  "bosses": [
    {
      "id": "boss_1",
      "entityType": "minecraft:wither",
      "customName": "Chef du Donjon",
      "baseHealth": 300.0,
      "baseDamage": 20.0,
      "healthMultiplierPerPlayer": 0.5,
      "damageMultiplierPerPlayer": 0.2,
      "phases": [
        {
          "phase": 1,
          "healthThreshold": 1.0,
          "damageMultiplier": 1.0,
          "speedMultiplier": 1.0,
          "description": "Phase 1",
          "phaseStartMessage": "&e[Boss] Préparez-vous!",
          "summonMobs": [],
          "requiredAction": "NONE"
        }
      ]
    }
  ]
}
```

### Variables Disponibles

- `%player%` : Nom du joueur
- `%dungeon%` : Nom du dungeon

### Schéma JSON

Consultez [dungeon-schema.json](dungeon-configs/dungeon-schema.json) pour la liste complète des propriétés disponibles.

### Exemple

Un exemple complet est fourni dans [dungeon-configs/examples/example_dungeon.json](dungeon-configs/examples/example_dungeon.json).

---

## 🎮 Utilisation

### Commandes

```
/arcadia_dungeon list                 # Lister tous les donjons
/arcadia_dungeon info <dungeon_id>    # Afficher les infos d'un dungeon
/arcadia_dungeon join <dungeon_id>    # Rejoindre un dungeon
/arcadia_dungeon leave                # Quitter le dungeon actuel
/arcadia_dungeon reload               # Recharger les configurations
```

---

## 🔧 Développement

### Structure du Projet

```
src/main/java/com/vyrriox/arcadiadungeon/
├── ArcadiaDungeon.java       # Point d'entrée du mod
├── boss/                      # Système de boss
├── command/                   # Commandes
├── config/                    # Configuration & sérialisation
├── dungeon/                   # Gestion des donjons
├── event/                     # Gestionnaires d'événements
└── util/                      # Utilitaires
```

### Build

```bash
# Compiler uniquement
./gradlew compileJava

# Compiler et tester
./gradlew build

# Générer les sources en IDE
./gradlew genSources
```

### Gradle Properties

Les propriétés principales se trouvent dans [gradle.properties](gradle.properties):

- `minecraft_version`: Version Minecraft ciblée
- `mod_version`: Version du mod
- `mod_id`: Identifiant unique du mod
- `mod_authors`: Auteurs du mod

---

## 📖 Documentation Additionnelle

- [Procédure de Test](PROCEDURE_TEST.html) - Guide pour tester le mod
- [Schéma JSON](dungeon-configs/dungeon-schema.json) - Schéma complet de configuration

---

## 🐛 Dépannage

### Le mod ne charge pas

- Vérifiez que vous utilisez Java 21+
- Assurez-vous que NeoForge 21.1.0+ est installé
- Consultez les logs du serveur pour les erreurs

### Les donjons ne se chargent pas

- Vérifiez que les fichiers JSON sont dans `dungeon-configs/`
- Validez la syntaxe JSON
- Vérifiez les logs pour les erreurs de parsing

---

## 📄 Licence

**All Rights Reserved** - Tous droits réservés

---

## 🤝 Contribution

Les contributions sont bienvenues ! Pour soumettre des améliorations :

1. Créez une fork du projet
2. Créez une branche (`git checkout -b feature/ma-feature`)
3. Committez vos changements (`git commit -am 'Ajout de ma feature'`)
4. Pushez vers la branche (`git push origin feature/ma-feature`)
5. Créez une Pull Request

---

## 📞 Support

Pour les rapports de bugs ou les demandes de features, consultez la page [Issues](https://github.com/vyrriox/Arcadia/issues) du projet.

---

**Fait avec ❤️ par vyrriox - Enjoy your dungeons!**
