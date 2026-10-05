# Shina Dual Space

Dual-space style app cloner inspired by Multiple Accounts: Dual Space. Clone apps into an Android Work Profile so each clone has separate data and login.

## v2.4 — direct cross-profile clone + stable signing
On Xiaomi devices the Android "Complete Action Using" chooser always showed an empty Work tab, so clone requests looped back to the personal profile. v2.4 sends clone requests with CrossProfileApps.startActivity (the official same-app cross-profile API) straight to the work-profile confirmation dialog, and signs every build with a committed throwaway debug-grade keystore so updates install over each other and both profile copies share one signature.

**One last clean start:** delete the old Work Profile (Settings > search "profil kerja" > Hapus), reinstall with the v2.4 APK, set up fresh. From v2.4 onward updates should install normally.

## Features
- Managed Work Profile (same tech as Shelter / Island / Samsung Secure Folder)
- CrossProfileApps clone requests with explicit confirmation, installExistingPackage, LauncherApps launch
- Hard warning before cloning Indonesian banking / e-wallet apps
- Dual Space grid (✓ = cloned), searchable picker, Secret Zone, optional PIN lock

## Build
APK is built by GitHub Actions. Download from Releases.
