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

	/** Ticks a hit on the target or an own attack keeps combat on. */
	public static final int COMBAT_WINDOW = 10;

	/**
	 * In combat: the player targets a real combat NPC (see {@link #realTarget}), or made an own attack within the
	 * last 10 ticks. Hits on the player never count by themselves (Wintertodt, Tempoross, thieving stuns, falls).
	 */
	public static boolean inCombat(boolean realTarget, int tick, int lastAttackTick)
	{
		return realTarget || tick - lastAttackTick <= COMBAT_WINDOW;
	}

	/**
	 * A real combat target: an NPC with a combat level (fishing spots, bankers and most quest NPCs have 0) that
	 * took a damage or block hitsplat within the last 10 ticks (so dialogue and pickpocketing are excluded).
	 */
	public static boolean realTarget(int combatLevel, int lastHitOnTargetTick, int tick)
	{
		return combatLevel > 0 && tick - lastHitOnTargetTick <= COMBAT_WINDOW;
	}
}
