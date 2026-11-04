# Live Sac Manager — version 2.0

Application Android 100 % hors ligne pour gérer les commandes de sacs pendant les Facebook Live.
Kotlin · Jetpack Compose · Room (SQLite). Aucune permission, aucun compte, aucun serveur, aucune publicité.

## Nouveautés de la version 2.0

- **Trois onglets fixes** : Clients (faUsers), Lives (faVideo, bouton central surélevé), Emballages (faBox).
  Paramètres et sauvegarde : petite icône ⚙ en haut des écrans principaux.
- **Lives hebdomadaires** : Mercredi, Samedi, Dimanche. Création rapide : titre et date du prochain Live du jour choisi proposés automatiquement.
- **Mot de passe administrateur** : choisi à la première ouverture, demandé uniquement pour modifier le titre ou la date d'un Live (cadenas en haut du récapitulatif). Il n'est jamais stocké en clair (sel aléatoire + PBKDF2-HMAC-SHA256, 120 000 itérations).
- **Regroupement samedi + dimanche** : un client ayant acheté aux deux Lives de la même semaine n'a qu'**un seul emballage**, avec tous ses codes JP, le Live d'origine de chaque sac et le total cumulé. Le mercredi reste indépendant.
- **Emballages** : badge orange « À emballer » / vert « Emballé » (un appui change le statut), jour de livraison (Mer / Sam / Dim), classement automatique par jour de livraison, filtres (Tous, À emballer, Emballés, Livraison mercredi/samedi/dimanche), progression « Emballages préparés : 12 / 20 ». Un sélecteur de lot (week-end ou mercredi) évite de mélanger les semaines.
- **Ajout rapide** : bouton « + Sac » sur chaque client d'un Live, sans ressaisir le nom.
- **Doublons** : noms comparés sans tenir compte de la casse, suggestion des clients existants pendant la saisie, avertissement si un code JP existe déjà dans le Live.
- **Fiche client** : sacs avec prix et Live d'origine, total, statut, livraison, ajout/modification/suppression avec confirmation (confirmation renforcée pour le dernier sac, qui supprime aussi la fiche).
- **Partager le récapitulatif** : texte généré hors ligne, à copier ou à envoyer (Messenger, SMS…).
- **Sauvegarde / restauration JSON** (Paramètres) : Lives, clients, commandes, emballages, jours de livraison. La restauration valide le fichier puis demande confirmation avant de remplacer les données. Le mot de passe n'est pas inclus dans la sauvegarde.
- **Mise à jour sans perte** : migration Room 1 → 2 (colonnes ajoutées uniquement ; aucun effacement). Les anciens Lives reçoivent leur type d'après le jour de leur date ; ceux d'un autre jour restent indépendants.

> Mot de passe oublié : il ne peut pas être récupéré (il n'est pas stocké). Faites une sauvegarde régulièrement.

## Obtenir l'APK avec Android Studio (méthode recommandée)

1. Installez **Android Studio** (version stable la plus récente) : https://developer.android.com/studio
2. Décompressez `LiveSacManager.zip`.
3. Dans Android Studio : **File > Open**, choisissez le dossier `LiveSacManager` (celui qui contient `settings.gradle.kts`).
4. Attendez la fin de **Gradle Sync** (barre de progression en bas). La première fois, il télécharge des composants : prévoyez 5 à 15 minutes et une connexion Internet.
   - Si Android Studio propose d'installer une plateforme SDK manquante (« Android SDK Platform 35 »), acceptez.
5. Menu **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
6. Quand « APK(s) generated successfully » s'affiche, cliquez sur **locate**. Le fichier est ici :
   `app/build/outputs/apk/debug/app-debug.apk`

## Installer l'APK sur votre téléphone

1. Copiez `app-debug.apk` sur le téléphone (câble USB, Google Drive, WhatsApp à vous-même, e-mail…).
2. Ouvrez le fichier depuis le gestionnaire de fichiers du téléphone.
3. Android demande d'autoriser l'installation depuis cette source (Chrome, Fichiers, WhatsApp…) : autorisez-la.
4. Si « Play Protect » affiche un avertissement (normal pour une application qui ne vient pas du Play Store), choisissez **Installer quand même**.
5. Ouvrez **Live Sac Manager**.

## Alternative sans Android Studio (optionnelle)

Le dossier `.github/workflows` contient une recette GitHub Actions qui compile l'APK en ligne :
1. Créez un dépôt GitHub privé et envoyez-y tout le contenu du projet.
2. Onglet **Actions > Compiler l'APK > Run workflow**.
3. À la fin, téléchargez l'APK dans la section **Artifacts**.

## Bon à savoir

- Les données sont enregistrées automatiquement sur le téléphone et restent après fermeture ou redémarrage.
- **Désinstaller l'application supprime toutes les données** (la sauvegarde cloud est volontairement désactivée).
- Un même nom Facebook ne crée jamais de doublon dans un Live : l'application propose d'ajouter le sac à la fiche existante.
- Supprimer le dernier sac d'un client supprime aussi sa fiche (après confirmation).
- Mettre à jour : installez le nouvel APK **par-dessus** l'ancien (même signature de débogage requise) ; ne désinstallez pas, sinon les données sont perdues. En cas de doute, exportez d'abord une sauvegarde.

## Vérifier les calculs

La logique (totaux, regroupement samedi/dimanche, filtres, doublons, recherche, récapitulatif, sauvegarde, mot de passe) est dans
`app/src/main/java/com/livesac/manager/domain/`, testée par `app/src/test/.../` (LogicTest, PackingTest, BackupAuthTest).
Lancer : `./gradlew testDebugUnitTest` ou clic droit sur le dossier `test` dans Android Studio > **Run Tests**.
`app/src/androidTest/` contient les tests à lancer sur téléphone/émulateur (persistance après fermeture, migration 1 → 2, sauvegarde/restauration) : `./gradlew connectedDebugAndroidTest`.

## Licences

Icônes Font Awesome Free (faUsers, faVideo, faBox) — CC BY 4.0 — https://fontawesome.com/license/free
