package com.superhistogram.ui.modes;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.BarState;
import com.superhistogram.model.RightSide;
import com.superhistogram.model.skill.AlchTopRow;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.SampleStates;
import org.junit.Test;

public class AlchViewTest
{
	private static String[] lines(AlchTopRow top, RightSide side, Long... profits)
	{
		return AlchView.rightLines(SampleStates.alchText(top, side, profits));
	}

	@Test
	public void formatProfit()
	{
		assertEquals("+236", AlchView.formatProfit(236L));
		assertEquals("−40", AlchView.formatProfit(-40L));
		assertEquals("+1.2k", AlchView.formatProfit(1234L));
		assertEquals("−1.2k", AlchView.formatProfit(-1234L));
		assertEquals("?", AlchView.formatProfit(null));
	}

	@Test
	public void goldLines()
	{
		assertArrayEquals(new String[]{"+160/hr", "+160 this session"},
			lines(AlchTopRow.GOLD, RightSide.PER_HOUR, 200L, -40L));
		assertArrayEquals(new String[]{"+160", "+160/hr"},
			lines(AlchTopRow.GOLD, RightSide.SESSION, 200L, -40L));
	}

	@Test
	public void xpLines()
	{
		assertArrayEquals(new String[]{"130 xp/hr", "130 xp · level 99"},
			lines(AlchTopRow.XP, RightSide.PER_HOUR, 200L, -40L));
		assertArrayEquals(new String[]{"130 xp", "130/hr · level 99"},
			lines(AlchTopRow.XP, RightSide.SESSION, 200L, -40L));
	}

	@Test
	public void bothLines()
	{
		assertArrayEquals(new String[]{"+160/hr", "+160 this session · 130 xp/hr"},
			lines(AlchTopRow.BOTH, RightSide.PER_HOUR, 200L, -40L));
		assertArrayEquals(new String[]{"+160", "+160/hr · 130 xp"},
			lines(AlchTopRow.BOTH, RightSide.SESSION, 200L, -40L));
	}

	@Test
	public void lossTotalHasMinus()
	{
		assertEquals("−40", lines(AlchTopRow.GOLD, RightSide.SESSION, -40L)[0]);
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
		BarState gain = SampleStates.alchText(200L), loss = SampleStates.alchText(-40L);
		assertEquals(Palette.DAMAGE, AlchView.rightColor(gain));
		assertEquals(Palette.TEXT, AlchView.rightColor(loss));
		assertEquals(Palette.WHITE,
			AlchView.rightColor(SampleStates.alchText(AlchTopRow.XP, RightSide.PER_HOUR, 200L)));
	}

	@Test
	public void noTrackerNoLines()
	{
		assertArrayEquals(new String[]{"", ""}, AlchView.rightLines(SampleStates.withoutAlch(SampleStates.alchText(1L))));
	}
}
