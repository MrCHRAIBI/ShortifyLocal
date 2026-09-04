# CAHIER DES CHARGES — ShortifyLocal AI
## Partie 5 : Sécurité, Chiffrement & Intégrité des Données
### Version Kotlin Android Natif — Android Keystore + EncryptedSharedPreferences + HMAC-SHA256

---

## 0. Règles de lecture pour l'Agent de Codage

1. **Aucune donnée sensible** (solde, clé Gemini, sel HMAC, hash) ne doit jamais être stockée dans `SharedPreferences` en clair, dans Room, dans les logs, ni dans `WorkManager.Data`.
2. **Toute lecture/écriture sécurisée** passe par l'interface `SecureStorageRepository` (domain) implémentée dans `data/local/secure`. Aucune autre classe n'accède directement au stockage chiffré.
3. Renvois : règles métier des tokens → **Partie 3** ; clés UI (placeholders, masquage) → **Partie 2** ; schéma Room → **Partie 4**.
4. La sécurité vise la **protection contre la manipulation naïve** (édition de fichiers, backup modifié) ; voir §7 pour les limites assumées.

---

## 1. Stockage Chiffré — Android Keystore + EncryptedSharedPreferences

### 1.1 Mécanique normative
- **MasterKey** : `MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM)` → clé maîtresse générée dans **Android Keystore** (chiffrement matériel *StrongBox* quand disponible).
- **EncryptedSharedPreferences** (Jetpack Security `androidx.security:security-crypto`) construit avec cette MasterKey : préférences chiffrées **AES-256** (clés + valeurs).
- Fichier dédié : `shortify_secure_prefs` (séparé des préférences UI non sensibles, qui restent en DataStore).

### 1.2 Clés de stockage sécurisées
| Clé | Type | Rôle | Partie concernée |
|---|---|---|---|
| `secure_user_token_balance` | Int chiffré | Solde de jetons | 3 |
| `token_balance_hash` | String chiffrée | Signature HMAC-SHA256 du solde (§3) | 3, 5 |
| `hmac_salt` | Bytes chiffrés | Sel unique généré à l'installation (§3) | 5 |
| `secure_gemini_api_key` | String chiffrée | Clé API Gemini BYOK | 1, 2, 5 |
| `is_first_launch` | Boolean chiffré | Flag bienvenue/parrainage | 3 |
| `last_share_epoch` | Long chiffré | Anti-spam du partage (+5/24h) | 3 |
| `pass24h_until` | Long chiffré | Expiration du Pass 24h | 3 |
| `secure_app_passcode` | String chiffrée | *(Optionnel, réservé v2 — non implémenté en v1)* | — |

### 1.3 Interdictions strictes
- Le solde et ses compteurs **ne sont JAMAIS dans Room** (Partie 4) ni dans DataStore.
- Jamais de `Log.d/Log.i` contenant solde, clé, hash ou sel (logs strippés en release, §5).

---

## 2. Algorithme Anti-Triche — Signature HMAC-SHA256

### 2.1 Initialisation (premier lancement)
1. Générer `hmac_salt` = **32 octets via `java.security.SecureRandom`** ; stocker chiffré (§1).
2. Initialiser le solde selon Partie 3 (+50 / +70) puis signer immédiatement (§2.2).

### 2.2 Écriture du solde (`writeBalance`)
1. `signature = HMAC-SHA256(key = hmac_salt, message = solde.toString())` via `javax.crypto.Mac`.
2. Stocker **atomiquement** `secure_user_token_balance` **et** `token_balance_hash = hex(signature)`.
3. Émettre la nouvelle valeur dans `balanceFlow` (Partie 3).

### 2.3 Lecture / vérification (`verifyIntegrity`)
Déclencheurs : **au démarrage de l'application** et **avant chaque débit** (−5 / −3).
1. Lire `solde`, `token_balance_hash`, `hmac_salt`.
2. Recalculer `HMAC-SHA256(hmac_salt, solde.toString())`.
3. Comparer en **temps constant** : `java.security.MessageDigest.isEqual(a, b)` (jamais `==` sur chaînes).
4. **Si mismatch** (tentative de modification externe, appareil rooté, backup altéré) :
   - Réinitialiser le solde à **0** + re-signer ;
   - Afficher le message localisé `secure_compromised` (§2.4) via `UiEvent` (dialogue bloquant) ;
   - Journaliser silencieusement l'événement en mémoire uniquement (jamais en clair sur disque).

### 2.4 Chaîne localisée `secure_compromised`
À ajouter aux fichiers de ressources de la Partie 2.
Format : `clé` → FR | EN | ES | IT | AR | JA | KO

- `secure_compromised` → ⚠️ Une anomalie de sécurité a été détectée sur votre solde. Réinitialisation en cours. | ⚠️ A security anomaly was detected on your balance. Resetting now. | ⚠️ Se detectó una anomalía de seguridad en tu saldo. Restableciendo ahora. | ⚠️ Rilevata un'anomalia di sicurezza sul tuo saldo. Ripristino in corso. | ⚠️ تم اكتشاف خلل أمني في رصيدك. جاري إعادة الضبط الآن. | ⚠️ 残高にセキュリティの異常が検出されました。現在リセット中。 | ⚠️ 잔액에서 보안 이상이 감지되었습니다. 지금 초기화 중입니다.

---

## 3. Protection de la Clé API Gemini (BYOK)

1. Saisie masquée par défaut (champ password + icône œil, Partie 2) ; la clé n'est **jamais affichée en clair** hors choix explicite de l'utilisateur.
2. Stockage uniquement dans `secure_gemini_api_key` (§1) ; **jamais** dans Room, DataStore, logs, exports ZIP, ni WorkManager.
3. Usage réseau exclusif : envoyée **uniquement** à l'endpoint HTTPS officiel de l'API Gemini (Partie 1, Fonction C.2).
4. Suppression : l'utilisateur peut effacer la clé depuis les Paramètres → suppression physique immédiate de la préférence chiffrée.

