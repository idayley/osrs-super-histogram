package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Hit;
import com.superhistogram.model.IdleTracker;
import com.superhistogram.model.Mode;
import com.superhistogram.model.SessionClock;
import com.superhistogram.model.Span;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpTracker;
import com.superhistogram.model.combat.ActionsRow;
import com.superhistogram.model.combat.Beat;
import com.superhistogram.model.combat.FlickClassifier;
import com.superhistogram.model.combat.FoodKind;
import com.superhistogram.model.skill.AlchTopRow;
import com.superhistogram.model.skill.AlchTracker;
import com.superhistogram.model.skill.InventoryRun;
import com.superhistogram.model.RightSide;
import java.util.HashMap;
import java.util.Map;

/** Sample bar states shared by the render tests. */
public final class SampleStates
{
	public static final long NOW_MS = 10_000_000L;
	public static final int NOW_TICK = 200;

	private SampleStates()
	{
	}

	/** Mirrors the "Questing / general play" bar in always-on-baseline.html. */
	static BarState general()
	{
		Timeline t = new Timeline(512);
		XpTracker xp = new XpTracker(t);
		int base = NOW_TICK - 16;
		xp.onStat("Thieving", 500_000, base - 40);
		xp.onStat("Magic", 2_000_000, base - 40);
		xp.onStat("Magic", 2_012_339, base - 30);
		xp.onStat("Thieving", 500_035, base + 6);
		xp.onStat("Magic", 2_012_400, base + 15);
		t.at(base + 12).hits.add(new Hit(3, false));

		t.openSpan("walk", base);
		t.closeSpan("walk", base + 4);
		Span talk = t.openSpan("action", base + 5);
		talk.label = "talk";
		t.closeSpan("action", base + 6);
		Span run = t.openSpan("run", base + 8);
		run.label = "running 71→64%";
		t.closeSpan("run", base + 12);
		Span tele = t.openSpan("action", base + 13);
		tele.label = "tele";

		for (int k : new int[]{0, 5, 8, 13})
		{
			t.at(base + k).clicks.add(0f);
		}
		t.at(NOW_TICK);

		SessionClock clock = new SessionClock();
		clock.onLoggedIn(NOW_MS - (2 * 3600 + 14 * 60 + 7) * 1000L);
		IdleTracker idle = new IdleTracker(60);
		idle.update(3, NOW_MS);

		return BarState.builder()
			.timeline(t).nowTick(NOW_TICK).xp(xp).idle(idle).clock(clock)
			.runEnergyPct(64).modeName("General").modeSub("auto · questing")
			.showRunEnergy(true).showLoginTime(true).mode(Mode.GENERAL).nowMs(NOW_MS)
			.build();
	}

	/**
	 * General mode with several skills dropping: Strength 120 + Hitpoints 40 at NOW_TICK - 10, Magic 123456
	 * at NOW_TICK - 6 and Fletching 15 on NOW_TICK - 9, -7, -5, -3, -1.
	 */
	static BarState multiXp(com.superhistogram.model.XpDrops d, boolean dots)
	{
		Timeline t = new Timeline(512);
		XpTracker xp = new XpTracker(t);
		for (String sk : new String[]{"Strength", "Hitpoints", "Magic", "Fletching"})
		{
			xp.onStat(sk, 1_000_000, NOW_TICK - 40);
		}
		xp.onStat("Strength", 1_000_120, NOW_TICK - 10);
		xp.onStat("Hitpoints", 1_000_040, NOW_TICK - 10);
		xp.onStat("Magic", 1_123_456, NOW_TICK - 6);
		int f = 1_000_000;
		for (int k = 9; k >= 1; k -= 2)
		{
			f += 15;
			xp.onStat("Fletching", f, NOW_TICK - k);
		}
		t.at(NOW_TICK);
		SessionClock clock = new SessionClock();
		clock.onLoggedIn(NOW_MS - (2 * 3600 + 14 * 60 + 7) * 1000L);
		return BarState.builder()
			.timeline(t).nowTick(NOW_TICK).xp(xp).idle(new IdleTracker(60)).clock(clock)
			.runEnergyPct(64).modeName("General").modeSub("auto").showRunEnergy(true).showLoginTime(true)
			.mode(Mode.GENERAL).nowMs(NOW_MS).xpDrops(d).xpDots(dots)
			.build();
	}

