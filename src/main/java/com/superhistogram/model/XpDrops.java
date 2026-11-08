package com.superhistogram.model;

/** How XP drops appear on the bar. */
public enum XpDrops
{
	NUMBERS("Numbers"),
	DOTS_ONLY("Dots only"),
	OFF("Off");

	private final String label;

	XpDrops(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
