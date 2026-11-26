<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHIslandJake

*Jake, le PNJ de chaque île : il parle, réagit à ce que fait le joueur, et un clic lance une commande (le guide sur Opale).*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Chaque île SuperiorSkyblock2 a son Jake, posé près du centre à la création de l'île, avec son nom au-dessus de lui. Un membre de l'île qui clique sur Jake lance la commande `clic.commande` (le guide, SHGuide, sur Opale) ; accroupi, il ouvre les réglages de Jake. Un visiteur reçoit un message.

### Le corps de Jake

Le corps vient de l'apparence de Jake (`types`) :

- **un modèle de BlockDisplayCreator** (`modele`, `taille`) : rien n'est écrit dans le monde, le moteur le montre aux joueurs proches, avec son regard, son sommeil et ses animations. Une zone cliquable (entité « interaction » envoyée en paquets) prend les clics, à la taille du dessin ou de `zone-clic` ;
- **un mob dessiné par FancyNpcs** (`mob`), le corps de secours : il se tourne vers chaque joueur qui s'approche (`affichage.regarder-joueurs`), et seulement pour lui.

Si le modèle d'une apparence manque (BlockDisplayCreator absent, fichier introuvable), Jake est dessiné avec l'apparence `apparences.secours` et un avertissement est écrit en console, une fois par apparence : il n'y a jamais d'île sans Jake. Un modèle que le moteur connaît mais n'a pas encore lu (premier dessin demandé depuis une commande) est redemandé au tick suivant, sans passer par le secours. Un Jake resté en secours réessaie son modèle toutes les `apparences.reessai-modele-secondes`, et tous reprennent leur modèle quand BlockDisplayCreator revient. Le nom se pose au-dessus du dessin du modèle (`apparences.marge-texte`), la zone cliquable à sa largeur (au plus `apparences.zone-clic.largeur-max`). `apparences.remplacements` fait passer toutes les îles d'une apparence à une autre (« VILLAGER: AVENTURIER »), au démarrage et au rechargement.

### Ce que Jake dit

Au-dessus de Jake, envoyés en paquets à chaque joueur (packetevents) :

- **le nom** (`hologramme` de `messages.yml`) ;
- au-dessus, **la parole** : la ligne d'alerte de ce joueur (`alerte`, un membre de l'île seulement, quand `alerte.placeholder` vaut `alerte.valeur` pour lui), ou **une bulle** quand Jake lui parle. La bulle apparaît en grossissant, s'écrit mot par mot (`bulle.ticks-par-mot`), reste le temps de la lire (`bulle.duree-base-secondes` + `bulle.duree-par-caractere` par caractère) puis disparaît ; l'alerte revient ensuite. Une seule bulle par joueur : une nouvelle remplace l'ancienne.

Fond transparent, ombre du texte et orientation `VERTICAL` par défaut (`hologramme`, `bulle`).

### Les réactions

