package com.superhistogram.model;

/** Where the bar sits on screen. */
public enum BarPosition
{
	/** Bottom-left, over the chat area. */
	REPLACE_CHAT,
	/** Bottom-left, lifted by the chatbox height. */
	ABOVE_CHAT,
	/** Dragged anywhere; RuneLite remembers the spot. */
	FLOATING
}
