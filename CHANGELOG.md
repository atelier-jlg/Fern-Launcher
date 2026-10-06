# Journal des versions

Chaque version installée sur le téléphone a une entrée ici.
Format : `vX.Y.Z (code N) — date`. Le numéro est défini dans `gradle.properties`.

## v0.21.2 (code 30) — 2026-10-06

- **« Faire de Fern l'écran d'accueil »** : en haut des Paramètres tant que Fern n'est pas le lanceur
  par défaut. Ouvre la demande d'Android (rôle « Accueil »), ou à défaut les réglages
  « Applications par défaut » (utile sur Xiaomi / HyperOS).

## v0.21.1 (code 29) — 2026-10-06

- **Widget Chat** : la tâche « Litière » est retirée (il ne reste que la Gamelle, 2 fois par jour).
  Tu peux toujours ajouter des tâches dans Paramètres → Le chat.

## v0.21.0 (code 28) — 2026-10-06

- **Pomodoro** : le bouton lancer / arrêter devient un bouton rond **à droite** (▶ pistache pour lancer,
  ■ pour arrêter, dessinés). Sous la durée : la durée de pause.
- **Réglages du Pomodoro depuis l'accueil** : en mode édition, un **engrenage** sur le widget ouvre
  les durées (travail 15 à 60 min, pause 5 à 20 min) et le mode Focus automatique.

## v0.20.0 (code 27) — 2026-10-06

- **Tiroir** : le bouton Réglages devient un petit **engrenage** dessiné, collé à droite.
  Le choix 4 / 5 colonnes quitte le tiroir et passe dans **Paramètres → Tiroir → Colonnes de la grille**
  (5 par défaut).
- **Textes de recherche** : « Recherche » dans le tiroir ; « Rechercher parmi les applis / sur le web »
  dans le panneau du haut (glisser vers le bas), sans le titre « Chercher ».

## v0.19.0 (code 26) — 2026-10-06

- **Packs « famille »** : en mode édition, **+ Famille** pose un pack qui montre les **4 applis les plus
  utilisées** d'une famille (Social, Argent & courses…), avec une pastille de sa couleur.
  Toucher une appli l'ouvre ; toucher la carte affiche **toute la famille** (« +8 » indique combien il en reste).
  Le pack se met à jour tout seul selon tes lancements et tes corrections de familles.

## v0.18.0 (code 25) — 2026-10-06

