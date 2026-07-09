# Azadi Shashn — Gameplay Evolution Ideas

A living design document. Two passes of ideation, cross-referenced: **Part A** is
the "real politics" deep pass — mechanics that make debates feel like actual
political life. **Part B** is the earlier broad experience pass (speed, ceremony,
identity, reliability). **Part C** maps the shared foundations so we build engines
once and power many features. **Part D** is a suggested sequencing.

---

## Part A — Making it feel like REAL politics

The current loop is `player → argument → neutral judge`. Real politics is never
that clean. Each way it's messier is a mechanic we can add.

### A1. Argue to audiences, not to a judge — stakeholder blocs
Politicians don't convince referees; they convince constituencies. Give each
round 2–3 **blocs** drawn from a pool (farmers, industrialists, students, army,
clergy, urban middle class, media barons, unions, diaspora). The judge scores how
the argument *lands per bloc* — you gain standing with some and lose it with
others, by design. An answer that thrills industrialists alienates unions.
- Adds the defining real-politics tension: **you cannot please everyone**.
- Output: per-bloc reaction line + support delta. UI: small bloc chips with
  up/down ticks on the verdict screen.
- Long game: players accumulate a **coalition** — which blocs they've courted —
  and scenarios can target a player's base ("your farmer base is watching…").

### A2. The record haunts you — hypocrisy & the artful U-turn
Every verdict is already stored. Feed the player's **past positions** into the
judge prompt so it can notice: *"In Round 2 you argued for blanket surveillance;
today you discovered privacy. The press notices."*
- Consistency earns a small bonus; a flip-flop costs — **unless the pivot is
  argued well** (the judge scores "justified evolution" vs "naked U-turn").
  Rewarding the skilful pivot is deeply true to politics.
- Zero new storage needed; it's prompt assembly from existing verdict history.

