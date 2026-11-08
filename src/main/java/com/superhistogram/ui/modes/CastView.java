package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.skill.SpellRun;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.CombatPainter;
import com.superhistogram.ui.Lane;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.SkillPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Skilling-spell lens (Plank Make, Enchant, Superheat, Spin Flax, ...): one Magic pill per cast, the white "+N"
 * ticks a cast came later than the spell's pace and a caret where the next cast can land, like high alch; right =
 * casts left from the input in the inventory, the time until it runs out and Magic XP per hour.
 */
public final class CastView implements Lens
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
		if (s.timeline == null || s.nowTick < 0 || s.spell == null || l.rows < 3)
		{
			return;
		}
		if (s.wastedTicks)
		{
			for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
			{
				TickRecord r = s.timeline.peek(t);
				if (r != null && r.spellLost > 0)
				{
					SkillPainter.wasted(g, l, s, TimelinePainter.x(l, s, t - r.spellLost) + 1,
						TimelinePainter.x(l, s, t) - 1, r.spellLost);
				}
			}
		}
		int next = s.spell.nextReadyTick();
		if (next > s.nowTick && s.spell.showing(s.nowTick))
		{
			CombatPainter.caret(g, TimelinePainter.x(l, s, next), l.laneY(Lane.RULER) + 1f * l.u, l.u, Palette.NEXT_BEAT);
		}
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.spell == null)
		{
			return;
		}
		String[] lines = rightLines(s);
		ReadoutPainter.right(g, l, s, lines[0], Palette.WHITE, lines[1], headlineFallbacks(s), smallFallbacks(s));
	}

	/** Big = "18 casts left" ("Out · bank" at 0, the casts made when the input is unknown); small = time, xp/hr. */
	public static String[] rightLines(BarState s)
	{
		SpellRun sp = s.spell;
		if (sp == null)
		{
			return new String[]{"", ""};
		}
		String[] p = smallParts(s);
		int left = sp.left();
		if (left == 0)
		{
			return new String[]{"Out · bank", join(casts(sp), p[1])};
		}
		if (left < 0)
		{
			return new String[]{casts(sp), p[1]};
		}
		return new String[]{left + (left == 1 ? " cast left" : " casts left"), join(p[0], p[1])};
	}

	/** {"done in ~0:43", "52k xp/hr"}; "" when absent. */
	private static String[] smallParts(BarState s)
	{
		int eta = s.spell.etaTicks();
		String done = s.timeToFinish && eta > 0 ? "done in " + GatherView.eta(eta) : "";
		String xp;
		if (!ReadoutPainter.ratesReady(s))
		{
			xp = ReadoutPainter.NO_RATE + " xp/hr";
		}
		else
		{
			long perHour = s.xp == null ? 0 : s.xp.xpPerHour("Magic", ReadoutPainter.sessionMs(s));
			xp = perHour > 0 ? ReadoutPainter.compact(perHour) + " xp/hr" : "";
		}
		return new String[]{done, xp};
	}

	static String[] headlineFallbacks(BarState s)
	{
		SpellRun sp = s.spell;
		if (sp == null)
		{
			return new String[0];
		}
		int left = sp.left();
		return left == 0 ? new String[]{"Out"} : left < 0 ? new String[0] : new String[]{left + " left"};
	}

	static String[] smallFallbacks(BarState s)
	{
		if (s.spell == null)
		{
			return new String[0];
		}
		String[] p = smallParts(s);
		if (s.spell.left() <= 0)
		{
			return new String[]{casts(s.spell)};
		}
		return new String[]{join(p[0].replace("done in ", ""), p[1].replace(" xp/hr", "/hr")), p[0]};
	}

	private static String casts(SpellRun sp)
	{
		return sp.casts() + (sp.casts() == 1 ? " cast" : " casts");
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
