# Shina Dual Space

Dual-space style app cloner inspired by Multiple Accounts: Dual Space. Clone apps into an Android Work Profile so each clone has separate data and login.

## v2.0 — real cloning via Work Profile
- Creates a managed Work Profile (same tech as Shelter / Island / Samsung Secure Folder)
- Clone installed apps into the profile with `installExistingPackage` — separate data/login
- Launch clones cross-profile via LauncherApps
- Dual Space grid (✓ = cloned), searchable clone picker, Secret Zone, optional 4-6 digit PIN lock
- Shina avatar icon

> v1.0 was a companion launcher only. v2.0 is the real clone: you must approve the one-time Work Profile setup in Android settings screens. One clone per app.

## Build
APK is built by GitHub Actions (`.github/workflows/build-apk.yml`). Download from Releases.
