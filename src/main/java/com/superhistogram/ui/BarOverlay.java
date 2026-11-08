package com.superhistogram.ui;

import com.superhistogram.model.BarPlacement;
import com.superhistogram.model.BarPosition;
import com.superhistogram.model.BarSize;
import com.superhistogram.model.BarState;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/** The RuneLite overlay: sizes and places the bar and hands painting to {@link BarPainter}. */
public final class BarOverlay extends Overlay
{
	static final int DEFAULT_W = BarSize.WIDTH, DEFAULT_H = BarSize.NORMAL.height();

	private final Supplier<BarState> state;
	private final BarPainter painter;
	private volatile BarPosition barPosition = BarPosition.REPLACE_CHAT;
	private volatile IntSupplier chatHeight = () -> 0;
	/** The spot the user dragged the bar to, kept while another position is selected. */
	private Point floatingLocation;

	public BarOverlay(Supplier<BarState> state, Pulse pulse)
	{
		this.state = state;
		this.painter = new BarPainter(pulse);
		setResizable(true);
		applyPosition(BarPosition.REPLACE_CHAT);
	}

	/** Chatbox height in px for ABOVE_CHAT; a value of 0 or less means unknown (165 is used). */
	public void setChatHeight(IntSupplier chatHeight)
	{
		this.chatHeight = chatHeight;
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
		if (next == BarPosition.FLOATING)
		{
			setPosition(OverlayPosition.DYNAMIC);
			setMovable(true);
			if (floatingLocation != null)
			{
				setPreferredLocation(floatingLocation);
			}
		}
		else
		{
			setPosition(OverlayPosition.BOTTOM_LEFT);
			setMovable(false);
			setPreferredLocation(null);
		}
	}

	/** Sets the preferred size from the preset, overriding any size the user dragged. */
	public void applySize(BarSize size)
	{
		BarSize s = size == null ? BarSize.NORMAL : size;
		setPreferredSize(new Dimension(BarSize.WIDTH, s.height()));
	}

	/** As {@link #applySize} but leaves a size RuneLite already restored (a hand-dragged one). */
	public void applySizeIfUnset(BarSize size)
	{
		if (getPreferredSize() == null)
		{
			applySize(size);
		}
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Dimension pref = getPreferredSize();
		BarLayout l = pref == null
			? BarLayout.of(DEFAULT_W, DEFAULT_H)
			: BarLayout.of(pref.width, pref.height);
		painter.paint(g, l, state.get(), System.currentTimeMillis());
		// The bar is drawn at the top of the block; the space under it is the chatbox it floats above.
		return new Dimension(l.width, BarPlacement.blockHeight(barPosition, l.height, chatHeight.getAsInt()));
	}
}
