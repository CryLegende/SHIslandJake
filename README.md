<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHIslandJake

*Jake, le PNJ de chaque île : un clic ouvre le menu de l'île, ses paramètres se règlent depuis /jake.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Chaque île SuperiorSkyblock2 a son Jake, posé près du centre à la création de l'île, avec un hologramme au-dessus de lui. Un membre de l'île qui clique sur Jake lance la commande `clic.commande` (le menu de l'île sur Opale) ; accroupi, il ouvre les paramètres de Jake. Un visiteur reçoit un message.

Paramètres de Jake (`/jake`, `/is jake`, accroupi + clic) :

| Bouton | Effet |
|---|---|
| Type | Choix parmi les types de `types` (villageois, évocateur, pillard, vindicateur, marchand, sorcière). Chaque type peut demander une permission. Sorcière : Jake s'appelle Jakie. |
| Visibilité | Cache Jake (et son hologramme) à tout le monde, ou le remontre. |
| Position | Le prochain clic sur un bloc de son île y pose Jake, tourné vers le joueur. |
| Retour | Commande `menu.principal.retour-commande` (le menu de l'île). |

Remplace IslandNPC : au premier démarrage, les PNJ de `plugins/IslandNPC/database.db` sont repris (type, visibilité, monde, position ; IslandNPC ne gardait pas d'orientation par PNJ). Les anciennes commandes `/islandnpc`, `/isnpc` et leurs sous-commandes `location`, `visibility`, `mob` restent acceptées.

Le plugin garde lui-même ses Jake (`jakes.yml`) ; FancyNpcs ne fait que les dessiner. Ils ne sont ni dans `/npc list` ni dans son `npcs.yml`, et ne sont créés que là où un joueur passe : le suivi ne regarde que la grille autour de chaque joueur, le nombre d'îles n'entre pas en compte. Un Jake que personne n'a vu depuis `affichage.oubli-minutes` est rendu à la mémoire. Avec les mondes en parallèle de DivineMC, tout passe par le fil principal.

Quand un joueur s'approche, Jake se tourne vers lui (`affichage.regarder-joueurs`) : tête, corps et inclinaison, jusqu'à `affichage.distance-regard` blocs, à la vitesse `affichage.vitesse-regard` pour que le mouvement reste fluide. Chaque joueur ne voit Jake tourné que vers lui-même, et Jake reprend son orientation d'origine quand le joueur s'éloigne. Seuls les Jake à moins de `distance-regard` + 4 blocs d'un joueur sont suivis, toutes les `intervalle-regard-ticks` ticks, et une rotation n'est envoyée que si l'angle change d'au moins `angle-regard-minimum` degrés : un joueur immobile ou loin de tout Jake ne coûte aucun paquet.

Utilisé sur : **Opale**.

## Dépendances

Requises : `SuperiorSkyblock2`, `FancyNpcs`.
Facultatives : `FancyHolograms` (hologramme au-dessus de Jake ; sans lui, la première ligne devient le nom du PNJ), `PlaceholderAPI`.

## Commandes

| Commande | Permission | Effet |
|---|---|---|
| `/jake` (`/jack`, `/islandnpc`, `/isnpc`, `/is jake`, `/is npc`) | `shislandjake.use` (tous) | Paramètres de Jake |
| `/jake deplacer` | `shislandjake.deplacer` (tous) | Clic sur un bloc de son île pour y placer Jake |
| `/jake visibilite` | `shislandjake.visibilite` (tous) | Cache ou montre Jake |
| `/jake type [type]` | `shislandjake.use` + `shislandjake.type.<type>` | Choix du type |
| `/shislandjake reload` | `shislandjake.admin.reload` | Recharge `config.yml` et `messages.yml` |
| `/shislandjake info [joueur]` | `shislandjake.admin.info` | Nombre de Jake, ou le Jake de l'île d'un joueur |
| `/shislandjake delete [joueur]` | `shislandjake.admin.delete` | Retire le Jake de l'île où l'on est (ou d'un joueur) ; il revient au prochain `/jake` |
| `/shislandjake import` | `shislandjake.admin.import` | Reprend les PNJ d'IslandNPC des îles sans Jake |

`shislandjake.admin` donne toutes les sous-commandes de `/shislandjake`, `shislandjake.bypass` (cliquer sur le Jake de n'importe quelle île) et `shislandjake.type.*`. `shislandjake.type.*` donne les six types de la configuration par défaut. Les permissions de type sont `op` par défaut : les joueurs les reçoivent par leur groupe.

## Placeholders

`%shislandjake_type%`, `%shislandjake_type_nom%`, `%shislandjake_nom%`, `%shislandjake_visible%` : le Jake de l'île du joueur.

## Configuration

Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHIslandJake/
├── <a href="src/main/resources/config.yml">config.yml</a>     place de départ, affichage (dont le regard de Jake), hologramme, clic, types, menus, sons
├── <a href="src/main/resources/messages.yml">messages.yml</a>   messages du chat, hologramme, menus, noms des types
└── jakes.yml       le Jake de chaque île (écrit par le plugin)
</pre>

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
