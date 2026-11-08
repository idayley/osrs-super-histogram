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
import com.superhistogram.model.combat.FoodKind;

/** Sample bar states shared by the render tests. */
final class SampleStates
{
	static final long NOW_MS = 10_000_000L;
	static final int NOW_TICK = 200;

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
		Timeline t = new Timeline(512);
		XpTracker xp = new XpTracker(t);
		xp.onStat("Strength", 1_000_000, NOW_TICK - 40);
		xp.onStat("Hitpoints", 1_000_000, NOW_TICK - 40);
		int str = 1_000_000, hp = 1_000_000;
		for (int a = NOW_TICK - 27; a <= NOW_TICK - 3; a += 4)
		{
			t.at(a).attackLate = a == ATTACK_LATE_TICK ? 1 : 0;
			str += 40;
			xp.onStat("Strength", str, a + 1);
			hp += 13;
			xp.onStat("Hitpoints", hp, a + 1);
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

	private static void pray(Timeline t, String kind, int from, int to)
	{
		t.openSpan(kind, from).endTick = to;
	}

	/** The same state without the login time. */
	static BarState withoutLogin(BarState s)
	{
		return BarState.builder()
			.timeline(s.timeline).nowTick(s.nowTick).xp(s.xp).idle(s.idle).clock(s.clock)
			.runEnergyPct(s.runEnergyPct).modeName(s.modeName).modeSub(s.modeSub)
			.showRunEnergy(s.showRunEnergy).showLoginTime(false).mode(s.mode).nowMs(s.nowMs)
			.beat(s.beat).weaponSpeed(s.weaponSpeed).actionsRow(s.actionsRow).showEatCosts(s.showEatCosts)
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
}
