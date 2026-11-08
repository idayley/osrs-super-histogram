package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Levels;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.Lane;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import java.awt.Graphics2D;
import java.util.Arrays;

/**
 * Minimal lens: only the top lane (XP drops; hits on you there too) is shown, every other lane is off;
 * right = XP/hr of the top skill and time to its next level.
 */
public final class MinimalView implements Lens
{
	@Override
	public float[] emphasis(BarState s)
	{
		float[] a = new float[Lane.values().length];
		Arrays.fill(a, 0f);
		a[Lane.TOP.ordinal()] = 1f;
		return a;
	}

	@Override
	public void overlay(Graphics2D g, BarLayout l, BarState s)
	{
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		ReadoutPainter.right(g, l, s, headline(s), Palette.WHITE, smallLine(s));
	}

	private static String topSkill(BarState s)
	{
		return s.xp == null ? null : s.xp.topSkill();
	}

	/** XP/hr of the skill over session time; 0 when unknown or in the session's first minute. */
	private static long perHour(BarState s, String skill)
	{
		return skill == null || !ReadoutPainter.ratesReady(s) ? 0 : s.xp.xpPerHour(skill, ReadoutPainter.sessionMs(s));
	}

	/** "99k/hr", or "—/hr" in the session's first minute. */
	public static String headline(BarState s)
	{
		String rate = ReadoutPainter.ratesReady(s) ? ReadoutPainter.compact(perHour(s, topSkill(s))) : ReadoutPainter.NO_RATE;
		return rate + "/hr";
	}

	/** "Magic · level 87 in 1h 5m"; no time to level in the session's first minute. */
	public static String smallLine(BarState s)
	{
		String skill = topSkill(s);
		return smallLine(s, skill, perHour(s, skill));
	}
	static String smallLine(BarState s, String skill, long perHour)
	{
		if (skill == null)
		{
			return "no XP yet";
		}
		String level = levelPart(s, skill, perHour);
		return level == null ? skill : skill + " \u00b7 " + level;
	}

	/** "level 87 in 1h 5m" (the next level), "level 99" at the cap, or null when the skill's XP is unknown. */
	public static String levelPart(BarState s, String skill, long perHour)
	{
		long xp = s.xp == null ? -1 : s.xp.currentXp(skill);
		if (xp < 0)
		{
			return null;
		}
		int level = Levels.levelFor(xp);
		if (level >= Levels.MAX_LEVEL)
		{
			return "level " + Levels.MAX_LEVEL;
		}
		int next = level + 1;
		String line = "level " + next;
		if (perHour <= 0)
		{
			return line;
		}
		long ms = (Levels.xpForLevel(next) - xp) * 3_600_000L / perHour;
		return line + " in " + ReadoutPainter.hoursMinutes(ms);
	}
}
