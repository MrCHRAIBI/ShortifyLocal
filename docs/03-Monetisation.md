# CAHIER DES CHARGES — ShortifyLocal AI
## Partie 3 : Monétisation & Économie des Jetons (Token Economy)
### Version Kotlin Android Natif — Android uniquement — 100% locale (aucun serveur central)

---

## 0. Règles de lecture pour l'Agent de Codage

1. **Économie 100% locale** : aucun serveur, aucune base centrale, aucun achat intégré (IAP). Le solde vit uniquement sur l'appareil.
2. **Toute mutation du solde** passe exclusivement par la fonction centrale `updateTokens()` (§2). Aucune écriture directe du solde ailleurs.
3. **Stockage & intégrité** : le solde et les compteurs sont stockés chiffrés et signés HMAC — mécanique détaillée en **Partie 5**. La présente partie définit les **règles métier** ; la Partie 5 définit la **mécanique de sécurité**.
4. **UI** : tous les points de contact UI (TokenPill, dialogues, bottom sheet) sont spécifiés en **Partie 2** ; les chaînes localisées propres à la monétisation sont en **§6** de la présente partie.
5. **MVVM** : `TokenRepository` (interface dans `domain`, implémentation dans `data/local/secure`) ; le solde est exposé aux ViewModels via `StateFlow<Int>` ; tout rafraîchissement d'UI passe par ce flux.

---

## 1. Modèle de Données & Clés de Stockage

Clés stockées dans **EncryptedSharedPreferences** (Jetpack Security, AES-256 via Android Keystore — voir Partie 5) :

| Clé | Type | Rôle |
|---|---|---|
| `secure_user_token_balance` | Int chiffré | Solde actuel de jetons |
| `token_balance_hash` | String | Signature HMAC-SHA256 du solde (anti-triche, Partie 5) |
| `is_first_launch` | Boolean | Flag premier démarrage (bienvenue + parrainage) |
| `last_share_epoch` | Long | Timestamp du dernier partage récompensé |
| `pass24h_until` | Long | Timestamp d'expiration du Pass 24h |
| `secure_gemini_api_key` | String chiffré | Clé Gemini BYOK (utilisée Partie 1, stockée Partie 5) |

**Valeur par défaut** : si `secure_user_token_balance` n'existe pas → premier démarrage → initialisation selon §3.

---

## 2. Fonction Centrale de Transaction

Signature normative (interface `domain/repository/TokenRepository`) :

```kotlin
interface TokenRepository {
    val balanceFlow: StateFlow<Int>
    suspend fun getBalance(): Int
    suspend fun updateTokens(amount: Int): Boolean   // true = succès, false = solde insuffisant
    fun isPass24hActive(): Boolean
}
```

- `updateTokens(amount)` : ajoute (positif) ou soustrait (négatif) ; **retourne `false` sans débiter** si le solde résultant serait négatif ; à chaque écriture, déclenche la re-signature HMAC (Partie 5) et l'émission dans `balanceFlow`.
- À chaque lecture (démarrage, avant débit) : vérification HMAC (Partie 5) ; si anomalie → reset 0 + message `SECURE_COMPROMISED` (Partie 5).

---

## 3. Table de Transactions (Logique métier stricte)

| Action | Trigger | Valeur | Règle de validation à coder (Kotlin) |
|---|---|---|---|
| **Premier démarrage** | Initialisation de l'application | **+50** | Si `is_first_launch` absent : init solde = 50 (ou 70 si parrainage, §5), puis flag = `true`. |
| **Publicité récompensée** | Callback `onUserEarnedReward` d'AdMob | **+10** | Créditer immédiatement après confirmation ; **ET activer le Pass 24h** (`pass24h_until = now + 24h`) ; rafraîchir `balanceFlow`. |
| **Partage de l'application** | Clic bouton partage (Écran 2) | **+5** | Si `now − last_share_epoch ≥ 24h` → créditer + mettre à jour `last_share_epoch` + snackbar `share_reward`. Sinon → **blocage silencieux** (aucun crédit, aucun message). |
| **Parrainage (filleul)** | Deep link `shortify://invite?ref=gift20` intercepté **AU premier démarrage** | **+20** | Si `ref == "gift20"` ET `is_first_launch` vrai → 50 + 20 = **total initial 70**. Ignoré si app déjà lancée auparavant. |
| **Lancement d'une analyse** | Validation de l'URL YouTube (Écran 1) | **−5** | Si `isPass24hActive()` → coût 0. Sinon si solde ≥ 5 → débiter 5. Sinon → **bloquer l'analyse** + ouvrir le bottom sheet AdMob (dialogue `insufficient_tokens`). |
| **Téléchargement d'un Short** | Clic "Télécharger" (Écran 2) | **−3** | Si `isPass24hActive()` OU `isUnlocked == true` (Room, Partie 4) → coût 0. Sinon si solde ≥ 3 → débiter 3 + `isUnlocked = true` + retrait filigrane + rendu haute qualité + export galerie MediaStore. Sinon → bloquer + proposer la pub. |

---

## 4. Pass 24h — Définition normative

> Le **visionnage complet** d'une publicité récompensée crédite **+10 tokens** ET active un **Pass 24h** (`pass24h_until = now + 24h`) pendant lequel **les analyses (−5) et les téléchargements (−3) sont gratuits** (coût = 0).
> - Indicateur visuel : tant que le Pass est actif, afficher le libellé `pass_active` (§6) dans le `TokenPill` (Écrans 1 et 2).
> - Expiration vérifiée à chaque lecture (`now > pass24h_until` → inactif).

