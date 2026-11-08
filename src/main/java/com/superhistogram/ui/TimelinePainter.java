package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Hit;
import com.superhistogram.model.Span;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.XpDrop;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Shared timeline drawing: tick ruler, "now" line, pills, XP, hits and clicks.
 * Positions follow always-on-baseline.html (62-px frame) scaled by u, with the
 * 2-row and 1-row placements from resizable-bar.html.
 */
public final class TimelinePainter
{
	/** Ticks of empty space kept to the right of "now". */
	private static final int MAX_LEAD_TICKS = 4;

	private static final Path2D DEMON = new Path2D.Float();

	static
	{
		DEMON.moveTo(-6, -1);
		DEMON.lineTo(-7, -7);
		DEMON.lineTo(-3, -4);
		DEMON.lineTo(0, -5);
		DEMON.lineTo(3, -4);
		DEMON.lineTo(7, -7);
		DEMON.lineTo(6, -1);
		DEMON.quadTo(6, 5, 0, 6);
		DEMON.quadTo(-6, 5, -6, -1);
		DEMON.closePath();
	}

	private TimelinePainter()
	{
	}

	// ---- geometry ----

	static int leadTicks(BarLayout l)
	{
		return Math.min(MAX_LEAD_TICKS, l.visibleTicks / 4);
	}

	/** x of the "now" line: the right edge of the current tick. */
	public static float nowX(BarLayout l)
	{
		return l.timelineX + (l.visibleTicks - leadTicks(l)) * l.tickWidth;
	}

	/** Left edge of a tick's slot. */
	public static float x(BarLayout l, BarState s, int tick)
	{
		return nowX(l) - (float) (s.nowTick + 1 - tick) * l.tickWidth;
	}

	/** Oldest tick with any part on screen. */
	public static int oldestTick(BarLayout l, BarState s)
	{
		return s.nowTick + 1 - (l.visibleTicks - leadTicks(l));
	}

	public static float pillY(BarLayout l)
	{
		return l.rows == 3 ? l.pillY : l.rows == 2 ? l.height * 0.48f : l.height * 0.32f;
	}

	public static float pillH(BarLayout l)
	{
		return l.rows == 1 ? Math.max(8f, l.height * 0.3f) : l.pillH;
	}

	/** Baseline of the XP numbers (top row). */
	public static float xpY(BarLayout l)
	{
		return pillY(l) - 7f * l.u;
	}

	private static boolean hasTicks(BarState s)
	{
		return s != null && s.timeline != null && s.nowTick >= 0;
	}

	// ---- structure ----

	public static void ruler(Graphics2D g, BarLayout l)
	{
		g.setColor(Palette.RULER);
		float y = l.height - 9f * l.u;
		for (int i = 0; i <= l.visibleTicks; i++)
		{
			g.fill(new Rectangle2D.Float(l.timelineX + i * l.tickWidth - 0.5f, y, 1f, 3f * l.u));
		}
	}

	public static void nowLine(Graphics2D g, BarLayout l)
	{
		g.setColor(Palette.NOW_LINE);
		g.fill(new RoundRectangle2D.Float(nowX(l) - 1f, l.height * 0.22f, 2f, l.height * 0.6f, 2f, 2f));
	}

	// ---- middle row ----

	/** Walk and run as faint pills with labels, anything else (animations) solid. */
	public static void spans(Graphics2D g, BarLayout l, BarState s)
	{
		if (!hasTicks(s))
		{
			return;
		}
		for (Span sp : s.timeline.spansBetween(oldestTick(l, s), s.nowTick))
		{
			int end = (sp.endTick == -1 ? s.nowTick : Math.min(sp.endTick, s.nowTick)) + 1;
			float x0 = x(l, s, sp.startTick), x1 = x(l, s, end);
			switch (sp.kind)
			{
				case "walk":
					pill(g, l, x0, x1, Palette.TEXT, true, sp.label != null ? sp.label : "walking");
					break;
				case "run":
					pill(g, l, x0, x1, Palette.RUN_ENERGY, true, sp.label != null ? sp.label : "running");
					break;
				default:
					pill(g, l, x0, x1, Palette.ACTION, false, sp.label);
			}
		}
	}

