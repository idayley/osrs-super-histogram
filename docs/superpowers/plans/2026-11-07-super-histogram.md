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
  - `final class Timeline { Timeline(int capacity); TickRecord at(int tick) /* creates if absent and within capacity of newest, else null */; TickRecord peek(int tick) /* null if absent */; int newestTick(); Span openSpan(String kind, int startTick); void closeSpan(String kind, int endTick); List<Span> spansBetween(int fromTick, int toTick); }`. Capacity 512. Spans older than the oldest retained tick are dropped.

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
- Produces: `final class XpTracker { XpTracker(Timeline t); void onStat(String skill, int totalXp, int tick); void onLoginScreen(); long sessionXp(); XpDrop lastDrop(); long xpPerHour(String skill, long sessionMs); String topSkill(); long currentXp(String skill) /* -1 if unknown */; }`. Skill names are RuneLite `Skill.getName()` strings, passed in by the plugin.

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
- Create: `model/Levels.java` (OSRS XP table: `static int levelFor(long xp)`, `static long xpForLevel(int level)`; tests `level99At13034431`, `level2At83`), `ui/Palette.java`, `ui/Fonts.java`, `ui/BarOverlay.java`, `ui/TimelinePainter.java`, `ui/ReadoutPainter.java`, `ui/ModeView.java`, `ui/modes/GeneralView.java`, `ui/modes/AfkView.java`, `ui/modes/MinimalView.java`, `model/ModeDetector.java`, `model/BarState.java`
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

All paths below are under `src/main/java/com/superhistogram/` or `src/test/java/com/superhistogram/`. Every test run is `JAVA_HOME=$(brew --prefix openjdk@11) ./gradlew test --offline --tests '<names>'`; every build is `JAVA_HOME=$(brew --prefix openjdk@11) ./gradlew build --offline`. Geometry from the mockups is given at the 62-px basis and is multiplied by `u` (Task 3).

**RuneLite API used, confirmed with `javap` against client / runelite-api 1.13.6:** `events.AnimationChanged.getActor()`, `events.HitsplatApplied`, `events.ItemContainerChanged.getContainerId()/getItemContainer()`, `events.NpcDespawned.getNpc()`, `events.MenuOptionClicked.getMenuOption()/getItemId()/getWidgetId()`, `Actor.getInteracting()/getAnimation()/isDead()/getName()`, `NPC.getIndex()`, `Client.isPrayerActive(Prayer)`, `Client.getVarcStrValue(int)` with `VarClientStr.CHATBOX_TYPED_TEXT` (335), `Prayer.PROTECT_FROM_MELEE/MISSILES/MAGIC, RETRIBUTION, REDEMPTION, SMITE`, `InventoryID.EQUIPMENT/INVENTORY.getId()`, `EquipmentInventorySlot.WEAPON.getSlotIdx()`, `ItemContainer.getItem(int)/getItems()`, `Item.getId()/getQuantity()`, `gameval.InterfaceID.PRAYERBOOK` (541) / `ORBS` (160), `AnimationID.CONSUMING` (829), `ItemID` food constants (below), `client.game.ItemManager.getItemStats(int)` → `ItemStats.getEquipment()` → `ItemEquipmentStats.getAspeed()` (builder has `aspeed(int)`), `client.util.HotkeyListener(Supplier<Keybind>)` + `hotkeyPressed()`, `client.input.KeyManager.registerKeyListener/unregisterKeyListener`, `client.config.Keybind(int keyCode, int modifiers)`, `client.callback.ClientThread.invoke(Runnable)`, `client.ui.overlay.outline.ModelOutlineRenderer.drawOutline(Actor, int width, Color, int feather)`, `OverlayLayer.ABOVE_SCENE`, `OverlayPosition.DYNAMIC`.

**Expansion notes (changes from the task-level text):**
1. ▲ marks the tick you click prayer. Prayer state read on GameTick `t` reflects a click during `t − 1`, so prayer spans are stored one tick earlier than observed and a beat's verdict is decided one tick after it. This lets CUE_TICK flash bright on the ▲ tick itself (the ruling) and still mean "click now"; the mockup's "cue one tick before ▲" is the same moment under its older convention.
2. Hull flash attributes a hit only when exactly one NPC swung at you within the window. "Most recent swing" mis-attributes a slow projectile in multi-combat (melee NPC swings after a ranger fired), so 2+ candidates → flash nothing.
3. Bosses are matched by NPC name, not ID: bosses have many form IDs, names are stable.
4. An eat is recorded only when the inventory count of the clicked food drops. Menu clicks alone overcount spam-clicks and failed eats. A karambwan and a food confirmed for the same tick merge into one COMBO inside `AttackTimer`.
5. Own-attack detection ignores the eat/drink animation (829) and animations more than 1 tick before the weapon is ready (block animations). The 1-tick slack tolerates Rapid style without reading the attack-style varp; a block on the ready-minus-one tick is miscounted (accepted).
6. The beat hotkey is ignored out of combat and while the chatbox has typed text.
7. The press tick is beat 0 (drawn, no verdict). Pressing again re-phases. A weapon swap changes the interval from the last beat, never scheduling into the past.
8. "+N" after a sword is shown only for 1–3 late ticks; a longer gap is a pause (same threshold as "free" eats).
9. (Controller ruling) Combat views KEEP the login time: the operator asked for it in every mode. The beat trail / in-tick histogram must end left of the login column — use `ReadoutPainter.rightLimit` (Task 9) as their right edge, shrinking the trail to fewer beats if needed.
10. Actions row OFF hides swords and eats; beat carets and combat XP stay (they are not "your actions" toggled by that setting). At under 56 px everything in the actions row hides, as the spec says.
11. `ModeDetector.detect` gains `inCombat` and `PrayerMode`; AFK still wins over combat.
12. Pill heights follow the mockups (lazy 12u, 1-tick 10u). `one-tick-flick.html` is drawn 52 px tall, so its right-readout y values are scaled by `h / 52`.
13. The rounded key box around "F" in the sub line is left for the Phase 5 icon set; Phase 2 writes plain text "4t · F".
14. `Beat` is read by the render thread: its public methods are `synchronized` and `trail()` returns a copy.

### Task 10: Attack timer, food kinds and eat costs

**Files:**
- Create: `model/combat/FoodKind.java`, `model/combat/Foods.java`, `model/combat/EatCost.java`, `model/combat/AttackTimer.java`, `collect/EatCollector.java`
- Modify: `model/TickRecord.java`, `SuperHistogramPlugin.java`
- Test: `model/combat/EatCostTest.java`, `model/combat/FoodsTest.java`, `model/combat/AttackTimerTest.java`, `collect/EatCollectorTest.java`, `SuperHistogramPluginHandlersTest.java`

**Interfaces:**
- Consumes: `Timeline`, `TickRecord` (Task 2); the plugin's own tick counter and handler pattern (Task 9).
- Produces:
  - `enum FoodKind { NORMAL(3), FAST(2), KARAMBWAN(2), COMBO(5); final int add; static FoodKind merge(FoodKind first, FoodKind second); }`. `merge` gives COMBO for KARAMBWAN with NORMAL or FAST (either order); otherwise `first` (a second normal food in one tick is not eaten).
  - `final class Foods { static FoodKind kindOf(int itemId); }`. Bundled ints, no RuneLite import: KARAMBWAN `3144` (COOKED_KARAMBWAN); FAST `32336` (HALIBUT), crunchies `2205 2209 2213 2217` (WORM_, CHOCCHIP_, SPICY_, TOAD_CRUNCHIES), battas `2253 2255 2259 2277 2281` (WORM_, TOAD_, CHEESETOM_, FRUIT_, VEGETABLE_BATTA), bowls `2185 2187 2191 2195` (CHOCOLATE_BOMB, TANGLED_TOADS_LEGS, WORM_HOLE, VEG_BALL); everything else NORMAL. Potions never arrive here: they are "Drink", not "Eat".
  - `final class EatCost { static int cost(int tick, int readyTick, FoodKind k); }` = `max(0, rem + k.add) − max(0, rem)`, `rem = readyTick − tick`.
  - `final class AttackTimer { static final int DEFAULT_SPEED = 4, LATE_MAX = 3; static final int NEVER = Integer.MIN_VALUE / 2; boolean canAttack(int tick); int onAttack(int tick, int weaponSpeed); int onEat(int tick, FoodKind k); int readyTick(); int lastAttackTick(); }`. `readyTick` and `lastAttackTick` start at NEVER. `canAttack` = `tick >= readyTick − 1`. `onAttack` returns late ticks (`tick − readyTick` when 1..LATE_MAX, else 0) and sets `readyTick = tick + weaponSpeed`. `onEat` returns the cost and adds `k.add` to `readyTick`; a second call in the same tick undoes the first and re-applies `merge(first, k)` from the saved pre-eat ready tick.
  - `TickRecord` gains `public int attackLate = -1; public FoodKind eat; public int eatCost;` (−1 = no attack that tick).
  - `final class EatCollector { EatCollector(Timeline t, AttackTimer timer); void onEatClick(int tick, int itemId); void onInventory(int tick, Map<Integer, Integer> counts); }`. Pure (no RuneLite types). The first `onInventory` only sets the baseline. Pending clicks live 2 ticks (confirmable at click + 1 or + 2). When an item's count drops by n, the n oldest pending clicks for that ID are confirmed at their click tick: `cost = timer.onEat(clickTick, Foods.kindOf(id))`, then `timeline.at(clickTick)` gets `eat` (`FoodKind.merge(existing, kind)` when that tick already has one) and `eatCost = cost`.
  - Plugin handlers: `void handleAnimation(Actor a)`, `void handleEatClick(int itemId)`, `void handleInventory(Map<Integer, Integer> counts)`, `void handleWeapon(int itemId /* -1 = none */)`; fields `AttackTimer attack`, `volatile int weaponSpeed`.

- [ ] **Step 1: Write failing tests.**
  - `EatCostTest`: `normalMidCycleCosts3` — `cost(101, 104, NORMAL) == 3`. `fastCosts2` — FAST and KARAMBWAN at (101, 104) are 2. `comboCosts5` — COMBO at (101, 104) is 5. `afterIdleIsFree` — `cost(110, 107, NORMAL) == 0`. `partialWhenNearlyReady` — `cost(105, 104, NORMAL) == 2`.
  - `FoodsTest`: `idsMatchRuneLite` — every bundled ID equals the `net.runelite.api.ItemID` constant named above (tests may import RuneLite). `sharkIsNormal` — `kindOf(ItemID.SHARK) == NORMAL`. `karambwanAndHalibut` — 3144 → KARAMBWAN, 32336 → FAST. `mergeMakesCombo` — `merge(NORMAL, KARAMBWAN) == COMBO == merge(KARAMBWAN, FAST)`; `merge(NORMAL, NORMAL) == NORMAL`.
  - `AttackTimerTest`: `attackSetsReadyByWeaponSpeed` — `onAttack(100,4) == 0`, `readyTick() == 104`. `lateAttackReportsLostTicks` — then `onAttack(105,4) == 1`. `pauseIsNotLate` — then `onAttack(130,4) == 0`. `eatPushesReady` — after `onAttack(100,4)`, `onEat(101, NORMAL) == 3`, `readyTick() == 107`. `secondFoodSameTickBecomesCombo` — `onEat(101, NORMAL)` then `onEat(101, KARAMBWAN) == 5`, `readyTick() == 109`. `twoSharksSameTickCountOnce` — two NORMAL at 101 → `readyTick() == 107`. `eatBeforeFirstAttackIsFree` — fresh timer, `onEat(5, NORMAL) == 0`. `blockAnimationBeforeReadyRejected` — after `onAttack(100,4)`: `canAttack(102) == false`, `canAttack(103) == true`. `speedIsPerAttack` — `onAttack(100,4)`, `onAttack(104,6)` → `readyTick() == 110`.
  - `EatCollectorTest` (timer has `onAttack(100,4)`; baseline `onInventory(100, {385:4, 3144:2})`): `confirmedEatRecordsCost` — `onEatClick(101,385)`, `onInventory(102,{385:3, 3144:2})` → `peek(101).eat == NORMAL`, `eatCost == 3`. `failedEatIgnored` — click, unchanged counts → `peek(101).eat == null`, `readyTick() == 104`. `spamClicksCountOnce` — clicks at 101 and 102, one drop at 102, nothing at 103 → only tick 101 has an eat, `readyTick() == 107`. `comboRecordedOnce` — clicks 385 and 3144 at 101, both drop at 102 → `peek(101).eat == COMBO`, `eatCost == 5`. `staleClickExpires` — click at 101, drop first seen at 104 → no eat.
  - `SuperHistogramPluginHandlersTest` (extend `setUp` with a mocked `ItemManager` and an `NPC npc`): `ownAttackAnimationRecordsAttack` — `me.getInteracting()` returns `npc`, `me.getAnimation()` 390, `handleTick`, `handleAnimation(me)` → `peek(0).attackLate == 0`. `eatAnimationIsNotAttack` — animation 829 → `attackLate == -1`. `animationWithoutNpcTargetIgnored` — interacting null → `attackLate == -1`. `weaponSpeedFromItemStats` — `getItemStats(4151)` returns `new ItemStats(true, 0.5, 0, ItemEquipmentStats.builder().aspeed(6).build())` → after `handleWeapon(4151)`, `weaponSpeed == 6`. `missingStatsFallBackTo4` — null stats, and `handleWeapon(-1)`, both give 4.
- [ ] **Step 2:** Run `--tests '*EatCostTest' --tests '*FoodsTest' --tests '*AttackTimerTest' --tests '*EatCollectorTest' --tests '*PluginHandlersTest'`. Expected: compile failure / FAIL.
- [ ] **Step 3: Implement** the model classes to the signatures above. Wire the plugin:
  - `@Inject ItemManager itemManager`. `initModel()` and the LOGIN_SCREEN branch create a fresh `AttackTimer` and `EatCollector(timeline, attack)`; `weaponSpeed = AttackTimer.DEFAULT_SPEED`.
  - `@Subscribe onAnimationChanged(AnimationChanged e)` → `handleAnimation(e.getActor())`. For the local player: skip animation −1 and `EAT_ANIMATION = 829` (`AnimationID.CONSUMING`), require `getInteracting() instanceof NPC` and `attack.canAttack(tick)`, then `timeline.at(tick).attackLate = attack.onAttack(tick, weaponSpeed)`.
  - In `onMenuOptionClicked`, also call `handleEatClick(e.getItemId())` when `"Eat".equals(e.getMenuOption())`.
  - `@Subscribe onItemContainerChanged`: EQUIPMENT → `handleWeapon(id at WEAPON.getSlotIdx(), or -1)`; INVENTORY → `handleInventory(counts summed by item ID)`.
  - `weaponSpeed(int itemId)`: `itemManager.getItemStats(id)` → `getEquipment().getAspeed()` when stats, equipment and aspeed > 0 exist; else 4.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Build.** Expected: `BUILD SUCCESSFUL`, all Phase 1 tests still pass.
- [ ] **Step 6: Commit.** `feat: attack timer and eat costs`

### Task 11: Beat, prayer spans, hotkey and cue metronome

**Files:**
- Create: `model/combat/Beat.java`, `collect/PrayerCollector.java`
- Modify: `model/ModeDetector.java`, `SuperHistogramConfig.java`, `SuperHistogramPlugin.java`
- Test: `model/combat/BeatTest.java`, `collect/PrayerCollectorTest.java`, `model/ModeDetectorTest.java`, `SuperHistogramPluginHandlersTest.java`

**Interfaces:**
- Consumes: `AttackTimer.lastAttackTick()`, `weaponSpeed` (Task 10); `Pulse.tick(nowMs, cue)` (Task 4); `Timeline` spans (Task 2).
- Produces:
  - `final class Beat { enum Verdict { ON_TIME, LATE, EARLY } static final int END_AFTER_NO_ATTACK = 10, TRAIL = 10; boolean press(int tick, int weaponSpeed, boolean inCombat); void onWeaponSpeed(int tick, int weaponSpeed); void onAttack(int tick); void onTick(int tick, boolean overheadOn); void reset(); boolean active(); boolean isBeat(int tick); int nextBeat() /* -1 when inactive */; int speed(); Verdict verdict(int beatTick) /* null if none */; List<Verdict> trail() /* last ≤ 10, oldest first, a copy */; int streak() /* ON_TIME in a row ending at the latest verdict */; }`. All public methods `synchronized`; past beat ticks kept in a bounded list (64).
    - `press`: ignored (returns false) unless `inCombat`. Otherwise beat 0 at `tick` (no verdict), `nextBeat = tick + speed`; re-pressing re-phases.
    - `onTick(t, on)`, called once per GameTick: (1) end the beat (keep history) when `t − max(pressTick, lastAttack) >= 10`; (2) if `t − 1` is a beat other than beat 0, its verdict is `on && !onAt(t−1)` → ON_TIME, `on && onAt(t−1)` → EARLY, `!on` → LATE; (3) if active and `t == nextBeat`, record beat `t`, `nextBeat = t + speed`.
    - `onWeaponSpeed`: `speed = s`, `nextBeat = max(lastBeat + s, tick + 1)`.
  - `final class PrayerCollector { PrayerCollector(Timeline t); void onTick(int tick, String kind /* "melee"|"ranged"|"magic"|"other"|null */); static String overheadKind(Client c); }`. Span kind `"pray-" + kind` covers `[firstObserved − 1, lastObserved − 1]` (a click during tick t−1 shows on GameTick t). `overheadKind`: PROTECT_FROM_MELEE → melee, PROTECT_FROM_MISSILES → ranged, PROTECT_FROM_MAGIC → magic, RETRIBUTION / REDEMPTION / SMITE → other, else null.
  - `ModeDetector` gains `static boolean inCombat(boolean interactingNpc, int tick, int lastHitTick, int lastAttackTick)`: interacting, or a hit on you or your own attack within the last 10 ticks.
  - Config `beatKey` (Keybind, `new Keybind(KeyEvent.VK_F, 0)`, position 10, "Beat hotkey"). `Metronome.CUE_TICK` javadoc becomes "bright pulse on beat ticks only".
  - Plugin: `void handleBeatKey()`, fields `Beat beat`, `PrayerCollector prayers`, `int lastHitTick`.

