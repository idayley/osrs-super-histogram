# Super Histogram Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Super Histogram RuneLite plugin: a resizable always-on tick-timeline bar that auto-detects the activity and shows the matching view.

**Architecture:** A thin plugin class turns RuneLite events into plain Java state (a ring buffer of per-tick records plus small trackers). That state has no RuneLite types, so it is unit-tested with JUnit. The overlay reads the state each frame and paints it with Graphics2D, through a pure layout class that applies the resize breakpoints. Each mode is a `ModeView` painter chosen by a pure `ModeDetector`.

**Tech Stack:** Java 11, Gradle (RuneLite example-plugin template), RuneLite client API, Lombok, JUnit 4, Mockito.

**Spec:** `design/spec.md` (mockups in `design/mockups/`).

## Scope: one plan, five phases

Each phase ends with a plugin you can run in the RuneLite dev client and use:

| Phase | Ships | Tasks |
|---|---|---|
| 1 Foundation | The bar itself: resizable, General / AFK / Minimal modes, XP drops, hits, clicks, run energy, login time, metronome, done-blink | 1–9 |
| 2 Combat | Lazy flick with the F beat, prayer pills, actions row, eat costs, 1-tick flick, combat XP placement, hull flash on hit | 10–14 |
| 3 Production + alch | Cooking-style inventory modes, high alch gold, multi-XP numbers and dots | 15–17 |
| 4 Gathering + agility | Woodcutting / mining / fishing / firemaking, agility laps | 18–19 |
| 5 Finish | Full settings panel, icon set, Plugin Hub submission | 20–21 |

Phases 2–5 are written at task level (files, interfaces, tests to write). Before starting a phase, the executor expands its tasks into steps in the same style as Phase 1, using that phase's mockup as the source of exact values.

## Global Constraints

- Package `com.superhistogram`; plugin name "Super Histogram".
- Java 11 source/target; `runeLiteVersion = 'latest.release'` (template default).
- Nothing in `model/` may import `net.runelite.*`. Only `SuperHistogramPlugin`, `SuperHistogramConfig`, `ui/` and `collect/` may.
- The rules boundary in `design/spec.md` is a hard constraint. Any feature that predicts an NPC's attack, counts attacks, suggests prayers, or shows which player an NPC targets is rejected in review.
- Default size 1026 × 62. Breakpoints are exactly: height < 56 hides the actions row, height < 40 gives one row, width < 720 hides the right readout, width < 480 hides the left readout. Scale `u = h / 62`, tick width `max(18, 32u)`.
- Colours are exactly the hex values in `design/spec.md` § Colours, defined once in `ui/Palette.java`.
- Font: Outfit (SIL OFL 1.1), bundled as `src/main/resources/com/superhistogram/Outfit-*.ttf` together with `OFL.txt`.
- No network calls of any kind, except item prices through RuneLite's `ItemManager` (Phase 3).
- 1 game tick = 600 ms. Idle logout = 300 s without input.

## Review Focus

1. **World hop:** hopping (game state HOPPING → LOGGED_IN) must not reset login time, session XP or the timeline. Test: `SessionClockTest.hopKeepsLoginTime`, `XpTrackerTest.hopDoesNotCreateDrops`.
2. **First stats after login:** RuneLite fires StatChanged for every skill at login with total XP. These must become the baseline, not 23 XP drops. Test: `XpTrackerTest.firstStatIsBaselineNotDrop`.
3. **Extreme sizes:** resizing to 1 × 1 or 4000 × 400 must clamp (min 220 × 22, max 4000 × 160) and never throw. Test: `BarLayoutTest.clampsTinyAndHuge` and `BarRenderSmokeTest.rendersAtExtremes`.
4. **Long sessions:** a 10-hour session must not grow memory. The timeline is a fixed ring of 512 ticks. Test: `TimelineTest.ringOverwritesOldest`.
5. **Logged out / loading:** rendering with no local player or while logged out draws the bar empty (or hidden) without a NullPointerException. Test: `BarRenderSmokeTest.rendersWithEmptyState` plus manual check in Task 9.

---

## Phase 1 — Foundation

