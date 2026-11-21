# MIND reference parity audit

Reviewed sources:
- `mind-web (1)(7).html` — authoritative source for the complete feature set, visual design, screen hierarchy, labels, behavior, responsive rules, animations, and interactions.
- `MIND_final(3).zip` — supporting native Android foundation and reusable implementation references only. It must not override or reduce the HTML reference.
- Active implementation: `feature/native-rebuild` at commit `34dc24abb9f300c674800dcd2f8852f5bbe8dc88`.

## HTML reference inventory

### Main navigation and dashboard
- Main navigation: Home, Habits, Statistics, Achievements, Profile.
- Home: daily completion ring and count, quick links, streak, water increment/decrement, today's habit list, daily quote.
- Responsive bottom navigation on compact screens and side navigation on wide screens; RTL/LTR behavior.

### Habits
- Add/edit/delete habits; icon, color, schedule, time, selected weekdays and reminders.
- Time-of-day grouping, daily completion states, animated today's curve, hold-to-compare-yesterday, daily statistics.

### Modules
- Sleep: start/wake session, history, duration comparisons, averages, sleep quality, alarm scheduling, alarm sound selection/custom audio and stop controls.
- Study: Pomodoro, focus timer, focus history and weekly bars; mind-map list/editor with nodes, templates, backgrounds, styling, image background, export to PNG/PDF.
- Fitness: step goals and history, hourly/day/week/month charts, calories and distance calculations, manual step adjustment, walking/running/cycling workouts, live timer, distance and workout history/share.
- Nutrition: food entries, calorie tracking and history.
- Journal: journal records and history.
- Notes: search, folders, sorting, pinning, private/locked notes, trash/restore/permanent delete, rich-text formatting, checklists, images, audio recording/playback, drawing, share, text/PDF export.

### Statistics and achievements
- Day/week/month/year views and reference charts/rings for steps, calories, habits, sleep, study and water.
- Goal comparisons, heatmaps/history and achievement thresholds/progress.

### Profile, account and settings
- Account providers in the HTML UI: Google, Facebook, email/password; account details, verification and password reset flows where supported by the source.
- Cloud upload/restore controls and local data export/import/reset.
- Theme: dark/light/automatic; Arabic/English; daily goals; weight/height/BMI; habit reminders; sound effects; PIN lock; screen rotation; about/share.

### Shared interactions and presentation
- Cairo typography; dark/gold palette; liquid-glass cards; radial glow and moving ambient orbs; animated rings, charts, counters, sheets, dialogs and toasts.
- Original icons, spacing, card order, labels, responsive layout and RTL/LTR behavior are part of parity, not optional polish.

## Supporting ZIP implementation inventory

The ZIP contains reusable native Android foundations for Room/DAOs, repositories, onboarding/auth, habits, sleep, study/Pomodoro, notes, statistics, achievements, fitness, nutrition, reminders, step tracking, foreground services, backup/cloud sync, and Android home-screen widgets (clock, water, Pomodoro, music). Its package/application IDs differ from the active repository, so code must be adapted deliberately rather than copied blindly.

## Active branch gaps confirmed from source inspection

- Active branch already has native Compose screens/state for core dashboard, habits, statistics, achievements, sleep, study/focus, fitness/steps, GPS workouts, nutrition/food journal, mind maps, notes, account and Firestore backup/restore.
- Active branch manifest currently declares step and GPS services and sleep alarm receivers, but does not declare Android AppWidget receivers/providers.
- Active branch main source contains no AppWidget implementation or native music/audio playback implementation.
- Active branch resource tree contains Cairo font and styles but no launcher mipmap/drawable icon resources; manifest has no `android:icon` or `android:roundIcon` attributes. This matches the reported missing launcher icon.
- The ZIP contains widget implementation files and launcher icon resources that can be evaluated for reuse, after checking their behavior and visual identity against the HTML reference.
- The HTML reference has richer note editor behavior (audio, drawing, rich formatting, export and trash/folder flows) than can be assumed from the current native notes screen; each action must be checked individually.

## Implementation rules

1. HTML is the parity acceptance checklist for both UI and behavior.
2. Reuse ZIP foundations only where they implement an HTML-defined behavior or supply necessary native infrastructure.
3. Do not introduce new user-facing modules or remove any HTML-defined action.
4. Keep Kotlin + Jetpack Compose, application ID `app.mind.mobile`, minSdk 23, targetSdk 30, compileSdk 34.
5. Verify each feature with tests and, where platform behavior is involved, on real Android devices. A successful APK build alone is not feature completion.
6. Work only on `feature/native-rebuild`; do not merge to `main` without explicit user approval.

This document is an audit baseline, not a claim that feature parity is complete.
