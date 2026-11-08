package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import com.superhistogram.model.XpDrops;
import com.superhistogram.model.skill.AlchTopRow;
import com.superhistogram.ui.modes.AlchView;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The XP grammar shared by every mode (multi-xp.html): each skill's drop as its own coloured "+N",
 * side by side on the tick it landed. Skills that drop on most recent ticks become small dots that
 * hide under any number group they would overlap.
 */
final class XpRow
{
	/** A skill is frequent with drops on at least FREQUENT_MIN distinct ticks of the last FREQUENT_WINDOW. */
	static final int FREQUENT_WINDOW = 10, FREQUENT_MIN = 5;

	private XpRow()
	{
	}

	static Set<String> frequentSkills(Timeline t, int nowTick)
	{
		Set<String> out = new HashSet<>();
		if (t == null)
		{
			return out;
		}
		Map<String, Integer> ticks = new HashMap<>();
		for (int k = nowTick - FREQUENT_WINDOW + 1; k <= nowTick; k++)
		{
			TickRecord r = t.peek(k);
			if (r == null)
			{
				continue;
			}
			Set<String> seen = new HashSet<>();
			for (XpDrop d : r.xp)
			{
				if (seen.add(d.skill) && ticks.merge(d.skill, 1, Integer::sum) >= FREQUENT_MIN)
				{
					out.add(d.skill);
				}
			}
		}
		return out;
	}

	/** Paints the row with each listed tick's numbers moved right by its shift (px), e.g. past a hit number. */
	static void paintShifted(Graphics2D g, BarLayout l, BarState s, Map<Integer, Float> shift)
	{
		layout(g, l, s, true, null, shift);
	}

	/** x ranges {left, right} of each tick's number group, without painting. */
	static List<float[]> numberRanges(Graphics2D g, BarLayout l, BarState s)
	{
		List<float[]> out = new ArrayList<>();
		layout(g, l, s, false, out, null);
		return out;
	}

	private static void layout(Graphics2D g, BarLayout l, BarState s, boolean draw, List<float[]> ranges,
		Map<Integer, Float> shift)
	{
		if (s == null || s.timeline == null || s.nowTick < 0)
		{
			return;
		}
		// Each alch cast's coins ride the top lane in every mode; they stay even when XP drops are off.
		boolean alch = s.alch != null;
		boolean xpOff = s.xpDrops == XpDrops.OFF;
		if (xpOff && !alch)
		{
			return;
		}
		if (l.rows == 1)
		{
			return;
		}
		float u = l.u;
		float baseline = TimelinePainter.xpY(l);
		float dotY = baseline - 3f * u;
		Set<String> frequent = s.xpDots ? frequentSkills(s.timeline, s.nowTick) : new HashSet<>();
		g.setFont(Fonts.bold(10f * u));
		float busyTo = -Float.MAX_VALUE;
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			boolean gold = alch && r != null && r.cast && s.alchTopRow != AlchTopRow.XP;
			if (r == null || (r.xp.isEmpty() && !gold) || (xpOff && !gold))
			{
				continue;
			}
			float x = TimelinePainter.x(l, s, t) + -4f * u;
			Float moved = shift == null ? null : shift.get(t);
			if (moved != null)
			{
				x += moved;
			}
			if (x < l.timelineX)
			{
				// Scrolling off the left edge: drop the whole label rather than leave a fragment ("400").
				continue;
			}
			float groupLeft = x, groupRight = -1;
			if (gold)
			{
				String text = AlchView.formatCastCoins(r.castCoins);
				int w = g.getFontMetrics().stringWidth(text);
				if (draw)
				{
					SkillPainter.coin(g, x + 3f * u, baseline - 3.5f * u, 3.4f * u);
					g.setColor(r.castCoins == null ? Palette.DIM : Palette.damage());
					g.drawString(text, x + 9f * u, baseline);
				}
				x += 9f * u + w + 6f * u;
				busyTo = x + 2f * u;
			}
			for (XpDrop d : r.xp)
			{
				if (xpOff || (gold && s.alchTopRow == AlchTopRow.GOLD && d.skill.equals("Magic")
					&& d.amount == com.superhistogram.model.skill.AlchTracker.XP))
				{
					continue;
				}
				g.setColor(Palette.skill(d.skill));
				if (s.xpDrops == XpDrops.DOTS_ONLY || frequent.contains(d.skill))
				{
					if (x < busyTo)
					{
						continue;
					}
					if (draw)
					{
						g.fill(Icons.icon(Icons.Icon.DOT, x + 2.6f * u, dotY + 2.6f * u, u * 5.2f / 6f));
					}
					x += 9f * u;
					continue;
				}
				String text = "+" + d.amount;
				int w = g.getFontMetrics().stringWidth(text);
				if (draw)
				{
					g.drawString(text, x, baseline);
				}
				groupRight = x + w;
				x += w + 6f * u;
				busyTo = x + 2f * u;
			}
			if (ranges != null && groupRight >= 0)
			{
				ranges.add(new float[]{groupLeft, groupRight});
			}
		}
	}
}
