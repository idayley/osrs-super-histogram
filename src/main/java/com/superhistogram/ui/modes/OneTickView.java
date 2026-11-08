package com.superhistogram.ui.modes;

import com.superhistogram.model.BarState;
import com.superhistogram.model.TickRecord;
import com.superhistogram.model.combat.FlickClassifier;
import com.superhistogram.model.combat.FlickClassifier.Kind;
import com.superhistogram.ui.BarLayout;
import com.superhistogram.ui.BaseTimeline;
import com.superhistogram.ui.CombatPainter;
import com.superhistogram.ui.Fonts;
import com.superhistogram.ui.Icons;
import com.superhistogram.ui.Lane;
import com.superhistogram.ui.Lens;
import com.superhistogram.ui.Palette;
import com.superhistogram.ui.ReadoutPainter;
import com.superhistogram.ui.TimelinePainter;
import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * 1-tick flick lens (one-tick-flick.html, one-timeline.html): every tick is a beat, so no carets. Dims the top,
 * doing and inputs lanes (your clicks stay at full strength) and marks each flick on the prayer lane over the
 * base's prayer pills: a mint dot = clean, an outline = split, a cross = skipped, an outlined run = draining.
 * Right = where in the tick your clicks land over the last 40 ticks, then the clean-flick streak or the last
 * verdict.
 */
public final class OneTickView implements Lens
{
	private static final float HIST_W = 96f, GAP = 16f;

	@Override
	public float[] emphasis(BarState s)
	{
		return Lens.full();
	}

	@Override
	public void overlay(Graphics2D g, BarLayout l, BarState s)
	{
		if (s.timeline == null || s.nowTick < 0 || s.flicks == null)
		{
			return;
		}
		window(g, l, s);
		// The inputs lane is dimmed, but the clicks are what you are flicking with.
		BaseTimeline.clicks(g, l, s);
		marks(g, l, s);
	}

	/** Faint mint bands either side of each tick line: where a flick click lands clean. */
	private static void window(Graphics2D g, BarLayout l, BarState s)
	{
		float h = l.laneH(Lane.INPUTS);
		if (s.clickWindowMs <= 0 || h <= 0f)
		{
			return;
		}
		float half = Math.min(0.5f, s.clickWindowMs / 600f) * l.tickWidth, y = l.laneY(Lane.INPUTS) - 0.5f * l.u;
		g.setColor(Palette.withAlpha(Palette.mint(), 0.28f));
		for (int t = TimelinePainter.oldestTick(l, s); t <= s.nowTick; t++)
		{
			float cx = TimelinePainter.x(l, s, t);
			g.fill(new RoundRectangle2D.Float(cx - half, y, 2f * half, h + l.u, 2f * l.u, 2f * l.u));
		}
	}

	private static void marks(Graphics2D g, BarLayout l, BarState s)
	{
		float ph = l.laneH(Lane.PRAYER);
		if (ph <= 0f)
		{
			return;
		}
		float u = l.u, q = ph / 10f, y = l.laneY(Lane.PRAYER), cy = y + ph / 2f;
		float sw = Math.max(1f, Math.min(1.5f * q, 1f * u));
		int from = TimelinePainter.oldestTick(l, s);
		List<float[]> labels = BaseTimeline.prayerLabels(g, l, s);

		// Drain outlines first, one per run of consecutive DRAIN ticks.
		g.setStroke(new BasicStroke(sw));
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
			float x0 = TimelinePainter.x(l, s, t), x1 = Math.min(TimelinePainter.x(l, s, end + 1), TimelinePainter.liveX(l));
			float ih = ph - 2f * sw;
			if (x1 - x0 > 2f * sw && ih > 0)
			{
				g.setColor(Palette.WHITE);
				g.draw(new RoundRectangle2D.Float(x0 + sw, y + sw, x1 - x0 - 2f * sw, ih, ih, ih));
			}
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
					float d = Math.min(5f * u, ph - 2f), dx = cx + 4f * u;
					if (clear(labels, dx - d / 2f - 1f * u, dx + d / 2f + 1f * u))
					{
						g.setColor(Palette.mint());
						g.fill(new Ellipse2D.Float(dx - d / 2f, cy - d / 2f, d, d));
					}
					break;
				case SPLIT:
					g.setColor(Palette.WHITE);
					float oh = ph - 2f * sw;
					g.draw(new RoundRectangle2D.Float(cx - 5f * q, y + sw, 14f * q, oh, oh, oh));
					break;
				case SKIP:
					float mx = x + l.tickWidth / 2f;
					g.setColor(Palette.WHITE);
					g.fill(Icons.icon(Icons.Icon.CROSS, mx, cy + 3.8f * q, q));
					break;
				default:
					break;
			}
		}
	}

	/** Whether [x0, x1] touches none of the ranges (the prayer-points labels a dot would sit on). */
	private static boolean clear(List<float[]> ranges, float x0, float x1)
	{
		for (float[] r : ranges)
		{
			if (x0 < r[1] && x1 > r[0])
			{
				return false;
			}
		}
		return true;
	}

	private static Kind kind(BarState s, int tick)
	{
		TickRecord r = s.timeline.peek(tick);
		return r == null ? null : r.flick;
	}

	@Override
	public void paintRight(Graphics2D g, BarLayout l, BarState s)
	{
		FlickClassifier fc = s.flicks;
		if (!l.showRight || s.timeline == null || s.nowTick < 0)
		{
			return;
		}
		if (fc == null || fc.last() == null)
		{
			// No flick judged yet: never a blank side.
			CombatPainter.hitsReadout(g, l, s);
			return;
		}
		float u = l.u, f = l.height / 52f;
		float hx = CombatPainter.rightX(l) + 16f * u;
		float limit = ReadoutPainter.rightLimit(g, l, s);

		Kind last = fc.last();
		boolean word = last == Kind.SPLIT || last == Kind.SKIP || last == Kind.DRAIN;
		String main = word ? (last == Kind.SPLIT ? "Split" : last == Kind.SKIP ? "Skipped" : "Draining")
			: Integer.toString(fc.streak());
		if (l.compact)
		{
			// Chat width: the verdict or streak in the shared compact readout; the histogram is dropped.
			ReadoutPainter.right(g, l, s, main, word ? Palette.WHITE : Palette.mint(), word ? "last tick" : "clean in a row");
			return;
		}
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
		g.setColor(word ? Palette.WHITE : Palette.mint());
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
			g.setColor(i < FlickClassifier.SPLIT_BIN ? Palette.mint() : Palette.WHITE);
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
