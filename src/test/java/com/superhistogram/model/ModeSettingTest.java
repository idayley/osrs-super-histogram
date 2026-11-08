package com.superhistogram.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.superhistogram.model.combat.PrayerMode;
import org.junit.Test;

public class ModeSettingTest
{
	private final ModeDetector d = new ModeDetector();

	@Test
	public void everyPinnedSettingMapsToItsMode()
	{
		assertNull(ModeSetting.AUTO.mode());
		for (ModeSetting s : ModeSetting.values())
		{
			if (s != ModeSetting.AUTO)
			{
				assertEquals(s.name(), s.mode().name());
			}
		}
		assertEquals(Mode.values().length, ModeSetting.values().length - 1);
	}

	@Test
	public void pinnedProductionWhileIdleStillShowsProduction()
	{
		assertEquals(Mode.PRODUCTION,
			d.detect(ModeSetting.PRODUCTION, true, false, null, PrayerMode.LAZY_FLICK, BaselineSetting.GENERAL));
	}

	@Test
	public void pinnedModeBeatsCombat()
	{
		assertEquals(Mode.GENERAL,
			d.detect(ModeSetting.GENERAL, false, true, Mode.ALCH, PrayerMode.ONE_TICK, BaselineSetting.MINIMAL));
	}

	@Test
	public void pinnedAfkShowsAfkEvenWhenActive()
	{
		assertEquals(Mode.AFK,
			d.detect(ModeSetting.AFK, false, false, null, PrayerMode.LAZY_FLICK, BaselineSetting.GENERAL));
	}

	@Test
	public void autoAndNullAreUnchanged()
	{
		assertEquals(Mode.AFK,
			d.detect(ModeSetting.AUTO, true, true, Mode.ALCH, PrayerMode.LAZY_FLICK, BaselineSetting.GENERAL));
		assertEquals(Mode.ONE_TICK,
			d.detect(null, false, true, null, PrayerMode.ONE_TICK, BaselineSetting.GENERAL));
		assertEquals(Mode.ALCH,
			d.detect(ModeSetting.AUTO, false, false, Mode.ALCH, PrayerMode.LAZY_FLICK, BaselineSetting.MINIMAL));
		assertEquals(Mode.MINIMAL,
			d.detect(ModeSetting.AUTO, false, false, null, PrayerMode.LAZY_FLICK, BaselineSetting.MINIMAL));
	}
}
