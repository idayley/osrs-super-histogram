package com.superhistogram.ui.modes;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.RightSide;
import com.superhistogram.model.skill.AlchTopRow;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.SampleStates;
import org.junit.Test;

public class AlchViewTest
{
	private static String[] lines(AlchTopRow top, RightSide side, Long... coins)
	{
		return AlchView.rightLines(SampleStates.alchText(top, side, coins));
	}

	@Test
	public void formatCoins()
	{
		assertEquals("+236", AlchView.formatCoins(236L));
		assertEquals("+1.2k", AlchView.formatCoins(1234L));
		assertEquals("?", AlchView.formatCoins(null));
	}

	@Test
	public void castCoinsAreExact()
	{
		assertEquals("+236", AlchView.formatCastCoins(236L));
		assertEquals("+2,360", AlchView.formatCastCoins(2360L));
		assertEquals("+65,000", AlchView.formatCastCoins(65000L));
		assertEquals("?", AlchView.formatCastCoins(null));
	}

	@Test
	public void goldLines()
	{
		assertArrayEquals(new String[]{"+240/hr", "+240 this session"},
			lines(AlchTopRow.GOLD, RightSide.PER_HOUR, 200L, 40L));
		assertArrayEquals(new String[]{"+240", "+240/hr"},
			lines(AlchTopRow.GOLD, RightSide.SESSION, 200L, 40L));
	}

	@Test
	public void xpLines()
	{
		assertArrayEquals(new String[]{"130 xp/hr", "130 xp · level 99"},
			lines(AlchTopRow.XP, RightSide.PER_HOUR, 200L, 40L));
		assertArrayEquals(new String[]{"130 xp", "130/hr · level 99"},
			lines(AlchTopRow.XP, RightSide.SESSION, 200L, 40L));
	}

	@Test
	public void bothLines()
	{
		assertArrayEquals(new String[]{"+240/hr", "+240 this session · 130 xp/hr"},
			lines(AlchTopRow.BOTH, RightSide.PER_HOUR, 200L, 40L));
		assertArrayEquals(new String[]{"+240", "+240/hr · 130 xp"},
			lines(AlchTopRow.BOTH, RightSide.SESSION, 200L, 40L));
	}

	@Test
	public void unpricedNoted()
	{
		String small = lines(AlchTopRow.GOLD, RightSide.PER_HOUR, 200L, null)[1];
		assertTrue(small, small.endsWith(" · 1 unpriced"));
	}

	@Test
	public void colours()
	{
		assertEquals(Palette.DAMAGE, AlchView.rightColor(SampleStates.alchText(200L)));
		assertEquals(Palette.WHITE,
			AlchView.rightColor(SampleStates.alchText(AlchTopRow.XP, RightSide.PER_HOUR, 200L)));
	}

	@Test
	public void noTrackerNoLines()
	{
		assertArrayEquals(new String[]{"", ""}, AlchView.rightLines(SampleStates.withoutAlch(SampleStates.alchText(1L))));
	}

	@Test
	public void ratesAreDashInTheFirstMinute()
	{
		com.superhistogram.model.BarState s = SampleStates.alchText(AlchTopRow.BOTH, RightSide.PER_HOUR, 200L, 40L);
		s.clock.resetSession(s.nowMs - 30_000);
		assertArrayEquals(new String[]{"\u2014/hr", "+240 this session · \u2014 xp/hr"}, AlchView.rightLines(s));
		s = SampleStates.alchText(AlchTopRow.XP, RightSide.SESSION, 200L, 40L);
		s.clock.resetSession(s.nowMs - 30_000);
		assertArrayEquals(new String[]{"130 xp", "\u2014/hr · level 99"}, AlchView.rightLines(s));
	}
}
