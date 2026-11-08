package com.superhistogram.model;

/** Whether the right readout's headline is the hourly rate or the session total. */
public enum RightSide
{
	PER_HOUR("Per hour"),
	SESSION("Session totals");

	private final String label;

	RightSide(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
