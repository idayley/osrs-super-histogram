package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Span;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.skill.InventoryRun;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.DoingLane;
import com.superhistogram.ui.Icons;
import com.superhistogram.ui.Lane;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Px;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.SkillPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/**
 * Production lens (cooking.html): dims the prayer lane; over the base's inventory pill it outlines what is
 * still to make up to the finish, plants the finish flag ahead of "now" and the white "+N" wasted before each
 * bank click; right = the time left.
 */
public final class ProductionView implements Lens
{
	private static final String CONSTRUCTION = "Construction";

	@Override
	public float[] emphasis(BarState s)
	{
		return Lens.full();
	}

	@Override
	public void overlay(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.timeline == null || s.nowTick < 0 || s.run == null)
		{
			return;
		}
		finishOutline(g, l, s);
		if (l.rows == 3)
		{
			actions(g, l, s);
		}
	}

	/** Faint outline of the current run from its start to the finish tick, in the doing lane. */
	private static void finishOutline(Graphics2D g, BarLayout l, BarState s)
	{
		InventoryRun run = s.run;
		if (!run.running() || !l.hasLane(Lane.DOING))
		{
			return;
		}
		float u = l.u, y = l.laneY(Lane.DOING), h = l.laneH(Lane.DOING);
		int oldest = TimelinePainter.oldestTick(l, s);
		for (Span sp : run.runs())
		{
			if (sp.endTick != -1 || !DoingLane.productionVisible(s, sp, oldest))
			{
				continue;
			}
			Color c = "Cooking".equals(sp.kind) ? Palette.COOK_PILL : Palette.skill(sp.kind);
			float x0 = TimelinePainter.x(l, s, sp.startTick);
			float xf = TimelinePainter.x(l, s, run.finishTick());
			float sw = Math.min(1.5f * u, h / 4f);
			if (xf - x0 > sw)
			{
				g.setStroke(new BasicStroke(sw));
				g.setColor(Palette.withAlpha(c, 0.45f));
				g.draw(Px.round(x0 + sw / 2, y + sw / 2, xf - x0 - sw, h - sw, h - sw, h - sw));
			}
		}
	}

	private static void actions(Graphics2D g, BarLayout l, BarState s)
	{
		float u = l.u;
		InventoryRun run = s.run;
		if (s.timeToFinish && run.running() && run.finishTick() > s.nowTick)
		{
			float bottom = l.laneY(Lane.INPUTS) + l.laneH(Lane.INPUTS);
			SkillPainter.flag(g, TimelinePainter.x(l, s, run.finishTick()), bottom - Icons.BOX * u, l.height - 1f * u, u,
				Palette.WHITE);
		}
		if (!s.wastedTicks)
		{
			return;
		}
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r != null && r.bankClick && r.wasted > 0)
			{
				float x = TimelinePainter.x(l, s, t);
				SkillPainter.wasted(g, l, s, TimelinePainter.x(l, s, t - r.wasted), x - 9f * u, r.wasted);
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
		String noun = "Cooking".equals(run.skill()) ? "cooked" : CONSTRUCTION.equals(run.skill()) ? "built" : "done";
		String count = run.done() + " of " + run.total();
		if (run.running())
		{
			if (s.timeToFinish)
			{
				int secs = Math.round(Math.max(0, run.finishTick() - s.nowTick) * 0.6f);
				return new String[]{ReadoutPainter.minSec(secs), count + " " + noun};
			}
			return new String[]{count, noun};
		}
		if (run.bankTick() == -1)
		{
			// Construction restocks from a butler or phials as often as a bank.
			return new String[]{CONSTRUCTION.equals(run.skill()) ? "Need planks" : "Bank now", count + " " + noun};
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
		if (run != null && !run.running() && run.bankTick() == -1 && CONSTRUCTION.equals(run.skill()))
		{
			return new String[]{"Planks", "Out"};
		}
		return new String[0];
	}

	public static Color rightColor(BarState s)
	{
		InventoryRun run = s.run;
		return run != null && !run.running() && run.bankTick() != -1 ? Palette.mint() : Palette.WHITE;
	}
}
