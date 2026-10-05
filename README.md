# Shina Dual Space

Dual-space style app cloner inspired by Multiple Accounts: Dual Space. Clone apps into an Android Work Profile so each clone has separate data and login.

## v2.2 — security hardening
- Clone trigger moved off the exported launcher into a dedicated `CloneActivity` protected by a signature-level permission — only same-certificate copies (personal + work profile) can trigger it
- Every clone requires an explicit confirmation dialog before installing
- Hard warning before cloning Indonesian banking / e-wallet apps (fresh Work Profile sessions can look high-risk to banks; do not clone finance apps)
- v2.1 clone-delivery fixes retained: filters registered at provisioning, unpinned cross-profile intents, manual clone-by-package inside the Work Profile

After updating, open the Work Profile copy (briefcase badge) once, then clone from the personal copy.

## Features
- Managed Work Profile (same tech as Shelter / Island / Samsung Secure Folder)
- Clone via `installExistingPackage`, launch clones via LauncherApps
- Dual Space grid (✓ = cloned), searchable picker, Secret Zone, optional PIN lock

## Build
APK is built by GitHub Actions. Download from Releases.