Une réaction (`reactions` de `config.yml`) est vue par le seul joueur concerné : une animation du modèle, une réplique tirée au hasard dans une liste de `messages.yml` (jamais deux fois de suite la même, selon le caractère de l'apparence), un son, des particules. Elle se lance :

- **par commande**, depuis un autre plugin, un menu, une quête : `/shislandjake react <joueur> <réaction> [texte]` ;
- **à l'arrivée du joueur près de Jake** (`approche: true`, `reactions.distance`) : la réaction possible de plus haute priorité gagne, une seule par arrivée.

Conditions (`si`, placeholders lus pour le joueur, seulement à l'arrivée et pour une commande), public (`pour` : membres, visiteurs, tous) et pause par joueur (`pause-secondes`). Une réaction demandée loin de tout Jake (`reactions.portee-commande`) est abandonnée sans un mot. Un joueur qui a un menu ouvert attend sa fermeture. Un son n'est joué qu'avec un geste, une bulle ou des particules.

### La vie et les scènes d'ambiance

Un personnage n'a aucune boucle permanente du moteur : loin des regards il est immobile (aucun paquet). Pour le joueur qui le regarde, il vit par des animations de vie et de courtes scènes (`types.<ID>.scenes`, animations « par commande » de son modèle). Une scène est jouée pour un joueur seul quand il est à moins de `ambiance.distance` blocs d'un Jake dessiné par un modèle, qu'il a Jake devant lui (`ambiance.angle-vue`), qu'il n'est pas discret et que rien ne se passe pour lui (bulle, réaction en cours ou en attente). Une scène au hasard, jamais deux fois de suite la même, puis une pause tirée entre `ambiance.pause-min-secondes` et `ambiance.pause-max-secondes`. Une réaction passe devant une scène ; une scène n'interrompt rien. La durée d'une animation vient du moteur s'il sait la donner, sinon de `ambiance.durees`, sinon de `ambiance.duree-defaut-secondes`.

Les animations de vie (`types.<ID>.vie` : respirer, petits mouvements, qui partent de la pose de repos et y reviennent) s'enchaînent pour ce joueur sans trou tant qu'il a Jake devant lui, à moins de `ambiance.distance` blocs : à la fin exacte de l'une, la suivante part au même tick (une tâche planifiée à la fin de chaque animation, pour les seuls joueurs qui regardent un Jake ; le test « de face » reste dans le passage du suivi). Tirées au hasard, jamais deux fois de suite la même ; seule, elle se répète. Une scène ou le geste d'une réaction la remplace, la vie reprend à sa fin ; la pause entre deux scènes compte pendant la vie. Quand le joueur se détourne ou s'éloigne, l'animation en cours finit et rien n'est relancé ; ça repart quand il regarde de nouveau Jake. `ambiance.vie-actif` l'éteint ; une animation de vie absente du modèle est signalée en console, une fois.

Chaque joueur peut rendre Jake **discret** (`/jake discret`, bouton des réglages) : ni bulle, ni geste, ni scène, ni son ; l'alerte reste, et la vie aussi (`ambiance.vie-si-discret`).

### Les apparences

Réglages de Jake (`/jake reglages`, accroupi + clic, ou `/jake` quand `jake-sans-argument` est vide) :

| Bouton | Effet |
|---|---|
| Apparence | Menu Apparence (`/jake type`), voir plus bas. |
| Visibilité | Cache Jake (et ses textes) à tout le monde, ou le remontre. |
| Position | Le prochain clic sur un bloc de son île y pose Jake, tourné vers le joueur. |
| Parole | Jake discret, ou qui parle, pour ce joueur seul. |
| Retour | Commande `menu.principal.retour-commande` (le guide sur Opale), ou celle de `menu.principal.retours` selon d'où vient le joueur (`/jake reglages <origine>`). |

Le menu Apparence suit la charte des menus de SkyHand (mêmes vitres et même tête que les réglages) : une carte par apparence proposée, dans l'ordre de `types`, centrée dans les lignes du milieu (cinq au plus par ligne), avec une page de plus seulement si les cartes ne tiennent pas. Chaque carte montre l'icône de l'apparence (`types.<ID>.icone` : matériau, `hdb-…` ou `basehead-…`), son nom et sa phrase de caractère (`types.<ID>.description` de `messages.yml`), et son état : actuelle (marquée), utilisable, pas encore obtenue (avec `types.<ID>.obtention-texte` et, si `obtention-valeur` est donnée, où en est le joueur : « niveau 4 / 10 »), ou obtenue mais réservée aux grades VIP et plus. L'apparence de secours nommée dans `apparences.secours` et les apparences remplacées ne sont jamais montrées.

Les apparences s'obtiennent en jouant et ne s'utilisent qu'avec VIP et plus : `types.<ID>.obtention` est une liste de conditions lues pour le joueur (écrites comme les `si` des réactions), au dessin du menu et au moment du choix, avec `types.<ID>.permission` en plus si elle est donnée. Une apparence qui s'obtient demande aussi le droit `apparences.permission-usage` (`shislandjake.personnages`, donné aux grades VIP et plus). Une apparence sans obtention (l'Aventurier) est à tout le monde. Une apparence déjà choisie n'est jamais retirée. Un Jake dont l'apparence n'est plus dans `types`, ou qui a l'apparence de secours, est dessiné avec `jake.type`.

### Performances

Le plugin garde lui-même ses Jake (`jakes.yml`) ; ils ne sont dessinés que là où un joueur passe. Le suivi ne regarde que la grille autour de chaque joueur, toutes les `affichage.intervalle-ticks` ticks : le nombre d'îles n'entre pas en compte, et ce qu'un joueur a reçu n'est renvoyé que s'il change. Un joueur qui n'a pas bougé de plus de `affichage.mouvement-minimum` bloc, pour qui aucun Jake n'a changé et dont l'alerte n'est pas à relire est sauté (seule son ambiance est regardée). Le moteur n'est interrogé sur un modèle montré que toutes les `affichage.verification-modele-secondes` et à l'arrivée d'un joueur. L'état « près de Jake » survit à un rechargement ou à un Jake redessiné : seule une vraie arrivée déclenche une réaction. Les placeholders ne sont lus qu'à l'arrivée, pour une commande et pour l'alerte (une fois par `alerte.verification-ticks`). Un joueur immobile ne coûte rien au regard du corps de secours. Les paquets d'un joueur partent ensemble ; tout ce qui a été envoyé est détruit au départ du joueur, au changement de monde, à la réapparition, au retrait du Jake et à l'arrêt du plugin. Avec les mondes en parallèle de DivineMC, tout passe par le fil principal. `/shislandjake stats` donne la durée des tâches sur la dernière minute.

Remplace IslandNPC : au premier démarrage, les PNJ de `plugins/IslandNPC/database.db` sont repris (type, visibilité, monde, position). Les anciennes commandes `/islandnpc`, `/isnpc` et leurs sous-commandes `location`, `visibility`, `mob` restent acceptées.

Utilisé sur : **Opale**.

## Dépendances

Requises : `SuperiorSkyblock2`, `FancyNpcs` (corps de secours), `packetevents` (textes, bulles, zone cliquable).
Facultatives : `BlockDisplayCreator` (corps « modèle » ; sans lui, le corps de secours), `PlaceholderAPI` (alerte, conditions, placeholders), `HeadDatabase` (tête de Jake `hdb-<id>` dans les menus).

## Commandes

| Commande | Permission | Effet |
|---|---|---|
| `/jake` (`/jack`, `/islandnpc`, `/isnpc`, `/is jake`, `/is npc`) | `shislandjake.use` (tous) | La commande `jake-sans-argument.commande`, ou les réglages de Jake si elle est vide |
| `/jake reglages [origine]` | `shislandjake.use` (tous) | Réglages de Jake |
| `/jake <autre chose>` | | La commande `jake-argument-inconnu.commande` (`{arguments}` = ce que le joueur a tapé), ou la liste des commandes si elle est vide |
| `/jake deplacer` | `shislandjake.deplacer` (tous) | Clic sur un bloc de son île pour y placer Jake |
| `/jake visibilite` | `shislandjake.visibilite` (tous) | Cache ou montre Jake |
| `/jake type [apparence]` | `shislandjake.use` + droits de l'apparence | Choix de l'apparence |
| `/jake discret` | `shislandjake.use` (tous) | Jake discret pour soi, ou qui parle de nouveau |
| `/shislandjake reload` | `shislandjake.admin.reload` | Recharge `config.yml` (réactions comprises) et `messages.yml` ; un fichier invalide est refusé et nommé |
| `/shislandjake info [joueur]` | `shislandjake.admin.info` | Nombre de Jake, ou le Jake de l'île d'un joueur (et ce qui le dessine) |
| `/shislandjake delete [joueur]` | `shislandjake.admin.delete` | Retire le Jake de l'île où l'on est (ou d'un joueur) ; il revient au prochain `/jake` |
| `/shislandjake import` | `shislandjake.admin.import` | Reprend les PNJ d'IslandNPC des îles sans Jake |
| `/shislandjake stats` | `shislandjake.admin.stats` | Durée des tâches de Jake sur la dernière minute (suivi, regard, réactions, bulles ; le temps des réactions d'approche et des scènes est compté aussi dans « suivi ») |
| `/shislandjake react <joueur> <réaction> [texte]` | `shislandjake.admin.react` | Joue une réaction pour ce joueur ; le texte remplace la réplique. Ne répond rien, qu'elle joue ou soit abandonnée |
| `/shislandjake say <joueur> <texte>` | `shislandjake.admin.say` | Jake dit ce texte dans une bulle, à ce joueur seul |

Le texte donné à `react` ou `say` est pris tel quel (l'appelant lit ses placeholders) ; seuls `{joueur}`, `{nom}` et `{chef}` y sont remplacés. Les répliques de `messages.yml` passent une fois par PlaceholderAPI, avant ces jetons : ce qui vient d'un joueur n'est jamais relu.

`shislandjake.admin` donne toutes les sous-commandes de `/shislandjake`, `shislandjake.bypass` (cliquer sur le Jake de n'importe quelle île) et `shislandjake.type.*`. Les droits des apparences sont `op` par défaut : les joueurs les reçoivent par leur groupe. `shislandjake.personnages` est le droit d'usage de `apparences.permission-usage`, donné aux grades VIP et plus.

## Placeholders

`%shislandjake_type%`, `%shislandjake_type_nom%`, `%shislandjake_nom%`, `%shislandjake_visible%` : le Jake de l'île du joueur. `%shislandjake_discret%` : `oui` si le joueur a rendu Jake discret.

## Configuration

Cliquez sur un fichier pour l'ouvrir (valeurs d'exemple) :

<pre>
plugins/SHIslandJake/
├── <a href="src/main/resources/config.yml">config.yml</a>     place de départ, affichage, textes et bulles, clic, réactions, ambiance, apparences, menus, sons
├── <a href="src/main/resources/messages.yml">messages.yml</a>   messages du chat, nom et alerte, format des bulles, répliques, menus, noms des apparences
├── <a href="src/main/resources/LISEZ-MOI.txt">LISEZ-MOI.txt</a>  guide du staff, réécrit à chaque démarrage : ajouter un personnage, une réaction, faire réagir Jake, basculer les îles
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
