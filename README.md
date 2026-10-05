# Shina Dual Space

Dual-space style app cloner inspired by Multiple Accounts: Dual Space. Clone apps into an Android Work Profile so each clone has separate data and login.

## v2.3 — tap-does-nothing fix
v2.2's signature-level permission silently blocked clones: each GitHub Actions debug build uses a fresh signing key, so the personal-profile and work-profile copies never matched certificates. v2.3 removes that permission and relies on the mandatory confirmation dialog instead (nothing clones without the user tapping Ya), and launch errors are now shown instead of swallowed.

**Clean start required:** delete the old Work Profile (Settings > search "profil kerja" > Hapus), reinstall with the single v2.3 APK, then set up fresh so both copies share one signature.

## Features
- Managed Work Profile (same tech as Shelter / Island / Samsung Secure Folder)
- Clone via `installExistingPackage` with explicit confirmation, launch via LauncherApps
- Hard warning before cloning Indonesian banking / e-wallet apps
- Dual Space grid (✓ = cloned), searchable picker, Secret Zone, optional PIN lock

## Build
APK is built by GitHub Actions. Download from Releases.
