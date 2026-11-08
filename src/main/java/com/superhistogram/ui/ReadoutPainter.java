package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.util.Collections;

/** Left readout (name, sub line), right readout and login time, plus number formatting. */
public final class ReadoutPainter
{
	private ReadoutPainter()
	{
	}

	/** Mode name (and, when wide, its sub line) at 22u from the left. */
	public static void left(Graphics2D g, BarLayout l, BarState s)
	{
		if (!l.showLeft || s == null)
		{
			return;
		}
		float u = l.u, x = (l.compact ? 16f : 22f) * u, mid = l.height / 2f;
		float room = l.timelineX - x - 6f * u;
		Font nf = Fonts.bold(Math.max(10f, 11f * u));
		if (l.rows == 1)
		{
			text(g, fit(g, s.modeName, nf, room), nf, Palette.TEXT, x, mid + 4f * u);
			return;
		}
		if (l.compact)
		{
			// Chat width: just the name; the sub line is dropped.
			text(g, fit(g, s.modeName, nf, room), nf, Palette.TEXT, x, mid + 4f * u);
			return;
		}
		text(g, fit(g, s.modeName, nf, room), nf, Palette.TEXT, x, mid - 2f * u);
		Font sf = Fonts.semiBold(11f * u);
		text(g, fit(g, s.modeSub, sf, room), sf, Palette.DIM, x, mid + 12f * u);
	}

	/** Marker that separates the optional "last:" part of the small line. */
	private static final String LAST_PART = " · last:";

	/**
	 * One headline number and one small line, just right of the timeline. Text is clamped so it
	 * never reaches the login time or the rounded right end: the small line first drops its
	 * " · last: ..." part, then both lines are ellipsized.
	 */
	public static void right(Graphics2D g, BarLayout l, BarState s, String big, Color bigColor, String small)
	{
		right(g, l, s, big, bigColor, small, new String[0]);
	}

	/**
	 * As above; {@code fallbacks} are shorter headlines tried in order when the full one does not fit
	 * (the first that fits is used; if none does, the full headline is ellipsized).
	 */
	public static void right(Graphics2D g, BarLayout l, BarState s, String big, Color bigColor, String small,
		String... fallbacks)
	{
		right(g, l, s, big, bigColor, small, false, fallbacks);
	}

	/**
	 * As the first form; {@code smallFallbacks} are shorter small lines tried in order when the full one
	 * does not fit (the first that fits is used; if none does, the full line is shortened as usual).
	 */
	public static void right(Graphics2D g, BarLayout l, BarState s, String big, Color bigColor, String small,
		String[] fallbacks, String[] smallFallbacks)
	{
		right(g, l, s, big, bigColor, small, false, fallbacks, smallFallbacks, 10f);
	}

	/** As above, with the text starting {@code insetU} (in u) right of the timeline instead of 10u. */
	public static void right(Graphics2D g, BarLayout l, BarState s, String big, Color bigColor, String small,
		String[] fallbacks, String[] smallFallbacks, float insetU)
	{
		right(g, l, s, big, bigColor, small, false, fallbacks, smallFallbacks, insetU);
	}

	/** As the first form; with {@code coin} a gold coin is drawn before the headline (high alch). */
	public static void right(Graphics2D g, BarLayout l, BarState s, String big, Color bigColor, String small,
		boolean coin)
	{
		right(g, l, s, big, bigColor, small, coin, new String[0], new String[0], 10f);
	}

	private static void right(Graphics2D g, BarLayout l, BarState s, String big, Color bigColor, String small,
		boolean coin, String[] fallbacks)
	{
		right(g, l, s, big, bigColor, small, coin, fallbacks, new String[0], 10f);
	}

	/** With a coin before the headline and shorter small lines to try in order. */
	public static void rightCoin(Graphics2D g, BarLayout l, BarState s, String big, Color bigColor, String small,
		boolean coin, String[] smallFallbacks)
	{
		right(g, l, s, big, bigColor, small, coin, new String[0], smallFallbacks, 10f);
	}

	private static void right(Graphics2D g, BarLayout l, BarState s, String big, Color bigColor, String small,
		boolean coin, String[] fallbacks, String[] smallFallbacks, float insetU)
	{
		if (!l.showRight)
		{
			return;
		}
		float u = l.u, x = l.timelineX + l.timelineW + (l.compact ? Math.min(insetU, 8f) : insetU) * u;
		if (l.compact && l.rows > 1)
		{
			compactRight(g, l, s, x, big, bigColor, small, coin, fallbacks, smallFallbacks);
			return;
		}
		float room = rightLimit(g, l, s) - x;
		if (l.rows == 1)
		{
			float size = Math.max(10f, 20f * u);
			Font f = Fonts.bold(size);
			float cy = l.height / 2f, skip = 0f;
			if (coin)
			{
				// The coin is 0.6 of the headline's size across; the text starts 0.75 of it later.
				SkillPainter.coin(g, x + 0.3f * size, cy, 0.3f * size);
				skip = 0.75f * size;
			}
			text(g, pickHeadline(g, f, room - skip, big, fallbacks), f, bigColor, x + skip, cy + size * 0.35f);
			return;
		}
		float skip = 0f;
		if (coin)
		{
			SkillPainter.coin(g, x + 6f * u, 26f * u, 6f * u);
			skip = 15f * u;
		}
		Font bf = Fonts.bold(20f * u), sf = Fonts.semiBold(10.5f * u);
		text(g, pickHeadline(g, bf, room - skip, big, fallbacks), bf, bigColor, x + skip, 33f * u);
		String line = pickSmall(g, sf, room, small, smallFallbacks);
		if (line != null && width(g, line, sf) > room)
		{
			int i = line.indexOf(LAST_PART);
			if (i >= 0)
			{
				line = line.substring(0, i);
			}
		}
		text(g, fit(g, line, sf, room), sf, Palette.DIM, x, 48f * u);
	}

