# Fern Clavier — choix et feuille de route

Décidé avec Jules le 2026-10-15. Look retenu : maquette **B « Vague »** (écume à deux filets, couleur Lichen,
ondulation brève à l'ouverture). Pas d'écriture en glissant, pas de GIF (déjà dans les applis), pas de cloud.

## Promesses
- **Aucune permission Internet.** Tout se passe sur le téléphone.
- **Aucun mot de passe gardé** (Jules utilise Proton Pass). Les copies marquées « sensibles » ne sont jamais
  proposées, et les champs de mot de passe n'apprendront rien.

## Feuille de route
1. ✅ **v0.1** — clavier de base : AZERTY, accents à l'appui long, majuscules (auto + verrouillage), chiffres,
   2 pages de symboles, pavé numérique, point auto, curseur sur l'espace, dernier texte copié, vague + écume,
   thème de Fern, réglages.
2. ✅ **v0.2** — correction et suggestions :
   - dictionnaire du français parlé (fréquences des sous-titres OpenSubtitles + liste de mots MIT, voir
     `tools/build_dictionary.py`) + formes familières/SMS ;
   - apostrophes gérées (m', t', j', l', d', qu'… : « m'ennuie » n'est plus massacré) ;
   - correction prudente (touches voisines en AZERTY, accents oubliés), jamais sur un mot connu ;
   - apprentissage : ⌫ juste après une correction remet le mot et le retient ; noms des contacts connus ;
   - Kotlin pur, testé hors du téléphone avec les phrases de Jules.
3. **v0.3** — presse-papiers (5 derniers, épingler), emoji favoris / récents / recherche en français,
   code SMS reçu par Fern Messages proposé d'un toucher, suggestions de Proton Pass dans la barre (saisie auto).
4. **v0.4** — memes et stickers : bibliothèque commune avec les stickers de Fern, mots-clés, envoi direct
   (Signal, WhatsApp, Fern Messages) ou copie à coller.
5. **v0.5** — dictée **Whisper** hors ligne (whisper.cpp, modèle « small » importé une fois par Jules).

Plus tard, si l'envie vient : traduction hors ligne, mode une main, mélanges d'emoji, raccourcis de texte.
