# MIND — Project map

Last reviewed: 2026-11-19
Active repository: `mousslmal5-devaloper/mind`
Active branch: `feature/native-rebuild`
Current baseline commit: `bdd826f11826eba922560463dd0d5754b3d3b1df`
Pull request: #1 (open draft; do not merge without explicit user approval).

## [TECH_STACK]
- Native Android app: Kotlin + Jetpack Compose; no WebView and no HTML runtime.
- Android compatibility contract: minSdk 23 (Android 6.0), targetSdk 30 (Android 11); compileSdk 34.
- Current pinned build: AGP 8.2.2, Kotlin 1.9.22, Java 17, Compose compiler extension 1.5.10, Compose BOM 2024.05.00.
- Current pinned AndroidX: Activity Compose 1.8.2, Navigation Compose 2.7.7, Lifecycle Runtime Compose 2.7.0, DataStore Preferences 1.0.0, Room 2.6.1 (declared; no Room database/entities currently in use).
- Firebase: BoM 32.8.1, Auth 22.3.1, Firestore 24.11.1; Google Services plugin 4.4.1.
- Package/application ID: `app.mind.mobile`.
- Arabic-first RTL UI with Cairo typography and the reference's dark/gold liquid-glass visual language.

### Dependency currency review (2026-11-19)
- Official current releases checked: AGP 9.5.1; Kotlin 2.4.21; stable Compose BOM 2026.09.00; Firebase Android BoM 35.2.0 and Google Services plugin 4.6.0.
- These are currency findings, not an instruction to upgrade blindly. Compose 2026.09 targets compileSdk 37 and its August 2026 release notes require AGP 9.1.2+; Firebase's newer BoM may raise the Android minimum supported API. Keep the currently verified Android 6/API 23 compatibility baseline until a staged toolchain/dependency compatibility build and API 23–30 validation pass. Do not mix a toolchain upgrade into feature-parity work.
- Sources: https://developer.android.com/reference/tools/gradle-api ; https://kotlinlang.org/docs/releases.html ; https://developer.android.com/develop/ui/compose/bom ; https://firebase.google.com/support/release-notes/android

## [SYSTEM_FLOW]
1. App process starts and restores local persisted state.
2. Native Compose navigation opens the requested reference-matched destination.
3. User action validates input/permissions, updates feature state, and persists it locally.
4. Sensors/GPS/alarms run through Android platform components only for corresponding reference functions.
5. Statistics and achievements derive from recorded source-equivalent events, not demo values.
6. Account authentication is separate from local-first use; cloud sync is not considered complete until per-user Firestore read/write, restore, and failure paths are verified.
7. Backup/import/reset and settings must preserve the reference's data semantics and clearly handle destructive actions.

## [ARCHITECTURE]
- `MainActivity.kt`: app host, current route state, dashboard, habits, water, study, fitness, sleep, notes and settings entry points.
- `MindPreferences.kt`: DataStore-backed local persistence and JSON codecs for current water, step, habit, study, sleep and workout records.
- `StatisticsScreens.kt`: native statistics/achievement UI and chart rendering.
- `MindMapScreen.kt`: mind-map editing/rendering.
- `StepCounterService.kt`, `StepCounterBootReceiver.kt`, `StepCounterMath.kt`: real Android step-counter sensor integration and daily baseline logic.
- `GpsWorkoutService.kt`, `WorkoutDistanceMath.kt`: GPS workout measurement and distance validation.
- `SleepAlarm.kt`: alarm scheduling, receiver behavior and sound/vibration settings.
- `AuthScreen.kt`: Firebase email/password authentication and email verification.
- Keep feature logic near the owning feature. Add Shared/Core abstractions only when at least two real call sites need the same behavior. No micro-file proliferation.
- Logging uses Android Log at failure boundaries; do not log passwords, tokens, personal note contents, or precise location traces.

