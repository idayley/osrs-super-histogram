package com.superhistogram.model;

/** Preset bar heights. {@link #WIDTH} is the classic chat width, used when floating or when the chat cannot be read. */
public enum BarSize
{
	SMALL(40),
	NORMAL(62),
	LARGE(80);

	public static final int WIDTH = 519;

	private final int height;

	BarSize(int height)
	{
		this.height = height;
	}

	public int height()
	{
		return height;
	}
}
