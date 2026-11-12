# Android app notes

Open this folder in Android Studio and build an APK.

Implemented in this MVP:
- Consent checkbox before any collection action
- User-initiated contacts export via Android runtime permission and Save As dialog
- User-initiated one-time location record (last known location) with runtime permission and Save As dialog
- System file picker to select owner-exported SMS/call-log evidence files (no direct SMS/call-log permission)

The app does not run a background service, hide itself, auto-upload files, or continuously track location. Evidence files can be uploaded through the website after investigator review. A future authenticated upload client should use TLS, short-lived scoped tokens and explicit per-file confirmation.
