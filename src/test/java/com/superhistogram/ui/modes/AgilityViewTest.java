package com.superhistogram.ui.modes;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.skill.AgilityCourse;
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
		assertArrayEquals(new String[]{"255 xp/hr", "2 laps \u00b7 \u22124t \u00b7 level 81 in 874 laps (753h 55m)"},
			AgilityView.rightLines(SampleStates.agility(AgilityPhase.MID_LAP)));
		assertEquals(Palette.WHITE, AgilityView.rightColor(SampleStates.agility(AgilityPhase.MID_LAP)));
	}

	@Test
	public void lapsCountFinishedLapsAndTicksLostCanBeOff()
	{
		assertArrayEquals(new String[]{"295 xp/hr", "3 laps \u00b7 \u22124t \u00b7 level 81 in 874 laps (651h 23m)"},
			AgilityView.rightLines(SampleStates.agility(AgilityPhase.LAP_DONE)));
		assertArrayEquals(new String[]{"255 xp/hr", "2 laps \u00b7 level 81 in 874 laps (753h 55m)"},
			AgilityView.rightLines(SampleStates.agility(AgilityPhase.MID_LAP, false)));
	}

	@Test
	public void marksPickedUpJoinTheSmallLine()
	{
		BarState s = SampleStates.agility(AgilityPhase.MID_LAP);
		s.agility.onMarksPickedUp(1);
		assertArrayEquals(new String[]{"255 xp/hr", "2 laps \u00b7 1 mark \u00b7 \u22124t \u00b7 level 81 in 874 laps (753h 55m)"}, AgilityView.rightLines(s));
		s.agility.onMarksPickedUp(2);
		assertArrayEquals(new String[]{"255 xp/hr", "2 laps \u00b7 3 marks \u00b7 \u22124t \u00b7 level 81 in 874 laps (753h 55m)"}, AgilityView.rightLines(s));
	}

	@Test
	public void rightLinesWithoutCourse()
	{
		BarState s = SampleStates.withoutAgility(SampleStates.agility(AgilityPhase.MID_LAP));
		assertArrayEquals(new String[]{"", ""}, AgilityView.rightLines(s));
		assertEquals(Palette.WHITE, AgilityView.rightColor(s));
	}

	@Test
	public void smallLineFallbacksDropTheTimeThenTheLevelThenTheMarks()
	{
		BarState s = SampleStates.agility(AgilityPhase.MID_LAP);
		s.agility.onMarksPickedUp(3);
		assertArrayEquals(new String[]{"2 laps \u00b7 3 marks \u00b7 \u22124t \u00b7 874 laps to 81", "2 laps \u00b7 3 marks \u00b7 874 laps to 81", "2 laps \u00b7 874 laps to 81", "874 laps to 81", "2 laps \u00b7 \u22124t"}, AgilityView.smallFallbacks(s));
	}

	@Test
	public void levelInLapsUsesTheLastFullLapsXp()
	{
		BarState s = SampleStates.agility(AgilityPhase.MID_LAP);
		assertEquals(220, s.agility.lapXp());
		long left = com.superhistogram.model.Levels.xpForLevel(81) - s.xp.currentXp("Agility");
		assertEquals("level 81 in " + (left + 219) / 220 + " laps", AgilityView.levelPart(s, false));
		assertEquals((left + 219) / 220 + " laps to 81", AgilityView.shortLevel(s));
	}
}
