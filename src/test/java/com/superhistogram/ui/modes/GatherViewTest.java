package com.superhistogram.ui.modes;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.ui.SampleStates;
import com.superhistogram.ui.SampleStates.GatherPhase;
import org.junit.Test;

public class GatherViewTest
{
	@Test
	public void choppingShowsCountEstimateAndRate()
	{
		assertArrayEquals(new String[]{"12/28", "full in ~1:40 · 600 logs/hr"},
			GatherView.rightLines(SampleStates.gathering(GatherPhase.CHOPPING)));
	}

	@Test
	public void timeToFinishOffDropsEstimate()
	{
		assertArrayEquals(new String[]{"12/28", "600 logs/hr"},
			GatherView.rightLines(SampleStates.gathering(GatherPhase.CHOPPING, false, true)));
	}

	@Test
	public void fullSaysBank()
	{
		assertArrayEquals(new String[]{"Full · bank", "28/28 logs"},
			GatherView.rightLines(SampleStates.gathering(GatherPhase.FULL)));
	}

	@Test
	public void earlyHasNoEstimateYet()
	{
		assertArrayEquals(new String[]{"7/28", "logs"},
			GatherView.rightLines(SampleStates.gathering(GatherPhase.EARLY)));
	}

	@Test
	public void nounsPerSkill()
	{
		assertTrue(GatherView.rightLines(SampleStates.gatheringSkill("Mining", 440))[1].endsWith(" ore/hr"));
		assertTrue(GatherView.rightLines(SampleStates.gatheringSkill("Fishing", 335))[1].endsWith(" fish/hr"));
		assertEquals("logs", GatherView.noun("Woodcutting"));
		assertEquals("ore", GatherView.noun("Mining"));
		assertEquals("fish", GatherView.noun("Fishing"));
		assertEquals("fires", GatherView.noun("Firemaking"));
	}

	@Test
	public void firemakingCountsLogsDown()
	{
		assertArrayEquals(new String[]{"24 left", "done in ~1:00 · 1.5k fires/hr"},
			GatherView.rightLines(SampleStates.firemaking()));
	}

	@Test
	public void nullGatherIsEmpty()
	{
		BarState s = BarState.builder().nowTick(5).build();
		assertArrayEquals(new String[]{"", ""}, GatherView.rightLines(s));
		assertArrayEquals(new String[0], GatherView.headlineFallbacks(s));
	}

	@Test
	public void etaRoundsToTenSeconds()
	{
		assertEquals("~1:40", GatherView.eta(160));
		assertEquals("~1:00", GatherView.eta(96));
		assertEquals("~0:10", GatherView.eta(5));
	}

	@Test
	public void fallbacks()
	{
		assertArrayEquals(new String[]{"Full"},
			GatherView.headlineFallbacks(SampleStates.gathering(GatherPhase.FULL)));
		assertArrayEquals(new String[0],
			GatherView.headlineFallbacks(SampleStates.gathering(GatherPhase.CHOPPING)));
	}
}
