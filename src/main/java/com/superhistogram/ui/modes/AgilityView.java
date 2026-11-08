package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.skill.AgilityCourse;
import com.superhistogram.model.skill.LapCompare;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.Fonts;
import com.superhistogram.ui.ModeView;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.SkillPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Agility view (agility.html): one numbered pill per obstacle from the click to the landing, a thin line
 * at each lap start, XP and marks of grace on top, "+N" for the ticks stood still before the next click,
 * and this lap's time against the best (or last) lap on the right. The layout passed to
 * {@link #paintTimeline} already has the narrow agility tick width.
 */
public final class AgilityView implements ModeView
{
	private static final String DOT = " · ";
	/** The readout starts at the timeline end (the lead ticks right of the now line are empty), not 10u later. */
	public static final float RIGHT_INSET = 0f;

	/**
	 * Tick width that fits a whole lap: the timeline over the longer of the best and the last lap plus
	 * 6 ticks, between max(4, 6u) and 14u; 14u when no lap is known.
	 */
	public static float tickWidth(BarLayout l, AgilityCourse a)
	{
		float hi = 14f * l.u;
		if (a == null)
		{
			return hi;
		}
		int ref = Math.max(a.bestTicks(), a.lastLapTicks());
		if (ref <= 0)
		{
			return hi;
		}
		float lo = Math.max(4f, 6f * l.u);
		return Math.max(lo, Math.min(hi, l.timelineW / (ref + 6)));
	}

	@Override
	public void paintTimeline(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.timeline == null || s.nowTick < 0 || s.agility == null)
		{
			return;
		}
		int since = s.agility.courseSinceTick();
		lapLines(g, l, s, since);
		obstacles(g, l, s, since);
		if (l.rows > 1)
		{
			TimelinePainter.xp(g, l, s);
			marks(g, l, s, since);
		}
		TimelinePainter.hits(g, l, s);
		if (l.rows == 3 && s.wastedTicks)
		{
			lost(g, l, s, since);
		}
	}

	private static void lapLines(Graphics2D g, BarLayout l, BarState s, int since)
	{
		float u = l.u;
		float top = l.rows == 3 ? 8f * u : TimelinePainter.pillY(l) - 4f * u;
		float h = l.rows == 3 ? 48f * u : TimelinePainter.pillH(l) + 8f * u;
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
			g.fill(new Rectangle2D.Float(lx - 0.5f, top, 1f, h));
			if (l.rows == 3 && lx >= l.timelineX)
			{
				g.setFont(label);
				g.setColor(Palette.DIM);
				g.drawString("lap " + ls.number, lx + 4f * u, 59f * u);
			}
		}
	}

	private static void obstacles(Graphics2D g, BarLayout l, BarState s, int since)
	{
		float u = l.u, y = TimelinePainter.pillY(l), h = TimelinePainter.pillH(l);
		int oldest = TimelinePainter.oldestTick(l, s);
		Color solid = Palette.skill("Agility");
		for (AgilityCourse.Obstacle o : s.agility.obstacles())
		{
			if (o.startTick <= since || o.startTick > s.nowTick || (o.endTick != -1 && o.endTick < oldest))
			{
				continue;
			}
			float x1 = TimelinePainter.x(l, s, o.startTick);
			float x2 = TimelinePainter.x(l, s, o.endTick == -1 ? s.nowTick + 1 : Math.min(o.endTick, s.nowTick + 1));
			float px0 = Math.max(x1 + 1f, l.timelineX);
			g.setColor(o.failed ? Palette.withAlpha(solid, Palette.FAINT_ALPHA) : solid);
			g.fill(new RoundRectangle2D.Float(px0, y, Math.max(h, x2 - 1f - px0), h, h, h));
			float nx = x1 + 10f * u;
			if (l.rows > 1 && o.number > 0 && nx < x2 - 6f * u && nx > l.timelineX)
			{
				g.setFont(Fonts.bold(10f * u));
				g.setColor(o.failed ? Palette.PILL_LABEL_FAINT : Palette.PILL_LABEL_SOLID);
				String text = String.valueOf(o.number);
				g.drawString(text, nx - g.getFontMetrics().stringWidth(text) / 2f, y + h / 2f + 4f * u);
			}
		}
	}

	private static void marks(Graphics2D g, BarLayout l, BarState s, int since)
	{
		float u = l.u, y = TimelinePainter.xpY(l) - 3.5f * u;
		for (int t = Math.max(TimelinePainter.oldestTick(l, s), since + 1); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r != null && r.mark)
			{
				SkillPainter.mark(g, TimelinePainter.x(l, s, t) - 9f * u, y, u);
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
				SkillPainter.wasted(g, l, TimelinePainter.x(l, s, t - r.agilityLost) + 1f,
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

	/** Headline and small line of the right readout; both empty without a course. */
	public static String[] rightLines(BarState s)
	{
		AgilityCourse a = s.agility;
		if (a == null)
		{
			return new String[]{"", ""};
		}
		boolean open = a.lapStartTick() >= 0;
		String head = open ? lapTime(s.nowTick - a.lapStartTick())
			: a.lastLapTicks() >= 0 ? lapTime(a.lastLapTicks()) : "";
		return new String[]{head, joinParts(smallParts(s))};
	}

	/** The small line's facts: the best/last lap, the ticks lost and the slowest obstacle; "" when absent. */
	private static String[] smallParts(BarState s)
	{
		AgilityCourse a = s.agility;
		String compare = "";
		if (s.lapCompare == LapCompare.LAST)
		{
			int last = a.lapStartTick() >= 0 ? a.lastLapTicks() : a.previousLapTicks();
			compare = last >= 0 ? "last " + lapTime(last) : "";
		}
		else if (a.bestTicks() >= 0)
		{
			compare = "best " + lapTime(a.bestTicks());
		}
		return new String[]{compare, s.wastedTicks ? "\u2212" + a.lapLost() + "t" : "",
			a.slowestAfter() > 0 ? "slowest #" + a.slowestAfter() : ""};
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

	/** Shorter small lines for narrow bars: without the ticks lost, then only the best/last lap. */
	static String[] smallFallbacks(BarState s)
	{
		if (s.agility == null)
		{
			return new String[0];
		}
		String[] p = smallParts(s);
		return new String[]{joinParts(p[0], p[2]), joinParts(p[0])};
	}

	/** MINT between laps when the lap just finished was the best, else WHITE. */
	public static Color rightColor(BarState s)
	{
		AgilityCourse a = s.agility;
		return a != null && a.lapStartTick() < 0 && a.lastLapWasBest() ? Palette.mint() : Palette.WHITE;
	}
}
