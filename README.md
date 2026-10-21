<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHSSBWarpsGUI

*Plugin du serveur SkyHand.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Plugin du serveur SkyHand.

Utilisé sur : **Opale**.

## Dépendances

- Obligatoires : `SuperiorSkyblock2`, `Vault`
- Optionnelles : `PlaceholderAPI`

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

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`) :

- `config.yml`

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
