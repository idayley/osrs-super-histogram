package com.superhistogram.model;

/** The standard OSRS experience table, levels 1 to 99. */
public final class Levels
{
	public static final int MAX_LEVEL = 99;
	/** XP[level] = total XP needed for that level; index 0 unused. */
	private static final long[] XP = new long[MAX_LEVEL + 1];

	static
	{
		long points = 0;
		for (int level = 1; level <= MAX_LEVEL; level++)
		{
			XP[level] = points / 4;
			points += (long) Math.floor(level + 300.0 * Math.pow(2.0, level / 7.0));
		}
	}

	private Levels()
	{
	}

	/** Level reached with this much XP, 1..99. */
	public static int levelFor(long xp)
	{
		int level = 1;
		while (level < MAX_LEVEL && xp >= XP[level + 1])
		{
			level++;
		}
		return level;
	}

	/** Total XP needed for the level; clamped to 1..99. */
	public static long xpForLevel(int level)
	{
		return XP[Math.max(1, Math.min(MAX_LEVEL, level))];
	}
}
