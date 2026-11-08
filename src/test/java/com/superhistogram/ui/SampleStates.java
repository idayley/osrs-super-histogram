package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Hit;
import com.superhistogram.model.IdleTracker;
import com.superhistogram.model.Mode;
import com.superhistogram.model.SessionClock;
import com.superhistogram.model.Span;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpTracker;

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
}
