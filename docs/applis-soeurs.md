# Fern Messages et Fern Contact — état des lieux et feuille de route

Audit du 2026-10-08 des deux applis Flutter de Jules (Fil 1.0.0+1 et Fil · Appels 1.0.0+1), lues en entier.
Légende : ✅ fait de bout en bout · 🟡 partiel · ❌ absent.

## Commun aux deux
- 🔴 Polices téléchargées à l'exécution chez Google (`google_fonts`), jamais embarquées. Fil · Appels n'a même pas
  INTERNET en release ⇒ affichage en Roboto.
- 🔴 Signées avec la clé de debug ⇒ mises à jour impossibles d'une machine à l'autre.
- 🟡 Thème : même fichier `fil_theme.dart` copié dans les deux (charte crème / olive / terracotta, clair + sombre).
- ❌ Tests (seulement l'écran de configuration), CI, CHANGELOG.

## Fil (SMS) → Fern Messages
- ✅ App SMS par défaut (+ aide « paramètres restreints » /e/OS) · réception des SMS longs · envoi + statut
  envoyé/échec · fil SMS+MMS · contacts/photos · archivage · recherche (corps, numéro) · liste noire ·
  envoi différé (AlarmManager exact, reboot) · copie de code OTP dans la bulle · notif par conversation
  (son / silencieux / off) · historique système lu directement.
- 🟡 Notifications : le tap n'ouvre pas le fil ; pas de réponse directe, « marquer lu », copie OTP.
- 🟡 Liste pas mise à jour en direct ; fils 100 % MMS absents.
- 🟡 Renvoi sur échec = doublon ; suppression d'un fil laisse les MMS ; envoi différé absent de « Nouveau message » ;
  `smsto:?body=` ignoré ; « Infos du contact » en dur vers `com.jules.fil_appels` ; groupes = SMS séparés.
- ❌ **Téléchargement des MMS entrants** (récepteur squelette) · envoi MMS / vCard · pièces jointes · accusés de remise ·
  brouillons · multi-SIM · réponse par SMS pendant un appel (HeadlessSmsSendService) · partage entrant.
- 🐞 **Supprimer un MMS sélectionné efface le SMS de même _id** · date « AUJOURD'HUI » partout ·
  le fil saute en bas pendant le défilement.

## Fil · Appels → Fern Contact
- ✅ App téléphone par défaut · contacts A–Z · ajout / édition / suppression / photo · favoris · appeler ·
  en appel : muet, haut-parleur / Bluetooth, attente, DTMF, raccrocher, chrono · appel de la messagerie ·
  lien vers Fil et retour « Infos du contact ».
- 🟡 Appel entrant : POST_NOTIFICATIONS jamais demandée, pas de showWhenLocked / turnScreenOn ⇒ **appel
  potentiellement invisible écran verrouillé**.
- 🟡 Fiche : « MOBILE » écrit en dur, pas d'e-mails · journal sans filtre ni suppression · pas de T9 ·
  `DIAL tel:` non pré-rempli · recherche sans accents / espaces · sonnerie favoris appliquée une fois ·
  VVM codée mais non vérifiée · blocage sans liste ni anti-spam.
- ❌ Répondre / Refuser dans la notif · notif « appel en cours » · double appel (le 2ᵉ écrase le 1ᵉʳ) · conférence ·
  refus par SMS (pastilles décoratives) · capteur de proximité · **multi-SIM (SELECT_PHONE_ACCOUNT non géré ⇒
  appel bloqué sans SIM par défaut)** · export / import vCard · doublons · rappels d'anniversaire.
- 🐞 **L'édition d'un contact efface** poste, service, notes en plus, autres dates, libellés personnalisés.

## Feuille de route
0. ✅ **Socle** (Fern v0.25.0, Messages/Contact v0.0.1) : module `theme`, thème partagé, CI à 3 APK.
1. **Fern Contact v0.1** — un téléphone fiable : CallActivity (écran verrouillé, réveil), notif CallStyle
   Répondre / Refuser, notif « en cours », proximité, double appel, choix de SIM, clavier, journal,
   contacts sans perte de données.
2. **Fern Messages v0.1** — des SMS fiables : liste et fil en direct, envoi / statut / renvoi propre, suppression
   correcte, notifs (ouvrir le fil, réponse directe, marquer lu, « Copier 123456 »), recherche, liste noire,
   archives, envoi différé.
3. **Fern Messages v0.2** — MMS : téléchargement des entrants (SmsManager.downloadMultimediaMessage), envoi
   photo / vCard, brouillons, accusés de remise, SIM, partage entrant.
4. **Fern Contact v0.2** — confort : recherche sans accents, T9, filtres du journal, sonnerie par contact,
   vCard, anniversaires, VVM, liste noire + anti-démarchage (préfixes ARCEP), refus par SMS.
5. **Intégration** : recherche de Fern → fiches de Fern Contact, dock, « À propos » commun.

## Bascule depuis Fil
Historique SMS conservé (stocké par Android). Perdus : archives, réglages de notif par conversation,
**messages programmés de Fil** (attendre qu'ils partent avant de désinstaller).
