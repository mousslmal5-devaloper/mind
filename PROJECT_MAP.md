# PROJECT_MAP.md

## [TECH_STACK]
- Android Native: Kotlin 1.9.22, Jetpack Compose, AGP 8.2.2, compileSdk 34.
- Compatibility contract: minSdk 23 / targetSdk 30 (Android 6–11).
- Firebase Authentication + Cloud Firestore; Firebase BoM 32.8.1 and Auth 22.3.1.
- Compose BOM 2024.05.00; Java 17.
- Arabic/English; supplied original font, logo, and audio assets are to be reused after validation.

## [SYSTEM_FLOW]
- App launch -> onboarding/profile setup when required -> authenticated or local app shell.
- Auth: email/password and OAuth providers only when their Firebase console configuration is verified.
- Local-first feature data -> authenticated per-user Firestore sync; explicit conflict/error handling.
- Native navigation mirrors the reference HTML's screens and journeys, with back navigation on every destination.

## [ARCHITECTURE]
- UI: Compose screens, reusable glass surfaces, reference-matched background/orbs, typography and motion system.
- Navigation: typed destinations and a consistent back affordance.
- Domain: feature use-cases and validation, independent of Compose.
- Data: repositories, Room/local persistence, Firebase adapters, sync state.
- Platform: notifications, audio playback, sensors, widgets and lifecycle integration only where specified by the HTML reference.
- Design fidelity: preserve the HTML's dark radial gradient, ambient moving orbs, translucent bordered cards, gold accent, responsive navigation, Cairo typography and transition timings; implement natively.

## [ORPHANS & PENDING]
- Read and inventory every reference HTML screen, event, data field, persistence path, auth provider and edge case.
- Validate supplied font family and extract original app icon, logo, and audio assets from the previous Android archive.
- Inspect and selectively port reusable native modules; exclude old package identity, unsafe signing material and stale placeholders.
- Confirm Firebase Android package registration and OAuth fingerprints; the previous project's Google OAuth client was reported empty.
- Implement feature parity incrementally and verify each acceptance criterion with tests.
- Add CI with separate assemble, unit-test and emulator-test outcomes plus APK artifact only after a successful build.
