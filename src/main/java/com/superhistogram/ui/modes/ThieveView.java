package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.skill.ThieveRun;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Thieving lens: the base timeline shows one pill per attempt and a grey "stunned" gap after each failure;
 * right = coin pouches to the cap (or stolen items to a full inventory), with the success rate and Thieving XP
 * per hour.
 */
public final class ThieveView implements Lens
{
	private static final String DOT = " · ";
	private static final String THIEVING = ThieveRun.SKILL;

	@Override
	public float[] emphasis(BarState s)
	{
		return Lens.full();
	}

	@Override
	public void overlay(Graphics2D g, BarLayout l, BarState s)
	{
		// The attempt pills and stun gaps are on the base timeline already.
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.thieve == null)
		{
			return;
		}
		String[] lines = rightLines(s);
		ReadoutPainter.right(g, l, s, lines[0], Palette.WHITE, lines[1], headlineFallbacks(s), smallFallbacks(s));
	}

	/** True when the pouches are at the cap (or the inventory is full): every attempt from here is wasted. */
	static boolean full(ThieveRun t)
	{
		int cap = t.capacity();
		return cap > 0 && t.count() >= cap;
	}

	/**
	 * Big = "17/28 pouches" (or "14/28 items" stolen); at the cap "Open pouches" / "Full · bank". Small = the
	 * success rate and Thieving XP per hour.
	 */
	public static String[] rightLines(BarState s)
	{
		ThieveRun t = s.thieve;
		if (t == null)
		{
			return new String[]{"", ""};
		}
		String[] p = smallParts(s);
		if (full(t))
		{
			return new String[]{t.countsPouches() ? "Open pouches" : "Full · bank", count(t)};
		}
		return new String[]{count(t), join(p[0], p[1])};
	}

	/** "17/28 pouches", "14/28 items", or the bare count when the capacity isn't known yet. */
	private static String count(ThieveRun t)
	{
		int cap = t.capacity();
		String noun = t.countsPouches() ? "pouches" : "items";
		return (cap > 0 ? t.count() + "/" + cap : String.valueOf(t.count())) + " " + noun;
	}

	/** {success rate, xp/hr}; "" when absent. */
	private static String[] smallParts(BarState s)
	{
		int rate = s.thieve.successRate();
		String xp;
		if (!ReadoutPainter.ratesReady(s))
		{
			xp = ReadoutPainter.NO_RATE + " xp/hr";
		}
		else
		{
			long perHour = s.xp == null ? 0 : s.xp.xpPerHour(THIEVING, ReadoutPainter.sessionMs(s));
			xp = perHour > 0 ? ReadoutPainter.compact(perHour) + " xp/hr" : "";
		}
		return new String[]{rate < 0 ? "" : rate + "%", xp};
	}

	private static String join(String... parts)
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

	/** Narrow bars: "17/28" without the noun; "Full" at the cap. */
	static String[] headlineFallbacks(BarState s)
	{
		ThieveRun t = s.thieve;
		if (t == null)
		{
			return new String[0];
		}
		if (full(t))
		{
			return new String[]{"Full"};
		}
		int cap = t.capacity();
		return new String[]{cap > 0 ? t.count() + "/" + cap : String.valueOf(t.count())};
	}

	/** Narrow bars: "93% · 268k/hr", then "93% success", then "93%". */
	static String[] smallFallbacks(BarState s)
	{
		ThieveRun t = s.thieve;
		if (t == null || full(t))
		{
			return new String[0];
		}
		String[] p = smallParts(s);
		String rate = p[0];
		return new String[]{join(rate, p[1].replace(" xp/hr", "/hr")), rate.isEmpty() ? "" : rate + " success", rate};
	}
}
