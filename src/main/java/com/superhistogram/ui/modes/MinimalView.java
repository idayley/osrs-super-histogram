package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Levels;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.ModeView;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Graphics2D;

/** Minimal view: XP drops only; right = XP/hr of the top skill and time to its next level. */
public final class MinimalView implements ModeView
{
	@Override
	public void paintTimeline(Graphics2D g, BarLayout l, BarState s)
	{
		TimelinePainter.xp(g, l, s);
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		String skill = s.xp == null ? null : s.xp.topSkill();
		long sessionMs = s.clock == null ? 0 : s.clock.onlineMs(s.nowMs);
		long perHour = skill == null ? 0 : s.xp.xpPerHour(skill, sessionMs);
		ReadoutPainter.right(g, l, ReadoutPainter.compact(perHour) + "/hr", Palette.WHITE, smallLine(s, skill, perHour));
	}

	static String smallLine(BarState s, String skill, long perHour)
	{
		if (skill == null)
		{
			return "no XP yet";
		}
		long xp = s.xp.currentXp(skill);
		if (xp < 0)
		{
			return skill;
		}
		int level = Levels.levelFor(xp);
		if (level >= Levels.MAX_LEVEL)
		{
			return skill + " · level " + Levels.MAX_LEVEL;
		}
		int next = level + 1;
		String line = skill + " · level " + next;
		if (perHour <= 0)
		{
			return line;
		}
		long ms = (Levels.xpForLevel(next) - xp) * 3_600_000L / perHour;
		return line + " in " + ReadoutPainter.hoursMinutes(ms);
	}
}
