package com.superhistogram.model.skill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class LoopRunTest
{
	private static final int PURE = 7936;
	private static final List<XpDrop> NONE = Collections.emptyList();
	private final Timeline tl = new Timeline(4096);
	private final Map<Integer, Integer> inv = new HashMap<>();

	private static List<XpDrop> xp(String skill, int n)
	{
		return Collections.singletonList(new XpDrop(skill, n));
	}

	@Test
	public void runecraftXpCountsOnlyWithEssenceLeavingAndPouchesMergeIntoOneTrip()
	{
		LoopRun r = new LoopRun(LoopRun.Kind.RUNECRAFT, tl);
		inv.put(PURE, 28);
		r.onTick(0, NONE, new HashMap<>(inv));
		// Runecraft XP with no essence leaving (a minigame reward, a lamp): not a craft.
		r.onTick(5, xp("Runecraft", 500), null);
		assertEquals(0, r.total());
		for (int trip = 0; trip < 3; trip++)
		{
			int t = 100 + trip * 90;
			inv.put(PURE, 0);
			r.onTick(t, xp("Runecraft", 300), new HashMap<>(inv));
			// Pouches emptied, crafted again 4 ticks later: the same trip.
			inv.put(PURE, 12);
			r.onTick(t + 2, NONE, new HashMap<>(inv));
			inv.put(PURE, 0);
			r.onTick(t + 4, xp("Runecraft", 130), new HashMap<>(inv));
			inv.put(PURE, 28);
			r.onTick(t + 60, NONE, new HashMap<>(inv));
		}
		assertEquals(3, r.total());
		assertEquals(430, r.stopXp());
		assertEquals(90, r.lastLoopTicks());
		assertTrue(r.showing(300));
		assertFalse(r.showing(284 + LoopRun.Kind.RUNECRAFT.linger + 1));
		assertEquals(3, r.marks().size());
		assertEquals(2, r.marks().get(0).number);
	}

	@Test
	public void farmRunsCountPatchesAgainstTheLastRun()
	{
		LoopRun r = new LoopRun(LoopRun.Kind.FARMING, tl);
		int t = 0;
		for (int run = 0; run < 2; run++)
		{
			for (int patch = 0; patch < (run == 0 ? 4 : 2); patch++)
			{
				// Harvest drops 3 ticks apart, then plant: one patch.
				for (int d = 0; d < 5; d++)
				{
					r.onTick(t + d * 3, xp("Farming", 30), null);
				}
				t += 80;
			}
			t += 8000;
		}
		assertEquals(2, r.inRun());
		assertEquals(4, r.previousRunCount());
		assertEquals(6, r.total());
		assertEquals(3 * 80 + 12, r.bestRunTicks());
	}

	@Test
	public void birdHouseXpNeedsABirdHouseClick()
	{
		LoopRun r = new LoopRun(LoopRun.Kind.BIRDHOUSE, tl);
		r.onTick(10, xp("Hunter", 200), null);
		assertEquals(0, r.total());
		r.onClick(50);
		r.onTick(60, xp("Hunter", 280), null);
		r.onClick(120);
		r.onTick(125, xp("Hunter", 280), null);
		assertEquals(2, r.total());
		assertTrue(r.showing(130));
	}
}
