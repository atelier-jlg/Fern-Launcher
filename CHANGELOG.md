# Journal des versions

Chaque version installée sur le téléphone a une entrée ici.
Format : `vX.Y.Z (code N) — date`. Le numéro est défini dans `gradle.properties`.

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
