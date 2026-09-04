# Pitfalls Research

**Domain:** App Android de clipping vidéo IA 100 % on-device (YouTube long → Shorts verticaux ; whisper.cpp, ffmpeg-kit, ML Kit, WorkManager, Room, AdMob)
**Researched:** 2026-09-04
**Confidence:** HIGH (supply-chain et plateforme Android, vérifiées multi-sources) / MEDIUM (perfs whisper on-device, pronostics d'application des règles Play)

> Verdict central : **les deux piliers du cahier des charges sont des dépendances à haut risque de mortalité**. `com.arthenica:ffmpeg-kit-full-gpl:6.0-2` (épinglé en Partie 1 §2.2) **ne résout plus** : ffmpeg-kit a été retiré le 6 janvier 2025 et ses binaires supprimés de Maven Central le 1er avril 2025. `NewPipeExtractor` fonctionne mais casse à chaque changement interne de YouTube (incidents récurrents documentés en 2025-2026). La clause de fraîcheur du cahier des charges est donc indispensable dès l'échafaudage, et le risque Play Store (application téléchargeant du contenu YouTube) est un risque produit existentiel à porter au niveau du roadmap, pas seulement technique.

## Critical Pitfalls

### Pitfall 1: Dépendance ffmpeg-kit morte — `com.arthenica:ffmpeg-kit-full-gpl:6.0-2` ne résout plus

**What goes wrong:**
FFmpegKit (Arthenica) a été officiellement retiré le **6 janvier 2025** (post « Saying Goodbye to FFmpegKit » de Taner Sener) ; le dépôt GitHub est archivé en lecture seule et **tous les binaires ont été supprimés de Maven Central le 1er avril 2025**. Le build échoue immédiatement avec `Could not find com.arthenica:ffmpeg-kit-full-gpl:6.0-2`. Aucune mise à jour de sécurité ne sortira jamais ; les binaires d'origine sont alignés 4 KB (incompatibles 16 KB, voir Pitfall 2) et compilés avec NDK r26c.

**Why it happens:**
Le cahier des charges a été épinglé sur la référence la plus citée de l'écosystème sans vérifier sa date de péremption ; c'est le cas d'école du risque de supply chain sur un artefact binaire non maintenu (pas un problème de code, un problème de distribution).

**How to avoid:**
1. Basculer sur le fork communautaire maintenu : `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl` (package Java inchangé `com.arthenica.ffmpegkit` → zéro changement de code). Lignes LTS 6.0/7.1/8.1, alignement 16 KB vérifié en CI, variantes `full` (LGPL-3.0) et `full-gpl` (GPL-3.0, inclut libx264 + libass + loudnorm).
2. Prévoir un plan B documenté : vendor l'.aar dans `libs/` (version figée) ou self-build FFmpeg (libass + x264 + loudnorm) via CMake — coûteux mais immunisé.
3. Noter les conséquences licences : `full-gpl` = GPL-3.0 (obligations copyleft) ; le cahier des charges accepte explicitement le LGPL/GPL mais la mention « ffmpeg-kit-full-gpl » doit être revalidée avec l'équipe juridique/produit.
4. Consigner l'artefact exact + checksum dans `gradle/libs.versions.toml` et dans la doc de build.

**Warning signs:**
Un `dependency resolution failure` dès le premier `gradle assemble` ; à l'inverse, si quelqu'un « répare » en pointant JitPack (`com.github.ffmpegkit-maintained`) sans verrouiller, tout build devient dépendant d'un build JitPack à la demande. Une `UnsatisfiedLinkError` sur émulateur x86_64 est aussi un signal : le fork ne livre qu'**arm64-v8a** → les émulateurs x86_64 ne chargeront pas les `.so` (tester sur appareil ARM64 ou émulateur ARM64).

**Phase to address:** Échafaudage (Phase 1) — décision bloquante avant toute ligne de code du pipeline.

---

### Pitfall 2: Taille de page 16 KB — crash natif sur appareils récents + rejet Play

**What goes wrong:**
Android 15+ supporte les appareils 16 KB page size ; **Google Play exige le support 16 KB pour les apps ciblant Android 15+ soumises/mises à jour après le 1er novembre 2025**. Une `.so` alignée 4 KB (binaires ffmpeg-kit d'origine, anciennes versions de ML Kit face-detection 16.1.7 — issue googlesamples/mlkit#1024, builds whisper.cpp non configurées) plante au chargement : `UnsatisfiedLinkError` / échec `dlopen`, uniquement sur les appareils 16 KB — impossibles à reproduire sur la majorité des bancs de test.

**Why it happens:**
L'alignement ELF (`-Wl,-z,max-page-size=16384`) est un détail de link invisible en dev ; les binaires pré-compilés récupérés çà et là datent d'avant l'exigence.

**How to avoid:**
1. whisper.cpp : compiler soi-même via CMake/NDK avec **NDK r28+** (alignement 16 KB par défaut) ou `APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true`.
2. ffmpeg-kit : utiliser le fork `ffmpegkit-maintained` (alignement 16 KB imposé, échec de CI si une `.so` est désalignée).
3. ML Kit : dépendre d'une version récente de `com.google.mlkit:face-detection` (modèle bundled), pas d'une version historique copiée d'un tutoriel.
4. Vérification mécanique en CI : script `check_elf_alignment.sh` de Google ou `llvm-readelf -l` sur chaque `.so` de l'APK ; plus smoke test sur l'**émulateur 16 KB** (image système dédiée SDK 35+).

**Warning signs:**
`dlopen failed: ... program alignment` dans les crashs Play Console (vital) ; crashs concentrés sur appareils 2025+ (Pixel avec option 16 KB, etc.).

**Phase to address:** Échafaudage (configuration NDK/CMake) + vérification continue à chaque phase ajoutant du natif ; contrôle bloquant en phase pré-release.

---

### Pitfall 3: NewPipeExtractor fragile — YouTube casse l'extracteur sans prévenir

**What goes wrong:**
À chaque changement interne de YouTube (player, BotGuard, endpoints de streams), NewPipeExtractor se met à échouer en masse : erreurs réseau, **403**, « Watch on the latest version of YouTube » (NewPipe#12930, NewPipeExtractor#1185 ; incidents récurrents 2025-2026). Les releases incluent régulièrement des **refactors breaking** (ex. v0.25.1 : refact du date parsing qui supprime `DateWrapper`). Pour ShortifyLocal AI, c'est la Fonction A (et donc tout le pipeline A→F) qui tombe, sans aucun levier interne.

**Why it happens:**
L'extracteur scrute des API non officielles ; YouTube ne donne ni préavis ni rétro-compatibilité. La maintenance est réactive : les fenêtres de panne existent entre la casse et le patch.

**How to avoid:**
1. **Isoler** : tout contact avec NewPipeExtractor derrière `NewPipeExtractorWrapper` (déjà prévu en `data/remote/youtube/`) ; le domaine ne voit que des modèles purs. Le jour du switch de version (ou de bibliothèque), une seule classe change.
2. **Épingler** la version exacte JitPack : `com.github.TeamNewPipe:NewPipeExtractor:v0.26.x` (tag précis, jamais `-SNAPSHOT` ni HEAD — un build JitPack sur HEAD est non reproductible et peut casser sans changement local).
3. **Mapper les erreurs** vers la machine à états `Error` + `sourceStatus = NON_DISPONIBLE_LOCALEMENT` + bouton re-téléchargement (prévu Partie 4 §6) : l'utilisateur voit un état propre au lieu d'un crash.
4. **Surveiller** les releases GitHub (watch releases) et budgéter une mise à jour extractor sous 48-72 h en cas de panne généralisée ; tester l'upgrade dans un canal beta avant prod.
5. Rester en **LGPL** (déjà accepté par le cahier des charges) — c'est la licence du projet.

**Warning signs:**
Taux d'erreur croissant sur l'étape `DownloadingAudio` avec code HTTP 403 ; issues GitHub identiques chez NewPipe/Piped ; builds qui cassent après un simple bump de version de l'extracteur.

**Phase to address:** Phase Pipeline (Fonction A) pour l'isolation ; phase pré-release pour mettre en place le canal de veille + procédure de hotfix.

---

### Pitfall 4: Risque de politique Google Play — apps de téléchargement YouTube

**What goes wrong:**
Les **YouTube API Developer Policies interdisent aux clients API de permettre le téléchargement de contenu YouTube** ; les ToS YouTube (juin 2021) permettent d'exclure les utilisateurs d'apps de téléchargement, et YouTube a annoncé publiquement le renforcement de la répression contre les apps tierces qui violent ses ToS. Des apps de téléchargement sont régulièrement supprimées du Play Store (cas « Downloader » banni en 2023, appel rejeté ; apps retirées pour violation des ToS YouTube API). Play a bloqué 2,36 M d'apps en 2024 et 1,75 M en 2025. Pour ShortifyLocal AI, le pire cas n'est pas un bug : c'est **la suppression de l'app ou la suspension du compte développeur après des semaines de travail**.

**Why it happens:**
Le cœur de la proposition de valeur (« lien YouTube → clips ») entre en collision frontale avec une règle de plateforme, quelle que soit la qualité d'implémentation. Les équipes techniques traitent un risque juridique/produit comme un détail d'implémentation.

**How to avoid:**
1. **Décision produit explicite au démarrage du milestone** (pas pendant le dev) : distribuer sur Play en assumant le risque de retrait, viser d'emblée une distribution alternative (site officiel / APK signé) comme plan B opérationnel.
2. Positionner le listing Play sur le **studio de montage IA** (clip vertical, sous-titres, reframing), pas sur le « YouTube downloader » ; éviter les mots « download YouTube » dans titre/description.
3. Réduire la surface d'attaque : respecter les quotas, pas de scraping agressif, aucun contournement de pub YouTube revendiqué.
4. Ne jamais intégrer l'API Data v3 de YouTube (elle interdit explicitement le download et expose l'app à un signalement direct) — le projet utilise NewPipeExtractor, ce qui ne légalise rien mais évite la violation contractuelle directe des API officielles.
5. Préparer la procédure d'appel et conserver toutes les preuves de conformité (fonctionnalité d'édition réelle, contenu de l'utilisateur).

**Warning signs:**
Rejet à la review avec citation de « Device and Network Abuse » ou des ToS YouTube ; signalements ; suspension de compte après soumissions répétées. Toute « v1 » planifiée uniquement sur Play sans plan B est un signal d'alarme de roadmap.

**Phase to address:** Décision de milestone (avant le roadmap) + phase pré-release (listing, review) ; le plan de distribution alternative se prépare dès l'échafaudage (signature, mise à jour in-app).

---

### Pitfall 5: whisper.cpp on-device — RAM, thermique et temps de transcription sous-estimés

**What goes wrong:**
La RAM consommée = poids du modèle + cache KV + buffers de calcul : **tiny ~200-273 Mo, base ~350-390 Mo, small ~850-900 Mo** (disque 75/142/466 Mio — les tailles « ~39/57/184 Mo » du cahier des charges sont des tailles de fichier F16, pas la conso runtime). Sur Android, l'inférence est **CPU-only** et nettement plus lente que sur iOS (whisper.cpp#959) ; une transcription de 15 minutes est une charge soutenue qui **provoque du thermal throttling** (la vitesse se dégrade en cours de route) et, si le code fait du streaming mal conçu, une latence croissante jusqu'à l'ANR/process kill (whisper.cpp discussion#3567). Sur appareil 4 Go, `small` déclenche des kills LMK ; sur appareil moyen, `base` sur 15 min peut dépasser les attentes de durée de plusieurs facteurs une fois throttled.

**Why it happens:**
Les benchmarks publiés sont mesurés à froid sur des flagships ; les tests de dev se font sur 1-2 minutes d'audio, jamais 15 minutes en continu avec l'écran allumé et le network actif.

**How to avoid:**
1. Défaut **Base** conforme au cahier des charges, mais activer des **modèles quantifiés** (Q5_0/Q8_0 : tiny ~42-75 Mo) dans le Whisper Manager — même format GGML, charge mémoire très réduite.
2. Charger l'audio **depuis le fichier WAV 16 kHz** (jamais en mémoire entière), limiter `threads` au nombre de cœurs physiques, tester `flash_attn`/GPU via OpenCL seulement si validé — sinon rester CPU.
3. Transcrire **par chunks** (ex. fenêtres de 30 s avec chevauchement) en émettant `setProgress` par chunk : progression réelle, checkpoints, et reprise possible après mort du process (cf. Pitfall 6).
4. Sondes thermiques : `PowerManager.OnThermalStatusChangedListener` — au-delà de `THROTTLING_MODERATE`, afficher une estimation révisée et/ou baisser le modèle.
5. Protocole de test : **Pixel milieu de gamme 4-6 Go RAM** + vidéo de 15-20 min, batterie < 30 %, mode économie d'énergie ; mesurer real-time factor début vs fin.

**Warning signs:**
Transcription 3-4× plus longue que prévu en fin de fichier vs début ; OOM dans les rapports sur appareils bas de gamme ; le worker `Transcribing` touche le timeout FGS (Pitfall 6) ; téléphone brûlant + batterie qui chute.

**Phase to address:** Phase Pipeline (Fonction B) — benchmark réel dès le premier spike JNI, avant de figer l'UX de progression.

---

### Pitfall 6: Mort du process en plein pipeline — ré-exécution non idempotente du Worker et double débit de tokens

**What goes wrong:**
Quand le système tue le process (LMK, l'utilisateur glisse l'app away, Update), le Worker n'a jamais retourné de résultat : **WorkManager re-planifie et ré-exécute le travail** dès que les contraintes le permettent. Si le pipeline n'est pas checkpointé, il repart de zéro (re-téléchargement, re-transcription). Pire : si le débit −5 tokens a eu lieu avant la mort mais que la reprise relance aussi une écriture, on obtient un débit ou un état incohérent. Par ailleurs : `setForeground` sur API 34+ exige le `foregroundServiceType` déclaré dans le manifest **ET** passé dans `ForegroundInfo`, sinon `MissingForegroundServiceTypeException` ; et **Android 15 plafonne les FGS `dataSync`/`mediaProcessing` à 6 h cumulées par 24 h** (toutes instances confondues, quota partagé) — `onTimeout()` puis **crash** si le service ne s'arrête pas. Un pipeline qui boucle sur des reprises silencieuses peut épuiser le quota sans API pour le consulter.

**Why it happens:**
« WorkManager = fiable » est vrai pour l'exécution *éventuelle*, pas pour l'*exécution unique* : la sémantique est at-least-once. Les dévs testent le chemin nominal (app au premier plan, process vivant) et jamais le kill à l'étape 3/6.

**How to avoid:**
1. Machine à états **persistée dans Room** (état courant + étape + chemins de fichiers) à chaque transition — le Worker redémarre à l'étape checkpointée, jamais à zéro. Le Data de progression ne porte rien de sensible (déjà exigé Partie 5).
2. **Idempotence du débit** : le −5 tokens est débité par une transaction liée à l'ID projet (débit une seule fois par `youtubeUrl` actif — cohérent avec l'index unique Room), pas à chaque run du Worker.
3. Téléchargement **reprenable** via Range (cf. Pitfall 10) ou redémarrable proprement ; fichiers temporaires suffixés `.part` puis renommés (rename atomique).
4. Respect strict des types FGS : `FOREGROUND_SERVICE_DATA_PROCESSING` + `dataProcessing` dans manifest et dans `setForeground(ForegroundInfo(id, notification, FOREGROUND_SERVICE_TYPE_DATA_PROCESSING))`.
5. Gérer `onStopped()` / `getStopReason()` : annuler proprement (FFmpeg `session.cancel()`, flag d'abort natif pour whisper — voir Pitfall 11) et retourner `Result.retry()` avec backoff.
6. Budget temps : vérifier la durée cumulée des FGS ; pour 15 min de vidéo, une exécution complète reste très en dessous de 6 h, mais des boucles de retry non bornées peuvent l'épuiser — borner les tentatives (`AttemptBasedBackoff`, max runs).

**Warning signs:**
Logs montrant deux exécutions du même `PipelineWorker` ; token balance incohérent après crash ; crashs `MissingForegroundServiceTypeException` sur Android 14+ ; `onTimeout` / « Service dataSync timeout » en vitals.

**Phase to address:** Phase Pipeline (orchestration WorkManager) — concevoir le checkpointing avant d'écrire les Workers.

---

### Pitfall 7: security-crypto `EncryptedSharedPreferences` — dépréciée en 1.1.0-alpha07 et instable par conception

**What goes wrong:**
Le cahier des charges épingle `security-crypto 1.1.0-alpha06+`. En **alpha07 (avril 2025), la bibliothèque entière (EncryptedSharedPreferences + EncryptedFile) est officiellement dépréciée**, après ~4 ans en alpha sans jamais atteindre une stable. Problèmes connus et documentés : crash `AEADBadTagException` quand la clé Keystore est invalidée (changement de credential, restauration), MasterKey non invalidée au changement de lock-screen contrairement à la doc, valeurs en clair dans les heap dumps au moment des lectures. Un appareil mis à jour ou restauré peut rendre le fichier illisible → **perte du solde et de la clé Gemini au premier lancement** si non géré.

**Why it happens:**
La bibliothèque semblait « la solution officielle » pendant des années ; le statut alpha et la dépréciation tardive passent inaperçus dans les tutoriels.

**How to avoid:**
1. Tout passer derrière `SecureStorageRepository` (déjà imposé par la Partie 5) — le jour du remplacement, une seule implémentation change.
2. **Epingler consciemment** `1.1.0-alpha06` (pas « + » ouvert) et consigner la décision ; surveiller le remplacement officiel (Jetpack pousse vers DataStore + Keystore). Alternative : fork communautaire `ed-george/encrypted-shared-preferences` si un maintien est requis.
3. **Intercepter les échecs de déchiffrement** (`AEADBadTagException`, `GeneralSecurityException`, `SecurityException`) à l'ouverture du fichier : reconstruire le fichier proprement + réinitialisation contrôlée via le parcours `SECURE_COMPROMISED` existant, plutôt qu'un crash au lancement.
4. **Atomicité réelle du solde** : EncryptedSharedPreferences n'offre pas de transaction multi-clés ; écrire `balance` + `token_balance_hash` dans **une seule préférence** (un blob : « valeur|hex(hmac) ») pour éviter l'état intermédiaire « balance écrite, hash pas » qui déclencherait le faux positif `SECURE_COMPROMISED` et un reset injuste à 0.

**Warning signs:**
Crashs au démarrage sur appareils restaurés/mises à jour ; rapports `AEADBadTagException` en vitals ; resets de solde signalés par des utilisateurs légitimes (le faux positif HMAC est le symptôme de l'écriture non atomique).

**Phase to address:** Phase Sécurité (couche `data/local/secure`) — l'encapsulation et l'atomicité se décident à la création de `SecureStorageImpl`.

---

### Pitfall 8: AdMob/UMP — ordre du consentement et cycle de vie de la pub récompensée

**What goes wrong:**
Trois échecs classiques : (1) `MobileAds.initialize()` appelé **avant** le flow UMP → dans l'UE/UK, `canRequestAds()` reste false, aucune pub ne se charge, l'économie de tokens est morte silencieusement ; `getConsentStatus() == OBTAINED` ne veut **pas** dire « a consenti » (un refus donne OBTAINED + canRequestAds=false). (2) Pub récompensée montrée depuis un contexte/état invalide ou app passée en arrière-plan pendant l'ad → `onAdFailedToShowFullScreenActivity` → `onUserEarnedReward` jamais appelé → l'utilisateur a regardé la pub sans +10 (arbitraire perçu = désastre). (3) Oublier de basculer des **IDs de test** (`ca-app-pub-3940256099942544~3347511713` / `/5224354917`) vers les IDs de prod : le faux App ID en release est une violation de policy, la prod avec l'ID de test ne monétise pas.

**Why it happens:**
L'ordre correct (consent → canRequestAds → initialize → load → show) est contredis par la plupart des tutos ; les callbacks récompensés sont asynchrones et dépendent du cycle de vie de l'Activity.

**How to avoid:**
1. Séquence canonique à chaque lancement : `ConsentInformation.requestConsentInfoUpdate()` → `loadAndShowConsentFormIfRequired()` → vérifier `canRequestAds()` **dans le chemin de succès ET dans le catch** (erreur réseau ≠ refus) → `MobileAds.initialize()` → précharger la rewarded. En dev (IDs test), le flow UMP n'affiche rien : câbler un flag debug qui court-circuite proprement.
2. Récompense **à l'épreuve du crash** : créditer `updateTokens(+10)` dans `onUserEarnedReward` (callback main thread) et **jamais** dans `onAdDismissed` ; recharger l'ad dans `onAdDismissedFullScreenContent`.
3. Timeout 10 s déjà spécifié (Partie 3) : le respecter et exposer `ad_unavailable` — mais prévoir aussi l'état « Pass actif » pour ne pas bloquer un utilisateur qui ne peut pas voir de pub (hors-ligne).
4. Grep de release : interdire la présence de l'ID de test `3940256099942544` dans un build release (check Gradle/CI).

**Warning signs:**
Fill rate nul dans l'UE ; utilisateurs « pub regardée mais pas de tokens » ; warnings AdMob en console (« app ID mismatch », « consent not gathered »).

**Phase to address:** Phase Monétisation — intégrer UMP+AdManager ensembles, jamais en deux fois.

---

### Pitfall 9: Chaîne FFmpeg de rendu — hardsub .ass, loudnorm et dimensions

**What goes wrong:**
Le rendu (Fonction E/F) casse de plusieurs façons silencieuses : (1) **sous-titres invisibles** : sur Android, libass n'a ni fontconfig ni polices système — sans `fontsdir` pointant vers les polices bundlées et sans `Fontname` correspondant au nom interne de la police (Montserrat-ExtraBold ≠ nom de famille du fichier), le filtre `subtitles=` rend… rien, sans erreur. (2) **Arabe/JA/KO** : sans `shaping=complex` (HarfBuzz) l'arabe est rendu lettres déconnectées ; le karaoké `\K` en RTL est le cas le plus fragile de libass. (3) **loudnorm** : en un seul pass le mode dynamique peut « pomper » sur la parole ; le filtre **upsample l'audio à 192 kHz en interne** — sans `-ar 48000` en sortie, l'AAC sort en 192 kHz (fichiers énormes, lecteurs incompatibles). Paramètres du cahier des charges `I=-16:TP=-1.5:LRA=11` valides (vérifié contre `ffmpeg -h filter=loudnorm` : I∈[-70;-5], TP∈[-9;0], LRA∈[1;50]) ; défaut `I=-24` doit bien être surchargé. (4) **Dimensions** : `w = h×9/16` non arrondi → largeur impaire → x264 en yuv420p refuse (« width not divisible by 2 »). (5) **Seek** : `-ss` avant `-i` sur un MP4 partiel sans `moov` complet → timestamps cassés, sous-titres désynchronisés.

**Why it happens:**
La commande du cahier des charges est correcte sur un desktop avec fontconfig ; l'environnement Android (pas de polices système, pas de shell) change silencieusement la sémantique. L'échec du hardsub est invisible dans les logs.

**How to avoid:**
1. Filtrer avec `subtitles=filename='<chemin absolu>':fontsdir='<dossier polices>':shaping=complex` et **forcer le style** (`force_style='FontName=Montserrat ExtraBold,...'`) — vérifier le nom de famille exact avec l'outil `fc-scan`/TTF au moment du build.
2. Après `loudnorm`, toujours **re-pinner `-ar 48000`** ; pour une qualité constante, faire le pass 1 (`loudnorm=print_format=json` → parser) puis pass 2 avec `measured_I/LRA/TP/thresh` + `linear=true` (deux passes sur un clip de ≤ 60 s coûtent peu).
3. Arrondir le crop à l'**entier pair** (`w = min(srcW, floor(h*9/16) & ~1)`, `x` clampé pair) avant `scale=1080:1920`.
4. Tests dorés : 1 clip par langue (FR/AR/JA/KO) + 1 clip son faible + 1 clip fort, rendus en CI nocturne avec vérification pixel (non-noir) et `ffmpeg loudnorm print_format=summary` en sortie.
5. Ne jamais découper les octets du MP4 soi-même au rendu : passer par `-ss/-t` en décodage (cf. Pitfall 10).

**Warning signs:**
Shorts « réussis » sans aucune lettre rendue (recadrage OK) ; fichiers MP4 avec piste audio 192 kHz ; crashs x264 « height/width not divisible by 2 » ; sous-titres décalés de quelques images.

**Phase to address:** Phase Rendu (Fonctions E/F) — construire un harness FFmpeg testable hors UI dès le début de la phase.

---

### Pitfall 10: HTTP Range sur googlevideo — URLs signées, verrouillées par IP, à durée de vie courte

**What goes wrong:**
Le « Smart Download partiel » (Fonction F) repose sur des URLs de stream googlevideo qui sont **signées, liées à l'IP et expirées (~6 h, paramètre `expire`)**. Conséquences : 403 dès que l'IP change (WiFi→4G, VPN), ou que l'URL est réutilisée après expiration ; **modifier/réordonner un paramètre (`itag`, `mime`, `clen`, signature) invalide la signature** ; le serveur répond 206 tant que l'URL est valide, 403 sinon. Et surtout, **l'offset d'octet ne correspond pas linéairement au temps vidéo** (keyframes, VBR, `moov` atom) : demander « les octets de 124 s à 178 s » par proportion durée/taille produit un fichier illisible ou tronqué.

**Why it happens:**
Le modèle mental « un fichier HTTP qu'on segmente » est faux pour les streams DASH/fMP4 de YouTube : c'est un format indexé où le début du fichier porte les métadonnées.

**How to avoid:**
1. Traiter le Range comme une **optimisation avec repli** : si 403/416/échec de validation → retélécharger le flux complet (10-20 Mo d'audio, flux vidéo d'un Short raisonnable) plutôt que d'échouer.
2. Télécharger **depuis le début** (le `moov` d'abord) puis utiliser `-ss {start} -t {dur}` en décodage pour l'intervalle — jamais de collage d'octets. Si l'optimisation Range est conservée : approximer l'octet de départ, télécharger jusqu'à la fin d'une unité de segment, valider avec `ffprobe`/décodage test, sinon repli complet.
3. Résoudre les URLs de stream **au moment du rendu** (pas 6 h avant) et télécharger le segment immédiatement ; gérer le changement de réseau (OkHttp retry sur nouvelle URL résolue par NewPipeExtractor).
4. Écrire en `.part` + `Content-Range` vérifié à chaque chunk ; never assume `Accept-Ranges` sans le tester (416/200 complet possible).

**Warning signs:**
403 sporadiques corrélés aux changements WiFi/4G ; MP4 injouables uniquement pour les clips « longs » ; fichiers de taille incohérente avec `clen`.

**Phase to address:** Phase Pipeline (Fonctions A/F) — implémenter le repli en même temps que l'optimisation.

---

### Pitfall 11: Annulation native non propagée — coroutines qui tuent le job mais pas le CPU

**What goes wrong:**
L'annulation d'un job Kotlin **n'arrête rien côté natif** : un `FFmpegKit.executeAsync` continue de consommer 100 % CPU tant qu'on n'appelle pas `FFmpegKit.cancel(sessionId)` ; une transcription whisper JNI continue jusqu'au bout si le code natif ne consulte pas un flag d'abort. Résultat : worker « annulé » mais téléphone qui chauffe, batterie vidée, et le FGS reste vivant → conflit avec le quota Android 15 (Pitfall 6).

**Why it happens:**
La règle « annulation propagée job → commande FFmpeg » (Partie 1 §3.3) exige un branchement explicite par session : les wrappers asynchrones d'ffmpeg-kit et les boucles natives whisper n'ont aucun lien automatique avec la coopération coroutine.

**How to avoid:**
1. Dans `FFmpegWrapper` : garder la référence `FFmpegSession` par exécution ; `kotlinx.coroutines` → `invokeOnCancellation { session.cancel() }`.
2. Dans le binding JNI whisper : passer un pointeur/callback d'abort vérifié entre les chunks (`whisper_abort_callback`) ou découper la transcription en chunks et vérifier `isActive` entre chaque (simplifie aussi le checkpointing du Pitfall 5).
3. Test automatisé : lancer un rendu 60 s, annuler à 2 s, vérifier que le CPU retombe à zéro et que le fichier temporaire est purgé (`frames_tmp/`, `.part`).

**Warning signs:**
Processus vivants après « annulation » ; température/batterie anormales avec app en arrière-plan ; notifications de progression qui continuent de bouger après le cancel.

**Phase to address:** Phase Pipeline — câbler l'annulation dans les wrappers dès leur écriture, pas après.

---

### Pitfall 12: Room — index uniques, IGNORE silencieux et migrations interdites à la destruction

**What goes wrong:**
(1) `@Insert(OnConflictStrategy.IGNORE)` sur `VideoProjectEntity` avec l'index unique `youtubeUrl` retourne **-1 sans erreur** : le code naïf croit avoir créé le projet alors que rien n'a été écrit ; le cahier des charges le prévoit (`findByUrl` → retourner l'existant) mais l'oubli est le bug n°1 des schémas à index unique. (2) Sans migration ou avec un schéma désynchronisé, Room lève `IllegalStateException` **au runtime chez l'utilisateur** ; `fallbackToDestructiveMigration` est interdit par le cahier des charges (bonne décision) mais toute version incrémentée sans `Migration`/`AutoMigration` + JSON de schéma archivé = crash en prod. (3) Les requêtes `LIKE '%'||:q||'%'` plantent si `q` contient `%` ou `_` (wildcards SQLite) et `COLLATE NOCASE`/`LOWER()` ne gèrent pas l'Unicode (déjà traité par `titleSearch`). (4) `onDelete = CASCADE` supprime les **lignes** shorts mais pas les **fichiers** MP4 du disque → orphelins hors GC.

**Why it happens:**
Les index uniques et les migrations se manifestent des semaines après leur écriture (à la 2e version de l'app, sur l'appareil d'un utilisateur), jamais pendant le dev greenfield où la base est recréée à chaque install.

**How to avoid:**
1. Toujours : `val id = dao.insert(p); if (id == -1L) { p = dao.findByUrl(url) }` — encapsuler dans le repository avec un test unitaire dédié.
2. Activer le **Room Gradle plugin** (`room { schemaDirectory("schemas") }`) dès la v1, committer les JSON, écrire un `MigrationTestHelper` test « v1 → vN » par release.
3. Échapper la saisie de recherche : `q.escapeForLike()` (`\` → `\\`, `%` → `\%`, `_` → `\_`) + `LIKE ... ESCAPE '\'`.
4. GC : au delete de projet/short, supprimer les fichiers listés dans les colonnes chemin **avant** le delete Room ; tester que `frames_tmp/` et `shorts/ isUnlocked=true` respectent les règles Partie 4.

**Warning signs:**
« Nouveau projet » qui n'apparaît pas ; crashs `IllegalStateException: A migration from X to Y was required` en vitals ; recherche « 100% » qui retourne tout l'historique ; stockage qui gonfle avec des MP4 invisibles dans l'UI.

**Phase to address:** Phase Couche données (Room v1 + plugin schema) ; les migrations se testent à chaque évolution de schéma.

---

### Pitfall 13: ML Kit — mémoire des frames et taille minimale des visages

**What goes wrong:**
ML Kit exige des visages d'au moins **100×100 px** dans l'image d'entrée (200×200 pour les contours) : un frame 1080p downscalé agressivement fait disparaître les visages lointains → crop erratique. À l'inverse, injecter des bitmaps pleine résolution (extraits toutes les 250 ms pendant 60 s = 240 frames) gonfle la mémoire et lente si on utilise `MediaMetadataRetriever` (lent, fuites sur certains codecs). Anciennes versions de la bibliothèque face-detection (16.1.7) sont incompatibles 16 KB (cf. Pitfall 2).

**Why it happens:**
Le pipeline d'extraction (FFmpeg → Bitmap → ML Kit) est composé de trois API dont personne ne documente l'interaction mémoire.

**How to avoid:**
1. Extraire les frames par **FFmpeg** (déjà spécifié — Fonction D), à résolution maîtrisée (ex. échelle max ~1280 de large), en fichiers temporaire purgés après rendu (`frames_tmp/` déjà prévu).
2. Décoder en `BitmapFactory.Options.inSampleSize` calculé pour garder chaque visage ≥ 100 px ; régler `minFaceSize` plus bas (0.05-0.1) ; prendre le **plus grand visage** et fallback « visage centré » si aucun visage détecté (segment sans visage = crop centre, jamais de crash).
3. Réutiliser un `FaceDetector` unique (close() en fin) et traiter les frames séquentiellement ; lisser comme spécifié (moyenne 1 s + interpolation).
4. Version ML Kit récente dans `libs.versions.toml`, vérifiée 16 KB.

**Warning signs:**
OOM sur projets longs ; crop qui « saute » quand le présentateur s'éloigne ; détection à 100 % sur flagships et à 30 % sur appareils milieu de gamme.

**Phase to address:** Phase Pipeline (Fonction D) — protocole de test sur vidéo réelle multi-visages.

---

### Pitfall 14: Deep link `shortify://invite` — injection de paramètres et faux positifs de parrainage

**What goes wrong:**
Un custom scheme est déclenchable par **n'importe quelle app ou page web** (`intent://shortify://invite?ref=...`) : sans liste blanche stricte, un acteur tiers peut déclencher le +20 (auto-fraude à l'économie de tokens), ou injecter un `ref` arbitraire. Le cahier des charges impose déjà la liste blanche (`gift20`) — le piège résiduel est l'implémentation : parser l'Intent **au chaud démarrage aussi** (`onNewIntent`), sinon le lien ne marche qu'à froid ; et traiter `is_first_launch` de façon atomique (deux lancements simultanés → double +20).

**Why it happens:**
Le test se fait toujours depuis un `adb shell am start` au lancement à froid, jamais depuis Chrome ni depuis une app déjà ouverte.

**How to avoid:**
1. Parser strictement : `Uri.getQueryParameter("ref")` ∈ {`gift20`} sinon ignoré ; ne **jamais** exécuter/afficher des données du deep link sans contrôle (déjà exigé Partie 5 §4).
2. Route unique d'entrée : `MainActivity.onNewIntent` + intent de lancement → même `RootViewModel.handleDeepLink()` ; flag `is_first_launch` lu/écrit dans une seule coroutine séquentielle (mutex).
3. Tester : cold start via navigateur, warm start, app en arrière-plan, `ref` invalide, `ref` absent, double fire.

**Warning signs:**
Solde initial 70/90 inexpliqué ; +20 appliqués après le premier lancement ; crash sur `getQueryParameter` si query malformée.

**Phase to address:** Phase Monétisation (avec l'économie de tokens) — le deep link se code avec `updateTokens()`, jamais après.

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Épingler JitPack `-SNAPSHOT`/HEAD pour NewPipeExtractor | « toujours à jour » | Builds non reproductibles, casse sans changement local, impossible de bisect | **Jamais** |
| Laisser l'ID d'app AdMob de test en release | Évite la création d'ID prod | Aucune monétisation + violation policy AdMob | **Jamais** (l'inverse — IDs prod en dev — est aussi une violation) |
| `fallbackToDestructiveMigration()` | 30 s de gain par changement de schéma | Perte silencieuse des projets utilisateur | **Jamais** (interdit par la Partie 4) |
| FFmpeg commandes en strings inline dans les Workers | Rapide à écrire | Aucune testabilité, escaping erratique, régression de rendu invisible | Seulement derrière `FFmpegWrapper` avec builder + tests dorés |
| Transcription sans checkpoints (un seul appel natif) | Simplicité du premier POC | Reprise impossible après mort du process ; 15 min à refaire | Jamais en prod ; toléré pour le spike technique initial |
| Skip de la vérification SHA-256 des modèles GGML | Un écran de moins | Modèle corrompu → crash natif inexpliqué weeks plus tard | Jamais (exigé Partie 5 §4) |
| Solde + hash dans deux clés ES séparées | Code « propre » à deux getters | Fenêtre de crash = faux `SECURE_COMPROMISED` + reset à 0 | Jamais — un seul blob atomique |
| Bitmaps pleine résolution vers ML Kit | Aucun code de downscale | OOM et détection lente sur appareils réels | Jamais |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Maven Central (ffmpeg-kit) | Copier l'ancien `com.arthenica:...` d'un tutoriel 2024 | `dev.ffmpegkit-maintained:ffmpeg-kit-full-gpl` + checksum + note de décision dans `libs.versions.toml` |
| JitPack (NewPipeExtractor) | Dépendance sans tag ou tag `master` | Tag exact (`v0.26.x`) ; veille sur les releases ; wrapper isolé |
| UMP SDK | `MobileAds.initialize()` dans `Application.onCreate()` inconditionnel | Consent d'abord, `canRequestAds` (succès **et** erreur), puis initialize ; à chaque lancement |
| AdMob rewarded | Créditer dans `onAdDismissedFullScreenContent` | Créditer dans `onUserEarnedReward` (main thread) ; recharger l'ad au dismiss |
| MediaStore (export) | Écrire sans vérifier l'espace, oublier `IS_PENDING` | Insert avec `IS_PENDING=1`, copy, `IS_PENDING=0` ; vérifier l'espace disque avant rendu → `DB_DISK_FULL` |
| Gemini API (BYOK) | Clé dans les logs d'erreur réseau/interceptors | Interceptor dédié qui redact la clé ; timeouts ; ne jamais logger le body d'erreur complet |
| Hugging Face (modèles GGML) | Télécharger sans checksum ni reprise | SHA-256 hard-codé (Partie 5), download resumable, suppression si échec |
| OkHttp Range | Supposer `Accept-Ranges` et 206 garantis | Valider `Content-Range`, repli téléchargement complet sur 403/416/200 |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Transcription 15 min en un seul appel natif | Téléphone brûlant, batterie −20 %, vitesse qui s'effondre en fin de course | Chunks + checkpoints + sonde thermique | Vidéos > 5 min, appareils milieu de gamme |
| `MediaMetadataRetriever.getFrameAtTime` en boucle (4 fps × 60 s) | Extraction 10× plus lente que le rendu lui-même | Extraction FFmpeg batch en fichiers temp | Dès 30 s de clip |
| Bitmaps 1080p × 240 frames en mémoire | OOM (250+ Mo) sur appareils 4 Go | Décoder 1 frame à la fois, `inSampleSize`, purge `frames_tmp/` après rendu | Dès 15-20 frames retenues |
| `small` (900 Mo RAM) par défaut sur appareil 4 Go | Kills process/LMK pendant `Transcribing` | Sélection de modèle guidée par `ActivityManager.memoryClass` | Appareils ≤ 4 Go |
| loudnorm 1 pass dynamique + upsample 192 kHz non corrigé | Fichiers MP4 gonflés, pompage du son, players qui butent | 2 passes (`measured_*`) + `-ar 48000` explicite | À chaque rendu |
| Rendu (Fonction F) lancé en parallèle de la transcription | Contention CPU, thermal spike, ANR du worker | Séquentialiser le pipeline (machine à états l'impose déjà) | Projets multi-Shorts |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Comparer le HMAC avec `==`/`equals` | Timing attack théorique sur le solde signé | `MessageDigest.isEqual()` (déjà exigé Partie 5 — vérifier à la review) |
| `balance` et `token_balance_hash` dans deux préférences | Crash entre 2 writes → faux positif → reset injuste à 0 | Un seul blob signé, écriture atomique unique |
| Ignorer `AEADBadTagException` à l'ouverture d'ES | Crash au lancement après restauration/update d'OS | Catch + reconstruction contrôlée + parcours `SECURE_COMPROMISED` |
| Clé Gemini dans `WorkManager.Data`, logs, ou exports ZIP | Fuite de credential (les Data peuvent être dumpées, les logs collectés) | Interdiction absolue (Partie 5) + check CI grep sur le payload builder |
| Trust du paramètre `ref` du deep link | Fraude au +20, comportement piloté par une app tierce | Liste blanche unique (`gift20`), parsing défensif, mutex sur `is_first_launch` |
| Filigrane/unlock contournables en lisant `outputMp4Path` dans la DB | Shorts payants lus avant débit | Le MP4 haute qualité n'existe sur disque qu'après débit (le preview basse qualité/ watermark est le seul fichier avant unlock) |
| Presenter le HMAC comme « inviolable » dans la comms produit | Réputation (cf. Partie 5 §7 qui l'interdit déjà) | Respecter la transparence : anti-manipulation naïve uniquement |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| POST_NOTIFICATIONS refusée → plus aucune visée de progression du pipeline | L'app semble « figée » pendant 10 min ; kills d'app massifs | Demander POST_NOTIFICATIONS avec contexte juste avant le 1er pipeline ; progression toujours visible in-app (Écran 2) |
| Transcription 15 min sans ETA ni étape visible | Abandon avant la fin, avis « ça ne marche pas » | Progression par chunk whisper + estimation restante (temps écoulé/percentile) |
| Modèle Whisper absent → redirection forcée Paramètres | Utilisateur perdu, téléchargement 57 Mo sans Wi-Fi check | Message clair taille + réseau, reprise auto du pipeline après download du modèle |
| Pub indisponible (offline) = analyse bloquée sans alternative | Frustration maximale, uninstall | État `ad_unavailable` clair + rattrapage : le Pass 24h ou la récompense au retour en ligne |
| Double attente : preview filtrée puis re-rendu au débit −3 | « J'ai déjà attendu, pourquoi encore 2 min ? » | Préparer le rendu haute qualité en arrière-plan dès la preview (si tokens probables) ou afficher une ETA honnête sur le bouton |
| Karaoké arabe mal shapé (lettres déconnectées) | Produit perçu « bugué » sur un marché entier (AR) | `shaping=complex` + clip de test AR dans les dorés de rendu |

## "Looks Done But Isn't" Checklist

- [ ] **Rendu karaoké :** souvent absent — vérifier qu'une lettre est réellement rendue (screenshot pixel-diff) avec la police bundlée, pas seulement « la commande retourne 0 »
- [ ] **Arabe (RTL) :** souvent cassé — vérifier shaping complex + `\K` + icônes flippées sur un vrai clip AR
- [ ] **Reprise pipeline :** souvent non testée — tuer le process à chaque étape (adb kill) et vérifier la reprise au checkpoint + un seul débit de tokens
- [ ] **16 KB :** souvent non vérifié — lancer le check d'alignement ELF en CI + smoke test émulateur 16 KB
- [ ] **Annulation :** souvent un fake — annuler à 2 s et vérifier CPU ≈ 0 et purge des temporaires
- [ ] **Consent UMP :** souvent « marche en dev » (IDs test ne déclenchent rien) — tester le flow EEA simulé (DebugGeography) en pré-prod
- [ ] **Export MediaStore :** souvent non testé sans permission stockage — vérifier IS_PENDING, disque plein → `DB_DISK_FULL`
- [ ] **GC 7 jours :** souvent jamais déclenché — manipuler `lastAccessedAt` et vérifier fichiers supprimés + `sourceStatus` + préservation des `isUnlocked=true`
- [ ] ** faux positif HMAC :** souvent invisible — simuler un kill entre deux writes et vérifier qu'aucun reset ne se produit
- [ ] **Migrations Room :** souvent non archivées — vérifier `schemas/` commité + test v1→vN vert avant chaque release

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| ffmpeg-kit indisponible (décidé en cours de projet) | LOW | Basculer sur `dev.ffmpegkit-maintained` (API identique) ; vendor l'.aar en secours ; 1 j max |
| NewPipeExtractor cassé en prod | LOW-MEDIUM | Release hotfix avec nouveau tag extractor ; en attendant, état `Error` propre + re-téléchargement ; canal beta pour pré-valider |
| App retirée du Play (policy YouTube) | HIGH | Appel immédiat ; bascule distribution directe (APK signé, site) ; repositionnement du listing ; ne jamais re-soumettre identique sans changement |
| Crash 16 KB en prod | MEDIUM | Identifier la `.so` fautive (stack `dlopen`), swap de version alignée, hotfix ; en attendant, exclure les appareils 16 KB n'est pas possible → priorité absolue |
| ES illisible (AEADBadTag) | LOW | Catch global, reset contrôlée, informer l'utilisateur ; sans catch = reviews 1 étoile en masse après chaque restore |
| Quota FGS 6 h épuisé (Android 15) | LOW | Arrêt propre au `onTimeout` ; replanification en work non-foreground ; resserrage des retry (Pitfall 6) |
| Faux `SECURE_COMPROMISED` en masse | MEDIUM | Patch de l'écriture atomique ; comms ; on ne peut pas « rembourser » sans serveur — d'où la prévention absolue |
| Rendu désaligné (sous-titres décalés) | LOW | Corriger le seek (`-ss` décodage) ; les tests dorés par langue détectent avant release |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1. ffmpeg-kit mort | Échafaudage (Phase 1) | Build vert avec coordonnées fork + checksum documenté |
| 2. 16 KB page size | Échafaudage + toutes phases natives | Check ELF alignment en CI ; smoke test émulateur 16 KB |
| 3. NewPipeExtractor fragile | Phase Pipeline (Fonction A) | Wrapper isolé + tag épinglé + procédure hotfix documentée |
| 4. Policy Play / YouTube | Décision de milestone + pré-release | Décision de distribution actée ; listing review-proof ; plan APK direct |
| 5. Whisper RAM/thermique | Phase Pipeline (Fonction B) | Benchmark 15 min sur appareil 4-6 Go : real-time factor début/fin mesuré |
| 6. Process death / quota FGS | Phase Pipeline (orchestration) | Test kill à chaque étape ; reprise au checkpoint ; 1 seul débit ; types FGS corrects |
| 7. security-crypto dépréciée | Phase Sécurité | `SecureStorageImpl` avec pin alpha06 + catch AEADBadTag + blob atomique (test kill entre writes) |
| 8. AdMob/UMP ordre | Phase Monétisation | Flow EEA simulé : consent → initialize → fill ; récompense créditée sur `onUserEarnedReward` uniquement |
| 9. Chaîne FFmpeg (hardsub/loudnorm/crop) | Phase Rendu (E/F) | Tests dorés par langue (FR/AR/JA/KO) + clips son faible/fort ; `-ar 48000` vérifié |
| 10. Range googlevideo | Phase Pipeline (A/F) | Tests 403 (changement réseau), URL expirée, repli full download |
| 11. Annulation native | Phase Pipeline (wrappers) | Test cancel à 2 s : CPU ≈ 0, temporaires purgés |
| 12. Room uniques/migrations | Phase Couche données | Test insert-conflit ; plugin schema + JSON commités ; test LIKE avec `%` |
| 13. ML Kit mémoire/visages | Phase Pipeline (Fonction D) | Clip multi-visages réels ; fallback sans visage ; profil mémoire |
| 14. Deep link injection | Phase Monétisation | Tests cold/warm start, `ref` invalide, double fire |

## Sources

- **ffmpeg-kit retirement (HIGH)** : [Saying Goodbye to FFmpegKit — Taner Sener, 6 jan 2025](https://tanersener.medium.com/saying-goodbye-to-ffmpegkit-33ae939767e1) ; [github.com/arthenica/ffmpeg-kit (archivé)](https://github.com/arthenica/ffmpeg-kit) ; suppression Maven Central 1er avril 2025 confirmée par [ffmpegkit-maintained/ffmpeg-kit](https://github.com/ffmpegkit-maintained/ffmpeg-kit) et [dev.to — fix Android build après retrait](https://dev.to/lucquebec/ffmpegkit-is-retired-heres-how-to-fix-your-android-build-ndk-r26c-patch-35j0) ; discussion [r/androiddev](https://www.reddit.com/r/androiddev/comments/1i25lzo/ffmpegkit_is_being_retired_are_there_any/)
- **16 KB page size (HIGH)** : [developer.android.com/guide/practices/page-sizes](https://developer.android.com/guide/practices/page-sizes) ; échéance Play 1er nov. 2025 ([discussion DelphiPraxis](https://en.delphipraxis.net/topic/14434-android-16-kb-page-sizes-support/), [guide Medium](https://medium.com/softaai-blogs/android-16-kb-page-size-a-complete-developers-guide-to-faster-apps-4ced47f1fe55)) ; incompatibilité ML Kit 16.1.7 : [googlesamples/mlkit#1024](https://github.com/googlesamples/mlkit/issues/1024)
- **NewPipeExtractor (HIGH)** : [Releases TeamNewPipe/NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor/releases) (v0.25.1 refactor breaking ; fixes YouTube v0.26.x) ; [NewPipeExtractor#1185 « Watch on the latest version »](https://github.com/TeamNewPipe/NewPipeExtractor/issues/1185) ; [NewPipe#12930 network errors](https://github.com/TeamNewPipe/NewPipe/issues/12930) ; [JitPack](https://jitpack.io/p/teamnewpipe/newpipeextractor)
- **FGS / WorkManager (HIGH)** : [FGS types requis (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required) ; [FGS service types](https://developer.android.com/develop/background-work/services/fgs/service-types) ; [FGS timeouts (6 h dataSync/mediaProcessing, Android 15)](https://developer.android.com/develop/background-work/services/fgs/timeout) ; [Long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running) ; [Define work (retry/backoff/expedited)](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) ; ré-exécution après process death : [SO — unique work re-run](https://stackoverflow.com/questions/57440835/android-workmanager-resending-unique-task-while-it-is-running)
- **whisper.cpp on-device (MEDIUM)** : [whisper.cpp#959 (Android plus lent)](https://github.com/ggerganov/whisper.cpp/issues/959) ; [whisper.cpp#272 (mémoire = poids + KV + buffers)](https://github.com/ggml-org/whisper.cpp/issues/272) ; [discussion #3567 (latence croissante/ANR)](https://github.com/ggml-org/whisper.cpp/discussions/3567) ; [ProAndroidDev — on-device Whisper](https://proandroiddev.com/from-cloud-llm-to-on-device-whisper-turning-speech-into-structured-actions-on-android-19791f383f4e) ; [Thermal mitigation AOSP](https://source.android.com/docs/core/power/thermal-mitigation)
- **Room (HIGH)** : [developer.android.com — migrer les versions de DB](https://developer.android.com/training/data-storage/room/migrating-db-versions) (migration manquante = IllegalStateException ; exportSchema ; AutoMigrationSpec ; MigrationTestHelper)
- **security-crypto (HIGH)** : [AndroidX releases security](https://developer.android.com/jetpack/androidx/releases/security) ; dépréciation alpha07 (avril 2025) : [ProAndroidDev — Goodbye EncryptedSharedPreferences](https://proandroiddev.com/goodbye-encryptedsharedpreferences-a-2026-migration-guide-4b819b4a537a), [r/androiddev](https://www.reddit.com/r/androiddev/comments/1k2crqv/handling_encryptedsharedpreferences_recent/), [SO](https://stackoverflow.com/questions/78362124/is-the-androidx-securitysecurity-crypto-module-being-deprecated-or-not-what-is) ; fork [ed-george/encrypted-shared-preferences](https://github.com/ed-george/encrypted-shared-preferences)
- **AdMob/UMP (HIGH)** : [Set up UMP SDK — Android](https://developers.google.com/admob/android/privacy) (canRequestAds gate ; OBTAINED ≠ consenti : [groupe AdMob SDK](https://groups.google.com/g/google-admob-ads-sdk/c/Dydp-cWcOIo)) ; [Ads Developer Blog — UMP updates](http://ads-developers.googleblog.com/2023/08/weve-made-updates-to-user-messaging.html)
- **Policy YouTube/Play (HIGH)** : [YouTube API Developer Policies (download interdit)](https://developers.google.com/youtube/terms/developer-policies) ; [YouTube — Enforcement on Third Party Apps](https://support.google.com/youtube/thread/269521462/enforcement-on-third-party-apps) ; [Play — retraits/suspensions](https://support.google.com/googleplay/android-developer/answer/2477981) ; bannissement « Downloader » 2023 ([r/Android](https://www.reddit.com/r/Android/comments/13py6j8/google_bans_downloader_app_after_tv_firms/)) ; stats Play 2024/2025 ([blog.google](https://blog.google/security/how-we-kept-google-play-android-app-ecosystem-safe-2024/))
- **FFmpeg filters (HIGH — vérifié localement contre ffmpeg 9.0 `-h filter=loudnorm` / `-h filter=subtitles` + docs ffmpeg.org)** : [ffmpeg-filters.html — subtitles (libass, fontsdir, force_style, shaping)](https://ffmpeg.org/ffmpeg-filters.html#subtitles-1), [loudnorm (I/LRA/TP/measured_*, linear)](https://ffmpeg.org/ffmpeg-filters.html#loudnorm-1) ; upsample 192 kHz loudnorm : [trac FFmpeg — Loudnessnorm wiki](https://trac.ffmpeg.org/wiki/AudioVolume)
- **googlevideo/Range (MEDIUM)** : [Super User — expiration URL YouTube (~6 h, IP-lock)](https://superuser.com/questions/1823181/how-long-the-url-of-a-youtube-video-last-before-expiring), [Super User — comment youtube-dl fonctionne](https://superuser.com/questions/1467872/how-youtube-dl-works) ; [MDN — 206 Partial Content](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Status/206) ; 403 yt-dlp : [issue #15586](https://github.com/yt-dlp/yt-dlp/issues/15586)
- **ML Kit (MEDIUM)** : [Detect faces with ML Kit on Android (100×100 px min)](https://developers.google.com/ml-kit/vision/face-detection/android)

---
*Pitfalls research for: ShortifyLocal AI — on-device AI video clipping (Android natif)*
*Researched: 2026-09-04*
