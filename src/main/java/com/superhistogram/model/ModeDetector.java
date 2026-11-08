package com.superhistogram.model;

import com.superhistogram.model.combat.PrayerMode;

/** Picks the bar's view: AFK wins, then combat; otherwise the baseline setting. */
public final class ModeDetector
{
	public Mode detect(boolean afk, boolean inCombat, PrayerMode prayerMode, BaselineSetting baseline)
	{
		if (afk)
		{
			return Mode.AFK;
		}
		if (inCombat)
		{
			// ONE_TICK maps here too until its own view exists.
			return Mode.LAZY_FLICK;
		}
		return baseline == BaselineSetting.MINIMAL ? Mode.MINIMAL : Mode.GENERAL;
	}

	/** In combat: interacting with an NPC, or a hit on you or your own attack within the last 10 ticks. */
	public static boolean inCombat(boolean interactingNpc, int tick, int lastHitTick, int lastAttackTick)
	{
		return interactingNpc || tick - lastHitTick <= 10 || tick - lastAttackTick <= 10;
	}
}
