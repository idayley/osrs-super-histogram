# One Timeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The bar draws one timeline with every fact in fixed lanes in every mode; modes become lenses that only dim lanes, add helpers and set the readouts.

**Architecture:** A new `BaseTimeline` paints five lanes (top, doing, prayer, inputs, ruler) from `BarState`, each lane under its own alpha. The nine `ModeView`s become `Lens`es: an emphasis (alpha per lane), an overlay (mode helpers) and the right readout (unchanged code). `BarPainter` always paints base → overlay → now line, and tweens emphasis over 300 ms on a mode change instead of fading the whole content.

**Tech Stack:** Java 11, RuneLite client 1.13.6, JUnit 4, Gradle (`JAVA_HOME=$(brew --prefix openjdk@11) ./gradlew build --offline -q </dev/null`).

**Spec:** `design/spec.md` (Grammar, Resizing, Modes) and mockup `design/mockups/one-timeline.html`.

## Global Constraints

- Switching mode never moves, hides or re-scales anything on the timeline. Tick width is `max(18, 32·u)` in every mode (agility no longer zooms).
- Lanes at a 62-px bar (u = 1), in bar px: top 4–18, doing 20–31, prayer 33–44, inputs 46–53, ruler 55–58. Everything scales by u.
- Height < 56: no inputs lane; doing and prayer split the middle band (pillY..pillY+pillH) in half. Height < 40: one row, doing pill only with prayer as a 3u strip under it; hits as notches.
- Rules boundary from the spec still holds (no prediction, enemy beats only from the hotkey).
- Smooth scrolling, the now-line % setting, click-timed prayer edges and prayer points labels keep working as now.
- Colours only from `Palette`; text only through `Fonts` (Tiny5).
- Never run a command that reads stdin; always pass `</dev/null` to gradle.

## Review Focus

1. A mode switch mid-fight (lazy flick ↔ general ↔ 1-tick): pixels of the base lanes are identical across lenses at full emphasis (test pins it).
2. Overlapping facts in the doing lane (cooking animation + production run, alch cast + generic action, running while fighting): exactly one pill per tick range, the specific one wins.
3. Small and Large bar sizes (40 and 80 px tall) and chat width 519: no lane overlaps, labels still skip when they don't fit.
4. A lens whose tracker is null (no agility course, no run yet): overlay and readout draw nothing, no exception.
5. Hits on you crossing lanes: the hit line spans top..prayer lane bottom and never covers the inputs lane.

---

### Task 1: Lane geometry

**Files:** Modify `src/main/java/com/superhistogram/ui/BarLayout.java`; Test `src/test/java/com/superhistogram/ui/BarLayoutTest.java`

**Interfaces — Produces:** `public enum Lane { TOP, DOING, PRAYER, INPUTS, RULER }` in `ui/Lane.java`; on `BarLayout`: `public float laneY(Lane)`, `public float laneH(Lane)`, `public boolean hasLane(Lane)`.

- [ ] Test `lanesAtDefaultHeight`: for `BarLayout.of(1026, 62)` assert laneY/H = TOP 4/14, DOING 20/11, PRAYER 33/11, INPUTS 46/7, RULER 55/3.
- [ ] Test `twoRowBarSplitsTheMiddle`: `of(1026, 50)`: `hasLane(INPUTS)` false; DOING and PRAYER each `pillH/2` tall, PRAYER directly below DOING.
- [ ] Test `oneRowBarHasAThinPrayerStrip`: `of(1026, 30)`: PRAYER height `3u`, starts at DOING bottom.
- [ ] Implement in `of()` from the constants above × u; copy the fields in `withTickWidth`.
- [ ] Run `./gradlew test --offline -q --tests '*BarLayoutTest' </dev/null` → PASS. Commit "feat: lane geometry".

### Task 2: Base timeline — top, prayer, inputs lanes

**Files:** Create `ui/BaseTimeline.java`; Modify `TimelinePainter.java`, `CombatPainter.java`; Test `ui/BaseTimelineTest.java`

**Interfaces — Produces:** `public final class BaseTimeline { public static void paint(Graphics2D g, BarLayout l, BarState s, float[] alpha) }` where `alpha` is indexed by `Lane.ordinal()`; helpers `top(...)`, `doing(...)`, `prayer(...)`, `inputs(...)`, each wrapping its drawing in `AlphaComposite.SrcOver` at that lane's alpha (skip when alpha == 0).

- [ ] Tests (render 519×62, sample state with a melee prayer span, a hit, an XP drop, an attack and an eat): the prayer colour is at the PRAYER lane centre; the sword and fish pixels are inside INPUTS; the XP text is inside TOP; with `alpha[PRAYER] = 0` the prayer pixel is ground colour.
- [ ] Move: `TimelinePainter.xp/hits` → top (hit line from TOP top to PRAYER bottom); `CombatPainter.prayerPills` → prayer lane at lane height, keeping `spanStartX/spanEndX/pendingPrayer` and the points label; `TimelinePainter.clicks` + LazyFlickView sword/eat drawing (without the "+N late" text and without the XP-avoidance code) + bank clicks (production, gathering), depletion/stump → inputs. Inputs respect `s.actionsRow` (OFF hides swords and eats, ATTACKS hides eats).
- [ ] Delete `CombatPainter.xpLow*`, `XpRow.Row.LOW` and LazyFlickView `shiftPastXp/nextSwordX`.
- [ ] Run the module's tests → PASS. Commit "feat: base timeline lanes".

### Task 3: Base timeline — doing lane

