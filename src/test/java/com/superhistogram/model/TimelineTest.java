package com.superhistogram.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class TimelineTest
{
	@Test
	public void atCreatesRecordForTick()
	{
		Timeline t = new Timeline(512);
		assertEquals(10, t.at(10).tick);
		assertNotNull(t.peek(10));
		assertEquals(10, t.newestTick());
	}

	@Test
	public void ringOverwritesOldest()
	{
		Timeline t = new Timeline(512);
		for (int i = 0; i < 1024; i++)
		{
			t.at(i);
		}
		assertNull(t.peek(0));
		assertNotNull(t.peek(1023));
		int retained = 0;
		for (int i = 0; i < 1024; i++)
		{
			if (t.peek(i) != null)
			{
				retained++;
			}
		}
		assertTrue(retained <= 512);
		assertEquals(512, retained);
	}

	@Test
	public void staleWriteIgnored()
	{
		Timeline t = new Timeline(512);
		t.at(1000);
		assertNull(t.at(100));
		assertNull(t.peek(100));
	}

	@Test
	public void spansBetweenReturnsOverlapping()
	{
		Timeline t = new Timeline(512);
		t.openSpan("walk", 0);
		t.closeSpan("walk", 5);
		t.openSpan("action", 8);
		t.openSpan("idle", 20);
		t.closeSpan("idle", 25);
		List<Span> found = t.spansBetween(4, 10);
		assertEquals(2, found.size());
		assertEquals(0, found.get(0).startTick);
		assertEquals(8, found.get(1).startTick);
		assertEquals(-1, found.get(1).endTick);
	}

	@Test
	public void spansDroppedWhenOlderThanBuffer()
	{
		Timeline t = new Timeline(512);
		t.at(0);
		t.openSpan("walk", 0);
		t.closeSpan("walk", 0);
		assertEquals(1, t.spansBetween(0, 10).size());
		t.at(600);
		assertTrue(t.spansBetween(0, 10).isEmpty());
	}
}
