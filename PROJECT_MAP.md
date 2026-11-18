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
- Latest uploaded reference HTML (`mind-web (1)(3).html`) is the sole visual/behavioral specification. Confirmed tokens from source: #0d0d0f background, gold #e8c547/#d4a942, radial glow, slow ambient orbs, translucent bordered cards, Cairo typography, floating bottom navigation and animated transitions. Full feature inventory is recorded below; native parity remains in progress.
- Validate supplied font family and extract original app icon, logo, and audio assets from the previous Android archive.
- Inspect and selectively port reusable native modules; exclude old package identity, unsafe signing material and stale placeholders.
- Confirm Firebase Android package registration and OAuth fingerprints; the previous project's Google OAuth client was reported empty.
- Implemented a first native Compose shell with the reference palette, animated ambient background layers, glass cards, bottom navigation and back affordance; this is a visual foundation, not complete parity.
- Added an email/password Firebase Authentication screen with create-account/sign-in flows, input validation and user-facing error handling. It requires a google-services.json registered for app.mind.mobile; the old app config does not match this package. OAuth and Firestore sync are not yet enabled.
- CI run 44360823804 confirms Firebase Google Services processing and dependency resolution now pass. Kotlin compilation then failed at five `Modifier.align` calls in `ReferenceAtmosphere`; fixed by placing the animated orbs inside a BoxScope. Await the new CI run before calling the fix successful.
- Current CI still has only the assemble job. Add separate unit-test and emulator-test jobs after the first clean assemble; upload APK only after successful assemble.

## Reference feature parity baseline
- Authoritative latest source upload: `mind-web (1)(3).html`, conversation file ID `file_00000000eba481f49c3e09aba0e7ad2a`, 215,327 bytes. It was preserved unchanged in the user's downloadable backup archive; SHA-256: `dd0230337f21a4b3e8ba7c530c6b08e8df5927d6eb1fc9c06f7a34e0ded3e990`. Treat this HTML as the source of truth for existing features, labels, layouts, colors, chart types, symbols, and behavior. Never overwrite it.
- User's explicit scope: reproduce ALL existing functionality and original design in native Android; no invented product modules, no unrelated additions, no removal/simplification of data or statistics. Allowed visual adaptation: render each existing feature as a polished interactive widget/card where appropriate; create a bespoke animated native background; add smooth transitions/micro-animations; preserve recognizable original iconography and chart symbols. The study module's charts and symbols are especially important.
- Design tokens/behavior found in source: dark base `#0d0d0f`, warm gold `#e8c547` / `#d4a942`, translucent glass cards with blur and soft borders, rounded card geometry, Cairo typography, staggered entrances, animated chart drawing, floating ambient orbs with gentle parallax (pointer/device orientation), responsive two/four-column widget grids, Arabic RTL and English LTR.
- Feature inventory from source code (native parity required):
  1. Onboarding: welcome, language selection, required name, body weight/height, permissions.
  2. Home: daily progress, habit summary, streaks, daily curve, today's stats, interactive widgets.
  3. Habits: create/edit/delete, custom icon/color, schedule days, time, reminders, daily completion and streak tracking.
  4. Statistics: segmented time ranges, progress rings, trend line charts/cards for steps, calories, habit completion, water, sleep, study/focus; daily/hourly breakdowns and habit/sleep grids.
  5. Awards: complete achievement catalog and progress thresholds for habits, streaks, check-ins, perfect days, water, sleep, study sessions/minutes, steps, workouts, meals, journal entries, notes, active days and unlocked achievements.
  6. Study: Pomodoro focus/break, timer controls and added time, focus tracking/lock, focus-minute summaries, study charts, and mind maps (create/edit nodes, colors, backgrounds, zoom/pan, export/share).
  7. Fitness: step sensor tracking, manual step entry, goals/body weight, walking/running/cycling sessions, GPS distance, duration, calories, history and shareable fitness summary.
  8. Sleep: start/wake sessions, duration validation, history/deletion, wake alarm, sound/vibration options.
  9. Water: daily intake, hourly tracking and progress goals.
  10. Nutrition/food logs, journal entries and their achievement/statistic data.
  11. Notes: search, folders, sorting, pinning, lock/private, trash/restore/permanent delete, rich text/checklists, image/audio attachments, voice recording/playback, drawing/doodle, export/share.
  12. Widgets: clock, water, Pomodoro, ambient audio/music and existing feature summaries. Treat these as native interactive widgets/cards inside the app; do not silently convert them into unrelated Android launcher widgets.
  13. Ambient audio: bundled/custom sounds, playback, looping and volume.
  14. Account and sync: email verification/sign-in, Google/Facebook/Apple provider flows in source, account management, Firebase user data sync/restore. Source HTML's Firebase config object is blank, so provider credentials/configuration must be supplied from the correct Firebase Android app; do not copy empty/old web config as a working login.
  15. Settings/security: Arabic/English, appearance, goals, notifications/sounds, motion/location permissions, PIN lock and timeout gate, orientation, backup JSON import/export, reset, about/privacy/help/share.
- Data parity: preserve all source records and timestamps needed for habit completion, hourly water, focus sessions, sleep, step/fitness sessions, food, journal, notes, mind maps, awards, and account backup/sync. Do not substitute demo values for real user data.
- Acceptance criteria: each source feature has a native screen or widget entry point, persists its source-equivalent data, contributes to the same relevant statistics/achievements, handles empty/error/permission states, and has tests. Charts must be fed by persisted data and retain the source metric labels, units, colors and symbols. No feature may be marked complete solely because a placeholder card exists.


## Current implementation checkpoint — 2026-11-18
- Commit `f4133b7b269bb1656e28a1f347c42340f8f373c1` replaces the initial placeholder-only home shell with interactive Compose screens for dashboard, habit check-ins and habit creation, water intake/goal, study Pomodoro controls, manual fitness step entry, sleep-duration logging, notes text entry, and account/settings navigation. The animated dark/gold atmosphere and glass surfaces remain.
- This is an initial UI/interaction slice, **not feature parity**. These values currently live in Compose saved state and are not yet backed by Room/DataStore or Firestore; GPS/sensor step tracking, full study charts/mind maps, sleep sessions/alarm, notes attachments/folders/trash, source achievement calculations, nutrition/journal, ambient audio, and complete settings are still pending.
- The step count control is explicitly manual; it is not represented as a working background pedometer. Do not claim sensor/GPS tracking until implemented and permission-tested.
- The study timer currently implements the 25-minute focus / 5-minute break controls and a session counter; the reference's full study chart and mind-map system remains pending.
- CI run 44393910445 was triggered for commit `f4133b7`; its status was pending when this checkpoint was written. Do not use the earlier successful run as evidence that this newer commit builds.
- Continue with durable local persistence and domain models before claiming data retention; then implement source-specific chart datasets and the remaining reference modules. Keep minSdk 23 / targetSdk 30.
