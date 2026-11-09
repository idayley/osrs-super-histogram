package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import com.superhistogram.model.Mode;
import com.superhistogram.ui.modes.AfkView;
import com.superhistogram.ui.modes.AgilityView;
import com.superhistogram.ui.modes.ThieveView;
import com.superhistogram.ui.modes.LoopView;
import com.superhistogram.ui.modes.RoundView;
import com.superhistogram.ui.modes.HuntView;
import com.superhistogram.ui.modes.CastView;
import com.superhistogram.ui.modes.AlchView;
import com.superhistogram.ui.modes.GatherView;
import com.superhistogram.ui.modes.GeneralView;
import com.superhistogram.ui.modes.LazyFlickView;
import com.superhistogram.ui.modes.MinimalView;
import com.superhistogram.ui.modes.OneTickView;
import com.superhistogram.ui.modes.ProductionView;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.EnumMap;
import java.util.Map;

/** Paints the whole bar. Free of RuneLite types so tests can paint into a BufferedImage. */
public final class BarPainter
{
	private static final int TICK_MS = 600;
	/** On a mode change the lane emphasis eases to the new lens's and its overlay fades in over this long. */
	static final int FADE_MS = 300;
	/** Corner radius of the bar at the 62 px reference height. */
	static final float CORNER_U = 14f;
	private final Pulse pulse;
	private volatile Colours colours = Colours.DEFAULT;
	private final Map<Mode, Lens> views = new EnumMap<>(Mode.class);

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
		views.put(Mode.THIEVING, new ThieveView());
		views.put(Mode.HUNTER, new HuntView());
		views.put(Mode.CASTING, new CastView());
		views.put(Mode.RUNS, new LoopView());
		views.put(Mode.ROUNDS, new RoundView());
	}

	/** The lens painted for a mode (General for any mode without its own). */
	Lens lens(Mode mode)
	{
		return views.getOrDefault(mode, views.get(Mode.GENERAL));
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

	/**
	 * The layout the ruler, timeline and now line use: {@code l} with the now-line % setting applied (the same
	 * tick width in every mode), else {@code l} itself.
	 */
	public static BarLayout timelineLayout(BarLayout l, BarState s)
	{
		return s != null && s.nowLinePct > 0 ? l.withNowPct(s.nowLinePct) : l;
	}

	/** Paints the bar at (0,0) in the layout's size. A null state paints the empty ground. */
	public void paint(Graphics2D g0, BarLayout l, BarState s, long nowMs)
	{
		Graphics2D g = (Graphics2D) g0.create();
		Colours prev = Palette.enter(colours);
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			// Pixel font: hard edges and whole-pixel spacing, so it stays crisp when the game is stretched.
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

			float w = l.width, h = l.height;
			Shape ground = Squircle.of(0, 0, w, h, CORNER_U * l.u);
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

			if (pulse != null && pulse.alternating(nowMs))
			{
				float sw = Pulse.CUE_WIDTH;
				g.setStroke(new BasicStroke(sw));
				g.setColor(Palette.withAlpha(pulse.phase() == 0 ? Palette.rimTick() : Palette.rimTock(),
					pulse.alternateAlpha(nowMs)));
				g.draw(Squircle.of(sw / 2, sw / 2, w - sw, h - sw, CORNER_U * l.u - sw / 2));
			}
			float outline = pulse == null ? 0f : pulse.outlineAlpha(nowMs);
			if (outline > 0f)
			{
				float sw = pulse.outlineWidth(nowMs);
				g.setStroke(new BasicStroke(sw));
				g.setColor(Palette.withAlpha(Palette.WHITE, outline));
				g.draw(Squircle.of(sw / 2, sw / 2, w - sw, h - sw, CORNER_U * l.u - sw / 2));
			}
		}
		finally
		{
			Palette.leave(prev);
			g.dispose();
		}
	}

	/** Tests: paint the base at full emphasis in every lens, without the lens overlays. */
	boolean baseOnly;
	private Mode shownMode;
	private long modeChangedMs;
	/** Lane alpha at the moment of the last mode change (the tween's start) and as last painted. */
	private float[] fromEmphasis, shownEmphasis;

	/**
	 * Lane alpha (by {@link Lane#ordinal()}) to paint the base with: on a mode change it eases from what was on
	 * screen to {@code target} over {@link #FADE_MS}; the first paint shows {@code target} at once.
	 */
	float[] laneAlpha(Mode mode, float[] target, long nowMs)
	{
		if (mode != shownMode)
		{
			boolean first = shownMode == null || shownEmphasis == null;
			modeChangedMs = first ? Long.MIN_VALUE / 2 : nowMs;
			fromEmphasis = first ? target : shownEmphasis.clone();
			shownMode = mode;
		}
		float t = ease(progress(nowMs));
		float[] out = new float[target.length];
		for (int i = 0; i < out.length; i++)
		{
			float from = fromEmphasis != null && i < fromEmphasis.length ? fromEmphasis[i] : target[i];
			out[i] = from + (target[i] - from) * t;
		}
		shownEmphasis = out;
		return out;
	}

	/** 0..1 opacity of the lens overlay: 0 right after a mode change, 1 after {@link #FADE_MS} (and on the first paint). */
	float overlayAlpha(long nowMs)
	{
		return shownMode == null ? 1f : progress(nowMs);
	}

	private float progress(long nowMs)
	{
		long dt = nowMs - modeChangedMs;
		return dt >= FADE_MS ? 1f : Math.max(0f, dt / (float) FADE_MS);
	}

	private static float ease(float t)
	{
		return 1f - (1f - t) * (1f - t);
	}

	/** How far (px) the timeline has slid since the newest tick: 0 at the tick, one slot just before the next. */
	static float glide(BarLayout tl, BarState s, long nowMs)
	{
		if (!s.smooth || s.tickMs <= 0)
		{
			return 0f;
		}
		float frac = (nowMs - s.tickMs) / (float) TICK_MS;
		return Math.max(0f, Math.min(1f, frac)) * tl.tickWidth;
	}

	private void paintContent(Graphics2D g, BarLayout l, BarState s, long nowMs)
	{
		Lens lens = views.getOrDefault(s.mode, views.get(Mode.GENERAL));
		float[] emphasis = laneAlpha(s.mode, baseOnly ? Lens.full() : lens.emphasis(s), nowMs);
		float overlay = baseOnly ? 0f : overlayAlpha(nowMs);
		ReadoutPainter.left(g, l, s);
		float glide = Math.round(glide(timelineLayout(l, s), s, nowMs));
		BarLayout base = timelineLayout(l, s);
		BarLayout tl = base.withGlide(glide, s.smooth && s.tickMs > 0 ? base.tickWidth : 0f);

		Graphics2D tg = (Graphics2D) g.create();
		try
		{
			// A little slack on the left so XP text that starts just before a slot isn't cut hard.
			float slack = 6f * l.u;
			tg.clip(Px.rect(l.timelineX - slack, 0, l.timelineW + slack, l.height));
			// Smooth scrolling: slide everything left through the current tick, so the next tick's
			// one-slot step lands exactly where the slide ends and nothing jumps.
			tg.translate(tl.lag - glide, 0);
			BaseTimeline.paint(tg, tl, s, emphasis);
			if (overlay > 0f)
			{
				Graphics2D og = (Graphics2D) tg.create();
				try
				{
					if (overlay < 1f)
					{
						og.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, overlay));
					}
					lens.overlay(og, tl, s);
				}
				finally
				{
					og.dispose();
				}
			}
		}
		finally
		{
			tg.dispose();
		}

		TimelinePainter.nowLine(g, tl);
		lens.paintRight(g, l, s);
		ReadoutPainter.login(g, l, s, nowMs);
	}
}
