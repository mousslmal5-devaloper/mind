# MIND — Native Android

MIND is being rebuilt as a native Android application with Kotlin and Jetpack Compose.

## Source of truth
`mind-web (1)(2).html` is the sole reference for the product's screens, visual design, background, interactions, and feature behavior. The native implementation must preserve its visual identity (including its radial background and animated ambient orbs), navigation, and user journeys. The previous Android project is an asset/code reference only.

## Compatibility
- minSdk 23 (Android 6.0)
- targetSdk 30 (Android 11)
- compileSdk 34
- JDK 17, Kotlin 1.9.22, AGP 8.2.2
- Firebase BoM 32.8.1; Firebase Auth 22.3.1
- Jetpack Compose BOM 2024.05.00

## Security
Never commit keystores, signing passwords, service-account credentials, or private OAuth secrets. Firebase client configuration is not a substitute for secure Firestore rules. Configure OAuth fingerprints and providers in Firebase Console before declaring each provider operational.

## Build
The project will include a reproducible Gradle 8.5 wrapper and CI workflow. A feature is complete only after build, unit tests, and applicable device/emulator tests pass.
