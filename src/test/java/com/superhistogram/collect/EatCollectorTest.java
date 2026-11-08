package com.superhistogram.collect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.superhistogram.model.Timeline;
import com.superhistogram.model.combat.AttackTimer;
import com.superhistogram.model.combat.FoodKind;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class EatCollectorTest
{
	private Timeline tl;
	private AttackTimer timer;
	private EatCollector c;

	private static Map<Integer, Integer> inv(int shark, int karam)
	{
		Map<Integer, Integer> m = new HashMap<>();
		m.put(385, shark);
		m.put(3144, karam);
		return m;
	}

	@Before
	public void setUp()
	{
		tl = new Timeline(512);
		timer = new AttackTimer();
		timer.onAttack(100, 4);
		c = new EatCollector(tl, timer);
		c.onInventory(100, inv(4, 2));
	}

	@Test
	public void confirmedEatRecordsCost()
	{
		c.onEatClick(101, 385);
		c.onInventory(102, inv(3, 2));
		// Eaten on the tick the food leaves the bag, a tick after the click.
		assertEquals(FoodKind.NORMAL, tl.peek(102).eat);
		assertTrue(tl.peek(101) == null || tl.peek(101).eat == null);
		assertEquals(3, tl.peek(102).eatCost);
	}

	@Test
	public void failedEatIgnored()
	{
		c.onEatClick(101, 385);
		c.onInventory(102, inv(4, 2));
		c.onInventory(103, inv(4, 2));
		assertTrue(tl.peek(101) == null || tl.peek(101).eat == null);
		assertEquals(104, timer.readyTick());
	}

	@Test
	public void spamClicksCountOnce()
	{
		c.onEatClick(101, 385);
		c.onEatClick(102, 385);
		c.onInventory(102, inv(3, 2));
		c.onInventory(103, inv(3, 2));
		assertEquals(FoodKind.NORMAL, tl.peek(102).eat);
		assertTrue(tl.peek(103) == null || tl.peek(103).eat == null);
		assertEquals(107, timer.readyTick());
	}

	@Test
	public void comboRecordedOnce()
	{
		c.onEatClick(101, 385);
		c.onEatClick(101, 3144);
		c.onInventory(102, inv(3, 1));
		assertEquals(FoodKind.COMBO, tl.peek(102).eat);
		assertEquals(5, tl.peek(102).eatCost);
	}

	@Test
	public void staleClickExpires()
	{
		c.onEatClick(101, 385);
		c.onInventory(104, inv(3, 2));
		assertTrue(tl.peek(101) == null || tl.peek(101).eat == null);
	}
}
