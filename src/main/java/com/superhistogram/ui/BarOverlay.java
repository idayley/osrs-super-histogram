package com.superhistogram.ui;

import com.superhistogram.model.BarPlacement;
import com.superhistogram.model.BarPosition;
import com.superhistogram.model.BarSize;
import com.superhistogram.model.BarState;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * The RuneLite overlay: sizes and places the bar and hands painting to {@link BarPainter}.
 * In the chat positions the bar copies the chat text area's width and sits on it (or just above it);
 * when floating, RuneLite places it wherever the user dragged it.
 */
public final class BarOverlay extends Overlay
{
	static final int DEFAULT_W = BarSize.WIDTH, DEFAULT_H = BarSize.NORMAL.height();

	private final Supplier<BarState> state;
	private final BarPainter painter;
	private volatile BarPosition barPosition = BarPosition.REPLACE_CHAT;
	private volatile Supplier<BarPlacement.Rect> chatArea = () -> null;
	/** The spot the user dragged the bar to, kept while another position is selected. */
	private Point floatingLocation;

	public BarOverlay(Supplier<BarState> state, Pulse pulse)
	{
		this.state = state;
		this.painter = new BarPainter(pulse);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		applyPosition(BarPosition.REPLACE_CHAT);
	}

	/** The chat text area in canvas pixels; null when unknown (the bar then floats at RuneLite's spot). */
	public void setChatArea(Supplier<BarPlacement.Rect> chatArea)
	{
		this.chatArea = chatArea;
	}

	public void setColours(Colours c)
	{
		painter.setColours(c);
	}

	public Colours colours()
	{
		return painter.colours();
	}

	public void applyPosition(BarPosition p)
	{
		BarPosition next = p == null ? BarPosition.REPLACE_CHAT : p;
		if (barPosition == BarPosition.FLOATING && next != BarPosition.FLOATING)
		{
			floatingLocation = getPreferredLocation();
		}
		barPosition = next;
		setPosition(OverlayPosition.DYNAMIC);
		if (next == BarPosition.FLOATING)
		{
			setMovable(true);
			setResizable(true);
			if (floatingLocation != null)
			{
				setPreferredLocation(floatingLocation);
			}
		}
		else
		{
			setMovable(false);
			setResizable(false);
			setPreferredLocation(null);
		}
	}

	/** Sets the bar height from the setting, overriding a height the user dragged (a dragged width stays). */
	public void applySize(int height)
	{
		int h = height <= 0 ? BarSize.NORMAL.height() : height;
		Dimension pref = getPreferredSize();
		setPreferredSize(new Dimension(pref == null ? BarSize.WIDTH : pref.width, h));
	}

	/** As {@link #applySize} but leaves a size RuneLite already restored (a hand-dragged one). */
	public void applySizeIfUnset(int height)
	{
		if (getPreferredSize() == null)
		{
			applySize(height);
		}
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Dimension pref = getPreferredSize();
		int h = pref == null ? DEFAULT_H : pref.height;
		BarPlacement.Rect spot = BarPlacement.place(barPosition, chatArea.get(), h);
		if (spot != null)
		{
			// Chat positions: drawn at the chat's own canvas coordinates (a DYNAMIC overlay is not translated).
			g.translate(spot.x, spot.y);
			try
			{
				painter.paint(g, BarLayout.of(spot.w, spot.h), state.get(), System.currentTimeMillis());
			}
			finally
			{
				g.translate(-spot.x, -spot.y);
			}
			return null;
		}
		BarLayout l = BarLayout.of(pref == null ? DEFAULT_W : pref.width, h);
		painter.paint(g, l, state.get(), System.currentTimeMillis());
		return new Dimension(l.width, l.height);
	}
}
