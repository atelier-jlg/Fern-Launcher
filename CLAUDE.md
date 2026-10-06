# CLAUDE.md — Interface Fairphone (Jules)

Tu reprends un projet en cours. Lis tout avant de proposer quoi que ce soit.

**Important : tous les designs décrits ici sont encore expérimentaux.** Rien n'est figé. Couleurs, mises en page, widgets et organisation des apps sont des pistes de travail validées « pour l'instant », pas une version finale. Tu peux les remettre en question si tu as une meilleure idée, en expliquant pourquoi.

## Façon de travailler
- On parle en français, ton décontracté. Réponses concises, structurées, avec le raisonnement.
- J'avance par itérations : tu proposes, je réagis, on ajuste.
- J'apprécie qu'on me contredise franchement si une idée ne tient pas (lisibilité, faisabilité technique, cohérence).
- Pose-moi des questions quand un choix m'appartient.

## Matériel et outils
- **Téléphone** : Fairphone 6 sous /e/OS (sans Google, avec microG). Écran 6,31", 1116 × 2484 px, environ 412 × 916 dp.
- **Personnalisable** : écran d'accueil, tiroir d'apps, widgets, icônes, fond d'écran. Pas l'écran de verrouillage, les notifications ni les réglages rapides.
- **Outils actuels** :
  - **Total Launcher** : placement libre, textes dynamiques pour l'heure, la date et la météo, polices TTF.
  - **Renkin** : pack d'icônes généré à partir d'Arcticons. Plaques de couleur rondes, une couleur par app.
  - **Material Photo Widget** : photos et stickers PNG.
  - **Widgets perso** : prévus en Jetpack Glance. **Réflexion en cours** : coder plutôt mon propre lanceur en Kotlin + Compose. Les widgets deviendraient des composants natifs, sans les limites de Glance (pas de police perso, pas d'animation).
- **Agenda** : j'utilise Proton Calendar, qui ne se synchronise pas avec l'agenda Android. Piste retenue : lien de partage ICS de Proton + ICSx⁵ (lecture seule).

## Mes goûts
- Ambiance botanique, nocturne et chaleureuse : verts profonds, crème, touches de rouge carmin, détails mignons.
- Rendu illustré. Mon fond d'écran actuel est en **pixel art** : jungle de nuit, cascade, ruines, fleurs bordeaux. Le style de départ était plutôt estampe ou sérigraphie.
- Formes douces : cartes arrondies, pilules, plaques d'icônes rondes, découpe en vague.
- Ton sur ton, contraste maîtrisé, rien de criard. Lisible d'un coup d'œil, y compris en extérieur.
- J'aime l'aération : peu d'apps par zone, et de la place pour des **stickers de ce qui me tient à cœur** (mon chat, mes proches).
- Je n'aime pas l'orange. Je l'ai remplacé par un **vert pistache** clair et mignon.

## Ce qu'on a fait jusqu'ici
1. **Pistes de départ** : plusieurs essais (nuancier sombre, taupe cosy, estampe claire). J'ai retenu une **fusion « nuancier + estampe »**, déclinée en sombre. C'est la piste de travail actuelle.
2. **Fonds d'écran** : deux couches.
   - Dessous, un fond topographique vert-bleu très sombre (lignes de niveau, léger vignettage).
   - Dessus, un PNG transparent en pixel art, détouré par une vague. Il prend environ 29 % de la hauteur sur l'accueil et 21 % sur les autres pages.
3. **Organisation en pages** :
   - **Accueil** : horloge « 09:41 » avec les minutes en rose, date et météo, widget Contexte, widget Musique, 4 favoris (WhatsApp, Instagram, Curve, dossier Proton), un sticker du chat posé sur la vague.
   - **Page 2 · Journée** : packs Organisation, Travail, Études.
   - **Page 3 · Pratique** : packs Argent, Déplacements, Courses, Urgence.
   - **Page 4 · Les miens** : un mur de stickers (chat, proches) et le widget Carnet.
   - **Dans chaque pack** : 4 apps maximum en grille 2×2, sur une carte arrondie. Les stickers sont disposés en quinconce entre les packs.
   - **Dock fixe** : Appels (Fil), SMS (Fil), Signal, Appareil photo, bouton du tiroir.
   - **Tiroir** : toutes les apps rangées par sections. Le dossier « Perso & système » reste discret en bas.
4. **Guide des icônes (Renkin)** : plaque ronde pleine, picto à 70 %, sans contour ni ombre, une couleur par famille :
   - Communication & mail : vert forêt #186348, trait crème
   - Social : carmin #8E2733, trait crème
   - Urgence : rose #EC9AA0, trait bordeaux #5A1520
   - Organisation, travail & fichiers : crème #EFE5CF, trait vert nuit
   - Argent & courses : pistache #BFE3A3, trait vert nuit #14241B
   - Admin & sécurité : vert sombre #35553F, trait crème
   - Photo, musique & loisirs : sauge #9DB384, trait vert nuit
   - Dehors, maison & web : olive #56633C, trait crème
5. **Widgets perso imaginés** (avec plusieurs états chacun) :
   - **Contexte** : matin, journée et soir (cours, révisions, coucher du soleil, note du jour).
   - **Alternance** : compte à rebours école ESB ou entreprise VINCI, frise des semaines.
   - **Révisions** : cartes AnkiDroid à réviser ; un lotus s'ouvre quand tout est fait.
   - **Ciel** : arc du soleil le jour, phase de lune et lien vers Stellarium la nuit.
   - **Carnet du jour** : humeur de la graine à la fleur, 3 habitudes, note vers Obsidian.
   - **Musique en estampe** : pochette recolorée en deux tons carmin et rose.
6. **Charte « Estampe nuit »** (provisoire) :
   - Fonds : Nuit #14241B, cartes Mousse #1E3427.
   - Textes : Crème #EFE5CF, Lichen #B5C2A5 pour le secondaire.
   - Accents : rose carmin #EC9AA0, carmin #8E2733, vert forêt #186348, pistache #BFE3A3.
   - Police unique : **Bricolage Grotesque**, étroite et ExtraBold pour les chiffres et titres, normale pour le texte.
   - Grille 4 dp, marges 20 dp, rayon des cartes 28 dp, cibles tactiles ≥ 44 dp.
   - Textes : tutoiement, libellés en capitales, point médian « · » comme séparateur.

## Points ouverts
- **Couleurs à recaler sur le fond pixel art** : les surfaces de la charte tirent vers le vert-jaune alors que le fond tire vers le vert-bleu. Proposition non encore validée : Nuit #0E1A1A, cartes #12201F à 85 %, séparateurs #2C4441.
- **Trait de la vague** : actuellement gris-bleu #98A0A8, à passer peut-être en crème ou en Lichen.
- **Charte à mettre à jour** : elle parle encore d'« estampe », alors que les visuels sont en pixel art.
- ~~**Réalisation**~~ : tranché, on code notre propre lanceur (voir « Projet Fern Launcher » plus bas).
- ~~**Rotation dans Total Launcher**~~ : sans objet (Compose gère la rotation des éléments).

## Ce que j'attends de toi
Voir les issues ou la demande de la session. Les couleurs, tailles et polices de référence sont dans `design/design-tokens.json`, et les fonds d'écran dans `design/assets/`.

---

# Projet Fern Launcher

## Décisions prises
- **Lanceur maison** en Kotlin + Jetpack Compose. On abandonne Total Launcher et Glance.
- **Priorité absolue : la logique.** Au minimum toutes les fonctions gratuites de Phi Launcher, sans accroc.
  Ce que Jules utilise vraiment dans Phi : **glisser ↓ = recherche**, **glisser ↑ = tiroir**, et les **widgets**.
- **Pour Jules seulement**, mais avec des **thèmes** modifiables dans un onglet **Paramètres** (obligatoire),
  exportables en fichier pour les **partager avec des proches**.
- **Stickers** : PNG faits maison par Jules, importés dans l'appli.
- **Installation** : APK téléchargé sur un Drive puis installé à la main. Les versions doivent être clairement numérotées.
- **iOS** : impossible (Apple interdit les lanceurs tiers). Pas de compromis d'architecture pour ça.
- **Nom** : « Fern Launcher ». Identifiant Android : `com.atelierjlg.fern` (ne doit plus jamais changer).

## Niveau de Jules
Débutant complet en Kotlin/Android. Connaît Python, HTML/CSS, un peu de C# (Arduino).
→ Code commenté en français, explications pédagogiques, analogies Python/HTML quand ça aide.

## Architecture (à garder simple)
```
app/src/main/java/com/atelierjlg/fern/
├── MainActivity.kt          l'unique écran (catégorie HOME), applique le thème
├── LauncherViewModel.kt     l'état et toutes les actions (le « cerveau »)
├── apps/                    liste des applis (LauncherApps), AppIndex (renommées/masquées), recherche, tiroir
├── data/                    LauncherConfig (tout est là, en JSON), ConfigEdits (fonctions pures testées),
│                            ConfigStore (fichier fern-config.json), DefaultLayout, ThemeData
├── search/                  calculatrice, contacts / agenda / raccourcis
├── system/                  accessibilité (verrouiller), notifications (musique), actions système
├── widgets/                 hôte des widgets Android, musique en cours, calculs soleil/lune
└── ui/
    ├── LauncherRoot.kt      empile : accueil, tiroir (bas), recherche (haut), Paramètres, sélecteur
    ├── home/                pages, blocs (packs, rangées, horloge), dock, mode édition, roue d'applis
    ├── drawer/              tiroir, panneau de recherche, résultats
    ├── settings/            onglet Paramètres (thème, Spaces, Focus, gestes, recherche, tiroir, lieu, sauvegarde)
    ├── widgets/             widgets maison (Ciel, Musique, Contexte) + affichage des widgets Android
    ├── common/              petits composants réutilisés (icône, pilule, dialogues, sélecteur d'applis)
    └── theme/               FernColors, FernType (Bricolage Grotesque)
```
- **Toute la configuration** vit dans `LauncherConfig` (JSON). Chaque champ a une valeur par défaut :
  ajouter un champ ne casse jamais un ancien fichier. Modifications = fonctions pures dans `ConfigEdits.kt`,
  avec tests dans `app/src/test`.
- Les couleurs passent **toujours** par `Fern.colors.xxx`, jamais en dur : elles viennent du thème actif.
- Police : `res/font/bricolage_grotesque.ttf` (variable : graisse + largeur 75–100 %). Licence OFL dans `design/`.

## Versions
- Numéro dans `gradle.properties` : `fernVersionName` (lisible) et `fernVersionCode` (entier, toujours croissant).
- Chaque version a une entrée dans `CHANGELOG.md`.
- APK produit : `fern-launcher-vX.Y.Z-release.apk`. Version visible en bas du tiroir (et plus tard dans Paramètres → À propos).
- Release : pousser un tag `vX.Y.Z` → la CI vérifie qu'il correspond à `gradle.properties` et publie l'APK.
- Signature : secrets GitHub `FERN_KEYSTORE_BASE64` + `FERN_KEYSTORE_PASSWORD` (alias `fern`). Même clé pour toujours,
  sinon Android refuse les mises à jour. Jules garde la clé `.jks` en lieu sûr ; elle n'est jamais dans le dépôt.

## Compiler
- La CI GitHub (`.github/workflows/build.yml`) compile à chaque push.
- Dans les sessions Claude Code cloud, `dl.google.com` (SDK Android) est bloqué par défaut : impossible de compiler
  localement, on s'appuie sur la CI. Pour compiler en local, autoriser `dl.google.com` dans le réseau de l'environnement.

## Feuille de route
1. ✅ Socle : projet, CI, APK numéroté, kit dans le dépôt. *(v0.1.x)*
2. ✅ Accueil : pages, dock, packs, mode édition dans l'appli. *(v0.2.0)*
3. ✅ Tiroir : grille/liste, A–Z, fréquence, masquer, renommer. *(v0.3.0)*
4. ✅ Recherche : panneau qui descend du haut, applis puis Firefox ; recherche étendue en option. *(v0.4.0)*
5. ✅ Gestes + roue d'applis + Paramètres + thèmes (export/import) + sauvegarde. *(v0.5.0)*
6. ✅ Spaces + planning + Focus. *(v0.6.0)*
7. ✅ Widgets : Ciel, Musique, Contexte, widgets Android. *(v0.7.0)*
8. ✅ Icônes : plaques par famille (Arcticons recoloré), packs d'icônes, familles dans le tiroir. *(v0.8.0)*
9. ✅ Demi-largeur (2 éléments côte à côte), espace vide, stickers en placement libre. *(v0.9.0)*
10. ✅ Lignes de 4 colonnes : éléments en 1/2/4 colonnes, espaces réglables, « + Appli ». *(v0.10.0)*
11. ✅ Widgets Alternance, Révisions (AnkiDroid, lotus), Carnet du jour (→ Obsidian). *(v0.11.0)*
12. À venir : nouveaux widgets à proposer /
   Révisions (AnkiDroid) / Carnet, glisser-déposer libre en mode édition.

## Décisions de détail (session du 2026-10-06)
- Recherche (glisser ↓) : panneau **du haut**, résultats **applis puis web**. Le web s'ouvre comme un
  **lien** dans Firefox (pour que la navigation privée des liens externes s'applique). Moteur par défaut DuckDuckGo.
- Contacts / agenda / raccourcis / calcul : « recherche étendue », **désactivée par défaut**.
- Pas de grand titre de page sur l'accueil (seulement en mode édition).
- Familles : classement auto (`FamilyClassifier`) + corrections de Jules (`appFamilies`), qui passent avant.
