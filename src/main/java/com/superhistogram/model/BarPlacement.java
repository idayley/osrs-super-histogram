package com.superhistogram.model;

/** Position math for the bar, free of RuneLite types. */
public final class BarPlacement
{
	/** Used when the chatbox bounds cannot be read. */
	public static final int FALLBACK_CHAT_HEIGHT = 165;

	private BarPlacement()
	{
	}

	/** How far above the bottom-left anchor the bar sits. */
	public static int lift(BarPosition position, int chatHeight)
	{
		if (position != BarPosition.ABOVE_CHAT)
		{
			return 0;
		}
		return chatHeight > 0 ? chatHeight : FALLBACK_CHAT_HEIGHT;
	}

	/** Height of the overlay block: the bar drawn at its top, with the lift below it. */
	public static int blockHeight(BarPosition position, int barHeight, int chatHeight)
	{
		return barHeight + lift(position, chatHeight);
	}
}
