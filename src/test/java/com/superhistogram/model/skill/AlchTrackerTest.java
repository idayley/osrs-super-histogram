package com.superhistogram.model.skill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class AlchTrackerTest
{
	private static final List<XpDrop> MAGIC_65 = Collections.singletonList(new XpDrop("Magic", 65));
	private static final List<XpDrop> NONE = Collections.emptyList();

	private final Timeline t = new Timeline(512);
	private final AlchTracker a = new AlchTracker(t);

	/** Click on {@code clickTick}, Magic 65 on {@code castTick}. */
	private void cast(int clickTick, int castTick, Long coins)
	{
		a.onCastClick(clickTick, coins);
		for (int k = clickTick; k < castTick; k++)
		{
			a.onTick(k, NONE);
		}
		a.onTick(castTick, MAGIC_65);
	}

	@Test
	public void castConfirmedByMagic65()
	{
		a.onCastClick(100, 200L);
		a.onTick(101, MAGIC_65);
		assertTrue(t.peek(101).cast);
		assertEquals(Long.valueOf(200), t.peek(101).castCoins);
		assertEquals(1, a.casts());
		assertEquals(101, a.lastCastTick());
		assertEquals(106, a.nextReadyTick());
	}

	@Test
	public void noCastYet()
	{
		assertEquals(-1, a.lastCastTick());
		assertEquals(-1, a.nextReadyTick());
	}

	@Test
	public void xpWithoutClickIsNoCast()
	{
		a.onTick(101, MAGIC_65);
		assertEquals(0, a.casts());
	}

	@Test
	public void clickWithoutXpExpires()
	{
		a.onCastClick(100, 200L);
		a.onTick(101, NONE);
		a.onTick(102, NONE);
		a.onTick(103, MAGIC_65);
		assertEquals(0, a.casts());
	}

	@Test
	public void otherMagicXpIsNoCast()
	{
		a.onCastClick(100, 200L);
		a.onTick(101, Collections.singletonList(new XpDrop("Magic", 31)));
		assertEquals(0, a.casts());
		a.onTick(102, Collections.singletonList(new XpDrop("Fishing", 65)));
		assertEquals(0, a.casts());
	}

	@Test
	public void singleCastDoesNotShow()
	{
		cast(100, 101, 200L);
		assertFalse(a.showing(102));
		cast(105, 106, 200L);
		assertTrue(a.showing(106));
		assertTrue(a.showing(116));
		assertFalse(a.showing(117));
	}

	@Test
	public void pauseBetweenCastsDoesNotShow()
	{
		cast(100, 101, 200L);
		cast(129, 130, 200L);
		assertFalse(a.showing(130));
	}

	@Test
	public void lostTicksBetweenCasts()
	{
		cast(100, 101, 1L);
		cast(107, 108, 1L);
		assertEquals(2, t.peek(108).castLost);
	}

	@Test
	public void onTimeAndPausedCastsLoseNothing()
	{
		cast(100, 101, 1L);
		cast(105, 106, 1L);
		assertEquals(0, t.peek(106).castLost);
		cast(129, 130, 1L);
		assertEquals(0, t.peek(130).castLost);
	}

	@Test
	public void tenLostTicksIsStillLost()
	{
		cast(100, 101, 1L);
		cast(115, 116, 1L);
		assertEquals(10, t.peek(116).castLost);
		cast(131, 132, 1L);
		assertEquals(0, t.peek(132).castLost);
	}

	@Test
	public void unknownPriceExcludedFromGold()
	{
		cast(100, 101, 200L);
		cast(105, 106, null);
		assertEquals(200, a.sessionGold());
		assertEquals(1, a.unpricedCasts());
		assertEquals(2, a.casts());
		assertNull(t.peek(106).castCoins);
		assertTrue(t.peek(106).cast);
	}

	@Test
	public void sessionGoldAndPerHour()
	{
		cast(100, 101, 200L);
		cast(105, 106, -40L);
		assertEquals(160, a.sessionGold());
		assertEquals(160, a.goldPerHour(3_600_000));
		assertEquals(0, a.goldPerHour(0));
	}

	@Test
	public void rebindKeepsGold()
	{
		cast(100, 101, 200L);
		cast(105, 106, 200L);
		a.rebind(new Timeline(512));
		assertEquals(400, a.sessionGold());
		assertEquals(-1, a.lastCastTick());
		assertFalse(a.showing(107));
	}

	@Test
	public void resetClearsEverything()
	{
		cast(100, 101, 200L);
		a.reset();
		assertEquals(0, a.sessionGold());
		assertEquals(0, a.casts());
		assertEquals(-1, a.lastCastTick());
	}

	@Test
	public void oldestPendingClickIsConfirmedFirst()
	{
		a.onCastClick(100, 1L);
		a.onCastClick(101, 2L);
		a.onTick(101, MAGIC_65);
		assertEquals(Long.valueOf(1), t.peek(101).castCoins);
	}
}
