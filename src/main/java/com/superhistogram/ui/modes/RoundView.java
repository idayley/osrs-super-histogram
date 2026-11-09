package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.skill.RoundTracker;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.Fonts;
import com.superhistogram.ui.Lane;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Px;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rounds lens for minigames (Wintertodt, Tempoross, Guardians of the Rift, Zalcano, Volcanic Mine): a line with
 * "round N" where each round starts and one where it ends. Right = the round's clock against your best and the
 * XP it has given; between rounds, how long the last one took (mint when it was your best) and rounds done.
 */
public final class RoundView implements Lens
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
		if (s.timeline == null || s.nowTick < 0 || s.rounds == null)
		{
			return;
		}
		float u = l.u;
		float bandTop = l.laneY(Lane.DOING), bandBottom = l.laneY(Lane.PRAYER) + l.laneH(Lane.PRAYER);
		float top = l.rows == 3 ? l.laneY(Lane.TOP) + 4f * u : bandTop - 4f * u;
		float bottom = l.rows == 3 ? l.laneY(Lane.RULER) : bandBottom + 4f * u;
		Font label = Fonts.bold(10f * u).deriveFont(Collections.singletonMap(TextAttribute.TRACKING, 0.04f));
		for (RoundTracker.Round r : s.rounds.history())
		{
			for (int t : new int[]{r.start, r.end})
			{
				if (t < 0 || t > s.nowTick)
				{
					continue;
				}
				float lx = TimelinePainter.x(l, s, t) - 2f * u;
				if (lx < l.timelineX - 40f * u)
				{
					continue;
				}
				g.setColor(Palette.LAP_LINE);
				g.fill(Px.rect(lx - 0.5f, top, 1f, bottom - top));
				if (l.rows == 3 && lx >= l.timelineX)
				{
					g.setFont(label);
					g.setColor(Palette.DIM);
					g.drawString(t == r.start ? "round " + r.number : "end", lx + 4f * u, l.laneY(Lane.RULER) + 4f * u);
				}
			}
		}
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.rounds == null)
		{
			return;
		}
		String[] lines = rightLines(s);
		ReadoutPainter.right(g, l, s, lines[0], rightColor(s), lines[1], headlineFallbacks(s), smallFallbacks(s),
			AgilityView.RIGHT_INSET);
	}

	private static String clock(int ticks)
	{
		return ReadoutPainter.minSec(Math.round(Math.max(0, ticks) * 0.6f));
	}

	/** In a round: "2:31" and "+8.1k xp · best 2:10". Between rounds: "Done 2:14" and "+12k xp · best · 5 rounds". */
	public static String[] rightLines(BarState s)
	{
		RoundTracker r = s.rounds;
		if (r == null)
		{
			return new String[]{"", ""};
		}
		String[] p = smallParts(s);
		if (r.roundPoints() >= 0)
		{
			// The game's own score leads: it is what the reward is paid on.
			if (r.inRound())
			{
				return new String[]{pts(r.roundPoints()), join(stability(r), clock(s.nowTick - r.roundStartTick()), p[1])};
			}
			return new String[]{pts(r.roundPoints()), join("Done " + clock(r.lastRoundTicks()), p[1])};
		}
		if (r.inRound())
		{
			return new String[]{clock(s.nowTick - r.roundStartTick()), join(p[0], p[1])};
		}
		return new String[]{"Done " + clock(r.lastRoundTicks()), join(p[0], p[1], p[2])};
	}

	/** "1,840 pts". */
	static String pts(int n)
	{
		return String.format(java.util.Locale.ROOT, "%,d pts", n);
	}

	/** "stability 74%" in Volcanic Mine; "" elsewhere. */
	private static String stability(RoundTracker r)
	{
		return r.stability() >= 0 && r.inRound() ? "stability " + r.stability() + "%" : "";
	}

	/** {round XP, best, rounds done}; "" when absent. */
	private static String[] smallParts(BarState s)
	{
		RoundTracker r = s.rounds;
		String xp = r.roundXp() > 0 ? "+" + ReadoutPainter.compact(r.roundXp()) + " xp" : "";
		String best = r.bestTicks() > 0 ? "best " + clock(r.bestTicks()) : "";
		if (!r.inRound() && r.lastWasBest())
		{
			best = "new best";
		}
		int n = r.rounds();
		return new String[]{xp, best, n + (n == 1 ? " round" : " rounds")};
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

	static String[] headlineFallbacks(BarState s)
	{
		RoundTracker r = s.rounds;
		if (r == null)
		{
			return new String[0];
		}
		if (r.roundPoints() >= 0)
		{
			return new String[]{String.format(java.util.Locale.ROOT, "%,d", r.roundPoints())};
		}
		if (r.inRound())
		{
			return new String[0];
		}
		return new String[]{clock(r.lastRoundTicks())};
	}

	/** Narrow bars keep the best time longest: it is what the clock is measured against. */
	static String[] smallFallbacks(BarState s)
	{
		if (s.rounds == null)
		{
			return new String[0];
		}
		String[] p = smallParts(s);
		RoundTracker r = s.rounds;
		if (r.roundPoints() >= 0)
		{
			// Points games: stability is the danger, then the clock.
			String c = r.inRound() ? clock(s.nowTick - r.roundStartTick()) : "Done " + clock(r.lastRoundTicks());
			return new String[]{join(stability(r), c), stability(r).isEmpty() ? c : stability(r)};
		}
		return new String[]{join(p[1], p[2]), p[1]};
	}

	public static Color rightColor(BarState s)
	{
		RoundTracker r = s.rounds;
		return r != null && !r.inRound() && r.lastWasBest() ? Palette.mint() : Palette.WHITE;
	}
}