	/**
	 * Chat-width right readout: a smaller headline across the whole readout, and under it the small line
	 * with the login time at its right end (no "ONLINE" label).
	 */
	private static void compactRight(Graphics2D g, BarLayout l, BarState s, float x, String big, Color bigColor,
		String small, boolean coin, String[] fallbacks, String[] smallFallbacks)
	{
		float u = l.u, edge = compactEdge(l);
		float skip = 0f;
		if (coin)
		{
			SkillPainter.coin(g, x + 5f * u, 25f * u, 5f * u);
			skip = 13f * u;
		}
		Font bf = Fonts.bold(16f * u), sf = Fonts.semiBold(9f * u);
		text(g, pickHeadline(g, bf, edge - x - skip, big, fallbacks), bf, bigColor, x + skip, 30f * u);
		float room = edge - x;
		if (loginVisible(l, s, s.nowMs))
		{
			room -= width(g, s.clock.format(s.nowMs), sf) + 6f * u;
		}
		text(g, wholeParts(g, pickSmall(g, sf, room, small, smallFallbacks), sf, room), sf, Palette.DIM, x, 45f * u);
	}

	/**
	 * The line, or as many of its leading " · "-separated parts as fit whole in room; null when not even the
	 * first part fits. Never cuts a word.
	 */
	static String wholeParts(Graphics2D g, String line, Font f, float room)
	{
		if (line == null)
		{
			return null;
		}
		String t = line;
		while (width(g, t, f) > room)
		{
			int i = t.lastIndexOf(" \u00b7 ");
			if (i < 0)
			{
				return null;
			}
			t = t.substring(0, i);
		}
		return t;
	}

	/** Right edge of compact readout text: clear of the rounded end. */
	private static float compactEdge(BarLayout l)
	{
		return l.width - 20f * l.u;
	}

	/** The first of full, fallbacks... that fits in room; the full line when none does. */
	static String pickSmall(Graphics2D g, Font f, float room, String full, String... fallbacks)
	{
		if (full == null || width(g, full, f) <= room)
		{
			return full;
		}
		for (String c : fallbacks)
		{
			if (c != null && width(g, c, f) <= room)
			{
				return c;
			}
		}
		return full;
	}

	/** The first of full, fallbacks... that fits in room; the ellipsized full headline when none does. */
	static String pickHeadline(Graphics2D g, Font f, float room, String full, String... fallbacks)
	{
		if (full == null || width(g, full, f) <= room)
		{
			return full;
		}
		for (String c : fallbacks)
		{
			if (c != null && width(g, c, f) <= room)
			{
				return c;
			}
		}
		return fit(g, full, f, room);
	}

	/** Right edge available to the right readout: left of the login time, or the rounded end. */
	public static float rightLimit(Graphics2D g, BarLayout l, BarState s)
	{
		float u = l.u, edge = l.width - 26f * u;
		if (l.compact && l.rows > 1)
		{
			// The login time sits on the small-line row, so the headline may run to the edge.
			return compactEdge(l);
		}
		if (loginVisible(l, s, s == null ? 0 : s.nowMs))
		{
			Font f = l.rows == 1 ? Fonts.semiBold(Math.max(9f, 11f * u)) : Fonts.semiBold(11f * u);
			edge -= width(g, s.clock.format(s.nowMs), f) + 8f * u;
		}
		return edge;
	}

	private static boolean loginVisible(BarLayout l, BarState s, long nowMs)
	{
		return l.showRight && s != null && s.showLoginTime && s.clock != null && s.clock.onlineMs(nowMs) > 0;
	}

	private static float width(Graphics2D g, String s, Font f)
	{
		return (float) f.getStringBounds(s, g.getFontRenderContext()).getWidth();
	}

	/** The text, or the longest prefix plus an ellipsis that fits in {@code room} pixels. */
	static String fit(Graphics2D g, String s, Font f, float room)
	{
		if (s == null || s.isEmpty() || width(g, s, f) <= room)
		{
			return s;
		}
		for (int n = s.length() - 1; n > 0; n--)
		{
			String t = s.substring(0, n).trim() + "…";
			if (width(g, t, f) <= room)
			{
				return t;
			}
		}
		return "";
	}

	/** Online time, right-aligned at w - 26u, with "ONLINE" under it. */
	public static void login(Graphics2D g, BarLayout l, BarState s, long nowMs)
	{
		if (!loginVisible(l, s, nowMs))
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
		if (l.compact)
		{
			rightAligned(g, time, Fonts.semiBold(9f * u), Palette.DIM, compactEdge(l), 45f * u);
			return;
		}
		rightAligned(g, time, Fonts.semiBold(11f * u), Palette.DIM, right, 28f * u);
		Font label = Fonts.bold(8.5f * u).deriveFont(Collections.singletonMap(TextAttribute.TRACKING, 0.06f));
		rightAligned(g, "ONLINE", label, Palette.ONLINE_LABEL, right, 40f * u);
	}

	// ---- formatting ----

	/** Shown in place of a per-hour number until the session has a minute of logged-in time. */
	public static final String NO_RATE = "\u2014";

	/** True once per-hour rates may be shown (a minute of session time). */
	public static boolean ratesReady(BarState s)
	{
		return s.clock != null && s.clock.ratesReady(s.nowMs);
	}

	/** Logged-in time summed across relogs: the divisor of every session per-hour rate. */
	public static long sessionMs(BarState s)
	{
		return s.clock == null ? 0 : s.clock.sessionMs(s.nowMs);
	}

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