	/** Contact-sheet sample: the lazy-flick bar with a Slayer drop alongside Strength and Hitpoints. */
	static BarState meleeSlayer()
	{
		return meleeSlayer(ActionsRow.ATTACKS_EATS);
	}

	static BarState meleeSlayer(ActionsRow row)
	{
		return lazyFlick(row, true, Beat.Verdict.EARLY, true);
	}

	/** Contact-sheet sample (gold arrives with Task 17b): Magic every 5 ticks, Fletching every other tick. */
	static BarState alchFletch()
	{
		Timeline t = new Timeline(512);
		XpTracker xp = new XpTracker(t);
		xp.onStat("Magic", 1_000_000, NOW_TICK - 40);
		xp.onStat("Fletching", 1_000_000, NOW_TICK - 40);
		int m = 1_000_000, f = 1_000_000;
		for (int k = 17; k >= 0; k--)
		{
			if (k % 2 == 0)
			{
				f += 15;
				xp.onStat("Fletching", f, NOW_TICK - k);
			}
			if (k % 5 == 1)
			{
				m += 65;
				xp.onStat("Magic", m, NOW_TICK - k);
			}
		}
		t.openSpan("action", NOW_TICK - 16).endTick = NOW_TICK - 11;
		t.at(NOW_TICK);
		return multiBase(t, xp, "Alch + fletch");
	}

	/** Contact-sheet sample: Fishing and Cooking drops, one tick with both. */
	static BarState tempoross()
	{
		Timeline t = new Timeline(512);
		XpTracker xp = new XpTracker(t);
		xp.onStat("Fishing", 1_000_000, NOW_TICK - 40);
		xp.onStat("Cooking", 1_000_000, NOW_TICK - 40);
		int fi = 1_000_000, co = 1_000_000;
		int[][] drops = {{16, 62, 0}, {13, 62, 0}, {10, 62, 0}, {6, 0, 91}, {4, 0, 91}, {2, 40, 30}};
		for (int[] d : drops)
		{
			if (d[1] > 0)
			{
				fi += d[1];
				xp.onStat("Fishing", fi, NOW_TICK - d[0]);
			}
			if (d[2] > 0)
			{
				co += d[2];
				xp.onStat("Cooking", co, NOW_TICK - d[0]);
			}
		}
		t.at(NOW_TICK);
		return multiBase(t, xp, "Tempoross");
	}

	private static BarState multiBase(Timeline t, XpTracker xp, String name)
	{
		SessionClock clock = new SessionClock();
		clock.onLoggedIn(NOW_MS - (2 * 3600 + 14 * 60 + 7) * 1000L);
		return BarState.builder()
			.timeline(t).nowTick(NOW_TICK).xp(xp).idle(new IdleTracker(60)).clock(clock)
			.runEnergyPct(64).modeName(name).modeSub("auto").showRunEnergy(true).showLoginTime(true)
			.mode(Mode.GENERAL).nowMs(NOW_MS)
			.build();
	}

	/** A timeline with only one click, nothing else that is white. */
	static BarState singleClick()
	{
		Timeline t = new Timeline(512);
		t.at(NOW_TICK - 3).clicks.add(0f);
		t.at(NOW_TICK);
		return BarState.builder()
			.timeline(t).nowTick(NOW_TICK).xp(new XpTracker(t)).idle(new IdleTracker(60))
			.clock(new SessionClock()).runEnergyPct(50).modeName("General").modeSub("auto")
			.showRunEnergy(true).showLoginTime(true).mode(Mode.GENERAL).nowMs(NOW_MS)
			.build();
	}

	static BarState empty(Mode mode)
	{
		Timeline t = new Timeline(512);
		return BarState.builder()
			.timeline(t).nowTick(-1).xp(new XpTracker(t)).idle(new IdleTracker(60))
			.clock(new SessionClock()).runEnergyPct(0).modeName(null).modeSub(null)
			.showRunEnergy(true).showLoginTime(true).mode(mode).nowMs(NOW_MS)
			.build();
	}

