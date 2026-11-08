package com.superhistogram.model.skill;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.Span;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ThieveRunTest
{
	private static final int POUCH = net.runelite.api.gameval.ItemID.PICKPOCKET_COIN_POUCH_KNIGHT;
	private static final List<XpDrop> NONE = Collections.emptyList();
	private static final List<XpDrop> XP = Collections.singletonList(new XpDrop("Thieving", 84));

	private final Timeline tl = new Timeline(1024);
	private final ThieveRun th = new ThieveRun(tl);
	private final Map<Integer, Integer> inv = new HashMap<>();

	private void tick(int t, boolean xp, boolean hit)
	{
		tl.at(t);
		th.onTick(t, xp ? XP : NONE, hit, new HashMap<>(inv), 28 - inv.size());
	}

	/** Click acted on at t, success at t + 1 with one more pouch. */
	private void steal(int t)
	{
		tick(t - 1, false, false);
		th.onAttempt(t, "Knight of Ardougne", false);
		tick(t, false, false);
		inv.merge(POUCH, 1, Integer::sum);
		tick(t + 1, true, false);
	}

	@Test
	public void successesCountPouchesAndShowTheView()
	{
		inv.put(POUCH, 10);
		tick(0, false, false);
		steal(2);
		steal(4);
		assertTrue(th.showing(5));
		assertTrue(th.countsPouches());
		assertEquals(12, th.count());
		assertEquals(28, th.capacity());
		assertEquals(2, th.successes());
		assertEquals(2f, th.ticksPerSuccess(), 0.001f);
		assertTrue(tl.peek(5).stolen);
		assertFalse(th.showing(5 + ThieveRun.LINGER + 1));
	}

	@Test
	public void aHitAfterAnAttemptIsAStunThatBlocksClicks()
	{
		inv.put(POUCH, 1);
		tick(0, false, false);
		steal(2);
		steal(4);
		th.onAttempt(6, "Knight of Ardougne", false);
		tick(6, false, false);
		tick(7, false, true);
		assertEquals(1, th.fails());
		assertTrue(th.stunned(8));
		Span stun = null;
		for (Span s : tl.spansBetween(0, 20))
		{
			if (ThieveRun.STUN.equals(s.kind))
			{
				stun = s;
			}
		}
		assertEquals(8, stun.startTick);
		assertEquals(7 + ThieveRun.STUN_TICKS, stun.endTick);
		assertEquals("stunned", stun.label);
		// A click while stunned does nothing.
		th.onAttempt(10, "Knight of Ardougne", false);
		tick(10, false, false);
		tick(11, true, false);
		assertEquals(75, th.successRate());
		assertFalse(th.stunned(16));
	}

	@Test
	public void theCapMessageIsReadOnceThePouchesAreIn()
	{
		inv.put(POUCH, 27);
		tick(0, false, false);
		steal(2);
		// The message comes before this tick's inventory has the 28th pouch... it was counted above.
		th.onMessage("You need to empty your coin pouches before you can continue pickpocketing.");
		tick(4, false, false);
		assertEquals(28, th.capacity());
		assertEquals(28, th.count());
	}

	@Test
	public void moreThan28PouchesMeansADiaryCap()
	{
		inv.put(POUCH, 30);
		tick(0, false, false);
		steal(2);
		assertEquals(56, th.capacity());
	}

	@Test
	public void stallsAndSeedsCountItemsToAFullInventory()
	{
		tick(0, false, false);
		th.onAttempt(2, "Fruit Stall", true);
		tick(2, false, false);
		inv.put(1963, 1);
		tick(3, true, false);
		assertFalse(th.countsPouches());
		assertTrue(th.stall());
		assertEquals(1, th.count());
		assertEquals(28, th.capacity());
	}
}
