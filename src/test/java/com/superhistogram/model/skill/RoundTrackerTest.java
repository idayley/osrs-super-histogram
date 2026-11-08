package com.superhistogram.model.skill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class RoundTrackerTest
{
	private static final int TODT = 6462, LUMBRIDGE = 12850;
	private static final List<XpDrop> NONE = Collections.emptyList();
	private static final List<XpDrop> FM = Collections.singletonList(new XpDrop("Firemaking", 300));
	private final RoundTracker rt = new RoundTracker(new Timeline(4096));

	private void round(int from, int to, boolean message)
	{
		for (int t = from; t <= to; t++)
		{
			rt.onTick(t, TODT, (t - from) % 3 == 0 ? FM : NONE);
		}
		if (message)
		{
			rt.onMessage(to, "Your subdued Wintertodt count is: 12.");
		}
	}

	@Test
	public void roundsRunFromFirstXpToTheKillCountMessageAndKeepTheBest()
	{
		rt.onTick(0, LUMBRIDGE, FM);
		assertFalse(rt.showing(0));
		round(10, 310, true);
		assertEquals(1, rt.rounds());
		assertEquals(300, rt.lastRoundTicks());
		assertTrue(rt.lastWasBest());
		assertTrue(rt.showing(320));
		assertEquals("Wintertodt", rt.game().name);
		round(380, 620, true);
		assertEquals(240, rt.bestTicks());
		assertTrue(rt.lastWasBest());
		round(700, 1000, true);
		assertFalse(rt.lastWasBest());
		assertEquals(3, rt.history().size());
		assertEquals(700, rt.history().get(2).start);
		assertEquals(1000, rt.history().get(2).end);
	}

	@Test
	public void aLongPauseOrLeavingEndsTheRound()
	{
		round(10, 100, false);
		for (int t = 101; t <= 100 + RoundTracker.ROUND_GAP + 1; t++)
		{
			rt.onTick(t, TODT, NONE);
		}
		assertEquals(1, rt.rounds());
		assertEquals(90, rt.lastRoundTicks());
		round(200, 260, false);
		rt.onTick(261, LUMBRIDGE, NONE);
		assertEquals(2, rt.rounds());
		assertFalse(rt.inRound());
		assertFalse(rt.showing(261 + RoundTracker.LINGER + 1));
	}

	@Test
	public void pointsAreKeptAfterTheGameZeroesThem()
	{
		RoundTracker r = new RoundTracker(new Timeline(4096));
		r.onScore(-1, -1);
		for (int t = 0; t <= 30; t++)
		{
			r.onTick(t, 12078, t % 3 == 0 ? FM : NONE);
			r.onScore(t * 10, -1);
		}
		assertEquals(300, r.roundPoints());
		r.onMessage(30, "Your Tempoross kill count is: 41.");
		r.onTick(31, 12078, NONE);
		r.onScore(0, -1);
		assertEquals(300, r.roundPoints());
		assertEquals("Tempoross", r.game().name);
	}
}
