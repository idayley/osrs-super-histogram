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
