#!/usr/bin/env bash
# verify_emulator_p1.sh — Cible émulateur P1 : install, lancement, navigation 3 onglets,
# bascule de thème + persistance après force-stop/relance, captures d'écran (plan 01-04, Task 2).
#
# Réutilisable TEL QUEL sur le téléphone physique (D-03) via :
#   ANDROID_SERIAL=<serial> bash scripts/verify_emulator_p1.sh
#
# RÈGLE D'ESCALADE ABI : si `adb install` échoue avec INSTALL_FAILED_NO_MATCHING_ABIS ou
# INSTALL_FAILED_CPU_ABI_INCOMPATIBLE — STOP immédiat, ne changer NI la cible NI les
# dépendances, escalader au propriétaire de spec (blocage ABI hérité de STATE.md).
#
# Exit 0 = « ÉMU OK » ; premier échec = « ÉCHEC: <check> » + exit non-zéro.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SCREENS=".planning/phases/01-chafaudage-d-cisions-bloquantes/screens"
APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="com.shortifylocal.ai"
ACTIVITE="${PKG}/.MainActivity"
DS_FILE="files/datastore/settings.preferences_pb"
DUMP_SRC="/sdcard/window_dump_p1.xml"
DUMP_LOCALE="build/uidump_p1.xml"

fail() { echo "ÉCHEC: $1" >&2; exit 1; }

# Git Bash (MSYS) réécrit les arguments commençant par « / » en chemins Windows
# (ex. /sdcard/x → C:/Program Files/Git/sdcard/x) — ce qui casse les chemins distants
# adb (pull, uiautomator dump). Inerte hors MSYS. (Fix Rule 3, plan 01-04 Task 2.)
export MSYS_NO_PATHCONV=1

# Toutes les commandes adb passent par ce wrapper (paramétrable par ANDROID_SERIAL)
adb_() { adb ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} "$@"; }

# — Résolution du binaire emulator (seulement utile au démarrage automatique d'un AVD) —
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/AppData/Local/Android/Sdk}}"
EMU_BIN="$(command -v emulator || true)"
if [ -z "$EMU_BIN" ] && [ -x "$SDK/emulator/emulator.exe" ]; then EMU_BIN="$SDK/emulator/emulator.exe"; fi
if [ -z "$EMU_BIN" ] && [ -x "$SDK/emulator/emulator" ]; then EMU_BIN="$SDK/emulator/emulator"; fi

[ -f "$APK" ] || fail "APK absent : $APK (lancer ./gradlew :app:assembleDebug)"
mkdir -p "$SCREENS" build

echo "== Vérification cible P1 — appareil: ${ANDROID_SERIAL:-auto} =="

# — (1) Appareil en ligne ? Sinon démarrer le premier AVD (uniquement si ANDROID_SERIAL vide) —
if [ -n "${ANDROID_SERIAL:-}" ]; then
  adb devices | grep -q "^${ANDROID_SERIAL}[[:space:]]" || fail "ANDROID_SERIAL=$ANDROID_SERIAL absent de « adb devices »"
  echo "-- Cible imposée : $ANDROID_SERIAL"
elif ! adb devices | awk 'NR>1 && $2=="device"{f=1} END{exit !f}'; then
  [ -n "$EMU_BIN" ] || fail "aucun appareil en ligne et binaire emulator introuvable (ANDROID_HOME=$SDK)"
  AVD="$( "$EMU_BIN" -list-avds 2>/dev/null | head -1 )"
  [ -n "$AVD" ] || fail "aucun appareil en ligne et aucun AVD listable via « emulator -list-avds »"
  echo "-- Aucun appareil en ligne : démarrage de l'AVD « $AVD » (fond, log build/emu.log)"
  "$EMU_BIN" -avd "$AVD" > build/emu.log 2>&1 &
  adb wait-for-device
  BOOT=""
  for _ in $(seq 1 36); do # ~180 s max
    BOOT="$(adb_ shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    [ "$BOOT" = "1" ] && break
    sleep 5
  done
  [ "$BOOT" = "1" ] || fail "boot de l'émulateur non terminé en ~180 s (voir build/emu.log)"
  echo "-- Boot terminé (sys.boot_completed=1)"