- [ ] **Step 1: Write failing tests.**
  - `BeatTest` (unless stated: `press(100, 4, true)`, `onAttack` at 101, 105, 109, …, and `onTick` for every tick in order):
    - `beatNeverStartsWithoutHotkey` — no `press`; 50 ticks with attacks every 4 and prayer toggling → `active() == false`, `nextBeat() == -1`, no `isBeat`, empty `trail()`.
    - `hotkeyIgnoredOutOfCombat` — `press(100, 4, false) == false`, `active() == false`.
    - `pressTickIsBeatWithoutVerdict` — `isBeat(100)`, `nextBeat() == 104`, `verdict(100) == null` after `onTick(101, true)`.
    - `beatsRepeatAtWeaponSpeed` — ticks to 112 → `isBeat` 104, 108, 112; `!isBeat(102)`.
    - `onTimeWhenPrayerClickedOnBeat` — off at 104, on at 105 → `verdict(104) == ON_TIME`.
    - `lateWhenPrayerAfterBeat` — off at 104 and 105, on at 106 → LATE.
    - `earlyWhenPrayerAlreadyOn` — on at 104 and 105 → EARLY.
    - `streakCounts` — verdicts ON_TIME, ON_TIME, LATE, ON_TIME, ON_TIME, ON_TIME → `streak() == 3`, `trail().size() == 6`, `trail().get(2) == LATE`; after 12 beats `trail().size() == 10`.
    - `endsAfter10TicksWithoutAttack` — no attacks: active after `onTick(109)`, inactive after `onTick(110)`; `isBeat(104)` and `isBeat(108)` stay true, `nextBeat() == -1`.
    - `attacksKeepItAlive` — only one attack, at 105 → active after `onTick(114)`, inactive after `onTick(115)`.
    - `weaponSwapAppliesFromLastBeat` — `onWeaponSpeed(102, 5)` → `nextBeat() == 105`, then a beat at 110.
    - `swapToFasterNeverSchedulesInPast` — `press(100, 6, true)`, ticks to 104, `onWeaponSpeed(104, 2)` → `nextBeat() == 105`.
    - `pressAgainRephases` — `press(102,4,true)` → `isBeat(102)`, `nextBeat() == 106`, `isBeat(100)` still true.
    - `resetClearsBeat` — `reset()` → inactive, empty trail, `nextBeat() == -1`, `isBeat(100) == false`.
  - `PrayerCollectorTest`: `spanStartsTickBeforeObservation` — `onTick(10,null)`, `onTick(11,"melee")` → one open span `"pray-melee"` with `startTick == 10`. `offClosesSpan` — on at 11 and 12, null at 13 → `endTick == 11`. `switchSplitsSpan` — melee at 11–12, magic at 13 → melee `[10,11]`, magic open from 12. `oneTickFlickIsOneTickSpan` — on only at 11 → `[10,10]`.
  - `ModeDetectorTest.combatLingers10TicksAfterHit` — `inCombat(false, 110, 100, NEVER)` true, `(false, 111, 100, NEVER)` false, `(true, 500, NEVER, NEVER)` true (`NEVER` = `AttackTimer.NEVER`).
  - `SuperHistogramPluginHandlersTest` (`me.getInteracting()` returns `npc`; `client.getVarcStrValue(VarClientStr.CHATBOX_TYPED_TEXT)` returns `""`):
    - `cueTickPulsesOnlyOnBeat` — metronome CUE_TICK; `handleTick(0)`, `handleBeatKey()`, then `handleTick` at 600, 1200, 1800, 2400 ms → `pulse.outlineAlpha(1800) == 0`, `pulse.outlineAlpha(2400) > 0.9f` (tick 4 is the first beat after the press).
    - `everyTickMetronomeBrightOnBeat` — EVERY_TICK, same sequence → `outlineAlpha(1800)` in (0, 0.5), `outlineAlpha(2400) > 0.9f`.
    - `hotkeyIgnoredWhileTypingInChat` — typed text `"f"` → after `handleBeatKey()`, `beat.active() == false`.
    - `hotkeyIgnoredOutOfCombatInPlugin` — interacting null, no hits → beat inactive.
    - `logoutMidBeatClearsBeat` — active beat, `handleGameState(LOGIN_SCREEN)` → `beat.active() == false`, `buildState() == null`; after `LOGGED_IN` and 10 ticks, no `isBeat` on any new tick.
    - `prayerObservedEachTick` — `client.isPrayerActive(PROTECT_FROM_MAGIC)` true from the second tick → a `"pray-magic"` span starting at tick 0.
- [ ] **Step 2:** Run `--tests '*BeatTest' --tests '*PrayerCollectorTest' --tests '*ModeDetectorTest' --tests '*PluginHandlersTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - `Beat`, `PrayerCollector`, `ModeDetector.inCombat` to the contracts above.
  - Plugin: `@Inject KeyManager keyManager; @Inject ClientThread clientThread;` a `HotkeyListener(() -> config.beatKey())` whose `hotkeyPressed()` calls `clientThread.invoke(this::handleBeatKey)`; register in `startUp`, unregister in `shutDown`.
  - `handleBeatKey`: return if the typed chat text is non-empty; `beat.press(tick, weaponSpeed, ModeDetector.inCombat(me.getInteracting() instanceof NPC, tick, lastHitTick, attack.lastAttackTick()))`.
  - `handleTick` order after `timeline.at(tick)`: `String k = PrayerCollector.overheadKind(client)`, `prayers.onTick(tick, k)`, `beat.onTick(tick, k != null)`, the Phase 1 movement / idle work, then the metronome: `EVERY_TICK → pulse.tick(now, beat.isBeat(tick))`; `CUE_TICK → if (beat.isBeat(tick)) pulse.tick(now, true)`. Remove the "CUE_TICK behaves like OFF" comment.
  - `handleAnimation` also calls `beat.onAttack(tick)` on an own attack. `handleWeapon` calls `beat.onWeaponSpeed(tick, weaponSpeed)` when the speed changes. `handleHit` sets `lastHitTick`.
  - `initModel()` creates `Beat` and `PrayerCollector(timeline)` and sets `lastHitTick = AttackTimer.NEVER`. LOGIN_SCREEN: `beat.reset()`, fresh `PrayerCollector(timeline)`, `lastHitTick = AttackTimer.NEVER`. HOPPING keeps the beat (the 10-tick rule ends it).
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Build.** Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 6: Commit.** `feat: hotkey beat, prayer spans and cue metronome`

### Task 12: Lazy-flick view

**Files:**
- Create: `model/combat/PrayerMode.java`, `model/combat/ActionsRow.java`, `ui/CombatPainter.java`, `ui/modes/LazyFlickView.java`
- Modify: `model/Mode.java`, `model/ModeDetector.java`, `model/BarState.java`, `ui/ModeView.java`, `ui/BarPainter.java`, `ui/Palette.java`, `SuperHistogramConfig.java`, `SuperHistogramPlugin.java`
- Test: `model/ModeDetectorTest.java`, `ui/BarRenderSmokeTest.java`, `ui/SampleStates.java`

**Interfaces:**
- Consumes: `Beat` (Task 11), `TickRecord.attackLate/eat/eatCost` (Task 10), `"pray-*"` spans (Task 11), `TimelinePainter.x/nowX/pill/hits` (Task 8).
- Produces:
  - `enum PrayerMode { LAZY_FLICK, ONE_TICK }`, `enum ActionsRow { OFF, ATTACKS, ATTACKS_EATS }`; `Mode` gains `LAZY_FLICK`.
  - `Mode detect(boolean afk, boolean inCombat, PrayerMode prayerMode, BaselineSetting baseline)`: AFK, else combat → LAZY_FLICK (ONE_TICK also maps here until Task 13), else the baseline. The old two-argument calls in `ModeDetectorTest` become `detect(afk, false, LAZY_FLICK, baseline)`.
  - `BarState` + builder gain `Beat beat` (null), `int weaponSpeed` (4), `ActionsRow actionsRow` (ATTACKS), `boolean showEatCosts` (true).
  - `final class CombatPainter` (static, `ui/`): `sword(g, x, y, u)`, `fish(g, cx, cy, u)`, `caret(g, x, y, u, Color)`, `prayerColor(String spanKind)`, `prayerPills(g, l, s, float h)`, `xpLow(g, l, s)`, `float rightX(BarLayout l)` (= `timelineX + timelineW`).
  - `Palette` adds `LOST = #c9ced4` (the mockup's `.lost`: "+N" labels and food icons), `TRAIL_LINE = white 12%`, `NEXT_BEAT = white 35%`.
  - Config: `prayerMode` (PrayerMode, LAZY_FLICK, position 11, "Prayer mode"), `actionsRow` (ActionsRow, ATTACKS, 12, "Actions row"), `showEatCosts` (true, 13, "Show eat costs").
  - `LazyFlickView implements ModeView`; its right readout (trail + verdict) stays left of `ReadoutPainter.rightLimit`. Smoke test `lazyFlickTrailDoesNotOverlapLoginTime` at 1026×62 and 720×62.

- [ ] **Step 1: Write failing tests.**
  - `ModeDetectorTest`: `combatBeatsBaseline` — `detect(false, true, LAZY_FLICK, MINIMAL) == LAZY_FLICK`. `afkBeatsCombat` — `detect(true, true, LAZY_FLICK, GENERAL) == AFK`. `noCombatGivesBaseline` — `detect(false, false, LAZY_FLICK, MINIMAL) == MINIMAL`.
  - `SampleStates.lazyFlick()` mirrors the lazy-flick.html zoom: press at `NOW_TICK − 20`, speed 4, verdicts ok, ok, late, ok, ok, early; attacks every 4 ticks, one with `attackLate == 1`; eats NORMAL (+3), COMBO (+5) and one free (0); `pray-melee` and `pray-magic` spans; one landed hit and one blocked hit; Strength + Hitpoints XP on the tick after each attack; no hit or XP in the last 2 ticks. `SampleStates.lazyFlick(ActionsRow, boolean showEatCosts, Beat.Verdict last)` varies those.
  - `BarRenderSmokeTest`:
    - `lazyFlickRendersAtThreeSizes` — 1026 × 62, 1026 × 46, 600 × 30, no exception. (`rendersAtExtremes` and `rendersWithEmptyState` already loop `Mode.values()`, so null `beat` at every size is covered.)
    - `lazyFlickWithIdleBeatIsSafe` — an inactive `Beat` and each `ActionsRow` value paint without exception.
    - `caretUnderBeatTick` — opaque white within x(b)±4u, y 55u–60u at 62 px; none at 46 px.
    - `swordsHiddenWhenActionsRowOff` — white sword pixels at an attack tick with no hit (x(t)±3u, y 38u–51u) with ATTACKS, none with OFF.
    - `eatCostsNeedAttacksAndEats` — exact `LOST` or `MINT` label pixels at an eat with no XP on the next tick (x(t)+tw/2+9u .. +24u, y 41u–49u) only with ATTACKS_EATS and `showEatCosts`.
    - `trailVerdictPaints` — last LATE: white pixels in (rightX+140u .. +190u, 22u–40u); last ON_TIME: mint pixels there.
    - `nothingAheadOfNowExceptNextBeat` (rules boundary) — for x in (`nowX + 2`, `rightX`), every pixel other than the ruler marks equals the ground, except inside the faint caret box at `nextBeat`.
- [ ] **Step 2:** Run `--tests '*ModeDetectorTest' --tests '*BarRenderSmokeTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** to lazy-flick.html (62-px values × u):
  - Prayer pills: `pray-*` spans only, solid, `prayerColor` (melee `PRAYER_MELEE`, ranged `PRAYER_RANGED`, magic `PRAYER_MAGIC`, other `PRAYER_OTHER`), y `pillY(l)`, height 12u at 3 rows, `pillH(l)` otherwise, from `x(start)` to `x(end + 1)`.
  - Hits: `TimelinePainter.hits` unchanged (yellow line + icon + number; blocked faint grey).
  - Actions row (only `rows == 3`):
    - Beat carets: apex `(x(b), 55u)`, base `(x(b) ± 4u, 60u)`, white for recorded beats, `NEXT_BEAT` for `nextBeat` (the only mark ahead of now).
    - Swords (ATTACKS, ATTACKS_EATS): white path `M x−1 y−6.5 h2 v7 h2.6 v1.6 h−2.6 v2.6 h−2 v−2.6 h−2.6 v−1.6 h2.6 z` at `(x(t), 44u)`. `"+N"` when `attackLate > 0`: bold 9u, `LOST`, at `(x+6u, 48u)`.
    - Eats (ATTACKS_EATS): fish at `ex = x(t) + tw/2`, y 44u: ellipse centre `ex−1u`, rx 4.5u, ry 2.8u, plus tail triangle `(ex+2.5u, y) (ex+6.5u, y−3.2u) (ex+6.5u, y+3.2u)`, `LOST`; COMBO = two fish at `(ex−1u, 41u)` and `(ex+1u, 47u)`. When `showEatCosts`: label at `(ex+9u, 47.5u)`, bold 9u, `"+N"` in `LOST`, or `"free"` in `MINT` when the cost is 0.
    - Combat XP (`xpLow`): `"+N"` per drop, bold 10u, skill colour, baseline 48u, starting `x(t) − 4u`, moved to `x(t) + 8u` when a sword is on the same tick; drops side by side +6u apart. The top row stays hits only.
  - Right (when `showRight`), `tx = rightX + 16u`:
    - a 1-px `TRAIL_LINE` from tx to tx+110u at y 32u;
    - trail entry i at `tx + 2u + 11u·i`: ON_TIME → 7u × 4u at y 30u, radius 2u, `MINT` at 80%; LATE → white 7u × 16u at y 16u; EARLY → white 7u × 16u at y 32u;
    - verdict at `vx = tx + 124u`: last LATE / EARLY → `"Late"` / `"Early"`, bold 22u, white, baseline 39u; else a mint check `M vx+4 29 l6 6 l12 −12`, stroke 3u, round caps;
    - nothing when `beat` is null or has no verdicts.
  - Plugin `buildState`: `inCombat` from `ModeDetector.inCombat`, mode from `detect(...)`; LAZY_FLICK → name `"Lazy flick"`, sub `weaponSpeed + "t · " + config.beatKey()`; set `beat`, `weaponSpeed`, `actionsRow`, `showEatCosts`. Register `LazyFlickView` in `BarPainter`.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Build.** Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 6: Manual check in the dev client** (fight any 4-tick NPC with a 4-tick weapon). Note results:
  - attacking switches the bar to "Lazy flick · 4t · F", and it falls back 10 ticks after combat ends;
  - no ▲ appears until F is pressed;
  - F starts ▲ every 4 ticks; with metronome "Cue tick" the bright outline lands on each ▲;
  - clicking protect on a ▲ tick gives ✓, a tick later gives "Late", holding it gives "Early";
  - eating a shark mid-cycle shows "+3", a karambwan combo "+5", eating while idle "free";
  - XP sits by the sword, hits stay on top, and the login time still shows at the far right without overlap.
- [ ] **Step 7: Commit.** `feat: lazy-flick combat view`

### Task 13: 1-tick flick view

**Files:**
- Create: `model/combat/FlickClassifier.java`, `ui/modes/OneTickView.java`
- Modify: `model/TickRecord.java`, `collect/ClickCollector.java`, `model/Mode.java`, `model/ModeDetector.java`, `model/BarState.java`, `ui/BarPainter.java`, `SuperHistogramPlugin.java`
- Test: `model/combat/FlickClassifierTest.java`, `collect/ClickCollectorTest.java`, `model/ModeDetectorTest.java`, `ui/BarRenderSmokeTest.java`, `ui/SampleStates.java`

**Interfaces:**
- Consumes: `PrayerCollector.overheadKind` and `pray-*` spans (Task 11), `CombatPainter` (Task 12), `ClickCollector` (Task 9).
- Produces:
  - `final class FlickClassifier { enum Kind { OK, SPLIT, SKIP, DRAIN, NONE } static final int HIST_BINS = 12, HIST_TICKS = 40, SPLIT_BIN = 10; Kind classify(List<Float> prayerClicks, boolean onAfter); float phase(); int streak(); Kind last(); void reset(); static int[] histogram(Timeline t, int fromTick, int toTick); }`. `classify` is called once per tick, oldest first. Algorithm (not fully pinned by tests): if the previous tick left a dangling click, drop this tick's first click (it completes that pair). Then for the remaining clicks: an even count ≥ 2 → OK, phase = first click; an odd count → SPLIT, phase = last click, set dangling. With 0 remaining: NONE if a dangling click was just consumed; else DRAIN if `onAfter`; else SKIP if the previous kind was OK or SPLIT; else NONE. `histogram` counts OK and SPLIT phases into `min(11, floor(phase · 12))`.
  - `TickRecord` gains `public final List<Float> prayerClicks = new ArrayList<>(); public FlickClassifier.Kind flick; public float flickPhase;`.
  - `ClickCollector.onPrayerClick(int tick, long nowMs)`: same in-tick fraction as `onClick`, into `prayerClicks`.
  - `Mode` gains `ONE_TICK`; `detect` returns ONE_TICK for `PrayerMode.ONE_TICK` in combat. `BarState` gains `FlickClassifier flicks`.
  - `OneTickView implements ModeView`; the in-tick histogram + streak stay left of `ReadoutPainter.rightLimit`. Smoke test `oneTickHistogramDoesNotOverlapLoginTime` at 1026×62 and 720×62.

- [ ] **Step 1: Write failing tests.**
  - `FlickClassifierTest`: `twoClicksSameTickIsOk` — `classify([.3f,.35f], true) == OK`, `phase() == .3f`. `oddClickIsSplitAndCarries` — `classify([.92f], false) == SPLIT`, phase .92; then `classify([.05f,.3f,.35f], true) == OK`, phase .3. `carryOnlyTickIsNone` — SPLIT then `classify([.05f], true) == NONE`. `noClicksPrayerOnIsDrain` — `classify([], true) == DRAIN`. `noClicksAfterFlickIsSkip` — OK, then `classify([], false) == SKIP`, then NONE. `idleIsNone` — fresh `classify([], false) == NONE`. `streakCountsCleanFlicks` — OK, OK, SPLIT, OK, OK → `streak() == 2`. `histogramBinsPhases` — ticks 10–12 with OK .30, OK .32, SPLIT .95 and an OK at tick 5 → `histogram(t, 10, 12)`: `[3] == 2`, `[11] == 1`, total 3.
  - `ClickCollectorTest.prayerClickRecordedSeparately` — `onTick(0)`, `onPrayerClick(5, 300)` → `peek(5).prayerClicks` is `[0.5f]`, `clicks` is empty.
  - `ModeDetectorTest.oneTickModeWhenConfigured` — `detect(false, true, ONE_TICK, GENERAL) == ONE_TICK`.
  - `SampleStates.oneTick(FlickClassifier.Kind last)` mirrors one-tick-flick.html: mostly OK at phases .2–.45, two SPLIT at ≥ .9, one SKIP, a 4-tick DRAIN, a `pray-magic` span, hits.
  - `BarRenderSmokeTest`: `oneTickRendersAtThreeSizes` (1026 × 62, 1026 × 46, 600 × 30). `oneTickVerdictPaints` — last OK: mint pixels at x rightX + 128u .. +160u, y (17..36)·h/52; last SPLIT: white pixels there. `splitBinsAreWhite` — histogram bar pixels at x ≥ hx + 80u are white, those left of it mint.
- [ ] **Step 2:** Run `--tests '*FlickClassifierTest' --tests '*ClickCollectorTest' --tests '*ModeDetectorTest' --tests '*BarRenderSmokeTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - Plugin: in `onMenuOptionClicked`, when `(e.getWidgetId() >>> 16)` is `InterfaceID.PRAYERBOOK` (541) or `InterfaceID.ORBS` (160) and the option is `"Activate"` or `"Deactivate"`, also call `clicks.onPrayerClick(tick, now)`. In `handleTick`, after `prayers.onTick(tick, k)`: `r = timeline.peek(tick − 1)`; if non-null, `r.flick = flicks.classify(r.prayerClicks, k != null)` and `r.flickPhase = flicks.phase()`. LOGIN_SCREEN → `flicks.reset()`. ONE_TICK → name `"1-tick flick"`, sub `"every tick"`.
  - `OneTickView` to one-tick-flick.html. Timeline (62-px basis × u), middle row at `pillY(l)`, height 10u:
    - OK = filled 10u × 10u pill centred on `cx = x(t) + phase·tw`, in the colour of the `pray-*` span covering t (`PRAYER_OTHER` if none);
    - SPLIT = hollow white 14u × 8.5u, stroke 1.5u, from `cx − 5u`;
    - SKIP = white ×, two 6u strokes 1.6u, round caps, at the slot centre;
    - DRAIN = one pill per run of consecutive DRAIN ticks, `x(first)` to `x(last + 1)`;
    - then `TimelinePainter.hits`, `CombatPainter.xpLow`, `TimelinePainter.clicks`. No carets.
  - Right (y values × h/52, x × u), `hx = rightX + 16u`, `hw = 96u`:
    - box `(hx, 9, hw, 26)`, radius 4u, white 4%; split zone (the last 2/12 of the box) white 7%;
    - bar i at `hx + 8u·i + 1u`, 6u wide, height `22·n/max`, bottom 33, radius 1.5u; mint for i < 10, white for 10–11;
    - `"tick start"` left at y 46 and `"next tick"` right-aligned at `hx + hw`, bold 10u, `DIM`;
    - at `hx + hw + 16u`: when `last()` is SPLIT / SKIP / DRAIN → `"Split"` / `"Skipped"` / `"Draining"`, bold 22u, white, y 36; else `streak()` in mint bold 22u at y 33 with `"clean in a row"` (bold 10u, `DIM`) at y 46.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Build.** Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 6: Manual check** (set Prayer mode to 1-tick, flick protect on a weak NPC). Note results: clean double-clicks draw dots where you clicked; a late second click draws a hollow; stopping draws one ×; leaving it on draws a long pill and "Draining"; the histogram fills.