- **Rythme des tâches du chat** :
  - **Gamelle** : 2 fois par jour, la case se décoche toute seule à **12 h** et à **minuit**
    (et le cœur du chat disparaît jusqu'au prochain repas).
  - **Litière** : 1 fois par semaine, remise à zéro le **samedi à 18 h**.
  - Réglable dans Paramètres → Le chat → Rythme (2 fois / 1 fois par jour / 1 fois par semaine,
    jour et heure pour l'hebdomadaire). Le partage avec ta compagne suit les mêmes périodes.

## v0.17.2 (code 24) — 2026-10-06

- **La grille occupe tout l'écran** : les pages commencent juste sous la barre d'état
  (plus d'espace réservé au bandeau). Le réglage « Espace en haut de l'accueil » est retiré.

## v0.17.1 (code 23) — 2026-10-06

- **Widget Musique** : les boutons précédent / lecture-pause / suivant sont de vraies icônes dessinées
  (couleur du thème) au lieu de caractères qu'Android affichait en emoji orange.
- Plus d'emoji ailleurs non plus : « ⚙ » remplacé par « Réglages », « ☀ » retiré du widget Contexte.

## v0.17.0 (code 22) — 2026-10-06

- **Fond d'écran géré par le téléphone** : Fern affiche maintenant le fond d'écran d'Android
  (Réglages → Fond d'écran, ou Paramètres → Thème → Fond d'écran). Les images intégrées sont retirées.
- Le fond glisse doucement quand tu changes de page (effet de profondeur, si le fond s'y prête).
- **Espace en haut de l'accueil** réglable (Paramètres → Thème) : 0 à 35 % de l'écran,
  pour laisser voir le haut de ton fond (21 % par défaut).

## v0.16.3 (code 21) — 2026-10-06

- **Le chat sans carte ni texte** : il est posé directement sur le fond, comme un sticker, en plus grand.
  Restent seulement les pastilles des tâches (Gamelle, Litière) dessous.

## v0.16.2 (code 20) — 2026-10-06

- **Chat en plus haute définition** : environ 40 px de haut au lieu de 17. Vrais yeux verts en amande,
  marbrage écaille de tortue naturel, liseré caramel sur le nez, intérieur des oreilles rose,
  moustaches et petit bâillement le matin.
- Les sprites sont générés par `design/tools/chat_sprites.py` (Python) : formes simples → pixels.
  Pour retoucher le chat : modifier le script, puis `python3 design/tools/chat_sprites.py`.

## v0.16.1 (code 19) — 2026-10-06

- **Le chat ressemble au tien** : écaille de tortue (brun très foncé marbré de caramel),
  yeux verts, pattes claires, avec un fin contour pour bien ressortir sur la carte sombre.

## v0.16.0 (code 18) — 2026-10-06

- **Chat partagé** : Paramètres → Le chat → Partage. « Créer un partage » donne un code secret
  (fern-chat-…) à envoyer à ta compagne ; sur son Fern : « Rejoindre » + le code.
  Quand l'un coche « Gamelle », la case se coche chez l'autre (et le chat a son cœur des deux côtés).
  Dans les deux sens, aussi pour décocher. Passe par **ntfy.sh** (libre, sans compte) : rien dans les SMS.
  Fern relève les changements quand l'accueil est affiché (toutes les 30 s) ; sans réseau, ils partent plus tard.

## v0.15.0 (code 17) — 2026-10-06

- **Le chat nourri** : quand la 1re tâche (Gamelle) est cochée, il a une **bouille contente**
  (yeux en « ^ », joues roses, queue qui remue) et un **petit cœur** qui bat à côté de la tête.
- **Tes animations** : Paramètres → Le chat accepte des PNG, **GIF ou WebP animés**, pour 4 moments :
  la journée, le matin, la nuit, et « nourri, content ». Le pixel art reste net (pas de lissage).

## v0.14.0 (code 16) — 2026-10-06

Six nouveaux widgets (mode édition → + Widget). Ils sont en demi-largeur au départ, ⇔ pour passer en pleine largeur.

- **Cours du jour** : le cours en cours ou le prochain (heure, salle), tiré de l'agenda Android
  (Proton via ICSx⁵), et « EXAMEN DANS 12 J » repéré dans les titres (partiel, DS, examen…).
  Les semaines en entreprise, il affiche « Semaine VINCI · ESB dans 20 j ».
  Paramètres → Cours du jour : agendas à lire, mots qui signalent un examen.
- **Le chat** : en pixel art, il dort la nuit (avec des « z »), s'étire le matin et cligne des yeux
  la journée. Tâches du jour à cocher (gamelle, litière). Tu peux mettre tes propres PNG (jour / nuit).
- **Météo** en pixel art (pluie et neige animées) : température, min / max, demain.
  Via Open-Meteo, sans compte ni pistage. Aussi **sous l'horloge** (« MARDI 6 OCTOBRE · 14° PLUIE »),
  désactivable dans Paramètres → Lieu.
- **Plante** : une fougère qui grandit (1 à 7 frondes) avec les habitudes cochées dans le Carnet
  sur 7 jours, et qui pâlit si rien depuis 2 jours.
- **Pomodoro** : une crosse de fougère qui se déroule pendant 25 min ; le **mode Focus s'active**
  tout seul et revient comme avant à la pause. Notification à la fin du travail et de la pause.
- **Temps d'écran doux** : temps passé aujourd'hui sur les applis du mode Focus (ou toutes),
  une jauge par rapport à un repère, sans rouge ni alerte. À autoriser une fois
  (« Accès aux données d'utilisation »).
- CI : la Release (APK signé) est publiée automatiquement à chaque nouveau numéro de version.

## v0.13.0 (code 15) — 2026-10-06

- **Alternance · calendrier ESB 2026/27** : Paramètres → Alternance → « Charger le calendrier ESB 2026/27 »
  remplit toutes les périodes du calendrier officiel (Ingénieur année 1, version du 16/06/2026).
- Nouveau type de période **Mission à l'international** (31/05 → 30/07/2027), en crème dans la frise,
  nom modifiable.

## v0.12.0 (code 14) — 2026-10-06

- **Taille des widgets Android** : en mode édition, bouton **⤢ Taille** sur un widget d'une autre appli.
  Fern propose les tailles en cases (largeur × hauteur : 1×1, 2×1, 2×2, 4×2…) **acceptées par le
  widget** (taille minimale, redimensionnable ou non). Le widget est prévenu et s'adapte.
- APK : désormais publié aussi en **Release GitHub** (fichier .apk direct, sans zip).
- CI : cache des dépendances activé sur la branche (moins de pannes de téléchargement).

## v0.11.0 (code 13) — 2026-10-06

Les trois widgets qui manquaient (+ Widget en mode édition).

- **Alternance** : où tu es cette semaine (ESB ou VINCI), « VINCI dans 5 j », et la frise des
  prochaines semaines (pistache = école, rose = entreprise, semaine en cours entourée).
  Paramètres → Alternance : noms, périodes une par une, ou **générer un rythme**
  (ex. 2 semaines d'école / 3 en entreprise, du … au …).
- **Révisions** : nombre de cartes AnkiDroid à revoir aujourd'hui ; le **lotus s'ouvre**
  quand tout est fait. Toucher ouvre AnkiDroid. Autorisation demandée une fois.
- **Carnet du jour** : l'humeur de la **graine à la fleur** (toucher la plante pour la faire grandir),
  **3 habitudes** à cocher, une **note** (toucher pour écrire) et « **→ Obsidian** » qui l'ajoute
  à la note du jour (Carnet/2026-10-06) avec l'humeur et les habitudes.
  Paramètres → Carnet du jour : noms des habitudes, coffre et dossier Obsidian.
- Les trois passent de demi à pleine largeur avec ⇔.

## v0.10.0 (code 12) — 2026-10-06

Placement plus libre.

- **Lignes de 4 colonnes** : chaque élément prend 1 (quart), 2 (demi) ou 4 colonnes (ligne entière),
  et les éléments se suivent sur la même ligne tant qu'il reste de la place.
- **+ Espace** ajoute une **ligne vide** : **⇔** change sa largeur (entière → demi → quart),
  **↕** sa hauteur (40 → 80 → 120 → 200 dp). Invisible hors mode édition.
  Exemple : horloge, puis une ligne vide, puis ta rangée d'applis.
- **+ Appli** : une appli seule (un quart de largeur), à placer où tu veux, à côté d'autres applis,
  d'un pack ou d'un widget. Le choix de l'appli s'ouvre aussitôt.
- Les éléments étroits ont un menu **⋯** (avant, après, largeur, hauteur, supprimer).

## v0.9.0 (code 11) — 2026-10-06

Côte à côte et stickers.

- **Demi-largeur (2×2)** : deux éléments « petits » se placent côte à côte, dans n'importe
  quelle combinaison — pack + pack, pack + widget, widget + widget…
  - Les packs sont toujours en demi-largeur.
  - Les widgets (Ciel, Musique, Contexte, widgets Android) passent de pleine à demi-largeur
    avec le bouton **⇔** en mode édition. Ciel et Musique ont une version compacte.
  - **+ Espace** : une demi-place vide, pour aérer ou poser un sticker à côté d'un pack.
- **Stickers en placement libre** (+ Sticker en mode édition) : choisis un PNG dans ta galerie
  ou tes fichiers. En mode édition : **un doigt pour déplacer, deux doigts pour agrandir
  et tourner**, ✕ pour retirer. Hors mode édition, ils ne gênent pas les touches.
- Note : les images des stickers ne sont pas incluses dans la sauvegarde (fichier JSON) ;
  seules leurs positions le sont.

## v0.8.0 (code 10) — 2026-10-06

Icônes et familles.

- **Plaques Fern** (style par défaut) : chaque icône devient une plaque ronde de la couleur
  de sa famille, avec le picto à 70 % recoloré (guide Renkin). Les pictos viennent
  d'**Arcticons** s'il est installé ; sinon de l'icône monochrome d'Android ; sinon l'initiale.
  Les plaques **suivent le thème** (et le thème de chaque Space).
- **Familles** : premier classement automatique (mots-clés + catégorie Android),
  à corriger par **appui long dans le tiroir → Famille…** (ou « Automatique »).
- **Tiroir par familles** : la pastille de tri passe par A–Z → **Familles** → Fréquence.
  « Admin & sécurité » reste discret, en dernier.
- **Paramètres → Icônes** : Icônes d'origine / **Pack d'icônes installé** (ton pack Renkin,
  ou n'importe quel pack ADW/Nova) / Plaques Fern ; choix du pack et de la source des pictos.
- **Paramètres → Thème** : les 8 couleurs de plaque et de picto des familles sont réglables.

## v0.7.1 (code 9) — 2026-10-06

- Les titres de pages (Journée, Pratique…) ne sont plus affichés sur l'accueil.
  Ils restent visibles (et modifiables) en mode édition.

## v0.7.0 (code 8) — 2026-10-06

Les widgets.

- **+ Widget** en mode édition (sous les boutons d'une page) :
  - **Ciel** : le jour, l'arc du soleil avec lever ↑ et coucher ↓ ; la nuit, la phase de la lune
    (toucher ouvre Stellarium s'il est installé). Calculé sur le téléphone, sans internet ni GPS ;
    la ville se règle dans Paramètres → Lieu (Nantes par défaut).
  - **Musique** : titre, artiste, pochette **en deux tons carmin et rose**, boutons ⏮ ⏯ ⏭.
    Il faut autoriser une fois Fern dans « Accès aux notifications » (bouton dans le widget).
  - **Contexte** : le moment de la journée, le prochain événement de l'agenda (si autorisé),
    le coucher du soleil le soir, et une **note du jour** (toucher pour l'écrire).
  - **Widget d'une autre appli** : tous les widgets Android installés. Android demande une
    autorisation la première fois, puis l'appli peut proposer de le configurer.
    En mode édition, **↕** change sa hauteur.
- Paramètres → **Lieu (ciel)** : ville, latitude, longitude.

## v0.6.0 (code 7) — 2026-10-06

Spaces et mode Focus.

- **Spaces** (Paramètres → Spaces) : plusieurs jeux de pages + dock, par exemple « Perso » et « Travail ».
  Créer un Space vide ou une copie de l'actuel, renommer, supprimer, **thème propre à chaque Space**.
  Quand il y en a plusieurs, leur nom s'affiche au-dessus du dock : toucher pour passer au suivant.
- **Planning** : bascule automatique selon le jour et l'heure (ex. Travail lun–ven 8:00–18:00).
  Fern ne rebascule qu'au changement de créneau : un changement manuel est respecté jusque-là.
- **Mode Focus** (Paramètres → Focus) : les applis choisies disparaissent de l'accueil,
  du tiroir et de la recherche tant qu'il est actif. À mettre sur un geste pour l'activer d'un coup.
- Nouvelles actions de geste : « Passer au Space suivant », « Activer / couper le mode Focus ».
- Note : l'éditeur de couleurs modifie le thème général ; un Space avec son propre thème
  garde le sien.

## v0.5.0 (code 6) — 2026-10-06

Gestes, Paramètres et thèmes.

- **Onglet Paramètres** (bouton ⚙ en mode édition, ou « ⚙ Réglages » en haut du tiroir) :
  - **Thème** : modifier chaque couleur (curseurs ou code #RRGGBB), thèmes prêts
    (Estampe nuit, Nuit teal = la proposition vert-bleu à tester, Sous-bois),
    **Enregistrer**, **Exporter** (fichier à envoyer à un proche) et **Importer**.
  - **Gestes** : choisir l'action de glisser ↑ / ↓, double appui, appui long
    (tiroir, recherche, notifications, réglages rapides, verrouiller, mode édition,
    roue d'applis, ouvrir une appli précise).
  - **Recherche** : recherche étendue (contacts, agenda, raccourcis, calcul), moteur web.
  - **Tiroir** : affichage, tri, ouverture directe.
  - **Sauvegarde** : exporter / restaurer toute la configuration dans un fichier.
  - **À propos** : version, choisir l'écran d'accueil par défaut.
- **Double appui = verrouiller l'écran** (par défaut). Il faut activer une fois
  « Fern Launcher » dans Paramètres Android → Accessibilité (Fern ne lit rien à l'écran).
- **Roue d'applis** : jusqu'à 8 applis en cercle autour du doigt, à attribuer à un geste.

## v0.4.0 (code 5) — 2026-10-06

La recherche, comme demandé.

- **Glisser vers le bas** : un panneau de recherche **descend du haut**, barre en haut, clavier ouvert.
  Glisser vers le haut sur la barre (ou Retour / Accueil) le referme.
- **Résultats** : tes **applis d'abord**, puis **« Chercher « … » avec Firefox »**.
  La recherche web s'ouvre comme un lien ordinaire dans Firefox : ton réglage
  « liens externes en navigation privée » s'applique. Moteur : DuckDuckGo (modifiable plus tard).
  Si Firefox n'est pas installé, le navigateur par défaut prend le relais.
- **Entrée** : ouvre la première appli, ou lance la recherche web s'il n'y a pas d'appli.
- La barre de recherche du **tiroir** (glisser vers le haut) fonctionne pareil, résultats près du pouce.
- **Recherche étendue** (désactivée par défaut, réglable dans les futurs Paramètres) :
  contacts (appeler / SMS), agenda (dont Proton via ICSx⁵), raccourcis d'applis, calculatrice.

## v0.3.0 (code 4) — 2026-10-06

Le tiroir version complète.

- **Pastilles en haut** : tri **A–Z** ou **Fréquence** (les applis que tu lances le plus d'abord),
  affichage **Grille** ou **Liste**, **4 ou 5 colonnes**.
- **Sections par lettre** (A, B, C…) en tri alphabétique, avec la **barre A–Z** à droite :
  toucher ou glisser sur une lettre pour y sauter.
- **Applis masquées** : bouton en bas du tiroir pour les voir et les faire réapparaître.
- Appui long en mode liste : même menu qu'en grille.

## v0.2.0 (code 3) — 2026-10-06

L'accueil prend forme, et tout se règle dans l'appli.

- **Pages** : glisser à gauche / à droite. Disposition de départ reprise de la charte :
  Accueil (horloge + favoris), Journée, Pratique, Les miens. Les applis sont retrouvées
  par leur nom ; celles qui ne sont pas installées laissent un emplacement vide.
- **Packs** : cartes arrondies de 4 applis en 2×2, deux par ligne.
- **Dock** : 4 applis + bouton du tiroir. Préréglé avec Téléphone, SMS, Signal, Appareil photo.
- **Bandeau** : 29 % de hauteur sur l'accueil, 21 % sur les autres pages, en fondu en changeant de page.
- **Mode édition** (appui long n'importe où sur l'accueil) :
  - ajouter / renommer / déplacer / supprimer des pages ;
  - ajouter des packs, rangées d'applis, horloges ; les renommer, monter, descendre, supprimer ;
  - toucher un emplacement pour choisir l'appli (ou la retirer).
- **Tiroir**, appui long sur une appli : « Ajouter à… » (premier emplacement libre d'un pack ou du dock),
  « Renommer », « Masquer ».
- Tout est enregistré sur le téléphone (`fern-config.json`) et survit aux mises à jour.
- Bouton Accueil : ferme ce qui est ouvert, puis revient à la première page.

## v0.1.1 (code 2) — 2026-10-06

- Corrigé : liseré clair en haut de l'écran (2 lignes de pixels gris clair laissées
  par l'export du bandeau pixel art, + bord gauche semi-transparent).

## v0.1.0 (code 1) — 2026-10-06 · testée sur le Fairphone 6 ✅

Première version : le socle du lanceur.

- Fern peut être choisi comme écran d'accueil.
- Accueil : fond topographique + bandeau pixel art, horloge (minutes en rose) et date.
  Toucher l'horloge ouvre les alarmes.
- Glisser vers le haut : tiroir de toutes les applis (grille de 4, ordre alphabétique).
- Glisser vers le bas : tiroir avec la recherche déjà ouverte.
- Recherche sans accents ni majuscules ; « Entrée » ouvre le premier résultat.
- Appui long sur une appli : infos de l'appli, désinstaller.
- Liste tenue à jour automatiquement (installation, désinstallation, mise à jour, profil travail).
- Fermer le tiroir : Retour, Accueil, ou tirer la liste vers le bas.
