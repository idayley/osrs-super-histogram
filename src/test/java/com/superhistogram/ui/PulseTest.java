package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PulseTest
{
	@Test
	public void outlineFadesWithin500ms()
	{
		Pulse p = new Pulse();
		p.tick(0, false);
		assertTrue(p.outlineAlpha(0) > 0f);
		assertTrue(p.outlineAlpha(250) < p.outlineAlpha(0));
		assertEquals(0f, p.outlineAlpha(500), 0f);
	}

	@Test
	public void cueIsBrighter()
	{
		Pulse plain = new Pulse();
		plain.tick(0, false);
		Pulse cue = new Pulse();
		cue.tick(0, true);
		assertTrue(cue.outlineAlpha(0) > plain.outlineAlpha(0));
		assertTrue(cue.outlineAlpha(0) <= 1f);
		assertTrue(cue.outlineAlpha(540) > 0f);
		assertEquals(0f, cue.outlineAlpha(550), 0f);
	}

	@Test
	public void peaksMatchMockup()
	{
		Pulse plain = new Pulse();
		plain.tick(0, false);
		assertEquals(0.32f, plain.outlineAlpha(0), 0.001f);
		Pulse cue = new Pulse();
		cue.tick(0, true);
		assertEquals(0.95f, cue.outlineAlpha(0), 0.001f);
	}

	@Test
	public void outlineWidthFollowsCurrentPulse()
	{
		assertEquals(1.5f, Pulse.OUTLINE_WIDTH, 0f);
		assertEquals(2.5f, Pulse.CUE_WIDTH, 0f);
		Pulse p = new Pulse();
		assertEquals(0f, p.outlineWidth(0), 0f);
		p.tick(0, false);
		assertEquals(1.5f, p.outlineWidth(100), 0f);
		assertEquals(0f, p.outlineWidth(500), 0f);
		p.tick(1000, true);
		assertEquals(2.5f, p.outlineWidth(1100), 0f);
		assertEquals(2.5f, p.outlineWidth(1540), 0f);
		assertEquals(0f, p.outlineWidth(1550), 0f);
		p.done(2000);
		p.tick(2000, false);
		assertEquals(0f, p.outlineWidth(2000), 0f);
	}

	@Test
	public void doneFlashesTwice()
	{
		Pulse p = new Pulse();
		p.done(0);
		assertEquals(0.55f, p.mintAlpha(0), 0.001f);
		assertEquals(0f, p.mintAlpha(180), 0.001f);
		assertEquals(0.55f, p.mintAlpha(320), 0.001f);
		assertEquals(0f, p.mintAlpha(1000), 0.001f);
		assertEquals(0f, p.mintAlpha(5000), 0f);
	}

	@Test
	public void doneSuppressesOutline()
	{
		Pulse p = new Pulse();
		p.done(0);
		p.tick(0, false);
		assertEquals(0f, p.outlineAlpha(0), 0f);
	}

	@Test
	public void doneAfterTickInSameInstantAlsoSuppresses()
	{
		Pulse p = new Pulse();
		p.tick(0, false);
		p.done(0);
		assertEquals(0f, p.outlineAlpha(0), 0f);
	}

	@Test
	public void idleBeforeAnyEvent()
	{
		Pulse p = new Pulse();
		assertEquals(0f, p.outlineAlpha(0), 0f);
		assertEquals(0f, p.mintAlpha(0), 0f);
	}

	@Test
	public void alternateRimStaysLitAndSwapsColourEachBeat()
	{
		Pulse p = new Pulse();
		p.setAlternate(true);
		p.tick(1000, true);
		int first = p.phase();
		assertEquals(0f, p.outlineAlpha(1000), 0f); // no white flash in this style
		assertEquals(Pulse.ALTERNATE_PEAK, p.alternateAlpha(1000), 1e-4f);
		assertEquals(Pulse.ALTERNATE_REST, p.alternateAlpha(3000), 1e-4f); // still lit between beats
		assertEquals(true, p.alternating(3000));
		p.tick(3400, true);
		assertEquals(1 - first, p.phase());
		assertEquals(false, p.alternating(3400 + Pulse.ALTERNATE_HOLD_MS)); // metronome stopped: goes dark
	}
}