- [ ] **Step 7: Commit.** `feat: 1-tick flick view`

### Task 14: Hull flash on hit

**Files:**
- Create: `model/combat/HitAttribution.java`, `model/combat/Bosses.java`, `model/combat/FlashRule.java`, `ui/HullFlashOverlay.java`
- Modify: `SuperHistogramConfig.java`, `SuperHistogramPlugin.java`
- Test: `model/combat/HitAttributionTest.java`, `model/combat/BossesTest.java`, `model/combat/FlashRuleTest.java`, `ui/HullFlashOverlayTest.java`, `SuperHistogramPluginHandlersTest.java`

**Interfaces:**
- Consumes: `handleAnimation` / `handleHit` (Tasks 9–10), `Palette.DAMAGE`, `Palette.BLOCKED`.
- Produces:
  - `final class HitAttribution { static final int WINDOW_TICKS = 5; void onSwing(int npcIndex, int tick); void forget(int npcIndex); int attribute(int hitTick) /* index, or -1 when none or 2+ */; void reset(); }`. Keeps each NPC's latest swing tick. A candidate is an NPC whose latest swing is in `[hitTick − 5, hitTick]`.
  - `final class Bosses { static boolean isBoss(String npcName); }`: a case-insensitive set bundled from the OSRS Wiki "Boss" list.
  - `final class FlashRule { enum Flash { NONE, HIT, MISS } static Flash decide(boolean hullFlash, boolean atBosses, boolean isBoss, boolean miss, boolean missesFaint); }`.
  - `HullFlashOverlay extends Overlay { static final long FADE_MS = 500; static final int WIDTH = 4, FEATHER = 4; static final class Target { final NPC npc; final long startMs; final boolean miss; } HullFlashOverlay(ModelOutlineRenderer r, Supplier<Target> target); static float alpha(long ageMs); }`. Layer ABOVE_SCENE, position DYNAMIC. `alpha` falls 1 → 0 linearly over 500 ms. The colour is `DAMAGE`, or `BLOCKED` for a miss, at that alpha.
  - Config: `hullFlash` (true, position 14, "Flash enemy on hit"), `hullFlashAtBosses` (false, 15, "Also at bosses"), `hullFlashMisses` (`enum HullFlashMisses { HIDE, FAINT }`, FAINT, 16, "Misses").
  - Plugin: `HullFlashOverlay.Target flashTarget()` (package-private for tests), `handleHit(Actor, int type, int amount, long nowMs)` (the 3-argument form delegates with the current time), `void handleNpcDespawned(NPC npc)`.

- [ ] **Step 1: Write failing tests.**
  - `HitAttributionTest`: `singleAttackerAttributed` — `onSwing(7,100)` → `attribute(100) == 7`, `attribute(105) == 7`, `attribute(106) == -1`. `ambiguousAttributesNone` — `onSwing(7,100)`, `onSwing(9,100)` → `-1`. `twoAttackersInWindowAttributesNone` — swings at 98 and 100, hit at 100 → `-1`. `oldSwingDoesNotCount` — 7 at 90, 9 at 100 → `attribute(100) == 9`. `forgetRemovesNpc` — `forget(7)` → `-1`. `resetClearsAll`.
  - `BossesTest.knownBossesMatch` — `"Zulrah"`, `"vorkath"`, `"General Graardor"` true; `"Goblin"` and `null` false.
  - `FlashRuleTest`: `bossSkippedByDefault` — `decide(true, false, true, false, true) == NONE`. `bossFlashesWhenAllowed` — `decide(true, true, true, false, true) == HIT`. `missFaintOrHidden` — miss with FAINT → MISS, with HIDE → NONE. `offFlashesNothing` — `hullFlash` false → NONE.
  - `HullFlashOverlayTest.alphaFadesOver500ms` — `alpha(0) == 1`, `alpha(250) ≈ 0.5`, `alpha(500) == 0`, `alpha(-1) == 0`.
  - `SuperHistogramPluginHandlersTest` (config `hullFlash` true, `hullFlashAtBosses` false, misses FAINT; `npc.getIndex()` 7, `npc.getInteracting()` = `me`, `npc.getAnimation()` 422, `npc.getName()` "Goblin"):
    - `hitFlashesSingleAttacker` — `handleTick`, `handleAnimation(npc)`, `handleHit(me, DAMAGE_ME, 5, 1000)` → `flashTarget().npc == npc`, `miss == false`, `startMs == 1000`.
    - `missFlashesFaint` — `BLOCK_ME, 0` → `flashTarget().miss == true`.
    - `twoAttackersSameTickFlashNothing` — a second NPC (index 9) also swings → `flashTarget() == null`.
    - `swingAtSomeoneElseIgnored` — `npc.getInteracting()` returns another player → no flash.
    - `bossNotFlashedByDefault` — name "Vorkath" → no flash.
    - `despawnAndLogoutClearAttribution` — `handleNpcDespawned(npc)`, then a hit → no flash; same after `LOGIN_SCREEN`.
- [ ] **Step 2:** Run `--tests '*HitAttributionTest' --tests '*BossesTest' --tests '*FlashRuleTest' --tests '*HullFlashOverlayTest' --tests '*PluginHandlersTest'`. Expected: FAIL.
- [ ] **Step 3: Implement.**
  - `handleAnimation`, for an `NPC` with animation ≠ −1, `!isDead()` and `getInteracting() == client.getLocalPlayer()`: `attribution.onSwing(npc.getIndex(), tick)` and remember `swingers.put(index, npc)`.
  - `handleHit`, after recording the hit and only on the local player: `idx = attribution.attribute(tick)`; `npc = swingers.get(idx)`; `FlashRule.decide(config.hullFlash(), config.hullFlashAtBosses(), Bosses.isBoss(npc.getName()), blocked, misses == FAINT)`; HIT or MISS → `flash = new Target(npc, nowMs, miss)`.
  - `@Subscribe onNpcDespawned` → `handleNpcDespawned` (forget, remove from `swingers`, clear `flash` if it is that NPC). HOPPING and LOGIN_SCREEN → `attribution.reset()`, `swingers.clear()`, `flash = null`.
  - `@Inject ModelOutlineRenderer`; add `HullFlashOverlay(renderer, this::flashTarget)` in `startUp`, remove in `shutDown`.
  - `render`: null when there is no target or `alpha(now − startMs) == 0`; otherwise `drawOutline(npc, WIDTH, colour, FEATHER)`.
  - Rules boundary: the attribution only picks which hull to flash after a hitsplat has landed. Nothing draws an NPC's target, and nothing flashes on a swing.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Build.** Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 6: Manual check.** Note results:
  - one goblin hitting you: its hull flashes yellow on each hitsplat and fades in about 0.5 s; a 0 flashes grey;
  - two NPCs hitting you on the same tick: no flash;
  - nothing flashes on a swing that has not landed;
  - at a boss there is no flash until "Also at bosses" is on.
- [ ] **Step 7: Commit.** `feat: hull flash on hit`

**Phase 2 done:** combat views with the F beat, eat costs, 1-tick flick feedback and hull flash.

---

## Phase 3 — Production + alch (mockups: `cooking.html`, `high-alch.html`, `multi-xp.html`, `settings-panel.html`)

Paths, test and build commands, and the 62-px × `u` convention are as in Phase 2. "Opaque white" in pixel tests means alpha ≥ 200 and every channel ≥ 240 (this excludes the 14 % ruler marks). Colour matches allow ±8 per channel.

**RuneLite API used, confirmed with `javap` against client / runelite-api 1.13.6:** `client.game.ItemManager.getItemPrice(int)` (returns `long`), `ItemManager.getItemComposition(int)`, `ItemManager.canonicalize(int)` (noted / placeholder → base item), `ItemComposition.getHaPrice()/isTradeable()`, `gameval.ItemID.NATURERUNE` (561), `events.MenuOptionClicked.getMenuAction()/getMenuOption()/getMenuTarget()/getItemId()`, `MenuAction.WIDGET_TARGET_ON_WIDGET`, `client.util.Text.removeTags(String)`, `events.StatChanged.getSkill()/getXp()`, `Skill.COOKING/FLETCHING/HERBLORE/CRAFTING/SMITHING/PRAYER/MAGIC.getName()`, `events.ItemContainerChanged` + `InventoryID.INVENTORY` (already used in Phase 2). Runtime behaviour that `javap` cannot show is checked in the manual steps: that `getItemId()` is the target item on a spell-on-item click, and that the target text reads `High Level Alchemy -> <item>`.

**Expansion notes (changes from the task-level text):**
1. Task 17 is split: **17a** multi-XP row (XP grammar for every mode) and **17b** high alch. 17b depends on 17a; either can be rejected alone.
2. An inventory "action" is a decrease of the run's input item, not an XP drop. A burnt fish uses an input but gives no XP, so it counts toward "n of 28" and is drawn as grey "burnt". XP is the detection signal: an action is matched when a drop of the run's skill lands on the same tick, or on the next tick if that tick has no action of its own.
3. The input is the limiting reagent: of the items that dropped on the first matched action, the one with the fewest actions left (`count / decrease`). Smithing's 5 bars per platebody works the same way.
4. Ticks per action = the smallest of the last 3 gaps between actions (gaps of 0 are ignored). A faster rate takes effect at once. A pause (level-up dialog) does not inflate the ETA. A slower rate takes effect after 3 gaps.
5. A bank click ends the run. If items were left, the run is abandoned: no done blink, no "+N", and the mode drops back at once. An increase in the input count (withdrawing items) also ends the run. A production action of another skill ends the run and starts a new candidate.
6. Detection: production shows after 2 matched actions of one skill no more than 12 ticks apart. It lingers 20 ticks after the last action or bank click. Alch shows after 2 confirmed casts no more than 15 ticks apart (5 + `LOST_MAX` 10) and lingers 10 ticks. Priority: AFK > combat > alch > production > baseline. Alch beats production so alch + fletch shows the alch view with Fletching dots, as `multi-xp.html` does.
7. Multi-XP is not a Mode. `XpRow` is the XP grammar wherever XP is drawn. `TimelinePainter.xp` (General, Minimal, AFK, Production, Alch: top row) and `CombatPainter.xpLow` (lazy flick, 1-tick: actions row) both delegate to it, with the same offsets as today. The cooking mockup's grey, centred "+210" and the alch mockup's "+65 xp" are replaced by this grammar: bold 10u, skill colour, at `x(t) − 4u`.
8. Config, mirroring `settings-panel.html`: `xpDrops` (NUMBERS / DOTS_ONLY / OFF, NUMBERS), `xpDots` ("Frequent skills as dots", true), `alchTopRow` (GOLD / XP / BOTH, GOLD), `rightSide` (PER_HOUR / SESSION, PER_HOUR, "Right side shows"), `timeToFinish` (true), `wastedTicks` ("Wasted ticks (+N)", true). Positions are 20–25; Phase 5 adds the sections. `rightSide` is used only by the alch view. General and Minimal keep their Phase 1 readouts. The mockup's per-skill "Str 92k/hr" right column is not adopted, because each mode keeps one headline.
9. Every right readout goes through `ReadoutPainter.right` (bold 20u headline, clamped at `rightLimit`), so the login time stays at the far right in every mode. This differs from the mockups' 22u headline. A new `coin` overload draws the coin icon before a gold headline.
10. Profit = HA value − GE price of the item − GE price of a nature rune, from `ItemManager` (its cached price data; there are no other network calls). If any price is ≤ 0, or the item ID or composition is missing, the profit is unknown (`null`). An unknown cast is drawn as a coin with a dim "?", is left out of the gold totals, and adds " · N unpriced" to the small line. Fire runes are not counted, as the spec says.
11. The finish flag and the next-cast ▲ are the player's own rhythm, which the rules boundary allows. With the metronome on (Every tick or Cue tick), the outline pulses bright on the finish tick and on the cast-ready tick, as the mockups say. When the inventory finishes, `Pulse.done` fires once, if `blinkWhenDone` is on.
12. The run's pills are kept in a bounded list inside `InventoryRun` (the last 8), not in the timeline's spans. If they were timeline spans, `TimelinePainter.spans` would paint them a second time in General. The pot icon is drawn for Cooking only. The other production skills get their icons in Phase 5.
13. `InventoryRun` and `AlchTracker` are read by the render thread: their public methods are `synchronized`, and their lists are returned as copies (as `Beat` does).

### Task 15: Inventory run tracker

**Files:**
- Create: `model/skill/InventoryRun.java`
- Modify: `model/TickRecord.java`
- Test: `model/skill/InventoryRunTest.java`

**Interfaces:**
- Consumes: `Timeline`, `TickRecord`, `XpDrop`, `Span` (Task 2).
- Produces:
  - `TickRecord` gains `public boolean failedAction; public boolean bankClick; public int wasted;`.
  - `final class InventoryRun { static final Set<String> SKILLS /* "Cooking","Fletching","Herblore","Crafting","Smithing","Prayer" */; static final int DETECT_WINDOW = 12, LINGER = 20, HISTORY = 8; InventoryRun(Timeline t); boolean onTick(int tick, List<XpDrop> drops, Map<Integer,Integer> inventory /* full counts, null = unchanged */); void onBankClick(int tick); boolean showing(int tick); boolean running(); boolean finished(); String skill(); int done(); int total(); int remaining(); int ticksPerAction() /* 0 unknown */; int finishTick() /* -1 unknown */; int bankTick() /* -1 none */; int wasted(); List<Span> runs() /* oldest first, copy; kind = skill, endTick -1 while running */; void reset(); }`
    - The first non-null inventory only sets the baseline.
    - `onTick` returns true exactly once per run, on the tick its last item is made, and only if the run was detected (running).
    - On detection, the run's span starts at `firstActionTick − ticksPerAction`. It closes at the last action tick: on finish, abandon, or a switch.
    - `finishTick = lastActionTick + remaining × ticksPerAction` while running; after the finish it is the last action tick.
    - `onBankClick` after a finish sets `bankTick`, `wasted = max(0, bankTick − finishTick)`, and the record's `bankClick` and `wasted`. Before a finish it abandons the run (note 5).
    - `showing(tick)`: running or finished, and `tick − max(lastActionTick, bankTick) ≤ 20`.

- [ ] **Step 1: Write failing tests** (`InventoryRunTest`; unless stated: raw shark 383, baseline `{383: 28}`, an action = inventory one lower plus `XpDrop("Cooking", 210)` on that tick, actions at 100, 104, 108, …, `onTick` for every tick in order):
  - `singleDropDoesNotDetect` — one action at 100, ticks to 110 → `running() == false`, `showing(110) == false`.
  - `twoDropsWithin12TicksDetect` — actions at 100 and 104 → `running()`, `skill() == "Cooking"`, `total() == 28`, `done() == 2`, `runs().get(0).startTick == 96`.
  - `dropsTooFarApartDoNotDetect` — actions at 100 and 113 → not running.
  - `xpWithoutInventoryDropIsNoAction` — Cooking drops at 100 and 104 with an unchanged inventory → not running, `done() == 0`.
  - `xpOneTickAfterDecreaseStillMatches` — decreases at 100 and 104, drops at 101 and 105 → running, no `failedAction`.
  - `burntCountsButFlagged` — at 108 the inventory drops with no drop at 108 or 109 → `peek(108).failedAction`, `done() == 3`, `remaining() == 25`.
  - `etaExactForCooking` — 14 actions (last at 152) → `ticksPerAction() == 4`, `finishTick() == 208`, `remaining() == 14`.
  - `etaFollowsRateChange` — actions at 100, 104, 108, 112, 114, 116 → `ticksPerAction() == 2`, `finishTick() == 160`.
  - `pauseDoesNotInflateEta` — actions at 100, 104, 108, 128 → `ticksPerAction() == 4`, `finishTick() == 224`.
  - `finishReturnsTrueOnce` — all 28 actions: `onTick(208, …)` returns true, every other call false; `finished()`; `runs().get(0).endTick == 208`.
  - `wastedTicksBeforeBank` — after the finish at 208, `onBankClick(211)` → `wasted() == 3`, `peek(211).bankClick`, `peek(211).wasted == 3`; on a fresh run `onBankClick(208)` → `wasted() == 0`.
  - `bankMidInventoryEndsRunWithoutBlink` — 10 actions, `onBankClick(140)` → no `onTick` ever returned true, `!running()`, `!showing(140)`, `peek(140).bankClick == false`, the span ends at 136, and the later deposit (`{383: 0}` at 143) adds no actions.
  - `withdrawEndsRun` — mid-run the count rises to 28 → `!running()`; the next two actions start a new run with `total() == 28`.
  - `secondProductionSkillStartsNewRun` — baseline `{383: 28, 1511: 28}`, 10 cooking actions, then logs 1511 dropping by 1 with `XpDrop("Fletching", 5)` at 150 and 153 → `skill() == "Fletching"`, `total() == 28`, cooking span ends at 136, `runs().size() == 2`.
  - `limitingReagentIsInput` — baseline `{1777: 14, 62: 20}`, each action drops both by 1 with Fletching XP → `total() == 14`.
  - `multiUnitInput` — baseline `{2363: 25}` (bars), each action −5 with Smithing XP → `total() == 5`, `remaining()` after 2 actions `== 3`.
  - `nonProductionSkillIgnored` — Magic drops with the inventory falling → not running.
  - `historyBounded` — 10 complete runs → `runs().size() == 8`.
  - `resetClearsAll`.
- [ ] **Step 2:** Run `--tests '*InventoryRunTest'`. Expected: compile failure / FAIL.
- [ ] **Step 3: Implement** to the contract and notes 2–6. Keep at most one pending unmatched decrease (from the previous tick). Keep the last 3 positive gaps.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Commit.** `feat: inventory run tracker`

### Task 16: Production view, detection and done blink

**Files:**
- Create: `ui/modes/ProductionView.java`, `ui/SkillPainter.java`
- Modify: `model/Mode.java`, `model/ModeDetector.java`, `model/BarState.java`, `ui/BarPainter.java`, `ui/Palette.java`, `SuperHistogramConfig.java`, `SuperHistogramPlugin.java`, `test/.../ui/BarRenderSamples.java`
- Test: `model/ModeDetectorTest.java`, `ui/modes/ProductionViewTest.java`, `ui/BarRenderSmokeTest.java`, `ui/SampleStates.java`, `SuperHistogramPluginHandlersTest.java`

