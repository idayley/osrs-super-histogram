package com.superhistogram.model.skill;

import com.superhistogram.model.Levels;
import com.superhistogram.model.XpDrop;
import java.util.ArrayDeque;
import java.util.List;

/**
 * Your slayer task while you fight it: the monsters left (the game's own count) and the Slayer XP a kill gives,
 * learned from your kills (only task monsters give Slayer XP, so that is also how the bar knows you are on task).
 * From those, where your Slayer level will be when the task is done. Read by the render thread: public methods are
 * synchronized.
 */
public final class SlayerTask
{
	public static final String SKILL = "Slayer";
	/** The task view stays this long after the last Slayer XP. */
	public static final int LINGER = 200;
	/** Kills kept for the XP per kill. */
	static final int KILLS = 8;

	private int left;
	private int lastKill = -1;
	private final ArrayDeque<Integer> kills = new ArrayDeque<>();

	/**
	 * @param count the monsters left on your task (0 with no task)
	 */
	public synchronized void onTick(int tick, int count, List<XpDrop> drops)
	{
		left = Math.max(0, count);
		for (XpDrop d : drops)
		{
			if (SKILL.equals(d.skill) && d.amount > 0)
			{
				kills.addLast(d.amount);
				while (kills.size() > KILLS)
				{
					kills.pollFirst();
				}
				lastKill = tick;
			}
		}
		if (left == 0)
		{
			// Task done (or none): the next task's monsters give other XP.
			kills.clear();
			lastKill = -1;
		}
	}

	/** True while fighting your task: monsters left and a Slayer kill recently. */
	public synchronized boolean showing(int tick)
	{
		return left > 0 && lastKill >= 0 && tick - lastKill <= LINGER;
	}

	public synchronized int left()
	{
		return left;
	}

	/** Mean Slayer XP per kill on this task; 0 before one. */
	public synchronized double xpPerKill()
	{
		if (kills.isEmpty())
		{
			return 0;
		}
		long sum = 0;
		for (int k : kills)
		{
			sum += k;
		}
		return sum / (double) kills.size();
	}

	/** Your Slayer XP once the monsters left are killed; -1 before a kill or with {@code xpNow} unknown. */
	public synchronized long xpAtEnd(long xpNow)
	{
		double per = xpPerKill();
		return xpNow < 0 || per <= 0 ? -1 : xpNow + Math.round(per * left);
	}

	/** Percent (0-99) into its level of {@code xp}. */
	public static int percentInto(long xp)
	{
		int level = Levels.levelFor(xp);
		if (level >= Levels.MAX_LEVEL)
		{
			return 100;
		}
		long from = Levels.xpForLevel(level), to = Levels.xpForLevel(level + 1);
		return (int) Math.min(99, (xp - from) * 100 / Math.max(1, to - from));
	}

	public synchronized void reset()
	{
		left = 0;
		lastKill = -1;
		kills.clear();
	}
}
