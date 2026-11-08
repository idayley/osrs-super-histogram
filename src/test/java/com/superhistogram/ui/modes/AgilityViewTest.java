package com.superhistogram.ui.modes;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.skill.AgilityCourse;
import com.superhistogram.model.skill.LapCompare;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.SampleStates;
import com.superhistogram.ui.SampleStates.AgilityPhase;
import org.junit.Test;

public class AgilityViewTest
{
	@Test
	public void lapTime()
	{
		assertEquals("0:52.8", AgilityView.lapTime(88));
		assertEquals("0:20.4", AgilityView.lapTime(34));
		assertEquals("1:00.0", AgilityView.lapTime(100));
		assertEquals("0:00.0", AgilityView.lapTime(0));
	}

	@Test
	public void rightLinesMidLap()
	{
		assertArrayEquals(new String[]{"0:20.4", "best 0:30.0 · \u22124t · slowest #4"},
			AgilityView.rightLines(SampleStates.agility(AgilityPhase.MID_LAP)));
		assertArrayEquals(new String[]{"0:20.4", "last 0:31.8 · \u22124t · slowest #4"},
			AgilityView.rightLines(SampleStates.agility(AgilityPhase.MID_LAP, LapCompare.LAST, true)));
		assertArrayEquals(new String[]{"0:20.4", "best 0:30.0 · slowest #4"},
			AgilityView.rightLines(SampleStates.agility(AgilityPhase.MID_LAP, LapCompare.BEST, false)));
		assertEquals(Palette.WHITE, AgilityView.rightColor(SampleStates.agility(AgilityPhase.MID_LAP)));
	}

	@Test
	public void rightLinesLapDone()
	{
		BarState s = SampleStates.agility(AgilityPhase.LAP_DONE);
		assertArrayEquals(new String[]{"0:31.2", "best 0:30.0 · \u22124t · slowest #4"},
			AgilityView.rightLines(s));
		assertEquals(Palette.WHITE, AgilityView.rightColor(s));
		String[] last = AgilityView.rightLines(SampleStates.agility(AgilityPhase.LAP_DONE, LapCompare.LAST, true));
		assertTrue(last[1], last[1].startsWith("last 0:31.8"));
	}

	@Test
	public void rightLinesNewBest()
	{
		BarState s = SampleStates.agility(AgilityPhase.NEW_BEST);
		assertArrayEquals(new String[]{"0:31.2", "best 0:31.2 · \u22124t · slowest #4"},
			AgilityView.rightLines(s));
		assertEquals(Palette.MINT, AgilityView.rightColor(s));
	}

	@Test
	public void rightLinesWithoutCourse()
	{
		BarState s = SampleStates.withoutAgility(SampleStates.agility(AgilityPhase.MID_LAP));
		assertArrayEquals(new String[]{"", ""}, AgilityView.rightLines(s));
		assertEquals(Palette.WHITE, AgilityView.rightColor(s));
	}

	@Test
	public void tickWidthWithoutLaps()
	{
		AgilityCourse fresh = new AgilityCourse(new Timeline(64));
		fresh.onCourse("SEERS");
		assertEquals(14f, AgilityView.tickWidth(BarLayout.of(1026, 62), fresh), 0.001f);
		assertEquals(14f, AgilityView.tickWidth(BarLayout.of(1026, 62), null), 0.001f);
		assertEquals(7f, AgilityView.tickWidth(BarLayout.of(1026, 31), fresh), 0.001f);
	}

	@Test
	public void tickWidthFitsTheLap()
	{
		BarLayout l = BarLayout.of(1026, 62);
		AgilityCourse ac = SampleStates.agility(AgilityPhase.MID_LAP).agility;
		assertEquals(688f / 59f, AgilityView.tickWidth(l, ac), 0.01f);
	}

	@Test
	public void tickWidthClamped()
	{
		AgilityCourse ac = new AgilityCourse(new Timeline(64));
		ac.onCourse("SEERS");
		ac.loadBest("SEERS", 200);
		assertEquals(6f, AgilityView.tickWidth(BarLayout.of(1026, 62), ac), 0.001f);
		assertTrue(AgilityView.tickWidth(BarLayout.of(1026, 22), ac) >= 4f);
	}

	@Test
	public void smallLineFallbacksDropFacts()
	{
		BarState s = SampleStates.agility(AgilityPhase.MID_LAP);
		assertArrayEquals(new String[]{"best 0:30.0 \u00b7 slowest #4", "best 0:30.0"}, AgilityView.smallFallbacks(s));
	}
}