**Interfaces:**
- Consumes: `InventoryRun` (Task 15), `Pulse.tick/done` (Task 4), `TimelinePainter.x/pill/pillY/pillH/xp/hits` (Task 8), `ReadoutPainter.right/minSec` (Task 8).
- Produces:
  - `Mode` gains `PRODUCTION`. `Mode detect(boolean afk, boolean inCombat, Mode skilling /* PRODUCTION, ALCH or null */, PrayerMode prayerMode, BaselineSetting baseline)`: AFK, then combat, then `skilling`, then the baseline. The 4-argument `detect` delegates with `null`, so the existing tests are unchanged.
  - `BarState` + builder gain `InventoryRun run` (null), `boolean timeToFinish` (true), `boolean wastedTicks` (true).
  - `Palette` adds `COOK_PILL = #d9a35b`, `BURNT = #6b737c`, `WASTED_LINE = white 55%`.
  - `final class SkillPainter` (static): `pot(g, x, y, u, Color)`, `flag(g, x, y0, y1, u, Color)`, `bank(g, x, y, u, Color)`, `wasted(g, l, x0, x1, int n)`. `wasted` draws a 1.5u `WASTED_LINE` at y 45.25u from x0 to x1, and `"+N"` in bold 9u `LOST` centred on the line, baseline 42u. Paths are the mockup's `pot`, `flag` and `bank` functions × u.
  - `final class ProductionView implements ModeView`; `static String[] rightLines(BarState s)` (headline, small line); `static Color rightColor(BarState s)`.
  - Config: `timeToFinish` (true, position 24, "Time to finish"), `wastedTicks` (true, 25, "Wasted ticks (+N)"). The `blinkWhenDone` description becomes "Flash the bar mint when an inventory finishes or the AFK logout warning fires".
  - Plugin: `volatile InventoryRun run`, `Map<Integer,Integer> latestInventory`, `void handleBankClick()`, `static boolean isBankClick(String option, String target)` (`"Bank"`, or `"Use"` with a tag-stripped target containing `"Bank"`).

