# CAHIER DES CHARGES — ShortifyLocal AI
## Partie 4 : Base de Données Locale (Room) & Gestion du Cache Fichiers
### Version Kotlin Android Natif — Room (Jetpack) — 100% hors-ligne (local-first)

---

## 0. Règles de lecture pour l'Agent de Codage

1. **Room remplace Isar** (le cahier d'origine Flutter utilisait Isar, inexistant en Android natif). Mapping conceptuel : collections → `@Entity`, `IsarLinks` → `@Relation` / table de jonction, `@Index(unique)` → `indices = [Index(unique = true)]`.
2. **Aucun accès DAO hors `data/local/room`** ; toute exposition vers l'UI passe par les interfaces `domain/repository` (implémentées dans `data/repository`) en `suspend` ou `Flow`. **Jamais sur le main thread** (Room l'impose par défaut).
3. **Réactivité** : toute liste affichée (Accueil, Historique, Détails) est observée via `Flow` Room → `StateFlow` ViewModel → Compose.
4. Renvois : règles de tokens / `isUnlocked` → Partie 3 ; sécurité & `SECURE_COMPROMISED` → Partie 5 ; chemins de rendu → Partie 1.

---

## 1. Schéma des Entités Room

### 1.1 `VideoProjectEntity` (table `videoproject`)
| Colonne | Type | Contrainte |
|---|---|---|
| `id` | Long | `@PrimaryKey(autoGenerate = true)` |
| `youtubeUrl` | String | **Index UNIQUE** (jamais ré-analyser/écraser la même URL : `findByUrl` retourne l'existant) |
| `title` | String | — |
| `titleSearch` | String | Index ; titre **pré-minorisé en Kotlin** (`lowercase()`, Unicode-aware) pour la recherche multilingue (§4) |
| `author` | String | — |
| `thumbnailLocalPath` | String | — |
| `durationSeconds` | Int | — |
| `createdAt` | Long | epoch millis (Converter) |
| `lastAccessedAt` | Long | epoch millis ; mis à jour à chaque ouverture/téléchargement (base du Garbage Collector §6) |
| `rawAudioPath` | String? | nullable (WAV 16k) |
| `rawVideoPath` | String? | nullable (flux vidéo partiel) |
| `sourceStatus` | String | enum persisté : `DISPONIBLE` / `NON_DISPONIBLE_LOCALEMENT` |

### 1.2 `ProjectTagEntity` (table `projecttag`)
| Colonne | Type | Contrainte |
|---|---|---|
| `id` | Long | `@PrimaryKey(autoGenerate = true)` |
| `label` | String | **Index UNIQUE** (ex: `#Business`) ; insertion en `OnConflictStrategy.IGNORE` |
| `colorHex` | String | — |

### 1.3 `VideoTagCrossRef` (table de jonction many-to-many)
| Colonne | Type | Contrainte |
|---|---|---|
| `videoId` | Long | `@ForeignKey` → `videoproject.id`, `onDelete = CASCADE` |
| `tagId` | Long | `@ForeignKey` → `projecttag.id`, `onDelete = CASCADE` |
| — | — | `@PrimaryKey(videoId, tagId)` composite |

### 1.4 `GeneratedShortEntity` (table `generatedshort`)
| Colonne | Type | Contrainte |
|---|---|---|
| `id` | Long | `@PrimaryKey(autoGenerate = true)` |
| `videoId` | Long | `@ForeignKey` → `videoproject.id`, `onDelete = CASCADE`, index |
| `title` | String | — |
| `startMilliseconds` | Long | — |
| `endMilliseconds` | Long | — |
| `viralityScore` | Double | 0.0 – 100.0 |
| `explanationReason` | String | justification IA du score |
| `outputMp4Path` | String? | nullable tant que non rendu |
| `subtitlesSrtPath` | String? | — |
| `subtitlesAssPath` | String? | — |
| `isUnlocked` | Boolean | `default false` ; `true` après achat −3 tokens (Partie 3) |

---

## 2. Relations & POJOs `@Transaction`

- **`ProjectWithTags`** : `@Embedded VideoProjectEntity` + `@Relation(entity = ProjectTagEntity::class, parentColumn = "id", entityColumn = "id", associateBy = @Junction(VideoTagCrossRef::class))` → `tags: List<ProjectTagEntity>`.
- **`ProjectWithTagsAndShorts`** : idem + `@Relation(parentColumn = "id", entityColumn = "videoId")` → `shorts: List<GeneratedShortEntity>`.
- Utilisés par l'Écran 1 (cartes + badge 🎥), l'Écran 2 (liste triée) et l'Écran 3 (mini-badges de tags).

---

## 3. DAOs — Méthodes normatives

### `VideoProjectDao`
- `@Insert(OnConflictStrategy.IGNORE) suspend fun insert(p: VideoProjectEntity): Long`
- `@Query("SELECT * FROM videoproject WHERE youtubeUrl = :url LIMIT 1") suspend fun findByUrl(url: String): VideoProjectEntity?`
- `@Query("SELECT * FROM videoproject ORDER BY createdAt DESC LIMIT 2") fun recent(): Flow<List<VideoProjectEntity>>`
- `@Query("SELECT * FROM videoproject ORDER BY createdAt DESC") fun all(): Flow<List<VideoProjectEntity>>`
- `@Query("UPDATE videoproject SET sourceStatus = :status WHERE id = :id") suspend fun updateSourceStatus(id: Long, status: String)`
- `@Query("UPDATE videoproject SET lastAccessedAt = :now WHERE id = :id") suspend fun touch(id: Long, now: Long)`

### `ProjectTagDao`
- `@Insert(OnConflictStrategy.IGNORE) suspend fun insert(tag: ProjectTagEntity): Long`
- `@Query("SELECT * FROM projecttag") fun all(): Flow<List<ProjectTagEntity>>`
- `@Query("SELECT t.* FROM projecttag t INNER JOIN videotagcrossref c ON t.id = c.tagId WHERE c.videoId = :videoId") fun tagsOf(videoId: Long): Flow<List<ProjectTagEntity>>`

### `VideoTagCrossRefDao`
- `@Insert(OnConflictStrategy.IGNORE) suspend fun link(refs: List<VideoTagCrossRef>)`
- `@Query("DELETE FROM videotagcrossref WHERE videoId = :videoId") suspend fun unlinkAll(videoId: Long)`

### `GeneratedShortDao`
- `@Insert suspend fun insertAll(shorts: List<GeneratedShortEntity>)`
- `@Query("SELECT * FROM generatedshort WHERE videoId = :videoId ORDER BY viralityScore DESC") fun shortsOf(videoId: Long): Flow<List<GeneratedShortEntity>>`
- `@Query("SELECT COUNT(*) FROM generatedshort WHERE videoId = :videoId") fun countOf(videoId: Long): Flow<Int>`
- `@Query("UPDATE generatedshort SET isUnlocked = 1, outputMp4Path = :path WHERE id = :id") suspend fun unlock(id: Long, path: String)`

---

## 4. Stratégie de recherche multilingue (remplace `caseSensitive: false` d'Isar)

⚠️ **Point d'expertise** : `COLLATE NOCASE` et `LOWER()` de SQLite **ne gèrent que l'ASCII** (échec sur `É/é`, `Ü/ü`…). Solution normative :

1. À l'insertion/mise à jour : calculer en Kotlin `titleSearch = title.lowercase()` (minuscule **Unicode-aware**, correcte pour FR/ES/IT accentués, AR, JA, KO).
2. À la requête : minoriser la saisie utilisateur en Kotlin (`q.lowercase()`), puis :
   `WHERE titleSearch LIKE '%' || :q || '%'`
3. Debounce 300 ms côté ViewModel (Partie 2, Écran 3).

---

## 5. Configuration `AppDatabase`

- `@Database(entities = [VideoProjectEntity, ProjectTagEntity, VideoTagCrossRef, GeneratedShortEntity], version = 1, exportSchema = true)`
- Compilation via **KSP** ; singleton injecté par **Hilt**.
- Nom du fichier : `shortify_local.db`.
- **Migrations** : `exportSchema = true` obligatoire ; toute version future ajoute des `Migration(n, n+1)` **additives** ; `fallbackToDestructiveMigration` **interdit** (perte de données utilisateur inacceptable).
- `TypeConverters` : epoch millis ↔ `Instant`/`LocalDateTime` si utilisé ; enums ↔ String.

---

## 6. Stockage fichiers & Garbage Collector (rotation du cache)

### 6.1 Arborescence normative (`context.filesDir`, privé)
```
files/
├── thumbnails/       # miniatures YouTube locales
├── audio_raw/        # WAV 16 kHz mono (Fonction A)
├── video_raw/        # flux vidéo partiels (Fonction F)
├── shorts/           # MP4 finaux avant export galerie
├── subtitles/        # .ass / .srt
├── whisper_models/   # GGML tiny/base/small (suppression manuelle uniquement)
└── frames_tmp/       # frames 250 ms (Fonction D) — purgées après CHAQUE rendu
```

### 6.2 Règles d'élimination automatique
Au **lancement** de l'application et à la **fermeture d'un projet** :
1. **Fichiers sources** (`audio_raw/`, `video_raw/`) d'un projet dont `now − lastAccessedAt > 7 jours` sans interaction → **suppression physique** des fichiers.
2. La fiche Room est **conservée intacte** mais `sourceStatus = NON_DISPONIBLE_LOCALEMENT`.
3. Si l'utilisateur ré-analyse ce projet plus tard → l'UI affiche un **bouton de re-téléchargement** (relance la Fonction A).
4. **Préservation absolue** : les fichiers MP4 de Shorts avec `isUnlocked == true` ne sont **JAMAIS** supprimés automatiquement.
5. `whisper_models/` : jamais supprimés automatiquement (suppression manuelle via Whisper Manager, Partie 2).

---

## 7. Traduction des erreurs Base de Données

Toute exception système doit être **interceptée dans `data/repository`**, mappée vers un code normatif, puis affichée via `UiEvent` → SnackBar/AlertDialog localisé :

| Exception Android | Code normatif |
|---|---|
| `android.database.sqlite.SQLiteFullException` / `IOException` disque | `DB_DISK_FULL` |
| `SQLiteException` / `SQLiteConstraintException` en écriture | `DB_WRITE_FAIL` |

Chaînes localisées (à ajouter aux fichiers de ressources de la Partie 2) :
Format : `clé` → FR | EN | ES | IT | AR | JA | KO

- `db_disk_full` → 💾 Espace de stockage insuffisant sur votre appareil pour générer la vidéo. | 💾 Insufficient storage space on your device to generate the video. | 💾 Espacio de almacenamiento insuficiente en tu dispositivo para generar el video. | 💾 Spazio di archiviazione insufficiente sul dispositivo per generare il video. | 💾 مساحة التخزين غير كافية على جهازك لإنشاء الفيديو. | 💾 動画を生成するための空き容量がデバイスに不足しています。 | 💾 동영상을 생성하기 위한 디바이스 저장 공간이 부족합니다.
- `db_write_fail` → ❌ Impossible d'enregistrer le projet. Veuillez réessayer. | ❌ Failed to save the project. Please try again. | ❌ No se pudo guardar el proyecto. Por favor, inténtalo de nuevo. | ❌ Impossibile salvare il progetto. Riprova. | ❌ فشل حفظ المشروع. يرجى المحاولة مرة أخرى. | ❌ プロジェクトの保存に失敗しました。もう一度お試しください。 | ❌ 프로젝트 저장에 실패했습니다. 다시 시도해 주세요.

*(Le code `SECURE_COMPROMISED` et la mécanique HMAC sont définis en **Partie 5**.)*

---

## 8. Checklist Partie 4 pour l'Agent

- [ ] 4 entités + jonction ; FK `CASCADE` ; index uniques `youtubeUrl` et `label`
- [ ] `titleSearch` pré-minorisé en Kotlin (Unicode) ; requête `LIKE` sans `COLLATE NOCASE`
- [ ] Listes UI observées en `Flow` ; tri shorts `viralityScore DESC` ; récents `LIMIT 2`
- [ ] `findByUrl` : jamais écraser un projet existant (retourner l'existant)
- [ ] `exportSchema = true` ; migrations additives ; `fallbackToDestructiveMigration` interdit
- [ ] GC 7 jours via `lastAccessedAt` ; `sourceStatus` basculé ; bouton re-téléchargement UI
- [ ] Shorts `isUnlocked == true` jamais supprimés ; `frames_tmp/` purgé après chaque rendu
- [ ] Exceptions mappées `DB_DISK_FULL` / `DB_WRITE_FAIL` → SnackBar localisé (§7)