package com.superhistogram;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import com.superhistogram.model.BarPosition;
import com.superhistogram.model.BarSize;
import com.superhistogram.model.BaselineSetting;
import com.superhistogram.model.ModeSetting;
import com.superhistogram.model.RightSide;
import com.superhistogram.model.XpDrops;
import com.superhistogram.model.combat.ActionsRow;
import com.superhistogram.model.combat.PrayerMode;
import com.superhistogram.model.skill.AlchTopRow;
import com.superhistogram.model.skill.LapCompare;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import org.junit.Test;

public class SuperHistogramConfigTest
{
	private final SuperHistogramConfig c = mock(SuperHistogramConfig.class, CALLS_REAL_METHODS);

	@Test
	public void mockupDefaults()
	{
		assertEquals(BarPosition.REPLACE_CHAT, c.position());
		assertEquals(BarSize.NORMAL, c.size());
		assertEquals(ModeSetting.AUTO, c.mode());
		assertEquals(BaselineSetting.GENERAL, c.baseline());
		assertEquals(60, c.afkAfterSeconds());
		assertTrue(c.afkWarning());
		assertTrue(c.showRunEnergy());
		assertTrue(c.showLoginTime());
		assertEquals(SuperHistogramConfig.Metronome.OFF, c.metronome());
		assertTrue(c.blinkWhenDone());
		assertEquals(PrayerMode.LAZY_FLICK, c.prayerMode());
		assertEquals(new net.runelite.client.config.Keybind(KeyEvent.VK_F, 0), c.beatKey());
		assertEquals(ActionsRow.ATTACKS, c.actionsRow());
		assertTrue(c.showEatCosts());
		assertTrue(c.hullFlash());
		assertEquals(false, c.hullFlashAtBosses());
		assertEquals(SuperHistogramConfig.HullFlashMisses.FAINT, c.hullFlashMisses());
		assertEquals(XpDrops.NUMBERS, c.xpDrops());
		assertTrue(c.xpDots());
		assertEquals(AlchTopRow.GOLD, c.alchTopRow());
		assertEquals(RightSide.PER_HOUR, c.rightSide());
		assertTrue(c.timeToFinish());
		assertTrue(c.wastedTicks());
		assertEquals(LapCompare.BEST, c.lapCompare());
		assertEquals(new Color(0xffd23f), c.damageColour());
		assertEquals(new Color(0x5ee0c8), c.mintColour());
		assertEquals(new Color(0xe8c86a), c.runEnergyColour());
		assertEquals(new Color(20, 23, 27, Math.round(0.92f * 255)), c.backgroundColour());
	}

	private static List<Method> items()
	{
		List<Method> out = new ArrayList<>();
		for (Method m : SuperHistogramConfig.class.getDeclaredMethods())
		{
			if (m.isAnnotationPresent(ConfigItem.class))
			{
				out.add(m);
			}
		}
		out.sort(Comparator.comparingInt(m -> m.getAnnotation(ConfigItem.class).position()));
		return out;
	}

	private static List<String> keysIn(String section)
	{
		List<String> keys = new ArrayList<>();
		for (Method m : items())
		{
			if (m.getAnnotation(ConfigItem.class).section().equals(section))
			{
				keys.add(m.getAnnotation(ConfigItem.class).keyName());
			}
		}
		return keys;
	}

	@Test
	public void itemsFollowTheMockupOrderPerSection()
	{
		assertEquals(Arrays.asList("position", "size", "mode", "baseline", "afkAfterSeconds", "afkWarning",
			"showRunEnergy", "showLoginTime"), keysIn("bar"));
		assertEquals(Arrays.asList("metronome", "blinkWhenDone"), keysIn("feedback"));
		assertEquals(Arrays.asList("prayerMode", "beatKey", "actionsRow", "showEatCosts", "hullFlash",
			"hullFlashAtBosses", "hullFlashMisses"), keysIn("combat"));
		assertEquals(Arrays.asList("xpDrops", "xpDots", "alchTopRow", "rightSide"), keysIn("xpGold"));
		assertEquals(Arrays.asList("timeToFinish", "wastedTicks", "lapCompare"), keysIn("skilling"));
		assertEquals(Arrays.asList("damageColour", "mintColour", "runEnergyColour", "backgroundColour"),
			keysIn("colours"));
	}

	@Test
	public void sectionsAreInOrderAndColoursStartsClosed()
	{
		List<java.lang.reflect.Field> fields = new ArrayList<>();
		for (java.lang.reflect.Field f : SuperHistogramConfig.class.getDeclaredFields())
		{
			if (f.isAnnotationPresent(ConfigSection.class))
			{
				fields.add(f);
			}
		}
		fields.sort(Comparator.comparingInt(f -> f.getAnnotation(ConfigSection.class).position()));
		List<String> names = new ArrayList<>();
		for (java.lang.reflect.Field f : fields)
		{
			names.add(f.getAnnotation(ConfigSection.class).name());
			assertEquals(f.getAnnotation(ConfigSection.class).name().equals("Colours"),
				f.getAnnotation(ConfigSection.class).closedByDefault());
		}
		assertEquals(Arrays.asList("Bar", "Feedback", "Combat", "XP & gold", "Skilling", "Colours"), names);
	}

	@Test
	public void existingKeyNamesAreUnchanged()
	{
		List<String> keys = new ArrayList<>();
		for (Method m : items())
		{
			keys.add(m.getAnnotation(ConfigItem.class).keyName());
		}
		for (String k : Arrays.asList("baseline", "afkAfterSeconds", "afkWarning", "showRunEnergy", "showLoginTime",
			"metronome", "blinkWhenDone", "beatKey", "prayerMode", "actionsRow", "showEatCosts", "hullFlash",
			"hullFlashAtBosses", "hullFlashMisses", "timeToFinish", "wastedTicks", "xpDrops", "xpDots",
			"alchTopRow", "rightSide", "lapCompare"))
		{
			assertTrue(k, keys.contains(k));
		}
	}

	@Test
	public void mockupLabels()
	{
		for (Method m : items())
		{
			assertNotNull(m.getAnnotation(ConfigItem.class).name());
		}
		assertEquals("Baseline when idle", label("baseline"));
		assertEquals("Switch to AFK after", label("afkAfterSeconds"));
		assertEquals("Flash enemy on hit", label("hullFlash"));
		assertEquals("Right side shows", label("rightSide"));
		assertEquals("Position", label("position"));
	}

	private static String label(String key)
	{
		for (Method m : items())
		{
			if (m.getAnnotation(ConfigItem.class).keyName().equals(key))
			{
				return m.getAnnotation(ConfigItem.class).name();
			}
		}
		return null;
	}

	@Test
	public void coloursAreAlpha() throws Exception
	{
		for (String n : Arrays.asList("damageColour", "mintColour", "runEnergyColour", "backgroundColour"))
		{
			assertTrue(n, SuperHistogramConfig.class.getMethod(n).isAnnotationPresent(Alpha.class));
		}
	}
}
