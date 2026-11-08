package com.superhistogram.model;

import java.util.Objects;

/** Position math for the bar, free of RuneLite types. */
public final class BarPlacement
{
	/** Classic chatbox: 519 wide, 165 tall including the 23 px button row. */
	static final int CLASSIC_CHAT_W = 519, CLASSIC_CHAT_H = 165, CLASSIC_BUTTONS_H = 23;

	private BarPlacement()
	{
	}

	/** A rectangle in canvas pixels. */
	public static final class Rect
	{
		public final int x, y, w, h;

		public Rect(int x, int y, int w, int h)
		{
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}

		@Override
		public boolean equals(Object o)
		{
			if (!(o instanceof Rect))
			{
				return false;
			}
			Rect r = (Rect) o;
			return x == r.x && y == r.y && w == r.w && h == r.h;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(x, y, w, h);
		}

		@Override
		public String toString()
		{
			return "Rect(" + x + "," + y + " " + w + "x" + h + ")";
		}
	}

	/**
	 * Where the bar goes for a chat-anchored position: as wide as the chat text area, either sitting at its
	 * bottom (replace chat) or just above it. Null for FLOATING, which RuneLite places.
	 */
	public static Rect place(BarPosition position, Rect chatArea, int barHeight)
	{
		if (position == BarPosition.FLOATING || chatArea == null)
		{
			return null;
		}
		int y = position == BarPosition.ABOVE_CHAT
			? chatArea.y - barHeight
			: chatArea.y + chatArea.h - barHeight;
		return new Rect(chatArea.x, y, chatArea.w, barHeight);
	}

	/** The classic chat text area at the bottom-left of a canvas, for when the chat widgets can't be read. */
	public static Rect fallbackChat(int canvasHeight)
	{
		return new Rect(0, canvasHeight - CLASSIC_CHAT_H, CLASSIC_CHAT_W, CLASSIC_CHAT_H - CLASSIC_BUTTONS_H);
	}
}
