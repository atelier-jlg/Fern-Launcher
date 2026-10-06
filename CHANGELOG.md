# Journal des versions

Chaque version installée sur le téléphone a une entrée ici.
Format : `vX.Y.Z (code N) — date`. Le numéro est défini dans `gradle.properties`.

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