	/** Same timeline, idle for 4:21 (0:39 to logout), as in the AFK mockup bar. */
	static BarState afk(BarState s)
	{
		IdleTracker idle = new IdleTracker(60);
		idle.update(261, NOW_MS);
		return BarState.builder()
			.timeline(s.timeline).nowTick(s.nowTick).xp(s.xp).idle(idle).clock(s.clock)
			.runEnergyPct(100).modeName("AFK").modeSub("auto · NMZ")
			.showRunEnergy(true).showLoginTime(true).mode(Mode.AFK).nowMs(s.nowMs)
			.build();
	}

	static BarState withMode(BarState s, Mode mode, String name, String sub)
	{
		return BarState.builder()
			.timeline(s.timeline).nowTick(s.nowTick).xp(s.xp).idle(s.idle).clock(s.clock)
			.runEnergyPct(s.runEnergyPct).modeName(name).modeSub(sub)
			.showRunEnergy(s.showRunEnergy).showLoginTime(s.showLoginTime).mode(mode).nowMs(s.nowMs)
			.build();
	}

	/** The beat is pressed here; beats then fall every 4 ticks. */
	static final int FLICK_PRESS = NOW_TICK - 28;
	/** Ticks of the sample's attacks (every 4), eats and landed / blocked hits. */
	static final int ATTACK_LATE_TICK = NOW_TICK - 11;
	static final int EAT_NORMAL = NOW_TICK - 13, EAT_COMBO = NOW_TICK - 9, EAT_FREE = NOW_TICK - 5;
	static final int HIT_LANDED = NOW_TICK - 12, HIT_BLOCKED = NOW_TICK - 6;

	static BarState lazyFlick()
	{
		return lazyFlick(ActionsRow.ATTACKS_EATS, true, Beat.Verdict.EARLY);
	}

	/**
	 * Mirrors the lazy-flick.html zoom: speed 4, verdicts ok, ok, late, ok, ok, then {@code last}
	 * (the press is 28 ticks back so all six verdicts are in), attacks every 4 ticks with one
	 * a tick late, three eats, a landed and a blocked hit, and Strength + Hitpoints XP on the tick
	 * after each attack. No hit or XP in the last 2 ticks.
	 */
	static BarState lazyFlick(ActionsRow row, boolean showEatCosts, Beat.Verdict last)
	{
		return lazyFlick(row, showEatCosts, last, false);
	}

