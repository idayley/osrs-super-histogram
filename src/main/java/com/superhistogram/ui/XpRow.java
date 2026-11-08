package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.Timeline;
import com.superhistogram.model.XpDrop;
import com.superhistogram.model.XpDrops;
import com.superhistogram.model.combat.ActionsRow;
import com.superhistogram.model.skill.AlchTopRow;
import com.superhistogram.ui.modes.AlchView;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
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
	enum Row
	{
		TOP,
		LOW
	}

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

	static void paint(Graphics2D g, BarLayout l, BarState s, Row row)
	{
		layout(g, l, s, row, true, null, null);
	}

	/** Paints the row, dropping any number that would touch a reserved x range (and the rest of its tick). */
	static void paint(Graphics2D g, BarLayout l, BarState s, Row row, List<float[]> reserved)
	{
		layout(g, l, s, row, true, reserved, null);
	}

	/** x ranges {left, right} of each tick's number group, without painting. */
	static List<float[]> numberRanges(Graphics2D g, BarLayout l, BarState s, Row row)
	{
		List<float[]> out = new ArrayList<>();
		layout(g, l, s, row, false, null, out);
		return out;
	}

	private static void layout(Graphics2D g, BarLayout l, BarState s, Row row, boolean draw,
		List<float[]> reserved, List<float[]> ranges)
	{
		if (s == null || s.timeline == null || s.nowTick < 0)
		{
			return;
		}
		// High alch puts each cast's coins in the top row; that stays even when XP drops are off.
		boolean alch = row == Row.TOP && s.mode == Mode.ALCH && s.alch != null;
		boolean xpOff = s.xpDrops == XpDrops.OFF;
		if (xpOff && !alch)
		{
			return;
		}
		if (row == Row.TOP ? l.rows == 1 : l.rows < 3)
		{
			return;
		}
		float u = l.u;
		float baseline = row == Row.TOP ? TimelinePainter.xpY(l) : 48f * u;
		float dotY = row == Row.TOP ? baseline - 3f * u : 44f * u;
		boolean swords = s.actionsRow != ActionsRow.OFF;
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
			float x = TimelinePainter.x(l, s, t) + (row == Row.LOW && swords && r.attackLate >= 0 ? 8f : -4f) * u;
			float groupLeft = x, groupRight = -1;
			boolean dropNumbers = false;
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
						g.fill(new Ellipse2D.Float(x, dotY - 2.6f * u, 5.2f * u, 5.2f * u));
					}
					x += 9f * u;
					continue;
				}
				String text = "+" + d.amount;
				int w = g.getFontMetrics().stringWidth(text);
				if (dropNumbers || overlaps(reserved, x, x + w))
				{
					dropNumbers = true;
					continue;
				}
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

	private static boolean overlaps(List<float[]> ranges, float x0, float x1)
	{
		if (ranges != null)
		{
			for (float[] r : ranges)
			{
				if (x0 < r[1] && x1 > r[0])
				{
					return true;
				}
			}
		}
		return false;
	}
}