fi
SERIAL_EFFECTIF="$(adb_ get-serialno | tr -d '\r')"
echo "-- Appareil en ligne : $SERIAL_EFFECTIF"

# — (2) Install — sonde l'ABI du fork (les .so embarqués ne sont jamais chargés en P1) —
INSTALL_OUT="$(adb_ install -r "$APK" 2>&1)" || {
  echo "$INSTALL_OUT" >&2
  case "$INSTALL_OUT" in
    *INSTALL_FAILED_NO_MATCHING_ABIS*|*INSTALL_FAILED_CPU_ABI_INCOMPATIBLE*)
      echo "BLOCAGE ABI : escalade au propriétaire de spec — ne changer NI la cible NI les dépendances." >&2
      fail "install — ABI incompatible (INSTALL_FAILED_*ABI* — blocage hérité de STATE.md)"
      ;;
  esac
  fail "install — adb install -r a échoué"
}
echo "-- [OK] install : $(printf '%s' "$INSTALL_OUT" | tail -1)"

# — (3) Lancement de MainActivity —
adb_ shell am start -n "$ACTIVITE" > /dev/null || fail "lancement — am start a échoué"
# Attendre que la coquille soit RÉELLEMENT affichée avant capture (un somme fixe attrape
# le splash system au cold start de l'émulateur) — boucle bornée ~30 s sur le dump.
UI_PRET=""
for _ in $(seq 1 10); do
  sleep 3
  if adb_ shell uiautomator dump "$DUMP_SRC" > /dev/null 2>&1 \
     && adb_ pull "$DUMP_SRC" "$DUMP_LOCALE" > /dev/null 2>&1 \
     && grep -q "Accueil" "$DUMP_LOCALE" 2>/dev/null; then
    UI_PRET=1
    break
  fi
done
[ -n "$UI_PRET" ] || fail "lancement — coquille non affichée après ~30 s (splash bloqué ou crash ?)"
PID="$(adb_ shell pidof "$PKG" | tr -d '\r')"
[ -n "$PID" ] || fail "lancement — process $PKG non détecté après am start (crash ?)"
echo "-- [OK] lancement — pid $PID (coquille affichée)"

# — (4) Capture de la coquille —
adb_ exec-out screencap -p > "$SCREENS/emulator-shell.png" || fail "capture — screencap shell a échoué"
[ -s "$SCREENS/emulator-shell.png" ] || fail "capture — $SCREENS/emulator-shell.png vide"
echo "-- [OK] capture : $SCREENS/emulator-shell.png"

# bounds_de <attribut-cible> <fichier-dump> — extrait « x1 y1 x2 y2 » du premier nœud
# portant l'attribut ; chaîne vide si introuvable (l'appelant décide de l'échec).
bounds_de() {
  local noeud="" bounds=""
  noeud="$(grep -o "<node[^>]*$1[^>]*>" "$2" | head -1)" || noeud=""
  [ -n "$noeud" ] && bounds="$(printf '%s' "$noeud" | sed -n 's/.*bounds="\[\([0-9]\{1,\}\),\([0-9]\{1,\}\)\]\[\([0-9]\{1,\}\),\([0-9]\{1,\}\)\]".*/\1 \2 \3 \4/p')"
  printf '%s' "$bounds"
}

