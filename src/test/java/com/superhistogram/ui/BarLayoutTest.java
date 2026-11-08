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
	public void actionsRowHidesBelow56()
	{
		assertEquals(2, BarLayout.of(1026, 55).rows);
		assertEquals(3, BarLayout.of(1026, 56).rows);
	}

	@Test
	public void oneRowBelow40()
	{
		assertEquals(1, BarLayout.of(1026, 39).rows);
		assertEquals(2, BarLayout.of(1026, 40).rows);
	}

	@Test
	public void rightHidesBelow720()
	{
		assertFalse(BarLayout.of(719, 62).showRight);
		assertTrue(BarLayout.of(720, 62).showRight);
	}

	@Test
	public void leftHidesBelow480()
	{
		assertFalse(BarLayout.of(479, 62).showLeft);
		assertTrue(BarLayout.of(480, 62).showLeft);
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
}
