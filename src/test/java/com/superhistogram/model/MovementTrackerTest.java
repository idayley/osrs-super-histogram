package com.superhistogram.model;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Test;

public class MovementTrackerTest
{
	@Test
	public void walkSpan()
	{
		Timeline t = new Timeline(512);
		MovementTracker m = new MovementTracker(t);
		m.onTick(10, 1, false, 100);
		m.onTick(11, 1, false, 100);
		m.onTick(12, 1, false, 100);
		m.onTick(13, 0, false, 100);
		List<Span> s = t.spansBetween(0, 100);
		assertEquals(1, s.size());
		assertEquals("walk", s.get(0).kind);
		assertEquals(10, s.get(0).startTick);
		assertEquals(12, s.get(0).endTick);
	}

	@Test
	public void runSpanLabelsEnergyCost()
	{
		Timeline t = new Timeline(512);
		MovementTracker m = new MovementTracker(t);
		m.onTick(1, 2, false, 71);
		m.onTick(2, 2, false, 69);
		m.onTick(3, 2, false, 64);
		m.onTick(4, 0, false, 64);
		Span s = t.spansBetween(0, 100).get(0);
		assertEquals("run", s.kind);
		assertEquals("running 71→64%", s.label);
		assertEquals(3, s.endTick);
	}

	@Test
	public void actionSpan()
	{
		Timeline t = new Timeline(512);
		MovementTracker m = new MovementTracker(t);
		m.onTick(1, 0, true, 50);
		m.onTick(2, 0, true, 50);
		m.onTick(3, 0, false, 50);
		Span s = t.spansBetween(0, 100).get(0);
		assertEquals("action", s.kind);
		assertEquals(1, s.startTick);
		assertEquals(2, s.endTick);
	}

	@Test
	public void switchWalkToRunSplitsSpans()
	{
		Timeline t = new Timeline(512);
		MovementTracker m = new MovementTracker(t);
		m.onTick(1, 1, false, 90);
		m.onTick(2, 1, false, 90);
		m.onTick(3, 2, false, 90);
		m.onTick(4, 2, false, 88);
		m.onTick(5, 0, false, 88);
		List<Span> s = t.spansBetween(0, 100);
		assertEquals(2, s.size());
		assertEquals("walk", s.get(0).kind);
		assertEquals(2, s.get(0).endTick);
		assertEquals("run", s.get(1).kind);
		assertEquals(3, s.get(1).startTick);
		assertEquals(4, s.get(1).endTick);
	}

	@Test
	public void resetClosesOpenSpan()
	{
		Timeline t = new Timeline(512);
		MovementTracker m = new MovementTracker(t);
		m.onTick(1, 1, false, 90);
		m.onTick(2, 1, false, 90);
		m.reset();
		Span s = t.spansBetween(0, 100).get(0);
		assertEquals(2, s.endTick);
		// Forgotten state: a later walk opens a fresh span instead of extending the old one.
		m.onTick(50, 1, false, 90);
		m.onTick(51, 0, false, 90);
		List<Span> all = t.spansBetween(0, 100);
		assertEquals(2, all.size());
		assertEquals(50, all.get(1).startTick);
		assertEquals(50, all.get(1).endTick);
	}
}