---

## 4. Intégrité des Téléchargements & du Réseau

| Règle | Spécification |
|---|---|
| **HTTPS uniquement** | `android:usesCleartextTraffic="false"` dans le Manifest ; OkHttp configuré sans fallback HTTP. |
| **Modèles Whisper** | Téléchargés uniquement depuis la source officielle (Hugging Face, HTTPS) ; **vérification SHA-256** du fichier GGML après téléchargement (liste de checksums configurée en dur dans `util/`) ; échec de checksum → suppression du fichier + erreur localisée. |
| **Deep link parrainage** | Parsing strict du paramètre `ref` : liste blanche (`gift20`) ; toute autre valeur ignorée ; aucune exécution dynamique de données issues du deep link. |
| **Entrées utilisateur** | URL YouTube validée par Regex (Partie 1) ; libellés de tags échappés/sanitisés avant affichage (Compose est immunisé par défaut contre l'injection HTML, mais aucun `AnnotatedString` construit depuis données externes sans contrôle). |

---

## 5. Durcissement de l'Application (Manifeste & Build)

| Point | Spécification |
|---|---|
| **Sauvegardes** | `android:allowBackup="false"` (empêche l'extraction du solde via `adb backup` / restauration modifiée). |
| **Fichiers privés** | Tous les médias bruts et rendus dans `context.filesDir` (privé, non listable par d'autres apps) ; seule la copie galerie finale est publique (MediaStore, Partie 1). |
| **Obfuscation** | R8/ProGuard activé en release ; règles `keep` uniquement pour Room/Reflection/Hilt ; **stripping des logs** : `-assumenosideeffects class android.util.Log { *** d(...); *** i(...); *** v(...); }`. |
| **Permissions minimales** | Aucune permission stockage ; aucune permission localisation/contacts ; liste exhaustive en Partie 1 (§2.3). |
| **Export ZIP** | Contient uniquement MP4 + WAV + ASS/SRT (Partie 1, Fonction F) ; **jamais** de clé, solde, hash ou sel dans l'archive. |
| **WorkManager** | Les payloads `Data` de progression ne contiennent aucun identifiant sensible (états + pourcentages uniquement). |

---

## 6. Confidentialité & Vie Privée (Local-First)

1. **Transcriptions, visages, analyses heuristiques** : 100% on-device (Partie 1). Aucune donnée biométrique ou textuelle ne quitte l'appareil en mode sans clé.
2. **Mode Gemini (BYOK)** : la transcription est envoyée à Google **uniquement** si l'utilisateur a fourni sa clé et lancé l'analyse avancée ; mention discrète dans les Paramètres (catégorie IA) : le traitement sort alors de l'appareil.
3. **AdMob / RGPD** : en production (hors IDs de test), intégrer le **consentement UMP** (`com.google.android.ump:user-messaging-platform`) **avant** `MobileAds.initialize()` pour les utilisateurs EEA/UK ; respecter `AdMob` personalized-ads flag selon le choix de l'utilisateur.
4. **Aucune analytics tierce** en v1 (cohérent avec la philosophie local-first).

---

## 7. Limites Assumées (Transparence)

- Un appareil **rooté** + un attaquant déterminé (reverse-engineering binaire) peut contourner toute protection purement locale. Le HMAC-SHA256 protège contre la **manipulation naïve** (édition de préférences, backups, fichiers copiés), pas contre une attaque outillée du binaire.
- Détection de root **non bloquante et optionnelle** en v1 (éviter les faux positifs sur utilisateurs légitimes) ; la réinitialisation silencieuse du solde (§2.3) reste la contre-mesure principale.
- Ne jamais présenter ce mécanisme comme "inviolable" dans la communication produit.

---

## 8. Interface Normative (domain)

```kotlin
interface SecureStorageRepository {
    val balanceFlow: StateFlow<Int>
    suspend fun initOnFirstLaunch(referralCode: String?)
    suspend fun getBalance(): Int                      // vérifie HMAC avant de retourner
    suspend fun writeBalance(value: Int)               // signe HMAC après écriture
    suspend fun verifyIntegrity(): Boolean             // false => compromis + reset 0 + UiEvent
    suspend fun getGeminiKey(): String?
    suspend fun saveGeminiKey(key: String?)            // null = suppression physique
    fun isPass24hActive(): Boolean
    suspend fun consumeShareWindow(): Boolean          // true si +5 accordé (règle 24h)
}
```

---

## 9. Checklist Partie 5 pour l'Agent

- [ ] MasterKey AES256_GCM Keystore + EncryptedSharedPreferences `shortify_secure_prefs`
- [ ] Solde/hachage/sel/compteurs **uniquement** chiffrés ; jamais dans Room/DataStore/logs/WorkManager
- [ ] HMAC-SHA256 : sel SecureRandom 32 o ; signature à chaque écriture ; vérification au démarrage + avant chaque débit ; comparaison `MessageDigest.isEqual`
- [ ] Mismatch → reset 0 + re-signature + dialogue `secure_compromised`
- [ ] Clé Gemini : masquée, chiffrée, envoyée uniquement à l'API officielle, supprimable
- [ ] `usesCleartextTraffic="false"` ; `allowBackup="false"` ; checksums SHA-256 des modèles Whisper
- [ ] R8 + stripping des logs en release ; deep link `ref` en liste blanche
- [ ] UMP consent avant `MobileAds.initialize()` en production
- [ ] Aucune donnée sensible dans les exports ZIP ni les payloads WorkManager