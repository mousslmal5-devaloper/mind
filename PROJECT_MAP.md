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
- Reference HTML (`mind-web (1)(2).html`) is the sole visual/behavioral specification. Confirmed tokens: #0d0d0f background, 900x500px radial gold glow at 85%/-10%, five slow parallax ambient orbs, translucent cards (rgba white .055/.09 border), 22px card corners, Cairo 15px/1.6, gold #e8c547/#d4a942, floating bottom navigation and staggered entrance transitions. Full feature inventory and native parity remain pending.
- Validate supplied font family and extract original app icon, logo, and audio assets from the previous Android archive.
- Inspect and selectively port reusable native modules; exclude old package identity, unsafe signing material and stale placeholders.
- Confirm Firebase Android package registration and OAuth fingerprints; the previous project's Google OAuth client was reported empty.
- Implemented a first native Compose shell with the reference palette, animated ambient background layers, glass cards, bottom navigation and back affordance; this is a visual foundation, not complete parity.
- Added an email/password Firebase Authentication screen with create-account/sign-in flows, input validation and user-facing error handling. It requires a google-services.json registered for app.mind.mobile; the old app config does not match this package. OAuth and Firestore sync are not yet enabled.
- CI run 44360823804 confirms Firebase Google Services processing and dependency resolution now pass. Kotlin compilation then failed at five `Modifier.align` calls in `ReferenceAtmosphere`; fixed by placing the animated orbs inside a BoxScope. Await the new CI run before calling the fix successful.
- Current CI still has only the assemble job. Add separate unit-test and emulator-test jobs after the first clean assemble; upload APK only after successful assemble.
