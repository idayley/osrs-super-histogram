package com.superhistogram.model;

import com.superhistogram.model.combat.PrayerMode;

/**
 * Picks the bar's view: combat wins, then AFK, then a skilling view; otherwise the last activity shown, or the
 * baseline setting before there has been one.
 */
public final class ModeDetector
{
	/** The last activity shown (a skilling or combat view); the bar sticks to it until another starts. */
	private Mode last;

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
		Mode m = detect(afk, inCombat, skilling, prayerMode, baseline);
		if (m == Mode.GENERAL || m == Mode.MINIMAL)
		{
			// Nothing going on right now (banking, walking, a pause): keep showing what you were last doing.
			return last != null ? last : m;
		}
		if (m != Mode.AFK)
		{
			last = m;
		}
		return m;
	}

	/** Forget the last activity (a new login): the bar starts on the baseline again. */
	public void reset()
	{
		last = null;
	}

	/**
	 * @param skilling the skilling view to show (PRODUCTION, ALCH, AGILITY, GATHERING) or null; it beats the baseline
	 *                 but not AFK or combat
	 */
	public Mode detect(boolean afk, boolean inCombat, Mode skilling, PrayerMode prayerMode, BaselineSetting baseline)
	{
		// Combat first: an auto-retaliate fight with no clicks is still a fight (and the game doesn't idle-log
		// you out mid-combat either).
		if (inCombat)
		{
			return prayerMode == PrayerMode.ONE_TICK ? Mode.ONE_TICK : Mode.LAZY_FLICK;
		}
		if (afk)
		{
			return Mode.AFK;
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
		return skilling(alching, producing, agility, false, gathering);
	}

	/** High alch, then production, agility, thieving, gathering. */
	public static Mode skilling(boolean alching, boolean producing, boolean agility, boolean thieving,
		boolean gathering)
	{
		return skilling(alching, producing, agility, thieving, false, gathering);
	}

	/** High alch, then another skilling spell, then the rest as below. */
	public static Mode skilling(boolean alching, boolean casting, boolean producing, boolean agility,
		boolean thieving, boolean hunting, boolean gathering)
	{
		return skilling(alching, casting, producing, agility, false, thieving, hunting, gathering);
	}

	/** As above, with a loop (Runecraft trips, a farm or bird house run) right after agility. */
	public static Mode skilling(boolean alching, boolean casting, boolean producing, boolean agility,
		boolean runs, boolean thieving, boolean hunting, boolean gathering)
	{
		return alching ? Mode.ALCH : casting ? Mode.CASTING : producing ? Mode.PRODUCTION : agility ? Mode.AGILITY
			: runs ? Mode.RUNS : skilling(false, false, false, thieving, hunting, gathering);
	}

	/** High alch, then production, agility, thieving, hunter traps, gathering. */
	public static Mode skilling(boolean alching, boolean producing, boolean agility, boolean thieving,
		boolean hunting, boolean gathering)
	{
		return alching ? Mode.ALCH : producing ? Mode.PRODUCTION : agility ? Mode.AGILITY
			: thieving ? Mode.THIEVING : hunting ? Mode.HUNTER : gathering ? Mode.GATHERING : null;
	}

	/** Ticks a hit on the target or an own attack keeps combat on. */
	public static final int COMBAT_WINDOW = 10;

	/**
	 * In combat: the player targets a real combat NPC (see {@link #realTarget}), or made an own attack within the
	 * last 10 ticks. Hits on the player never count by themselves (Wintertodt, Tempoross, thieving stuns, falls).
	 */
	public static boolean inCombat(boolean realTarget, int tick, int lastAttackTick)
	{
		return inCombat(realTarget, tick, lastAttackTick, COMBAT_WINDOW);
	}

	/** As above, holding combat for {@code holdTicks} after the last combat activity (an attack or own hit). */
	public static boolean inCombat(boolean realTarget, int tick, int lastActivityTick, int holdTicks)
	{
		return realTarget || tick - lastActivityTick <= holdTicks;
	}

	/** Seconds to whole game ticks (0.6 s each), rounded. */
	public static int ticks(int seconds)
	{
		return Math.round(Math.max(0, seconds) / 0.6f);
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
