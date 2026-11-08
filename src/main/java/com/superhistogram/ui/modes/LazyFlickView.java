package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.combat.ActionsRow;
import com.superhistogram.model.combat.Beat;
import com.superhistogram.model.combat.FoodKind;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.CombatPainter;
import com.superhistogram.ui.Fonts;
import com.superhistogram.ui.ModeView;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.BasicStroke;
import java.util.List;

/**
 * Lazy-flick combat view: hits on you on top, your prayer in the middle, your own beat carets,
 * swords, eats and XP below; right = beat trail and the last verdict. Shows nothing ahead of
 * "now" except the next beat caret.
 */
public final class LazyFlickView implements ModeView
{
	@Override
	public void paintTimeline(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.timeline == null || s.nowTick < 0)
		{
			return;
		}
		float u = l.u;
		if (l.rows == 3)
		{
			carets(g, l, s);
		}
		CombatPainter.prayerPills(g, l, s, l.rows == 3 ? 12f * u : TimelinePainter.pillH(l));
		TimelinePainter.hits(g, l, s);
		if (l.rows == 3)
		{
			actions(g, l, s);
			CombatPainter.xpLow(g, l, s);
		}
	}

	private static void carets(Graphics2D g, BarLayout l, BarState s)
	{
		Beat b = s.beat;
		if (b == null)
		{
			return;
		}
		float u = l.u;
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			if (b.isBeat(t))
			{
				CombatPainter.caret(g, TimelinePainter.x(l, s, t), 55f * u, u, Palette.WHITE);
			}
		}
		int next = b.nextBeat();
		if (next > s.nowTick)
		{
			CombatPainter.caret(g, TimelinePainter.x(l, s, next), 55f * u, u, Palette.NEXT_BEAT);
		}
	}

	private static void actions(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.actionsRow == ActionsRow.OFF)
		{
			return;
		}
		float u = l.u;
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r == null)
			{
				continue;
			}
			float x = TimelinePainter.x(l, s, t);
			if (r.attackLate >= 0)
			{
				CombatPainter.sword(g, x, 44f * u, u);
				if (r.attackLate > 0)
				{
					g.setFont(Fonts.bold(9f * u));
					g.setColor(Palette.LOST);
					g.drawString("+" + r.attackLate, x + 6f * u, 48f * u);
				}
			}
			if (s.actionsRow == ActionsRow.ATTACKS_EATS && r.eat != null)
			{
				float ex = x + l.tickWidth / 2f;
				if (r.eat == FoodKind.COMBO)
				{
					CombatPainter.fish(g, ex - 1f * u, 41f * u, u);
					CombatPainter.fish(g, ex + 1f * u, 47f * u, u);
				}
				else
				{
					CombatPainter.fish(g, ex, 44f * u, u);
				}
				if (s.showEatCosts)
				{
					g.setFont(Fonts.bold(9f * u));
					boolean free = r.eatCost <= 0;
					g.setColor(free ? Palette.MINT : Palette.LOST);
					g.drawString(free ? "free" : "+" + r.eatCost, ex + 9f * u, 47.5f * u);
				}
			}
		}
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		if (!l.showRight || s.beat == null)
		{
			return;
		}
		List<Beat.Verdict> trail = s.beat.trail();
		if (trail.isEmpty())
		{
			return;
		}
		float u = l.u;
		float dy = l.rows == 1 ? l.height / 2f - 32f * u : 0f;
		Beat.Verdict last = trail.get(trail.size() - 1);
		boolean text = last != Beat.Verdict.ON_TIME;
		String word = last == Beat.Verdict.LATE ? "Late" : "Early";
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
			g.fill(new Rectangle2D.Float(tx, 32f * u + dy, trailEnd - tx, 1f));
		}
		for (int i = 0; i < n; i++)
		{
			Beat.Verdict v = trail.get(trail.size() - n + i);
			float x = tx + 2f * u + 11f * u * i;
			if (v == Beat.Verdict.ON_TIME)
			{
				g.setColor(Palette.withAlpha(Palette.MINT, 0.8f));
				g.fill(new RoundRectangle2D.Float(x, 30f * u + dy, 7f * u, 4f * u, 4f * u, 4f * u));
			}
			else
			{
				g.setColor(Palette.WHITE);
				float y = (v == Beat.Verdict.LATE ? 16f : 32f) * u + dy;
				g.fill(new RoundRectangle2D.Float(x, y, 7f * u, 16f * u, 4f * u, 4f * u));
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
			Path2D check = new Path2D.Float();
			check.moveTo(vx + 4f * u, 29f * u + dy);
			check.lineTo(vx + 10f * u, 35f * u + dy);
			check.lineTo(vx + 22f * u, 23f * u + dy);
			g.setColor(Palette.MINT);
			g.setStroke(new BasicStroke(3f * u, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(check);
		}
	}
}
