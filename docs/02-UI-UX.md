# CAHIER DES CHARGES — ShortifyLocal AI
## Partie 2 : Interface Utilisateur (UI) & Expérience Utilisateur (UX)
### Version Kotlin Android Natif — Jetpack Compose — Multi-régions : FR, EN, ES, IT, AR, JA, KO

---

## 0. Règles de lecture pour l'Agent de Codage

1. **UI 100% Jetpack Compose** (Material 3 thématisé). Aucune vue XML hors `MainActivity`.
2. **MVVM** (règles de la Partie 1) : chaque écran = 1 `@HiltViewModel` + `StateFlow<XUiState>` ; les Composables sont purement déclaratifs ; événements one-shot via `SharedFlow<UiEvent>`.
3. **INTERDICTION FORMELLE DU NEUMORPHISME** : pas d'ombres doubles clair+sombre, pas d'éléments de la même couleur que le fond, pas d'effet "extrudé/moulé". Le Design System est **"Soft-Clean / Soft UI"** : surfaces distinctes du fond + **UNE seule ombre douce verticale** par surface (§1).
4. **Toutes les chaînes UI** passent par les ressources Android localisées (§8). Aucune chaîne en dur dans le code.
5. **RTL** : toutes les alignments via `Alignment.Start/End` / `Arrangement.Start/End` ; jamais `Left/Right`. Icônes directionnelles flippées selon `LocalLayoutDirection` (§1.6).
6. Renvois : tokens/AdMob → Partie 3 ; Room/recherche → Partie 4 ; stockage sécurisé → Partie 5 ; pipeline A→F → Partie 1.

---

## 1. Design System "Soft-Clean / Soft UI"

### 1.1 Tokens de couleurs
| Token | Clair (défaut) | Sombre |
|---|---|---|
| `Background` | `#EEF0F2` | `#0E0E10` |
| `Card` | `#FFFFFF` | `#1A1A1E` |
| `PrimaryText` | `#1A1A1A` | `#FFFFFF` |
| `SecondaryText` | `#8A8F98` | `#9A9AA2` |
| `PrimaryButton` (boutons primaires, bottom nav clair) | `#111111` (texte `#FFFFFF`) | `#FFFFFF` (texte `#000000`) |
| `AccentActive` (états actifs : onglet, tag sélectionné, liens, score moyen) | `#E4590C` | `#FF7A3D` |
| `ScoreExcellent` | `#22C55E` | `#22C55E` |

### 1.2 Ombres (UNE seule, verticale, douce)
| Mode | Couleur | Blur | Offset |
|---|---|---|---|
| Clair | noir **5%** | 24 | (0, 8) |
| Sombre | noir **30%** | 16 | (0, 6) |

Implémentation : `Modifier.shadow()` Material 3 ou `drawBehind` custom avec ces valeurs exactes. **Jamais deux ombres superposées.**

### 1.3 Rayons de courbure
| Élément | Radius |
|---|---|
| Grandes cartes, sections, modales | **32** |
| Boutons, chips, champs de saisie | **16** |
| Images / miniatures | **24** |
| Bottom nav (pilule flottante) | **28** |
| Boutons circulaires | cercle parfait (50%) |

### 1.4 Typographie & polices par écriture
- Titres importants : **w800** (ExtraBold) — grand titre Accueil 28-32sp ; titres d'écran/bannière 24sp ; titres de cartes 14-16sp.
- Corps de texte : **w500** (Medium) — 14-16sp.
- Polices **bundlées dans `res/font`** (offline-first, pas de téléchargement runtime) :
  - FR/EN/ES/IT (LTR latin) : **Inter** (w500/w800)
  - AR (RTL) : **Cairo**
  - JA : **Noto Sans JP**
  - KO : **Noto Sans KR**
- Sélection automatique via la locale active (`LocaleHelper` + `Typography` Compose dynamique).

### 1.5 Thème clair/sombre
- Bascule globale via état global (ViewModel racine + `CompositionLocal`), **persistée dans DataStore**.
- Appliquée via `MaterialTheme(colorScheme = ...)` construit depuis les tokens §1.1.

### 1.6 Règles RTL (Arabe)
- `android:supportsRtl="true"` ; changement de langue via `AppCompatDelegate.setApplicationLocales` (langue par application, natif Android 13+), persisté.
- Icônes directionnelles **obligatoirement flippées** en RTL : flèche de validation du lien, bouton retour, flèche "Voir tout", flèche du bouton primaire.
- Icônes non directionnelles (recherche, lune/soleil, globe, play) : **non flippées**.
- Position des icônes de champ : alignement `Start` automatique (gauche LTR / droite RTL).

