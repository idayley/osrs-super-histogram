package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Span;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.combat.FlickClassifier;
import com.superhistogram.model.combat.FlickClassifier.Kind;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.CombatPainter;
import com.superhistogram.ui.Fonts;
import com.superhistogram.ui.ModeView;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 1-tick flick view (one-tick-flick.html): every tick is a beat, so no carets. Middle row = one
 * mark per tick (dot, hollow, x or a long drain pill); right = where in the tick your clicks land
 * over the last 40 ticks, then the clean-flick streak or the last verdict.
 */
public final class OneTickView implements ModeView
{
	private static final float HIST_W = 96f, GAP = 16f;

	@Override
	public void paintTimeline(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.timeline == null || s.nowTick < 0)
		{
			return;
		}
		marks(g, l, s);
		TimelinePainter.hits(g, l, s);
		CombatPainter.xpLow(g, l, s);
		TimelinePainter.clicks(g, l, s);
	}

	private static void marks(Graphics2D g, BarLayout l, BarState s)
	{
		float ph = TimelinePainter.pillH(l) * 10f / 14f;
		float q = ph / 10f, y = TimelinePainter.pillY(l), cy = y + ph / 2f;
		int from = TimelinePainter.oldestTick(l, s);

		// Drain pills first, one per run of consecutive DRAIN ticks.
		for (int t = from; t <= s.nowTick; t++)
		{
			if (kind(s, t) != Kind.DRAIN)
			{
				continue;
			}
			int end = t;
			while (end + 1 <= s.nowTick && kind(s, end + 1) == Kind.DRAIN)
			{
				end++;
			}
			float x0 = TimelinePainter.x(l, s, t), x1 = TimelinePainter.x(l, s, end + 1);
			g.setColor(colourAt(s, t));
			g.fill(new RoundRectangle2D.Float(x0, y, x1 - x0, ph, ph, ph));
			t = end;
		}
		for (int t = from; t <= s.nowTick; t++)
		{
			TickRecord r = s.timeline.peek(t);
			if (r == null || r.flick == null)
			{
				continue;
			}
			float x = TimelinePainter.x(l, s, t), cx = x + r.flickPhase * l.tickWidth;
			switch (r.flick)
			{
				case OK:
					g.setColor(colourAt(s, t));
					g.fill(new RoundRectangle2D.Float(cx - 5f * q, y, 10f * q, ph, ph, ph));
					break;
				case SPLIT:
					g.setColor(Palette.WHITE);
					g.setStroke(new BasicStroke(1.5f * q));
					g.draw(new RoundRectangle2D.Float(cx - 5f * q, y + .75f * q, 14f * q, 8.5f * q, 8.5f * q, 8.5f * q));
					break;
				case SKIP:
					float mx = x + l.tickWidth / 2f;
					Path2D cross = new Path2D.Float();
					cross.moveTo(mx - 3f * q, cy - 3f * q);
					cross.lineTo(mx + 3f * q, cy + 3f * q);
					cross.moveTo(mx + 3f * q, cy - 3f * q);
					cross.lineTo(mx - 3f * q, cy + 3f * q);
					g.setColor(Palette.WHITE);
					g.setStroke(new BasicStroke(1.6f * q, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
					g.draw(cross);
					break;
				default:
					break;
			}
		}
	}

	private static Kind kind(BarState s, int tick)
	{
		TickRecord r = s.timeline.peek(tick);
		return r == null ? null : r.flick;
	}

	/** Colour of the pray-* span covering the tick, PRAYER_OTHER when none. */
	private static Color colourAt(BarState s, int tick)
	{
		Color c = null;
		for (Span sp : s.timeline.spansBetween(tick, tick))
		{
			Color pc = CombatPainter.prayerColor(sp.kind);
			if (pc != null)
			{
				c = pc;
			}
		}
		return c == null ? Palette.PRAYER_OTHER : c;
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		FlickClassifier fc = s.flicks;
		if (!l.showRight || fc == null || s.timeline == null || s.nowTick < 0)
		{
			return;
		}
		float u = l.u, f = l.height / 52f;
		float hx = CombatPainter.rightX(l) + 16f * u;
		float limit = ReadoutPainter.rightLimit(g, l, s);

		Kind last = fc.last();
		boolean word = last == Kind.SPLIT || last == Kind.SKIP || last == Kind.DRAIN;
		String main = word ? (last == Kind.SPLIT ? "Split" : last == Kind.SKIP ? "Skipped" : "Draining")
			: Integer.toString(fc.streak());
		float size = 22f * u, avail = limit - hx;
		float mw = width(g, main, Fonts.bold(size));
		float gap = GAP * u;
		// The histogram gives way to the verdict, then shrinks to what is left of the room.
		float hw = Math.min(HIST_W * u, avail - mw - gap);
		boolean hist = hw >= 36f * u;
		float vx = hist ? hx + hw + (hw >= HIST_W * u ? gap : 10f * u) : hx;
		if (!hist)
		{
			hw = 0f;
		}
		float room = limit - vx;
		if (mw > room)
		{
			size = size * room / mw;
			if (size < 11f * u)
			{
				return;
			}
		}
		if (hist)
		{
			histogram(g, l, s, hx, hw, f);
		}
		g.setFont(Fonts.bold(size));
		g.setColor(word ? Palette.WHITE : Palette.MINT);
		g.drawString(main, vx, (word ? 36f : 33f) * f);
		if (!word)
		{
			Font sf = Fonts.bold(10f * u);
			if (width(g, "clean in a row", sf) <= limit - vx)
			{
				g.setFont(sf);
				g.setColor(Palette.DIM);
				g.drawString("clean in a row", vx, 46f * f);
			}
		}
	}

	private static void histogram(Graphics2D g, BarLayout l, BarState s, float hx, float hw, float f)
	{
		float u = l.u, pitch = hw / FlickClassifier.HIST_BINS;
		int[] bins = FlickClassifier.histogram(s.timeline, s.nowTick - FlickClassifier.HIST_TICKS + 1, s.nowTick);
		int max = 1;
		for (int n : bins)
		{
			max = Math.max(max, n);
		}
		Shape box = new RoundRectangle2D.Float(hx, 9f * f, hw, 26f * f, 8f * u, 8f * u);
		g.setColor(Palette.withAlpha(Palette.WHITE, 0.04f));
		g.fill(box);
		Graphics2D zg = (Graphics2D) g.create();
		try
		{
			zg.clip(box);
			zg.setColor(Palette.withAlpha(Palette.WHITE, 0.07f));
			zg.fill(new java.awt.geom.Rectangle2D.Float(hx + pitch * FlickClassifier.SPLIT_BIN, 9f * f,
				pitch * (FlickClassifier.HIST_BINS - FlickClassifier.SPLIT_BIN), 26f * f));
		}
		finally
		{
			zg.dispose();
		}
		for (int i = 0; i < bins.length; i++)
		{
			if (bins[i] == 0)
			{
				continue;
			}
			float bh = 22f * f * bins[i] / max;
			g.setColor(i < FlickClassifier.SPLIT_BIN ? Palette.MINT : Palette.WHITE);
			g.fill(new RoundRectangle2D.Float(hx + pitch * i + pitch / 8f, 33f * f - bh, pitch * 0.75f, bh,
				3f * u, 3f * u));
		}
		Font lf = Fonts.bold(10f * u);
		String a = "tick start", b = "next tick";
		if (width(g, a, lf) + width(g, b, lf) + 8f * u <= hw)
		{
			g.setFont(lf);
			g.setColor(Palette.DIM);
			g.drawString(a, hx, 46f * f);
			g.drawString(b, hx + hw - width(g, b, lf), 46f * f);
		}
	}

	private static float width(Graphics2D g, String text, Font f)
	{
		return (float) f.getStringBounds(text, g.getFontRenderContext()).getWidth();
	}
}
