package com.superhistogram.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.superhistogram.model.Timeline;
import org.junit.Test;

public class ClickCollectorTest
{
	@Test
	public void clickLandsAtFractionOfTick()
	{
		Timeline t = new Timeline(512);
		ClickCollector c = new ClickCollector(t);
		c.onTick(1000);
		c.onClick(5, 1300);
		assertEquals(0.5f, t.peek(5).clicks.get(0), 0.001f);
	}

	@Test
	public void lateClickClampsBelowOne()
	{
		Timeline t = new Timeline(512);
		ClickCollector c = new ClickCollector(t);
		c.onTick(1000);
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
}