	private static BarState lazyFlick(ActionsRow row, boolean showEatCosts, Beat.Verdict last, boolean slayer)
	{
		Timeline t = new Timeline(512);
		XpTracker xp = new XpTracker(t);
		xp.onStat("Strength", 1_000_000, NOW_TICK - 40);
		xp.onStat("Hitpoints", 1_000_000, NOW_TICK - 40);
		int str = 1_000_000, hp = 1_000_000, sl = 1_000_000;
		if (slayer)
		{
			xp.onStat("Slayer", sl, NOW_TICK - 40);
		}
		for (int a = NOW_TICK - 27; a <= NOW_TICK - 3; a += 4)
		{
			t.at(a).attackLate = a == ATTACK_LATE_TICK ? 1 : 0;
			str += 40;
			xp.onStat("Strength", str, a + 1);
			hp += 13;
			xp.onStat("Hitpoints", hp, a + 1);
			if (slayer)
			{
				sl += 16;
				xp.onStat("Slayer", sl, a + 1);
			}
		}
		TickRecord eatN = t.at(EAT_NORMAL);
		eatN.eat = FoodKind.NORMAL;
		eatN.eatCost = 3;
		TickRecord eatC = t.at(EAT_COMBO);
		eatC.eat = FoodKind.COMBO;
		eatC.eatCost = 5;
		TickRecord eatF = t.at(EAT_FREE);
		eatF.eat = FoodKind.NORMAL;
		eatF.eatCost = 0;
		t.at(HIT_LANDED).hits.add(new Hit(14, false));
		t.at(HIT_BLOCKED).hits.add(new Hit(0, true));

		Beat beat = new Beat();
		Beat.Verdict[] plan = {Beat.Verdict.ON_TIME, Beat.Verdict.ON_TIME, Beat.Verdict.LATE,
			Beat.Verdict.ON_TIME, Beat.Verdict.ON_TIME, last};
		boolean[] on = new boolean[NOW_TICK - FLICK_PRESS + 2];
		for (int i = 0; i < plan.length; i++)
		{
			int b = 4 * (i + 1);
			on[b] = plan[i] == Beat.Verdict.EARLY;
			on[b + 1] = plan[i] != Beat.Verdict.LATE;
		}
		beat.press(FLICK_PRESS, 4, true);
		for (int k = 0; k <= NOW_TICK - FLICK_PRESS; k++)
		{
			int tick = FLICK_PRESS + k;
			if (t.peek(tick) != null && t.peek(tick).attackLate >= 0)
			{
				beat.onAttack(tick);
			}
			beat.onTick(tick, on[k]);
		}
		pray(t, "pray-melee", NOW_TICK - 13, NOW_TICK - 13);
		pray(t, "pray-magic", NOW_TICK - 9, NOW_TICK - 9);
		pray(t, "pray-melee", NOW_TICK - 6, NOW_TICK - 5);
		pray(t, "pray-magic", NOW_TICK - 1, NOW_TICK - 1);
		t.at(NOW_TICK);

		SessionClock clock = new SessionClock();
		clock.onLoggedIn(NOW_MS - (2 * 3600 + 14 * 60 + 7) * 1000L);
		return BarState.builder()
			.timeline(t).nowTick(NOW_TICK).xp(xp).idle(new IdleTracker(60)).clock(clock)
			.runEnergyPct(64).modeName("Lazy flick").modeSub("4t \u00b7 F").showRunEnergy(true)
			.showLoginTime(true).mode(Mode.LAZY_FLICK).nowMs(NOW_MS)
			.beat(beat).weaponSpeed(4).actionsRow(row).showEatCosts(showEatCosts)
			.build();
	}

	/**
	 * Mirrors one-tick-flick.html: 40 classified ticks (up to NOW_TICK - 1) of mostly clean flicks
	 * at phases .2-.45, two splits, a skip, a 4-tick drain, hits, and a pray-magic then pray-ranged
	 * span. {@code last} decides the final classified tick (OK, SPLIT, SKIP or DRAIN).
	 */
	static BarState oneTick(FlickClassifier.Kind last)
	{
		Timeline t = new Timeline(512);
		FlickClassifier fc = new FlickClassifier();
		int first = NOW_TICK - 40;
		for (int i = 0; i < 40; i++)
		{
			java.util.List<Float> clicks = new java.util.ArrayList<>();
			boolean on = true;
			float ph = .2f + .25f * ((i * 7) % 10) / 10f;
			boolean split = i == 12 || i == 30 || (i == 39 && last == FlickClassifier.Kind.SPLIT);
			boolean carry = i == 13 || i == 31;
			boolean skip = i == 18 || i == 26 || (i == 39 && last == FlickClassifier.Kind.SKIP);
			boolean drain = (i >= 34 && i <= 37) || (i == 39 && last == FlickClassifier.Kind.DRAIN);
			if (split)
			{
				clicks.add(i == 12 ? .86f : .93f);
			}
			else if (carry)
			{
				clicks.add(.05f);
			}
			else if (skip)
			{
				on = false;
			}
			else if (!drain)
			{
				clicks.add(ph);
				clicks.add(ph + .04f);
			}
			TickRecord r = t.at(first + i);
			r.prayerClicks.addAll(clicks);
			r.flick = fc.classify(r.prayerClicks, on);
			r.flickPhase = fc.phase();
			if (i % 5 == 2)
			{
				boolean safe = r.flick == FlickClassifier.Kind.OK || r.flick == FlickClassifier.Kind.DRAIN;
				r.hits.add(safe ? new Hit(0, true) : new Hit(18 + i % 13, false));
			}
			if (i % 4 == 0)
			{
				r.clicks.add(.4f);
			}
		}
		pray(t, "pray-magic", first, NOW_TICK - 14);
		pray(t, "pray-ranged", NOW_TICK - 13, NOW_TICK - 1);
		t.at(NOW_TICK);

		SessionClock clock = new SessionClock();
		clock.onLoggedIn(NOW_MS - (2 * 3600 + 14 * 60 + 7) * 1000L);
		return BarState.builder()
			.timeline(t).nowTick(NOW_TICK).xp(new XpTracker(t)).idle(new IdleTracker(60)).clock(clock)
			.runEnergyPct(64).modeName("1-tick flick").modeSub("every tick").showRunEnergy(true)
			.showLoginTime(true).mode(Mode.ONE_TICK).nowMs(NOW_MS)
			.flicks(fc).actionsRow(ActionsRow.ATTACKS)
			.build();
	}

