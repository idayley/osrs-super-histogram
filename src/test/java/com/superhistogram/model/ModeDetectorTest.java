package com.superhistogram.model;

import static org.junit.Assert.assertEquals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.combat.AttackTimer;
import com.superhistogram.model.combat.PrayerMode;
import org.junit.Test;

public class ModeDetectorTest
{
	private final ModeDetector detector = new ModeDetector();

	@Test
	public void afkBeatsBothBaselines()
	{
		assertEquals(Mode.AFK, detector.detect(true, false, PrayerMode.LAZY_FLICK, BaselineSetting.GENERAL));
		assertEquals(Mode.AFK, detector.detect(true, false, PrayerMode.LAZY_FLICK, BaselineSetting.MINIMAL));
	}

	@Test
	public void minimalBaselineGivesMinimal()
	{
		assertEquals(Mode.MINIMAL, detector.detect(false, false, PrayerMode.LAZY_FLICK, BaselineSetting.MINIMAL));
	}

	@Test
	public void generalBaselineGivesGeneral()
	{
		assertEquals(Mode.GENERAL, detector.detect(false, false, PrayerMode.LAZY_FLICK, BaselineSetting.GENERAL));
	}

	@Test
	public void combatBeatsBaseline()
	{
		assertEquals(Mode.LAZY_FLICK, detector.detect(false, true, PrayerMode.LAZY_FLICK, BaselineSetting.MINIMAL));
	}

	@Test
	public void afkBeatsCombat()
	{
		assertEquals(Mode.AFK, detector.detect(true, true, PrayerMode.LAZY_FLICK, BaselineSetting.GENERAL));
	}

	@Test
	public void noCombatGivesBaseline()
	{
		assertEquals(Mode.MINIMAL, detector.detect(false, false, PrayerMode.LAZY_FLICK, BaselineSetting.MINIMAL));
	}

	@Test
	public void combatLingers10TicksAfterHit()
	{
		int never = AttackTimer.NEVER;
		assertTrue(ModeDetector.inCombat(false, 110, 100, never));
		assertFalse(ModeDetector.inCombat(false, 111, 100, never));
		assertTrue(ModeDetector.inCombat(true, 500, never, never));
		assertTrue(ModeDetector.inCombat(false, 110, never, 100));
	}

	@Test
	public void oneTickModeWhenConfigured()
	{
		assertEquals(Mode.ONE_TICK, detector.detect(false, true, PrayerMode.ONE_TICK, BaselineSetting.GENERAL));
	}
}