- [ ] **Step 1: Write failing tests.**
  - `ModeDetectorTest`: `productionBeatsBaseline` — `detect(false, false, PRODUCTION, LAZY_FLICK, MINIMAL) == PRODUCTION`. `combatBeatsProduction` — `detect(false, true, PRODUCTION, LAZY_FLICK, GENERAL) == LAZY_FLICK`. `afkBeatsProduction` — `detect(true, false, PRODUCTION, …) == AFK`.
  - `SampleStates.cooking(ProdPhase p)`, with `enum ProdPhase { RUNNING, DONE, BANKED }`, drives a real `InventoryRun` as cooking.html: 28 raw sharks, 4 ticks per action, burnt at items 9 and 25.
    - RUNNING: 27 actions, the last at `NOW_TICK − 2`, so `finishTick == NOW_TICK + 2`.
    - DONE: 28 actions, the last at `NOW_TICK − 3`.
    - BANKED: the last action at `NOW_TICK − 5`, bank click at `NOW_TICK − 2` (wasted 3).
    - `cooking(p, boolean timeToFinish, boolean wastedTicks)` varies the two settings.
  - `ProductionViewTest.rightLines`:
    - RUNNING → `{"0:01", "left · 27 of 28 cooked"}` (2 ticks × 0.6 = 1.2 s, rounded);
    - DONE → `{"Bank now", "28 of 28 cooked"}`;
    - BANKED → `{"Banking · +3", "28 of 28 cooked"}` with `rightColor == MINT`; with wasted 0 → `"Banking ✓"`;
    - RUNNING with `timeToFinish` false → `{"27 of 28", "cooked"}`;
    - a Fletching run uses `"done"` in place of `"cooked"`;
    - a null `run` → `{"", ""}`.
  - `BarRenderSmokeTest`:
    - `productionRendersAtThreeSizes` — each ProdPhase at 1026 × 62, 1026 × 46 and 600 × 30, no exception (the `Mode.values()` loops cover a null `run`).
    - `cookedPillSolidRemainingFaint` — RUNNING: `COOK_PILL` pixels at (x(NOW_TICK − 10), 29u); at (x(NOW_TICK + 1) + 16u, 29u) only the 1.5u outline, so the interior pixel equals the ground.
    - `finishFlagAheadOfNow` — RUNNING: opaque white within x(NOW_TICK + 2) − 1u .. + 9u, y 42u–60u at 62 px. None at 46 px, and none with `timeToFinish` false.
    - `wastedLabelOnlyWhenEnabled` — BANKED: `LOST` pixels in x(NOW_TICK − 5) .. x(NOW_TICK − 2), y 34u–42u; none with `wastedTicks` false.
    - `burntIsGrey` — RUNNING: `BURNT` pixels within x(item 25's tick) ± 12u, y 6u–16u.
    - `productionRightDoesNotOverlapLoginTime` — the same assertion as `lazyFlickTrailDoesNotOverlapLoginTime`, at 1026 × 62 and 720 × 62.
  - `SuperHistogramPluginHandlersTest` (logged in; an action = `handleTick`, `handleInventory({383: n})`, `handleStat("Cooking", base + 210·k)`; the first inventory and stat are baselines):
    - `productionDetectedAfterTwoActions` — after one action plus 4 ticks, `buildState().mode == GENERAL`; after the second, `PRODUCTION` with `modeName == "Cooking"` and `modeSub == "auto"`.
    - `inventoryDoneBlinks` — 28 actions, one per tick, then one more `handleTick(now)` → `pulse.mintAlpha(now) > 0`. With `blinkWhenDone` false → 0.
    - `cueOnFinishTick` — metronome CUE_TICK: `pulse.outlineAlpha` > 0.9 just after the `handleTick` whose tick equals `run.finishTick()`, and 0 on the tick before.
    - `bankClickRecordsWasted` — 3 ticks after the finish, `handleBankClick()` → `timeline.peek(tick).wasted == 3`.
    - `isBankClickMatches` — `("Bank", "<col=ffff>Banker")`, `("Bank", null)` and `("Use", "<col=ffff>Bank chest")` are true; `("Use", "Raw shark")` and `("Use", null)` are false, without throwing.
    - `logoutClearsRun` — `LOGIN_SCREEN` → `run.running() == false`, `latestInventory == null`.
- [ ] **Step 2:** Run `--tests '*ModeDetectorTest' --tests '*ProductionViewTest' --tests '*BarRenderSmokeTest' --tests '*PluginHandlersTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** to cooking.html (62-px values × u):
  - Pills (from `s.run.runs()`): fill `COOK_PILL` for Cooking, otherwise `Palette.skill(skill)`; y `pillY(l)`, h `pillH(l)`.
    - The current unfinished run first gets a faint outline from `x(start)` to `x(finishTick)`: stroke 1.5u, 45 % alpha.
    - Then a solid part from `x(start)` to `x(min(nowTick + 1, end or finishTick))`, at least `pillH` wide.
    - Pot (Cooking only): at `ix = max(x(start) + 11u, timelineX + 11u)`, `pillY + pillH/2 + 1u`, in `PILL_LABEL_SOLID`, drawn when `ix < solidEnd − 6u`.
  - Top row: `TimelinePainter.xp`. `"burnt"` (semibold 9.5u, `BURNT`) is centred on `x(t)` at baseline 15u for each `failedAction` tick, then `TimelinePainter.hits`.
  - Actions row (`rows == 3`):
    - finish flag at `x(finishTick)`, y 41u–61u, white, when the run is running, `finishTick > nowTick` and `timeToFinish`;
    - bank icon at `(x(t), 46u)` in `TEXT` on `bankClick` ticks;
    - when `wasted > 0` and `wastedTicks`: `SkillPainter.wasted(g, l, x(t − wasted), x(t) − 9u, wasted)` (this leaves room for the bank icon).
  - Right: `ReadoutPainter.right(g, l, s, lines[0], rightColor(s), lines[1])`. While running with `timeToFinish`, the headline is `minSec(round((finishTick − nowTick) × 0.6))`.
  - Plugin:
    - `initModel` and LOGIN_SCREEN create `new InventoryRun(timeline)` and set `latestInventory = null`. HOPPING keeps them.
    - `handleInventory` also stores `latestInventory = new HashMap<>(counts)`.
    - In `handleTick`, before the metronome: `prev = timeline.peek(tick − 1)`, `boolean finished = run.onTick(tick − 1, prev == null ? emptyList() : prev.xp, latestInventory)`; if `finished && config.blinkWhenDone()` → `pulse.done(nowMs)`. The metronome cue becomes `beat.isBeat(tick) || (run.running() && run.finishTick() == tick)`; CUE_TICK pulses only when that cue is true.
    - In `onMenuOptionClicked`: `isBankClick(option, target)` → `handleBankClick()`.
    - `buildState`: `skilling = run.showing(tickNow) ? PRODUCTION : null`. PRODUCTION → name `run.skill()`, sub `"auto"`. Set `run`, `timeToFinish`, `wastedTicks`. Register `ProductionView` in `BarPainter`.
  - `BarRenderSamples.writeProductionContactSheet` — each ProdPhase at 1026 × 62, 1026 × 46 and 600 × 30 (same pattern as `writeLazyFlickContactSheet`), to compare with cooking.html.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Build.** Expected: `BUILD SUCCESSFUL`, all earlier tests pass.
- [ ] **Step 6: Manual check in the dev client** (cook a full inventory at a range, then fletch logs). Note results:
  - the bar switches to "Cooking · auto" on the second item, not the first;
  - the pill fills and the faint part shrinks;
  - a burnt fish shows grey "burnt";
  - the countdown matches the real finish to within one tick;
  - the flag sits on the last item's tick, and with the metronome on, the outline goes bright there;
  - the bar blinks mint once when the last item is done;
  - clicking the bank 2 ticks late shows "+2" and "Banking · +2";
  - banking halfway drops back to General with no blink;
  - fletching right after cooking switches to "Fletching";
  - XP and the inventory change land on the same tick (if not, note the offset, since note 2 depends on it);
  - the login time is still at the far right.
- [ ] **Step 7: Commit.** `feat: production view with finish flag and done blink`

### Task 17a: Multi-XP row

**Files:**
- Create: `ui/XpRow.java`, `model/XpDrops.java`
- Modify: `ui/TimelinePainter.java`, `ui/CombatPainter.java`, `model/BarState.java`, `SuperHistogramConfig.java`, `SuperHistogramPlugin.java`
- Test: `ui/XpRowTest.java`, `ui/BarRenderSmokeTest.java`, `ui/SampleStates.java`

**Interfaces:**
- Consumes: `TickRecord.xp`, `TickRecord.attackLate`, `BarState.actionsRow` (Tasks 2, 10, 12).
- Produces:
  - `enum XpDrops { NUMBERS, DOTS_ONLY, OFF }` (toString "Numbers" / "Dots only" / "Off").
  - `final class XpRow { enum Row { TOP, LOW } static final int FREQUENT_WINDOW = 10, FREQUENT_MIN = 5; static Set<String> frequentSkills(Timeline t, int nowTick); static void paint(Graphics2D g, BarLayout l, BarState s, Row row); }`
    - `frequentSkills`: skills with drops on ≥ 5 distinct ticks in `[nowTick − 9, nowTick]`. It is computed once per frame and applies to every visible tick.
    - `paint` goes oldest tick → newest, with one `busyTo` carried across ticks. Start `x = x(t) − 4u`; LOW uses `x(t) + 8u` when `actionsRow != OFF` and `attackLate >= 0` (today's `xpLow` rule). For each entry:
      - a dot (frequent skill with `xpDots`, or any skill under DOTS_ONLY): skipped if `x < busyTo`, otherwise a circle r 2.6u at `(x + 2.6u, dotY)` in the skill colour, then `x += 9u`;
      - otherwise a number: `"+N"` bold 10u in the skill colour at the baseline, then `x += width + 6u` and `busyTo = x + 2u`.
    - TOP: baseline `xpY(l)`, dotY `xpY − 3u`, hidden at `rows == 1`. LOW: baseline 48u, dotY 44u, hidden at `rows < 3`. OFF draws nothing.
    - Task 17b adds gold entries at the front of a cast tick.
  - `TimelinePainter.xp(g, l, s)` → `XpRow.paint(g, l, s, TOP)`; `CombatPainter.xpLow(g, l, s)` → `XpRow.paint(g, l, s, LOW)`.
  - `BarState` + builder gain `XpDrops xpDrops` (NUMBERS), `boolean xpDots` (true).
  - Config: `xpDrops` (XpDrops, NUMBERS, position 20, "XP drops"), `xpDots` (true, 21, "Frequent skills as dots").

- [ ] **Step 1: Write failing tests.**
  - `XpRowTest`: `frequentNeedsFiveTicksInTen` — Fletching on ticks 191, 193, 195, 197, 199 with now 200 → frequent; drop 191 → not. `frequencyCountsTicksNotDrops` — 6 Fletching drops on tick 199 → not frequent. `oldDropsDoNotCount` — 5 drop ticks at 180–188 → not frequent at 200.
  - `SampleStates.multiXp(XpDrops d, boolean dots)` (General mode): Strength 120 + Hitpoints 40 at `NOW_TICK − 10`, Magic 123456 at `NOW_TICK − 6`, Fletching 15 at `NOW_TICK − 9, −7, −5, −3, −1`.
  - `BarRenderSmokeTest`:
    - `numbersSideBySide` — Strength-colour pixels in y 6u–16u all lie left of every Hitpoints-colour pixel on tick `NOW_TICK − 10`.
    - `dotsHiddenUnderNumbers` — there are no Fletching-colour pixels within 2u of `(x(NOW_TICK − 5) − 1.4u, 12u)`, which is under the "+123456" group; there are some within 2u of `(x(NOW_TICK − 3) − 1.4u, 12u)`.
    - `dotsOffShowsFrequentAsNumbers` — `xpDots` false: Fletching pixels in x(NOW_TICK − 3) + 4u .. + 14u, y 6u–16u.
    - `dotsOnlyDrawsDots` — DOTS_ONLY: Strength pixels within 2u of `(x(NOW_TICK − 10) − 1.4u, 12u)`, none in x(NOW_TICK − 10) + 4u .. + 18u.
    - `xpDropsOffHidesXp` — OFF: no pixel of any of the four skill colours in y 5u–17u.
    - `combatXpStillLow` — `lazyFlick()` still has Strength pixels at y 40u–49u, and none at y 5u–17u. The existing lazy-flick and 1-tick tests pass unchanged.
- [ ] **Step 2:** Run `--tests '*XpRowTest' --tests '*BarRenderSmokeTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** `XpRow` and the two delegations. Plugin `buildState` sets `xpDrops` and `xpDots` from the config.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Build.** Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 6: Manual check.** Note results:
  - melee slayer shows Strength, Hitpoints and Slayer numbers side by side by the sword;
  - fletching darts while walking shows Fletching as dots, and the dots hide under a number group;
  - "Dots only" and "Off" behave as named.
- [ ] **Step 7: Commit.** `feat: multi-xp row with frequent-skill dots`

### Task 17b: High alch

> Changed by operator: coins received (High Alchemy value), not profit.

**Files:**
- Create: `model/skill/AlchTracker.java`, `model/skill/AlchTopRow.java`, `model/RightSide.java`, `ui/modes/AlchView.java`
- Modify: `model/TickRecord.java`, `model/XpTracker.java`, `model/Mode.java`, `model/ModeDetector.java`, `model/BarState.java`, `ui/XpRow.java`, `ui/SkillPainter.java`, `ui/ReadoutPainter.java`, `ui/modes/MinimalView.java`, `ui/BarPainter.java`, `SuperHistogramConfig.java`, `SuperHistogramPlugin.java`, `test/.../ui/BarRenderSamples.java`
- Test: `model/skill/AlchTrackerTest.java`, `model/XpTrackerTest.java`, `model/ModeDetectorTest.java`, `ui/modes/AlchViewTest.java`, `ui/BarRenderSmokeTest.java`, `ui/SampleStates.java`, `SuperHistogramPluginHandlersTest.java`

**Interfaces:**
- Consumes: `XpRow` (17a), `SkillPainter.wasted` (Task 16), `CombatPainter.caret` (Task 12), `MinimalView` level line (Task 8), `ModeDetector.detect` with `skilling` (Task 16).
- Produces:
  - `TickRecord` gains `public boolean cast; public Long castProfit /* null = unknown */; public int castLost;`.
  - `final class AlchTracker { static final int CAST_TICKS = 5, XP = 65, PENDING_TICKS = 2, LOST_MAX = 10, LINGER = 10; AlchTracker(Timeline t); void onCastClick(int tick, Long profit); void onTick(int tick, List<XpDrop> drops); boolean showing(int tick); int casts(); int lastCastTick() /* -1 */; int nextReadyTick() /* lastCast + 5, -1 none */; long sessionGold(); int unpricedCasts(); long goldPerHour(long sessionMs); void rebind(Timeline t); void reset(); }`
    - A `"Magic"` drop of exactly 65 at `onTick(t)` confirms the oldest pending click with `click ≤ t ≤ click + 2`, and records the cast on tick `t`. Expired clicks are dropped. A drop with no pending click is ignored.
    - `castLost = t − (lastCast + 5)` when that is 1..10, otherwise 0.
    - `showing`: the last two casts are ≤ 15 ticks apart, and `tick − lastCast ≤ 10`.
    - `rebind` keeps the session gold, as `XpTracker.rebind` does.
  - `enum AlchTopRow { GOLD, XP, BOTH }`, `enum RightSide { PER_HOUR, SESSION }` (toString "Per hour" / "Session totals").
  - `SkillPainter.coin(g, cx, cy, float r)`: a `DAMAGE` disc of radius r, with a `#b8901d` ring at 0.53r, stroke 0.26r (the mockup's 3.4 / 1.8 / 0.9).
  - `XpTracker.sessionXp(String skill)`. `MinimalView.levelPart(BarState s, String skill, long perHour)` returns `"level 87 in 1h 5m"`; `smallLine` becomes `skill + " · " + levelPart`, and its output is unchanged.
  - `Mode` gains `ALCH`; `static Mode ModeDetector.skilling(boolean alching, boolean producing)` gives ALCH, PRODUCTION or null.
  - `XpRow` (TOP, `s.mode == ALCH`): on a `cast` tick, unless `alchTopRow == XP`, a gold entry comes first: `coin(g, x + 3u, baseline − 3.5u, 3.4u)`, `x += 9u`, then `formatProfit` in bold 10u (`DAMAGE` when ≥ 0, `TEXT` when < 0, `DIM` when unknown). With `alchTopRow == GOLD`, that tick's 65 Magic drop is skipped. Other skills are always drawn.
  - `ReadoutPainter.right(g, l, s, big, colour, small, boolean coin)`: with `coin`, `SkillPainter.coin(g, x + 6u, 26u, 6u)` is drawn and the headline starts 15u later.
  - `final class AlchView implements ModeView`; `static String formatProfit(Long p)` gives `"+236"`, `"−40"`, `"+1.2k"` (`compact` of the absolute value) or `"?"`; `static String[] rightLines(BarState s)`.
  - `BarState` + builder gain `AlchTracker alch` (null), `AlchTopRow alchTopRow` (GOLD), `RightSide rightSide` (PER_HOUR).
  - Config: `alchTopRow` (GOLD, position 22, "Alch top row"), `rightSide` (PER_HOUR, 23, "Right side shows").
  - Plugin: `volatile AlchTracker alch`, `void handleCastClick(int itemId)`, `Long alchProfit(int itemId)`, `static boolean isHighAlchClick(MenuAction a, String target)`.

- [ ] **Step 1: Write failing tests.**
  - `AlchTrackerTest`:
    - `castConfirmedByMagic65` — `onCastClick(100, 200L)`, `onTick(101, [Magic 65])` → `peek(101).cast`, `castProfit == 200`, `casts() == 1`, `nextReadyTick() == 106`.
    - `xpWithoutClickIsNoCast` — one Magic 65 drop and no click → `casts() == 0`.
    - `clickWithoutXpExpires` — click at 100, no XP at 101–102, a 65 drop at 103 → no cast.
    - `otherMagicXpIsNoCast` — click, then Magic 31 → no cast.
    - `singleCastDoesNotShow` — one cast at 101 → `showing(102) == false`. A second at 106 → `showing(106)`, `showing(116)`, `!showing(117)`.
    - `lostTicksBetweenCasts` — casts at 101 and 108 → `castLost == 2`; 101 and 106 → 0; 101 and 130 → 0 (a pause).
    - `unknownPriceExcludedFromGold` — profits 200 and `null` → `sessionGold() == 200`, `unpricedCasts() == 1`, `peek(t).castProfit == null`.
    - `sessionGoldAndPerHour` — profits 200 and −40 → `sessionGold() == 160`, `goldPerHour(3_600_000) == 160`, `goldPerHour(0) == 0`.
    - `rebindKeepsGold` — after `rebind(new Timeline(512))`, `sessionGold()` is unchanged and `lastCastTick() == -1`.
  - `XpTrackerTest.sessionXpPerSkill` — Magic +65 twice, Fishing +50 → `sessionXp("Magic") == 130`, `sessionXp("Cooking") == 0`.
  - `ModeDetectorTest`: `skillingPrefersAlch` — `skilling(true, true) == ALCH`, `skilling(false, true) == PRODUCTION`, `skilling(false, false) == null`. `combatBeatsAlch` — `detect(false, true, ALCH, LAZY_FLICK, GENERAL) == LAZY_FLICK`.
  - `SampleStates.alch(AlchTopRow top, RightSide side)` drives a real `AlchTracker` as high-alch.html:
    - casts every 5 ticks, the last at `NOW_TICK − 2` (next ready `NOW_TICK + 3`);
    - casts at `NOW_TICK − 19`, then `CAST_LATE = NOW_TICK − 12` (2 ticks late, profit 236), `CAST_LOSS = NOW_TICK − 7` (−40) and `NOW_TICK − 2` (unpriced); earlier casts every 5 ticks, at 212 and 198;
    - a Magic 65 drop on each cast tick;
    - the clock is online 1 h;
    - for exact text: `alchText(Long... profits)` with Magic at 13,034,301 then +65 per cast (level 99).
  - `AlchViewTest`:
    - `formatProfit` — 236 → `"+236"`, −40 → `"−40"`, 1234 → `"+1.2k"`, null → `"?"`.
    - `rightLines` with `alchText(200L, -40L)`:
      - GOLD / PER_HOUR → `{"+160/hr", "+160 this session"}`;
      - GOLD / SESSION → `{"+160", "+160/hr"}`;
      - XP / PER_HOUR → `{"130 xp/hr", "130 xp · level 99"}`;
      - XP / SESSION → `{"130 xp", "130/hr · level 99"}`;
      - BOTH / PER_HOUR → `{"+160/hr", "+160 this session · 130 xp/hr"}`;
      - BOTH / SESSION → `{"+160", "+160/hr · 130 xp"}`.
    - `lossTotalHasMinus` — `alchText(-40L)` GOLD / SESSION → `"−40"`.
    - `unpricedNoted` — `alchText(200L, null)` → the small line ends `" · 1 unpriced"`.
  - `BarRenderSmokeTest`:
    - `alchRendersAtThreeSizes` — each AlchTopRow at 1026 × 62, 1026 × 46 and 600 × 30.
    - `castPillIsMagicBlue` — `PRAYER_MAGIC` at (x(CAST_LOSS) + 16u, 29u).
    - `goldHasCoin` — `DAMAGE` within 1u of (x(CAST_LATE) − 1u, 11.5u) with GOLD and BOTH, not with XP.
    - `profitNegativeShowsMinus` — GOLD: `TEXT` pixels and no `DAMAGE` pixels in x(CAST_LOSS) + 5u .. + 25u, y 7u–16u.
    - `lostTicksLabel` — `LOST` pixels in x(CAST_LATE − 2) .. x(CAST_LATE), y 34u–42u; none with `wastedTicks` false.
    - `nextCastCaretAhead` — non-ground pixels in x(NOW_TICK + 3) ± 3u, y 57u–60u at 62 px (below the ruler marks); none at 46 px.
    - `alchFletchShowsDots` — `alch` plus Fletching 15 on every even tick → Fletching pixels within 2u of (x(NOW_TICK − 4) − 1.4u, 12u), and none in x(NOW_TICK − 4) + 4u .. + 20u (a dot, not a number).
    - `alchRightDoesNotOverlapLoginTime` — the same assertion as Phase 2, at 1026 × 62 and 720 × 62, for each AlchTopRow.
  - `SuperHistogramPluginHandlersTest` (mocked `ItemManager`: `canonicalize(1127)` and `canonicalize(1128)` → 1127, `getItemComposition(1127).getHaPrice()` 39000, `getItemPrice(1127)` 38500, `getItemPrice(ItemID.NATURERUNE)` 100):
    - `alchProfitFromPrices` — `alchProfit(1127) == 400L`.
    - `notedItemUsesBasePrice` — `alchProfit(1128) == 400L`.
    - `alchProfitUnknownWhenNoPrice` — item price 0 → null; nature price 0 → null; `alchProfit(-1)` → null; a null composition → null; none throw.
    - `highAlchClickDetected` — `isHighAlchClick(WIDGET_TARGET_ON_WIDGET, "<col=00ff00>High Level Alchemy</col><col=ffffff> -> <col=ff9040>Rune platebody")` is true. `"Low Level Alchemy -> …"`, another `MenuAction`, and a null target are false.
    - `alchModeAfterTwoCasts` — after a Magic baseline: `handleCastClick(1127)`, `handleTick`, `handleStat("Magic", base + 65)`, 4 more ticks, a second cast → `buildState().mode == ALCH`, `modeName == "High alch"`, `modeSub == "auto · 5t"`; after only the first cast → GENERAL.
    - `alchBeatsProductionInPlugin` — an active cooking run and two casts → ALCH.
    - `logoutKeepsGoldClearsCasts` — `LOGIN_SCREEN` → `alch.sessionGold()` unchanged, `alch.lastCastTick() == -1`.
- [ ] **Step 2:** Run `--tests '*AlchTrackerTest' --tests '*XpTrackerTest' --tests '*ModeDetectorTest' --tests '*AlchViewTest' --tests '*BarRenderSmokeTest' --tests '*PluginHandlersTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** to high-alch.html (62-px values × u):
  - Timeline:
    - for ticks from `oldestTick − 5`, each `cast` tick c gets `TimelinePainter.pill(g, l, x(c), x(min(c + 5, nowTick + 1)), PRAYER_MAGIC, false, null)`;
    - then `TimelinePainter.xp` (the gold entries through `XpRow`) and `TimelinePainter.hits`;
    - actions row (`rows == 3`): `SkillPainter.wasted(g, l, x(c − castLost) + 1, x(c) − 1, castLost)` when `castLost > 0` and `wastedTicks`;
    - `CombatPainter.caret(g, x(next), 55u, u, NEXT_BEAT)` when `nextReadyTick > nowTick` and alch is showing.
  - Right: `ReadoutPainter.right(g, l, s, lines[0], colour, lines[1], coin)`.
    - The headline is gold for GOLD and BOTH. It is drawn with the coin, in `DAMAGE` (≥ 0) or `TEXT` (< 0). The rate uses `clock.onlineMs`.
    - The headline is XP for XP: white, no coin, using `xp.sessionXp("Magic")` and `xp.xpPerHour("Magic", …)`.
    - `rightSide` picks which of total / rate is the headline. The other goes in the small line. BOTH appends the Magic XP in the `rightSide` form. XP appends `levelPart`.
  - Plugin:
    - `initModel` creates `new AlchTracker(timeline)`; LOGIN_SCREEN calls `alch.rebind(timeline)`.
    - `onMenuOptionClicked`: `isHighAlchClick(e.getMenuAction(), e.getMenuTarget())` → `handleCastClick(e.getItemId())`, which calls `alch.onCastClick(tick, alchProfit(itemId))`. This runs on the client thread, so `getItemComposition` is safe there.
    - `alchProfit`: `id = itemManager.canonicalize(itemId)`, `ha = composition.getHaPrice()`, `item = getItemPrice(id)`, `nat = getItemPrice(ItemID.NATURERUNE)`; return null under note 10, else `ha − item − nat`.
    - `handleTick`: `alch.onTick(tick − 1, prev.xp)` next to `run.onTick`. The cue adds `alch.showing(tick) && alch.nextReadyTick() == tick`.
    - `buildState`: `skilling = ModeDetector.skilling(alch.showing(t), run.showing(t))`. ALCH → name `"High alch"`, sub `"auto · 5t"`. Set `alch`, `alchTopRow`, `rightSide`.
    - Register `AlchView`.
  - `BarRenderSamples.writeAlchContactSheet` — each AlchTopRow × RightSide at 1026 × 62, plus 1026 × 46 and 600 × 30.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Build.** Expected: `BUILD SUCCESSFUL`, and all Phase 1–3 tests pass.
- [ ] **Step 6: Manual check in the dev client** (high alch a stack of noted items with a fire staff). Note results:
  - the click event's `getItemId()` is the alched item (if it is −1, note it: every cast then shows "?");
  - the bar switches to "High alch · auto · 5t" on the second cast;
  - the pills touch when casting on the ▲, and a late cast shows "+N";
  - the per-cast gold matches the HA value − GE price − nature rune price shown by the GE tooltip;
  - an untradeable item shows "?" and no crash;
  - Top row Gold / XP / Both and Right side Per hour / Session totals switch the readout;
  - alch + fletch darts shows Fletching dots;
  - the login time stays at the far right.
- [ ] **Step 7: Commit.** `feat: high alch view with per-cast profit`

**Phase 3 done:** inventory skilling with exact countdowns and the done blink, high alch profit, and multi-skill XP numbers and dots in every mode.

---

## Phase 4 — Gathering + agility (mockups: `gathering.html`, `agility.html`, `settings-panel.html`)

Paths, test and build commands, and the 62-px × `u` convention are as in Phase 2. Pixel-test conventions are as in Phase 3 ("opaque white" = alpha ≥ 200 and every channel ≥ 240; colour matches allow ±8 per channel). `x(t)` means `TimelinePainter.x(tl, s, t)`, where `tl` is the timeline layout (`BarPainter.timelineLayout`, Task 19b). Outside agility, `tl` is the bar's layout.

**RuneLite API used, confirmed with `javap` against client / runelite-api 1.13.6:**
- `events.GameObjectDespawned.getGameObject()/getTile()`, `GameObject.getSceneMinLocation()` → `Point.getX()/getY()`
- `events.NpcDespawned.getNpc()` + `NPC.getIndex()` (already used)
- `events.ItemSpawned.getItem()/getTile()`, `TileItem.getId()`, `Tile.getWorldLocation()`
- `events.MenuOptionClicked.getMenuAction()/getMenuOption()/getMenuTarget()/getId()/getParam0()/getParam1()`
- `MenuAction.GAME_OBJECT_FIRST_OPTION … GAME_OBJECT_FIFTH_OPTION`, `NPC_FIRST_OPTION … NPC_FIFTH_OPTION`, `CC_OP`, `WIDGET_TARGET_ON_WIDGET`, `WALK`
- `events.GameStateChanged.getGameState()` with `GameState.LOADING`
- `events.StatChanged.getSkill()/getXp()` with `Skill.WOODCUTTING/MINING/FISHING/FIREMAKING/AGILITY.getName()`
- `Actor.getAnimation()/getWorldLocation()`
- `coords.WorldPoint(int x, int y, int plane)`, `WorldPoint.getX()/getY()/getPlane()/getRegionID()`
- `ItemContainer.getItems()`
- `gameval.ItemID.GRACE` (11849: the mark of grace; the gameval name is `GRACE`, not `MARK_OF_GRACE`), `gameval.ItemID.YEW_LOGS` (1515), `IRON_ORE` (440), `RAW_TROUT` (335), `TINDERBOX` (590)
- `gameval.ObjectID.ROOFTOPS_SEERS_WALLCLIMB/JUMP/TIGHTROPE/JUMP_1/JUMP_2/LEAPDOWN` (14927, 14928, 14932, 14929, 14930, 14931). These are used only as test data.
- `client.config.ConfigManager.getRSProfileConfiguration(String group, String key, java.lang.reflect.Type)` (generic `T`) and `setRSProfileConfiguration(String, String, T)`
- `client.util.Text.removeTags(String)` (already used)

`client.plugins.agility.Courses` and `Obstacles` exist but are package-private, so they cannot be referenced. Their course regions and lap-end tiles are copied into our own table (note 10).

Runtime behaviour that `javap` cannot show is checked in the manual steps:
- that a tree or rock depleting fires `GameObjectDespawned` at the same scene-min tile as the menu click's `param0/param1`;
- that `getId()` on an NPC menu click is the NPC index;
- that a moving fishing spot fires `NpcDespawned`;
- that the lap-end Agility XP arrives while the player stands on the lap-end tile.

**Expansion notes (changes from the task-level text):**
1. Each task is split into a model task (**18a**, **19a**) and a view + plugin task (**18b**, **19b**), the same way Tasks 15 and 16 are split. Each **b** depends on its **a**. 19b also depends on 18b, because both edit `ModeDetector.skilling` and the plugin. A reviewer can reject a view without rejecting its model.
2. (Ruling) A **roll** is a tick on which the player animates while a gathering pill is open. A **success** is an XP drop of the pill's skill. The item gained on the same or the next tick is optional: infernal tools and Forestry give XP without an item.
   - The mockup draws a notch every 4 ticks. That is not adopted, because the real roll interval depends on the tool. The estimate only needs items per animating tick, so one notch per roll tick is both honest and enough.
3. **Estimate:**
   - `rate` = successes ÷ rolls over the last 100 rolls. A success on a tick with no roll counts as a roll. The rate needs at least 2 successes and is reset when the skill changes.
   - ETA = `ceil(free / rate)` ticks (Firemaking: `ceil(logsLeft / rate)`).
   - It is shown as `"~" + m:ss`, with the seconds rounded to the nearest 10 s (at least 10 s). Per hour = `round(rate × 6000)`.
   - Walking and re-click time are not in the rate, which is one more reason for the "~".
   - Capacity is `count + free slots`, where free slots = 28 − occupied slots, read from the container. So an axe kept in the inventory is handled.
4. **Resource identity comes from the click:**
   - `"Chop down"`/`"Chop"` → Woodcutting and `"Mine"` → Mining, on `GAME_OBJECT_*` options. The key comes from the scene tile `param0/param1`.
   - Fishing options on `NPC_*` options: `"Net"`, `"Small Net"`, `"Big Net"`, `"Bait"`, `"Lure"`, `"Harpoon"`, `"Cage"`, `"Fish"`, `"Use-rod"`. The key is `npcKey(index)`.
   - `"Light"` on a log, or `"Use"` with a tag-stripped target of `"Tinderbox -> <log>"` or `"<log> -> Tinderbox"`, is Firemaking, with key 0. The name is the log side.
   - **Depletion** is a `GameObjectDespawned` at the key's tile, or an `NpcDespawned` of the spot. Despawns on the tick a `LOADING` state arrives are ignored, because a scene reload despawns everything.
   - A success with no recognised click still opens a pill (key 0, no depletion tracking), so an unlisted option only loses the stump.
5. **A pill ends** on:
   - depletion (stump or marker), at the depletion tick;
   - a click on another target;
   - a bank click;
   - the inventory filling up;
   - 3 ticks without a roll (stopped or interrupted), at the last roll + 1.
   - A pill with no roll within 15 ticks is dropped and never drawn (a misclick, or a level too low).
   - **"+N"** is the number of ticks from the depletion to the next target click. It counts only for 1..10 ticks. More than that is a pause, which matches alch's `LOST_MAX`.
6. **Firemaking:** one pill per log. A success ends the pill (with no depletion mark) and starts the "+N" count. The headline counts logs left ("24 left", "done in ~1:00"). Running out of logs is the "done" event.
7. **Blink and cue (rulings):**
   - Gathering blinks when the inventory fills (Firemaking: when the logs run out). `gathering.html` blinks on every tree fall; that is not adopted.
   - Agility blinks when a lap is completed. `agility.html` blinks on every landing; that is not adopted. The `blinkWhenDone` description is updated to match.
   - Metronome cue: for gathering, the first tick after the resource depletes (for Firemaking, after a fire is lit), which is the "click the next one" tick. For agility, the first tick after a landing (obstacle ready). Both come only from the player's own actions. No cue is given while the inventory is full.
8. **Gathering right readout** follows `gathering.html`:
   - The headline is the count, `"12/28"`. The small line is `"full in ~1:40 · 600 logs/hr"`, so the "~" estimate is in the small line.
   - When full: `"Full · bank"` / `"28/28 logs"`.
   - `timeToFinish` off drops the "full in" part.
   - There is no finish flag, because the finish time is an estimate (the mockup says the same).
   - The nouns are Woodcutting "logs", Mining "ore", Fishing "fish" and Firemaking "fires". Rates use `ReadoutPainter.compact`.
9. **Top row:**
   - XP goes through `XpRow` (Phase 3 note 7).
   - The item icon is drawn just left of the XP group, centred at `x(t) − 11u`. This replaces the mockup's log-then-number layout.
   - Woodcutting gets the mockup's log, axe and stump. Mining and Fishing get a generic item disc and a depleted-ring marker. Firemaking gets no item icon. Phase 5's icon set replaces these.
10. **Course identification (decision): a bundled region table plus a learned obstacle order.**
    - `AgilityCourses` holds 12 courses: Gnome, Draynor, Al Kharid, Varrock, Canifis, Falador, Seers', Pollnivneach, Rellekka, Ardougne, Barbarian, Wilderness. Each has a region ID and lap-end tiles, copied from RuneLite 1.13.6's `Courses.java` (BSD-2, credited in a comment).
    - An **obstacle** is a `GAME_OBJECT_*` click made inside a course region (not a bank or gathering click) and confirmed by an Agility XP drop.
    - A **lap ends** on Agility XP while the player stands on a lap-end tile. This is the rule RuneLite's agility plugin uses.
    - Obstacle numbers come from the order learned on the first full lap. Why: RuneLite's obstacle IDs are package-private and unordered. The region table gives the course name and an exact lap end from lap 1. Learning the order copes with falls and alternate routes, without bundling about 100 ordered IDs that cannot be verified offline.
11. **Is lap 1 full?**
    - A lap that starts right after a lap end is full.
    - The first lap on a course in a login is provisional: the player may have started mid-course. It is confirmed when the next lap starts with the same obstacle ID. If confirmed, its time counts for best and its order is learned. If not, its time, order and gap stats are discarded, and lap 2 is learned instead.
    - Before an order is learned, numbers are positions in the lap.
12. **Lap time, "+N" and slowest:**
    - Lap time = the final landing − the lap's first obstacle click. It is shown as `m:ss.t` (ticks × 0.6 s, exact to 0.1 s).
    - **"+N"** = ticks from a landing to the next obstacle click. It counts for 1..10 ticks: 0 if you clicked on or before the landing tick, a pause if more than 10. The gap after a lap-end landing is the walk back to the start, so it is neither drawn nor counted.
    - **Lost** = the sum of "+N" within the lap.
    - **Slowest after #k** = the obstacle number with the highest mean gap (0s included) over the session's laps on that course. Ties go to the lower number. If all means are 0, there is none.
13. **Falls and misclicks:**
    - A hit on the player while an obstacle is pending, on a tick with no Agility XP, fails the obstacle: the pill is faint and nothing lands. If Agility XP arrives within 2 ticks, it becomes a normal landing (a Wilderness monster hit you mid-obstacle).
    - A failure clears the gap start, so the next click shows no "+N".
    - A pending click with no XP and no hit for 30 ticks is dropped (a misclick). A click on a different object replaces the pending one. A repeated click on the same object is ignored.
    - **Priority adjustment:** while agility is showing, hits alone do not switch to the combat view. Only an NPC target or an own attack does. Otherwise every fall would flip the bar to lazy flick for 10 ticks.
14. **Marks of grace:** an `ItemSpawned` of `ItemID.GRACE` counts when:
    - its tile's region is the current course's region;
    - it did not spawn on the `LOADING` tick;
    - it spawned 0–2 ticks after a landing. This filters scene-load spawns, marks off the course, and marks spawned long after the player passed.
15. **Agility tick width:**
    - `agility.html` uses 14-px ticks at 62 px, which is **14u**.
    - A real rooftop lap is about 60–110 ticks, but 14u shows only 49 ticks at 1026 px, so "whole lap fits" would fail.
    - The agility tick width is therefore `clamp(timelineW / (lapRef + 6), max(4, 6u), 14u)`, where `lapRef = max(bestTicks, lastLapTicks)` on the course. Before any lap it is 14u.
    - It is applied through `BarLayout.withTickWidth` to the ruler, the timeline and the now line. The left and right readouts keep the bar's layout.
16. **Best lap is saved per RuneLite account** with `ConfigManager` RS-profile config, under key `bestLap.<COURSE_ID>` in group `superhistogram` (ticks). It is loaded when the player enters a course region and written whenever it improves. This takes three calls, so it is included.
    - Config `lapCompare` ("Agility: compare lap to", Best / Last lap, default Best, position 26), as in `settings-panel.html`.
17. **Priority after this phase:** AFK > combat > alch > production > agility > gathering > baseline.
    - Agility is placed above gathering because it needs a course region and confirmed landings, so it is the more specific signal.
    - AFK still wins: a minute of AFK fishing shows the AFK view with its logout countdown, as the spec intends.
18. `GatherRun` and `AgilityCourse` are read by the render thread. Their public methods are `synchronized`, and their lists are returned as copies of immutable snapshots, as `Beat` does.

### Task 18a: Gathering tracker

**Files:**
- Create: `model/skill/GatherRun.java`
- Modify: `model/TickRecord.java`
- Test: `model/skill/GatherRunTest.java`

**Interfaces:**
- Consumes: `Timeline`, `TickRecord`, `XpDrop` (Task 2).
- Produces:
  - `TickRecord` gains `public boolean roll; public int gathered /* item id gained by a success, 0 none */; public boolean depleted; public int reclick /* +N ticks, 0 none */; public boolean gatherBank;`.
  - `final class GatherRun { static final Set<String> SKILLS /* "Woodcutting","Mining","Fishing","Firemaking" */; static final int INVENTORY = 28, ROLL_WINDOW = 100, MIN_SUCCESSES = 2, IDLE_END = 3, STALE = 15, LOST_MAX = 10, LINGER = 20, HISTORY = 16; static final class Pill { final String skill; final int key; final String name; final int startTick; final int endTick /* -1 while open */; final boolean depleted; final int rolls; } GatherRun(Timeline t); void onTargetClick(int tick, String skill, int key /* 0 unknown */, String name); void onTargetGone(int tick, int key); void onBankClick(int tick); boolean onTick(int tick, boolean animating, List<XpDrop> drops, Map<Integer,Integer> inventory /* full counts, null = unchanged */, int freeSlots /* -1 = unchanged */); boolean showing(int tick); boolean active(); String skill(); String targetName(); int count(); int capacity() /* -1 unknown */; boolean full(); int successes(); double rate() /* 0 unknown */; int etaTicks() /* -1 unknown */; long perHour(); int cueTick() /* -1 none */; List<Pill> pills() /* oldest first, snapshots */; void reset(); }`
    - The first non-null inventory only sets the baseline. A tick with no record (`timeline.at` is null) is skipped.
    - `onTick(t)` order:
      - (1) diff the inventory and update the free slots;
      - (2) a roll, when a pill is open and `animating`: `peek(t).roll`, `rolls++`;
      - (3) a success, for a drop whose skill is the pill's skill, or any `SKILLS` skill when no pill is open. With no pill open, a pill opens with key 0 at `t − 1`. Item matching: an increase on t or t − 1, or on t + 1 (deferred), sets `peek(t).gathered` and adds the ID to the gathered set. For Firemaking a decrease does the same.
      - (4) a pending `onTargetGone` for this pill's key → end at t, `depleted`, `peek(t).depleted`;
      - (5) Firemaking: a success ends the pill at t;
      - (6) the full / out event;
      - (7) the idle end and the stale drop (note 5).
    - `onTick` returns true exactly once when a success makes `free == 0` (Firemaking: when the logs left reach 0). It stays false until `full()` has cleared (free > 0) and the inventory fills again.
    - `count()` = the inventory total of the gathered item IDs. They are kept per skill; trout and salmon both count. `capacity()` = `count + free` when free slots are known (−1 for Firemaking).
    - `onTargetClick` with the open pill's key (key ≠ 0, same skill) does nothing. Otherwise it closes the open pill at `tick` and opens a new one. If the last end tick `e` (a depletion or a Firemaking success) is known and `1 ≤ tick − e ≤ 10`, it sets `peek(tick).reclick`. The last end then clears.
    - `onBankClick` closes the open pill at `tick` and clears the last end. When `full()`, it also sets `peek(tick).gatherBank`.
    - `cueTick()` = last end + 1 while it is set and not `full()`.
    - `showing(t)`: the open or latest pill has ≥ 2 rolls, and either a pill is open or `t − lastActivity ≤ 20`. `lastActivity` is the last roll, success, depletion or bank click.

- [ ] **Step 1: Write failing tests** (`GatherRunTest`). Unless stated:
  - Woodcutting, yew logs `YEW = 1515`, tree keys `A = 101`, `B = 102`;
  - baseline `onTick(99, false, [], {}, 28)`;
  - a roll tick is `onTick(t, true, [], null, -1)`;
  - a success at t is `onTick(t, true, [XpDrop("Woodcutting", 175)], counts with one more 1515, free one fewer)`;
  - the click is `onTargetClick(100, "Woodcutting", A, "Yew tree")`, with ticks 100–101 not animating (walking);
  - `onTick` is called for every tick in order.

  The tests:
  - `clickThenRollsOpenPill` — rolls 102–110 → one pill, `startTick == 100`, `endTick == -1`; `peek(102..110).roll` is true, `peek(101).roll` is false; `showing(110)`; `skill() == "Woodcutting"`; `targetName() == "Yew tree"`.
  - `clickWithoutAnimationIsDropped` — no animation for 20 ticks → `pills().isEmpty()`, `!showing(120)`.
  - `itemWithoutXpIsNoSuccess` — +1 log with no drop (picked up) → `successes() == 0`, `peek(t).gathered == 0`.
  - `xpWithoutItemIsSuccess` — a drop with no item (infernal axe) → `successes() == 1`, `peek(t).gathered == 0`, `count() == 0`.
  - `itemOneTickAfterXpMatches` — XP at 106, log at 107 → `peek(106).gathered == 1515`, `count() == 1`.
  - `estimateUsesObservedRate` — rolls 102–141, successes at 111, 121, 131, 141 → `count() == 4`, `capacity() == 28`, `rate() == 0.1` (±1e-9), `etaTicks() == 240`, `perHour() == 600`.
  - `noEstimateBeforeTwoSuccesses` — one success → `rate() == 0`, `etaTicks() == -1`, `perHour() == 0`.
  - `rateWindowIsLast100Rolls` — 100 rolls without a success, then 100 rolls with a success on every 10th → `rate() == 0.1`.
  - `depletionEndsPillAndMarks` — `onTargetGone(130, A)` before `onTick(130)` → `endTick == 130`, `depleted`, `peek(130).depleted`, `cueTick() == 131`.
  - `otherTargetGoneIgnored` — `onTargetGone(130, B)` → the pill stays open.
  - `reclickDelayCounted` — depleted at 130, `onTargetClick(132, …, B, …)` → `peek(132).reclick == 2`, 2 pills, `cueTick() == -1`.
  - `twoTreesInARowKeepCountAndRate` — tree A: 30 rolls, 3 successes, falls; click B 2 ticks later; tree B: 30 rolls, 3 successes → `count() == 6`, `rate() == 0.1`, the second pill has `rolls == 30`, and the first is `depleted`.
  - `longPauseIsNotReclick` — click 12 ticks after a depletion → `reclick == 0`.
  - `sameTargetClickContinuesPill` — click A again at 115 → still 1 pill.
  - `animationStopEndsPill` — rolls to 120, none from 121 → after `onTick(123)` the `endTick == 121` and the pill is not `depleted`.
  - `fullInventoryReturnsTrueOnce` — baseline `{1515: 26}`, free 2; two successes → only the second `onTick` returns true; `full()`; the pill ends there; `cueTick() == -1`.
  - `fullAndDepletedSameTick` — the last log and `onTargetGone` on the same tick → `depleted` and `full()`, `cueTick() == -1`.
  - `powerdropMidGather` — after 10 logs, the inventory drops to `{1515: 5}`, free 23, with no XP while animating → `successes()` unchanged, the pill is still open, `count() == 5`, `capacity() == 28`, `!full()`.
  - `dropAfterFullClearsFull` — full, then all logs dropped → `!full()`. The next success returns false. Filling again returns true.
  - `bankMidGatherEndsPill` — `onBankClick(140)` mid-pill → `endTick == 140`, not `depleted`, `!peek(140).gatherBank`. The deposit (`{}`, 28) → `count() == 0`. The next click has `reclick == 0`.
  - `bankAfterFullMarksBank` — full, then `onBankClick` → `peek(t).gatherBank`.
  - `fishingSpotMoves` — Fishing, keys −5 and −7, raw trout 335; `onTargetGone(t, -5)` → `depleted`; a click on −7 at t + 3 → `reclick == 3`.
  - `unknownOptionStillGathers` — no click; Mining drop + `{440: 1}` at 150 → a pill with `key == 0` and `startTick == 149`; after rolls 150–152 → `showing(152)`, `skill() == "Mining"`.
  - `firemakingSuccessEndsPill` — baseline `{1515: 27}`, free 1; `onTargetClick(100, "Firemaking", 0, "Yew logs")`, animating 100–103, `[Firemaking 202]` with `{1515: 26}` at 103 → `endTick == 103`, not `depleted`, `cueTick() == 104`, `count() == 26`, `capacity() == -1`; a click at 105 → `reclick == 2`.
  - `firemakingOutOfLogsReturnsTrue` — the last log burnt → `onTick` returns true once.
  - `skillChangeResetsRate` — a Woodcutting rate, then a Mining click → `rate() == 0`.
  - `historyBounded` — 20 pills → `pills().size() == 16`.
  - `lingersAfterPillEnds` — a pill ends at 150 (last activity 150) → `showing(170)`, `!showing(171)`.
  - `resetClearsAll`.
- [ ] **Step 2:** Run `--tests '*GatherRunTest'`. Expected: compile failure / FAIL.
- [ ] **Step 3: Implement** to the contract and notes 2–6. Keep the roll window as two ring arrays of 100 (`rolled`, `succeeded`). Keep at most one unmatched increase (from the previous tick) and one success waiting for its item (for the next tick).
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Commit.** `feat: gathering tracker with rolls, depletion and estimate`

### Task 18b: Gathering view, detection and blink

**Files:**
- Create: `ui/modes/GatherView.java`
- Modify: `model/Mode.java`, `model/ModeDetector.java`, `model/BarState.java`, `ui/Palette.java`, `ui/SkillPainter.java`, `ui/BarPainter.java`, `SuperHistogramConfig.java`, `SuperHistogramPlugin.java`, `test/.../ui/BarRenderSamples.java`
- Test: `model/ModeDetectorTest.java`, `ui/modes/GatherViewTest.java`, `ui/BarRenderSmokeTest.java`, `ui/SampleStates.java`, `SuperHistogramPluginHandlersTest.java`

**Interfaces:**
- Consumes: `GatherRun` (18a), `XpRow` (17a), `SkillPainter.wasted/bank` (Task 16), `ReadoutPainter.right/compact/minSec`, `Pulse.tick/done`.
- Produces:
  - `Mode` gains `GATHERING`. `ModeDetector.skilling(boolean alching, boolean producing, boolean gathering)` gives ALCH, then PRODUCTION, then GATHERING, else null. The 2-argument form delegates with `false`.
  - `BarState` + builder gain `GatherRun gather` (null).
  - `Palette` adds `WC_PILL = #b5a06a`, `LOG = #c89a5a`, `LOG_END = #e8c58f`, `NOTCH = rgba(20,23,27,.45)` (all from gathering.html).
  - `SkillPainter`:
    - `axe(g, x, y, u, Color)`: a 1.6u × 10u handle at `x − 0.8u, y − 5u`, plus a head from `x + 0.8u, y − 5u`, 5.2u × 5u with 2u-radius rounded right corners;
    - `log(g, x, y, u)`: a `LOG` 10u × 5.2u round rect centred at x, with a `LOG_END` circle r 2.6u at `x + 4u`;
    - `stump(g, x, y, u)`: the mockup's trapezoid `M x−4,y+4 h8 l−1.2,−6 h−5.6 z` in `TEXT`, with a `DIM` ellipse rx 2.8u, ry 1.1u at `y − 2u`;
    - `item(g, x, y, u, Color)`: a disc r 2.8u;
    - `depleted(g, x, y, u)`: a `TEXT` ring r 3u, stroke 1.2u.
  - `final class GatherView implements ModeView`; `static String[] rightLines(BarState s)`; `static String[] headlineFallbacks(BarState s)` (`"Full · bank"` → `"Full"`); `static String noun(String skill)`; `static String eta(int ticks)` (`"~1:40"`).
  - Config: the `blinkWhenDone` description becomes "Flash the bar mint when an inventory finishes or fills, or the AFK logout warning fires".
  - Plugin:
    - fields `volatile GatherRun gather`, `volatile int latestFreeSlots = -1`, `int loadingTick = Integer.MIN_VALUE`;
    - `void handleInventory(Map<Integer,Integer> counts, int freeSlots)`. The 1-argument form keeps `latestFreeSlots`.
    - `void handleGatherClick(String skill, int key, String name)`, `void handleObjectDespawned(int sceneX, int sceneY)`;
    - `static String gatherSkill(MenuAction a, String option, String target)`;
    - `static int objectKey(int sceneX, int sceneY)` (`1 + sceneX * 512 + sceneY`), `static int npcKey(int index)` (`-(index + 1)`);
    - `@Subscribe onGameObjectDespawned`.

- [ ] **Step 1: Write failing tests.**
  - `ModeDetectorTest.skillingOrder`:
    - `skilling(true, true, true) == ALCH`;
    - `skilling(false, true, true) == PRODUCTION`;
    - `skilling(false, false, true) == GATHERING`;
    - `skilling(false, false, false) == null`;
    - the 2-argument results are unchanged;
    - `detect(false, true, GATHERING, LAZY_FLICK, GENERAL) == LAZY_FLICK`, and `detect(true, false, GATHERING, …) == AFK`.
  - `SampleStates.gathering(GatherPhase p, boolean timeToFinish, boolean wastedTicks)`, with `enum GatherPhase { CHOPPING, FELLED, FULL, EARLY }`. It drives a real `GatherRun` plus `XpTracker` (+175 Woodcutting per success, baseline at `NOW_TICK − 65`) as gathering.html:
    - baseline inventory at `NOW_TICK − 65`: `{1515: 6}`, free 22;
    - tree A: click `NOW_TICK − 64` (key 101, "Yew tree"); rolls `NOW_TICK − 62 … − 33`; successes on rolls 10, 20, 30 (`− 53, − 43, − 33`); falls at `NOW_TICK − 33`;
    - tree B: click `NOW_TICK − 31` (+2); rolls `NOW_TICK − 29 … NOW_TICK`; successes at `− 20, − 10, NOW_TICK`;
    - CHOPPING is the above: 12 logs, free 16, rate 0.1, ETA 160 ticks;
    - FELLED is CHOPPING plus `onTargetGone(NOW_TICK, 102)`;
    - FULL is CHOPPING with a baseline of `{1515: 22}`, free 6, so it is full at `NOW_TICK`;
    - EARLY is tree B only, with a single success at `NOW_TICK − 20`.
    - `gathering(GatherPhase p)` = `(p, true, true)`. `gatheringSkill(String skill, int itemId)` = CHOPPING with that skill and item. `firemaking()`: baseline `{1515: 27}`, free 1, at `NOW_TICK − 16`; clicks at `NOW_TICK − 15, − 10, − 5` (key 0, "Yew logs"); animating c..c+3; a success with −1 log at c+3.
  - `GatherViewTest`:
    - `rightLines`:
      - CHOPPING → `{"12/28", "full in ~1:40 · 600 logs/hr"}`;
      - CHOPPING with `timeToFinish` false → `{"12/28", "600 logs/hr"}`;
      - FULL → `{"Full · bank", "28/28 logs"}`;
      - EARLY → `{"7/28", "logs"}`;
      - `gatheringSkill("Mining", 440)` → the small line ends `" ore/hr"`; `gatheringSkill("Fishing", 335)` ends `" fish/hr"`;
      - `firemaking()` → `{"24 left", "done in ~1:00 · 1.5k fires/hr"}`;
      - a null `gather` → `{"", ""}`.
    - `eta`: 160 → `"~1:40"`, 96 → `"~1:00"`, 5 → `"~0:10"`.
    - `fallbacks` — FULL → `{"Full"}`; CHOPPING → `{}`.
  - `BarRenderSmokeTest`:
    - `gatheringRendersAtThreeSizes` — each GatherPhase and `firemaking()` at 1026 × 62, 1026 × 46 and 600 × 30, with no exception (the `Mode.values()` loops cover a null `gather`).
    - `pillHasWoodcuttingColour` — CHOPPING: `WC_PILL` at (x(NOW_TICK − 12) + 16u, 29u).
    - `rollNotchesDarkenPill` — CHOPPING at 62 px: the pixel at (x(NOW_TICK − 12), 29u) has every channel at least 20 below `WC_PILL`. At 600 × 30 (one row) the pixel at the same x, at `pillY + pillH/2`, matches `WC_PILL`.
    - `stumpMarksFall` — CHOPPING: `TEXT` pixels in x(NOW_TICK − 33) + 2u .. + 10u, y 40u–50u.
    - `reclickLabelOnlyWhenEnabled` — CHOPPING: `LOST` pixels in x(NOW_TICK − 33) + 13u .. x(NOW_TICK − 31), y 34u–42u; none with `wastedTicks` false.
    - `logBeforeXp` — CHOPPING: `LOG` pixels in x(NOW_TICK − 10) − 16u .. − 5u, y 6u–16u.
    - `noFinishFlagForEstimate` — CHOPPING: no opaque white in x(NOW_TICK + 1) .. x(NOW_TICK + 4), y 42u–60u.
    - `gatheringRightDoesNotOverlapLoginTime` — the same assertion as Phase 2, at 1026 × 62 and 720 × 62, for every phase.
  - `SuperHistogramPluginHandlersTest`. "Detect" means: one `handleTick`, `handleGatherClick("Woodcutting", objectKey(50, 50), "Yew tree")`, `me.getAnimation()` → 867, then 3 × `handleTick`.
    - `gatherSkillFromMenu`:
      - `(GAME_OBJECT_FIRST_OPTION, "Chop down", "<col=ffff>Yew tree")` → `"Woodcutting"`; `"Mine"` → `"Mining"`;
      - `(NPC_FIRST_OPTION, "Lure", …)` and `(NPC_SECOND_OPTION, "Bait", …)` → `"Fishing"`;
      - `(CC_OP, "Light", "<col=ff9040>Logs")`, `(WIDGET_TARGET_ON_WIDGET, "Use", "Tinderbox -> <col=ff9040>Logs")` and `(WIDGET_TARGET_ON_WIDGET, "Use", "Logs -> Tinderbox")` → `"Firemaking"`;
      - `(GAME_OBJECT_FIRST_OPTION, "Lure", …)`, `(WALK, "Walk here", null)` and a null option → null, without throwing.
    - `keysDistinct` — `objectKey(0, 0) > 0`, `npcKey(0) < 0`, `objectKey(1, 2) != objectKey(2, 1)`.
    - `gatheringDetectedAfterRolls` — after detect: `buildState().mode == GATHERING`, `modeName == "Woodcutting"`, `modeSub == "auto · Yew tree"`; with one `handleTick` instead of 3 → GENERAL.
    - `treeFallCuesNextTick` — metronome CUE_TICK, after detect: `handleObjectDespawned(50, 50)`, then `handleTick(now)` → `pulse.outlineAlpha(now) > 0.9`.
    - `despawnWhileLoadingIgnored` — after detect: `handleGameState(LOADING, …)`, `handleObjectDespawned(50, 50)`, `handleTick` → `gather.active()`.
    - `inventoryFullBlinks` — `handleInventory({1515: 26}, 2)` and a Woodcutting baseline, detect, then two successes (`handleInventory({1515: 27}, 1)` + `handleStat` +175, `handleTick`; then `{1515: 28}`, 0, the same). The `handleTick(now)` after the second success → `pulse.mintAlpha(now) > 0`. With `blinkWhenDone` false → 0.
    - `npcDespawnEndsFishing` — `handleGatherClick("Fishing", npcKey(7), "Rod Fishing spot")`, ticks with animation, `npc.getIndex()` → 7, `handleNpcDespawned(npc)`, `handleTick` → `gather.pills().get(0).depleted`.
    - `bankClickEndsGatherPill` — after detect, `handleBankClick()` → `!gather.active()`.
    - `logoutClearsGather` — `LOGIN_SCREEN` → `!gather.active()`, `latestFreeSlots == -1`.
- [ ] **Step 2:** Run `--tests '*ModeDetectorTest' --tests '*GatherViewTest' --tests '*BarRenderSmokeTest' --tests '*PluginHandlersTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** to gathering.html (62-px values × u):
  - **Pills** (from `s.gather.pills()`, those overlapping the visible ticks):
    - `x0 = x(start)`, `x1 = x(endTick == -1 ? nowTick + 1 : min(endTick, nowTick + 1))`;
    - fill a round rect from x0, `pillY(l)`, width `max(pillH, x1 − x0)`, height `pillH`, arc `pillH`. The colour is `WC_PILL` for Woodcutting, otherwise `Palette.skill(skill)`.
    - Axe (Woodcutting, `rows > 1`): at `ix = max(x0 + 10u, timelineX + 10u)`, `pillY + pillH/2`, in `PILL_LABEL_SOLID`, when `ix < x1 − 8u`.
    - Notches (`rows > 1`): for each `roll` tick t inside the pill, a 1.2u × (pillH − 6u) `NOTCH` round rect at `x(t) − 0.6u`, `pillY + 3u`, when `x(t) > ix + 6u` and `x(t) < x1`.
  - **Top row** (`rows > 1`):
    - for each `gathered > 0` tick (not Firemaking): `log` (Woodcutting) or `item(…, Palette.skill(skill))` at `(x(t) − 11u, xpY − 3.5u)`;
    - then `TimelinePainter.xp` and `TimelinePainter.hits`.
  - **Actions row** (`rows == 3`):
    - `depleted` ticks: `stump` (Woodcutting) or `depleted` at `(x(t) + 6u, 45u)`;
    - `reclick = n > 0` with `wastedTicks`: `SkillPainter.wasted(g, l, x(t − n) + 13u, x(t), n)`;
    - `gatherBank` ticks: `SkillPainter.bank(g, x(t), 46u, u, TEXT)`.
  - **Right:** `ReadoutPainter.right(g, l, s, lines[0], WHITE, lines[1], headlineFallbacks(s))`, with `rightLines` as in note 8:
    - Firemaking: `count + " left"`, then `["done in " + eta]` and `[compact(perHour) + " fires/hr"]`. When out of logs: `"Out of logs"` / `successes + " fires"`.
    - Otherwise: full → `"Full · bank"`; else `count + "/" + capacity` (or the count alone when the capacity is unknown). The small line is `["full in " + eta]` and `[compact(perHour) + " " + noun + "/hr"]`, joined by `" · "`; when both are missing, it is the noun.
  - **Plugin:**
    - `initModel` and LOGIN_SCREEN create `new GatherRun(timeline)` and set `latestFreeSlots = -1`. HOPPING keeps them. `handleGameState(LOADING)` sets `loadingTick = tick`.
    - `onItemContainerChanged` (inventory): `free = 28 − (items with id > 0)`, then `handleInventory(counts, free)`.
    - `onMenuOptionClicked`: `gs = gatherSkill(action, option, target)`. When non-null:
      - the key is `objectKey(param0, param1)` for `GAME_OBJECT_*`, `npcKey(getId())` for `NPC_*`, otherwise 0;
      - the name is the tag-stripped target, or for `"Use"` the side of `" -> "` that is not `"Tinderbox"`;
      - then call `handleGatherClick(gs, key, name)`.
    - `handleBankClick` also calls `gather.onBankClick(tick)`. `handleNpcDespawned` also calls `gather.onTargetGone(tick, npcKey(index))`.
    - `onGameObjectDespawned`: `p = getGameObject().getSceneMinLocation()` → `handleObjectDespawned(p.getX(), p.getY())`, which calls `gather.onTargetGone(tick, objectKey(x, y))` unless `tick == loadingTick`.
    - `handleTick`: move the `me` lookup above the skill trackers. `animating = me != null && me.getAnimation() != -1`. Then `filled = gather.onTick(tick − 1, animating, prevXp, latestInventory, latestFreeSlots)`, where `prevXp` is `prev.xp` (empty when `prev` is null, as today); if `filled && blinkWhenDone` → `pulse.done(nowMs)`. The cue adds `gather.cueTick() == tick`.
    - `buildState`: `skilling = ModeDetector.skilling(alch, run, gather.showing(t))`. GATHERING → name `gather.skill()`, sub `"auto"` + (`" · " + targetName` when it is known). Set `gather`. Register `GatherView` in `BarPainter`.
  - `BarRenderSamples.writeGatheringContactSheet` — each GatherPhase and `firemaking()` at 1026 × 62, 720 × 62 and 400 × 36 (same pattern as `writeProductionContactSheet`), to compare with gathering.html.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Build.** Expected: `BUILD SUCCESSFUL`, and all Phase 1–3 tests pass.
- [ ] **Step 6: Manual check in the dev client** (chop yews, mine iron, fly-fish, burn logs). Note results:
  - "Woodcutting · auto · Yew tree" appears within 2 ticks of the chop animation starting;
  - there is one notch per animating tick, and a log + XP on each log;
  - the tree falling draws a stump, and re-clicking 2 ticks late shows "+2". If no stump ever appears, the despawn's scene-min tile does not equal the click's `param0/param1`: note both;
  - iron rocks deplete after each ore, with the ring marker;
  - a moving fishing spot ends the pill with a marker. If it does not, `NpcDespawned` did not fire: note it (the pill then ends 3 ticks after the animation stops);
  - the "~" estimate is within about 20 % of the real time to full;
  - the bar blinks mint once when the inventory fills, and never on a tree fall;
  - with the metronome on, the outline goes bright on the tick after a tree falls;
  - powerdropping or banking mid-inventory gives no blink and no crash;
  - firemaking counts logs down and blinks when the last one is lit;
  - walking across a region border mid-chop does not draw a false stump;
  - the login time is still at the far right.
- [ ] **Step 7: Commit.** `feat: gathering view with rolls, depletion and estimate`

### Task 19a: Agility course and lap tracker

**Files:**
- Create: `model/skill/AgilityCourses.java`, `model/skill/AgilityCourse.java`
- Modify: `model/TickRecord.java`
- Test: `model/skill/AgilityCourseTest.java`

**Interfaces:**
- Consumes: `Timeline`, `TickRecord`, `XpDrop` (Task 2).
- Produces:
  - `TickRecord` gains `public boolean mark; public int agilityLost /* +N on an obstacle click, 0 none */;`.
  - `final class AgilityCourses { static final class Course { final String id, name; final int region; final int[][] ends /* {x, y, plane} */; } static Course byRegion(int regionId) /* null off course */; static Course byId(String id); static boolean isLapEnd(String id, int x, int y, int plane); static List<Course> all(); }`. The table (from RuneLite 1.13.6 `Courses.java`, BSD-2; credit it in the class comment):

    | id | name | region | lap-end tiles (plane 0) |
    |---|---|---|---|
    | GNOME | Gnome | 9781 | (2484,3437), (2487,3437) |
    | DRAYNOR | Draynor | 12338 | (3103,3261) |
    | AL_KHARID | Al Kharid | 13105 | (3299,3194) |
    | VARROCK | Varrock | 12853 | (3236,3417) |
    | BARBARIAN | Barbarian | 10039 | (2543,3553) |
    | CANIFIS | Canifis | 13878 | (3510,3485) |
    | FALADOR | Falador | 12084 | (3029,3332), (3029,3333), (3029,3334), (3029,3335) |
    | WILDERNESS | Wilderness | 11837 | (2993,3933), (2994,3933), (2995,3933) |
    | SEERS | Seers' | 10806 | (2704,3464) |
    | POLLNIVNEACH | Pollnivneach | 13358 | (3363,2998) |
    | RELLEKKA | Rellekka | 10553 | (2653,3676) |
    | ARDOUGNE | Ardougne | 10547 | (2668,3297) |

  - `final class AgilityCourse { static final int LOST_MAX = 10, STALE = 30, LINGER = 50, MARK_WINDOW = 2, REVERT_WINDOW = 2, HISTORY = 64, LAPS_KEPT = 16; static final class Obstacle { final int objectId, startTick, endTick /* landing, -1 pending */, number /* 0 unknown */, lap; final boolean failed; } static final class LapStart { final int tick, number; } AgilityCourse(Timeline t); void onCourse(String courseId /* null = not on a course */); void onObstacleClick(int tick, int objectId); boolean onTick(int tick, List<XpDrop> drops, boolean hitOnMe, boolean atLapEnd); void onMark(int tick); void loadBest(String courseId, int ticks); boolean takeBestChanged(); String course(); boolean showing(int tick); boolean pending(); int lapStartTick() /* -1 between laps */; int lapNumber(); int lastLapTicks() /* -1 */; int previousLapTicks() /* -1 */; boolean lastLapWasBest(); int bestTicks() /* -1 */; int lapLost(); int slowestAfter() /* 0 none */; int readyTick() /* -1 */; List<Obstacle> obstacles(); List<LapStart> lapStarts(); void rebind(Timeline t); void reset(); }`
    - `onCourse(null)` keeps everything. A different course ID switches: the open lap, pending obstacle, last landing, provisional lap and last/previous lap times clear. Best laps, learned orders and gap stats are kept per course. The landing count restarts.
    - `onObstacleClick` is ignored with no course, and when the ID equals the pending obstacle's. A different ID replaces the pending one.
    - The gap rule (note 12): `n = tick − lastLanding` when a landing is known, it was not a lap end, and no failure came since. For 1..10 it sets `peek(tick).agilityLost = n`, adds n to `lapLost`, and adds n to the gap stats of the landed obstacle's number. For n ≤ 0 it adds 0 to the stats. For n > 10 it does nothing.
    - With no open lap, the click starts lap `lapNumber + 1` (`lapStarts` gets `(tick, number)`). The lap is full when it follows a lap end (note 11), and it confirms or discards a provisional lap.
    - The number is the learned index + 1. When no order is learned, it is the position in the lap. When learned and the ID is not in the order, it is 0.
    - `onTick(t)`:
      - An Agility drop with a pending obstacle lands it (`endTick = t`). With `atLapEnd`, the open lap closes: `lastLapTicks = t − lapStart`, `afterLapEnd = true`, and `onTick` returns true. A full lap updates the best and `lastLapWasBest` (strictly faster), and learns the order on the first full lap. A provisional lap is stored for confirmation.
      - An Agility drop with no pending obstacle counts only when `atLapEnd`.
      - A drop within 2 ticks of a failure turns the failure into a landing.
      - `hitOnMe` with a pending obstacle and no drop fails it (`endTick = t`, `failed`).
      - A pending obstacle older than 30 ticks is removed.
      - A mark from `onMark(m)` is kept when a landing lies in `[m − 2, m]`; it is resolved at `onTick(m)`.
    - `showing(t)`: a course is set, there have been ≥ 2 landings on it since the switch or rebind, and either an obstacle is pending or `t − lastLanding ≤ 50`.
    - `readyTick()` = last landing + 1 when nothing is pending.
    - `lapLost()` is for the open lap, or for the last lap between laps.
    - `slowestAfter()` needs one completed full lap (note 12).
    - `rebind`: like a course switch (the course ID is kept), plus it clears `obstacles` and `lapStarts`.
    - `loadBest` keeps the lower value, ignores `ticks ≤ 0`, and does not set `takeBestChanged`.
    - `takeBestChanged()` returns true once after each new best (including a confirmed provisional lap).

- [ ] **Step 1: Write failing tests** (`AgilityCourseTest`). Unless stated: `onCourse("SEERS")`; `SEERS_IDS = {14927, 14928, 14932, 14929, 14930, 14931}`; `DUR = {7, 9, 6, 8, 11, 7}`; `XP = {30, 40, 25, 35, 60, 30}`. The helper `int lap(int start, int... gaps)` clicks obstacle k at c, calls `onTick` every tick, lands it at `c + DUR[k]` with `XpDrop("Agility", XP[k])` (`atLapEnd` on k = 5), and sets the next `c = landing + gaps[k]`. It returns the final landing tick. Laps follow each other after a 3-tick walk.
  - `courseTable`:
    - `byRegion(10806)` → id `"SEERS"`, name `"Seers'"`; `byRegion(9781).name == "Gnome"`; `byRegion(12850) == null`;
    - `isLapEnd("SEERS", 2704, 3464, 0)` is true; `(2704, 3464, 2)` and `(2705, 3464, 0)` are false; `isLapEnd("GNOME", 2487, 3437, 0)` is true;
    - `all().size() == 12`;
    - every end tile lies in its course's region: `((x >> 6) << 8 | (y >> 6)) == region`.
  - `obstaclePillFromClickToLanding` — click 14927 at 100, landing 107 → one obstacle with start 100, end 107, number 1, not failed; `readyTick() == 108`.
  - `pendingUntilXp` — click at 100, ticks to 104 → `endTick == -1`, `pending()`, `readyTick() == -1`.
  - `clickWithoutCourseIgnored` — a fresh tracker with no `onCourse` → `obstacles().isEmpty()`.
  - `spamClickKeepsFirst` — 14927 at 100 and 101 → start 100.
  - `newClickReplacesPending` — 14927 at 100, 14928 at 102 → one obstacle, ID 14928, start 102.
  - `staleClickDropped` — click at 100, nothing to 131 → `obstacles().isEmpty()`, `!pending()`.
  - `ticksLostSummed` — `lap(100, 1, 0, 3, 0, 2)` → `peek(#4 click).agilityLost == 3`; at the lap end `lapLost() == 6`.
  - `lostOverTenIsPause` — gaps `(12, 0, 0, 0, 0)` → `lapLost() == 0`, and the #2 click's `agilityLost == 0`.
  - `gapAfterLapEndNotCounted` — the next lap's first click 3 ticks after the lap end → its `agilityLost == 0`.
  - `lapTimeAndBest`:
    - lap 1 `lap(100, 1, 0, 1, 3, 0)` (53 ticks): its last `onTick` returns true; `lastLapTicks() == 53`; `bestTicks() == -1` (provisional); `!lastLapWasBest()`;
    - at lap 2's first click (14927): `bestTicks() == 53`, `takeBestChanged()` true then false;
    - lap 2 with gaps `(0, 1, 0, 2, 1)` (52) → `lastLapTicks() == 52`, `previousLapTicks() == 53`, `bestTicks() == 52`, `lastLapWasBest()`, `takeBestChanged()`.
  - `lapEndReturnsTrueOnce` — over a whole lap, only the lap-end `onTick` returns true.
  - `firstLapPartialNotBest` — the session starts at 14932 (obstacles 3–6, ending at the lap end); lap 2 starts with 14927 → `bestTicks() == -1`. After lap 2 → `bestTicks() == lap 2`. Lap 3's obstacles are numbered 1..6. `slowestAfter()` ignores lap 1's gaps.
  - `fallingOffNoXp` — after the order is learned: the #3 click at t, then `onTick(t + 4, [], true, false)` → the obstacle is `failed`, `endTick == t + 4`, `number == 3`; `lapStartTick()` unchanged; `lapLost()` unchanged. Re-clicking 14932 later gives number 3 and `agilityLost == 0`.
  - `hitThenXpIsLanding` — a hit at t, then the Agility drop at t + 1 → not failed, `endTick == t + 1`.
  - `hitWithoutPendingIgnored`.
  - `slowestObstacle` — laps with gaps `(1, 0, 1, 3, 0)`, `(0, 1, 0, 2, 2)`, then a third with `(0, 0, 1, 3)` → `slowestAfter() == 4`. All gaps 0 → 0.
  - `landingWithoutClickCountsForLapEnd` — the final obstacle's click is not reported, but its drop has `atLapEnd` → `onTick` returns true and `lastLapTicks()` is set.
  - `changingCoursesResetsLap` — after two laps (best 52), mid lap 3 `onCourse("CANIFIS")` → `course() == "CANIFIS"`, `lapStartTick() == -1`, `!pending()`, `bestTicks() == -1`. Back with `onCourse("SEERS")` → `bestTicks() == 52`.
  - `nullCourseKeepsLap` — `onCourse(null)` mid-lap → `lapStartTick()` unchanged.
  - `markOfGraceOnLanding` — `onMark(107)` with a landing at 107 → `peek(107).mark`. `onMark(109)` → `peek(109).mark`.
  - `markWithoutRecentLandingIgnored` — `onMark` 5 ticks after the last landing → no mark.
  - `markOffCourseIgnored` — no course → no mark.
  - `loggingOutMidLap` — after 2 laps (order learned, best 52), mid lap 3 `rebind(new Timeline(512))` → `lapStartTick() == -1`, `obstacles().isEmpty()`, `bestTicks() == 52`, `!showing(t)`. The next click on 14929 is number 4.
  - `firstLapAfterLoginIsProvisional` — after `rebind`, a faster full lap from 14927 → `bestTicks()` unchanged until the next lap starts with 14927.
  - `showingNeedsTwoLandingsAndLingers` — one landing → `!showing`. Second landing at 116 → `showing(116)`, `showing(166)`, `!showing(167)`.
  - `loadBestKeepsLower` — `loadBest("SEERS", 80)`, then `90` → 80; `loadBest("SEERS", 0)` ignored; `takeBestChanged()` false.
  - `historyBounded` — 20 laps → `obstacles().size() == 64`, `lapStarts().size() == 16`.
  - `resetClearsAll` — including bests and learned orders.
- [ ] **Step 2:** Run `--tests '*AgilityCourseTest'`. Expected: compile failure / FAIL.
- [ ] **Step 3: Implement** to the contract and notes 10–14. Per-course state lives in a `Map<String, CourseStats>`: `best`, `order` (List<Integer>), `gapSum[]`, `gapCount[]`.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Commit.** `feat: agility course table and lap tracker`

### Task 19b: Agility view, narrow ticks, detection and best-lap persistence

**Files:**
- Create: `ui/modes/AgilityView.java`, `model/skill/LapCompare.java`
- Modify: `model/Mode.java`, `model/ModeDetector.java`, `model/BarState.java`, `ui/BarLayout.java`, `ui/BarPainter.java`, `ui/Palette.java`, `ui/SkillPainter.java`, `SuperHistogramConfig.java`, `SuperHistogramPlugin.java`, `test/.../ui/BarRenderSamples.java`
- Test: `ui/BarLayoutTest.java`, `model/ModeDetectorTest.java`, `ui/modes/AgilityViewTest.java`, `ui/BarRenderSmokeTest.java`, `ui/SampleStates.java`, `SuperHistogramPluginHandlersTest.java`

**Interfaces:**
- Consumes: `AgilityCourse`, `AgilityCourses` (19a), `ModeDetector.skilling` (18b), `XpRow` (17a), `SkillPainter.wasted` (Task 16), `ReadoutPainter.right`.
- Produces:
  - `Mode` gains `AGILITY`. `ModeDetector.skilling(boolean alching, boolean producing, boolean agility, boolean gathering)` gives ALCH > PRODUCTION > AGILITY > GATHERING (note 17). The 3-argument form delegates with `agility = false`.
  - `BarLayout withTickWidth(float tw)`: a copy with `tickWidth = tw` and `visibleTicks = floor(timelineW / tw)`. All other fields are equal, and the original is unchanged.
  - `BarPainter.timelineLayout(BarLayout l, BarState s)`: `s != null && s.mode == AGILITY ? l.withTickWidth(AgilityView.tickWidth(l, s.agility)) : l` (the same object). `paintContent` uses it for `ruler`, `paintTimeline` and `nowLine`. The left and right readouts and the login time keep `l`.
  - `AgilityView.tickWidth(BarLayout l, AgilityCourse a)`: note 15. `lapRef = max(best, lastLap)`. With a null `a` or `lapRef ≤ 0` it is `14u`.
  - `Palette.LAP_LINE` = white 30 %. `SkillPainter.mark(g, cx, cy, u)`: the mockup's `MINT` diamond (cx, cy − 4.5u) → (cx + 4u, cy) → (cx, cy + 4.5u) → (cx − 4u, cy).
  - `enum LapCompare { BEST, LAST }` (toString "Best" / "Last lap").
  - `BarState` + builder gain `AgilityCourse agility` (null) and `LapCompare lapCompare` (BEST; a null keeps the default).
  - Config:
    - `lapCompare` (BEST, position 26, "Agility: compare lap to", "Compare this lap's time with your best lap or your last lap");
    - the `blinkWhenDone` description becomes "Flash the bar mint when an inventory finishes or fills, an agility lap ends, or the AFK logout warning fires".
  - `final class AgilityView implements ModeView`; `static String lapTime(int ticks)` (`"0:52.8"`); `static String[] rightLines(BarState s)`; `static Color rightColor(BarState s)` (MINT between laps when `lastLapWasBest`, else WHITE).
  - Plugin:
    - `static final String CONFIG_GROUP = "superhistogram"`; `@Inject ConfigManager configManager`;
    - fields `volatile AgilityCourse agility`, `volatile String regionCourse`, `int lapEndTick = -1`;
    - `void handleObstacleClick(int objectId)`, `void handleItemSpawned(int itemId, WorldPoint where)`;
    - `static boolean isObstacleAction(MenuAction a)` (`GAME_OBJECT_FIRST_OPTION … FIFTH_OPTION`);
    - `@Subscribe onItemSpawned`.

- [ ] **Step 1: Write failing tests.**
  - `BarLayoutTest.withTickWidthKeepsGeometry` — `of(1026, 62).withTickWidth(14f)`: `tickWidth == 14`, `visibleTicks == 49`, and `timelineX`, `timelineW`, `rows`, `u` are equal. The original's `tickWidth` is still 32.
  - `ModeDetectorTest.agilityOrder`:
    - `skilling(false, false, true, true) == AGILITY`;
    - `skilling(false, true, true, true) == PRODUCTION`;
    - `skilling(true, false, true, false) == ALCH`;
    - `skilling(false, false, false, true) == GATHERING`;
    - the 3-argument results are unchanged.
  - `SampleStates.agility(AgilityPhase p, LapCompare c, boolean wastedTicks)`, with `enum AgilityPhase { MID_LAP, LAP_DONE, NEW_BEST }`. It drives a real `AgilityCourse` on SEERS like the 19a helper, plus an `XpTracker`:
    - lap 1 gaps `(1, 0, 1, 3, 0)` = 53 ticks;
    - a 3-tick walk;
    - lap 2 gaps `(0, 1, 0, 2, 2)` = 53;
    - a 3-tick walk;
    - lap 3.
    - MID_LAP: lap 3 starts at `S3 = NOW_TICK − 34`, gaps `(0, 0, 1, 3)`; #5 is clicked at `NOW_TICK` (pending); `loadBest("SEERS", 50)`.
    - LAP_DONE: lap 3 has gaps `(0, 0, 1, 3, 0)` (52) and ends at `NOW_TICK`; `loadBest` 50.
    - NEW_BEST: as LAP_DONE with no `loadBest`.
    - A mark of grace is on lap 3's #2 landing (`S3 + 16`).
    - `agility(p)` = `(p, BEST, true)`. `S3` is exposed as `lapThreeStart(p)`.
  - `AgilityViewTest`:
    - `lapTime` — 88 → `"0:52.8"`, 34 → `"0:20.4"`, 100 → `"1:00.0"`, 0 → `"0:00.0"`.
    - `rightLines`:
      - MID_LAP → `{"0:20.4", "best 0:30.0 · lost 4t · slowest after #4"}`;
      - MID_LAP with LAST → `{"0:20.4", "last 0:31.8 · lost 4t · slowest after #4"}`;
      - MID_LAP with `wastedTicks` false → `{"0:20.4", "best 0:30.0 · slowest after #4"}`;
      - LAP_DONE → `{"0:31.2", "best 0:30.0 · lost 4t · slowest after #4"}` with `rightColor == WHITE`;
      - LAP_DONE with LAST → the small line starts `"last 0:31.8"` (the lap before);
      - NEW_BEST → `{"0:31.2", "best 0:31.2 · lost 4t · slowest after #4"}` with `rightColor == MINT`;
      - a null `agility` → `{"", ""}`.
    - `tickWidth`:
      - no laps → 14 at 1026 × 62, 7 at 1026 × 31;
      - MID_LAP (lapRef 53) → `688f / 59` (±0.01);
      - a 200-tick best → 6 at 62 px;
      - at 1026 × 22 → at least 4.
  - `BarRenderSmokeTest` (positions use `x(t)` with `BarPainter.timelineLayout(l, s)`; `tickX` changes to use it, and other modes are unaffected):
    - `agilityRendersAtThreeSizes` — each phase at 1026 × 62, 1026 × 46 and 600 × 30.
    - `otherModesKeepTickWidth` — `timelineLayout(l, cooking(RUNNING)) == l`.
    - `obstaclePillIsAgilityBlue` — MID_LAP: `Palette.skill("Agility")` at (x(S3 + 3) + tw/2, 29u).
    - `obstacleNumberDrawn` — MID_LAP: a pixel with every channel < 60 in x(S3) + 7u .. + 13u, y 25u–33u.
    - `lapStartLine` — MID_LAP: the pixel at (x(S3) − 2u, 12u) has every channel ≥ 20 above the ground. `DIM` pixels ("lap 3") in x(S3) + 2u .. + 30u, y 51u–60u at 62 px.
    - `lostLabelOnlyWhenEnabled` — MID_LAP: `LOST` pixels in x(S3 + 31) .. x(NOW_TICK), y 34u–42u; none with `wastedTicks` false.
    - `markOfGraceIsMint` — MID_LAP: `MINT` pixels within 3u of (x(S3 + 16) − 9u, xpY − 3.5u).
    - `agilityRightDoesNotOverlapLoginTime` — the same assertion as Phase 2, at 1026 × 62 and 720 × 62, for every phase.
  - `SuperHistogramPluginHandlersTest`. "On course" means `at(2710, 3470)` (Seers' region 10806). "Obstacle k" means `handleObstacleClick(SEERS_IDS[k])`, `DUR[k]` × `handleTick`, `handleStat("Agility", base + …)`, then one `handleTick` (which processes the landing). An Agility baseline stat comes first. `plugin.configManager = mock(ConfigManager.class)`.
    - `isObstacleActionMatches` — `GAME_OBJECT_FIRST_OPTION … FIFTH` are true; `NPC_FIRST_OPTION`, `WALK` and null are false.
    - `agilityDetectedOnCourse` — on course, obstacles 0 and 1 → `buildState().mode == AGILITY`, `modeName == "Agility"`, `modeSub == "auto · Seers'"`; after obstacle 0 only → GENERAL.
    - `obstacleClickIgnoredOffCourse` — `at(3200, 3200)`, a tick, `handleObstacleClick(14927)` → `agility.obstacles().isEmpty()`.
    - `lapEndBlinksOnlyAtEndTile` — on course, obstacles 0–4; obstacle 5 lands with `at(2704, 3464)` before its `handleStat`, then `handleTick(now)` → `pulse.mintAlpha(now) > 0`. The earlier landings did not blink. With `blinkWhenDone` false → 0.
    - `cueOnReadyTick` — metronome CUE_TICK, after detection: `pulse.outlineAlpha` > 0.9 just after the `handleTick` that processes a landing (its tick equals `agility.readyTick()`).
    - `fallDamageIsNotCombat` — agility showing, `handleHit(me, DAMAGE_ME, 3)` → `mode == AGILITY`. With agility not showing, the same hit → LAZY_FLICK (unchanged behaviour).
    - `bestLapLoadedFromProfile` — `getRSProfileConfiguration("superhistogram", "bestLap.SEERS", Integer.class)` → 80; on course, one `handleTick` → `agility.bestTicks() == 80`. A null return leaves −1, without throwing.
    - `newBestSavedToProfile` — a full lap from obstacle 0 (ending on the end tile), then lap 2's first click (14927) and a `handleTick` → `verify(configManager).setRSProfileConfiguration("superhistogram", "bestLap.SEERS", lap1Ticks)`.
    - `markSpawnOnCourse` — on a landing tick, `handleItemSpawned(ItemID.GRACE, new WorldPoint(2706, 3466, 2))`, then `handleTick` → `timeline.peek(t).mark`. At `(3200, 3200, 0)`, with another item ID, or on a LOADING tick → no mark.
    - `logoutMidLapKeepsBest` — `LOGIN_SCREEN` mid-lap → `agility.lapStartTick() == -1` and `bestTicks()` unchanged; `regionCourse == null`.
- [ ] **Step 2:** Run `--tests '*BarLayoutTest' --tests '*ModeDetectorTest' --tests '*AgilityViewTest' --tests '*BarRenderSmokeTest' --tests '*PluginHandlersTest'`. Expected: FAIL.
- [ ] **Step 3: Implement** to agility.html (62-px values × u, ticks from `timelineLayout`):
  - **Lap lines:** for each `LapStart` in view:
    - `lx = x(tick) − 2u`; a 1px-wide `LAP_LINE` rect at `lx − 0.5`, y 8u, height 48u (`rows == 3`). With fewer rows it spans from `pillY − 4u` to `pillY + pillH + 4u`.
    - Label `"lap " + number`, bold 10u, `DIM`, tracking 0.04, at `(lx + 4u, 59u)` (`rows == 3` only).
  - **Obstacles:**
    - `x1 = x(start)`, `x2 = x(endTick == -1 ? nowTick + 1 : min(endTick, nowTick + 1))`;
    - fill a round rect from `x1 + 1`, `pillY`, width `max(pillH, x2 − x1 − 2)`, arc `pillH`, in `Palette.skill("Agility")`. A failed obstacle uses `withAlpha(…, FAINT_ALPHA)`.
    - Number (`rows > 1`, `number > 0`): bold 10u, `PILL_LABEL_SOLID`, centred on `nx = x1 + 10u`, baseline `pillY + pillH/2 + 4u`, when `nx < x2 − 6u` and `nx > timelineX`.
  - **Top row** (`rows > 1`): `TimelinePainter.xp`; for each `mark` tick `SkillPainter.mark(g, x(t) − 9u, xpY − 3.5u, u)`; then `TimelinePainter.hits` (falls show their damage).
  - **Actions row** (`rows == 3`): for `agilityLost = n > 0` with `wastedTicks`: `SkillPainter.wasted(g, tl, x(t − n) + 1, x(t) − 1, n)`.
  - **Right:** `ReadoutPainter.right(g, l, s, lines[0], rightColor(s), lines[1])`.
    - The headline is `lapTime(nowTick − lapStartTick)` while a lap is open, else `lapTime(lastLapTicks)`, else `""`.
    - The small line joins, with `" · "`:
      - BEST → `"best " + lapTime(best)` (if best ≥ 0); LAST → `"last " + lapTime(…)`, using `lastLapTicks` while a lap is open and `previousLapTicks` between laps (if ≥ 0);
      - `"lost " + lapLost() + "t"` (with `wastedTicks`);
      - `"slowest after #" + k` (k > 0).
  - **Plugin:**
    - `initModel` creates `new AgilityCourse(timeline)`. LOGIN_SCREEN calls `agility.rebind(timeline)` and sets `lapEndTick = -1`, `regionCourse = null`. HOPPING keeps them.
    - `handleTick`, after the `me` lookup:
      - `c = here == null ? null : AgilityCourses.byRegion(here.getRegionID())`, and `regionCourse = c == null ? null : c.id`;
      - when `c != null` and `!c.id.equals(agility.course())`: `agility.onCourse(c.id)`, then `Integer b = configManager.getRSProfileConfiguration(CONFIG_GROUP, "bestLap." + c.id, Integer.class)`, and if it is non-null, `agility.loadBest(c.id, b)`;
      - then `lapDone = agility.onTick(tick − 1, prevXp, prev != null && !prev.hits.isEmpty(), lapEndTick == tick − 1)`; blink when `lapDone && blinkWhenDone`;
      - when `agility.takeBestChanged()` → `configManager.setRSProfileConfiguration(CONFIG_GROUP, "bestLap." + agility.course(), agility.bestTicks())`;
      - the cue adds `agility.showing(tick) && agility.readyTick() == tick`.
      - A null `configManager` (tests that do not set it) skips the load and save.
    - `handleStat`: for `"Agility"`, when the local player's location satisfies `AgilityCourses.isLapEnd(agility.course(), x, y, plane)`, set `lapEndTick = tick`. Then continue as today.
    - `onMenuOptionClicked`: `isObstacleAction(action) && !isBankClick(option, target) && gatherSkill(...) == null` → `handleObstacleClick(e.getId())`. That calls `agility.onObstacleClick(tick, id)` only when `regionCourse != null`.
    - `onItemSpawned` → `handleItemSpawned(e.getItem().getId(), e.getTile().getWorldLocation())`. It calls `agility.onMark(tick)` when `itemId == ItemID.GRACE`, `tick != loadingTick`, and `byRegion(where.getRegionID())` is the current course.
    - `buildState`:
      - `agilityOn = agility.showing(t)`; `combat` uses `AttackTimer.NEVER` in place of `lastHitTick` when `agilityOn` (note 13);
      - `skilling = ModeDetector.skilling(alch, run, agilityOn, gather)`;
      - AGILITY → name `"Agility"`, sub `"auto · " + AgilityCourses.byId(course).name`;
      - set `agility` and `lapCompare`. Register `AgilityView`.
  - `BarRenderSamples.writeAgilityContactSheet` — each AgilityPhase at 1026 × 62, 720 × 62 and 400 × 36, to compare with agility.html.
- [ ] **Step 4:** Run the tests. Expected: PASS.
- [ ] **Step 5: Build.** Expected: `BUILD SUCCESSFUL`, and all Phase 1–4 tests pass.
- [ ] **Step 6: Manual check in the dev client** (Seers' rooftop, then Canifis; one Gnome lap). Note results:
  - "Agility · auto · Seers'" appears on the second obstacle;
  - the pills are numbered 1–6 from lap 2 on, and stay numbered correctly after a fall;
  - a whole lap fits on the bar at 1026 px after the first lap;
  - "+N" matches the ticks you stood still, and nothing is drawn for the walk back to the start;
  - the lap-end Agility XP arrives while you stand on the end tile, so the lap closes and the bar blinks once per lap, not per obstacle. If the lap never closes, note the location reported at the final XP;
  - with the metronome on, the outline goes bright on the tick after each landing;
  - a mark of grace shows the mint diamond on the landing where it appeared; marks already on the roof when you arrive are not drawn;
  - a fall shows a faint pill and the bar does not switch to combat;
  - the best lap survives a client restart, and "compare to Last lap" switches the small line;
  - moving to Canifis switches the name and best;
  - logging out mid-lap and back in gives no crash and keeps the best;
  - the login time stays at the far right.
- [ ] **Step 7: Commit.** `feat: agility view with lap times, best lap and narrow ticks`

**Phase 4 done:** woodcutting, mining, fishing and firemaking with roll notches, depletion "+N" and an estimated time to full. Agility laps with numbered obstacles, ticks lost, the slowest obstacle and a saved best lap, with the whole lap on the bar.

---

## Phase 5 — Finish

### Task 20: Full settings panel
Bring `SuperHistogramConfig` to match `design/mockups/settings-panel.html`: sections Bar / Feedback / Combat / XP & gold / Skilling / Colours, with the defaults there. Add `mode` (AUTO or a pinned mode) and colour overrides.

### Task 21: Icons + Plugin Hub
- Replace the rough icons with one consistent set (pot, axe, log, food, sword, flag, coin, mark of grace, bank) as small vector paths in `ui/Icons.java`.
- Check the Plugin Hub submission rules: no reflection, a bundled-font licence, and no network.
- Add `icon.png` and a README with screenshots.