### Task 1: Project scaffold that loads in the dev client

**Files:**
- Create: `build.gradle`, `settings.gradle`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `runelite-plugin.properties`, `.gitignore`
- Create: `src/main/java/com/superhistogram/SuperHistogramPlugin.java`, `SuperHistogramConfig.java`
- Create: `src/test/java/com/superhistogram/SuperHistogramPluginTest.java` (dev-client launcher)
- Create: `src/main/resources/com/superhistogram/Outfit-Regular.ttf`, `Outfit-SemiBold.ttf`, `Outfit-Bold.ttf`, `OFL.txt`

**Interfaces:**
- Produces: `SuperHistogramPlugin` (with `@PluginDescriptor(name = "Super Histogram")`) and `SuperHistogramConfig` (`@ConfigGroup("superhistogram")`), empty for now.

- [ ] **Step 1: Install JDK 11** with `brew install openjdk@11`, then `export JAVA_HOME=$(brew --prefix openjdk@11)`. Check that `$JAVA_HOME/bin/java -version` prints 11.
- [ ] **Step 2: Copy the template.** Clone `https://github.com/runelite/example-plugin` into a temp dir and copy its Gradle wrapper, `build.gradle`, `settings.gradle` and `.gitignore`. Set `rootProject.name = 'super-histogram'` and `group = 'com.superhistogram'`. Add `testImplementation 'org.mockito:mockito-core:4.11.0'`.
- [ ] **Step 3: Write the plugin and config classes.** The plugin's `startUp`/`shutDown` do nothing yet. `runelite-plugin.properties` has `displayName=Super Histogram`, `author=idayley`, `plugins=com.superhistogram.SuperHistogramPlugin`.
- [ ] **Step 4: Write the launcher test** `SuperHistogramPluginTest.main` calling `ExternalPluginManager.loadBuiltin(SuperHistogramPlugin.class); RuneLite.main(args);`. This is the template pattern.
- [ ] **Step 5: Add fonts.** Download Outfit from Google Fonts (OFL), put the 400/600/700 TTFs and `OFL.txt` in resources.
- [ ] **Step 6: Build.** Run `./gradlew build`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 7: Manual check.** Run the launcher (`./gradlew run` if the template defines it, otherwise run the test class's main from the IDE) and check that "Super Histogram" appears in the plugin list.
- [ ] **Step 8: Commit.** `feat: scaffold Super Histogram plugin`

### Task 2: Tick timeline (ring buffer)

**Files:**
- Create: `src/main/java/com/superhistogram/model/TickRecord.java`, `model/Timeline.java`, `model/XpDrop.java`, `model/Hit.java`, `model/Span.java`
- Test: `src/test/java/com/superhistogram/model/TimelineTest.java`

**Interfaces:**
- Produces:
  - `final class XpDrop { final String skill; final int amount; }`
  - `final class Hit { final int amount; final boolean blocked; }` (blocked = 0 or miss)
  - `final class Span { final String kind; final int startTick; int endTick; String label; }`. kind ∈ `"walk"`, `"run"`, `"action"`, `"idle"`; later phases add their own kinds; `endTick` is inclusive and -1 while open.
  - `final class TickRecord { final int tick; final List<XpDrop> xp; final List<Hit> hits; final List<Float> clicks; }`. Clicks are the in-tick fraction 0..1 when known, else 0.
  - `final class Timeline { Timeline(int capacity); TickRecord at(int tick) /* creates if absent and within capacity of newest, else null */; TickRecord peek(int tick) /* null if absent */; int newestTick(); void openSpan(Span s); Span openSpan(String kind); void closeSpan(String kind, int endTick); List<Span> spansBetween(int fromTick, int toTick); }`. Capacity 512. Spans older than the oldest retained tick are dropped.

- [ ] **Step 1: Write failing tests.**
  - `atCreatesRecordForTick` — `at(10).tick == 10` and `peek(10) != null`.
  - `ringOverwritesOldest` — with capacity 512, writing ticks 0..1023 makes `peek(0) == null` and `peek(1023) != null`; the record count never exceeds 512.
  - `staleWriteIgnored` — after newest = 1000, `at(100)` returns null.
  - `spansBetweenReturnsOverlapping` — spans [0,5], [8,open], [20,25] queried 4..10 → first two.
  - `spansDroppedWhenOlderThanBuffer` — a span closed at tick 0 is gone after writing tick 600.
- [ ] **Step 2:** Run `./gradlew test --tests '*TimelineTest'`. Expected: compile failure / FAIL.
- [ ] **Step 3: Implement** using an array indexed `tick % capacity`, with the tick stored on each record to detect staleness.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Commit.** `feat: tick timeline ring buffer`

### Task 3: Bar layout and resize breakpoints

**Files:**
- Create: `src/main/java/com/superhistogram/ui/BarLayout.java` (pure, no RuneLite imports, so it lives in `ui/` but is tested like model code)
- Test: `src/test/java/com/superhistogram/ui/BarLayoutTest.java`

**Interfaces:**
- Produces: `final class BarLayout { static BarLayout of(int width, int height); int width, height; float u; float tickWidth; int rows /* 3, 2 or 1 */; boolean showLeft, showRight; float timelineX, timelineW; int visibleTicks; float topY, pillY, pillH, actionsY, rulerY; }`

- [ ] **Step 1: Write failing tests** (values from the spec):
  - `defaultSize` — `of(1026,62)`: rows 3, showLeft, showRight, `u == 1f`, `tickWidth == 32f`.
  - `actionsRowHidesBelow56` — `of(1026,55).rows == 2`, `of(1026,56).rows == 3`.
  - `oneRowBelow40` — `of(1026,39).rows == 1`, `of(1026,40).rows == 2`.
  - `rightHidesBelow720` — `of(719,62).showRight == false`, `of(720,62).showRight == true`.
  - `leftHidesBelow480` — `of(479,62).showLeft == false`.
  - `tickWidthFloor` — `of(1026,22).tickWidth == 18f`.
  - `widerMeansMoreTicks` — `of(1240,62).visibleTicks > of(1026,62).visibleTicks` and tickWidth unchanged.
  - `clampsTinyAndHuge` — `of(1,1)` gives 220 × 22; `of(4000,400)` gives height 160; neither throws.
- [ ] **Step 2:** Run, expect FAIL.
- [ ] **Step 3: Implement.** Left readout width `max(0.7h, 128u)` when shown, else `0.5h`; right readout width `min(210u, 0.25w)` when shown, else `0.5h`. `visibleTicks = floor(timelineW / tickWidth)`. Row y-positions scale the 62-px mockup values by `u`: top 15, pill 22 (h 14), actions 46, ruler 53.
- [ ] **Step 4:** Run, expect PASS.
- [ ] **Step 5: Commit.** `feat: bar layout with resize breakpoints`

### Task 4: Metronome and done-blink timing

**Files:**
- Create: `src/main/java/com/superhistogram/ui/Pulse.java` (pure)
- Test: `src/test/java/com/superhistogram/ui/PulseTest.java`

**Interfaces:**
- Produces: `final class Pulse { void tick(long nowMs, boolean cue); void done(long nowMs); float outlineAlpha(long nowMs) /* 0..1 */; float mintAlpha(long nowMs) /* 0..0.55 */; }`. A done-blink suppresses that tick's outline.

- [ ] **Step 1: Write failing tests.**
  - `outlineFadesWithin500ms` — after `tick(0,false)`: alpha at 0 > 0, at 500 == 0.
  - `cueIsBrighter` — the peak after `tick(0,true)` > the peak after `tick(0,false)`; the cue lasts 550 ms.
  - `doneFlashesTwice` — `mintAlpha` is 0.55 at 0 ms, near 0 at 180 ms, 0.55 again at 320 ms, 0 at 1000 ms (matches the mockup keyframes 0 / 18% / 32% / 100% of 1 s).
  - `doneSuppressesOutline` — `done(0)` then `tick(0,false)`: `outlineAlpha(0) == 0`.
- [ ] **Step 2:** Run, expect FAIL.
- [ ] **Step 3: Implement** with linear interpolation between keyframes.
- [ ] **Step 4:** Run, expect PASS.
- [ ] **Step 5: Commit.** `feat: metronome and done-blink timing`

### Task 5: XP tracker

**Files:**
- Create: `src/main/java/com/superhistogram/model/XpTracker.java`
- Test: `src/test/java/com/superhistogram/model/XpTrackerTest.java`

**Interfaces:**
- Consumes: `Timeline`, `XpDrop` (Task 2).
- Produces: `final class XpTracker { XpTracker(Timeline t); void onStat(String skill, int totalXp, int tick); void onLoginScreen(); long sessionXp(); XpDrop lastDrop(); long xpPerHour(String skill, long sessionMs); String topSkill(); }`. Skill names are RuneLite `Skill.getName()` strings, passed in by the plugin.

- [ ] **Step 1: Write failing tests.**
  - `firstStatIsBaselineNotDrop` — `onStat("Fishing", 1_000_000, 5)` adds no drop.
  - `deltaBecomesDrop` — then `onStat("Fishing", 1_000_050, 7)` puts `XpDrop("Fishing",50)` on tick 7; `sessionXp() == 50`.
  - `hopDoesNotCreateDrops` — the same total re-reported after a hop adds no drop.
  - `loginScreenResetsBaselineNotSession` — after `onLoginScreen()`, the next stat is a baseline again and `sessionXp()` is unchanged.
  - `perHour` — 50,000 XP over 3,600,000 ms gives 50,000.
- [ ] **Step 2:** Run, expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4:** Run, expect PASS.
- [ ] **Step 5: Commit.** `feat: xp tracker with login baseline`

### Task 6: Session clock and idle tracker

**Files:**
- Create: `model/SessionClock.java`, `model/IdleTracker.java`
- Test: `model/SessionClockTest.java`, `model/IdleTrackerTest.java`

**Interfaces:**
- Produces:
  - `final class SessionClock { void onLoggedIn(long nowMs); void onHopping(); void onLoginScreen(); long onlineMs(long nowMs); String format(long nowMs) /* "2:14:07" */; }`
  - `final class IdleTracker { IdleTracker(int afkAfterSeconds); void update(int idleSeconds, long nowMs); boolean isAfk(); int secondsToLogout() /* 300 - idle, floored at 0 */; boolean warningFired() /* true once, the first update at or past 270 s idle */; }`

- [ ] **Step 1: Write failing tests.**
  - `hopKeepsLoginTime` — loggedIn(0), hopping, loggedIn(5000): `onlineMs(10000) == 10000`.
  - `loginScreenResets` — after onLoginScreen and loggedIn(20000): `onlineMs(21000) == 1000`.
  - `formatsHms` — 8,047,000 ms formats as `"2:14:07"`.
  - `afkAfterThreshold` — with threshold 60: `update(59)` is not AFK, `update(60)` is.
  - `countdown` — `update(261)` gives `secondsToLogout() == 39`.
  - `warningOnceAt270` — updates 269, 270, 271: the warning fires only at 270; going back to 0 then 270 again fires again.
- [ ] **Step 2:** Run, expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4:** Run, expect PASS.
- [ ] **Step 5: Commit.** `feat: session clock and idle tracker`

### Task 7: Movement and action spans (+ run energy cost)

**Files:**
- Create: `model/MovementTracker.java`
- Test: `model/MovementTrackerTest.java`

**Interfaces:**
- Consumes: `Timeline`, `Span`.
- Produces: `final class MovementTracker { MovementTracker(Timeline t); void onTick(int tick, int tilesMoved, boolean animating, int runEnergyPct); }`. It opens and closes spans: tilesMoved 1 → `"walk"`, ≥2 → `"run"`, animating with 0 tiles → `"action"`. A closed run span's label is `"running " + startPct + "→" + endPct + "%"`.

- [ ] **Step 1: Write failing tests.**
  - `walkSpan` — 3 ticks of 1 tile then a stop gives one walk span covering those 3 ticks.
  - `runSpanLabelsEnergyCost` — run from 71% to 64% gives the label `"running 71→64%"`.
  - `actionSpan` — animating while still gives an action span.
  - `switchWalkToRunSplitsSpans`.
- [ ] **Step 2:** Run, expect FAIL.
- [ ] **Step 3: Implement.**
- [ ] **Step 4:** Run, expect PASS.
- [ ] **Step 5: Commit.** `feat: walk/run/action spans`

### Task 8: Painters, overlay and baseline modes

**Files:**
- Create: `ui/Palette.java`, `ui/Fonts.java`, `ui/BarOverlay.java`, `ui/TimelinePainter.java`, `ui/ReadoutPainter.java`, `ui/ModeView.java`, `ui/modes/GeneralView.java`, `ui/modes/AfkView.java`, `ui/modes/MinimalView.java`, `model/ModeDetector.java`, `model/BarState.java`
- Test: `ui/BarRenderSmokeTest.java`, `model/ModeDetectorTest.java`

**Interfaces:**
- Consumes: everything from Tasks 2–7.
- Produces:
  - `final class BarState` — read-only snapshot for painters: `Timeline timeline; int nowTick; XpTracker xp; IdleTracker idle; SessionClock clock; int runEnergyPct; String modeName, modeSub; boolean showRunEnergy, showLoginTime; Mode mode;`
  - `enum Mode { GENERAL, AFK, MINIMAL }` in `model/Mode.java` (later phases add values).
  - `final class ModeDetector { Mode detect(boolean afk, BaselineSetting baseline); }` with `enum BaselineSetting { GENERAL, MINIMAL }`. AFK wins; otherwise the baseline setting.
  - `interface ModeView { void paintTimeline(Graphics2D g, BarLayout l, BarState s); void paintRight(Graphics2D g, BarLayout l, BarState s); }`
  - `BarOverlay extends Overlay`: `setResizable(true)`, `setPosition(OverlayPosition.BOTTOM_LEFT)`, preferred size 1026 × 62. `render()` builds a `BarLayout` from `getPreferredSize()`, paints the ground (rounded, radius h/2, plus the mint alpha), the left readout (name, sub line, run gauge), the mode view, the login time, and the metronome outline.

- [ ] **Step 1: Write failing tests.**
  - `ModeDetectorTest`: AFK beats both baselines; MINIMAL baseline gives MINIMAL.
  - `BarRenderSmokeTest.rendersAtDefault` — paint into a 1026 × 62 `BufferedImage`. The pixel at (5,31) is transparent (outside the rounded end) and the centre pixel is non-transparent.
  - `rendersAtExtremes` — sizes 220 × 22, 360 × 28, 4000 × 160 paint without exception.
  - `rendersWithEmptyState` — an empty timeline, null lastDrop and logged-out clock paint without exception.
  - `noActionsRowAt46` — with a click recorded, the white click mark is present in the actions band at 62 px and absent at 46 px.
- [ ] **Step 2:** Run, expect FAIL.
- [ ] **Step 3: Implement.** Match `design/mockups/always-on-baseline.html` and `resizable-bar.html`:
  - walk and run spans are faint pills (22% alpha) with labels; action spans are solid;
  - XP is coloured text on the top row;
  - hits are a yellow line + number;
  - clicks are 2 × 8 white marks;
  - the run gauge is 64u wide and 3u tall under the sub line;
  - login time is right-aligned at `w - 26u` with "ONLINE" under it;
  - right readouts: General = session XP + "this session · last: Skill +N"; AFK = mint `m:ss` + "until idle logout"; Minimal = XP/hr + "Skill · level N in Xh Ym".
- [ ] **Step 4:** Run, expect PASS.
- [ ] **Step 5: Commit.** `feat: bar overlay with general/afk/minimal views`

### Task 9: Wire the plugin to RuneLite events and config

**Files:**
- Modify: `SuperHistogramPlugin.java`, `SuperHistogramConfig.java`
- Create: `collect/ClickCollector.java`

**Interfaces:**
- Consumes: all model classes and `BarOverlay`.
- Produces: config items (keys exact):
  - `baseline` (BaselineSetting, GENERAL)
  - `afkAfterSeconds` (int, 60, range 10–290)
  - `afkWarning` (bool, true)
  - `showRunEnergy` (true)
  - `showLoginTime` (true)
  - `metronome` (enum OFF / EVERY_TICK / CUE_TICK, OFF; CUE_TICK behaves like OFF until Phase 2)
  - `blinkWhenDone` (true)
  - `rightSide` (enum PER_HOUR / SESSION, PER_HOUR)

- [ ] **Step 1: Subscribe and map events.**
  - `GameTick` → `timeline.at(client.getTickCount())`, `MovementTracker.onTick` (tiles = Chebyshev distance between last and current `getWorldLocation()`, animating = `getAnimation() != -1`, energy = `client.getEnergy() / 100`), `IdleTracker.update(min(mouseIdle, keyboardIdle) / 50)`, `Pulse.tick`.
  - `StatChanged` → `XpTracker.onStat`.
  - `HitsplatApplied` where the actor is the local player → `Hit` on the current tick.
  - `MenuOptionClicked` → a click on the current tick.
  - `GameStateChanged` → SessionClock / XpTracker hooks.
  - AFK warning firing → `Pulse.done` if `blinkWhenDone`.
- [ ] **Step 2: Build.** `./gradlew build` passes, including all earlier tests.
- [ ] **Step 3: Manual check in the dev client** (log in on a real account). Check each and note results:
  - the bar shows above the chat;
  - Alt-drag resizes it and detail drops at the breakpoints;
  - walking and running draw faint pills, and running shows the energy cost;
  - XP drops appear in skill colours;
  - getting hit draws a yellow line;
  - clicks mark the ruler;
  - after 60 s idle the AFK view counts down, and at 4:30 the bar flashes mint;
  - login time survives a world hop;
  - logging out clears without errors in the console.
- [ ] **Step 4: Commit.** `feat: wire plugin to game events and config`

**Phase 1 done:** the always-on bar, usable every day.

---

## Phase 2 — Combat (mockups: `lazy-flick.html`, `one-tick-flick.html`, `hull-flash-on-hit.html`, `multi-xp.html`)

### Task 10: Combat state model
Create `model/combat/AttackTimer.java`, `model/combat/EatCost.java` and tests.
- `AttackTimer { void onAttack(int tick, int weaponSpeed); void onEat(int tick, FoodKind k); int readyTick(); }`
- `EatCost.cost(int tick, int readyTick, FoodKind k) -> int`: add 3 (`NORMAL`), 2 (`FAST`: karambwan / halibut / gnome food), or 5 for a combo (3 + 2). Cost = `max(0, rem + add) - max(0, rem)` where `rem = readyTick - tick`. An eat 3+ ticks after the timer ran down costs 0 ("free").
- Tests:
  - `normalMidCycleCosts3`
  - `fastCosts2`
  - `comboCosts5`
  - `afterIdleIsFree` — the timer ran down 3 ticks earlier.
  - `partialWhenNearlyReady`

### Task 11: Beat and prayer timeline
Create `model/combat/Beat.java`, `collect/PrayerCollector.java`, and Span kinds `"pray-melee|ranged|magic|other"`.
- The beat starts only on the configured hotkey (`Keybind beatKey`, default F) and repeats at the equipped weapon's attack speed until 10 ticks with no attack.
- The verdict per beat is ON_TIME / LATE / EARLY: did the prayer come on on the beat tick, after it, or before it.
- Tests: `beatNeverStartsWithoutHotkey`, `lateWhenPrayerAfterBeat`, `streakCounts`. Wire metronome CUE_TICK to the beat tick here (`Pulse.tick(now, isBeat)`).

### Task 12: Lazy-flick view
`ui/modes/LazyFlickView.java`, matching `lazy-flick.html`:
- prayer pills;
- hit line with blocked hits faint;
- ▲ beat carets;
- actions row Off / Attacks / Attacks + eats (`actionsRow` config);
- eat cost labels;
- XP in the actions row by the sword;
- right side: beat trail + ✓ / Late / Early.

Render smoke tests at 3 sizes. ModeDetector gains COMBAT (local player interacting with an NPC, or hit within the last 10 ticks).

### Task 13: 1-tick flick view
`ui/modes/OneTickView.java` + `model/combat/FlickClassifier.java` (ok / split / skip / drain from per-tick prayer toggles and in-tick click times), matching `one-tick-flick.html`. Tests cover each class.

### Task 14: Hull flash on hit
`ui/HullFlashOverlay.java` (uses `ModelOutlineRenderer`) + `model/combat/HitAttribution.java`.
- On a hitsplat on the local player, attribute it to the NPC that is interacting with the player and whose attack animation started most recently, within its attack window. If two or more are equally likely, flash nothing.
- Yellow fading over 500 ms; misses faint grey or hidden.
- Config `hullFlash` (true), `hullFlashAtBosses` (false; a boss = NPC on the bundled boss-ID list), `hullFlashMisses` (HIDE / FAINT, FAINT).
- Tests: `singleAttackerAttributed`, `ambiguousAttributesNone`, `bossSkippedByDefault`.
- The attribution is used only to choose what to flash. Nothing displays an NPC's target.

---

## Phase 3 — Production + alch (mockups: `cooking.html`, `high-alch.html`, `multi-xp.html`)

### Task 15: Inventory activity tracker
`model/skill/InventoryRun.java`: counts processed items per inventory, gets action ticks from the gap between successive XP drops of the same skill, gives exact ETA = remaining × ticks-per-action × 0.6 s, and records "+N" wasted ticks between the last item and the next bank click. Tests: `etaExactForCooking`, `wastedTicksBeforeBank`.

### Task 16: Production view + detection
`ui/modes/ProductionView.java`: inventory pill filling, finish flag ahead on the ruler, countdown on the right; `Pulse.done` when the inventory finishes (if `blinkWhenDone`). Detection: the same production skill's XP drops 2+ times within 12 ticks while the inventory count of its input decreases.

### Task 17: High alch + multi-XP
- `model/skill/AlchTracker.java`: cast = High Level Alchemy spell click + Magic XP 65. Profit per cast = item HA value (`ItemComposition.getHaPrice()`) − item price − nature rune price (prices via `ItemManager.getItemPrice`). Session gold and /hr.
- `ui/modes/AlchView.java` with `alchTopRow` (GOLD / XP / BOTH).
- `ui/XpRow.java` paints multi-skill coloured numbers side by side, with frequent skills (≥ 1 drop per 2 ticks over the last 10) as dots, hidden under number groups, matching `multi-xp.html`.
- Tests: `profitNegativeShowsMinus`, `dotsHiddenUnderNumbers`.

---

## Phase 4 — Gathering + agility (mockups: `gathering.html`, `agility.html`)

### Task 18: Gathering
`model/skill/GatherRun.java` + `ui/modes/GatherView.java`:
- pill per resource (from the click on a tree/rock/spot until depletion or the target changes);
- roll notches every N ticks (4 for woodcutting);
- item + XP drops;
- depletion mark + "+N" re-click delay;
- "~" estimated time to full from the observed success rate.
Tests: `estimateUsesObservedRate`, `reclickDelayCounted`.

### Task 19: Agility
`model/skill/AgilityCourse.java` (course obstacle-ID lists for rooftop courses, lap start/end, best lap, ticks lost per obstacle, slowest obstacle) + `ui/modes/AgilityView.java`. Narrower ticks in this mode so one lap fits (tick width 14u). `Pulse.done` on each landing. The mark-of-grace drop comes from `ItemSpawned` id 11849 near the player. Tests: `lapTimeAndBest`, `ticksLostSummed`, `slowestObstacle`.

---

## Phase 5 — Finish

### Task 20: Full settings panel
Bring `SuperHistogramConfig` to match `design/mockups/settings-panel.html`: sections Bar / Feedback / Combat / XP & gold / Skilling / Colours, with the defaults there. Add `mode` (AUTO or a pinned mode) and colour overrides.

### Task 21: Icons + Plugin Hub
- Replace the rough icons with one consistent set (pot, axe, log, food, sword, flag, coin, mark of grace, bank) as small vector paths in `ui/Icons.java`.
- Check the Plugin Hub submission rules: no reflection, a bundled-font licence, and no network.
- Add `icon.png` and a README with screenshots.
