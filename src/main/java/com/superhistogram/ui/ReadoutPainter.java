package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.util.Collections;

/** Left readout (name, sub line, run gauge), right readout and login time, plus number formatting. */
public final class ReadoutPainter
{
	private ReadoutPainter()
	{
	}

	/** Mode name, sub line and run-energy gauge at 22u from the left. */
	public static void left(Graphics2D g, BarLayout l, BarState s)
	{
		if (!l.showLeft || s == null)
		{
			return;
		}
		float u = l.u, x = 22f * u, mid = l.height / 2f;
		if (l.rows == 1)
		{
			text(g, s.modeName, Fonts.bold(Math.max(10f, 11f * u)), Palette.TEXT, x, mid + 4f * u);
			return;
		}
		float subY = mid + 12f * u;
		text(g, s.modeName, Fonts.bold(Math.max(10f, 11f * u)), Palette.TEXT, x, mid - 4f * u);
		text(g, s.modeSub, Fonts.semiBold(11f * u), Palette.DIM, x, subY);
		if (!s.showRunEnergy)
		{
			return;
		}
		int pct = Math.max(0, Math.min(100, s.runEnergyPct));
		float gy = subY + 6f * u, gw = 64f * u, gh = 3f * u;
		g.setColor(Palette.GAUGE_TRACK);
		g.fill(new RoundRectangle2D.Float(x, gy, gw, gh, gh, gh));
		g.setColor(pct < 20 ? Palette.WHITE : Palette.RUN_ENERGY);
		g.fill(new RoundRectangle2D.Float(x, gy, gw * pct / 100f, gh, gh, gh));
		text(g, pct + "%", Fonts.bold(8.5f * u), Palette.DIM, x + 68f * u, gy + 3.5f * u);
	}

	/** One headline number and one small line, just right of the timeline. */
	public static void right(Graphics2D g, BarLayout l, String big, Color bigColor, String small)
	{
		if (!l.showRight)
		{
			return;
		}
		float u = l.u, x = l.timelineX + l.timelineW + 10f * u;
		if (l.rows == 1)
		{
			float size = Math.max(10f, 20f * u);
			text(g, big, Fonts.bold(size), bigColor, x, l.height / 2f + size * 0.35f);
			return;
		}
		text(g, big, Fonts.bold(20f * u), bigColor, x, 33f * u);
		text(g, small, Fonts.semiBold(10.5f * u), Palette.DIM, x, 48f * u);
	}

	/** Online time, right-aligned at w - 26u, with "ONLINE" under it. */
	public static void login(Graphics2D g, BarLayout l, BarState s, long nowMs)
	{
		if (!l.showRight || s == null || !s.showLoginTime || s.clock == null || s.clock.onlineMs(nowMs) <= 0)
		{
			return;
		}
		float u = l.u, right = l.width - 26f * u;
		String time = s.clock.format(nowMs);
		if (l.rows == 1)
		{
			rightAligned(g, time, Fonts.semiBold(Math.max(9f, 11f * u)), Palette.DIM, right, l.height / 2f + 4f * u);
			return;
		}
		rightAligned(g, time, Fonts.semiBold(11f * u), Palette.DIM, right, 28f * u);
		Font label = Fonts.bold(8.5f * u).deriveFont(Collections.singletonMap(TextAttribute.TRACKING, 0.06f));
		rightAligned(g, "ONLINE", label, Palette.ONLINE_LABEL, right, 40f * u);
	}

	// ---- formatting ----

	/** 950, 12.4k, 124k, 1.2M. */
	public static String compact(long n)
	{
		if (n < 1_000)
		{
			return String.valueOf(n);
		}
		if (n < 100_000)
		{
			return trim(Math.floor(n / 100.0) / 10.0) + "k";
		}
		if (n < 1_000_000)
		{
			return n / 1_000 + "k";
		}
		if (n < 100_000_000)
		{
			return trim(Math.floor(n / 100_000.0) / 10.0) + "M";
		}
		return n / 1_000_000 + "M";
	}

	/** "2h 10m" or "45m" (at least 1m). */
	public static String hoursMinutes(long ms)
	{
		long minutes = Math.max(1, (ms + 59_999) / 60_000);
		return minutes >= 60 ? minutes / 60 + "h " + minutes % 60 + "m" : minutes + "m";
	}

	/** "m:ss". */
	public static String minSec(int seconds)
	{
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}

	private static String trim(double v)
	{
		return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
	}

	private static void text(Graphics2D g, String s, Font f, Color c, float x, float y)
	{
		if (s == null || s.isEmpty())
		{
			return;
		}
		g.setFont(f);
		g.setColor(c);
		g.drawString(s, x, y);
	}

	private static void rightAligned(Graphics2D g, String s, Font f, Color c, float right, float y)
	{
		g.setFont(f);
		text(g, s, f, c, right - g.getFontMetrics().stringWidth(s), y);
	}
}