	private static void pray(Timeline t, String kind, int from, int to)
	{
		t.openSpan(kind, from).endTick = to;
	}

	// ---- production (cooking.html) ----

	/** Where a sample production run stands. */
	public enum ProdPhase
	{
		/** 27 of 28 made, the last at NOW_TICK - 2, so the finish is 2 ticks ahead. */
		RUNNING,
		/** All 28 made, the last at NOW_TICK - 3, no bank click yet. */
		DONE,
		/** The last item at NOW_TICK - 5, the bank click at NOW_TICK - 2 (3 wasted). */
		BANKED
	}

	public static final int RAW_SHARK = 383, COOKED_SHARK = 385, BURNT_SHARK = 387;
	public static final int COOK_TICKS = 4, COOK_ITEMS = 28;
	/** Items (1-based) that burn in the cooking sample. */
	public static final int BURNT_A = 9, BURNT_B = 25;

	public static BarState cooking(ProdPhase p)
	{
		return cooking(p, true, true);
	}

	public static BarState cooking(ProdPhase p, boolean timeToFinish, boolean wastedTicks)
	{
		switch (p)
		{
			case RUNNING:
				return production("Cooking", RAW_SHARK, COOKED_SHARK, COOK_ITEMS, COOK_TICKS, 27, NOW_TICK - 2, -1,
					timeToFinish, wastedTicks);
			case DONE:
				return production("Cooking", RAW_SHARK, COOKED_SHARK, COOK_ITEMS, COOK_TICKS, 28, NOW_TICK - 3, -1,
					timeToFinish, wastedTicks);
			default:
				return cookingBanked(3, timeToFinish, wastedTicks);
		}
	}

	/** A finished cooking run whose bank click came {@code wasted} ticks after the last item. */
	public static BarState cookingBanked(int wasted, boolean timeToFinish, boolean wastedTicks)
	{
		return production("Cooking", RAW_SHARK, COOKED_SHARK, COOK_ITEMS, COOK_TICKS, 28, NOW_TICK - 5,
			NOW_TICK - 5 + wasted, timeToFinish, wastedTicks);
	}

	/** A fletching run in the same shape as the RUNNING cooking sample (3 ticks per item, nothing burns). */
	public static BarState fletching()
	{
		return production("Fletching", 1511, 52, 28, 3, 27, NOW_TICK - 2, -1, true, true);
	}

	/** Tick of the n-th (1-based) cooking item in the sample for the given last item tick. */
	public static int cookingItemTick(int n, int made, int lastTick)
	{
		return lastTick - (made - n) * COOK_TICKS;
	}

