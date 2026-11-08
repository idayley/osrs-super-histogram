package com.superhistogram.ui;

import com.superhistogram.model.BarState;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/** The RuneLite overlay: sizes the bar and hands painting to {@link BarPainter}. */
public final class BarOverlay extends Overlay
{
	static final int DEFAULT_W = 1026, DEFAULT_H = 62;

	private final Supplier<BarState> state;
	private final BarPainter painter;

	public BarOverlay(Supplier<BarState> state, Pulse pulse)
	{
		this.state = state;
		this.painter = new BarPainter(pulse);
		setResizable(true);
		setPosition(OverlayPosition.BOTTOM_LEFT);
		setPreferredSize(new Dimension(DEFAULT_W, DEFAULT_H));
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Dimension pref = getPreferredSize();
		BarLayout l = pref == null
			? BarLayout.of(DEFAULT_W, DEFAULT_H)
			: BarLayout.of(pref.width, pref.height);
		painter.paint(g, l, state.get(), System.currentTimeMillis());
		return new Dimension(l.width, l.height);
	}
}
