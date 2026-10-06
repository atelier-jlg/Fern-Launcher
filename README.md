# 🌿 Fern Launcher

Un lanceur Android fait maison, ambiance botanique et nocturne, pensé pour un Fairphone 6 sous /e/OS.

## Récupérer l'APK

**Version officielle** : onglet **Releases** du dépôt → télécharger `fern-launcher-vX.Y.Z-release.apk`.

**Version de travail** (après chaque modification) : onglet **Actions** → cliquer sur le dernier « Build APK » réussi ✅
→ en bas, section **Artifacts** → télécharger le zip, qui contient l'APK.

## Installer sur le téléphone

1. Copier l'APK sur le Drive, l'ouvrir depuis le téléphone.
2. Autoriser l'installation depuis cette source si Android le demande.
3. Une fois installé : **Paramètres → Applications → Applications par défaut → Appli d'écran d'accueil → Fern Launcher**.

Pour mettre à jour, installer simplement le nouvel APK par-dessus : les réglages sont conservés
(à condition que l'APK soit signé avec la même clé, voir `CLAUDE.md`).

## Sortir une nouvelle version

1. Dans `gradle.properties`, augmenter `fernVersionName` (ex. `0.2.0`) **et** `fernVersionCode` (+1).
2. Ajouter une entrée dans `CHANGELOG.md`.
3. Créer un tag `v0.2.0` : la Release se publie toute seule avec l'APK.

## Contenu du dépôt

- `app/` : le code de l'appli (Kotlin + Jetpack Compose).
- `design/` : charte (couleurs, tailles, police) et fonds d'écran.
- `CLAUDE.md` : tout le contexte du projet (goûts, décisions, feuille de route).
- `CHANGELOG.md` : ce qui change à chaque version.

Police : [Bricolage Grotesque](https://github.com/ateliertriay/bricolage), licence SIL OFL 1.1.
