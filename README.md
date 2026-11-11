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

`/shssbwarpsgui import` (permission `shssbwarpsgui.admin.import`) copie une fois `favorites.yml`, `warp_visits.yml` et `featured_warps.yml` dans la base, sans rien écraser (un favori déjà là reste, un compteur garde le plus grand total, une mise en avant l'échéance la plus tardive) ; elle peut être relancée. À faire à l'activation du mode réseau : les fichiers ne sont plus lus ensuite.

L'utilisateur MariaDB de `reseau.base-de-donnees` doit pouvoir créer les trois tables (ou elles existent déjà) et lire `island_host`.

## Dépendances

- Obligatoires : `SuperiorSkyblock2`, `Vault`
- Optionnelles : `PlaceholderAPI`, `ViaVersion`, `SHNetwork` (mode réseau), `SHSkyBridge` (warps d'îles hébergées par un autre serveur)

## Commandes

Commandes joueur : ce sont des sous-commandes de `/is` de SuperiorSkyblock2, interceptées par le plugin.

| Commande | Description | Permission |
|---|---|---|
| `/is warps [joueur]` | Liste des warps de tous les joueurs, ou de ceux d'un joueur. | `shssbwarpsgui.use`, `shssbwarpsgui.warps.list.other` |
| `/is warp [nom]` | Warps de votre île, ou téléportation à l'un d'eux. | `shssbwarpsgui.warp.self`, `shssbwarpsgui.warp.teleport` |
| `/is editwarp <warp> <confirmicon\|rename>` | Confirme l'icône (objet en main) ou renomme un warp. | `shssbwarpsgui.warp.edit` |

Administration :

| Commande | Alias | Description | Permission |
|---|---|---|---|
| `/shssbwarpsgui reload` | `/swgadmin`, `/shssbwarps` | Recharge `config.yml` et `messages.yml` ; si un fichier est illisible, l'ancienne configuration reste active. Ne touche ni aux favoris, ni aux visites, ni aux warps mis en avant. La section `reseau` demande un redémarrage. | `shssbwarpsgui.admin.reload` |
| `/shssbwarpsgui import` | idem | Avec SHNetwork : copie les fichiers dans la base du réseau. | `shssbwarpsgui.admin.import` |
| `/shssbwarpsgui featured` | idem | Liste les warps mis en avant avec leur propriétaire et leur échéance. | `shssbwarpsgui.admin.featured` |

Les sous-commandes d'administration marchent aussi depuis la console.

## Permissions

| Permission | Description | Par défaut |
|---|---|---|
| `shssbwarpsgui.use` | Ouvrir le menu des warps avec `/is warps`. | `true` |
| `shssbwarpsgui.warps.list.all` | Voir la liste de tous les warps avec `/is warps`. | `true` |
| `shssbwarpsgui.warps.list.other` | Voir les warps d'un autre joueur avec `/is warps <pseudo>`. | `true` |
| `shssbwarpsgui.warp.self` | Voir ses propres warps avec `/is warp`. | `true` |
| `shssbwarpsgui.warp.teleport` | Se téléporter à ses propres warps avec `/is warp <nom>`. | `true` |
| `shssbwarpsgui.warp.edit` | Éditer ses warps avec `/is editwarp`. | `true` |
| `shssbwarpsgui.admin` | Toutes les sous-commandes de `/shssbwarpsgui` (parent de `admin.reload`, `admin.import`, `admin.featured`). | `op` |
| `shssbwarpsgui.admin.reload` `.import` `.featured` | Une sous-commande de `/shssbwarpsgui`. | `op` |
| `shssbwarpsgui.admin.editothers` | Éditer les warps d'autres joueurs (menu de gestion). | `op` |
| `shssbwarpsgui.admin.featureothers` | Mettre en avant ou retirer la mise en avant du warp d'un autre joueur. | `op` |

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHSSBWarpsGUI/
├── <a href="src/main/resources/config.yml">config.yml</a>     résumé des commandes et des permissions, menus, mise en avant, favoris, compteur de visites, dialogues (réglages), skins, mode réseau
└── <a href="src/main/resources/messages.yml">messages.yml</a>   tous les textes : messages, gestion d'un warp, favoris, mise en avant, confirmations, textes des dialogues
</pre>

La section `reseau` (mode réseau, base de données, délais) ne sert qu'avec SHNetwork et demande un redémarrage ; ses textes (import, warp payant) restent dans `config.yml`.

Le plugin ne réécrit jamais le `config.yml` ni le `messages.yml` du serveur : toute clé absente d'un fichier (la section entière ou une seule ligne) est lue dans le fichier embarqué dans le jar, sans rien y copier, et les fenêtres gardent donc leurs textes (titre, boutons, champ, erreurs). Pour personnaliser un texte ou un réglage, ajoutez la clé (ou toute la section) dans le fichier du serveur : elle l'emporte sur celle du jar. Au premier démarrage, `messages.yml` est créé avec les textes des anciennes sections de `config.yml` quand elles existent.

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
