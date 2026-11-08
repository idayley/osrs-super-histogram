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
	public void combatIsARealTargetOrAnOwnAttackInTheLast10Ticks()
	{
		int never = AttackTimer.NEVER;
		assertTrue(ModeDetector.inCombat(true, 500, never));
		assertTrue(ModeDetector.inCombat(false, 110, 100));
		assertFalse(ModeDetector.inCombat(false, 111, 100));
		assertFalse(ModeDetector.inCombat(false, 500, never));
	}

	@Test
	public void realTargetNeedsCombatLevelAndAHitInTheWindow()
	{
		int never = AttackTimer.NEVER;
		assertTrue(ModeDetector.realTarget(2, 100, 110));
		assertFalse(ModeDetector.realTarget(2, 100, 111));
		assertFalse(ModeDetector.realTarget(0, 100, 100));
		assertFalse(ModeDetector.realTarget(2, never, 100));
	}

	@Test
	public void oneTickModeWhenConfigured()
	{
		assertEquals(Mode.ONE_TICK, detector.detect(false, true, PrayerMode.ONE_TICK, BaselineSetting.GENERAL));
	}

	@Test
	public void productionBeatsBaseline()
	{
		assertEquals(Mode.PRODUCTION,
			detector.detect(false, false, Mode.PRODUCTION, PrayerMode.LAZY_FLICK, BaselineSetting.MINIMAL));
	}

	@Test
	public void combatBeatsProduction()
	{
		assertEquals(Mode.LAZY_FLICK,
			detector.detect(false, true, Mode.PRODUCTION, PrayerMode.LAZY_FLICK, BaselineSetting.GENERAL));
	}

	@Test
	public void afkBeatsProduction()
	{
		assertEquals(Mode.AFK,
			detector.detect(true, false, Mode.PRODUCTION, PrayerMode.LAZY_FLICK, BaselineSetting.GENERAL));
	}

	@Test
	public void skillingPrefersAlch()
	{
		assertEquals(Mode.ALCH, ModeDetector.skilling(true, true));
		assertEquals(Mode.PRODUCTION, ModeDetector.skilling(false, true));
		assertEquals(Mode.ALCH, ModeDetector.skilling(true, false));
		assertEquals(null, ModeDetector.skilling(false, false));
	}

	@Test
	public void combatBeatsAlch()
	{
		assertEquals(Mode.LAZY_FLICK,
			detector.detect(false, true, Mode.ALCH, PrayerMode.LAZY_FLICK, BaselineSetting.GENERAL));
	}

	@Test
	public void skillingOrder()
	{
		assertEquals(Mode.ALCH, ModeDetector.skilling(true, true, true));
		assertEquals(Mode.PRODUCTION, ModeDetector.skilling(false, true, true));
		assertEquals(Mode.GATHERING, ModeDetector.skilling(false, false, true));
		assertEquals(null, ModeDetector.skilling(false, false, false));
		assertEquals(Mode.ALCH, ModeDetector.skilling(true, true));
		assertEquals(Mode.PRODUCTION, ModeDetector.skilling(false, true));
		assertEquals(null, ModeDetector.skilling(false, false));
	}

	@Test
	public void combatAndAfkBeatGathering()
	{
		assertEquals(Mode.LAZY_FLICK,
			detector.detect(false, true, Mode.GATHERING, PrayerMode.LAZY_FLICK, BaselineSetting.GENERAL));
		assertEquals(Mode.AFK,
			detector.detect(true, false, Mode.GATHERING, PrayerMode.LAZY_FLICK, BaselineSetting.GENERAL));
		assertEquals(Mode.GATHERING,
			detector.detect(false, false, Mode.GATHERING, PrayerMode.LAZY_FLICK, BaselineSetting.MINIMAL));
	}

	@Test
	public void agilityOrder()
	{
		assertEquals(Mode.AGILITY, ModeDetector.skilling(false, false, true, true));
		assertEquals(Mode.PRODUCTION, ModeDetector.skilling(false, true, true, true));
		assertEquals(Mode.ALCH, ModeDetector.skilling(true, false, true, false));
		assertEquals(Mode.GATHERING, ModeDetector.skilling(false, false, false, true));
		assertEquals(null, ModeDetector.skilling(false, false, false, false));
		assertEquals(Mode.GATHERING, ModeDetector.skilling(false, false, true));
		assertEquals(Mode.ALCH, ModeDetector.skilling(true, true, true));
	}
}
