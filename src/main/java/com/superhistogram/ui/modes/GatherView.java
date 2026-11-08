package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.skill.GatherRun;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.Lane;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.SkillPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Graphics2D;

/**
 * Gathering lens (gathering.html): dims the prayer lane and adds the white "+N" re-click delay after each
 * depleted resource; right = the inventory count with an estimated time to full.
 */
public final class GatherView implements Lens
{
	private static final String DOT = " · ";
	private static final String WOODCUTTING = "Woodcutting", FIREMAKING = "Firemaking";

	@Override
	public float[] emphasis(BarState s)
	{
		return Lens.full();
	}

	@Override
	public void overlay(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.timeline == null || s.nowTick < 0 || s.gather == null || l.rows < 3 || !s.wastedTicks)
		{
			return;
		}
		float u = l.u;
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r != null && r.reclick > 0)
			{
				SkillPainter.wasted(g, l, s, TimelinePainter.x(l, s, t - r.reclick) + 13f * u, TimelinePainter.x(l, s, t),
					r.reclick);
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
			return new String[]{"Sailing".equals(skill) ? "Full · sort" : "Full · bank", (cap > 0 ? count + "/" + cap : String.valueOf(count)) + " " + noun};
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
			case "Sailing":
				return "salvage";
			default:
				return "items";
		}
	}

	/** "~1:40": the ticks in seconds, rounded to 10 s; under 30 s to the second, so the last stretch counts down. */
	public static String eta(int ticks)
	{
		double raw = ticks * 0.6;
		int secs = raw < 30 ? Math.max(1, (int) Math.round(raw)) : (int) Math.round(raw / 10.0) * 10;
		return "~" + ReadoutPainter.minSec(secs);
	}
}
