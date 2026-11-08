package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Span;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.skill.InventoryRun;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.Fonts;
import com.superhistogram.ui.ModeView;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.SkillPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Production view (cooking.html): one long pill per inventory (solid = made so far, faint outline =
 * still to make), XP and "burnt" on top, the finish flag and bank click below, and the time left
 * on the right.
 */
public final class ProductionView implements ModeView
{
	@Override
	public void paintTimeline(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.timeline == null || s.nowTick < 0 || s.run == null)
		{
			return;
		}
		pills(g, l, s);
		TimelinePainter.xp(g, l, s);
		burnt(g, l, s);
		TimelinePainter.hits(g, l, s);
		if (l.rows == 3)
		{
			actions(g, l, s);
		}
	}

	private static void pills(Graphics2D g, BarLayout l, BarState s)
	{
		InventoryRun run = s.run;
		float u = l.u, y = TimelinePainter.pillY(l), h = TimelinePainter.pillH(l);
		int oldest = TimelinePainter.oldestTick(l, s);
		for (Span sp : run.runs())
		{
			boolean current = sp.endTick == -1;
			if (sp.startTick > s.nowTick || (!current && sp.endTick < oldest))
			{
				continue;
			}
			Color c = "Cooking".equals(sp.kind) ? Palette.COOK_PILL : Palette.skill(sp.kind);
			float x0 = TimelinePainter.x(l, s, sp.startTick);
			if (current && run.running())
			{
				float xf = TimelinePainter.x(l, s, run.finishTick());
				float sw = 1.5f * u;
				g.setStroke(new BasicStroke(sw));
				g.setColor(Palette.withAlpha(c, 0.45f));
				g.draw(new RoundRectangle2D.Float(x0 + sw / 2, y + sw / 2, xf - x0 - sw, h - sw, h - sw, h - sw));
			}
			int endTick = current ? run.finishTick() : sp.endTick;
			float solidEnd = Math.max(x0 + h, TimelinePainter.x(l, s, Math.min(s.nowTick + 1, endTick)));
			g.setColor(c);
			g.fill(new RoundRectangle2D.Float(x0, y, solidEnd - x0, h, h, h));
			if ("Cooking".equals(sp.kind) && l.rows > 1)
			{
				float ix = Math.max(x0 + 11f * u, l.timelineX + 11f * u);
				if (ix < solidEnd - 6f * u)
				{
					SkillPainter.pot(g, ix, y + h / 2f + 1f * u, u, Palette.PILL_LABEL_SOLID);
				}
			}
		}
	}

	private static void burnt(Graphics2D g, BarLayout l, BarState s)
	{
		if (l.rows == 1)
		{
			return;
		}
		g.setFont(Fonts.semiBold(9.5f * l.u));
		g.setColor(Palette.BURNT);
		float y = TimelinePainter.xpY(l);
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r != null && r.failedAction)
			{
				float w = g.getFontMetrics().stringWidth("burnt");
				g.drawString("burnt", TimelinePainter.x(l, s, t) - w / 2f, y);
			}
		}
	}

	private static void actions(Graphics2D g, BarLayout l, BarState s)
	{
		float u = l.u;
		InventoryRun run = s.run;
		if (s.timeToFinish && run.running() && run.finishTick() > s.nowTick)
		{
			SkillPainter.flag(g, TimelinePainter.x(l, s, run.finishTick()), 41f * u, 61f * u, u, Palette.WHITE);
		}
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r == null || !r.bankClick)
			{
				continue;
			}
			float x = TimelinePainter.x(l, s, t);
			SkillPainter.bank(g, x, 46f * u, u, Palette.TEXT);
			if (r.wasted > 0 && s.wastedTicks)
			{
				SkillPainter.wasted(g, l, TimelinePainter.x(l, s, t - r.wasted), x - 9f * u, r.wasted);
			}
		}
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		String[] lines = rightLines(s);
		ReadoutPainter.right(g, l, s, lines[0], rightColor(s), lines[1], headlineFallbacks(s));
	}

	/** Headline and small line of the right readout; both empty without a run. */
	public static String[] rightLines(BarState s)
	{
		InventoryRun run = s.run;
		if (run == null || run.skill() == null)
		{
			return new String[]{"", ""};
		}
		String noun = "Cooking".equals(run.skill()) ? "cooked" : "done";
		String count = run.done() + " of " + run.total();
		if (run.running())
		{
			if (s.timeToFinish)
			{
				int secs = Math.round(Math.max(0, run.finishTick() - s.nowTick) * 0.6f);
				return new String[]{ReadoutPainter.minSec(secs), "left · " + count + " " + noun};
			}
			return new String[]{count, noun};
		}
		if (run.bankTick() == -1)
		{
			return new String[]{"Bank now", count + " " + noun};
		}
		String head = run.wasted() == 0 ? "Banking ✓" : s.wastedTicks ? "Banking · +" + run.wasted() : "Banking";
		return new String[]{head, count + " " + noun};
	}

	/** Shorter headlines for narrow bars: "Banking · +3" becomes "Bank +3", then "+3". */
	public static String[] headlineFallbacks(BarState s)
	{
		InventoryRun run = s.run;
		if (run != null && !run.running() && run.bankTick() != -1 && run.wasted() > 0 && s.wastedTicks)
		{
			return new String[]{"Bank +" + run.wasted(), "+" + run.wasted()};
		}
		return new String[0];
	}

	public static Color rightColor(BarState s)
	{
		InventoryRun run = s.run;
		return run != null && !run.running() && run.bankTick() != -1 ? Palette.mint() : Palette.WHITE;
	}
}
