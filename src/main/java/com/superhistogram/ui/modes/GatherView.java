package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.skill.GatherRun;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.ModeView;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.SkillPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Gathering view (gathering.html): one pill per resource clicked with a notch per roll, the item and
 * XP of each success on top, a stump (or ring) where the resource ran out and the "+N" before the next
 * click below, and the inventory count with an estimated time to full on the right.
 */
public final class GatherView implements ModeView
{
	private static final String DOT = " · ";
	private static final String WOODCUTTING = "Woodcutting", FIREMAKING = "Firemaking";

	@Override
	public void paintTimeline(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.timeline == null || s.nowTick < 0 || s.gather == null)
		{
			return;
		}
		pills(g, l, s);
		if (l.rows > 1)
		{
			items(g, l, s);
		}
		TimelinePainter.xp(g, l, s);
		TimelinePainter.hits(g, l, s);
		if (l.rows == 3)
		{
			actions(g, l, s);
		}
	}

	private static void pills(Graphics2D g, BarLayout l, BarState s)
	{
		float u = l.u, y = TimelinePainter.pillY(l), h = TimelinePainter.pillH(l);
		int oldest = TimelinePainter.oldestTick(l, s);
		for (GatherRun.Pill p : s.gather.pills())
		{
			if (p.rolls == 0 || p.startTick > s.nowTick || (p.endTick != -1 && p.endTick < oldest))
			{
				continue;
			}
			boolean wood = WOODCUTTING.equals(p.skill);
			float x0 = TimelinePainter.x(l, s, p.startTick);
			float x1 = TimelinePainter.x(l, s, p.endTick == -1 ? s.nowTick + 1 : Math.min(p.endTick, s.nowTick + 1));
			g.setColor(wood ? Palette.WC_PILL : Palette.skill(p.skill));
			float px0 = Math.max(x0, l.timelineX);
			g.fill(new RoundRectangle2D.Float(px0, y, Math.max(h, x1 - px0), h, h, h));
			if (l.rows == 1)
			{
				continue;
			}
			float ix = Math.max(x0 + 10f * u, l.timelineX + 10f * u);
			if (wood && ix < x1 - 8f * u)
			{
				SkillPainter.axe(g, ix, y + h / 2f, u, Palette.PILL_LABEL_SOLID);
			}
			g.setColor(Palette.NOTCH);
			int from = Math.max(p.startTick, oldest), to = Math.min(p.endTick == -1 ? s.nowTick : p.endTick, s.nowTick);
			for (int t = from; t <= to; t++)
			{
				TickRecord r = s.timeline.peek(t);
				float x = TimelinePainter.x(l, s, t);
				if (r != null && r.roll && x > ix + 6f * u && x < x1)
				{
					g.fill(new RoundRectangle2D.Float(x - 0.6f * u, y + 3f * u, 1.2f * u, h - 6f * u, 1.2f * u, 1.2f * u));
				}
			}
		}
	}

	/** The item icon of every success, just left of its XP. */
	private static void items(Graphics2D g, BarLayout l, BarState s)
	{
		String skill = s.gather.skill();
		if (skill == null || FIREMAKING.equals(skill))
		{
			return;
		}
		float u = l.u, y = TimelinePainter.xpY(l) - 3.5f * u;
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r == null || r.gathered <= 0)
			{
				continue;
			}
			float x = TimelinePainter.x(l, s, t) - 11f * u;
			if (WOODCUTTING.equals(skill))
			{
				SkillPainter.log(g, x, y, u);
			}
			else
			{
				SkillPainter.item(g, x, y, u, Palette.skill(skill));
			}
		}
	}

	private static void actions(Graphics2D g, BarLayout l, BarState s)
	{
		float u = l.u;
		boolean wood = WOODCUTTING.equals(s.gather.skill());
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r == null)
			{
				continue;
			}
			float x = TimelinePainter.x(l, s, t);
			if (r.depleted)
			{
				if (wood)
				{
					SkillPainter.stump(g, x + 6f * u, 45f * u, u);
				}
				else
				{
					SkillPainter.depleted(g, x + 6f * u, 45f * u, u);
				}
			}
			if (r.reclick > 0 && s.wastedTicks)
			{
				SkillPainter.wasted(g, l, TimelinePainter.x(l, s, t - r.reclick) + 13f * u, x, r.reclick);
			}
			if (r.gatherBank)
			{
				SkillPainter.bank(g, x, 46f * u, u, Palette.TEXT);
			}
		}
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.gather == null)
		{
			return;
		}
		String[] lines = rightLines(s);
		ReadoutPainter.right(g, l, s, lines[0], Palette.WHITE, lines[1], headlineFallbacks(s));
	}

	/** Headline and small line of the right readout; both empty without a run. */
	public static String[] rightLines(BarState s)
	{
		GatherRun run = s.gather;
		if (run == null || run.skill() == null)
		{
			return new String[]{"", ""};
		}
		String skill = run.skill();
		String noun = noun(skill);
		int count = run.count();
		int eta = run.etaTicks();
		long perHour = run.perHour();
		if (FIREMAKING.equals(skill))
		{
			if (run.full())
			{
				return new String[]{"Out of logs", run.successes() + " fires"};
			}
			return new String[]{count + " left", small(s, "done in ", eta, perHour, noun)};
		}
		int cap = run.capacity();
		if (run.full())
		{
			return new String[]{"Full · bank", (cap > 0 ? count + "/" + cap : String.valueOf(count)) + " " + noun};
		}
		return new String[]{cap > 0 ? count + "/" + cap : String.valueOf(count), small(s, "full in ", eta, perHour, noun)};
	}

	private static String small(BarState s, String etaPrefix, int eta, long perHour, String noun)
	{
		StringBuilder b = new StringBuilder();
		if (s.timeToFinish && eta >= 0)
		{
			b.append(etaPrefix).append(eta(eta));
		}
		boolean ready = ReadoutPainter.ratesReady(s);
		if (!ready || perHour > 0)
		{
			if (b.length() > 0)
			{
				b.append(DOT);
			}
			b.append(ready ? ReadoutPainter.compact(perHour) : ReadoutPainter.NO_RATE).append(' ').append(noun).append("/hr");
		}
		return b.length() == 0 ? noun : b.toString();
	}

	/** Shorter headlines for narrow bars: "Full · bank" becomes "Full". */
	public static String[] headlineFallbacks(BarState s)
	{
		GatherRun run = s.gather;
		if (run != null && run.full() && !FIREMAKING.equals(run.skill()))
		{
			return new String[]{"Full"};
		}
		return new String[0];
	}

	/** What the skill gathers: "logs", "ore", "fish", "fires". */
	public static String noun(String skill)
	{
		if (skill == null)
		{
			return "items";
		}
		switch (skill)
		{
			case WOODCUTTING:
				return "logs";
			case "Mining":
				return "ore";
			case "Fishing":
				return "fish";
			case FIREMAKING:
				return "fires";
			default:
				return "items";
		}
	}

	/** "~1:40": the ticks in seconds, rounded to the nearest 10 s (at least 10 s). */
	public static String eta(int ticks)
	{
		int secs = Math.max(10, (int) Math.round(ticks * 0.6 / 10.0) * 10);
		return "~" + ReadoutPainter.minSec(secs);
	}
}
