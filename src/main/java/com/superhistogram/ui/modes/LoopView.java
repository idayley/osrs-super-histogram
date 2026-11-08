package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Levels;
import com.superhistogram.model.RightSide;
import com.superhistogram.model.skill.LoopRun;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.Fonts;
import com.superhistogram.ui.Lane;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs lens, the agility view for any loop: a line with "trip N" (Runecraft) or "patch N" / "house N" (farm and
 * bird house runs) where each starts. Runecraft is a steady loop like a course, so right = Runecraft XP per hour
 * with trips, the last trip's time and trips to the next level. A farm or bird house run is a short checklist, so
 * right = stops done against your last run ("3 of 5 patches") with the run's clock against your best.
 */
public final class LoopView implements Lens
{
	private static final String DOT = " · ";

	@Override
	public float[] emphasis(BarState s)
	{
		return Lens.full();
	}

	@Override
	public void overlay(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.timeline == null || s.nowTick < 0 || s.loop == null)
		{
			return;
		}
		float u = l.u;
		float bandTop = l.laneY(Lane.DOING), bandBottom = l.laneY(Lane.PRAYER) + l.laneH(Lane.PRAYER);
		float top = l.rows == 3 ? l.laneY(Lane.TOP) + 4f * u : bandTop - 4f * u;
		float bottom = l.rows == 3 ? l.laneY(Lane.RULER) : bandBottom + 4f * u;
		Font label = Fonts.bold(10f * u).deriveFont(Collections.singletonMap(TextAttribute.TRACKING, 0.04f));
		String word = markWord(s.loop.kind());
		for (LoopRun.Mark m : s.loop.marks())
		{
			if (m.tick > s.nowTick)
			{
				continue;
			}
			float lx = TimelinePainter.x(l, s, m.tick) - 2f * u;
			if (lx < l.timelineX - 40f * u)
			{
				continue;
			}
			g.setColor(Palette.LAP_LINE);
			g.fill(new Rectangle2D.Float(lx - 0.5f, top, 1f, bottom - top));
			if (l.rows == 3 && lx >= l.timelineX)
			{
				g.setFont(label);
				g.setColor(Palette.DIM);
				g.drawString(word + " " + m.number, lx + 4f * u, l.laneY(Lane.RULER) + 4f * u);
			}
		}
	}

	/** The label at each line: "trip", "patch", "house". */
	static String markWord(LoopRun.Kind k)
	{
		return k == LoopRun.Kind.BIRDHOUSE ? "house" : k.noun;
	}

	/** The mode name on the left. */
	public static String name(LoopRun.Kind k)
	{
		switch (k)
		{
			case RUNECRAFT:
				return "Runecraft";
			case FARMING:
				return "Farm run";
			default:
				return "Bird houses";
		}
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.loop == null)
		{
			return;
		}
		String[] lines = rightLines(s);
		ReadoutPainter.right(g, l, s, lines[0], Palette.WHITE, lines[1], headlineFallbacks(s), smallFallbacks(s),
			AgilityView.RIGHT_INSET);
	}

	public static String[] rightLines(BarState s)
	{
		LoopRun r = s.loop;
		if (r == null)
		{
			return new String[]{"", ""};
		}
		String[] p = smallParts(s);
		if (r.kind() == LoopRun.Kind.RUNECRAFT)
		{
			return new String[]{rateHead(s), join(p)};
		}
		return new String[]{count(r, true), join(p)};
	}

	private static String rateHead(BarState s)
	{
		String skill = s.loop.kind().skill;
		if (s.rightSide == RightSide.SESSION)
		{
			return ReadoutPainter.compact(s.xp == null ? 0 : s.xp.sessionXp(skill)) + " xp";
		}
		if (!ReadoutPainter.ratesReady(s))
		{
			return ReadoutPainter.NO_RATE + " xp/hr";
		}
		long perHour = s.xp == null ? 0 : s.xp.xpPerHour(skill, ReadoutPainter.sessionMs(s));
		return ReadoutPainter.compact(perHour) + " xp/hr";
	}

	/** "3 of 5 patches" against the last run (or "3 patches" before one); "3/5" without the noun. */
	static String count(LoopRun r, boolean noun)
	{
		int n = r.inRun(), of = r.previousRunCount();
		String c = of > 0 && n <= of ? (noun ? n + " of " + of : n + "/" + of) : String.valueOf(n);
		if (!noun)
		{
			return c;
		}
		int plural = of > 0 && n <= of ? of : n;
		return c + " " + plural(r.kind().noun, plural);
	}

	private static String plural(String noun, int n)
	{
		return n == 1 ? noun : noun.endsWith("ch") ? noun + "es" : noun + "s";
	}

	private static String clock(int ticks)
	{
		return ReadoutPainter.minSec(Math.round(Math.max(0, ticks) * 0.6f));
	}

	/**
	 * Runecraft: {trips, last trip's time, trips to the next level}. Runs: {the run's clock, your best run}.
	 */
	private static String[] smallParts(BarState s)
	{
		LoopRun r = s.loop;
		if (r.kind() == LoopRun.Kind.RUNECRAFT)
		{
			int trips = r.total();
			String last = r.lastLoopTicks() > 0 ? clock(r.lastLoopTicks()) + "/trip" : "";
			return new String[]{trips + (trips == 1 ? " trip" : " trips"), last, tripsToLevel(s)};
		}
		String run = r.runStartTick() >= 0 ? "run " + clock(s.nowTick - r.runStartTick()) : "";
		String best = r.bestRunTicks() > 0 ? "best " + clock(r.bestRunTicks()) : "";
		return new String[]{run, best};
	}

	/** "40 trips to 77" from the XP of your last craft; "" before one or at 99. */
	static String tripsToLevel(BarState s)
	{
		String skill = s.loop.kind().skill;
		long xp = s.xp == null ? -1 : s.xp.currentXp(skill);
		int per = s.loop.stopXp();
		if (xp < 0 || per <= 0 || Levels.levelFor(xp) >= Levels.MAX_LEVEL)
		{
			return "";
		}
		int next = Levels.levelFor(xp) + 1;
		long n = (Levels.xpForLevel(next) - xp + per - 1) / per;
		return n + (n == 1 ? " trip" : " trips") + " to " + next;
	}

	private static String join(String... parts)
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

	static String[] headlineFallbacks(BarState s)
	{
		LoopRun r = s.loop;
		if (r == null)
		{
			return new String[0];
		}
		if (r.kind() == LoopRun.Kind.RUNECRAFT)
		{
			return new String[]{rateHead(s).replace(" xp/hr", "/hr")};
		}
		String full = count(r, true);
		return new String[]{full.substring(0, full.lastIndexOf(' ')), count(r, false)};
	}

	/** Narrow bars: Runecraft keeps the trips to level longest; runs keep the clock. */
	static String[] smallFallbacks(BarState s)
	{
		LoopRun r = s.loop;
		if (r == null)
		{
			return new String[0];
		}
		String[] p = smallParts(s);
		if (r.kind() == LoopRun.Kind.RUNECRAFT)
		{
			return new String[]{join(p[1], p[2]), p[2], p[0]};
		}
		return new String[]{p[0], p[0].replace("run ", "")};
	}
}
