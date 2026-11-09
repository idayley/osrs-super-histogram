package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.combat.ActionsRow;
import com.superhistogram.model.combat.Beat;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.BaseTimeline;
import com.superhistogram.ui.CombatPainter;
import com.superhistogram.ui.Fonts;
import com.superhistogram.ui.Icons;
import com.superhistogram.ui.Lane;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Px;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Lazy-flick lens: adds the "+N late" by each late sword (the beat carets are on every bar); right = beat trail
 * and the last verdict.
 */
public final class LazyFlickView implements Lens
{
	@Override
	public float[] emphasis(BarState s)
	{
		return Lens.full();
	}

	@Override
	public void overlay(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.timeline == null || s.nowTick < 0 || l.rows < 3)
		{
			return;
		}
		late(g, l, s);
	}

	/** "+N" ticks late just right of each late sword in the inputs lane (swords shown only). */
	private static void late(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.actionsRow == ActionsRow.OFF || !l.hasLane(Lane.INPUTS))
		{
			return;
		}
		float u = l.u, baseline = l.laneY(Lane.INPUTS) + l.laneH(Lane.INPUTS) - 0.5f * u;
		g.setFont(Fonts.bold(9f * u));
		g.setColor(Palette.lost());
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r != null && r.attackLate > 0)
			{
				g.drawString("+" + r.attackLate, BaseTimeline.swordX(l, TimelinePainter.x(l, s, t)) + 5f * u, baseline);
			}
		}
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		if (!l.showRight)
		{
			return;
		}
		List<Beat.Verdict> trail = s.beat == null ? java.util.Collections.<Beat.Verdict>emptyList() : s.beat.trail();
		if (trail.isEmpty())
		{
			// No beat yet (the hotkey starts one): never a blank side.
			CombatPainter.hitsReadout(g, l, s);
			return;
		}
		float u = l.u;
		float dy = l.rows == 1 ? l.height / 2f - 32f * u : 0f;
		Beat.Verdict last = trail.get(trail.size() - 1);
		boolean text = last != Beat.Verdict.ON_TIME;
		String word = last == Beat.Verdict.LATE ? "Late" : "Early";
		if (l.compact)
		{
			// Chat width: just the last beat's verdict, in the shared compact readout (no trail).
			ReadoutPainter.right(g, l, s, text ? word : "On time", text ? Palette.WHITE : Palette.mint(), "last beat");
			return;
		}
		Font vf = Fonts.bold(22f * u);
		float vw = text ? (float) vf.getStringBounds(word, g.getFontRenderContext()).getWidth() : 22f * u + 2f;

		float tx = CombatPainter.rightX(l) + 16f * u;
		float limit = ReadoutPainter.rightLimit(g, l, s);
		float vx = Math.min(tx + 124f * u, limit - vw);
		if (vx < tx)
		{
			return;
		}
		float trailEnd = Math.min(tx + 110f * u, vx - 14f * u);
		int fit = (int) Math.floor((trailEnd - tx + 2f * u) / (11f * u));
		int n = Math.min(trail.size(), fit);
		if (n > 0)
		{
			g.setColor(Palette.TRAIL_LINE);
			g.fill(Px.rect(tx, 32f * u + dy, trailEnd - tx, 1f));
		}
		for (int i = 0; i < n; i++)
		{
			Beat.Verdict v = trail.get(trail.size() - n + i);
			float x = tx + 2f * u + 11f * u * i;
			if (v == Beat.Verdict.ON_TIME)
			{
				g.setColor(Palette.withAlpha(Palette.mint(), 0.8f));
				g.fill(Px.round(x, 30f * u + dy, 7f * u, 4f * u, 4f * u, 4f * u));
			}
			else
			{
				g.setColor(Palette.WHITE);
				float y = (v == Beat.Verdict.LATE ? 16f : 32f) * u + dy;
				g.fill(Px.round(x, y, 7f * u, 16f * u, 4f * u, 4f * u));
			}
		}
		if (text)
		{
			g.setFont(vf);
			g.setColor(Palette.WHITE);
			g.drawString(word, vx, 39f * u + dy);
		}
		else
		{
			g.setColor(Palette.mint());
			g.fill(Icons.icon(Icons.Icon.CHECK, vx + 13f * u, 36.5f * u + dy, 2.2f * u));
		}
	}
}
