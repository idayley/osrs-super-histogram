package com.superhistogram.model.combat;

public final class EatCost
{
	private EatCost()
	{
	}

	/** Ticks added to the next attack: eating while the weapon is already ready is not free, but idle time absorbs it. */
	public static int cost(int tick, int readyTick, FoodKind k)
	{
		int rem = readyTick - tick;
		return Math.max(0, rem + k.add) - Math.max(0, rem);
	}
}
