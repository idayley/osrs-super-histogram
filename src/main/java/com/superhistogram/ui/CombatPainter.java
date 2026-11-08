package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Span;
import com.superhistogram.model.TickRecord;
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

	/** Solid prayer pills on the middle row, never past "now". */
	public static void prayerPills(Graphics2D g, BarLayout l, BarState s, float h)
	{
		if (!hasTicks(s))
		{
			return;
		}
		float y = TimelinePainter.pillY(l);
		for (Span sp : s.timeline.spansBetween(TimelinePainter.oldestTick(l, s), s.nowTick))
		{
			Color c = prayerColor(sp.kind);
			if (c == null)
			{
				continue;
			}
			int end = (sp.endTick == -1 ? s.nowTick : Math.min(sp.endTick, s.nowTick)) + 1;
			float x0 = TimelinePainter.x(l, s, sp.startTick), x1 = TimelinePainter.x(l, s, end);
			if (x1 - x0 <= 0)
			{
				continue;
			}
			g.setColor(c);
			g.fill(new RoundRectangle2D.Float(x0, y, x1 - x0, h, h, h));
		}
	}

	/** Combat XP by the sword on the actions row (see {@link XpRow}). */
	public static void xpLow(Graphics2D g, BarLayout l, BarState s)
	{
		XpRow.paint(g, l, s, XpRow.Row.LOW);
	}

	/** Combat XP that leaves room for the reserved x ranges {left, right}: numbers touching one are dropped. */
	public static void xpLow(Graphics2D g, BarLayout l, BarState s, java.util.List<float[]> reserved)
	{
		XpRow.paint(g, l, s, XpRow.Row.LOW, reserved);
	}

	/** x ranges {left, right} of each tick's XP number group on the actions row. */
	public static java.util.List<float[]> xpLowRanges(Graphics2D g, BarLayout l, BarState s)
	{
		return XpRow.numberRanges(g, l, s, XpRow.Row.LOW);
	}

	private static boolean hasTicks(BarState s)
	{
		return s != null && s.timeline != null && s.nowTick >= 0;
	}
}
