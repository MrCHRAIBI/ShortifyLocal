#!/usr/bin/env bash
# verify_p1.sh — Batterie mécanique de vérification Phase 1 (plan 01-04, Task 1).
#
# Rejouable en une commande : tous les interdits de la SPEC (01-SPEC.md ## Prohibitions),
# les checks mécaniques R1/R2/R4/R5/R6, le catalogue errata E2, l'absence de tranche Room
# en P1 (décision propriétaire B du 2026-09-05) et le checksum SHA-256 de l'AAR du fork
# ffmpeg-kit 8.1.7.
#
# Usage : bash scripts/verify_p1.sh   (depuis n'importe où — racine déduite du script)
# Exit 0 = « P1 OK » ; premier échec = « ÉCHEC: <check> » + exit 1.
# Aucun repli silencieux : un fichier absent est un échec, pas un 0.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

CHECKSUM_ATTENDU="374d3734755fd4a4f2241da23b93ab0b3bcae9bd39f01cf3bf29a5731914658b"
TOML="gradle/libs.versions.toml"
FICHIERS_BUILD=(settings.gradle.kts build.gradle.kts app/build.gradle.kts gradle)

fail() {
  echo "ÉCHEC: $1" >&2
  exit 1
}

# check_absent <description> <flags grep...> -- <motif> <cibles...>
# zéro occurrence attendu ; exit grep 1 = OK (aucun match), 0 = ÉCHEC, 2 = ÉCHEC (erreur).
check_absent() {
  local desc="$1"; shift
  local flags=()
  while [ "$1" != "--" ]; do flags+=("$1"); shift; done
  shift # --
  local motif="$1"; shift
  local out rc=0
  out=$(grep "${flags[@]}" -n "$motif" "$@" 2>&1) || rc=$?
  if [ "$rc" -ge 2 ]; then
    echo "$out" >&2
    fail "$desc — erreur grep (cible manquante ?)"
  fi
  if [ "$rc" -eq 0 ]; then
    echo "$out" >&2
    fail "$desc — occurrence(s) trouvée(s)"
  fi
  echo "  [OK] 0 occurrence — $desc"
}

# check_present <description> <motif fixe> <fichier>
check_present() {
  local desc="$1" motif="$2" fichier="$3"
  if ! grep -qF "$motif" "$fichier"; then
    fail "$desc — « $motif » introuvable dans $fichier"
  fi
  echo "  [OK] présent — $desc"
}

exiger_fichier() {
  [ -f "$1" ] || fail "prérequis — fichier manquant : $1"
}
exiger_dossier() {
  [ -d "$1" ] || fail "prérequis — dossier manquant : $1"
}

echo "== Batterie mécanique P1 — racine: $ROOT =="

# Prérequis d'existence (un fichier absent doit ÉCHOUER, jamais passer sous silence)
for f in settings.gradle.kts build.gradle.kts app/build.gradle.kts "$TOML" app/src/main/AndroidManifest.xml; do
  exiger_fichier "$f"
done
exiger_dossier gradle
exiger_dossier app/src/main/java/com/shortifylocal/ai/domain
exiger_dossier app/src/main/java/com/shortifylocal/ai/presentation

echo "-- (1) Interdits fichiers de build (arthenica, kapt/KSP1, migration destructive) --"
check_absent "ancienne coordonnée ffmpeg vendeur retiré (com.arthenica)" -r -- 'com\.arthenica' "${FICHIERS_BUILD[@]}"
check_absent "processeur d'annotations legacy interdit (kapt/KSP1)" -r -i -- 'kapt' "${FICHIERS_BUILD[@]}"
check_absent "migration destructive interdite (fallbackToDestructiveMigration)" -r -- 'fallbackToDestructiveMigration' "${FICHIERS_BUILD[@]}"

