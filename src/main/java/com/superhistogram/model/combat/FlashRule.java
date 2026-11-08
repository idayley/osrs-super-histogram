package com.superhistogram.model.combat;

/** Whether, and how, to flash an enemy's hull once a hitsplat has landed. */
public final class FlashRule
{
	public enum Flash
	{
		NONE, HIT, MISS
	}

	private FlashRule()
	{
	}

	public static Flash decide(boolean hullFlash, boolean atBosses, boolean isBoss, boolean miss, boolean missesFaint)
	{
		if (!hullFlash || (isBoss && !atBosses))
		{
			return Flash.NONE;
		}
		if (miss)
		{
			return missesFaint ? Flash.MISS : Flash.NONE;
		}
		return Flash.HIT;
	}
}
