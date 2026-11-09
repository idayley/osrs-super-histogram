package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Timeline;
import org.junit.Test;

public class LagTickTest
{
	private static final BarLayout L = BarLayout.of(519, 62);

	private static BarState state(int lagTick, int lengthMs)
	{
		Timeline t = new Timeline(64);
		for (int i = 0; i <= 10; i++)
		{
			t.at(i).lengthMs = i == 10 ? 0 : 600;
		}
		t.at(lagTick).lengthMs = lengthMs;
		return BarState.builder().timeline(t).nowTick(10).build();
	}

	@Test
	public void slowTickIsDrawnWiderInProportion()
	{
		BarState s = state(6, 930);
		assertEquals(L.tickWidth * 930f / 600f, TimelinePainter.slotW(L, s, 6), 0.01f);
		assertEquals(TimelinePainter.slotW(L, s, 6), TimelinePainter.x(L, s, 7) - TimelinePainter.x(L, s, 6), 0.01f);
		// Ticks after it are where they always were; ticks before it move left.
		assertEquals(TimelinePainter.nowX(L) - 3 * L.tickWidth, TimelinePainter.x(L, s, 8), 0.01f);
	}

	@Test
	public void aSlightlySlowTickStaysNormal()
	{
		BarState s = state(6, 700);
		assertEquals(L.tickWidth, TimelinePainter.slotW(L, s, 6), 0f);
	}

	@Test
	public void aLateTickThenAnEarlyOneIsAClientHitchNotLag()
	{
		BarState s = state(6, 930);
		s.timeline.at(7).lengthMs = 270;
		assertEquals(L.tickWidth, TimelinePainter.slotW(L, s, 6), 0f);
	}

	@Test
	public void theNewestTickIsNotWidenedUntilTheTickAfterItIsOver()
	{
		// Tick 9 just ended long; tick 10 (now) hasn't shown whether it was a catch-up.
		assertEquals(L.tickWidth, TimelinePainter.slotW(L, state(9, 930), 9), 0f);
	}

	@Test
	public void aLagTickWidensGraduallyWhileScrolling()
	{
		Timeline t = state(8, 930).timeline;
		BarState s = BarState.builder().timeline(t).nowTick(10).tickMs(1000).smooth(true).build();
		float full = L.tickWidth * 930f / 600f;
		assertEquals(L.tickWidth, TimelinePainter.slotW(L.withGlide(0f), s, 8), 0.01f);
		float half = TimelinePainter.slotW(L.withGlide(L.tickWidth / 2f), s, 8);
		assertEquals(true, half > L.tickWidth && half < full);
		assertEquals(full, TimelinePainter.slotW(L.withGlide(L.tickWidth), s, 8), 0.01f);
		// A tick later it is fully wide from the start, so nothing jumps.
		BarState next = BarState.builder().timeline(t).nowTick(11).tickMs(1000).smooth(true).build();
		t.at(10).lengthMs = 600;
		assertEquals(full, TimelinePainter.slotW(L.withGlide(0f), next, 8), 0.01f);
	}
}