## [REFERENCE_SOURCE]
- Sole visual/behavioral source: uploaded `mind-web (1)(5).html`.
- Conversation file ID: `file_000000001bb881f4bf9bb2fa5a4f65f8`; size 215,327 bytes; uploaded 2026-11-19.
- Never modify or replace the user's reference file. Do not use older `mind-web` uploads as the active specification unless the user explicitly changes the reference.
- Preserve the original layout, content hierarchy, labels, charts, metrics, background, responsive behavior and interactions. No new modules, no unsolicited features, no redesign.
- Verified visual tokens: dark background `#0d0d0f`; card `rgba(255,255,255,.055)`; border `rgba(255,255,255,.09)`; text `#f4f4f6`; secondary `#a2a2ab`; gold `#e8c547` / `#d4a942`; Cairo; radial gold glow; ambient moving orbs; translucent rounded cards; floating bottom navigation; responsive side navigation; animated transitions and charts.

## [CURRENT_IMPLEMENTATION]
Verified in source at baseline commit `9d5276254b921e1f5231ffd40bcbc4581977aee7`:
- Native Compose shell, animated ambient background, glass-card primitives and responsive bottom/side navigation.
- Nutrition screen: calorie input, daily total, seven-day bars, last-30 history and delete; entries persisted locally in DataStore.
- Journal screen: text entry, dated history and delete; entries persisted locally in DataStore. The reference defines the journal module but does not expose a navigation entry point in its visible navigation, so surfacing it remains unresolved rather than inventing a new dashboard shortcut.
- Food and journal records now feed their corresponding achievement thresholds and active-day calculations.
- Dashboard, habit check-in/create flow (basic list model), water intake and goal, statistics, awards.
- Pomodoro focus/break timer, session tracking and native mind maps.
- Real Android `TYPE_STEP_COUNTER` integration; no manual step increment/decrement control.
- GPS walking/running/cycling workout tracking, session history and distance/duration.
- Sleep start/wake sessions, history and wake alarm with silent/vibration, ringtone and selected audio file options.
- Notes currently persist a single plain-text field; this is not parity with the reference's full notes workspace.
- Firebase email/password sign-in/create-account and email verification. Google/Facebook/Apple sign-in and Firestore sync/restore are not verified as implemented.
- DataStore currently persists the implemented records listed above. Room is only a declared dependency; do not claim Room-backed storage.
- Unit tests exist for step-counter math, workout-distance math, and nutrition/journal input/date-total rules. CI run 44471696348 attempt 1 passed Unit tests; Assemble debug APK initially failed because Maven Central returned HTTP 403 while resolving dependencies. Assemble retry (attempt 2) is still in progress; no new APK artifact is verified for this commit yet. No connected/emulator test job is currently present.

## [ORPHANS & PENDING]
Every item below remains open until implemented from the exact HTML behavior, persisted, and covered by a verification goal.

### P0 — Source fidelity and data integrity
- Compare every native screen's layout, labels, responsive arrangement, colors, icons, charts and actions against `mind-web (1)(5).html`; fix mismatches without redesign.
- Add a reference-to-native parity checklist with per-feature evidence and test status.
- Validate DataStore serialization/migration and ensure records survive process death, date rollover and malformed stored data.
- Implement source-equivalent onboarding/profile flow (welcome, language, name, body metrics and permissions) where present in the reference.
- Complete habit model and editor: description, icon/color, time, scheduled weekdays, reminder, edit/delete, daily done/missed/pending state, time lock, history and streak calculations.
- Complete water behavior: hourly entries, daily goal/progress and matching hourly/statistical breakdown.
- Complete statistics: source time ranges, rings, curves, hourly breakdowns, weekly habit/sleep grids, source labels/units and persisted-data calculations.
- Complete achievements catalog and thresholds, including food, journal, notes, active days and unlocked-achievement progression.