---

## 2. Composants du Design System (`presentation/components/`)

Chaque composant doit exister en fichier propre avec `@Preview` clair / sombre / RTL.

| Composant | Spécification |
|---|---|
| `SoftCard` | Fond `Card`, radius 32, ombre §1.2, padding 16 |
| `SoftInsetField` | Fond `Card`, radius 16, hauteur 56, bordure interne 1dp noir 3% (clair) / blanc 5% (sombre), texte 16sp w500, placeholder `SecondaryText` |
| `SoftCircleIconButton` | Cercle 48dp, fond `Card` (ou blanc 70% sur image), ombre §1.2, icône 22dp |
| `TokenPill` | `SoftCard` radius 16 horizontal : icône 🪙 + solde w800 (format §8 `tokens_balance`) + séparateur + bouton `+` circulaire 28dp fond `PrimaryButton` texte blanc → ouvre le bottom sheet AdMob (Partie 3) |
| `TagChip` | Radius 16 ; non sélectionné : fond `Card`, texte `SecondaryText` ; sélectionné : fond `AccentActive` 10%, texte + bordure 1dp `AccentActive` |
| `AddTagChip` | `TagChip` avec icône `+` `AccentActive` → AlertDialog (§4) |
| `SectionHeader` | Titre w800 18sp + lien `see_all` + flèche directionnelle `AccentActive` w500 |
| `ImageProjectCard` | Miniature radius 24 (aspect ~1:1.1) ; badge `🎥 N` en haut **End** (fond noir 40%, radius 12, texte blanc 12sp) ; bas : titre blanc w800 14sp (2 lignes max) sur dégradé linéaire transparent → noir 70% |
| `ShortRowCard` | `SoftCard` radius 24, ligne : miniature 9:16 72×128dp radius 12 + icône play centrée (blanc sur noir 40%) ; colonne : titre w800 16sp (2 lignes max) + score 14sp coloré (§5) |
| `InfoBadgeCard` | `SoftCard` radius 16, ligne : icône dans carré doux 40dp (fond `AccentActive` 10%) + colonne (label 12sp `SecondaryText`, valeur w800 16sp) |
| `BottomNavPill` | Pilule flottante (margin 16), radius 28, fond `#111111` (clair) / `#1A1A1E` (sombre) + ombre ; 3 destinations ; icône inactive `#8A8F98` ; icône active blanche dans cercle 40dp dégradé `#E4590C → #FF7A3D` |
| `PrimaryBlackButton` | Hauteur 56, radius 16, fond `PrimaryButton`, texte w800 16sp + icône flèche directionnelle en **End** (flippée RTL) ; pression : scale 0.98 |

---

## 3. Navigation

- **NavGraph Compose** : 3 destinations d'onglets (Accueil, Historique, Paramètres) + route `detail/{videoId}` (hors onglets).
- `BottomNavPill` masquée sur l'écran Détails.
- Lecteur vidéo = `ModalBottomSheet` glissante (`ShortPlayerSheet`), aspect **9:16**, lecteur **Media3 ExoPlayer**.

---

## 4. Écran 1 — Accueil (`HomeScreen`)

De haut en bas :