	/**
	 * Drives a real InventoryRun like the plugin does: each made item is one tick with the input
	 * leaving the inventory and XP arriving on the same tick (burnt items, cooking only, get no XP).
	 */
	private static BarState production(String skill, int in, int out, int items, int per, int made, int lastTick,
		int bankTick, boolean timeToFinish, boolean wastedTicks)
	{
		Timeline t = new Timeline(512);
		XpTracker xp = new XpTracker(t);
		InventoryRun run = new InventoryRun(t);
		int first = cookingItemTick(1, made, lastTick) - per;
		int total = 1_000_000;
		xp.onStat(skill, total, first - 1);
		Map<Integer, Integer> inv = new HashMap<>();
		inv.put(in, items);
		t.at(first - 1);
		run.onTick(first - 1, t.at(first - 1).xp, new HashMap<>(inv));
		int n = 0;
		for (int tick = first; tick <= NOW_TICK; tick++)
		{
			t.at(tick);
			Map<Integer, Integer> changed = null;
			if (n < made && tick == cookingItemTick(n + 1, made, lastTick))
			{
				n++;
				boolean burnt = skill.equals("Cooking") && (n == BURNT_A || n == BURNT_B);
				inv.put(in, items - n);
				inv.put(burnt ? BURNT_SHARK : out, inv.getOrDefault(burnt ? BURNT_SHARK : out, 0) + 1);
				changed = new HashMap<>(inv);
				if (!burnt)
				{
					total += 210;
					xp.onStat(skill, total, tick);
				}
			}
			run.onTick(tick, t.at(tick).xp, changed);
			if (tick == bankTick)
			{
				run.onBankClick(tick);
			}
		}
		SessionClock clock = new SessionClock();
		clock.onLoggedIn(NOW_MS - (2 * 3600 + 14 * 60 + 7) * 1000L);
		return BarState.builder()
			.timeline(t).nowTick(NOW_TICK).xp(xp).idle(new IdleTracker(60)).clock(clock)
			.runEnergyPct(64).modeName(skill).modeSub("auto").showRunEnergy(true)
			.showLoginTime(true).mode(Mode.PRODUCTION).nowMs(NOW_MS)
			.run(run).timeToFinish(timeToFinish).wastedTicks(wastedTicks)
			.build();
	}

	/** The same state without its run. */
	static BarState withoutRun(BarState s)
	{
		return BarState.builder()
			.timeline(s.timeline).nowTick(s.nowTick).xp(s.xp).idle(s.idle).clock(s.clock)
			.runEnergyPct(s.runEnergyPct).modeName(s.modeName).modeSub(s.modeSub)
			.showRunEnergy(s.showRunEnergy).showLoginTime(s.showLoginTime).mode(s.mode).nowMs(s.nowMs)
			.build();
	}

	/** The same state without the login time. */
	static BarState withoutLogin(BarState s)
	{
		return BarState.builder()
			.timeline(s.timeline).nowTick(s.nowTick).xp(s.xp).idle(s.idle).clock(s.clock)
			.runEnergyPct(s.runEnergyPct).modeName(s.modeName).modeSub(s.modeSub)
			.showRunEnergy(s.showRunEnergy).showLoginTime(false).mode(s.mode).nowMs(s.nowMs)
			.beat(s.beat).flicks(s.flicks).weaponSpeed(s.weaponSpeed).actionsRow(s.actionsRow)
			.showEatCosts(s.showEatCosts)
			.build();
	}

	/** The same state without a beat. */
	static BarState withoutBeat(BarState s)
	{
		return BarState.builder()
			.timeline(s.timeline).nowTick(s.nowTick).xp(s.xp).idle(s.idle).clock(s.clock)
			.runEnergyPct(s.runEnergyPct).modeName(s.modeName).modeSub(s.modeSub)
			.showRunEnergy(s.showRunEnergy).showLoginTime(s.showLoginTime).mode(s.mode).nowMs(s.nowMs)
			.weaponSpeed(s.weaponSpeed).actionsRow(s.actionsRow).showEatCosts(s.showEatCosts)
			.build();
	}

	/** The same state without the flick classifier (the right readout paints nothing). */
	static BarState withoutFlicks(BarState s)
	{
		return BarState.builder()
			.timeline(s.timeline).nowTick(s.nowTick).xp(s.xp).idle(s.idle).clock(s.clock)
			.runEnergyPct(s.runEnergyPct).modeName(s.modeName).modeSub(s.modeSub)
			.showRunEnergy(s.showRunEnergy).showLoginTime(s.showLoginTime).mode(s.mode).nowMs(s.nowMs)
			.weaponSpeed(s.weaponSpeed).actionsRow(s.actionsRow).showEatCosts(s.showEatCosts)
			.build();
	}

	// ---- high alch (high-alch.html) ----

	/** Cast ticks of the alch sample; the last one (NOW_TICK - 2) has no price. */
	public static final int CAST_LATE = NOW_TICK - 12, CAST_MID = NOW_TICK - 7, CAST_LAST = NOW_TICK - 2;
	/** Profit of the late cast. */
	public static final long LATE_COINS = 2360;