### A3. Spin — the same answer, two front pages
After the verdict, generate **two partisan headlines** reporting the player's
argument from opposite outlets (e.g. a pro-market daily and a workers' paper).
The same sentence becomes "BOLD REFORM SAVES GRID" and "SELLOUT REGIME AUCTIONS
THE NATION."
- Cheapest high-impact feature in this doc: one extra schema field on judge.
- Teaches the realest lesson in politics: **you don't control what you said —
  the press does.** Also the most screenshot-able moment in the game.

### A4. The nation remembers — state meters + conditioned scenarios
Deepens the "consequence thread": track 3–4 **nation meters** (Economy, Liberty,
Stability, Institutional Trust). Each verdict's causal chain nudges them.
Scenario generation is conditioned on the meters: low Liberty breeds unrest and
censorship scenarios; low Economy breeds austerity and IMF-style dilemmas.
- Choices become **systemic**, not episodic — governance, not trivia.
- The end-of-game epilogue derives from the meters: where did the nation land?
- UI: a slim meter strip on Standings; movement revealed after each verdict.

### A5. Snap polls — approval as a living number
Politicians live by polls. After each verdict, a **snap approval rating** per
player (drifting with bloc reactions, hypocrisy hits, meter outcomes). Shown as
a one-line poll flash: *"Snap poll: 54% approve, down 6."*
- Cheap (derived from A1/A2 outputs), keeps score emotional between rounds.

### A6. Cross-examination — debates, not monologues
Real debates have rebuttals. After the answer, one opponent gets a timed **20s
rebuttal**, then the answerer a **15s closer**. Judge weighs all three.
- Converts dead waiting time (other players just listening) into play.
- Natural pairing with the argument timer (B6) and the existing STT flow.

### A7. Party lines — the devil's advocate draw
Sometimes you defend what the party decided, not what you believe. Optional
mode: the round **assigns** the ideology you must argue (secret draw). Judge
scores how convincingly you served the *assigned* position.
- Kills ideology-farming completely; trains actual debate skill.
- Great party variant: everyone knows someone is arguing against their heart.

### A8. Scandals & skeletons
Random event between rounds: a **scandal** generated from the player's own past
choices ("the surveillance contract you approved went to your cousin's firm").
The player must respond publicly; the judge scores **damage control** (denial,
deflection, apology, counter-attack — all valid, all scored differently).
- Reuses the stored history (same engine as A2); pure comedy gold at a table.

### A9. Leaks mid-debate — imperfect information
Real decisions happen with incomplete facts. Variant of twist: the complication
drops **while the player is arguing** (a "BREAKING" interstitial), forcing them
to adapt live. Judge scores composure and integration of the new fact.
- Uses the existing twist machinery with different timing.

### A10. Eras — the possible changes with the times
The app already takes a city context. Add an **era dial**: Partition-era 1947,
Emergency 1975, Liberalization 1991, present day, near-future 2035. Era changes
scenario flavor, vocabulary, what's politically thinkable, and the historical
echoes the judge cites.
- One prompt parameter; enormous replayability and a history lesson besides.

---

## Part B — Earlier broad pass (kept for the record)

- **B1. Streaming generation** — stream scenario text in progressively; waits
  *feel* half as long. *(Grows in value as Part A makes prompts/outputs richer.)*
- **B2. Prefetch next round** during debate; **cache a round buffer** offline.
- **B3. Consequence thread** — scenarios reference prior outcomes. *(Superseded
  by / merged into A4's meters + story-so-far.)*
- **B4. Verdict ceremony** — hold → drumroll haptic → seal stamp → strength
  count-up. *(Ends with A3's two spun headlines as the final beat.)*
- **B5. End-of-game awards** — "Idealist of the Night," "Sharpest Tongue,"
  "Flip-Flopper." *(A2's hypocrisy data feeds Flip-Flopper; A1's blocs feed
  "Coalition Builder.")*
- **B6. Argument timer** (60/90s ring) + **live transcription** with tap-to-fix.
- **B7. Contest the verdict** — one appeal per player per game. *(A6's rebuttal
  phase may subsume this; keep whichever plays better.)*
- **B8. Ideology fingerprint** — per-player four-axis profile across games.
  *(Extends naturally with A1's bloc coalition profile.)*
- **B9. Prompt caching + model routing** (Haiku for judging, big model for
  generation). *(More important once Part A enriches every prompt.)*
- **B10. Epilogue** — generated end-of-game "where the nation ended up."
  *(Becomes deterministic-flavored via A4's meters.)*

---

## Part C — Cross-reference map: shared engines

Build these once; many features light up.

**Engine 1: The Dossier (stored political history)**
Already exists as persisted rounds/verdicts. Powers: A2 hypocrisy, A8 scandals,
B5 awards, B8 fingerprint, B10/A4 epilogue. → *First investment: a small
`dossier(playerId)` summarizer that turns stored verdicts into 3–5 prompt lines.*

**Engine 2: Judge schema extensions**
One richer judge call powers: A1 bloc reactions, A3 headlines, A5 poll delta,
A2 consistency note. All are extra fields on the existing structured output —
no new API calls. *(Watch token budget: MAX_TOKENS=8000 was sized for narration;
re-check with the richer schema. B9's caching/routing offsets the cost.)*

**Engine 3: Nation state (meters)**
A4 powers B10 epilogue, conditions generation, feeds A5 polls and A8 scandal
plausibility. Small persisted struct on GameState; nudged by verdict output.

**Engine 4: Debate flow (timers + phases)**
B6 timer underlies A6 cross-examination and A9 mid-debate leaks. One phase state
machine in RoundScreen serves all three.

**Engine 5: Perceived-latency stack**
B1 streaming + B2 prefetch + B9 caching. Prerequisite for Part A: richer prompts
mean longer generations; this stack keeps the table from feeling it.

Dependency sketch:
```
Dossier ──────────► A2 hypocrisy ─► B5 awards
   │                └► A8 scandals
Judge schema+ ────► A1 blocs ─► A5 polls ─► B8 fingerprint+
   │                └► A3 headlines ─► B4 ceremony finale
Nation meters ────► conditioned scenarios ─► B10/A4 epilogue
Debate phases ────► B6 timer ─► A6 cross-exam / A9 leaks
Latency stack ────► (enables everything above to stay snappy)
```

---

## Part D — Suggested sequencing

**Wave 1 — cheap, transformative, no new UI flows:**
A3 headlines · A2 hypocrisy callouts · B4 ceremony · B1 streaming.
*(One judge-schema pass + dossier summarizer + reveal choreography.)*

**Wave 2 — the living nation:**
A4 meters + conditioned generation · A5 snap polls · B10 epilogue · B2 prefetch.

**Wave 3 — richer table play:**
A1 blocs · B6 timer + live transcription · A6 cross-examination · B5 awards.

**Wave 4 — variants & depth:**
A7 party-lines mode · A8 scandals · A9 mid-debate leaks · A10 eras ·
B8 fingerprint · B9 caching/routing.

---

*Nothing here is committed work — it's a menu. Each wave is independently
shippable and testable at a real table before the next.*
