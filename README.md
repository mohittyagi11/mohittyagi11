# Azadi Shashn — AI question companion for SHASN: Azadi

A native Android (Kotlin + Jetpack Compose) companion for **[SHASN](https://www.shasnthegame.com/)
/ SHASN: Azadi** by Memesys Games — the political-strategy board game where players answer
**ideology question cards** to earn political capital across four ideologies.

This app replaces the game's finite question deck with **AI-generated scenarios** and fixes the
parts of the card draw that flatten the debate. The physical board, voter pegs, and win condition
stay on the table — the app handles the **questions, the debate, and capital tracking**.

> Pass-and-play on one shared phone.

## The four SHASN ideologies

| Ideology | Earns | Leaning |
|---|---|---|
| **Capitalist** | Funds | free markets / free trade |
| **Supremo** | Clout | identity politics / the strongman |
| **Showstopper** | Media | showmanship / spectacle |
| **Idealist** | Trust | people's welfare / principle |

## What it fixes (vs. the printed cards)

| Problem in the deck | What the app does |
|---|---|
| Finite question cards — memorized, run out | Claude **generates** fresh scenarios on demand (real-history or futuristic, any country/structure) |
| A card forces a **yes/no** between just **2 ideologies** → kills debate | Every scenario gives **4 options — one per ideology** to argue over |
| Players **farm** the easy ideology | The **questioner can Twist** a card to make the obvious answer costly |
| The earned ideology is **printed** on the card | An **impartial AI judge** scores your argument and awards the matching ideology, gated by the **table's baseline** — rivals can't stall a good argument |

## The turn flow (matches the table)

The **questioner is the player seated *before* the turn-player**. On one shared phone:

1. **Questioner** reads the scenario to the turn-player and may **Twist** the card.
2. Hand over → the **turn-player** champions one of the four positions and argues it (typing the gist).
3. **Claude judges** the argument — scoring how genuine a case it makes for that ideology — and awards the matching **ideology point** (capital). The decision doesn't depend on rivals, so they can't stall it.
4. The running tally is kept. Rotate — the turn-player becomes the next questioner.

The app does **not** decide the winner — that's the physical board (voter pegs / region majorities).
It tracks each player's ideology points so you can read who holds what.

## The award rule (impartial judge + baseline)

Rivals don't decide the award — that would let them stall a good argument to deny you capital.
Instead it's objective:

1. **Claude judges** — it scores the argument 0–3 on how genuine a case it makes, and the point
   goes to the ideology the argument actually advances. Clear and un-stallable: *a real argument
   for X earns X.*
2. **Baseline gates farming** — the score you must clear **rises the more of that ideology you
   already hold**, and drops to the floor for an ideology you're light on (a brave/minority stance).
   You know the bar before you argue.
3. **Offline (no key)** — a deterministic rule: you earn the ideology you championed unless you're
   clearly hoarding it (held ≥ table-minimum + 2). Still no rival veto.

## Claude integration

- All API access is isolated in [`net/ClaudeClient.kt`](app/src/main/java/com/azadishashn/app/net/ClaudeClient.kt).
- Uses the Messages API with **structured outputs** (`output_config.format` + JSON schema) — every
  response is guaranteed-parseable JSON, and each option's ideology is constrained (schema `enum`)
  to the four SHASN ideologies.
- **Model** selectable in Settings: `claude-opus-4-8` (default, richest), `claude-sonnet-4-6`
  (recommended for fast/cheap live play), or `claude-haiku-4-5`.
- **No key? It still plays** on a small bundled deck
  ([`data/OfflineContent.kt`](app/src/main/java/com/azadishashn/app/data/OfflineContent.kt)).

### API key & security

v1 stores **your own** Anthropic API key on-device (Settings). An API key cannot be shipped safely
inside an APK, so for wide distribution you'd put it behind a backend proxy — because all network
access funnels through `ClaudeClient`, that swap only touches the networking layer. Never paste a
key into a shared chat; type it directly into the app.

## Build

The APK is built in CI — see [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml).
Push to the working branch (or run the workflow manually) and download the
**`azadi-shashn-debug-apk`** artifact from the run. To build locally, open the folder in Android
Studio (it generates the Gradle wrapper) and run on a device/emulator (min SDK 24).

## Project layout

```
app/src/main/java/com/azadishashn/app/
  model/Models.kt        DTOs + the four SHASN ideologies
  data/SettingsStore.kt  on-device API key + model
  data/OfflineContent.kt bundled fallback rounds
  net/ClaudeClient.kt    Messages API client (structured outputs)
  game/GameViewModel.kt  questioner/turn flow + consensus/baseline award logic
  ui/                    Compose screens (Setup, Settings, Round, Vote, Result, Standings)
```

## Credit

SHASN and SHASN: Azadi are created by **Memesys Games**. This is an unofficial fan companion app.