redumper() {
  adb_ shell uiautomator dump "$DUMP_SRC" > /dev/null || fail "dump — uiautomator dump a échoué"
  rm -f "$DUMP_LOCALE"
  adb_ pull "$DUMP_SRC" "$DUMP_LOCALE" > /dev/null || fail "dump — pull de $DUMP_SRC a échoué"
  grep -q "<hierarchy" "$DUMP_LOCALE" || { cat "$DUMP_LOCALE" 2>/dev/null >&2; fail "dump — $DUMP_LOCALE ne contient pas de hiérarchie UI valide"; }
}

# — (5) Preuve mécanique des 3 onglets via uiautomator dump —
redumper
for LIBELLE in "Accueil" "Historique" "Paramètres"; do
  if ! grep -q "$LIBELLE" "$DUMP_LOCALE"; then
    cat "$DUMP_LOCALE" >&2
    fail "onglets — libellé « $LIBELLE » absent du dump uiautomator"
  fi
done
echo "-- [OK] 3 onglets affichés : Accueil / Historique / Paramètres"

# — (6) Bascule du thème : naviguer vers l'onglet Paramètres (la bascule y vit),
#      puis bounds du nœud content-desc, centre, tap —
BOUNDS_TAB="$(bounds_de 'text="Paramètres"' "$DUMP_LOCALE")"
[ -n "$BOUNDS_TAB" ] || { cat "$DUMP_LOCALE" >&2; fail "navigation — bounds de l'onglet Paramètres non extraits du dump"; }
read -r X1 Y1 X2 Y2 <<< "$BOUNDS_TAB"
adb_ shell input tap $(( (X1 + X2) / 2 )) $(( (Y1 + Y2) / 2 )) || fail "navigation — tap sur l'onglet Paramètres a échoué"
sleep 1
redumper
BOUNDS="$(bounds_de 'content-desc="Basculer le thème clair/sombre"' "$DUMP_LOCALE")"
if [ -z "$BOUNDS" ]; then
  cat "$DUMP_LOCALE" >&2
  fail "bascule introuvable — bounds non extraits du dump (retenter à la main : input tap aux coordonnées lues)"
fi
read -r X1 Y1 X2 Y2 <<< "$BOUNDS"
TX=$(( (X1 + X2) / 2 ))
TY=$(( (Y1 + Y2) / 2 ))
adb_ shell input tap "$TX" "$TY" || fail "bascule — input tap $TX,$TY a échoué"
sleep 1
adb_ exec-out screencap -p > "$SCREENS/emulator-after-toggle.png" || fail "capture — screencap après bascule a échoué"
[ -s "$SCREENS/emulator-after-toggle.png" ] || fail "capture — $SCREENS/emulator-after-toggle.png vide"
echo "-- [OK] bascule tapée ($TX,$TY) + capture : $SCREENS/emulator-after-toggle.png"

# — (7) Persistance : force-stop → relance à froid → pid + mode choisi dans le DataStore —
adb_ shell am force-stop "$PKG" || fail "persistance — am force-stop a échoué"
sleep 1
adb_ shell am start -n "$ACTIVITE" > /dev/null || fail "persistance — relance am start a échoué"
sleep 3
PID2="$(adb_ shell pidof "$PKG" | tr -d '\r')"
[ -n "$PID2" ] || fail "persistance — process non détecté après relance à froid (crash au cold start ?)"
DS="$(adb_ shell run-as "$PKG" cat "$DS_FILE" | tr -d '\r')"
MODE=""
if printf '%s' "$DS" | grep -aq "DARK"; then MODE="DARK"
elif printf '%s' "$DS" | grep -aq "LIGHT"; then MODE="LIGHT"
else
  printf '%s' "$DS" | head -c 200 >&2 || true
  fail "persistance — $DS_FILE ne contient ni LIGHT ni DARK après la bascule"
fi
echo "-- [OK] relance à froid — pid $PID2, thème persisté dans le DataStore : $MODE"

# — (8) Récapitulatif —
echo ""
echo "ÉMU OK — install + lancement + 3 onglets + bascule + persistance ($MODE) prouvés sur $SERIAL_EFFECTIF."
