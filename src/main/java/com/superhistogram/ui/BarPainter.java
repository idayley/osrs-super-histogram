package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import com.superhistogram.ui.modes.AfkView;
import com.superhistogram.ui.modes.AgilityView;
import com.superhistogram.ui.modes.AlchView;
import com.superhistogram.ui.modes.GatherView;
import com.superhistogram.ui.modes.GeneralView;
import com.superhistogram.ui.modes.LazyFlickView;
import com.superhistogram.ui.modes.MinimalView;
import com.superhistogram.ui.modes.OneTickView;
import com.superhistogram.ui.modes.ProductionView;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.Map;

/** Paints the whole bar. Free of RuneLite types so tests can paint into a BufferedImage. */
public final class BarPainter
{
	private final Pulse pulse;
	private volatile Colours colours = Colours.DEFAULT;
	private final Map<Mode, ModeView> views = new EnumMap<>(Mode.class);

	/** @param pulse metronome / done-blink timing; null for none. */
	public BarPainter(Pulse pulse)
	{
		this.pulse = pulse;
		views.put(Mode.GENERAL, new GeneralView());
		views.put(Mode.AFK, new AfkView());
		views.put(Mode.MINIMAL, new MinimalView());
		views.put(Mode.LAZY_FLICK, new LazyFlickView());
		views.put(Mode.ONE_TICK, new OneTickView());
		views.put(Mode.PRODUCTION, new ProductionView());
		views.put(Mode.ALCH, new AlchView());
		views.put(Mode.GATHERING, new GatherView());
		views.put(Mode.AGILITY, new AgilityView());
	}

	/** Replaces the overridable colours; takes effect on the next paint. */
	public void setColours(Colours c)
	{
		this.colours = c == null ? Colours.DEFAULT : c;
	}

	public Colours colours()
	{
		return colours;
	}

	/** The layout the ruler, timeline and now line use: narrower ticks in the agility view, else {@code l} itself. */
	public static BarLayout timelineLayout(BarLayout l, BarState s)
	{
		return s != null && s.mode == Mode.AGILITY ? l.withTickWidth(AgilityView.tickWidth(l, s.agility)) : l;
	}

	/** Paints the bar at (0,0) in the layout's size. A null state paints the empty ground. */
	public void paint(Graphics2D g0, BarLayout l, BarState s, long nowMs)
	{
		Graphics2D g = (Graphics2D) g0.create();
		Colours prev = Palette.enter(colours);
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

			float w = l.width, h = l.height;
			RoundRectangle2D ground = new RoundRectangle2D.Float(0, 0, w, h, h, h);
			g.setColor(Palette.ground());
			g.fill(ground);
			float mint = pulse == null ? 0f : pulse.mintAlpha(nowMs);
			if (mint > 0f)
			{
				g.setColor(Palette.withAlpha(Palette.mint(), mint));
				g.fill(ground);
			}

			if (s != null)
			{
				paintContent(g, l, s, nowMs);
			}

			float outline = pulse == null ? 0f : pulse.outlineAlpha(nowMs);
			if (outline > 0f)
			{
				float sw = pulse.outlineWidth(nowMs);
				g.setStroke(new BasicStroke(sw));
				g.setColor(Palette.withAlpha(Palette.WHITE, outline));
				g.draw(new RoundRectangle2D.Float(sw / 2, sw / 2, w - sw, h - sw, h - sw, h - sw));
			}
		}
		finally
		{
			Palette.leave(prev);
			g.dispose();
		}
	}

	private void paintContent(Graphics2D g, BarLayout l, BarState s, long nowMs)
	{
		ModeView view = views.getOrDefault(s.mode, views.get(Mode.GENERAL));
		ReadoutPainter.left(g, l, s);
		BarLayout tl = timelineLayout(l, s);
		TimelinePainter.ruler(g, tl);

		Graphics2D tg = (Graphics2D) g.create();
		try
		{
			// A little slack on the left so XP text that starts just before a slot isn't cut hard.
			float slack = 6f * l.u;
			tg.clip(new Rectangle2D.Float(l.timelineX - slack, 0, l.timelineW + slack, l.height));
			view.paintTimeline(tg, tl, s);
		}
		finally
		{
			tg.dispose();
		}

		TimelinePainter.nowLine(g, tl);
		view.paintRight(g, l, s);
		ReadoutPainter.login(g, l, s, nowMs);
	}
}
