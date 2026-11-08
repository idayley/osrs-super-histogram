package com.superhistogram.model;

/** Preset bar heights; the width is always {@link #WIDTH}. */
public enum BarSize
{
	SMALL(40),
	NORMAL(62),
	LARGE(80);

	public static final int WIDTH = 1026;

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
