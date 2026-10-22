<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHSSBWarpsGUI

*Menus des warps d'île SuperiorSkyblock2 : liste, téléportation, nom, icône et description.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Menus des warps d'île SuperiorSkyblock2 : liste, téléportation, nom, icône et description.

Utilisé sur : **Opale**.

### Saisies par dialogue natif (clients 1.21.6 et plus)

Depuis le menu de gestion d'un warp (clic droit sur le warp), les saisies qui passaient par le chat s'ouvrent dans une fenêtre Minecraft avec les boutons **Valider** et **Annuler** (Échap annule) :

- **Renommer** : champ de texte prérempli avec le nom actuel, mêmes règles qu'avant (longueur visible maximale des noms d'île de SuperiorSkyblock2, nom déjà pris sur l'île).
- **Description** : champ multiligne, une ligne de description par ligne du champ (le séparateur `//` du chat marche aussi), prérempli avec la description actuelle. Chaque ligne est grise sauf si elle commence par un code couleur, `manage-warp-gui.max-lore-lines` lignes au maximum ; un champ vide efface la description.
- **Suppression** : fenêtre de confirmation Oui / Non à la place du menu de confirmation ; Non ou Échap revient au menu de gestion.

Les erreurs s'affichent en rouge dans la fenêtre, qui se rouvre avec le texte déjà saisi. Mêmes messages de succès et mêmes droits qu'avant : le warp est revérifié au moment d'agir (île et warp toujours là, joueur membre de l'île ou `shssbwarpsgui.admin.editothers`).

Les clients plus anciens, ou `dialogues.actif: false`, gardent exactement l'ancienne saisie dans le chat (`annuler`, `fin`, `clear`, `//`) et le menu de confirmation. Avec ViaVersion, le plugin lit la version du client (`dialogues.min-protocol`, 771 = 1.21.6) ; sans ViaVersion tous les clients reçoivent le dialogue. Le choix de l'icône (objet en main) et la mise en avant payante gardent leur fonctionnement.

Chaque fenêtre porte un jeton secret à usage unique, valable `dialogues.expiration` secondes : un clic ancien, rejoué, forgé ou venant d'un autre joueur est ignoré.

## Dépendances

- Obligatoires : `SuperiorSkyblock2`, `Vault`
- Optionnelles : `PlaceholderAPI`, `ViaVersion`

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/is_edit` | `/island_edit` | Editer un de vos warps. | `shssbwarpsgui.warp.edit` |
| `/swgadmin` | `/shssbwarps` | Commandes d'administration pour SHSSBWarpsGUI. | `shssbwarpsgui.admin` |

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shssbwarpsgui.admin` | Permet d'utiliser les commandes d'administration comme /swgadmin reload. | `op` |
| `shssbwarpsgui.use` | Permission de base pour utiliser le plugin. | `true` |
| `shssbwarpsgui.warps.list.all` | Permet de voir la liste de tous les warps avec /is warps. | `true` |
| `shssbwarpsgui.warps.list.other` | Permet de voir les warps d'un autre joueur avec /is warps <pseudo>. | `true` |
| `shssbwarpsgui.warp.self` | Permet de voir ses propres warps avec /is warp. | `true` |
| `shssbwarpsgui.warp.teleport` | Permet de se téléporter à ses propres warps avec /is warp <nom>. | `true` |
| `shssbwarpsgui.warp.edit` | Permet d'éditer ses warps avec /is edit. | `true` |
| `shssbwarpsgui.admin.editothers` | Permet d'éditer les warps d'autres joueurs (via le GUI de gestion). | `op` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHSSBWarpsGUI/
└── <a href="src/main/resources/config.yml">config.yml</a>
    ├── messages, manage-warp-messages, main-gui, player-warps-gui, manage-warp-gui, featured-warps…   (inchangés)
    └── <a href="src/main/resources/config.yml#L338">dialogues</a>
        ├── <a href="src/main/resources/config.yml#L339">actif</a>                 dialogues activés (false : chat comme avant)
        ├── <a href="src/main/resources/config.yml#L342">min-protocol</a>          version de protocole minimale (771 = 1.21.6)
        ├── <a href="src/main/resources/config.yml#L344">expiration</a>            validité d'une fenêtre, en secondes
        ├── <a href="src/main/resources/config.yml#L345">boutons</a>               textes Valider / Annuler
        ├── <a href="src/main/resources/config.yml#L348">renommer</a>              fenêtre du nom (titre, corps, champ, longueur-max)
        ├── <a href="src/main/resources/config.yml#L356">description</a>           fenêtre de la description (champ multiligne, hauteur, pre-remplir)
        ├── <a href="src/main/resources/config.yml#L369">suppression</a>           confirmation de suppression
        ├── <a href="src/main/resources/config.yml#L377">erreurs</a>               erreurs affichées en rouge dans la fenêtre
        └── <a href="src/main/resources/config.yml#L382">messages</a>              fenêtre expirée, erreur inattendue
</pre>

La section `dialogues` est complète dans le jar : un `config.yml` déjà présent sur le serveur qui ne la contient pas utilise les valeurs par défaut.

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
