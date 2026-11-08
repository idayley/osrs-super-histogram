package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Span;
import com.superhistogram.model.TickRecord;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.RoundRectangle2D;

/**
 * Shared timeline geometry and drawing: tick positions, span ends, the "now" line and pill labels.
 * Positions follow always-on-baseline.html (62-px frame) scaled by u, with the
 * 2-row and 1-row placements from resizable-bar.html.
 */
public final class TimelinePainter
{
	/** Ticks of empty space kept to the right of "now". */
	private static final int MAX_LEAD_TICKS = 4;

	private TimelinePainter()
	{
	}

	// ---- geometry ----

	static int leadTicks(BarLayout l)
	{
		if (l.nowPct > 0)
		{
			int lead = Math.round(l.visibleTicks * (100 - Math.min(100, l.nowPct)) / 100f);
			return Math.max(0, Math.min(l.visibleTicks - 1, lead));
		}
		return Math.min(MAX_LEAD_TICKS, l.visibleTicks / 4);
	}

	/** x of the "now" line: the right edge of the current tick. */
	public static float nowX(BarLayout l)
	{
		return l.timelineX + (l.visibleTicks - leadTicks(l)) * l.tickWidth;
	}

	/**
	 * Where something still going on ends: at the "now" line on screen. The timeline is drawn slid left by
	 * the glide, so this sits glide px right of {@link #nowX} in timeline coordinates.
	 */
	public static float liveX(BarLayout l)
	{
		return nowX(l) + l.glide - l.lag;
	}

	/**
	 * Right end of a span on screen. Open spans reach the "now" line. A prayer you click off stays on in the game
	 * until the next tick, so its pill runs to where the game turned it off (your click is the white mark); it
	 * never grows past that and shrinks back. A span closed on the newest tick eases to its end over the tick.
	 */
	public static float spanEndX(BarLayout l, BarState s, Span sp)
	{
		if (sp.endTick == -1)
		{
			return liveX(l);
		}
		float end = x(l, s, Math.min(sp.endTick, s.nowTick) + 1);
		float live = liveX(l);
		if (s.smooth && sp.closedAt == s.nowTick && live > end && l.tickWidth > 0)
		{
			float t = Math.min(1f, l.glide / l.tickWidth);
			t = 1f - (1f - t) * (1f - t);
			return live + (end - live) * t;
		}
		return end;
	}

	/** Left end of a span: the tick it started (a prayer: the tick the game turned it on). */
	public static float spanStartX(BarLayout l, BarState s, Span sp)
	{
		return x(l, s, sp.startTick);
	}

	/** A tick that ran at least this long (a normal one is 600 ms) is drawn wider: the server lagged. */
	public static final int LAG_MS = 750;
	private static final float MAX_STRETCH = 2.5f;

	/** Width of a tick's slot: wider in proportion for a lag tick. */
	public static float slotW(BarLayout l, BarState s, int tick)
	{
		TickRecord r = s.timeline == null || tick >= s.nowTick ? null : s.timeline.peek(tick);
		return r == null || r.lengthMs < LAG_MS ? l.tickWidth : l.tickWidth * Math.min(MAX_STRETCH, r.lengthMs / 600f);
	}

	/** Left edge of a tick's slot. Lag ticks before it push it left. */
	public static float x(BarLayout l, BarState s, int tick)
	{
		float x = nowX(l) - (float) (s.nowTick + 1 - tick) * l.tickWidth;
		if (s.timeline == null || tick >= s.nowTick)
		{
			return x;
		}
		for (int t = Math.max(tick, s.nowTick - l.visibleTicks - 2); t < s.nowTick; t++)
		{
			x -= slotW(l, s, t) - l.tickWidth;
		}
		return x;
	}

	/** Oldest tick with any part on screen. */
	public static int oldestTick(BarLayout l, BarState s)
	{
		// One extra: with smooth scrolling the timeline sits up to a slot right, so the tick before is in view.
		return s.nowTick - (l.visibleTicks - leadTicks(l));
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

	public static void nowLine(Graphics2D g, BarLayout l)
	{
		g.setColor(Palette.NOW_LINE);
		g.fill(new RoundRectangle2D.Float(nowX(l) - 1f, l.height * 0.22f, 2f, l.height * 0.6f, 2f, 2f));
	}

	// ---- pills ----

	/** A pill across [x0, x1) at y, h tall; label omitted when null or the pill is too thin. */
	public static void pill(Graphics2D g, BarLayout l, float x0, float x1, float y, float h, Color c, boolean faint,
		String label)
	{
		float w = x1 - x0 - 2f;
		if (w <= 0)
		{
			return;
		}
		Shape pill = new RoundRectangle2D.Float(x0 + 1f, y, w, h, h, h);
		g.setColor(faint ? Palette.withAlpha(c, Palette.FAINT_ALPHA) : c);
		g.fill(pill);
		label(g, l, x0, x1, y, h, label, faint ? Palette.PILL_LABEL_FAINT
			: Palette.isDark(c) ? Palette.TEXT : Palette.PILL_LABEL_SOLID);
	}

	/** A pill's label, riding its right end; skipped when null or the pill is too short for it. */
	public static void label(Graphics2D g, BarLayout l, float x0, float x1, float y, float h, String label, Color c)
	{
		if (label == null)
		{
			return;
		}
		// Whole words only, skipped when the pill is too short. The label rides the pill's right end, so on an
		// ongoing action it stays by the "now" line and on finished ones it scrolls off with the pill, never
		// snapping back as history moves.
		float[] r = labelRange(g, l, x0, x1, label);
		if (r == null)
		{
			return;
		}
		float fs = labelSize(l);
		g.setFont(Fonts.bold(fs));
		g.setColor(c);
		g.drawString(label, r[0], y + h / 2f + 0.35f * fs);
	}

	/** {left, right} of a pill's label as {@link #label} would draw it; null when it is not drawn. */
	public static float[] labelRange(Graphics2D g, BarLayout l, float x0, float x1, String label)
	{
		if (label == null)
		{
			return null;
		}
		Font f = Fonts.bold(labelSize(l));
		float tw = (float) f.getStringBounds(label, g.getFontRenderContext()).getWidth();
		float tx = labelX(x0, x1, tw, l.u);
		return tx + tw > x1 - 6f * l.u ? null : new float[]{tx, tx + tw};
	}

	/** Pill label size: 10u, but never under 8 px, where the pixel font stops reading. */
	static float labelSize(BarLayout l)
	{
		return Math.max(8f, 10f * l.u);
	}

	/** Where a label of width {@code tw} starts: 9u before the pill's right end, but never before 9u in. */
	static float labelX(float x0, float x1, float tw, float u)
	{
		return Math.max(x0 + 9f * u, x1 - 9f * u - tw);
	}
}