### P1 — Missing reference modules and full feature depth
- Verify nutrition and journal screen acceptance against the reference on device, including date rollover, persistence after process death and delete behavior.
- Decide how to expose the source-defined journal route: `mind-web (1)(5).html` declares the module metadata and generic module renderer, but no visible navigation action reaches it. Do not invent a shortcut without resolving this source ambiguity.
- Include food/journal entries in backup export/import and restore validation.
- Study parity: focus lock behavior, complete charts and source-equivalent summaries; retain native mind-map editing, backgrounds, node operations, zoom/pan and export/share parity.
- Fitness parity: profile body metrics/step goal, hourly step distribution, source calculations and complete workout summary/history/share behavior. Distinguish sensor-measured values from estimates.
- Sleep parity: exact session validation, comparisons, 7-day/30-day summaries, grids, history and wake alarm behavior.
- Notes workspace parity: search, folders, sorting, pinning, lock/PIN, trash/restore/permanent delete, rich text/checklists, image/audio attachments, recording/playback, drawing, export/share.
- Dashboard/widgets parity: source clock, water, Pomodoro, ambient audio and feature summary widgets as in-app UI; do not invent launcher widgets.
- Ambient audio parity: source sound choices, custom audio, looping and volume controls.
- Settings parity: language, appearance, goals, notification/sound options, permissions, PIN/timeout, orientation, backup/import/export, reset, about/privacy/help/share.

### P2 — Account, sync and release verification
- Verify correct Firebase Android app configuration for `app.mind.mobile`, OAuth fingerprints and provider setup before enabling providers.
- Implement Firestore per-user sync, restore, offline/error handling and conflict policy; no cloud-sync claim before end-to-end verification.
- Verify import/export preserves all supported records and attachments; destructive reset must require confirmation.
- Add instrumentation/UI tests and API 23–30 smoke tests; current CI has unit tests + assemble only.
- Confirm final font/icon/audio assets and licensing/source files before bundling.
- Review dependency/toolchain upgrades separately, preserving minSdk 23 and targetSdk 30.
- Resolve existing build warnings before release: deprecated AutoMirrored icon usages in StatisticsScreens.kt, deprecated Notification.Builder(Context) usage in StepCounterService.kt, and Node 20 warnings from the current GitHub Actions v4 steps.

## [MILESTONES — VERIFIABLE GOALS]
1. **M0 — Baseline and source lock:** exact reference identified; baseline CI, artifact and branch verified; PROJECT_MAP synchronized. Goal: source and implementation inventory has no stale reference or unsupported completion claim.
2. **M1 — Native shell parity:** all reference destinations reachable with matching responsive layout/theme/navigation. Goal: per-screen visual checklist passes on phone and wide layouts.
3. **M2 — Reliable domain data:** source-equivalent models and persistence for habits, water, study, sleep, fitness, food, journal, notes and mind maps. Goal: round-trip serialization + process-restart tests pass.
4. **M3 — Feature parity:** each module's user journey, edge cases, permissions, edit/delete and empty states work. Goal: acceptance tests pass for each feature before the next module is marked complete.
5. **M4 — Statistics and achievements:** all source metrics derive from persisted records. Goal: deterministic fixture tests match source formulas and chart datasets.
6. **M5 — Platform integrations:** sensors, GPS, alarms, audio and media/document access behave correctly across supported Android versions. Goal: permission-denied, unavailable hardware, lifecycle and restart cases pass.
7. **M6 — Account and backup:** authentication providers only when configured; Firestore sync/restore and JSON backup round trips verified. Goal: user-scoped data and failure recovery tests pass.
8. **M7 — Release quality:** no regressions, no placeholder/TODO code added, no new deprecations, API 23–30 smoke coverage, signed release configuration kept outside source control. Goal: all CI jobs pass and APK artifact is tied to the exact final commit.

## [WORK RULES]
- Work only in this repository and `feature/native-rebuild`; do not touch legacy repositories.
- Never merge PR #1 or push to `main` without explicit user approval.
- For every code change: inspect current file + blob SHA, make the smallest targeted edit, add/update tests, run CI, then update this map with exact evidence.
- Do not call a feature complete because a route/card exists. Completion requires working behavior, persisted source-equivalent data, error/empty/permission handling, statistics integration where relevant, and tests.
- Do not fabricate successful builds, test coverage, sensor measurements, OAuth setup, cloud sync, APKs or links.