echo "-- (2) Interdits code UI (Left/Right, littéraux directs) --"
check_absent "alignements Left/Right (Start/End uniquement)" -r -E -- 'Alignment\.(Left|Right)|TextAlign\.(Left|Right)' app/src/main/java/
check_absent "littéraux UI directs (text/label/contentDescription = \"…\")" -r -E -- '(text|label|contentDescription)[[:space:]]*=[[:space:]]*"' app/src/main/java/com/shortifylocal/ai/presentation/

echo "-- (3) Pureté domain (aucun import android./androidx.) --"
check_absent "imports android.*/androidx.* dans domain/" -r -E -- '^import[[:space:]]+(android|androidx)\.' app/src/main/java/com/shortifylocal/ai/domain/

echo "-- (4) Manifest (permissions interdites, cleartext, RTL, backup) --"
check_absent "permission READ_MEDIA*" -E -- 'READ_MEDIA' app/src/main/AndroidManifest.xml
check_absent "permission READ_EXTERNAL_STORAGE" -E -- 'READ_EXTERNAL_STORAGE' app/src/main/AndroidManifest.xml
check_absent "permission ACCESS_*_LOCATION" -E -- 'ACCESS_[A-Z_]*_LOCATION' app/src/main/AndroidManifest.xml
check_absent "permission READ_CONTACTS" -E -- 'READ_CONTACTS' app/src/main/AndroidManifest.xml
check_absent "attribut usesCleartextTraffic (HTTPS-only)" -E -- 'usesCleartextTraffic' app/src/main/AndroidManifest.xml
check_present "manifest supportsRtl=true" 'android:supportsRtl="true"' app/src/main/AndroidManifest.xml
check_present "manifest allowBackup=false" 'android:allowBackup="false"' app/src/main/AndroidManifest.xml

echo "-- (5) Catalogue errata E2 — 21 versions + checksum fork dans $TOML --"
VERSIONS_E2=(
  "2.4.10" "9.4.0" "9.7.1" "2026.08.00" "2.60.1" "1.4.0" "2.8.4" "2.3.11"
  "2.10.0" "2.11.2" "1.1.0" "25.4.0" "4.0.0" "16.1.7" "1.11.0" "5.5.0"
  "3.6.2" "1.2.1" "v0.26.5" "8.1.7" "b4938"
)
for v in "${VERSIONS_E2[@]}"; do
  check_present "version E2 $v" "$v" "$TOML"
done
check_present "SHA-256 consigné du fork 8.1.7" "$CHECKSUM_ATTENDU" "$TOML"

echo "-- (6) Absence tranche Room en P1 (décision propriétaire B 2026-09-05 — report Phase 3 DATA-01) --"
if [ -e app/schemas ]; then
  fail "tranche Room P1 — dossier app/schemas/ existe (doit être absent : schéma livré en Phase 3)"
fi
echo "  [OK] app/schemas/ absent"
check_absent "code RoomDatabase dans app/src/" -r -- 'RoomDatabase' app/src/

echo "-- (7) Checksum SHA-256 de l'AAR fork 8.1.7 (cache Gradle, Maven Central) --"
AAR="$(find "$HOME/.gradle/caches/modules-2/files-2.1/dev.ffmpegkit-maintained/" -name 'ffmpeg-kit-full-gpl-8.1.7.aar' 2>/dev/null | head -1)"
[ -n "$AAR" ] || fail "checksum — AAR ffmpeg-kit-full-gpl-8.1.7.aar introuvable dans ~/.gradle/caches/modules-2/files-2.1/dev.ffmpegkit-maintained/"
SHA_ACTUEL="$(sha256sum "$AAR" | awk '{print $1}')"
if [ "$SHA_ACTUEL" != "$CHECKSUM_ATTENDU" ]; then
  fail "checksum — SHA-256 inattendu : $SHA_ACTUEL (attendu $CHECKSUM_ATTENDU) sur $AAR"
fi
echo "  [OK] checksum $SHA_ACTUEL — $AAR"

echo ""
echo "P1 OK — 7/7 sections vertes : interdits build, interdits UI, pureté domain, manifest, catalogue E2, absence tranche Room, checksum fork."
