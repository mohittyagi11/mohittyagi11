# Dose

Reminders that follow your **day**, not the clock.

Dose is a personal supplement reminder for Android built around one idea: a
rigid 9:00 AM alarm is useless when your life isn't rigid. Instead of clock
times, Dose anchors reminders to **real-world events** — when you wake, when you
get home, when you head out — and asks for a single, satisfying tap to confirm.

> Status: **early, in active construction.** Built in small increments. See the
> roadmap below for what's live.

---

## Why event-anchored

Fixed-time reminders fail the moment the day shifts. Dose's triggers are:

- **On waking** → the morning, fasted stack. Detected by fusing multiple signals
  (see below), not a single alarm.
- **Arriving home** → the evening, with-food stack (geofence).
- **Leaving (office / for home)** → a light checkpoint, detected by geofence
  **and** by your ride-hailing app's "driver arriving" notification (held for
  ~45s so a cancelled cab doesn't trip it).
- **Before sleep** → the night, wind-down stack.
- **Cadence** (alternate-day iron, monthly pulse) → date math from an anchor, so
  a missed dose never silently breaks the rhythm.

## The wake-inference engine (the hard part)

Android has no "wake" event, so Dose infers it from a fusion of evidence and
only fires once it's **confident and sustained** (a 3 AM half-asleep glance
won't trip it):

1. **Health Connect sleep data** — your Ultrahuman ring's sleep session; its
   *end* time is strong wake evidence.
2. **Google Sleep API** — sleep-segment / low sustained sleep-confidence.
3. **Activity transition** — your first walk with the phone ("switching places").
4. **Sustained usage** — confirmed real interaction, not a momentary check.

A latest-wake fallback alarm guarantees the morning dose is never dropped. All
sources are event/poll based, so **no persistent notification is required**.

## Designed to be lived with

- **Dark-first, restrained** — near-black canvas, one accent, generous space,
  spring-physics motion, crisp haptics. No streaks, no nagging.
- **One-tap confirm** — mark a group taken straight from the notification,
  without opening the app.
- **Yours to shape** — every item is data: category, type, dose, frequency and
  trigger are all editable, and customizing is meant to be *fun*.
- **Easy restock** — items can carry a purchase link (e.g. Amazon) for one-tap
  reorder when stock runs low.
- **A brain** — an integrated vision-language model (Claude) for scanning a
  label into a new item, suggesting tweaks, and natural-language automation.

---

## Build & run

This is a standard Gradle + Kotlin + Jetpack Compose (Material 3) project.

**In Android Studio:** open the project and Run. (`minSdk 29`, `targetSdk 35`.)

**From the command line** (requires the Android SDK):

```bash
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

**CI:** every push runs `.github/workflows/android.yml`, which builds the debug
APK on GitHub's runners and uploads it as the `dose-debug-apk` artifact — so an
installable build is available even without a local Android SDK.

### Tech

Kotlin · Jetpack Compose + Material 3 · manual DI (no Hilt, for a lean build) ·
Room (planned) · WorkManager + AlarmManager · Play Services Location
(geofencing) · Health Connect · Activity Recognition / Sleep API.

---

## Roadmap

- [x] **1 — Foundation:** Gradle/Compose scaffold, theme, CI APK pipeline.
- [ ] **2 — Data:** flexible Room schema (items/categories/frequency/triggers) + seed.
- [ ] **3 — Triggers:** wake engine, geofence, cadence alarms, reliability/boot re-arm.
- [ ] **4 — Notifications + one-tap check-off.**
- [ ] **5 — UI:** glanceable home + the playful, customizable config.
- [ ] **6 — Cab-arrival leaving trigger** (notification listener + live probe).
- [ ] **7 — Restock from product links.**
- [ ] **8 — VLM brain:** label scanning, suggestions, NL automation.

> Built for a single user's stack; not medical advice.
