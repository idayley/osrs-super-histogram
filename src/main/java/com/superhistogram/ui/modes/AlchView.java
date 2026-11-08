package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.RightSide;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.skill.AlchTopRow;
import com.superhistogram.model.skill.AlchTracker;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.CombatPainter;
import com.superhistogram.ui.ModeView;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.SkillPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * High-alch view (high-alch.html): one 5-tick pill per cast (touching when you cast on time), the
 * coins and/or XP of each cast on top, lost ticks and the next-cast caret below, and the session
 * gold or XP on the right.
 */
public final class AlchView implements ModeView
{
	private static final String DOT = " · ";

	@Override
	public void paintTimeline(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.timeline == null || s.nowTick < 0)
		{
			return;
		}
		if (s.alch != null)
		{
			pills(g, l, s);
		}
		TimelinePainter.xp(g, l, s);
		TimelinePainter.hits(g, l, s);
		if (s.alch != null && l.rows == 3)
		{
			actions(g, l, s);
		}
	}

	private static void pills(Graphics2D g, BarLayout l, BarState s)
	{
		for (int t = TimelinePainter.oldestTick(l, s) - AlchTracker.CAST_TICKS; t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r != null && r.cast)
			{
				TimelinePainter.pill(g, l, TimelinePainter.x(l, s, t),
					TimelinePainter.x(l, s, Math.min(t + AlchTracker.CAST_TICKS, s.nowTick + 1)),
					Palette.PRAYER_MAGIC, false, null);
			}
		}
	}

	private static void actions(Graphics2D g, BarLayout l, BarState s)
	{
		float u = l.u;
		if (s.wastedTicks)
		{
			for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
			{
				TickRecord r = s.timeline.peek(t);
				if (r != null && r.cast && r.castLost > 0)
				{
					SkillPainter.wasted(g, l, TimelinePainter.x(l, s, t - r.castLost) + 1,
						TimelinePainter.x(l, s, t) - 1, r.castLost);
				}
			}
		}
		int next = s.alch.nextReadyTick();
		if (next > s.nowTick && s.alch.showing(s.nowTick))
		{
			CombatPainter.caret(g, TimelinePainter.x(l, s, next), 55f * u, u, Palette.NEXT_BEAT);
		}
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.alch == null)
		{
			return;
		}
		String[] lines = rightLines(s);
		ReadoutPainter.right(g, l, s, lines[0], rightColor(s), lines[1], showsGold(s));
	}

	private static boolean showsGold(BarState s)
	{
		return s.alchTopRow != AlchTopRow.XP;
	}

	/** Headline colour: yellow for coins, white for XP. */
	public static Color rightColor(BarState s)
	{
		if (s.alch == null || !showsGold(s))
		{
			return Palette.WHITE;
		}
		return Palette.damage();
	}

	/** "+236", "+1.2k" or "?" when the value is unknown. */
	public static String formatCoins(Long p)
	{
		if (p == null)
		{
			return "?";
		}
		return "+" + ReadoutPainter.compact(p);
	}

	/** One cast's coins, exact: "+2,360", or "?" when the value is unknown. */
	public static String formatCastCoins(Long p)
	{
		if (p == null)
		{
			return "?";
		}
		return "+" + String.format(java.util.Locale.ROOT, "%,d", p);
	}

	/** Headline and small line of the right readout; both empty without a tracker. */
	public static String[] rightLines(BarState s)
	{
		AlchTracker a = s.alch;
		if (a == null)
		{
			return new String[]{"", ""};
		}
		// Rates divide by session time (summed across relogs) and show "—" in its first minute.
		boolean ready = ReadoutPainter.ratesReady(s);
		long ms = ReadoutPainter.sessionMs(s);
		boolean perHour = s.rightSide != RightSide.SESSION;
		long gold = a.sessionGold(), goldRate = ready ? a.goldPerHour(ms) : 0;
		long xp = s.xp == null ? 0 : s.xp.sessionXp("Magic");
		long xpRate = s.xp == null || !ready ? 0 : s.xp.xpPerHour("Magic", ms);
		String goldRateText = ready ? formatCoins(goldRate) : ReadoutPainter.NO_RATE;
		String xpRateText = ready ? ReadoutPainter.compact(xpRate) : ReadoutPainter.NO_RATE;
		String goldHead = perHour ? goldRateText + "/hr" : formatCoins(gold);
		String goldRest = perHour ? formatCoins(gold) + " this session" : goldRateText + "/hr";
		String xpHead = perHour ? xpRateText + " xp/hr" : ReadoutPainter.compact(xp) + " xp";
		String xpRest = perHour ? ReadoutPainter.compact(xp) + " xp" : xpRateText + "/hr";
		String level = MinimalView.levelPart(s, "Magic", xpRate);
		if (level != null)
		{
			xpRest += DOT + level;
		}
		if (s.alchTopRow == AlchTopRow.XP)
		{
			return new String[]{xpHead, xpRest};
		}
		String small = goldRest;
		if (s.alchTopRow == AlchTopRow.BOTH)
		{
			small += DOT + xpHead;
		}
		if (a.unpricedCasts() > 0)
		{
			small += DOT + a.unpricedCasts() + " unpriced";
		}
		return new String[]{goldHead, small};
	}
}
