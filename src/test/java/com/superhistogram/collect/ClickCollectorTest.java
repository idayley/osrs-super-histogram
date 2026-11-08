package com.superhistogram.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.Timeline;
import org.junit.Test;

public class ClickCollectorTest
{
	@Test
	public void clickLandsAtFractionOfTick()
	{
		Timeline t = new Timeline(512);
		ClickCollector c = new ClickCollector(t);
		c.onTick(4, 1000);
		c.onClick(5, 1300);
		assertEquals(0.5f, t.peek(5).clicks.get(0), 0.001f);
	}

	@Test
	public void lateClickClampsBelowOne()
	{
		Timeline t = new Timeline(512);
		ClickCollector c = new ClickCollector(t);
		c.onTick(4, 1000);
		c.onClick(5, 5000);
		assertEquals(0.99f, t.peek(5).clicks.get(0), 0.001f);
	}

	@Test
	public void clickBeforeAnyTickIsIgnoredSafely()
	{
		Timeline t = new Timeline(512);
		new ClickCollector(t).onClick(-1, 5);
		assertNull(t.peek(0));
	}

	@Test
	public void prayerClickRecordedSeparately()
	{
		Timeline t = new Timeline(512);
		ClickCollector c = new ClickCollector(t);
		c.onTick(4, 0);
		c.onPrayerClick(5, 300);
		assertEquals(1, t.peek(5).prayerClicks.size());
		assertEquals(0.5f, t.peek(5).prayerClicks.get(0), 0.001f);
		assertEquals(0, t.peek(5).clicks.size());
	}

	@Test
	public void clickTooLateForTheNextTickMovesThereWithAGhost()
	{
		Timeline t = new Timeline(512);
		ClickCollector c = new ClickCollector(t);
		c.setPingMs(60);
		c.onTick(5, 0);
		// 570 ms in + 60 ms ping = 630 ms: the server gets it 30 ms into the next tick.
		assertTrue(c.late(570));
		c.onClick(5, 570);
		assertEquals(0, t.peek(5).clicks.size());
		assertEquals(0.95f, t.peek(5).clickGhosts.get(0), 0.001f);
		c.onTick(6, 600);
		assertEquals(0.05f, t.peek(6).clicks.get(0), 0.001f);
	}

	@Test
	public void clickInTimeStaysOnItsTick()
	{
		Timeline t = new Timeline(512);
		ClickCollector c = new ClickCollector(t);
		c.setPingMs(60);
		c.onTick(5, 0);
		assertFalse(c.late(500));
		c.onClick(5, 500);
		assertEquals(1, t.peek(5).clicks.size());
		assertTrue(t.peek(5).clickGhosts.isEmpty());
	}

	@Test
	public void latePrayerClickCountsOnTheNextTick()
	{
		Timeline t = new Timeline(512);
		ClickCollector c = new ClickCollector(t);
		c.setPingMs(100);
		c.onTick(5, 0);
		c.onOverheadClick(5, 550, "melee");
		c.onPrayerClick(5, 550);
		c.onTick(6, 600);
		assertTrue(t.peek(5).prayerClicks.isEmpty());
		assertEquals(1, t.peek(6).prayerClicks.size());
		assertEquals("melee", t.peek(6).overheadClickKind);
	}
}
