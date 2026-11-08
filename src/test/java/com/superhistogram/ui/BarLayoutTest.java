package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BarLayoutTest
{
	@Test
	public void defaultSize()
	{
		BarLayout l = BarLayout.of(1026, 62);
		assertEquals(3, l.rows);
		assertTrue(l.showLeft);
		assertTrue(l.showRight);
		assertEquals(1f, l.u, 0f);
		assertEquals(32f, l.tickWidth, 0f);
	}

	@Test
	public void allLanesFrom44()
	{
		// Replay finding 2: a 52 px bar keeps its swings and pill text.
		assertEquals(2, BarLayout.of(1026, 43).rows);
		assertEquals(3, BarLayout.of(1026, 44).rows);
		assertEquals(3, BarLayout.of(1026, 52).rows);
	}

	@Test
	public void oneRowBelow40()
	{
		assertEquals(1, BarLayout.of(1026, 39).rows);
		assertEquals(2, BarLayout.of(1026, 40).rows);
	}

	@Test
	public void readoutsGoCompactBelow720()
	{
		assertTrue(BarLayout.of(719, 62).compact);
		assertFalse(BarLayout.of(720, 62).compact);
	}

	@Test
	public void chatWidthKeepsBothReadoutsCompact()
	{
		BarLayout l = BarLayout.of(519, 62);
		assertTrue(l.showLeft);
		assertTrue(l.showRight);
		assertTrue(l.compact);
		// Compact readouts leave about ten 32 px ticks of history at the chat width.
		assertTrue(l.visibleTicks >= 9);
	}

	@Test
	public void rightHidesBelow400AndLeftBelow330()
	{
		assertFalse(BarLayout.of(399, 62).showRight);
		assertTrue(BarLayout.of(400, 62).showRight);
		assertFalse(BarLayout.of(329, 62).showLeft);
		assertTrue(BarLayout.of(330, 62).showLeft);
	}

	@Test
	public void tickWidthFloor()
	{
		assertEquals(18f, BarLayout.of(1026, 22).tickWidth, 0f);
	}

	@Test
	public void widerMeansMoreTicks()
	{
		BarLayout narrow = BarLayout.of(1026, 62);
		BarLayout wide = BarLayout.of(1240, 62);
		assertTrue(wide.visibleTicks > narrow.visibleTicks);
		assertEquals(narrow.tickWidth, wide.tickWidth, 0f);
	}

	@Test
	public void clampsTinyAndHuge()
	{
		BarLayout tiny = BarLayout.of(1, 1);
		assertEquals(220, tiny.width);
		assertEquals(22, tiny.height);
		BarLayout huge = BarLayout.of(4000, 400);
		assertEquals(4000, huge.width);
		assertEquals(160, huge.height);
	}

	@Test
	public void timelineFitsBetweenReadouts()
	{
		BarLayout l = BarLayout.of(1026, 62);
		assertEquals(128f, l.timelineX, 0.001f);
		assertEquals(1026f - 128f - 210f, l.timelineW, 0.001f);
		assertEquals(21, l.visibleTicks);
		assertEquals(15f, l.topY, 0.001f);
		assertEquals(22f, l.pillY, 0.001f);
		assertEquals(14f, l.pillH, 0.001f);
		assertEquals(46f, l.actionsY, 0.001f);
		assertEquals(53f, l.rulerY, 0.001f);
	}

	@Test
	public void withTickWidthKeepsGeometry()
	{
		BarLayout l = BarLayout.of(1026, 62);
		BarLayout n = l.withTickWidth(14f);
		assertEquals(14f, n.tickWidth, 0f);
		assertEquals(49, n.visibleTicks);
		assertEquals(l.timelineX, n.timelineX, 0f);
		assertEquals(l.timelineW, n.timelineW, 0f);
		assertEquals(l.rows, n.rows);
		assertEquals(l.u, n.u, 0f);
		assertEquals(l.width, n.width);
		assertEquals(l.pillY, n.pillY, 0f);
		assertEquals(32f, l.tickWidth, 0f);
	}

	@Test
	public void nowLineSitsAtTheChosenPercent()
	{
		BarLayout l = BarLayout.of(1026, 62);
		// 21 ticks in view: at 50 % the line is after about 10 of them, at 100 % at the right edge.
		assertEquals(l.timelineX + 10 * l.tickWidth, TimelinePainter.nowX(l.withNowPct(50)), 0.001f);
		assertEquals(l.timelineX + 21 * l.tickWidth, TimelinePainter.nowX(l.withNowPct(100)), 0.001f);
		// Always at least one tick of history.
		assertEquals(l.timelineX + l.tickWidth, TimelinePainter.nowX(l.withNowPct(1)), 0.001f);
	}

	private static void lane(BarLayout l, Lane lane, float y, float h)
	{
		assertEquals(lane + " y", y, l.laneY(lane), 0.001f);
		assertEquals(lane + " h", h, l.laneH(lane), 0.001f);
	}

	@Test
	public void lanesAtDefaultHeight()
	{
		BarLayout l = BarLayout.of(1026, 62);
		lane(l, Lane.TOP, 4, 14);
		lane(l, Lane.DOING, 20, 11);
		lane(l, Lane.PRAYER, 33, 11);
		lane(l, Lane.INPUTS, 46, 7);
		lane(l, Lane.RULER, 55, 3);
		for (Lane k : Lane.values())
		{
			assertTrue(l.hasLane(k));
		}
	}

	@Test
	public void lanesScaleWithHeight()
	{
		BarLayout l = BarLayout.of(1026, 124);
		lane(l, Lane.TOP, 8, 28);
		lane(l, Lane.INPUTS, 92, 14);
		lane(l, Lane.RULER, 110, 6);
	}

	@Test
	public void lanesSurviveCopies()
	{
		BarLayout l = BarLayout.of(1026, 62).withTickWidth(20f).withGlide(5f, 32f).withNowPct(50);
		lane(l, Lane.PRAYER, 33, 11);
		lane(l, Lane.INPUTS, 46, 7);
	}

	@Test
	public void twoRowBarSplitsTheMiddle()
	{
		BarLayout l = BarLayout.of(1026, 42);
		assertFalse(l.hasLane(Lane.INPUTS));
		assertEquals(TimelinePainter.pillY(l), l.laneY(Lane.DOING), 0.001f);
		assertEquals(l.pillH / 2f, l.laneH(Lane.DOING), 0.001f);
		assertEquals(l.pillH / 2f, l.laneH(Lane.PRAYER), 0.001f);
		assertEquals(l.laneY(Lane.DOING) + l.laneH(Lane.DOING), l.laneY(Lane.PRAYER), 0.001f);
		// The top lane ends above the middle band and the ruler starts below it.
		assertTrue(l.laneY(Lane.TOP) + l.laneH(Lane.TOP) < l.laneY(Lane.DOING));
		assertTrue(l.laneY(Lane.PRAYER) + l.laneH(Lane.PRAYER) < l.laneY(Lane.RULER));
	}

	@Test
	public void oneRowBarHasAThinPrayerStrip()
	{
		BarLayout l = BarLayout.of(1026, 30);
		assertFalse(l.hasLane(Lane.INPUTS));
		assertEquals(TimelinePainter.pillY(l), l.laneY(Lane.DOING), 0.001f);
		assertEquals(TimelinePainter.pillH(l), l.laneH(Lane.DOING), 0.001f);
		assertEquals(3f * l.u, l.laneH(Lane.PRAYER), 0.001f);
		assertEquals(l.laneY(Lane.DOING) + l.laneH(Lane.DOING), l.laneY(Lane.PRAYER), 0.001f);
		assertTrue(l.laneY(Lane.PRAYER) + l.laneH(Lane.PRAYER) < l.laneY(Lane.RULER));
	}
}