**Files:** Modify `ui/BaseTimeline.java`; move pill code out of `ProductionView`, `GatherView`, `AlchView`, `AgilityView` into `ui/DoingLane.java`; Test `ui/DoingLaneTest.java`

**Interfaces — Produces:** `final class DoingLane { static void paint(Graphics2D g, BarLayout l, BarState s) }`, drawn from `s.run`, `s.gather`, `s.alch`, `s.agility` whenever they have history in view (not only in their mode), plus `s.timeline` walk/run/action/fight spans.

- [ ] Test `specificActivityHidesTheGenericPill`: a production run over ticks 10–20 and an "action" span 12–15 → the pixel at tick 13's centre is the cook colour, not `Palette.ACTION`.
- [ ] Test `movementShowsWhereNothingElseIs`: a run span 5–8 with nothing else → faint run-energy colour there.
- [ ] Implement: collect the tick ranges covered by run/gather/alch/agility/fight pills first; draw generic spans clipped to the gaps. Gather roll notches, cooking pot, axe and agility numbers come along. Gather item icons, marks of grace and "burnt" go to the top lane.
- [ ] Run tests → PASS. Commit "feat: doing lane".

### Task 4: Fight span

**Files:** Modify `SuperHistogramPlugin.java`; Create `collect/FightCollector.java`; Test `collect/FightCollectorTest.java`

**Interfaces — Produces:** `FightCollector.onTick(int tick, String targetName /* null when not in combat */)` opening/closing a `"fight"` span labelled with the NPC name; drawn by DoingLane in `Palette.ACTION`.

- [ ] Tests: in combat ticks 10–14 with "Abyssal demon" → one span 10..14 labelled "Abyssal demon"; a new target name closes and opens a new span; null closes.
- [ ] Wire in `handleTick` using the same `inCombat` result buildState uses (with the combat hold) and the interacting NPC's name.
- [ ] Also fixes attacks: record a swing when the player clicked "Attack" on that NPC or the NPC is interacting with the player, not only after the NPC was hit (`handleAnimation`). Test in `SuperHistogramPluginHandlersTest`: first swing on an unhit NPC after an Attack click sets `attackLate >= 0`.
- [ ] Run tests → PASS. Commit "feat: fight span and first-swing attacks".

### Task 5: Lenses

**Files:** Create `ui/Lens.java`; convert each `ui/modes/*View.java` to implement it; delete `ui/ModeView.java`; Tests in `ui/modes/*Test.java`

**Interfaces — Produces:**
```java
public interface Lens
{
	/** Alpha per Lane.ordinal(); 1 = full, DIM = 0.3. */
	float[] emphasis(BarState s);
	/** Mode helpers over the base timeline (timeline coordinates, same translation as the base). */
	void overlay(Graphics2D g, BarLayout l, BarState s);
	void paintRight(Graphics2D g, BarLayout l, BarState s);
}
```

| Lens | Dims | Overlay |
|---|---|---|
| General | — | — |
| AFK | doing | idle pill on the doing lane |
| Minimal | all but top (XP only: hits hidden in top) | — |
| Lazy flick | doing | beat carets (ruler), "+N late" by swords |
| 1-tick flick | top, doing, inputs except clicks | verdict dots / split outline / skip cross on the prayer lane |
| Production | prayer | finish outline + flag, "+N" wasted |
| Gathering | prayer | "+N" re-click |
| Alch | prayer | "+N" lost, next-ready caret |
| Agility | prayer | lap lines + labels, "+N" lost |

- [ ] Test per lens with a null tracker: overlay and paintRight draw nothing and don't throw.
- [ ] Test `lensesShareTheBase`: same state through every lens with emphasis forced to all-1 → identical pixels inside the timeline clip, excluding each lens's overlay (compare with overlay disabled).
- [ ] Move the overlay code out of each view's `paintTimeline`; keep `paintRight` and the static readout helpers untouched (existing readout tests stay green).
- [ ] Commit "feat: modes are lenses".

### Task 6: Wire into BarPainter

**Files:** Modify `ui/BarPainter.java`, `ui/modes/AgilityView.java`; Tests `ModeFadeTest`, `SmoothScrollTest`, `BarRenderSmokeTest`

- [ ] `paintContent`: `BaseTimeline.paint(tg, tl, s, emphasis)` then `lens.overlay(tg, tl, s)` inside the translated clip; now line and readouts as today.
- [ ] Replace `fadeIn` with an emphasis tween: on a mode change, alpha per lane eases from the old lens's emphasis to the new one's over 300 ms (overlay fades in over the same 300 ms). Rewrite `ModeFadeTest` to assert: right after a switch the base lanes are unchanged at full emphasis lanes, the overlay alpha is 0, after 300 ms the new emphasis is reached.
- [ ] Remove agility's tick-width zoom from `timelineLayout` (keep `withNowPct`); delete `AgilityView.tickWidth` and its tests.
- [ ] Full build `./gradlew build --offline -q </dev/null` → all tests pass. Commit "feat: one timeline in the bar".

### Task 7: Samples and look

**Files:** `src/test/java/com/superhistogram/ui/SampleStates.java`, `BarRenderSamples.java`

- [ ] Add a `oneTimelineSheet` render: one combat+skilling scenario through every lens at 519×62, plus 1026×40 and 1026×80, written to `-PrenderDir`.
- [ ] Render, look at it against `design/mockups/one-timeline.html`, fix overlaps. Pin the sheet for the operator.
- [ ] Commit "test: one-timeline sample sheet".
