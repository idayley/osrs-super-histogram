package com.superhistogram.ui;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import org.junit.Test;

public class ColourSettingsTest
{
	@Test
	public void changedColoursReachThePainters()
	{
		Color red = new Color(255, 0, 0);
		Colours c = Colours.DEFAULT.with(Colours.PRAYER_MELEE, red).with(Colours.skillKey("Mining"), red)
			.with(Colours.NOW_LINE, red);
		Colours prev = Palette.enter(c);
		try
		{
			assertEquals(red, Palette.prayerMelee());
			assertEquals(red, CombatPainter.prayerColor("pray-melee"));
			assertEquals(red, Palette.skill("Mining"));
			assertEquals(red, Palette.nowLine());
			// Untouched ones keep their defaults.
			assertEquals(Palette.PRAYER_RANGED, Palette.prayerRanged());
			assertEquals(Palette.defaultSkill("Fishing"), Palette.skill("Fishing"));
		}
		finally
		{
			Palette.leave(prev);
		}
		assertEquals(Palette.PRAYER_MELEE, Palette.prayerMelee());
	}

	@Test
	public void unknownSkillStaysTextColour()
	{
		assertEquals(Palette.TEXT, Palette.skill("Dungeoneering"));
	}
}
