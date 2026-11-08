package com.superhistogram.model.combat;

public final class AttackTimer
{
	public static final int DEFAULT_SPEED = 4;
	public static final int LATE_MAX = 3;
	public static final int NEVER = Integer.MIN_VALUE / 2;

	private int readyTick = NEVER;
	private int lastAttackTick = NEVER;
	private int eatTick = NEVER;
	private int preEatReady = NEVER;
	private FoodKind eatKind;

	/** An attack animation can start one tick before the weapon is ready. */
	public boolean canAttack(int tick)
	{
		return tick >= readyTick - 1;
	}

	/** Returns the late ticks (1..LATE_MAX), else 0, and starts the next cooldown. */
	public int onAttack(int tick, int weaponSpeed)
	{
		int late = tick - readyTick;
		int result = late >= 1 && late <= LATE_MAX ? late : 0;
		readyTick = tick + weaponSpeed;
		lastAttackTick = tick;
		return result;
	}

	public int onEat(int tick, FoodKind k)
	{
		FoodKind kind = k;
		if (eatTick == tick && eatKind != null)
		{
			readyTick = preEatReady;
			kind = FoodKind.merge(eatKind, k);
		}
		else
		{
			preEatReady = readyTick;
		}
		int cost = EatCost.cost(tick, readyTick, kind);
		readyTick += kind.add;
		eatTick = tick;
		eatKind = kind;
		return cost;
	}

	public int readyTick()
	{
		return readyTick;
	}

	public int lastAttackTick()
	{
		return lastAttackTick;
	}
}
