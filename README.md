# Azadi Shashn — AI debate companion

A native Android (Kotlin + Jetpack Compose) companion app for the freedom-and-governance
party game **Azadi Shashn**. It replaces the game's finite scenario deck with **AI-generated
government scenarios** and fixes the parts of the physical game that flatten the debate.

> Pass-and-play on one shared phone. Players still collect the physical **ideology cards** —
> the app generates the scenarios, runs the vote, and tracks who holds what.

## What it fixes (vs. the physical deck)

| Problem in the deck | What the app does |
|---|---|
| Finite scenarios — memorized, run out | Claude **generates** fresh government scenarios on demand (historical or futuristic, any country/structure) |
| Each card gives only **2 ideologies** + true/false → kills debate | Every scenario offers **4 distinct ideology-framed positions** to argue |
| Players **farm** the easy ideology | The question-master can **Twist** a scenario to make the obvious answer costly |
| The "correct" ideology is **printed/static** | The card you earn comes from **group consensus + the table's baseline**, not an answer key |

## The award rule (the interesting bit)

Instead of a printed answer, the ideology card a player earns is decided dynamically:

1. **Consensus** — after the active player argues a position, the *other* players vote on which
   of the four ideologies the argument embodied, and how many were persuaded.
2. **Baseline** — the app knows how many of each ideology every player already holds. The more
   of the consensus ideology the active player *already* holds, the **more voters they must
   convince** to earn another (anti-farming). Defending an ideology you're *light* on (a "brave
   minority stance" vs. the table) **lowers** the threshold.
3. **Neutral adjudicator** — optionally, Claude classifies the spoken argument and adds one line
   of real historical context, to keep the vote honest. The **table still decides**.

So: *consensus picks the ideology, the baseline sets how hard it is to get, Claude keeps the
judgment grounded.*

## How the turn flows

`Setup players → generate round → champion a position & debate → (optional Twist / ask Claude)
→ table votes → award resolved against consensus+baseline → next player`

## Claude integration

- All API access is isolated in [`net/ClaudeClient.kt`](app/src/main/java/com/azadishashn/app/net/ClaudeClient.kt).
- Calls use the Messages API with **structured outputs** (`output_config.format` + a JSON schema),
  so every response is guaranteed-parseable JSON — no prose parsing on-device.
- Each scenario's four ideologies are constrained (schema `enum`) to a fixed 12-card set, so vote
  tallies and collected-card counts stay consistent.
- **Model** is selectable in Settings: `claude-opus-4-8` (default, richest), `claude-sonnet-4-6`
  (recommended for fast/cheap live play), or `claude-haiku-4-5`.
- **No key? It still plays.** Without an API key the app falls back to a small bundled deck
  ([`data/OfflineContent.kt`](app/src/main/java/com/azadishashn/app/data/OfflineContent.kt)).

### API key & security

v1 stores **your own** Anthropic API key on-device (entered in Settings). This is the simplest
setup for a game among friends. An API key cannot be shipped safely inside an APK, so for wide
distribution you'd put the key behind a small backend proxy — because all network access funnels
through `ClaudeClient`, that swap only touches the networking layer.

## Build & run

Requires **Android Studio** (Ladybug or newer) with the Android SDK.

1. Open this folder in Android Studio. It will sync Gradle and generate the Gradle wrapper.
   (From a CLI with Gradle 8.9+ installed you can instead run `gradle wrapper` once, then
   `./gradlew assembleDebug`.)
2. Run on an emulator or device (min SDK 24).
3. Open **Settings**, paste your Anthropic API key, pick a model, and start a game. Or skip the
   key to try it on the bundled deck.

## Project layout

```
app/src/main/java/com/azadishashn/app/
  model/Models.kt        data classes + the 12 ideology cards
  data/SettingsStore.kt  on-device API key + model
  data/OfflineContent.kt bundled fallback rounds
  net/ClaudeClient.kt    Messages API client (structured outputs)
  game/GameViewModel.kt  turn flow + consensus/baseline award logic
  ui/                    Compose screens (Setup, Settings, Round, Vote, Result, Standings)
```

## Status / assumptions

This is a v1 built to validate the design. A few choices were made as defaults (model `opus-4-8`,
own-key auth, the exact award thresholds) and are easy to tune — see `GameViewModel.submitVote`
for the threshold maths.
