package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Span;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/** Combat-view drawing from lazy-flick.html (62-px frame, scaled by u). Static helpers only. */
public final class CombatPainter
{
	private CombatPainter()
	{
	}

	/** Right edge of the timeline, where the right readout starts. */
	public static float rightX(BarLayout l)
	{
		return l.timelineX + l.timelineW;
	}

	/** White sword with its hilt centre at (x, y) (the grip ends 4.7u below it). */
	public static void sword(Graphics2D g, float x, float y, float u)
	{
		g.setColor(Palette.WHITE);
		g.fill(Icons.icon(Icons.Icon.SWORD, x, y + 4.7f * u, u));
	}

	/** One fish in the LOST colour; (cx, cy) is the mockup's x, y. */
	public static void fish(Graphics2D g, float cx, float cy, float u)
	{
		g.setColor(Palette.LOST);
		g.fill(Icons.icon(Icons.Icon.FOOD, cx + 0.5f * u, cy + 3.2f * u, u));
	}

	/** Upward triangle with its apex at (x, y), 8u wide and 5u tall. */
	public static void caret(Graphics2D g, float x, float y, float u, Color c)
	{
		g.setColor(c);
		g.fill(Icons.icon(Icons.Icon.CARET, x, y + 5 * u, u));
	}

	/** Colour of a "pray-*" span kind; null when the span is not a prayer. */
	public static Color prayerColor(String spanKind)
	{
		if (spanKind == null || !spanKind.startsWith("pray-"))
		{
			return null;
		}
		switch (spanKind)
		{
			case "pray-melee":
				return Palette.PRAYER_MELEE;
			case "pray-ranged":
				return Palette.PRAYER_RANGED;
			case "pray-magic":
				return Palette.PRAYER_MAGIC;
			default:
				return Palette.PRAYER_OTHER;
		}
	}

	/**
	 * Solid prayer pills from y, h tall, from the click that turned each on to the click that turned it off;
	 * with {@code labels}, the prayer points used ride each pill's right end when they fit.
	 */
	public static void prayerPills(Graphics2D g, BarLayout l, BarState s, float y, float h, boolean labels)
	{
		if (!hasTicks(s))
		{
			return;
		}
		java.util.List<Span> all = new java.util.ArrayList<>(s.timeline.spansBetween(TimelinePainter.oldestTick(l, s), s.nowTick));
		for (Span sp : all)
		{
			Color c = prayerColor(sp.kind);
			if (c == null)
			{
				continue;
			}
			float x0 = TimelinePainter.spanStartX(l, s, sp), x1 = TimelinePainter.spanEndX(l, s, sp);
			if (x1 - x0 <= 0)
			{
				continue;
			}
			g.setColor(c);
			g.fill(new RoundRectangle2D.Float(x0, y, x1 - x0, h, h, h));
			// Prayer points used while it was on.
			if (labels)
			{
				TimelinePainter.label(g, l, x0, x1, y, h, sp.label, Palette.PILL_LABEL_SOLID);
			}
		}
	}

	/** {left, right} of each prayer-points label the prayer lane draws (see {@link #prayerPills}). */
	public static java.util.List<float[]> prayerLabelRanges(Graphics2D g, BarLayout l, BarState s)
	{
		java.util.List<float[]> out = new java.util.ArrayList<>();
		if (!hasTicks(s))
		{
			return out;
		}
		java.util.List<Span> all = new java.util.ArrayList<>(s.timeline.spansBetween(TimelinePainter.oldestTick(l, s), s.nowTick));
		for (Span sp : all)
		{
			if (prayerColor(sp.kind) == null)
			{
				continue;
			}
			float x0 = TimelinePainter.spanStartX(l, s, sp), x1 = TimelinePainter.spanEndX(l, s, sp);
			float[] r = x1 - x0 <= 0 ? null : TimelinePainter.labelRange(g, l, x0, x1, sp.label);
			if (r != null)
			{
				out.add(r);
			}
		}
		return out;
	}

	private static boolean hasTicks(BarState s)
	{
		return s != null && s.timeline != null && s.nowTick >= 0;
	}

	/** {hits, swings} of your swings in this fight (the open fight span, else the ticks in view); null when none. */
	public static int[] swingCounts(BarState s)
	{
		if (s == null || s.timeline == null || s.nowTick < 0)
		{
			return null;
		}
		int from = Math.max(0, s.nowTick - 100);
		for (Span sp : s.timeline.spansBetween(s.nowTick, s.nowTick))
		{
			if ("fight".equals(sp.kind) && sp.endTick == -1)
			{
				from = sp.startTick - 2;
			}
		}
		int hits = 0, swings = 0;
		for (int t = from; t <= s.nowTick; t++)
		{
			com.superhistogram.model.TickRecord r = s.timeline.peek(t);
			if (r != null && r.attackLate >= 0 && r.swingHit >= 0)
			{
				swings++;
				if (r.swingHit > 0)
				{
					hits++;
				}
			}
		}
		return swings == 0 ? null : new int[]{hits, swings};
	}

	/** The combat readout before there is a beat or flick to judge: hits out of swings this fight. */
	/**
	 * On a slayer task: "48 left", and where your Slayer level lands when they're all dead ("Slayer 87→88 · 14% at
	 * task end"), from the Slayer XP per kill so far.
	 */
	public static String[] slayerLines(BarState s)
	{
		com.superhistogram.model.skill.SlayerTask t = s.slayer;
		String head = t.left() + " left";
		long now = s.xp == null ? -1 : s.xp.currentXp(com.superhistogram.model.skill.SlayerTask.SKILL);
		long end = t.xpAtEnd(now);
		if (end < 0)
		{
			return new String[]{head, "on task"};
		}
		return new String[]{head, "Slayer " + levels(now, end) + " \u00b7 " + percent(end) + " at task end"};
	}

	private static String levels(long now, long end)
	{
		int a = com.superhistogram.model.Levels.levelFor(now), b = com.superhistogram.model.Levels.levelFor(end);
		return a == b ? String.valueOf(a) : a + "\u2192" + b;
	}

	private static String percent(long xp)
	{
		return com.superhistogram.model.skill.SlayerTask.percentInto(xp) + "%";
	}

	/** Narrow bars: "87→88 · 14% at end", then "14% at end". */
	static String[] slayerFallbacks(BarState s)
	{
		com.superhistogram.model.skill.SlayerTask t = s.slayer;
		long now = s.xp == null ? -1 : s.xp.currentXp(com.superhistogram.model.skill.SlayerTask.SKILL);
		long end = t.xpAtEnd(now);
		if (end < 0)
		{
			return new String[0];
		}
		return new String[]{"end: lvl " + levels(now, end) + " \u00b7 " + percent(end), "end: " + levels(now, end) + " \u00b7 " + percent(end), percent(end) + " at end"};
	}

	public static void hitsReadout(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.slayer != null && s.slayer.showing(s.nowTick))
		{
			String[] lines = slayerLines(s);
			ReadoutPainter.right(g, l, s, lines[0], Palette.WHITE, lines[1],
				new String[]{String.valueOf(s.slayer.left())}, slayerFallbacks(s));
			return;
		}
		int[] c = swingCounts(s);
		ReadoutPainter.right(g, l, s, c == null ? "0/0" : c[0] + "/" + c[1], Palette.WHITE,
			"hits this fight");
	}
}
