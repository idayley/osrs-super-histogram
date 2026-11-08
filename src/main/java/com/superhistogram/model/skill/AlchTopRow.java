package com.superhistogram.model.skill;

/** What the top row shows for each high-alch cast. */
public enum AlchTopRow
{
	GOLD("Gold"),
	XP("XP"),
	BOTH("Both");

	private final String label;

	AlchTopRow(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