	/** A pill across [x0, x1) on the middle row; label omitted when null or the pill is too thin. */
	public static void pill(Graphics2D g, BarLayout l, float x0, float x1, Color c, boolean faint, String label)
	{
		float w = x1 - x0 - 2f;
		if (w <= 0)
		{
			return;
		}
		float y = pillY(l), h = pillH(l);
		Shape pill = new RoundRectangle2D.Float(x0 + 1f, y, w, h, h, h);
		g.setColor(faint ? Palette.withAlpha(c, Palette.FAINT_ALPHA) : c);
		g.fill(pill);
		if (label == null || l.rows == 1)
		{
			return;
		}
		Shape clip = g.getClip();
		g.clip(pill);
		g.setFont(Fonts.bold(10f * l.u));
		g.setColor(faint ? Palette.PILL_LABEL_FAINT : Palette.PILL_LABEL_SOLID);
		g.drawString(label, x0 + 9f * l.u, y + 10.5f * l.u);
		g.setClip(clip);
	}

	// ---- top row ----

	/** XP drops as "+N" in each skill's colour, side by side within a tick. Hidden in one-row mode. */
	public static void xp(Graphics2D g, BarLayout l, BarState s)
	{
		if (!hasTicks(s) || l.rows == 1)
		{
			return;
		}
		g.setFont(Fonts.bold(10f * l.u));
		float y = xpY(l);
		for (int t = oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r == null || r.xp.isEmpty())
			{
				continue;
			}
			float x = x(l, s, t) - 4f * l.u;
			for (XpDrop d : r.xp)
			{
				String text = "+" + d.amount;
				g.setColor(Palette.skill(d.skill));
				g.drawString(text, x, y);
				x += g.getFontMetrics().stringWidth(text) + 6f * l.u;
			}
		}
	}

	/** Hits on you: yellow line + icon + number; blocked/zero in faint grey. Notches in one-row mode. */
	public static void hits(Graphics2D g, BarLayout l, BarState s)
	{
		if (!hasTicks(s))
		{
			return;
		}
		float pY = pillY(l), pH = pillH(l), u = l.u;
		g.setFont(Fonts.bold(11f * u));
		for (int t = oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r == null || r.hits.isEmpty())
			{
				continue;
			}
			boolean landed = false;
			for (Hit h : r.hits)
			{
				landed |= !h.blocked;
			}
			Color c = landed ? Palette.DAMAGE : Palette.BLOCKED;
			float x = x(l, s, t);
			g.setColor(c);
			if (l.rows == 1)
			{
				g.fill(new Rectangle2D.Float(x + l.tickWidth / 2f - 1.5f, pY - 4f * u, 3f, 4f * u + 1f));
				continue;
			}
			g.fill(new RoundRectangle2D.Float(x - 1f, pY - 4f * u, 2f, pH + 8f * u, 2f, 2f));
			AffineTransform at = AffineTransform.getTranslateInstance(x - 6f * u, pY - 10f * u);
			at.scale(0.9f * u, 0.9f * u);
			g.fill(at.createTransformedShape(DEMON));
			float tx = x + 5f * u;
			for (Hit h : r.hits)
			{
				if (h.blocked)
				{
					continue;
				}
				String text = String.valueOf(h.amount);
				g.drawString(text, tx, pY - 6f * u);
				tx += g.getFontMetrics().stringWidth(text) + 4f * u;
			}
		}
	}

	// ---- actions row ----

	/** Clicks as 2 × 8 white marks, placed by in-tick time. Only with the actions row (3 rows). */
	public static void clicks(Graphics2D g, BarLayout l, BarState s)
	{
		if (!hasTicks(s) || l.rows < 3)
		{
			return;
		}
		g.setColor(Palette.WHITE);
		float y = l.actionsY - 4f * l.u, h = 8f * l.u;
		for (int t = oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r == null)
			{
				continue;
			}
			for (Float f : r.clicks)
			{
				float x = x(l, s, t) + (f == null ? 0f : f) * l.tickWidth;
				g.fill(new RoundRectangle2D.Float(x - 1f, y, 2f, h, 2f, 2f));
			}
		}
	}
}