*(Cette règle lève l'ambiguïté du document d'origine qui référençait un "Pass 24h" sans définir son acquisition.)*

---

## 5. Parrainage — Deep Link

- **Intent-filter** dans `AndroidManifest.xml` :
  - Actions : `android.intent.action.VIEW` ; catégories `DEFAULT` + `BROWSABLE`
  - Data : `android:scheme="shortify"`, `android:host="invite"`
- **Parsing** au démarrage (MainActivity → ViewModel racine) : query parameter `ref` ; si `ref == "gift20"` ET premier lancement → appliquer +20 (§3).
- Le lien de partage généré par l'utilisateur (bouton partage) doit contenir cette URL de parrainage + le texte `share_app` (Partie 2).

---

## 6. Intégration Google AdMob (Rewarded Ads) — Android uniquement

### 6.1 Configuration
- SDK : **`com.google.android.gms:play-services-ads`** (24.x).
- **Identifiants de TEST obligatoires en développement** (l'ID iOS de l'ancien cahier des charges est **supprimé**, projet Android-only) :
  - Application ID (manifest, meta-data `com.google.android.gms.ads.APPLICATION_ID`) : `ca-app-pub-3940256099942544~3347511713`
  - Rewarded Ad Unit ID : `ca-app-pub-3940256099942544/5224354917`
- Initialisation `MobileAds.initialize()` au démarrage de l'application.

### 6.2 Singleton `AdManager` (injecté via Hilt, `@Singleton`)
Cycle de vie normatif :

1. **Préchargement** (`loadRewardedAd()`) : chargé en arrière-plan dès l'ouverture de l'application ; rechargé automatiquement après chaque affichage.
2. **Affichage** (`showRewardedAd()`), appelé par le bottom sheet AdMob (Partie 2) ou les blocages de solde :
   - Si la pub n'est pas encore chargée → exposer `isAdLoading = true` (indicateur de chargement UI).
   - **Timeout : 10 secondes** (et non 5) ; si échec de chargement après timeout → fermer l'indicateur + `SnackBar` clé `ad_unavailable` (§7).
3. **Récompense** (`onUserEarnedReward`, posté sur le main thread) :
   - Appeler **obligatoirement** `updateTokens(+10)` ;
   - Activer le **Pass 24h** (§4) ;
   - Le rafraîchissement UI (TokenPill, écrans) découle automatiquement de `balanceFlow`.

### 6.3 Règles UX
- Jamais bloquer le thread UI pendant le chargement d'une pub.
- Le bottom sheet AdMob reste fermable à tout moment avant `onUserEarnedReward` (pas de récompense si fermeture anticipée).

---

## 7. Chaînes localisées propres à la Monétisation

À ajouter aux fichiers de ressources de la Partie 2 (`values/`, `values-en`, ...).
Format : `clé` → FR | EN | ES | IT | AR | JA | KO

- `ad_unavailable` → Publicité indisponible pour le moment, veuillez réessayer. | Ad unavailable at the moment, please try again. | Anuncio no disponible por el momento, inténtalo de nuevo. | Pubblicità non disponibile al momento, riprova. | الإعلان غير متوفر حالياً، يرجى المحاولة مرة أخرى. | 現在広告を利用できません。もう一度お試しください。 | 현재 광고를 사용할 수 없습니다. 다시 시도해 주세요.
- `ad_offer_title` → Gagner des Tokens | Earn Tokens | Ganar tokens | Guadagna Token | اربح رموزاً | トークンを獲得 | 토큰 획득
- `ad_watch_btn` → Regarder une publicité (+10 🪙) | Watch an ad (+10 🪙) | Ver un anuncio (+10 🪙) | Guarda una pubblicità (+10 🪙) | شاهد إعلاناً (+10 🪙) | 広告を視聴 (+10 🪙) | 광고 시청 (+10 🪙)
- `pass_active` → 🎟️ Pass 24h actif | 🎟️ 24h Pass active | 🎟️ Pase de 24 h activo | 🎟️ Pass 24h attivo | 🎟️ تصريح 24 ساعة نشط | 🎟️ 24時間パス有効 | 🎟️ 24시간 패스 활성
- `share_reward` → +5 🪙 Merci pour le partage ! | +5 🪙 Thanks for sharing! | +5 🪙 ¡Gracias por compartir! | +5 🪙 Grazie per la condivisione! | +5 🪙 شكراً للمشاركة! | +5 🪙 共有ありがとうございます！ | +5 🪙 공유해 주셔서 감사합니다!

*(Rappel : `tokens_balance`, `insufficient_tokens`, `download_short`, `share_app` sont définis en Partie 2, §8.)*

---

## 8. Checklist Partie 3 pour l'Agent

- [ ] Aucune écriture directe du solde hors `updateTokens()` ; UI synchronisée via `balanceFlow`
- [ ] `is_first_launch` : parrainage +20 appliqué **uniquement** au tout premier lancement (total 70)
- [ ] Partage : +5 uniquement si ≥ 24h depuis le dernier partage ; sinon silencieux
- [ ] Pass 24h : activé par `onUserEarnedReward` ; rend analyses et téléchargements gratuits ; indicateur `pass_active` visible
- [ ] AdMob : IDs de test Android uniquement ; préchargement au lancement ; timeout **10 s** → `ad_unavailable`
- [ ] `onUserEarnedReward` → `updateTokens(+10)` + Pass 24h + refresh UI (main thread)
- [ ] Débits −5 / −3 : vérification Pass 24h puis `isUnlocked` puis solde ; blocage → bottom sheet / dialogue pub
- [ ] Aucune référence iOS résiduelle (ID AdMob iOS supprimé)