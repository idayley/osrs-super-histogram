package com.superhistogram.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IdleTrackerTest
{
	@Test
	public void afkAfterThreshold()
	{
		IdleTracker i = new IdleTracker(60);
		i.update(59, 0);
		assertFalse(i.isAfk());
		i.update(60, 0);
		assertTrue(i.isAfk());
	}

	@Test
	public void countdown()
	{
		IdleTracker i = new IdleTracker(60);
		i.update(261, 0);
		assertEquals(39, i.secondsToLogout());
		i.update(400, 0);
		assertEquals(0, i.secondsToLogout());
	}

	@Test
	public void warningOnceAt270()
	{
		IdleTracker i = new IdleTracker(60);
		i.update(269, 0);
		assertFalse(i.warningFired());
		i.update(270, 0);
		assertTrue(i.warningFired());
		i.update(271, 0);
		assertFalse(i.warningFired());
		i.update(0, 0);
		assertFalse(i.warningFired());
		i.update(270, 0);
		assertTrue(i.warningFired());
	}
}
