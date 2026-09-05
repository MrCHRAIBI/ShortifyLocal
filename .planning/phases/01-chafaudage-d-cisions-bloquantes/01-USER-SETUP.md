# User Setup — Phase 01 (produit par le plan 01-04)

**Status: Incomplete**

## physical_device — téléphone ARM64 Android 13+ (D-03 / R7)

**Why:** R7 / D-03 : la double cible exige un téléphone physique ARM64 Android 13+ en adb USB pour la vérification fin de phase (émulateur x86_64 déjà disponible et vérifié vert au plan 01-04, D-01).

**Dashboard config:**
- Task: « Connecter le téléphone ARM64 Android 13+ en USB avec le débogage USB activé (visible via adb devices) »
- Location: Sur le téléphone : Options développeur → Débogage USB
- Env vars: none

**Vérification (une fois le téléphone branché) — commande scriptée fournie :**

```bash
adb devices                                  # le serial du téléphone doit apparaître (état « device »)
ANDROID_SERIAL=<serial> bash scripts/verify_emulator_p1.sh   # install + lancement + 3 onglets + bascule + persistance + captures
cp .planning/phases/01-chafaudage-d-cisions-bloquantes/screens/emulator-shell.png \
   .planning/phases/01-chafaudage-d-cisions-bloquantes/screens/device-shell.png
```

Attendu : `ÉMU OK — install + lancement + 3 onglets + bascule + persistance (LIGHT|DARK) prouvés sur <serial>` — sans `INSTALL_FAILED_NO_MATCHING_ABIS`/`INSTALL_FAILED_CPU_ABI_INCOMPATIBLE` (en cas de blocage ABI : STOP, escalade au propriétaire de spec, ne rien changer).
