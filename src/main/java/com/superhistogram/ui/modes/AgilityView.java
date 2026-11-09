package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Levels;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.skill.AgilityCourse;
import com.superhistogram.model.RightSide;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.Fonts;
import com.superhistogram.ui.Lane;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Px;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.SkillPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Agility lens (agility.html): dims the prayer lane and adds a thin line with "lap N" at each lap start and the
 * white "+N" ticks stood still before the next click; right = Agility XP per hour, laps, marks and the next level.
 * Same tick width as every other lens: the timeline never re-scales.
 */
public final class AgilityView implements Lens
{
	private static final String DOT = " · ";
	private static final String AGILITY = "Agility";
	/** The readout starts at the timeline end (the lead ticks right of the now line are empty), not 10u later. */
	public static final float RIGHT_INSET = 0f;

	@Override
	public float[] emphasis(BarState s)
	{
		return Lens.full();
	}

	@Override
	public void overlay(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.timeline == null || s.nowTick < 0 || s.agility == null)
		{
			return;
		}
		int since = s.agility.courseSinceTick();
		lapLines(g, l, s, since);
		if (l.rows == 3 && s.wastedTicks)
		{
			lost(g, l, s, since);
		}
	}

	/**
	 * A line at each lap start: 3-row bar from 4u below the top lane's top to the ruler, with "lap N" under it
	 * at the very bottom; smaller bars across the middle band only.
	 */
	private static void lapLines(Graphics2D g, BarLayout l, BarState s, int since)
	{
		float u = l.u;
		float bandTop = l.laneY(Lane.DOING), bandBottom = l.laneY(Lane.PRAYER) + l.laneH(Lane.PRAYER);
		float top = l.rows == 3 ? l.laneY(Lane.TOP) + 4f * u : bandTop - 4f * u;
		float bottom = l.rows == 3 ? l.laneY(Lane.RULER) : bandBottom + 4f * u;
		Font label = Fonts.bold(10f * u).deriveFont(Collections.singletonMap(TextAttribute.TRACKING, 0.04f));
		for (AgilityCourse.LapStart ls : s.agility.lapStarts())
		{
			if (ls.tick <= since || ls.tick > s.nowTick)
			{
				continue;
			}
			float lx = TimelinePainter.x(l, s, ls.tick) - 2f * u;
			if (lx < l.timelineX - 40f * u)
			{
				continue;
			}
			g.setColor(Palette.LAP_LINE);
			g.fill(Px.rect(lx - 0.5f, top, 1f, bottom - top));
			if (l.rows == 3 && lx >= l.timelineX)
			{
				g.setFont(label);
				g.setColor(Palette.DIM);
				g.drawString("lap " + ls.number, lx + 4f * u, l.laneY(Lane.RULER) + 4f * u);
			}
		}
	}

	private static void lost(Graphics2D g, BarLayout l, BarState s, int since)
	{
		for (int t = Math.max(TimelinePainter.oldestTick(l, s), since + 1); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r != null && r.agilityLost > 0)
			{
				SkillPainter.wasted(g, l, s, TimelinePainter.x(l, s, t - r.agilityLost) + 1f,
					TimelinePainter.x(l, s, t) - 1f, r.agilityLost);
			}
		}
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.agility == null)
		{
			return;
		}
		String[] lines = rightLines(s);
		ReadoutPainter.right(g, l, s, lines[0], rightColor(s), lines[1], new String[0], smallFallbacks(s),
			RIGHT_INSET);
	}

	/** "0:52.8": ticks in minutes, seconds and tenths (a tick is 0.6 s). */
	static String lapTime(int ticks)
	{
		int tenths = Math.max(0, ticks) * 6;
		return String.format(Locale.ROOT, "%d:%02d.%d", tenths / 600, tenths / 10 % 60, tenths % 10);
	}

	/**
	 * Headline and small line of the right readout; both empty without a course. Big = Agility XP per hour (or
	 * this session's XP with the session setting); small = laps, marks, ticks lost this lap, the next level.
	 */
	public static String[] rightLines(BarState s)
	{
		if (s.agility == null)
		{
			return new String[]{"", ""};
		}
		String[] p = smallParts(s);
		return new String[]{head(s), joinParts(p[0], p[1], p[2], p[3])};
	}

	private static long rate(BarState s)
	{
		return s.xp == null || !ReadoutPainter.ratesReady(s) ? 0
			: s.xp.xpPerHour(AGILITY, ReadoutPainter.sessionMs(s));
	}

	private static String rateText(BarState s)
	{
		return ReadoutPainter.ratesReady(s) ? ReadoutPainter.compact(rate(s)) : ReadoutPainter.NO_RATE;
	}

	private static String head(BarState s)
	{
		if (s.rightSide == RightSide.SESSION)
		{
			return ReadoutPainter.compact(s.xp == null ? 0 : s.xp.sessionXp(AGILITY)) + " xp";
		}
		return rateText(s) + " xp/hr";
	}

	/** The small line's facts: laps, marks, ticks lost this lap, the next level; "" when absent. */
	private static String[] smallParts(BarState s)
	{
		AgilityCourse a = s.agility;
		int laps = a.laps(), marks = a.marks();
		String lapsText = laps + (laps == 1 ? " lap" : " laps");
		if (s.rightSide == RightSide.SESSION)
		{
			lapsText = rateText(s) + "/hr" + DOT + lapsText;
		}
		String level = levelPart(s, true);
		return new String[]{lapsText, marks > 0 ? marks + (marks == 1 ? " mark" : " marks") : "",
			s.wastedTicks ? "\u2212" + a.lapLost() + "t" : "", level == null ? "" : level,
			shortLevel(s)};
	}

	/** "405 laps to 74" for narrow bars; "" before a lap's XP is known or at level 99. */
	static String shortLevel(BarState s)
	{
		long xp = s.xp == null ? -1 : s.xp.currentXp(AGILITY);
		int perLap = s.agility.lapXp();
		if (xp < 0 || perLap <= 0 || Levels.levelFor(xp) >= Levels.MAX_LEVEL)
		{
			return "";
		}
		int next = Levels.levelFor(xp) + 1;
		long laps = (Levels.xpForLevel(next) - xp + perLap - 1) / perLap;
		return laps + (laps == 1 ? " lap" : " laps") + " to " + next;
	}

	/**
	 * "level 71 in 38 laps (1h 5m)": the next level in laps (from the XP of your last full lap) and, with
	 * {@code withTime}, the time at this XP rate; "level 99" at the cap; null when the XP is unknown.
	 */
	static String levelPart(BarState s, boolean withTime)
	{
		long xp = s.xp == null ? -1 : s.xp.currentXp(AGILITY);
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
		long left = Levels.xpForLevel(next) - xp;
		int perLap = s.agility.lapXp();
		long rate = rate(s);
		String laps = perLap > 0 ? (left + perLap - 1) / perLap + ((left + perLap - 1) / perLap == 1 ? " lap" : " laps")
			: "";
		String time = withTime && rate > 0 ? ReadoutPainter.hoursMinutes(left * 3_600_000L / rate) : "";
		String line = "level " + next;
		if (!laps.isEmpty())
		{
			return line + " in " + laps + (time.isEmpty() ? "" : " (" + time + ")");
		}
		return time.isEmpty() ? line : line + " in " + time;
	}

	private static String joinParts(String... parts)
	{
		List<String> kept = new ArrayList<>();
		for (String p : parts)
		{
			if (!p.isEmpty())
			{
				kept.add(p);
			}
		}
		return String.join(DOT, kept);
	}

	/** Shorter small lines for narrow bars: drop the level's time, the ticks lost, the marks, then the laps. */
	static String[] smallFallbacks(BarState s)
	{
		if (s.agility == null)
		{
			return new String[0];
		}
		String[] p = smallParts(s);
		// The next level is what you're working towards, so it is the last fact to go.
		return new String[]{joinParts(p[0], p[1], p[2], p[4]), joinParts(p[0], p[1], p[4]), joinParts(p[0], p[4]),
			joinParts(p[4]), joinParts(p[0], p[2])};
	}

	public static Color rightColor(BarState s)
	{
		return Palette.WHITE;
	}
}
