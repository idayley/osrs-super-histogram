package com.superhistogram.model;

import com.superhistogram.model.combat.PrayerMode;

/** Picks the bar's view: AFK wins, then combat, then a skilling view; otherwise the baseline setting. */
public final class ModeDetector
{
	public Mode detect(boolean afk, boolean inCombat, PrayerMode prayerMode, BaselineSetting baseline)
	{
		return detect(afk, inCombat, null, prayerMode, baseline);
	}

	/** As below, but a pinned (non-AUTO) setting overrides detection completely, AFK included. */
	public Mode detect(ModeSetting pinned, boolean afk, boolean inCombat, Mode skilling, PrayerMode prayerMode,
		BaselineSetting baseline)
	{
		if (pinned != null && pinned.mode() != null)
		{
			return pinned.mode();
		}
		return detect(afk, inCombat, skilling, prayerMode, baseline);
	}

	/**
	 * @param skilling the skilling view to show (PRODUCTION, ALCH, AGILITY, GATHERING) or null; it beats the baseline
	 *                 but not AFK or combat
	 */
	public Mode detect(boolean afk, boolean inCombat, Mode skilling, PrayerMode prayerMode, BaselineSetting baseline)
	{
		if (afk)
		{
			return Mode.AFK;
		}
		if (inCombat)
		{
			return prayerMode == PrayerMode.ONE_TICK ? Mode.ONE_TICK : Mode.LAZY_FLICK;
		}
		if (skilling != null)
		{
			return skilling;
		}
		return baseline == BaselineSetting.MINIMAL ? Mode.MINIMAL : Mode.GENERAL;
	}

	/** The skilling view to show: high alch beats production; null when neither is going on. */
	public static Mode skilling(boolean alching, boolean producing)
	{
		return skilling(alching, producing, false);
	}

	/** As above; gathering comes after high alch and production. */
	public static Mode skilling(boolean alching, boolean producing, boolean gathering)
	{
		return skilling(alching, producing, false, gathering);
	}

	/** High alch, then production, agility, gathering. */
	public static Mode skilling(boolean alching, boolean producing, boolean agility, boolean gathering)
	{
		return alching ? Mode.ALCH : producing ? Mode.PRODUCTION : agility ? Mode.AGILITY
			: gathering ? Mode.GATHERING : null;
	}

	/** In combat: interacting with an NPC, or a hit on you or your own attack within the last 10 ticks. */
	public static boolean inCombat(boolean interactingNpc, int tick, int lastHitTick, int lastAttackTick)
	{
		return interactingNpc || tick - lastHitTick <= 10 || tick - lastAttackTick <= 10;
	}
}