1. **Header** : `Start` = avatar circulaire épuré + salutation dynamique selon l'heure (`greeting_morning/afternoon/evening`, §8) ; `End` = `TokenPill`.
2. **Grand titre** ExtraBold 2 lignes : clé `home_title`.
3. **Section lien YouTube** (noyau de l'écran) : `SoftInsetField` placeholder `paste_link` + `SoftCircleIconButton` noir (blanc en sombre) avec flèche de validation directionnelle (flip RTL). Clic → validation Regex YouTube (Partie 1, Fonction A) → si valide : règles de débit (Partie 3) → lancement `PipelineWorker` → navigation vers Écran 2.
4. **Gestionnaire de tags** : rangée horizontale scrollable ; **premier élément = `AddTagChip`** → `AlertDialog` (placeholder `new_tag`) → insertion Room (Partie 4) ; tags multi-sélectionnables (`TagChip`), la sélection s'applique au prochain projet analysé.
5. **Section "Dernières réalisations"** : `SectionHeader` (titre `recent_projects`, lien `see_all` → Historique) ; grille fixe 2 colonnes, **2 cartes max** (les plus récentes) ; `ImageProjectCard` ; clic → Écran 2. Si vide → texte `empty_home`.
6. **`BottomNavPill`** flottante.

**`HomeUiState`** : `balance`, `greeting`, `tags`, `selectedTagIds`, `recentProjects (≤2)`, `isAdLoading`.

---

## 5. Écran 2 — Détails & Shorts (`DetailScreen`)

De haut en haut :

1. **Bannière** (~moitié haute) : miniature YouTube `cover` radius 32 ; `Start` haut = `SoftCircleIconButton` retour (fond blanc semi-transparent, icône flippée RTL) ; `End` haut = **`SoftCircleIconButton` partage** → `ACTION_SEND` (texte `share_app`) → `ShareRewardUseCase` (+5/24h, Partie 3 ; blocage silencieux si < 24h) ; bas = titre complet blanc w800 24sp sur dégradé noir.
2. **Deux `InfoBadgeCard`** : `⏱️` + `duration_badge` ; `🔥` + `shorts_count`.
3. **État pipeline** (si analyse en cours) : stepper de progression affichant les libellés `st_*` (§8) selon la machine à états de la Partie 1 ; état `st_error` avec bouton retry.
4. **Liste des Shorts** triée `viralityScore` DESC : `ShortRowCard` ; score affiché `[Score]% (label)` : **≥ 75** → `ScoreExcellent` + `score_excellent` ; **< 75** → `AccentActive` + `score_average`. Clic ligne ou play → `ShortPlayerSheet` (Media3, 9:16).
5. **Filigrane** : tant que `isUnlocked == false`, overlay texte oblique semi-transparent clé `watermark` sur le lecteur.
6. **Bouton fixe bas** `PrimaryBlackButton` : libellé `download_short`. Logique de clic strictement définie en **Partie 3** (solde < 3 → dialog `insufficient_tokens` ; sinon débit −3, retrait du filigrane, rendu haute qualité, export galerie MediaStore).

**`DetailUiState`** : `project`, `shorts (triés)`, `pipelineState`, `balance`.

---

## 6. Écran 3 — Historique (`HistoryScreen`)

1. **Barre de recherche** : `SoftInsetField` + icône recherche alignée `Start` ; placeholder `search_projects` ; `onChanged` avec **debounce 300 ms** → requête Room `LIKE` insensible à la casse (Partie 4).
2. **Grille complète** (`LazyVerticalGrid` 2 colonnes) : cartes = miniature, titre, **date locale de création** (format medium selon locale), **mini-badges de tags** en bas (fond `colorHex` 15%, texte `colorHex`). Clic → Écran 2.
3. Si aucun résultat → texte `empty_history`.

---

## 7. Écran 4 — Paramètres (`SettingsScreen`)

Liste de `SoftCard` catégorisées (en-têtes `cat_interface`, `cat_ai`, `cat_pro`, §8) :

### Catégorie "Interface"
- **Mode Sombre** : tuile icône lune/soleil + `Switch` → bascule thème global, persisté DataStore. Libellé `dark_mode`.
- **Langue** : tuile icône globe + sélecteur (7 langues : `fr, en, es, it, ar, ja, ko` affichées en natif : Français, English, Español, Italiano, العربية, 日本語, 한국어). Sélection → mise à jour immédiate de la locale + persistance ; si `ar` → bascule RTL instantanée automatique. Libellé `language`.

### Catégorie "Intelligence Artificielle"
- **Clé API Gemini** : champ masqué par défaut (type password + icône œil pour révéler), placeholder `gemini_placeholder` ; valeur stockée chiffrée (Partie 5) ; bouton `test_key` → appel Gemini minimal → snackbar `key_ok` / `key_fail`.
- **Whisper Manager** : liste des modèles **Tiny (~39 Mo) / Base (~57 Mo) / Small (~184 Mo)** ; si absent : bouton `download_model` (avec progression) ; si présent : coche verte + bouton suppression (`delete_model`).

### Catégorie "Options Professionnelles"
- **Export projet complet** : `Switch` libellé `export_full` + sous-titre `export_full_desc` ; si actif, l'export génère un **ZIP** (MP4 + WAV + ASS/SRT) via `java.util.zip` (Partie 1, Fonction F).

---

## 8. Annexe i18n — 7 langues (FR, EN, ES, IT, AR, JA, KO)

Fichiers : `values/` (FR défaut), `values-en`, `values-es`, `values-it`, `values-ar`, `values-ja`, `values-ko`.
Format : `clé` → FR | EN | ES | IT | AR | JA | KO

- `tokens_balance` → 🪙 %d Tokens | idem | idem | idem | 🪙 %d رموز | 🪙 %d トークン | 🪙 %d 토
- `home_title` → Que voulez-vous créer aujourd'hui ? | What do you want to create today? | ¿Qué quieres crear hoy? | Cosa vuoi creare oggi? | ماذا تريد أن تنشئ اليوم؟ | 今日は何を作りますか？ | 오늘 무엇을 만들고 싶으신가요?
- `greeting_morning` → Bonjour | Good morning | Buenos días | Buongiorno | صباح الخير | おはようございます | 좋은 아침입니다
- `greeting_afternoon` → Bon après-midi | Good afternoon | Buenas tardes | Buon pomeriggio | طاب يومك | こんにちは | 좋은 오후입니다
- `greeting_evening` → Bonsoir | Good evening | Buenas noches | Buonasera | مساء الخير | こんばんは | 좋은 저녁입니다
- `tab_home` → Accueil | Home | Inicio | Home | الرئيسية | ホーム | 홈
- `tab_history` → Historique | History | Historial | Cronologia | السجل | 履歴 | 기록
- `tab_settings` → Paramètres | Settings | Ajustes | Impostazioni | الإعدادات | 設定 | 설정
- `paste_link` → Coller le lien de votre vidéo YouTube ici... | Paste your YouTube video link here... | Pega el enlace de tu video de YouTube aquí... | Incolla qui il link del tuo video di YouTube... | قم بلصق رابط فيديو يوتيوب هنا... | ここにYouTube動画のリンクを貼り付けてください... | 여기에 YouTube 동영상 링크를 붙여넣으세요...
- `new_tag` → Nouveau tag (ex: #Business) | New tag (e.g., #Business) | Nueva etiqueta (ej: #Negocios) | Nuovo tag (es: #Business) | وسم جديد (مثال: #ريادة_أعمال) | 新規タグ (例: #ビジネス) | 새 태그 (예: #비즈니스)
- `recent_projects` → Dernières réalisations | Recent Projects | Proyectos recientes | Progetti recenti | آخر المشاريع | 最近のプロジェクト | 최근 프로젝트
- `see_all` → Voir tout | See all | Ver todo | Vedi tutto | عرض الكل | すべて表示 | 전체 보기
- `empty_home` → Aucune réalisation pour le moment. Collez un lien YouTube pour commencer ! | No projects yet. Paste a YouTube link to get started! | Aún no hay proyectos. ¡Pega un enlace de YouTube para empezar! | Nessun progetto per ora. Incolla un link di YouTube per iniziare! | لا توجد مشاريع بعد. الصق رابط يوتيوب للبدء! | まだプロジェクトがありません。YouTubeリンクを貼り付けて始めましょう！ | 아직 프로젝트가 없습니다. YouTube 링크를 붙여넣고 시작하세요!
- `empty_history` → Aucun projet trouvé. | No projects found. | No se encontraron proyectos. | Nessun progetto trovato. | لم يتم العثور على مشاريع. | プロジェクトが見つかりません。 | 프로젝트를 찾을 수 없습니다.
- `duration_badge` → ⏱️ %s min | idem | idem | idem | ⏱️ %s دقيقة | ⏱️ %s 分 | ⏱️ %s 분
- `shorts_count` → 🔥 %d Shorts | idem | idem | 🔥 %d Clip | 🔥 %d مقاطع | 🔥 %d クリップ | 🔥 %d 클립
- `score_excellent` → (Excellent) | (Excellent) | (Excelente) | (Eccellente) | (ممتاز) | (優秀) | (우수)
- `score_average` → (Moyen) | (Average) | (Medio) | (Medio) | (متوسط) | (平均的) | (보통)
- `watermark` → APERÇU | PREVIEW | VISTA PREVIA | ANTEPRIMA | معاينة | プレビュー | 미리보기
- `download_short` → Télécharger ce Short (Déduira 3 🪙) | Download this Short (Costs 3 🪙) | Descargar este Short (Costará 3 🪙) | Scarica questo Short (Costerà 3 🪙) | تحميل هذا المقطع (سيخصم 3 🪙) | このショートをダウンロード (3 🪙を消費します) | 이 쇼츠 다운로드 (3 🪙 소모)
- `insufficient_tokens` → Tokens insuffisants ! Regardez une publicité pour obtenir +10 Tokens immédiatement ? | Not enough tokens! Watch an ad to get +10 Tokens immediately? | ¡Falta de tokens! ¿Ver un anuncio para obtener +10 tokens de inmediato? | Token insufficienti! Guardare una pubblicità per ottenere subito +10 Token? | الرموز غير كافية! هل تريد مشاهدة إعلان للحصول على +10 رموز فوراً؟ | トークンが不足しています！広告を視聴してすぐに+10トークンを獲得しますか？ | 토이 부족합니다! 광고를 시청하고 즉시 +10 토을 받으시겠습니까?
- `search_projects` → Rechercher un projet... | Search projects... | Buscar un proyecto... | Cerca un progetto... | بحث عن مشروع... | プロジェクトを検索... | 프로젝트 검색...
- `dark_mode` → Mode Sombre | Dark Mode | Modo Oscuro | Modalità Scura | الوضع الداكن | ダークモード | 다크 모드
- `language` → Langue | Language | Idioma | Lingua | اللغة | 言語 | 언어
- `cat_interface` → Interface | Interface | Interfaz | Interfaccia | الواجهة | インターフェース | 인터페이스
- `cat_ai` → Intelligence Artificielle | Artificial Intelligence | Inteligencia Artificial | Intelligenza Artificiale | الذكاء الاصطناعي | 人工知能 | 인공지능
- `cat_pro` → Options Professionnelles | Professional Options | Opciones profesionales | Opzioni professionali | خيارات احترافية | プロフェッショナルオプション | 전문가 옵션
- `gemini_placeholder` → Saisir votre clé API Gemini | Enter your Gemini API Key | Introducir clave API de Gemini | Inserisci la chiave API Gemini | أدخل مفتاح API الخاص بـ Gemini | Gemini APIキーを入力 | Gemini API 키 입력
- `test_key` → Tester la clé | Test Key | Probar clave | Testa chiave | اختبار المفتاح | キーをテスト | 키 테스트
- `key_ok` → ✅ Clé Gemini valide. | ✅ Gemini key is valid. | ✅ Clave de Gemini válida. | ✅ Chiave Gemini valida. | ✅ مفتاح Gemini صالح. | ✅ Geminiキーは有効です。 | ✅ Gemini 키가 유효합니다.
- `key_fail` → ❌ Clé Gemini invalide ou quota dépassé. | ❌ Invalid Gemini key or quota exceeded. | ❌ Clave de Gemini no válida o cuota superada. | ❌ Chiave Gemini non valida o quota superata. | ❌ مفتاح Gemini غير صالح أو تم تجاوز الحصة. | ❌ Geminiキーが無効か、クォータを超過しています。 | ❌ Gemini 키가 유효하지 않거나 할당량을 초과했습니다.
- `download_model` → Télécharger | Download | Descargar | Scarica | تنزيل | ダウンロード | 다운로드
- `delete_model` → Supprimer | Delete | Eliminar | Elimina | حذف | 削除 | 삭제
- `export_full` → Toujours exporter le dossier de projet complet | Always export complete project folder | Exportar siempre carpeta de proyecto completa | Esporta sempre cartella di progetto completa | تصدير مجلد المشروع الكامل دائماً | 常に完全なプロジェクトフォルダをエクスポートする | 항상 전체 프로젝트 폴더 내보내기
- `export_full_desc` → Génère un fichier ZIP contenant la vidéo, l'audio WAV et les sous-titres ASS/SRT. | Generates a ZIP file containing the video, WAV audio, and ASS/SRT subtitles. | Genera un archivo ZIP con el video, audio WAV y subtítulos ASS/SRT. | Genera un file ZIP contenente il video, l'audio WAV e i sottotitoli ASS/SRT. | ينتج ملف ZIP يحتوي على الفيديو، وصوت WAV، والترجمة بصيغتي ASS/SRT. | 動画、WAV音声、ASS/SRT字幕を含むZIPファイルを生成します。 | 비디오, WAV 오디오 및 ASS/SRT 자막이 포함된 ZIP 파일을 생성합니다.
- `share_app` → Découvre ShortifyLocal AI, le studio IA qui transforme tes vidéos YouTube en Shorts ! | Discover ShortifyLocal AI, the AI studio that turns your YouTube videos into Shorts! | ¡Descubre ShortifyLocal AI, el estudio de IA que convierte tus videos de YouTube en Shorts! | Scopri ShortifyLocal AI, lo studio IA che trasforma i tuoi video YouTube in Short! | اكتشف ShortifyLocal AI، الاستوديو الذكي الذي يحوّل فيديوهات يوتيوب إلى مقاطع قصيرة! | YouTube動画をショーツに変えるAIスタジオ、ShortifyLocal AIを発見しよう！ | YouTube 동영상을 쇼츠로 바꿔주는 AI 스튜디오, ShortifyLocal AI를 만나보세요!
- `share_reward` → +5 🪙 Merci pour le partage ! | +5 🪙 Thanks for sharing! | +5 🪙 ¡Gracias por compartir! | +5 🪙 Grazie per la condivisione! | +5 🪙 شكراً للمشاركة! | +5 🪙 共有ありがとうございます！ | +5 🪙 공유해 주셔서 감사합니다!
- `st_downloading` → Téléchargement de l'audio... | Downloading audio... | Descargando audio... | Download dell'audio... | جارٍ تنزيل الصوت... | 音声をダウンロード中... | 오디오 다운로드 중...
- `st_transcribing` → Transcription Whisper en cours... | Whisper transcription in progress... | Transcripción de Whisper en curso... | Trascrizione Whisper in corso... | جارٍ النسخ بواسطة Whisper... | Whisperで文字起こし中... | Whisper 변환 중...
- `st_segmenting` → Détection des moments viraux... | Detecting viral moments... | Detectando momentos virales... | Rilevamento dei momenti virali... | جارٍ رصد اللحظات الرائجة... | バイラル瞬間を検出中... | 바이럴 순간 감지 중...
- `st_reframing` → Suivi du visage & recadrage... | Face tracking & reframing... | Seguimiento facial y reencuadre... | Tracciamento del volto e reframing... | جارٍ تتبع الوجه وإعادة التأطير... | 顔追跡とリフレーム中... | 얼굴 추적 및 리프레이밍 중...
- `st_subtitling` → Génération des sous-titres karaoké... | Generating karaoke subtitles... | Generando subtítulos de karaoke... | Generazione dei sottotitoli karaoke... | جارٍ إنشاء الترجمة الكاريوكي... | カラオケ字幕を生成中... | 노래방 자막 생성 중...
- `st_rendering` → Rendu final du Short... | Rendering final Short... | Renderizando el Short final... | Rendering dello Short finale... | جارٍ العرض النهائي للمقطع... | ショートの最終レンダリング中... | 최종 쇼츠 렌더링 중...
- `st_done` → ✅ Vos Shorts sont prêts ! | ✅ Your Shorts are ready! | ✅ ¡Tus Shorts están listos! | ✅ I tuoi Short sono pronti! | ✅ مقاطعك جاهزة! | ✅ ショートの完成です！ | ✅ 쇼츠가 준비되었습니다!
- `st_error` → ❌ Une erreur est survenue. Veuillez réessayer. | ❌ An error occurred. Please try again. | ❌ Se produjo un error. Inténtalo de nuevo. | ❌ Si è verificato un errore. Riprova. | ❌ حدث خطأ. يرجى المحاولة مرة أخرى. | ❌ エラーが発生しました。もう一度お試しください。 | ❌ 오류가 발생했습니다. 다시 시도해 주세요.

*(Messages d'erreur base de données → Partie 4 ; message `SECURE_COMPROMISED` et snackbar AdMob `ad_unavailable` → Parties 5 et 3.)*

---

## 9. Checklist Partie 2 pour l'Agent

- [ ] Aucune ombre double / aucun élément de la même couleur que le fond (§1)
- [ ] Tous les composants §2 existent en fichiers propres avec `@Preview` clair/sombre/RTL
- [ ] Aucune chaîne en dur : 100% via §8
- [ ] Icônes directionnelles flippées en RTL ; icônes non directionnelles intactes (§1.6)
- [ ] Bottom nav = 3 onglets uniquement ; écran Détails hors onglets
- [ ] Polices bundlées `res/font` (Inter/Cairo/Noto JP/Noto KR), aucune police téléchargée au runtime
- [ ] Thème et langue persistés (DataStore / AppCompatDelegate)