# Shina Dual Space

Dual-space style app cloner inspired by Multiple Accounts: Dual Space. Clone apps into an Android Work Profile so each clone has separate data and login.

## v2.1 — clone fix
v2.0's Setup worked but cloning silently did nothing: cross-profile filters were only registered after a work-side launch, clone intents were pinned to the personal profile, and wrong-profile requests were dropped without feedback.

v2.1 registers the filters during provisioning, forwards clone requests correctly, explains wrong-profile requests, fixes work-profile detection, and adds a manual clone-by-package dialog inside the Work Profile.

After updating an existing v2.0 profile, open the Work Profile copy (briefcase badge) once first, then clone from the personal copy — or clone manually inside the work copy.

## Features
- Managed Work Profile (same tech as Shelter / Island / Samsung Secure Folder)
- Clone via `installExistingPackage`, launch clones via LauncherApps
- Dual Space grid (✓ = cloned), searchable picker, Secret Zone, optional PIN lock

## Build
APK is built by GitHub Actions. Download from Releases.
