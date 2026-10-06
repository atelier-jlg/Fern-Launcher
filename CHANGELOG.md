# Journal des versions

Chaque version installée sur le téléphone a une entrée ici.
Format : `vX.Y.Z (code N) — date`. Le numéro est défini dans `gradle.properties`.

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
