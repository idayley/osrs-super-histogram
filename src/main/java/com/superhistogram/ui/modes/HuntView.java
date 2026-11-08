package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Levels;
import com.superhistogram.model.skill.TrapTracker;
import com.superhistogram.model.skill.TrapTracker.State;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Hunter lens: the base timeline shows a pill per trap check; right = your traps and how many caught something
 * or went down (what needs a click), with your catches and Hunter XP per hour.
 */
public final class HuntView implements Lens
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
		// The check pills are on the base timeline already.
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.hunt == null)
		{
			return;
		}
		String[] lines = rightLines(s);
		ReadoutPainter.right(g, l, s, lines[0], Palette.WHITE, lines[1], headlineFallbacks(s), smallFallbacks(s));
	}

	/** The traps you may set, from your Hunter level; -1 when unknown. */
	private static int max(BarState s)
	{
		long xp = s.xp == null ? -1 : s.xp.currentXp(TrapTracker.SKILL);
		return xp < 0 ? -1 : TrapTracker.maxTraps(Levels.levelFor(xp));
	}

	/** "2 full · 1 down"; "" when every trap is set. */
	private static String attention(TrapTracker t)
	{
		int full = t.count(State.CAUGHT), down = t.count(State.DOWN);
		return join(full > 0 ? full + " full" : "", down > 0 ? down + " down" : "");
	}

	/** "4/5 set" (or "4 set" without a level). */
	private static String set(BarState s)
	{
		int max = max(s), n = s.hunt.traps();
		return (max > 0 ? n + "/" + Math.max(max, n) : String.valueOf(n)) + " traps";
	}

	/** Big = "5/5 traps · 2 full · 1 down"; small = catches and Hunter XP per hour. */
	public static String[] rightLines(BarState s)
	{
		TrapTracker t = s.hunt;
		if (t == null)
		{
			return new String[]{"", ""};
		}
		String[] p = smallParts(s);
		return new String[]{join(set(s), attention(t)), join(p[0], p[1])};
	}

	private static String[] smallParts(BarState s)
	{
		int caught = s.hunt.caught();
		String xp;
		if (!ReadoutPainter.ratesReady(s))
		{
			xp = ReadoutPainter.NO_RATE + " xp/hr";
		}
		else
		{
			long perHour = s.xp == null ? 0 : s.xp.xpPerHour(TrapTracker.SKILL, ReadoutPainter.sessionMs(s));
			xp = perHour > 0 ? ReadoutPainter.compact(perHour) + " xp/hr" : "";
		}
		return new String[]{caught + " caught", xp};
	}

	/** Narrow bars: what needs a click comes first ("2 full · 1 down", "2 full"), else "5/5 traps". */
	static String[] headlineFallbacks(BarState s)
	{
		TrapTracker t = s.hunt;
		if (t == null)
		{
			return new String[0];
		}
		String a = attention(t);
		int full = t.count(State.CAUGHT);
		List<String> out = new ArrayList<>();
		if (!a.isEmpty())
		{
			out.add(a);
			out.add(full > 0 ? full + " full" : t.count(State.DOWN) + " down");
		}
		else
		{
			out.add(set(s));
			out.add(set(s).replace(" traps", ""));
		}
		return out.toArray(new String[0]);
	}

	static String[] smallFallbacks(BarState s)
	{
		if (s.hunt == null)
		{
			return new String[0];
		}
		String[] p = smallParts(s);
		return new String[]{join(p[0], p[1].replace(" xp/hr", "/hr")), p[0]};
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
}
