package com.superhistogram.model.combat;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class EatCostTest
{
	@Test
	public void normalMidCycleCosts3()
	{
		assertEquals(3, EatCost.cost(101, 104, FoodKind.NORMAL));
	}

	@Test
	public void fastCosts2()
	{
		assertEquals(2, EatCost.cost(101, 104, FoodKind.FAST));
		assertEquals(2, EatCost.cost(101, 104, FoodKind.KARAMBWAN));
	}

	@Test
	public void comboCosts5()
	{
		assertEquals(5, EatCost.cost(101, 104, FoodKind.COMBO));
	}

	@Test
	public void afterIdleIsFree()
	{
		assertEquals(0, EatCost.cost(110, 107, FoodKind.NORMAL));
	}

	@Test
	public void partialWhenNearlyReady()
	{
		assertEquals(2, EatCost.cost(105, 104, FoodKind.NORMAL));
	}
}
