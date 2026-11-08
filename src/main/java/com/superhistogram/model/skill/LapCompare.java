package com.superhistogram.model.skill;

/** What an agility lap is compared with in the right readout. */
public enum LapCompare
{
	BEST("Best"),
	LAST("Last lap");

	private final String label;

	LapCompare(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
