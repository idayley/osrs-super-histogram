package com.superhistogram.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CombatHoldTest
{
	@Test
	public void holdsForTheChosenTicksAfterLastActivity()
	{
		int hold = ModeDetector.ticks(10);
		assertEquals(17, hold);
		assertTrue(ModeDetector.inCombat(false, 117, 100, hold));
		assertFalse(ModeDetector.inCombat(false, 118, 100, hold));
	}

	@Test
	public void zeroSecondsSwitchesBackRightAway()
	{
		assertFalse(ModeDetector.inCombat(false, 101, 100, ModeDetector.ticks(0)));
		assertTrue(ModeDetector.inCombat(true, 500, 100, 0));
	}
}
