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

### Mode réseau (plusieurs serveurs Opale)

Avec [SHNetwork](https://github.com/CryLegende/SHNetwork) et `reseau.actif: true` (valeur par défaut), les données que le plugin gardait dans les fichiers de chaque serveur passent par la base commune (`bo_net` en test) et un joueur retrouve les mêmes favoris, les mêmes compteurs et les mêmes warps mis en avant sur tous les serveurs. Sans SHNetwork, ou avec `actif: false`, le plugin fonctionne exactement comme avant : `favorites.yml`, `warp_visits.yml` et `featured_warps.yml` restent la seule source, aucune connexion à la base n'est ouverte.

| Donnée | Fichier d'origine | Table commune | Fonctionnement |
|---|---|---|---|
| Favoris d'un joueur | `favorites.yml` | `shssbwarps_favorites` (viewer, target) | une ligne ajoutée ou retirée à chaque clic ; relus à l'arrivée du joueur, une fois que le serveur de départ a fini d'écrire (barrière de SHNetwork) |
| Visites d'un warp | `warp_visits.yml` | `shssbwarps_visits` (owner, warp, visits) | `visits = visits + 1` à chaque visite : deux serveurs qui comptent en même temps ne perdent rien ; le délai entre deux visites du même joueur reste propre à chaque serveur |
| Warps mis en avant | `featured_warps.yml` | `shssbwarps_featured` (owner, warp, warp_name, expiry) | écrits à la mise en avant, relus par les autres serveurs dès l'annonce du changement ; une mise en avant échue est retirée une seule fois pour tout le réseau (la suppression de la ligne désigne le serveur qui prévient le propriétaire, où qu'il soit) |

Les menus ne lisent jamais la base : compteurs et mises en avant sont gardés en mémoire et relus toutes les `reseau.rafraichissement-secondes` secondes, hors du fil principal ; les écritures partent sur un fil à part.

Ce qui change aussi en réseau :

- **Statut « En Ligne »** : un propriétaire connecté sur un autre serveur est « En Ligne » dans la liste des warps et compté dans « Membres en ligne » ; la liste met en tête les propriétaires en ligne sur tout le réseau. Un joueur invisible n'est masqué que sur son propre serveur (l'invisibilité d'un joueur d'un autre serveur n'est pas connue).
- **`/is warps <pseudo>`** reconnaît un joueur qui n'a joué que sur un autre serveur (SuperiorSkyblock2 connaît tous les joueurs) et la complétion propose les joueurs de tout le réseau.
- **Liste des warps** : avec [SHSkyBridge](https://github.com/CryLegende/SHSkyBridge), chaque serveur connaît toutes les îles et tous leurs warps : la liste est celle du réseau entier sans rien lire d'autre.
- **Téléportation à un warp d'une île hébergée par un autre serveur** : le joueur est envoyé sur le serveur de l'île, à l'emplacement du warp, après les mêmes contrôles qu'avant (warp privé, île verrouillée, joueur banni). Le plugin ne passe pas par `Island#warpPlayer` pour ces îles : SuperiorSkyblock2 y vérifie que le bloc du warp est sûr dans la copie du monde de ce serveur, qui est périmée, et avec `delete-unsafe-warps: true` il supprimerait le warp pour tout le réseau. L'hôte d'une île est lu dans la table `island_host` de SHSkyBridge ; une île sans ligne est chez `reseau.hote-par-defaut`, ou, s'il est vide, chez `hote-par-defaut` de SHSkyBridge. Sans SHSkyBridge, c'est la téléportation de SuperiorSkyblock2.

`/swgadmin import` (permission `shssbwarpsgui.admin`) copie une fois `favorites.yml`, `warp_visits.yml` et `featured_warps.yml` dans la base, sans rien écraser (un favori déjà là reste, un compteur garde le plus grand total, une mise en avant l'échéance la plus tardive) ; elle peut être relancée. À faire à l'activation du mode réseau : les fichiers ne sont plus lus ensuite.

L'utilisateur MariaDB de `reseau.base-de-donnees` doit pouvoir créer les trois tables (ou elles existent déjà) et lire `island_host`.

## Dépendances

- Obligatoires : `SuperiorSkyblock2`, `Vault`
- Optionnelles : `PlaceholderAPI`, `ViaVersion`, `SHNetwork` (mode réseau), `SHSkyBridge` (warps d'îles hébergées par un autre serveur)

## Commandes

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/is_edit` | `/island_edit` | Editer un de vos warps. | `shssbwarpsgui.warp.edit` |
| `/swgadmin <reload\|import>` | `/shssbwarps` | Commandes d'administration pour SHSSBWarpsGUI : `reload`, et `import` (mode réseau : copie les fichiers dans la base). | `shssbwarpsgui.admin` |

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
└── <a href="src/main/resources/config.yml">config.yml</a>   (en-tête : résumé des commandes et des permissions)
    ├── messages, manage-warp-messages, main-gui, player-warps-gui, manage-warp-gui, featured-warps…   (inchangés)
    ├── <a href="src/main/resources/config.yml#L365">dialogues</a>
    │   ├── <a href="src/main/resources/config.yml#L366">actif</a>                 dialogues activés (false : chat comme avant)
    │   ├── <a href="src/main/resources/config.yml#L369">min-protocol</a>          version de protocole minimale (771 = 1.21.6)
    │   ├── <a href="src/main/resources/config.yml#L371">expiration</a>            validité d'une fenêtre, en secondes
    │   ├── <a href="src/main/resources/config.yml#L372">boutons</a>               textes Valider / Annuler
    │   ├── <a href="src/main/resources/config.yml#L375">renommer</a>              fenêtre du nom (titre, corps, champ, longueur-max)
    │   ├── <a href="src/main/resources/config.yml#L383">description</a>           fenêtre de la description (champ multiligne, hauteur, pre-remplir)
    │   ├── <a href="src/main/resources/config.yml#L396">suppression</a>           confirmation de suppression
    │   ├── <a href="src/main/resources/config.yml#L404">erreurs</a>               erreurs affichées en rouge dans la fenêtre
    │   └── <a href="src/main/resources/config.yml#L409">messages</a>              fenêtre expirée, erreur inattendue
    └── <a href="src/main/resources/config.yml#L425">reseau</a>
        ├── <a href="src/main/resources/config.yml#L426">actif</a>                 mode réseau (sans SHNetwork, rien n'est utilisé)
        ├── <a href="src/main/resources/config.yml#L429">attente-max-secondes</a>  attente de la sauvegarde du serveur de départ avant de relire les favoris
        ├── <a href="src/main/resources/config.yml#L431">rafraichissement-secondes</a>  relecture des visites et des mises en avant
        ├── <a href="src/main/resources/config.yml#L434">hote-par-defaut</a>       hôte des îles absentes de island_host (vide : celui de SHSkyBridge)
        ├── <a href="src/main/resources/config.yml#L437">base-de-donnees</a>       url, utilisateur, mot-de-passe, delai-ms
        └── <a href="src/main/resources/config.yml#L444">messages</a>              import et warp payant
</pre>

Les sections `dialogues` et `reseau` sont complètes dans le jar. Le plugin ne réécrit jamais le `config.yml` du serveur : toute clé absente du fichier (la section entière ou une seule ligne) est lue dans le `config.yml` embarqué dans le jar, sans rien y copier, et les fenêtres gardent donc leurs textes (titre, boutons, champ, erreurs). Pour personnaliser un texte ou un réglage, ajoutez la clé (ou toute la section) dans le `config.yml` du serveur : elle l'emporte sur celle du jar.

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
