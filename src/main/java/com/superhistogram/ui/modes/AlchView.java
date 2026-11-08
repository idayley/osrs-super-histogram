package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.RightSide;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.skill.AlchTopRow;
import com.superhistogram.model.skill.AlchTracker;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.CombatPainter;
import com.superhistogram.ui.Lane;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.SkillPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Color;
import java.awt.Graphics2D;

/**
 * High-alch lens (high-alch.html): dims the prayer lane and adds the white "+N" ticks lost before each late
 * cast and the next-cast caret on the ruler; right = the session gold or XP.
 */
public final class AlchView implements Lens
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
		if (s.timeline == null || s.nowTick < 0 || s.alch == null || l.rows < 3)
		{
			return;
		}
		float u = l.u;
		if (s.wastedTicks)
		{
			for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
			{
				TickRecord r = s.timeline.peek(t);
				if (r != null && r.cast && r.castLost > 0)
				{
					SkillPainter.wasted(g, l, s, TimelinePainter.x(l, s, t - r.castLost) + 1,
						TimelinePainter.x(l, s, t) - 1, r.castLost);
				}
			}
		}
		int next = s.alch.nextReadyTick();
		if (next > s.nowTick && s.alch.showing(s.nowTick))
		{
			CombatPainter.caret(g, TimelinePainter.x(l, s, next), l.laneY(Lane.RULER) + 1f * u, u, Palette.NEXT_BEAT);
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
		String hour = lines[1].contains(" last hr") ? lines[1].substring(0, lines[1].indexOf(" last hr") + 8) : "";
		ReadoutPainter.rightCoin(g, l, s, lines[0], rightColor(s), lines[1], showsGold(s), new String[]{hour, ""});
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
		long gold = a.sessionGold();
		long xp = s.xp == null ? 0 : s.xp.sessionXp("Magic");
		long xpRate = s.xp == null || !ready ? 0 : s.xp.xpPerHour("Magic", ms);
		String xpRateText = ready ? ReadoutPainter.compact(xpRate) : ReadoutPainter.NO_RATE;
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
		// Gold is a total, not a rate: alching is often mixed with other things (salvaging), so a per-hour
		// figure over the session says little. Big = this run (since the last other activity); small = the
		// last hour and today.
		String goldHead = formatCoins(a.runGold());
		String small = formatCoins(a.goldLastHour(s.nowTick)) + " last hr";
		if (s.alchToday >= 0)
		{
			small += DOT + formatCoins(Math.max(s.alchToday, gold)) + " today";
		}
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