	/**
	 * Drives a real AlchTracker like the plugin: casts at NOW-29 (2360), -24 (2120), -19 (1980), CAST_LATE
	 * (2360, two ticks late), CAST_MID (400) and CAST_LAST (unpriced), each a click on the tick before and a
	 * Magic 65 drop on the cast tick. Online for 1 h.
	 */
	public static BarState alch(AlchTopRow top, RightSide side)
	{
		return alch(top, side, false);
	}

	/** As above; {@code fletch} adds Fletching 15 on every even tick of the last 20. */
	public static BarState alch(AlchTopRow top, RightSide side, boolean fletch)
	{
		return alch(top, side, fletch, true);
	}

	public static BarState alch(AlchTopRow top, RightSide side, boolean fletch, boolean wastedTicks)
	{
		int[] ticks = {NOW_TICK - 29, NOW_TICK - 24, NOW_TICK - 19, CAST_LATE, CAST_MID, CAST_LAST};
		Long[] coins = {2360L, 2120L, 1980L, LATE_COINS, 400L, null};
		return alchRun(top, side, fletch, wastedTicks, ticks, coins, 13_034_301);
	}

	/** Casts every 5 ticks ending at CAST_LAST with the given coins (oldest first); Magic starts at 13,034,301. */
	public static BarState alchText(Long... coins)
	{
		return alchText(AlchTopRow.GOLD, RightSide.PER_HOUR, coins);
	}

	public static BarState alchText(AlchTopRow top, RightSide side, Long... coins)
	{
		int[] ticks = new int[coins.length];
		for (int i = 0; i < ticks.length; i++)
		{
			ticks[i] = CAST_LAST - (ticks.length - 1 - i) * AlchTracker.CAST_TICKS;
		}
		return alchRun(top, side, false, true, ticks, coins, 13_034_301);
	}

	private static BarState alchRun(AlchTopRow top, RightSide side, boolean fletch, boolean wastedTicks, int[] castTicks,
		Long[] coins, int magicBase)
	{
		Timeline t = new Timeline(512);
		XpTracker xp = new XpTracker(t);
		AlchTracker alch = new AlchTracker(t);
		int first = NOW_TICK - 40;
		xp.onStat("Magic", magicBase, first);
		xp.onStat("Fletching", 1_000_000, first);
		int magic = magicBase, fle = 1_000_000;
		for (int tick = first + 1; tick <= NOW_TICK; tick++)
		{
			t.at(tick);
			for (int i = 0; i < castTicks.length; i++)
			{
				if (castTicks[i] - 1 == tick)
				{
					alch.onCastClick(tick, coins[i]);
				}
				if (castTicks[i] == tick)
				{
					magic += AlchTracker.XP;
					xp.onStat("Magic", magic, tick);
				}
			}
			if (fletch && tick > NOW_TICK - 20 && tick % 2 == 0)
			{
				fle += 15;
				xp.onStat("Fletching", fle, tick);
			}
			alch.onTick(tick, t.at(tick).xp);
		}
		SessionClock clock = new SessionClock();
		clock.onLoggedIn(NOW_MS - 3_600_000L);
		return BarState.builder()
			.timeline(t).nowTick(NOW_TICK).xp(xp).idle(new IdleTracker(60)).clock(clock)
			.runEnergyPct(64).modeName("High alch").modeSub("auto \u00b7 5t").showRunEnergy(true)
			.showLoginTime(true).mode(Mode.ALCH).nowMs(NOW_MS)
			.alch(alch).alchTopRow(top).rightSide(side).wastedTicks(wastedTicks)
			.build();
	}

	/** The same state without its alch tracker. */
	public static BarState withoutAlch(BarState s)
	{
		return BarState.builder()
			.timeline(s.timeline).nowTick(s.nowTick).xp(s.xp).idle(s.idle).clock(s.clock)
			.runEnergyPct(s.runEnergyPct).modeName(s.modeName).modeSub(s.modeSub)
			.showRunEnergy(s.showRunEnergy).showLoginTime(s.showLoginTime).mode(s.mode).nowMs(s.nowMs)
			.alchTopRow(s.alchTopRow)
			.build();
	}
}
